# Upstream cherry-pick log

Tracks which upstream commits still need to be cherry-picked into this fork.

- **Output:** [`cherrypick_log.md`](../../../cherrypick_log.md) in the repo root, a Markdown table with one row per upstream commit.
- **Generator:** `update_cherrypick_log.py`. This is deterministic Python 3.9+ that needs only the standard library and `git`.
- **AI review:** `SKILL.md`, a Claude Code skill (`/cherrypick-log`). It runs the generator and then resolves the
  rows the generator couldn't decide (`?`) by comparing code.
- **Settings:** `config.json`.

The script decides everything that has a mechanical answer: the list itself, its order, the filters, matching
by hash or PR, and keeping manual marks. The AI handles only the ambiguous rows, and it writes through the
script's `mark` command, so the table can't be corrupted.

## Quick start

```bash
# 1. Fetch upstreams and regenerate cherrypick_log.md (safe to run any time)
python3 .claude/skills/cherrypick-log/update_cherrypick_log.py

# 2. In Claude Code, let the AI review the uncertain rows
/cherrypick-log review the ? rows

# 3. Pick commits, then mark them yourself (or edit the Status / Notes cells by hand)
python3 .claude/skills/cherrypick-log/update_cherrypick_log.py mark 1a2b3c4d --status O
python3 .claude/skills/cherrypick-log/update_cherrypick_log.py mark 5e6f7a8b --status X --note "SY-only release bump"
```

The upstream remotes named in `config.json` must exist. In Komikku these are `git remote add mihon https://github.com/mihonapp/mihon`
and `git remote add tachiyomiSY https://github.com/jobobby04/TachiyomiSY`. The script fetches them itself
unless you pass `--no-fetch`.

## Commands

| Command | What it does |
|---------|--------------|
| `update` (default) | Fetch upstreams, rebuild the list, keep manual marks and Notes. Options: `--no-fetch`, `--dry-run`, `--verbose` |
| `uncertain` | Print `?` rows that haven't been AI-reviewed yet. Options: `--include-empty`, `--include-reviewed`, `--repo NAME`, `--grep REGEX` (title), `--details REGEX` (Details, e.g. `'partial:'`), `--limit N`, `--json` |
| `mark HASH` | Set one row's status (`--status O\|?\|X\|none`) and/or append a note (`--note`, `--replace-note`). Refuses to change `O`/`X` rows unless you pass `--force` |

All commands accept `--config PATH` (default: `config.json` next to the script), `--since YYYY-MM-DD`
(overrides the config's start date for this run, e.g. `... --since 2024-01-01 update`) and `--target BRANCH` (overrides
`target_branch` for this run, e.g. `... --target my-feature update` to check a branch that is not merged into `master` yet).
They go before the command. Quote `?` in zsh (`--status '?'`).

## Columns and statuses

