---
name: cherrypick-log
description: Update and review cherrypick_log.md, the todo list of upstream commits (mihon/main, tachiyomiSY/master) not yet cherry-picked into master. Use when asked to refresh/update the cherry-pick log, list new upstream commits, or resolve '?' (uncertain) entries by checking whether an upstream change is already in master.
---

# Cherry-pick log: update + AI review

Everything for this tool lives in this folder (`.claude/skills/cherrypick-log/`):

| File | Role |
|------|------|
| `SKILL.md` | This file: the procedure an AI agent follows |
| `update_cherrypick_log.py` | Deterministic generator: fetch, filter, pair, order, detect, keep manual marks |
| `config.json` | Upstreams, target branch, start date, output path, excluded identities |
| `README.md` | Human documentation: rules, columns, commands, porting to another project |

The output is `cherrypick_log.md` in the repo root. **The script is the source of truth** for
the list, its order and statuses found by hash or PR. The AI's job is narrow: decide the rows the script
could not (`?`, and empty rows only if the user asks), by comparing the code itself.

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
   Never set **X**. That is the maintainer's "won't pick" decision.

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

## 5. Finish

1. `python3 $P update --no-fetch` to make sure the file still parses and to refresh the summary.
2. Report to the user how many rows you reviewed, how many became O / stayed ? / empty, and any rows
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
