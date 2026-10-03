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
| `uncertain` | Print `?` rows that haven't been AI-reviewed yet. Options: `--include-empty`, `--include-reviewed`, `--repo NAME`, `--grep REGEX` (title), `--limit N`, `--json` |
| `mark HASH` | Set one row's status (`--status O\|?\|X\|none`) and/or append a note (`--note`, `--replace-note`). Refuses to change `O`/`X` rows unless you pass `--force` |

All commands accept `--config PATH` (default: `config.json` next to the script) and `--since YYYY-MM-DD`
(overrides the config's start date for this run, e.g. `... --since 2024-01-01 update`). Quote `?` in zsh (`--status '?'`).

## Columns and statuses

| Column | Meaning |
|--------|---------|
| Status | `O` cherry-picked into the target branch · `?` maybe, needs review · empty (written as one space so the raw table stays aligned) = not found · `X` won't pick (set by hand, or omitted automatically, see [Omitted commits](#omitted-commits-x)) |
| Commit | Short hash, linked to the full GitHub commit. The script reads the full hash back from this link, so don't edit it |
| Upstream | `mihon`, `tachiyomiSY`, or `↳ tachiyomiSY` for SY's copy of the mihon commit directly below it |
| Date | Committer date, i.e. when the commit landed on that upstream branch (ISO) |
| Title / Author | First line of the commit message and its author |
| Details | Generated on every run: how the row was paired and how its status was found (see below) |
| Notes | Yours. Kept across runs. AI notes start with `AI checked <date>:` |

Only **Status** and **Notes** are yours to edit. Every other cell, the order of the rows and the header are rewritten on each run.

## Rules

### Which commits are listed

- Non-merge commits on each upstream branch whose committer date is on or after `since`.
- A commit is excluded if its author, committer or any `Co-authored-by` matches `exclude_identity_regex` (Renovate).
  An SY copy of an excluded mihon commit is excluded too.
- Weblate translation commits are included. Mark them `X` if you don't want them.

### Pairing SY commits with mihon commits

`main_upstream` in `config.json` is the **primary** one (mihon in Komikku). Each entry of `additional_upstreams`
is a **secondary** (tachiyomiSY in Komikku), and each secondary is interleaved with the primary on its own.
A secondary commit is a copy of a primary commit if one of these applies, checked in this order (shown as Details `pick of mihon … [method]`):

1. `marker`: its message has `cherry picked from commit <primary hash>`.
2. `pr+title`: its title has the same `(#N)` as a primary commit, and the titles are similar.
3. `title+author`: the normalized title is identical, matches only one primary commit, has the same author, and the dates are within 30 days.

If an SY commit copies a mihon commit dated before `since`, it is listed as an SY-only commit, with `(before <since>)` in Details.

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
| `hash of …` | O | A target commit's message has `cherry picked from commit <hash>` (or a `github.com/…/commit/<hash>` link) |
| `pr+title owner/repo#N` | O | A target commit line has `owner/repo#N` (or `…/pull/N`) and a similar title. Refs right after `Fixes`, `Closes`, `See` and similar words are ignored |
| `title+author` | O | A target commit line has the same normalized title and the same author |
| `pr only owner/repo#N` | ? | The PR is referenced but the title differs |
| `title` | ? | Same title, different author |

Title matches only count if the title is unique among the listed upstream commits, has at least 3 words,
and the target commit is at most 180 days older than the upstream commit.
The title is normalized before comparing: lowercased, conventional-commit prefix removed, `(#N)` refs and quotes removed.

### Omitted commits (X)

`X` means "won't pick". Apart from your own marks, two kinds of commit are marked `X` automatically:

| Who | What | Note |
|-----|------|------|
| Script, on every `update` | Commits that cancel each other out. These are found by following `This reverts commit <hash>` links between listed commits, a mihon commit and its SY copies counting as one. A chain is only omitted when it is linear and every row in it is still empty or `?` (the fork has none of them). An even-length chain cancels out completely. In an odd-length chain (`C`, `Revert C`, `Revert "Revert C"`) the oldest commit carries the net change and stays | `Omitted (script): cancels out - reverts / reverted by <hash>` |
| AI skill, when asked to clean the log | Commits whose whole diff is release bookkeeping for that fork: version code/name, release notes, version numbers in issue templates. See SKILL.md section 5 | `AI checked <date>: omitted - version bump only (<files>)` |

If the fork already has one side of a revert pair, the pair is not omitted, because the other side may still matter.

### Rerunning

- Rows with a status other than empty or `?` (e.g. `O`, `X`) are **never changed**.
- Empty and `?` rows are checked again. An empty row whose Notes contain `AI checked` stays empty unless new `O` evidence appears.
- Notes are always kept, and new upstream commits are added in their place in the order.
- If a commit is no longer reachable from its upstream branch (force-push), it moves to a **Gone from upstream** section
  and keeps its status and notes. Delete it by hand once you no longer need it.
- Rows dated **before `since`** that are already in the file stay at the end of the list, unchanged, in their current
  order. These were added by a one-off pass over older history (in Komikku: the mihon commits before its `since` that
  SY never picked, whose Details start with `pre-<since>`). An empty or `?` row there only changes to `O`, when new `O`
  evidence appears. You may delete such rows by hand; they are not re-added, and the summary simply counts the rows
  that are in the file.
- If a commit is still upstream and dated on or after `since`, but no longer matches the filters (e.g. you changed
  `exclude_identity_regex`), it is dropped.

## Config (`config.json`)

| Key | Meaning |
|-----|---------|
| `target_branch` | Branch to check for already-picked commits (`master`) |
| `since` | First commit date to include, `YYYY-MM-DD` (committer date). Required, and specific to each repo (Komikku: `2024-01-01`); `--since` overrides it |
| `output` | Output Markdown path, relative to the repo root (or absolute) |
| `exclude_identity_regex` | Case-insensitive regex matched against author, committer and co-authors (Komikku: `renovate`). Empty = exclude nothing |
| `main_upstream` | `{name, remote, branch, github}` of the main upstream. Its commits set the order of the list. Required |
| `additional_upstreams` | List of one or more `{name, remote, branch, github}`. Their commits are paired with, and interleaved into, the main upstream's. Names must be unique |

Tuning constants (similarity threshold, date limits, minimum title words) are at the top of the script.

## Using this in another project

1. Copy the whole `.claude/skills/cherrypick-log/` folder.
2. Edit `config.json`: the `main_upstream`, one or more `additional_upstreams` (remote, branch, GitHub `owner/repo`),
   target branch, start date (`since`, specific to that repo), excluded identities and output.
3. Add the remotes (`git remote add <name> <url>`) and run the script.
4. Search `README.md` and `SKILL.md` for project-specific wording (mihon, SY, `// KMK` markers, `AGENTS.md`) and adjust it.

The same applies to Komikku's sibling forks. The only hard requirement is that the fork records upstream picks
with `git cherry-pick -x` (`cherry picked from commit …`) or `owner/repo#N` references in commit titles.

## Limitations

- These are heuristics. A reworded, split or hand-ported change can show as empty or `?`. That's what the AI review is for.
- Reverts are listed as normal commits. A revert and the commit it reverts are not linked.
- One SY commit that squashes several mihon commits is shown only once, above the newest of them.
