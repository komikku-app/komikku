---
name: cherrypick-log
description: Update and review cherrypick_log.md, the todo list of upstream commits not yet cherry-picked into the fork's target branch. The upstreams are set in config.json (one main upstream plus one or more additional ones; in Komikku, main mihon/main, additional tachiyomiSY/master). Use when asked to refresh/update the cherry-pick log, list new upstream commits, resolve '?' (uncertain) entries by checking whether an upstream change is already in master, or clean the log by omitting (marking X) commits that only bump the app version.
---

# Cherry-pick log: update + AI review

Everything for this tool lives in this folder (`.claude/skills/cherrypick-log/`):

| File | Role |
|------|------|
| `SKILL.md` | This file: the procedure an AI agent follows |
| `update_cherrypick_log.py` | Deterministic generator: fetch, filter, pair, order, detect, keep manual marks |
| `config.json` | Main upstream + additional upstreams, target branch, start date (`since`, per repo), output path, excluded identities |
| `README.md` | Human documentation: rules, columns, commands, porting to another project |

The output is `cherrypick_log.md` in the repo root. **The script is the source of truth** for
the list, its order and statuses found by hash or PR. It also flags commits that cancel each other out:
a commit and its revert (or a longer revert chain) that the fork has none of get **?** with a
`Cancels out (script):` note on every `update`. These flags are recomputed on every run until the row gets
an `AI checked` note or a status set by hand (see README "Revert chains"). The AI's job is narrow:
- decide the rows the script could not (`?`, and empty rows only if the user asks) by comparing the code itself (sections 2-4);
- when asked to clean the log, omit the commits that only bump the app version (section 5).

## 1. Update the list

```bash
python3 .claude/skills/cherrypick-log/update_cherrypick_log.py          # fetch + regenerate
python3 .claude/skills/cherrypick-log/update_cherrypick_log.py update --no-fetch   # offline
```

Report the summary line (commits / new / status changes / gone) and the summary table at the top
of `cherrypick_log.md`. If the user only asked to update, stop here.

## 2. Get the rows to review

```bash
P=.claude/skills/cherrypick-log/update_cherrypick_log.py
python3 $P uncertain --json                      # '?' rows not yet AI-reviewed
python3 $P uncertain --json --repo mihon --limit 20
python3 $P uncertain --json --include-empty --repo mihon --limit 30   # only when the user asks
```

Rows whose Notes already contain `AI checked` are skipped (use `--include-reviewed` to redo them).
A `↳ tachiyomiSY` row is SY's copy of the mihon row directly below it. Both share one status, so
review the mihon commit once and mark both rows.

## 3. Review each row

A row with a `Cancels out (script):` note is part of a revert pair or chain: review all of its rows together
(the hashes in the notes) and give them consistent verdicts. Leave them `?` with an `AI checked` note when the
pair cancels out and the fork has neither side, since dropping the pair (X) is the maintainer's decision.

For upstream commit `H` (already fetched locally):

1. `git show --stat --format=fuller H` and then the diff (`git show H -- <path>`) for the main files.
2. If Details names target commits (``master `abc` [title]``, `[pr only …]`), inspect them:
   `git show --stat abc`, then compare the hunks with the upstream change.
3. Otherwise, search `master` for the change itself:
   - `git grep -n -F '<distinctive added line>' master -- <path>` (Komikku keeps most upstream paths;
     changes may be wrapped in `// KMK` / `// SY` markers or be in a renamed file).
   - `git log master --oneline -S'<distinctive snippet>' --since=<upstream date - 6 months>`.
   - For removals, check that the removed code is gone from `master`.
4. Decide:
   - **O**: the change is in `master` (cherry-picked, squashed into another commit, or re-implemented).
   - **?**: partly present, conflicts with a Komikku rework, or you can't tell.
   - **empty**: not present.
   Never set **X** here. That is the maintainer's "won't pick" decision. The only exception is
   section 5 (version-bump-only commits).

## 4. Record the verdict (never hand-edit the table)

```bash
python3 $P mark <hash> --status O    --note "AI checked YYYY-MM-DD: present in master <kmk-hash or file> - <why>"
python3 $P mark <hash> --status '?'  --note "AI checked YYYY-MM-DD: partial - <what is missing>"
python3 $P mark <hash> --status none --note "AI checked YYYY-MM-DD: not found - <what you searched>"
```

- Every AI note **must start with `AI checked <date>:`**. The script uses this marker to keep an AI
  "not found" verdict and to leave reviewed rows out of `uncertain`.
- `mark` refuses to change `O` / `X` rows. Do not pass `--force` unless the user explicitly asks.
- Keep notes to one line. Mention the evidence (target commit hash, file, or search done).

## 5. Clean the log: omit version-bump-only commits

Run this step when the user asks to clean the log, or together with a review if they ask for that.
Release commits of a fork (mihon's `Release v0.20.4`, SY's `Release 1.13.2` / `1.10.5`, `Bump versionCode
to 33`, `Update version code`) are only for that fork's releases, so the fork never picks them.

1. List candidates among empty and `?` rows (an `AI checked` note does not exclude them here):
   ```bash
   python3 $P uncertain --json --include-empty --include-reviewed \
     --grep '^(release|v?[0-9]+\.[0-9]+(\.[0-9]+)?$)|version ?code|bump (the )?(app )?version|update (app )?version'
   ```
   The title only nominates a row. Also nominate rows you come across whose diff only bumps the version.
2. Check each one: `git show --stat --format= H`, then `git show H` for small diffs. Mark it **X** only if
   **every** change is release bookkeeping:
   - `versionCode` / `versionName` (or a version constant) in `app/build.gradle.kts` or another build file;
   - release notes: `CHANGELOG.md` release headings and links, `fastlane/**/changelogs`, release README badges;
   - version numbers in `.github` issue templates, and the release-notes text in a release workflow;
   - whitespace-only fixes (e.g. a missing final newline) that ride along with the release.
   Anything else in the diff (min/target SDK, dependency bumps, migrations, code, strings) means **not** X:
   leave the row as it is, and mention it in the report if it looks interesting.
3. Record it: `python3 $P mark H --status X --note "AI checked YYYY-MM-DD: omitted - version bump only (<files>)"`.
   SY copies (`↳` rows) of a version-bump commit get the same mark.

## 6. Finish

1. `python3 $P update --no-fetch` to make sure the file still parses and to refresh the summary. This also
   applies the script's revert-chain omission.
2. Report to the user how many rows you reviewed, how many became O / stayed ? / empty / X, and any rows
   that need a human decision (big reworks, likely X candidates).
3. Do not commit unless asked. If asked, follow `AGENTS.md` git rules (feature branch, never `master`).

## Large batches

For more than ~20 rows, split them into batches (e.g. by `--repo` and `--limit`) and give each batch
to a subagent, which only *reports* `hash → verdict + one-line evidence`. The main agent then runs all
`mark` commands, so only one process writes the file.

## Changing the rules

Matching and ordering rules are in `update_cherrypick_log.py`: the module docstring, the constants at
the top, and `Builder.pair` / `Builder.detect`. They are explained in `README.md`. Update both when
you change behavior.
