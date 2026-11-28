#!/usr/bin/env python3
"""List the earlier upstream commits a pick jumps ahead of.

Usage: find_rewriters.py <upstream-hash> [<upstream-hash> ...]

For each upstream commit given (mihon or tachiyomiSY), list the commits that come before it upstream, that Komikku
doesn't have yet (no `(cherry picked from commit ...)` line on HEAD unless its row has a `Partial` or `Folded` note,
and its cherrypick_log.md row is not `O` or `X`),
and that wrote or rewrote the lines it changes. Each one rewrites those lines when it is picked later, so the
change picked early conflicts there, and taking upstream's side loses it. That includes the commit that created a
file the given commit changes, when the file doesn't exist here at that path (code moved into a new file arrives in
its older form, with no conflict at all).

For lines the given commit only inserts, the lines around the insertion are traced: an earlier commit that rewrites
them conflicts around the inserted lines when picked later, and taking upstream's side drops them.
Commits without a row (before the log's `since`, or Renovate's) are left out, since there is no row to note.

Output, for each commit given, one line per commit found, oldest first:
    <row-hash> [<status>] <date> <subject> :: <files> (via <SY commit> when the hit is SY's copy of a mihon commit)
<row-hash> is the row to note: the mihon row for SY's copy of a mihon commit, otherwise the commit itself.
Then `== Rows to note: <N>` and each of those rows once, since both copies of a commit often list the same one.
"""

import importlib.util
import json
import re
import subprocess
import sys
from pathlib import Path


def _load_log_script():
    """The cherry-pick log's own script, for its table parsing (escaped pipes, notes) instead of a copy of it."""
    path = Path(__file__).resolve().parents[1] / "cherrypick-log" / "update_cherrypick_log.py"
    spec = importlib.util.spec_from_file_location("update_cherrypick_log", path)
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module  # dataclasses look the module up while it loads
    spec.loader.exec_module(module)
    return module


LOG = _load_log_script()


def git(*args: str) -> str:
    # Plain output whatever the user's config: colored diffs hide the hunk headers, quoted paths name no file
    config = ["-c", "color.ui=never", "-c", "color.diff=never", "-c", "core.quotepath=off"]
    return subprocess.run(["git", *config, *args], capture_output=True, text=True, check=False).stdout


ROOT = Path(git("rev-parse", "--show-toplevel").strip())
CONFIG = json.loads((ROOT / ".claude/skills/cherrypick-log/config.json").read_text())
SINCE = CONFIG["since"]
IGNORED = ("CHANGELOG.md",)

HASH_RE = re.compile(r"/commit/([0-9a-f]{40})")
SY_PICK_RE = re.compile(r"pick of mihon `([0-9a-f]{7,40})`")
CITED_RE = re.compile(r"cherry picked from commit ([0-9a-f]{40})")


def read_rows() -> dict[str, dict]:
    rows = {}
    for line in (ROOT / CONFIG["output"]).read_text().splitlines():
        m = HASH_RE.search(line)
        if not m or not line.startswith("|"):
            continue
        cells = LOG.split_cells(line)  # splits on unescaped pipes only, as the log script writes them
        if len(cells) != len(LOG.COLUMNS):
            continue
        sy = SY_PICK_RE.search(cells[6]) if cells[2].startswith("↳") else None
        # A Partial or Folded note marks a pick that left part of the commit out
        rows[m.group(1)] = {"status": cells[0], "mihon": sy.group(1) if sy else None,
                            "partial": bool(LOG.PARTIAL_NOTE_RE.search(LOG.unesc(cells[-1])))}
    return rows


def renamed_from_here(creator: str, path: str) -> bool:
    """Whether `creator` made `path` by renaming a file Komikku has: git then carries a change made there through it."""
    fields = git("show", "-M", "--name-status", "-z", "--format=", creator).split("\0")
    i = 0
    while i < len(fields) and fields[i]:
        kind = fields[i]
        if kind.startswith(("R", "C")):
            source, dest = fields[i + 1], fields[i + 2]
            if dest == path:
                return kind.startswith("R") and bool(git("ls-tree", "HEAD", "--", source).strip())
            i += 3
        else:
            i += 2
    return False


