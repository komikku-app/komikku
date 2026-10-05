#!/usr/bin/env python3
"""
Upstream cherry-pick log generator.

Builds and maintains a Markdown todo list (default: ``cherrypick_log.md`` in the repo
root) of upstream commits that may still need to be cherry-picked into this fork.

How it works (full explanation in README.md next to this file):

* Upstreams are set in ``config.json``: one ``main_upstream`` (the *primary*, e.g. mihon for
  Komikku), whose commits define the order of the list, and one or more
  ``additional_upstreams`` (*secondaries*, e.g. tachiyomiSY), whose commits are interleaved
  with the primary's.
* Only non-merge commits whose committer date is on/after ``since`` are listed. ``since`` is
  per repo: set it in ``config.json`` or override it with ``--since YYYY-MM-DD``.
  Commits whose author / committer / co-author matches ``exclude_identity_regex``
  (e.g. Renovate) are dropped, along with secondary copies of them.
* A secondary commit is paired with the primary commit it copies, checked in this order:
  ``cherry picked from commit <hash>`` marker, then the same PR number with a similar
  title, then the same title and author. A copy is shown directly above its original.
  A secondary-only commit is shown directly above the group of the nearest OLDER
  secondary commit that copies a primary commit (or at the bottom if there is none).
* Status column: ``O`` cherry-picked into the target branch, ``?`` maybe, empty = not
  found, ``X`` = will not pick (set by hand). Detection uses the target branch's history,
  ``cherry picked from commit`` markers, ``owner/repo#N`` PR references and titles.
  A commit and its copies in other upstreams share one status.
* Re-running keeps ``O`` / ``X`` (and any other hand-written status) and the Notes
  column, re-checks empty / ``?`` rows, adds new upstream commits, and moves commits
  that disappeared from upstream into a "Gone from upstream" section. Rows dated before
  ``since`` that are already in the file (added by a one-off pass over older history) are
  kept as they are at the end of the list; an empty / ``?`` one only changes to ``O``.
* Commits that cancel each other out (``This reverts commit …`` chains) are flagged ``?``
  with a ``Cancels out (script):`` note for a human (or the AI review) to decide, but only
  while the fork has none of them (all rows empty / ``?``) and none is AI-reviewed. The flags
  are recomputed on every run. Commits that only bump the app version are marked ``X`` by
  the AI review.

Commands:
    update     (default) fetch upstreams and regenerate the log
    uncertain  list rows still needing review (used by the AI skill, SKILL.md)
    mark       set Status / Notes of one row without hand-editing the table

Only the Python 3.9+ standard library and ``git`` are required.
"""

from __future__ import annotations

import argparse
import datetime as dt
import difflib
import json
import re
import subprocess
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
DEFAULT_CONFIG = SCRIPT_DIR / "config.json"
README_REL = ".claude/skills/cherrypick-log/README.md"

STATUS_DONE = "O"
STATUS_MAYBE = "?"
STATUS_SKIP = "X"
RECHECKED_STATUSES = ("", STATUS_MAYBE)
# Notes written by the AI review start with this; an empty status with such a note is a
# reviewed "not picked" verdict and is only overridden by strong (O) evidence.
AI_NOTE_MARK = "AI checked"
# Notes for rows the script itself flags '?' (commits that cancel each other out). Older versions
# marked them X with an "Omitted (script):" note; such rows are released the same way.
REVERT_NOTE_MARK = "Cancels out (script):"
SCRIPT_NOTE_MARKS = (REVERT_NOTE_MARK, "Omitted (script):")
OMIT_WAS_RE = re.compile(r"\[was: ([^\]]*)\]")
NOTE_SPLIT_RE = re.compile(r"((?<!&lt);\s*)")  # capturing: re.split keeps the separators

COLUMNS = ["Status", "Commit", "Upstream", "Date", "Title", "Author", "Details", "Notes"]
SHORT_LEN = 10

LOG_FORMAT = "%H%x1f%cs%x1f%an%x1f%ae%x1f%cn%x1f%ce%x1f%B%x1e"

REVERT_RE = re.compile(r"This reverts commit ([0-9a-f]{7,40})", re.I)
CHERRY_RE = re.compile(r"cherry[- ]picked from commit ([0-9a-f]{7,40})", re.I)
COMMIT_URL_RE = re.compile(r"github\.com/([\w.-]+/[\w.-]+)/commit/([0-9a-f]{7,40})", re.I)
SUBJECT_PR_RE = re.compile(r"\((?:([\w.-]+/[\w.-]+))?#(\d+)\)")
QUALIFIED_REF_RE = re.compile(
    r"(?:https?://github\.com/([\w.-]+/[\w.-]+)/pull/(\d+))|(?<![\w/.-])([\w.-]+/[\w.-]+)#(\d+)",
    re.I,
)
# A ref right after one of these words is an issue/PR being referenced, not the change itself.
CLOSING_BEFORE_RE = re.compile(
    r"\b(?:fix(?:es|ed)?|close[sd]?|resolve[sd]?|see|refs?|related to|part of|follow[- ]?up (?:to|of))\s*:?\s*$",
    re.I,
)
CONVENTIONAL_RE = re.compile(
    r"^(?:feat|fix|chore|refactor|perf|docs?|style|tests?|build|ci|i18n|deps|revert)(?:\([^)]*\))?!?:\s*",
    re.I,
)
BULLET_RE = re.compile(r"^\s*(?:[-*•+]|\d+[.)])\s+")
CO_AUTHOR_RE = re.compile(r"^co-authored-by:(.*)$", re.I | re.M)
ROW_LINK_RE = re.compile(r"\]\(https://github\.com/([\w.-]+/[\w.-]+)/commit/([0-9a-f]{40})\)")
CELL_SPLIT_RE = re.compile(r"(?<!\\)\|")