| Column | Meaning |
|--------|---------|
| Status | `O` cherry-picked into the target branch · `?` maybe, needs review (including commits that cancel each other out, see [Revert chains](#revert-chains-flagged-)) · empty (written as one space so the raw table stays aligned) = not found · `X` won't pick (set by hand, or version-bump-only commits, see [Omitted commits](#omitted-commits-x)) |
| Commit | Short hash, linked to the full GitHub commit. The script reads the full hash back from this link, so don't edit it |
| Upstream | `mihon`, `tachiyomiSY`, or `↳ tachiyomiSY` for SY's copy of the mihon commit directly below it |
| Date | Committer date, i.e. when the commit landed on that upstream branch (ISO) |
| Title / Author | First line of the commit message and its author |
| Details | Generated on every run: why the row is listed (`repo-wide: …`, only with [path filters](#path-filters-optional)), how it was paired and how its status was found (see below) |
| Notes | Yours. Kept across runs. AI notes start with `AI checked <date>:` |

Only **Status** and **Notes** are yours to edit. Every other cell, the order of the rows and the header are rewritten on each run.

## Rules

### Which commits are listed

- Non-merge commits on each upstream branch whose committer date is on or after `since`.
- A commit is excluded if its author, committer or any `Co-authored-by` matches `exclude_identity_regex` (Renovate).
  An SY copy of an excluded mihon commit is excluded too.
- Weblate translation commits are included. Mark them `X` if you don't want them.
- Optionally, an upstream's commits that only change some paths are left out (see [Path filters](#path-filters-optional)).
  Komikku doesn't use this.

### Path filters (optional)

These per-upstream keys are off in Komikku. They exist for forks that take only part of an upstream, e.g. an extensions
repo that takes the shared code but not the upstream's own sources.

- `exclude_only_paths`: a commit is excluded if every file it changes is under one of these paths. Renames count as a
  delete plus an add, so a file moved out of those paths keeps the commit. A commit that changes no files is kept. A
  secondary copy of an excluded primary commit is judged by its own files and its own upstream's setting.
- `repo_wide_min_modules`: such a commit is kept anyway, as *repo-wide*, when it touches at least this many modules.
  Its Details start with `repo-wide: N modules, only under <paths>`. A file's module is the directory before its first
  nested `src/`, `res/` or `assets/` folder (Gradle / Android layout), else the file's directory. The AI review sorts
  these rows and marks the ones that don't apply `X`.

### Pairing SY commits with mihon commits

`main_upstream` in `config.json` is the **primary** one (mihon in Komikku). Each entry of `additional_upstreams`
is a **secondary** (tachiyomiSY in Komikku), and each secondary is interleaved with the primary on its own.
A secondary commit is a copy of a primary commit if one of these applies, checked in this order (shown as Details `pick of mihon … [method]`):

1. `marker`: its message has `cherry picked from commit <primary hash>`.
2. `pr+title`: its title has the same `(#N)` as a primary commit, and the titles are similar.
3. `title+author`: the normalized title is identical, matches only one primary commit, and has the same author. The date window is one-sided: the secondary copy may land
   any time after the primary commit, but at most 30 days before it (`PAIR_DATE_SLACK_DAYS`; a copy can be picked from
   the PR before upstream merges it).

If an SY commit copies a mihon commit dated before `since`, it is listed as an SY-only commit, with `(before <since>)` in Details.
If it copies a mihon commit dropped by `exclude_only_paths` (and is not dropped itself), Details say
`(not listed: only changes under <paths>)` instead.

### Order (newest first)

- Primary commits keep the primary branch order.
- An SY copy goes **directly above** its mihon original. If one SY commit squashes several mihon commits, it goes above the newest of them.
- An SY-only commit goes directly above the group of the nearest **older** SY commit that copies a mihon commit.
  SY-only commits with no older copy go at the bottom.

Example: mihon `M1, M2` and SY `MS1, S2, S3, MS2` (MS1 copies M1, MS2 copies M2) produce
`MS2, M2, S3, S2, MS1, M1`.

### Status detection (shown as Details ``master `abc` [method]``)

A mihon commit and its SY copies share one status, and evidence for any of them counts for all of them.

| Method | Status | Meaning |
|--------|--------|---------|
| `in history as …` | O | The upstream commit is reachable from the target branch |
| `hash of …` | O | A target commit's message has `cherry picked from commit <hash>` (or a `github.com/…/commit/<hash>` link), and the commits that cite it carry all of its files (see [Partial picks](#partial-picks)) |
| `hash of … (partial: N of M files not picked)` | ? | They cite it, but left out N of the M files that apply here. Title and PR matches on those same commits become `?` too |
| `pr+title owner/repo#N` | O | A target commit line has `owner/repo#N` (or `…/pull/N`) and a similar title. Refs right after `Fixes`, `Closes`, `See` and similar words are ignored |
| `title+author` | O | A target commit line has the same normalized title and the same author |
| `pr only owner/repo#N` | ? | The PR is referenced but the title differs |
| `title` | ? | Same title, different author |

Title matches only count if the title is unique among the listed upstream commits, has at least 3 words,
and the target commit is at most 180 days older than the upstream commit.
The title is normalized before comparing: lowercased, conventional-commit prefix removed, `(#N)` refs and quotes removed.

### Partial picks

`git cherry-pick -x` adds `cherry picked from commit <hash>` even when the pick keeps only part of the commit (a
conflict resolved by dropping a file). For example, `34102027d1` cites mihon `b543bc089a` (Clean up some build
warnings) but carries only 14 of its 21 files. So the script compares files: the files of the upstream commit against
the files changed by every target commit that cites it.

- Only files outside the upstream's `exclude_only_paths` and `partial_ignore_paths`, and inside its
  `partial_check_paths` (when set), count. Komikku sets `partial_ignore_paths` on mihon to `CHANGELOG.md` (Komikku never
  takes mihon's changelog entries, which about 300 mihon commits since 2024 add) and
  `data/src/main/sqldelight/tachiyomi/migrations/` (Komikku numbers its migrations differently, so mihon's `9.sqm` is
  another file here and the pick adds a new migration instead).
- A file the target branch had at the same path before the first citing commit counts as picked only when the citing
  commits changed that path.
- A file it had only in another package (a file of the same name in the same module) counts as picked when they
  changed a file of that name in that module.
- A new file applies when its module was there, or when the citing commits change files in it.
- Anything else doesn't apply here, and doesn't count: a changed or deleted file the target branch didn't have in any
  package (code Komikku dropped or never had), or a new file in a module that neither the target branch nor the
  citing commits have.

When files that apply are left out, the row gets `?` instead of `O`. It turns `O` by itself once later picks that cite
the same hash carry the rest. If the rest will never be picked, mark the row `O` (or `X`) by hand.
Rows already marked `O`, by the script or by hand, skip this check: they stay `O`, and their Details never show
`partial: …`. So the check only affects rows the script hasn't marked `O` yet.

### Revert chains (flagged `?`)

On every `update`, the script flags commits that cancel each other out as `?`, with a
`Cancels out (script): reverts / reverted by <hash> [was: …]` note. You (or the AI review) decide what to do with them,
e.g. mark both `X`.

- They are found by following `This reverts commit <hash>` links between listed commits. A mihon commit and its SY copies
  count as one.
- A chain is only flagged when it is linear, every row in it is still empty or `?` (the fork has none of them), and no
  row has an AI verdict or is a `?` with a `Partial <date>:` / `Folded <date>:` note (part of it is here).
- An even-length chain cancels out completely, so every commit in it is flagged. In an odd-length chain (`C`,
  `Revert C`, `Revert "Revert C"`) the oldest commit carries the net change and is not flagged.
- If the fork already has one side of a revert pair, the pair is not flagged, because the other side may still matter.

Older versions marked these rows `X` with an `Omitted (script):` note. Those rows are turned into `?` flags on the next run.

### Omitted commits (X)

`X` means "won't pick". Apart from your own marks, the AI skill marks one kind of commit `X` when asked to clean the log:
commits whose whole diff is release bookkeeping for that fork (version code/name, release notes, version numbers in issue
templates). Their note is `AI checked <date>: omitted - version bump only (<files>)`. See SKILL.md section 5. With
[path filters](#path-filters-optional), it also marks `X` the `repo-wide:` rows that turn out not to apply
(`AI checked <date>: omitted - not repo-wide (<what it changes>)`).

### Rerunning

- Rows with a status other than empty or `?` (e.g. `O`, a manual `X`) are **never changed**.
- A revert-chain flag (a `?` with a `Cancels out (script):` note, or an older `X` with `Omitted (script):`) is released on
  every run, back to the status recorded in its `[was: …]` part, and the chains are recomputed. So when a later revert
  extends a chain, the odd/even rule applies to the whole chain again. A flagged row is left alone once it has an
  `AI checked` note, or once you change its status (e.g. to `X`). Only the stale script note is removed then.
- Empty and `?` rows are checked again. An empty or `?` row whose Notes contain `AI checked` keeps that AI verdict
  unless new `O` evidence appears.
- A `?` set by hand for a partial pick, with a `Partial <YYYY-MM-DD>:` or `Folded <YYYY-MM-DD>:` note, is kept the same
  way: unless `O` evidence appears. Use `Partial` when you picked only part of a commit, and `Folded` when you folded a
  part of it into the pick of another commit. Detection only sees whole files and the commits citing a hash, so it
  would reset such a row to empty, e.g. while the pick is still on a branch, or when nothing cites the commit. The date
  is required; other notes don't count.
- Notes are always kept, and new upstream commits are added in their place in the order.
- If a commit is no longer reachable from its upstream branch (force-push), it moves to a **Gone from upstream** section
  and keeps its status and notes. Delete it by hand once you no longer need it.
- Rows dated **before `since`** that are already in the file stay at the end of the list, unchanged, in their current
  order. These were added by a one-off pass over older history (in Komikku: the mihon commits before its `since` that
  SY never picked, whose Details start with `pre-<since>`). An empty or `?` row there only changes to `O`, when new `O`
  evidence appears. You may delete such rows by hand; they are not re-added, and the summary simply counts the rows
  that are in the file.
- If a commit is still upstream and dated on or after `since`, but no longer matches the filters (e.g. you changed
  `exclude_identity_regex`, `exclude_only_paths` or `repo_wide_min_modules`), it is dropped. That run checks each
  dropped row with one `git` call, so it can take a minute when thousands of rows go.

## Config (`config.json`)

| Key | Meaning |
|-----|---------|
| `target_branch` | Branch to check for already-picked commits (`master`). The local branch is read, so keep it up to date; `--target` overrides it |
| `since` | First commit date to include, `YYYY-MM-DD` (committer date). Required, and specific to each repo (Komikku: `2024-01-01`); `--since` overrides it |
| `output` | Output Markdown path, relative to the repo root (or absolute) |
| `exclude_identity_regex` | Case-insensitive regex matched against author, committer and co-authors (Komikku: `renovate`). Empty = exclude nothing |
| `main_upstream` | `{name, remote, branch, github}` of the main upstream, plus the optional keys below. Its commits set the order of the list. Required |
| `additional_upstreams` | List of one or more `{name, remote, branch, github}`, each with the optional keys below. Their commits are paired with, and interleaved into, the main upstream's. Names must be unique |
| `exclude_only_paths` | Per upstream, optional. List of repo-relative directories or files (leading / trailing `/` ignored). That upstream's commits that only change files under them are not listed ([Path filters](#path-filters-optional)). Komikku: not set |
| `repo_wide_min_modules` | Per upstream, optional, whole number. A commit dropped by `exclude_only_paths` is kept as repo-wide when it touches at least this many modules. `0` or absent = off. Komikku: not set |
| `partial_check_paths` | Per upstream, optional. List of repo-relative directories or files. A pick of that upstream's commits must carry its files under them to count as `O` ([Partial picks](#partial-picks)). Absent = all files. Komikku: not set |
| `partial_ignore_paths` | Per upstream, optional. List of repo-relative directories or files that a pick needn't carry. Komikku: `CHANGELOG.md` and the SQLDelight `migrations/` folder on mihon |

Tuning constants (similarity threshold, date limits, minimum title words, module folders) are at the top of the script.

## Using this in another project

1. Copy the whole `.claude/skills/cherrypick-log/` folder.
2. Edit `config.json`: the `main_upstream`, one or more `additional_upstreams` (remote, branch, GitHub `owner/repo`),
   target branch, start date (`since`, specific to that repo), excluded identities, output, and the optional path
   keys (excluded paths, repo-wide threshold, partial-pick paths).
3. Add the remotes (`git remote add <name> <url>`) and run the script.
4. Search `README.md` and `SKILL.md` for project-specific wording (mihon, SY, `// KMK` markers, `AGENTS.md`) and adjust it.

The same applies to Komikku's sibling forks. The only hard requirement is that the fork records upstream picks
with `git cherry-pick -x` (`cherry picked from commit …`) or `owner/repo#N` references in commit titles.

## Tests

```bash
python3 -m unittest discover -s .claude/skills/cherrypick-log -p 'test_*.py'
```

The tests in `test_update_cherrypick_log.py` cover revert-chain flags (including when a chain gets longer and the older `X` omissions), releasing
the script's own flags (including notes with escaped characters and user text), manual `X` pins, the rerun status rules
(`carry_over`: manual status kept, AI verdict and a `Partial`/`Folded` `?` kept unless `O` evidence appears), the path
filters (path matching, modules, config checks, and `Builder.build` dropping path-only commits, keeping repo-wide ones
and keeping a copy of a dropped one), the order (copies above their originals, secondary-only commits above older
copies or at the bottom), and partial picks (which files count, ignored paths, a package moved for this repo, a file
kept at the same path, a module the picks add, reading the files a root commit's parent doesn't have, a partial pick
staying `?` even with a matching title until a later pick carries the rest, and rows already `O` skipping the check).
They need no git repository.

## Limitations

- These are heuristics. A reworded, split or hand-ported change can show as empty or `?`. That's what the AI review is for.
- The [partial-pick](#partial-picks) check compares file names, not contents. A citing commit that changes a file
  counts as carrying all of that file's change. A file left out on purpose (a change Komikku reworked in another file)
  still makes the row `?`, and the rest of a pick made by a commit that doesn't cite the hash isn't seen.
- Reverts are only linked through git's default `This reverts commit <hash>` message, and only to commits that are in
  the list. A reworded revert, or a revert of a commit outside the list (before `since`, excluded, or deleted by hand),
  stays an ordinary row.
- Revert chains are only flagged when they are linear, the fork has none of their commits and no row in them has an AI
  verdict (`AI checked`). Branching chains (two reverts of one commit) and pairs where the fork already has one side are
  not flagged.
- One SY commit that squashes several mihon commits is shown only once, above the newest of them.