def row_target(rows: dict[str, dict], c: str) -> str:
    """The row to note for `c`: the mihon row when `c` is SY's copy of a mihon commit, otherwise `c` itself."""
    mihon = rows[c]["mihon"]
    if mihon:
        full = next((h for h in rows if h.startswith(mihon)), None)
        if full:
            return full
    return c


def traced_range(start: int, count: int, old_lines: int) -> tuple[int, int]:
    """The old lines to trace for a hunk: the ones it changes, or for a pure insertion after line `start`, the lines on
    either side of it."""
    if count:
        return start, start + count - 1
    return max(start, 1), min(start + 1, old_lines)


def file_hits(up: str, path: str, hits: dict[str, set[str]]) -> None:
    """Add to `hits` the earlier commits that wrote the lines `up` changes in `path`, and the commit that created
    `path` when it doesn't exist here."""
    if not git("ls-tree", "HEAD", "--", path).strip():
        creator = git("log", "-1", "--format=%H", "--diff-filter=A", f"{up}~1", "--", path).strip()
        if creator and not renamed_from_here(creator, path):
            hits.setdefault(creator, set()).add(path)
    old_lines = len(git("show", f"{up}~1:{path}").splitlines())
    hunks = git("diff", "--no-ext-diff", "-U0", f"{up}~1", up, "--", path)
    for start, count in re.findall(r"^@@ -(\d+)(?:,(\d+))? \+", hunks, re.M):
        first, last = traced_range(int(start), int(count or 1), old_lines)
        if first > last:
            continue
        log = git("log", f"--since={SINCE}", "--format=%H", "-s", "-L", f"{first},{last}:{path}", f"{up}~1")
        for c in re.findall(r"^[0-9a-f]{40}$", log, re.M):
            hits.setdefault(c, set()).add(path)


def commit_hits(up: str) -> dict[str, set[str]]:
    """Every earlier commit that wrote lines `up` changes, with the files it wrote them in."""
    hits: dict[str, set[str]] = {}
    # One diff per file, reading only its hunk headers: in a combined -U0 diff a removed line starting with
    # "-- " (an SQL comment) reads as a "--- <path>" file header
    fields = git("diff", "--name-status", "-z", "--no-renames", f"{up}~1", up).split("\0")
    for kind, path in zip(fields[0::2], fields[1::2]):
        if kind == "A" or path.endswith(IGNORED):
            continue  # A new file has no earlier lines to rewrite
        file_hits(up, path, hits)
    return hits


def missing_rows(hits: dict[str, set[str]], rows: dict[str, dict], cited: set[str]) -> list[tuple]:
    """The hits Komikku doesn't have yet, as (date, row hash, commit, files), oldest first."""
    found = []
    for c, paths in hits.items():
        if c not in rows:
            continue
        target = row_target(rows, c)
        # Picked in part (a Partial or Folded note): the part left out can still rewrite the lines
        partial = rows[c]["partial"] or rows[target]["partial"]
        if rows[target]["status"] in ("O", "X") or ({c, target} & cited and not partial):
            continue
        date = git("log", "-1", "--format=%ad", "--date=short", target).strip()
        found.append((date, target, c, paths))
    return sorted(found)


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__.strip(), file=sys.stderr)
        return 2
    rows = read_rows()
    cited = set(CITED_RE.findall(git("log", "HEAD", "--format=%b")))

    to_note: dict[str, tuple[str, str]] = {}  # row hash -> (date, subject), across every commit given
    for arg in sys.argv[1:]:
        up = git("rev-parse", "--verify", "--quiet", f"{arg}^{{commit}}").strip()
        if not up:
            print(f"{arg}: not a commit", file=sys.stderr)
            return 1
        print(f"== {up[:10]} {git('log', '-1', '--format=%s', up).strip()}")
        for date, target, c, paths in missing_rows(commit_hits(up), rows, cited):
            via = f" (via {c[:10]})" if target != c else ""
            subject = git("log", "-1", "--format=%s", target).strip()
            status = rows[target]["status"]
            files = ", ".join(sorted(p.rsplit("/", 1)[-1] for p in paths))
            print(f"{target[:10]} [{status or ' '}] {date} {subject} :: {files}{via}")
            to_note.setdefault(target, (date, subject))

    # Both copies of a commit often list the same row: count and note each row once
    print(f"== Rows to note: {len(to_note)}")
    for target, (date, subject) in sorted(to_note.items(), key=lambda item: item[1]):
        print(f"{target[:10]} {date} {subject}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