MIN_TITLE_WORDS = 3
SIMILARITY = 0.85
# A secondary copy can land before its primary original (picked from the PR before it was merged).
PAIR_DATE_SLACK_DAYS = 30
# Title matches only count if the target commit is at most this much older than the upstream commit
# (it may be older when the change was written in the fork first and upstreamed later).
TITLE_MAX_EARLIER_DAYS = 180


def days_between(earlier: str, later: str) -> int:
    return (dt.date.fromisoformat(later) - dt.date.fromisoformat(earlier)).days


# --------------------------------------------------------------------------- git helpers


def git(*args: str, cwd: Path, check: bool = True) -> str:
    proc = subprocess.run(
        ["git", *args],
        cwd=cwd,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    if check and proc.returncode != 0:
        sys.exit(f"git {' '.join(args)} failed:\n{proc.stderr.strip()}")
    return proc.stdout


def git_ok(*args: str, cwd: Path) -> bool:
    return subprocess.run(["git", *args], cwd=cwd, capture_output=True).returncode == 0


# --------------------------------------------------------------------------- model


def norm_title(text: str) -> str:
    """Normalize a commit title for comparison (case, PR refs, conventional prefix, quotes)."""
    s = CONVENTIONAL_RE.sub("", text.strip())
    s = re.sub(r"\s*\((?:[\w.-]+/[\w.-]+)?#\d+\)", "", s)
    s = re.sub(r"[`\"'“”‘’]", "", s)
    s = re.sub(r"\s+", " ", s).strip().rstrip(".").strip()
    return s.lower()


def similar(a: str, b: str) -> bool:
    if not a or not b:
        return False
    if a == b:
        return True
    shorter, longer = sorted((a, b), key=len)
    if len(shorter) >= 12 and shorter in longer:
        return True
    return difflib.SequenceMatcher(None, a, b).ratio() >= SIMILARITY


@dataclass
class Commit:
    full: str
    date: str
    author: str
    author_email: str
    committer: str
    committer_email: str
    message: str
    repo: str = ""  # upstream name, e.g. "mihon"
    subject: str = field(init=False)
    norm: str = field(init=False)
    prs: list[tuple[str | None, int]] = field(init=False)

    def __post_init__(self) -> None:
        lines = [line for line in self.message.splitlines() if line.strip()]
        self.subject = lines[0].strip() if lines else ""
        self.norm = norm_title(self.subject)
        self.prs = [(m.group(1), int(m.group(2))) for m in SUBJECT_PR_RE.finditer(self.subject)]

    @property
    def short(self) -> str:
        return self.full[:SHORT_LEN]

    def identities(self) -> list[str]:
        co_authors = CO_AUTHOR_RE.findall(self.message)
        return [self.author, self.author_email, self.committer, self.committer_email, *co_authors]


def parse_log(text: str, repo: str = "") -> list[Commit]:
    commits = []
    for record in text.split("\x1e"):
        record = record.strip("\n")
        if not record:
            continue
        parts = record.split("\x1f")
        if len(parts) < 7:
            continue
        commits.append(Commit(*parts[:6], "\x1f".join(parts[6:]), repo=repo))
    return commits


@dataclass
class Upstream:
    name: str
    remote: str
    branch: str
    github: str

    @property
    def ref(self) -> str:
        return f"{self.remote}/{self.branch}"


@dataclass
class Config:
    target_branch: str
    since: str
    output: str
    exclude_identity_regex: str
    main_upstream: Upstream
    additional_upstreams: list[Upstream]

    @property
    def upstreams(self) -> list[Upstream]:
        """The main upstream first, then the additional ones (config order)."""
        return [self.main_upstream, *self.additional_upstreams]

    @staticmethod
    def load(path: Path) -> Config:
        raw = json.loads(path.read_text(encoding="utf-8"))
        if "main_upstream" not in raw:
            sys.exit("config: `main_upstream` is required ({name, remote, branch, github})")
        main = Upstream(**raw["main_upstream"])
        additional = [Upstream(**u) for u in raw.get("additional_upstreams", [])]
        if not additional:
            sys.exit("config: `additional_upstreams` needs at least one upstream")
        names = [u.name for u in (main, *additional)]
        if len(set(names)) != len(names):
            sys.exit(f"config: upstream names must be unique: {names}")
        return Config(
            target_branch=raw.get("target_branch", "master"),
            since=raw.get("since", ""),
            output=raw.get("output", "cherrypick_log.md"),
            exclude_identity_regex=raw.get("exclude_identity_regex", ""),
            main_upstream=main,
            additional_upstreams=additional,
        )


# --------------------------------------------------------------------------- target index


class TargetIndex:
    """Everything known about the fork's target branch that can prove a commit was picked."""

    def __init__(self, root: Path, ref: str, githubs: list[str]) -> None:
        self.reachable = set(git("rev-list", ref, cwd=root).split())
        self.hash_full: dict[str, str] = {}
        self.hash_prefix: dict[str, str] = {}
        self.pr_lines: dict[tuple[str, int], list[tuple[str, str]]] = defaultdict(list)
        self.titles: dict[str, list[tuple[str, str, str]]] = defaultdict(list)  # -> (short, author, date)
        known = {g.lower() for g in githubs}

        for c in parse_log(git("log", ref, f"--format={LOG_FORMAT}", cwd=root)):
            for h in CHERRY_RE.findall(c.message) + [m[1] for m in COMMIT_URL_RE.findall(c.message)]:
                h = h.lower()
                (self.hash_full if len(h) == 40 else self.hash_prefix).setdefault(h, c.short)
            for line in c.message.splitlines():
                text = BULLET_RE.sub("", line).strip()
                if not text or text.startswith("#"):
                    continue
                for m in QUALIFIED_REF_RE.finditer(text):
                    gh, num = (m.group(1), m.group(2)) if m.group(1) else (m.group(3), m.group(4))
                    if gh.lower() not in known or CLOSING_BEFORE_RE.search(text[: m.start()]):
                        continue
                    self.pr_lines[(gh.lower(), int(num))].append((c.short, norm_title(text)))
                n = norm_title(text)
                if len(n.split()) >= MIN_TITLE_WORDS:
                    self.titles[n].append((c.short, c.author, c.date))

    def picked_as(self, full: str) -> str | None:
        if full in self.hash_full:
            return self.hash_full[full]
        for prefix, short in self.hash_prefix.items():
            if full.startswith(prefix):
                return short
        return None


# --------------------------------------------------------------------------- existing log


@dataclass
class Row:
    full: str
    github: str
    cells: list[str]  # raw (escaped) cell text, len == len(COLUMNS)

    @property
    def status(self) -> str:
        return self.cells[0]

    @property
    def notes(self) -> str:
        return self.cells[-1]


def split_cells(line: str) -> list[str]:
    parts = CELL_SPLIT_RE.split(line.strip())
    return [p.strip() for p in parts[1:-1]]


def parse_rows(path: Path) -> list[tuple[int, Row]]:
    """Return (line_index, Row) for every commit row in an existing log file."""
    if not path.exists():
        return []
    rows = []
    for i, line in enumerate(path.read_text(encoding="utf-8").splitlines()):
        if not line.startswith("|"):
            continue
        m = ROW_LINK_RE.search(line)
        if not m:
            continue
        cells = split_cells(line)
        if len(cells) != len(COLUMNS):
            print(f"warning: skipping malformed row on line {i + 1}", file=sys.stderr)
            continue
        rows.append((i, Row(full=m.group(2), github=m.group(1), cells=cells)))
    return rows


def esc(text: str) -> str:
    return text.replace("\n", " ").replace("|", "\\|").replace("<", "&lt;").strip()


def unesc(text: str) -> str:
    return text.replace("\\|", "|").replace("&lt;", "<")


def format_row(cells: list[str]) -> str:
    # An empty Status is written as one space, so it is as wide as "O" / "?" / "X" and the raw table stays aligned.
    return "| " + " | ".join([cells[0] or " ", *cells[1:]]) + " |"


# --------------------------------------------------------------------------- update


@dataclass
class Entry:
    commit: Commit
    upstream: Upstream
    kind: str  # "primary" | "copy" | "own"
    cluster: str  # cluster key: the primary commit's hash, or the own commit's hash
    details: list[str] = field(default_factory=list)
    status: str = ""
    pre_pick: bool = False  # own commit that copies a primary commit older than `since`


class Builder:
    def __init__(self, root: Path, cfg: Config) -> None:
        self.root = root
        self.cfg = cfg
        self.exclude = re.compile(cfg.exclude_identity_regex, re.I) if cfg.exclude_identity_regex else None
        self.primary = cfg.upstreams[0]
        self.secondaries = cfg.upstreams[1:]

    def excluded(self, c: Commit) -> bool:
        return bool(self.exclude) and any(self.exclude.search(i or "") for i in c.identities())

    def log(self, up: Upstream, since: str | None) -> list[Commit]:
        args = ["log", up.ref, "--no-merges", "--topo-order", f"--format={LOG_FORMAT}"]
        if since:
            args.append(f"--since={since}")
        commits = parse_log(git(*args, cwd=self.root), repo=up.name)
        return [c for c in commits if not since or c.date >= since]

    # -- pairing secondary -> primary -------------------------------------------------

    def index_primary(self) -> None:
        self.prim_all = self.log(self.primary, None)
        self.prim_by_full = {c.full: c for c in self.prim_all}
        self.prim_pos = {c.full: i for i, c in enumerate(self.prim_all)}
        self.prim_by_pr: dict[int, list[Commit]] = defaultdict(list)
        self.prim_by_title: dict[str, list[Commit]] = defaultdict(list)
        own = self.primary.github.lower()
        for c in self.prim_all:
            for gh, num in c.prs:
                if gh is None or gh.lower() == own:
                    self.prim_by_pr[num].append(c)
            self.prim_by_title[c.norm].append(c)
        self.prim_excluded = {c.full for c in self.prim_all if self.excluded(c)}
        self.prim_in_range = [
            c for c in self.prim_all if c.date >= self.cfg.since and c.full not in self.prim_excluded
        ]
        self.prim_in_range_set = {c.full for c in self.prim_in_range}

    def resolve_primary_hash(self, h: str) -> Commit | None:
        h = h.lower()
        if h in self.prim_by_full:
            return self.prim_by_full[h]
        if len(h) < 40:
            for full, c in self.prim_by_full.items():
                if full.startswith(h):
                    return c
        return None

    def pair(self, s: Commit) -> tuple[list[Commit], str]:
        found = [p for p in map(self.resolve_primary_hash, CHERRY_RE.findall(s.message)) if p]
        if found:
            return list({p.full: p for p in found}.values()), "marker"
        own = self.primary.github.lower()
        for gh, num in s.prs:
            if gh is not None and gh.lower() != own:
                continue
            hits = [p for p in self.prim_by_pr.get(num, []) if similar(p.norm, s.norm)]
            if hits:
                return hits, "pr+title"
        if len(s.norm.split()) >= MIN_TITLE_WORDS:
            same = self.prim_by_title.get(s.norm, [])
            if len(same) == 1 and same[0].author == s.author and days_between(s.date, same[0].date) <= PAIR_DATE_SLACK_DAYS:
                return same, "title+author"
        return [], ""

    # -- build ordered entries ----------------------------------------------------------

    def build(self) -> list[Entry]:
        self.index_primary()
        primary_entries = {c.full: Entry(c, self.primary, "primary", c.full) for c in self.prim_in_range}
        attached: dict[str, list[tuple[int, int, Entry]]] = defaultdict(list)
        bottom: list[tuple[int, int, Entry]] = []
        self.members: dict[str, list[Entry]] = defaultdict(list)
        for e in primary_entries.values():
            self.members[e.cluster].append(e)

        for sec_no, up in enumerate(self.secondaries):
            commits = [c for c in self.log(up, self.cfg.since) if not self.excluded(c)]
            anchor: str | None = None
            for pos in range(len(commits) - 1, -1, -1):  # oldest -> newest
                s = commits[pos]
                mapped, how = self.pair(s)
                if any(p.full in self.prim_excluded for p in mapped):
                    continue  # copy of an excluded (e.g. Renovate) commit
                in_range = sorted(
                    (p for p in mapped if p.full in self.prim_in_range_set), key=lambda p: self.prim_pos[p.full]
                )
                if in_range:
                    target = in_range[0].full  # newest of the picked primary commits
                    entry = Entry(s, up, "copy", target)
                    picked = ", ".join(f"`{p.short}`" for p in in_range)
                    entry.details.append(f"pick of {self.primary.name} {picked} [{how}]")
                    for p in in_range:
                        self.members[p.full].append(entry)
                    attached[target].append((sec_no, pos, entry))
                    anchor = target
                    continue
                entry = Entry(s, up, "own", s.full)
                if mapped:
                    picked = ", ".join(f"`{p.short}`" for p in mapped)
                    entry.details.append(f"pick of {self.primary.name} {picked} (before {self.cfg.since}) [{how}]")
                    entry.pre_pick = True
                self.members[entry.cluster].append(entry)
                (attached[anchor] if anchor else bottom).append((sec_no, pos, entry))

        ordered: list[Entry] = []
        for p in self.prim_in_range:
            ordered += [e for _, _, e in sorted(attached.get(p.full, []), key=lambda t: (t[0], t[1]))]
            ordered.append(primary_entries[p.full])
        ordered += [e for _, _, e in sorted(bottom, key=lambda t: (t[0], t[1]))]
        return ordered

    # -- detection in the target branch ----------------------------------------------

    def detect(self, entries: list[Entry]) -> None:
        target = TargetIndex(self.root, self.cfg.target_branch, [u.github for u in self.cfg.upstreams])
        cluster_title: dict[str, str] = {}
        for e in entries:
            if e.kind in ("primary", "own"):
                cluster_title[e.cluster] = e.commit.norm
        title_count = Counter(cluster_title.values())
        results: dict[str, tuple[str, list[str]]] = {}

        branch = self.cfg.target_branch
        for key, members in self.members.items():
            found: list[tuple[str, str, str]] = []  # (status, target commit, method)
            head = next((m for m in members if m.kind in ("primary", "own")), members[0])
            for m in members:
                c = m.commit
                if c.full in target.reachable:
                    found.append((STATUS_DONE, c.short, f"in history as {m.upstream.name}"))
                picked = target.picked_as(c.full)
                if picked:
                    found.append((STATUS_DONE, picked, f"hash of {m.upstream.name}"))

            pr_keys = []
            for gh, num in head.commit.prs:
                if gh is None:
                    is_pick = head.kind == "primary" or head.pre_pick
                    gh = self.primary.github if is_pick else head.upstream.github
                pr_keys.append((gh.lower(), num))
            for gh, num in pr_keys:
                for short, line in target.pr_lines.get((gh, num), []):
                    if similar(line, head.commit.norm):
                        found.append((STATUS_DONE, short, f"pr+title {gh}#{num}"))
                    else:
                        found.append((STATUS_MAYBE, short, f"pr only {gh}#{num}"))

            title = cluster_title.get(key, head.commit.norm)
            if title_count[title] <= 1 and len(title.split()) >= MIN_TITLE_WORDS:
                authors = {m.commit.author for m in members}
                newest = max(m.commit.date for m in members)
                for short, author, date in target.titles.get(title, []):
                    if days_between(date, newest) > TITLE_MAX_EARLIER_DAYS:
                        continue
                    if author in authors:
                        found.append((STATUS_DONE, short, "title+author"))
                    else:
                        found.append((STATUS_MAYBE, short, "title"))

            if not found:
                results[key] = ("", [])
                continue
            status = STATUS_DONE if any(f[0] == STATUS_DONE for f in found) else STATUS_MAYBE
            by_target: dict[str, list[str]] = defaultdict(list)
            for st, short, method in found:
                if st == status and method not in by_target[short]:
                    by_target[short].append(method)
            texts = [f"{branch} `{short}` [{', '.join(methods)}]" for short, methods in by_target.items()]
            results[key] = (status, texts[:3] + ([f"+{len(texts) - 3} more"] if len(texts) > 3 else []))

        for e in entries:
            e.status, evidence = results.get(e.cluster, ("", []))
            e.details += evidence


def flag_revert_chains(rendered: list[tuple[Entry, list[str]]]) -> int:
    """Flag commits that cancel each other out (``This reverts commit …``) as '?' for review.

    Works on clusters (a primary commit plus its secondary copies). Revert links form chains
    (C <- revert C <- revert of the revert ...). A chain is only touched if it is linear, every
    row in it is still empty / '?' (the fork has none of them) and none has an AI verdict. An even-length chain cancels out
    completely; in an odd-length chain the oldest commit carries the net change and is not flagged.
    Flagged rows get '?' and a ``Cancels out (script):`` note; a human (or the AI review) decides.
    Rows this function flagged on an earlier run are released first by ``release_script_flag``, so
    chains are always recomputed (e.g. when a later revert extends one). Returns the number of
    rows changed.
    """
    by_hash: dict[str, str] = {}
    cluster_rows: dict[str, list[list[str]]] = defaultdict(list)
    cluster_head: dict[str, Commit] = {}
    for e, cells in rendered:
        by_hash[e.commit.full] = e.cluster
        cluster_rows[e.cluster].append(cells)
        if e.kind != "copy":
            cluster_head[e.cluster] = e.commit

    def resolve(h: str) -> str | None:
        h = h.lower()
        if h in by_hash:
            return by_hash[h]
        hits = {k for full, k in by_hash.items() if full.startswith(h)}
        return hits.pop() if len(hits) == 1 else None

    reverts: dict[str, set[str]] = defaultdict(set)  # cluster -> clusters it reverts
    for e, _ in rendered:
        for h in REVERT_RE.findall(e.commit.message):
            target = resolve(h)
            if target and target != e.cluster:
                reverts[e.cluster].add(target)
    reverted_by: dict[str, set[str]] = defaultdict(set)
    for r, targets in reverts.items():
        for t in targets:
            reverted_by[t].add(r)

    changed = 0
    done: set[str] = set()
    for root in list(cluster_head):
        if root in done or root in reverts or root not in reverted_by:
            continue  # start from an original commit that is reverted but reverts nothing
        chain = [root]
        while True:
            nxt = reverted_by.get(chain[-1], set())
            if not nxt:
                break
            if len(nxt) > 1 or len(reverts.get(next(iter(nxt)), set())) > 1 or next(iter(nxt)) in chain:
                chain = []  # branching or cyclic history: leave it to a human
                break
            chain.append(next(iter(nxt)))
        done.update(chain)
        if len(chain) < 2 or any(c not in cluster_head for c in chain):
            continue
        if any(cells[0] not in RECHECKED_STATUSES for c in chain for cells in cluster_rows[c]):
            continue
        if any(AI_NOTE_MARK in unesc(cells[-1]) for c in chain for cells in cluster_rows[c]):
            continue  # an AI-reviewed row: its verdict wins, a human decides
        flagged = chain if len(chain) % 2 == 0 else chain[1:]
        for older, newer in zip(flagged[::2], flagged[1::2]):
            for key, text in ((older, f"reverted by `{cluster_head[newer].short}`"), (newer, f"reverts `{cluster_head[older].short}`")):
                for cells in cluster_rows[key]:
                    note = esc(f"{REVERT_NOTE_MARK} {text} [was: {cells[0] or 'empty'}]")
                    cells[0] = STATUS_MAYBE
                    cells[-1] = note if not cells[-1] else f"{cells[-1]}; {note}"
                    changed += 1
    return changed


def release_script_flag(status: str, notes: str) -> tuple[str, str]:
    """Undo a previous run's revert-chain flag so the chain can be recomputed.

    A row is script-owned when its Notes contain a ``Cancels out (script):`` part (or the older
    ``Omitted (script):``), its status is the one the script set ('?', or X for the older note) and it
    has no AI verdict. The script's part is removed and the status recorded in it (``[was: …]``) is
    restored: only '?' or empty, and '?' wins if several parts disagree; anything else, or a note
    without the record, gives empty.

    - No script part, or an AI verdict (``AI checked``): returned unchanged.
    - Status changed by hand (e.g. O or a manual X on a new-style flag): the stale script part is
      removed and the status kept.

    Only the script's text is removed: from its mark to the end of its ``;``-separated part, plus one
    separator. Any user text before the mark in the same part, and the rest of the note, is kept byte
    for byte (``;`` inside an ``&lt;`` entity is not a separator).
    """
    if not any(mark in notes for mark in SCRIPT_NOTE_MARKS) or AI_NOTE_MARK in unesc(notes):
        return status, notes
    pieces = NOTE_SPLIT_RE.split(notes)  # [part, sep, part, sep, part]
    parts, seps = pieces[0::2], pieces[1::2]
    previous, legacy, out = "", False, ""
    for i, part in enumerate(parts):
        idx = min((part.find(mark) for mark in SCRIPT_NOTE_MARKS if mark in part), default=-1)
        if idx >= 0:
            legacy = legacy or REVERT_NOTE_MARK not in part
            m = OMIT_WAS_RE.search(part[idx:])
            if m and m.group(1) == STATUS_MAYBE:
                previous = STATUS_MAYBE
            part = part[:idx].rstrip(" ,-")
            if part.endswith(";") and not part.endswith("&lt;"):
                part = part[:-1].rstrip(" ,-")
            if not part:
                continue
        if out:
            out += seps[i - 1]
        out += part
    owned = status == (STATUS_SKIP if legacy else STATUS_MAYBE)
    return (previous if owned else status), out.strip()


def carry_over(old_status: str, old_notes: str, computed: str) -> tuple[str, str]:
    """Status and Notes of a regenerated row, from its previous row and this run's detection.

    Script revert-chain flags are released first. A manual status (O, X, …) is kept. An empty / '?' row is
    re-checked (``computed``), except that an AI verdict (Notes with ``AI checked``) is kept unless
    strong O evidence appears.
    """
    status, notes = release_script_flag(old_status, old_notes)
    if status not in RECHECKED_STATUSES:
        return status, notes
    if AI_NOTE_MARK in unesc(notes) and computed != STATUS_DONE:
        return status, notes
    return computed, notes


def cmd_update(root: Path, cfg: Config, args: argparse.Namespace) -> None:
    if not args.no_fetch:
        for up in cfg.upstreams:
            print(f"fetching {up.ref} ...", file=sys.stderr)
            git("fetch", "--quiet", up.remote, up.branch, cwd=root)

    builder = Builder(root, cfg)
    entries = builder.build()

    out = root / cfg.output
    old_rows = {r.full: r for _, r in parse_rows(out)}
    gh_to_up = {u.github.lower(): u for u in cfg.upstreams}
    new_hashes = {e.commit.full for e in entries}

    # Rows dated before `since` (e.g. added by a one-off pass over older history) are kept as they are,
    # at the end of the list. They are re-checked against the target branch like the generated rows.
    pre_rows: dict[str, tuple[Row, Upstream]] = {}
    gone_rows: list[Row] = []
    for full, row in old_rows.items():
        if full in new_hashes:
            continue
        up = gh_to_up.get(row.github.lower())
        if not (up and git_ok("merge-base", "--is-ancestor", full, up.ref, cwd=root)):
            gone_rows.append(row)
        elif row.cells[3] < cfg.since:
            pre_rows[full] = (row, up)
        # else: still upstream but filtered out now (config change) -> drop
    pre_entries: list[Entry] = []
    if pre_rows:
        log = git("log", "--no-walk=unsorted", f"--format={LOG_FORMAT}", *pre_rows, cwd=root)
        for c in parse_log(log):
            up = pre_rows[c.full][1]
            c.repo = up.name
            entry = Entry(c, up, "primary" if up is builder.primary else "own", c.full)
            builder.members[entry.cluster].append(entry)
            pre_entries.append(entry)
    builder.detect(entries + pre_entries)

    rendered: list[tuple[Entry, list[str]]] = []  # (entry, cells) in list order
    added = 0
    for e in entries:
        c = e.commit
        old = old_rows.get(c.full)
        status, notes = e.status, ""
        if old is None:
            added += 1
        else:
            status, notes = carry_over(old.status, old.notes, e.status)
        label = f"↳ {e.upstream.name}" if e.kind == "copy" else e.upstream.name
        rendered.append(
            (
                e,
                [
                    status,
                    f"[{c.short}](https://github.com/{e.upstream.github}/commit/{c.full})",
                    label,
                    c.date,
                    esc(c.subject),
                    esc(c.author),
                    esc("; ".join(e.details)),
                    notes,
                ],
            ),
        )

    for e in pre_entries:
        row = pre_rows[e.commit.full][0]
        cells = list(row.cells)
        cells[0], cells[-1] = release_script_flag(cells[0], cells[-1])
        # Only strong evidence changes a kept row; its '?' may come from the pass that added it.
        if cells[0] in RECHECKED_STATUSES and e.status == STATUS_DONE:
            cells[0] = STATUS_DONE
            evidence = [d for d in e.details if d not in unesc(cells[6])]
            cells[6] = esc("; ".join([unesc(cells[6]), *evidence]).strip("; "))
        rendered.append((e, cells))

    flag_revert_chains(rendered)
    changed = sum(
        1 for e, cells in rendered if e.commit.full in old_rows and old_rows[e.commit.full].status != cells[0]
    )

    lines: list[str] = []
    counts: dict[str, Counter] = defaultdict(Counter)
    for e, cells in rendered:
        counts[e.upstream.name][cells[0] or "empty"] += 1
        lines.append(format_row(cells))

    gone: list[str] = []
    for row in gone_rows:
        cells = list(row.cells)
        if "gone from upstream" not in cells[6]:
            cells[6] = (cells[6] + "; " if cells[6] else "") + "gone from upstream"
        gone.append(format_row(cells))

    heads = ", ".join(
        f"{u.name} `{u.ref}` @ `{git('rev-parse', '--short=10', u.ref, cwd=root).strip()}`" for u in cfg.upstreams
    )
    summary = ["| Upstream | O | ? | X | empty | other | total |", "|---|--:|--:|--:|--:|--:|--:|"]
    for up in cfg.upstreams:
        cnt = counts[up.name]
        other = sum(v for k, v in cnt.items() if k not in (STATUS_DONE, STATUS_MAYBE, STATUS_SKIP, "empty"))
        summary.append(
            f"| {up.name} | {cnt[STATUS_DONE]} | {cnt[STATUS_MAYBE]} | {cnt[STATUS_SKIP]} | {cnt['empty']} | {other} | {sum(cnt.values())} |",
        )

    header = [
        "# Upstream cherry-pick log",
        "",
        f"<!-- Generated by .claude/skills/cherrypick-log/update_cherrypick_log.py - see {README_REL}. -->",
        "<!-- Only edit the Status and Notes columns; everything else is regenerated on every run. -->",
        "",
        f"Commits from main upstream **{cfg.main_upstream.name}** and additional "
        f"{', '.join(f'**{u.name}**' for u in cfg.additional_upstreams)} since **{cfg.since}**"
        + (f" (authors matching `{cfg.exclude_identity_regex}` excluded)" if cfg.exclude_identity_regex else "")
        + f", checked against `{cfg.target_branch}`.",
        f"Last updated **{dt.date.today().isoformat()}** from {heads}.",
        f"How to update / review: [{README_REL}]({README_REL}).",
        "",
        f"**Status:** `{STATUS_DONE}` cherry-picked · `{STATUS_MAYBE}` maybe, needs review (includes commits that "
        f"cancel each other out, flagged by the script) · empty = not found · `{STATUS_SKIP}` won't pick (set by hand, "
        f"or version-bump-only commits). **Upstream** `↳ name` = copy of the {builder.primary.name} commit "
        "right below it. **Details** = how pairing / status was found (regenerated); **Notes** = yours, kept.",
        "",
        *summary,
        "",
        "## Commits",
        "",
        format_row(COLUMNS),
        "|:-:|---|---|---|---|---|---|---|",
        *lines,
    ]
    if gone:
        header += ["", "## Gone from upstream", "", format_row(COLUMNS), "|:-:|---|---|---|---|---|---|---|", *gone]

    text = "\n".join(header) + "\n"
    if args.dry_run:
        print(text if args.verbose else "\n".join(summary))
    else:
        out.write_text(text, encoding="utf-8")
    print(
        f"{'(dry run) ' if args.dry_run else ''}{out}: {len(entries)} commits, "
        f"{added} new, {changed} status changes, {len(gone)} gone",
        file=sys.stderr,
    )


# --------------------------------------------------------------------------- uncertain / mark


def cmd_uncertain(root: Path, cfg: Config, args: argparse.Namespace) -> None:
    wanted = {STATUS_MAYBE} | ({""} if args.include_empty else set())
    title_re = re.compile(args.grep, re.I) if args.grep else None
    shown = 0
    for _, row in parse_rows(root / cfg.output):
        if row.status not in wanted:
            continue
        notes = unesc(row.notes)
        if AI_NOTE_MARK in notes and not args.include_reviewed:
            continue
        repo = row.cells[2].replace("↳", "").strip()
        if args.repo and repo != args.repo:
            continue
        if title_re and not title_re.search(unesc(row.cells[4])):
            continue
        item = {
            "status": row.status,
            "hash": row.full,
            "upstream": repo,
            "copy": row.cells[2].startswith("↳"),
            "date": row.cells[3],
            "title": unesc(row.cells[4]),
            "author": unesc(row.cells[5]),
            "details": unesc(row.cells[6]),
            "notes": notes,
        }
        if args.json:
            print(json.dumps(item, ensure_ascii=False))
        else:
            print(f"[{item['status'] or ' '}] {row.full[:SHORT_LEN]} {repo:<12} {item['date']} {item['title']}")
            if item["details"]:
                print(f"      details: {item['details']}")
            if notes:
                print(f"      notes:   {notes}")
        shown += 1
        if args.limit and shown >= args.limit:
            break
    print(f"{shown} row(s)", file=sys.stderr)


def cmd_mark(root: Path, cfg: Config, args: argparse.Namespace) -> None:
    out = root / cfg.output
    rows = [(i, r) for i, r in parse_rows(out) if r.full.startswith(args.hash.lower())]
    if not rows:
        sys.exit(f"no row matches {args.hash}")
    if len(rows) > 1:
        sys.exit(f"{args.hash} is ambiguous ({len(rows)} rows), use a longer prefix")
    line_no, row = rows[0]
    if row.status not in RECHECKED_STATUSES and not args.force:
        sys.exit(f"row {row.full[:SHORT_LEN]} has status '{row.status}'; pass --force to change it")
    cells = list(row.cells)
    if args.status is not None:
        cells[0] = "" if args.status == "none" else args.status
    if args.note:
        note = esc(args.note)
        cells[-1] = note if args.replace_note or not cells[-1] else f"{cells[-1]}; {note}"
    text_lines = out.read_text(encoding="utf-8").splitlines()
    text_lines[line_no] = format_row(cells)
    out.write_text("\n".join(text_lines) + "\n", encoding="utf-8")
    print(f"{row.full[:SHORT_LEN]}: status '{row.status}' -> '{cells[0]}'", file=sys.stderr)


# --------------------------------------------------------------------------- main


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--config", type=Path, default=DEFAULT_CONFIG, help="config file (default: %(default)s)")
    parser.add_argument(
        "--since",
        help="first commit date to list, YYYY-MM-DD (overrides `since` in the config; it is per repo)",
    )
    sub = parser.add_subparsers(dest="command")

    p_update = sub.add_parser("update", help="fetch upstreams and regenerate the log (default)")
    p_update.add_argument("--no-fetch", action="store_true", help="use the remote-tracking refs as they are")
    p_update.add_argument("--dry-run", action="store_true", help="do not write the file, print the summary")
    p_update.add_argument("--verbose", action="store_true", help="with --dry-run, print the whole file")

    p_unc = sub.add_parser("uncertain", help="list '?' rows (and optionally empty rows) to review")
    p_unc.add_argument("--include-empty", action="store_true", help="also list rows with an empty status")
    p_unc.add_argument("--include-reviewed", action="store_true", help=f"include rows with an '{AI_NOTE_MARK}' note")
    p_unc.add_argument("--repo", help="only this upstream name (as in the config)")
    p_unc.add_argument("--limit", type=int, default=0, help="at most N rows")
    p_unc.add_argument("--grep", help="only rows whose title matches this regex (case-insensitive)")
    p_unc.add_argument("--json", action="store_true", help="one JSON object per line")

    p_mark = sub.add_parser("mark", help="set the Status and/or Notes of one row")
    p_mark.add_argument("hash", help="upstream commit hash (prefix)")
    p_mark.add_argument("--status", choices=[STATUS_DONE, STATUS_MAYBE, STATUS_SKIP, "none"])
    p_mark.add_argument("--note", help="text to append to Notes")
    p_mark.add_argument("--replace-note", action="store_true", help="replace Notes instead of appending")
    p_mark.add_argument("--force", action="store_true", help="allow changing an O / X row")

    args = parser.parse_args()
    if args.command is None:
        args = parser.parse_args(["--config", str(args.config), *(["--since", args.since] if args.since else []), "update"])

    root = Path(git("rev-parse", "--show-toplevel", cwd=Path.cwd()).strip())
    cfg = Config.load(args.config)
    if args.since:
        cfg.since = args.since
    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", cfg.since or ""):
        sys.exit("a start date is required: set `since` (YYYY-MM-DD) in the config or pass --since")
    {"update": cmd_update, "uncertain": cmd_uncertain, "mark": cmd_mark}[args.command](root, cfg, args)


if __name__ == "__main__":
    main()
