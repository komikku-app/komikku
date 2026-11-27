---
name: cherry-pick
description: Cherry-pick one or more upstream commits (mihon/main or tachiyomiSY/master) into the current branch, adapt them for Komikku following AGENTS.md "Mandatory rules for AI agents" (fork markers, i18n, formatting), refuse (and argue strongly against) any pick that would break Komikku (extension loading, database migrations, backups, preferences, source IDs, Komikku features) unless the user explicitly confirms, mark them O in cherrypick_log.md in the same commit, verify the result, review each pick with the built-in code-review skill and fix what is worth fixing as uncommitted changes until nothing is left, report later upstream commits that fix bugs in the picked commit and whether the pick belongs in the current PR, a separate PR, or a PR that must wait for earlier picks, then ask whether to fold the fixes into the pick, commit them separately, both, or neither. Use when asked to cherry-pick, pick, port or bring over an upstream commit by hash.
argument-hint: <upstream-commit> [<upstream-commit>...]
---

# Cherry-pick an upstream commit

Input: one or more upstream commit hashes (`$ARGUMENTS`). Pick several in the order given, taking each through every
step below before starting the next, and give one combined report at the end.

The fork rules live in AGENTS.md, section **Mandatory rules for AI agents** (git, fork markers, strings, formatting).
They win over anything here; this file is the procedure. The upstreams and their remotes are in
`.claude/skills/cherrypick-log/config.json`: `mihon` (`mihon/main`, GitHub `mihonapp/mihon`), the base code, and
`tachiyomiSY` (`tachiyomiSY/master`, GitHub `jobobby04/TachiyomiSY`), which Komikku is forked from and which picks
mihon commits too.

Never pick onto `master` or `main`, never push, and never put a bug fix in the pick commit on your own: bugs found
while picking are fixed only by step 7's review, as uncommitted changes, and the user decides whether they go in the
pick or a commit of their own. Write temporary files (messages, test data, worktrees) to the scratchpad directory, not
the repo.

**Referring to commits.** When you name a commit to the user, give its short hash and then its subject in parentheses,
with no quotes, like `git log --format=reference`. Shorten long subjects with `…` and drop a trailing PR reference such
as `(#3248)` so the parentheses don't nest: `430b13bb81` (Adapt network implementation for TachiyomiX 1.6). This is for
conversation only, not for commit messages or PR descriptions.

**A pick commit holds only that upstream change**, plus its row(s) in `cherrypick_log.md` and the removal of any
`ISSUES.md` entry it fixes (step 4). Uncommitted changes that are already in the tree stay out of it (step 0).

## 0. Preflight

1. The current branch is a named feature branch: `git branch --show-current` prints its name (nothing means a
   detached `HEAD`), and it is not `master` or `main`. Otherwise stop and ask.
2. Check that each argument is a commit hash, 7 to 40 hexadecimal characters (`^[0-9a-f]{7,40}$`), before putting it in
   any command. Otherwise stop and ask. Then resolve it: `git rev-parse --verify '<hash>^{commit}'`, and
   `git branch -r --contains <hash>` must list `mihon/main` or `tachiyomiSY/master`. If either fails (a commit that
   isn't fetched yet fails `rev-parse`), `git fetch mihon` / `git fetch tachiyomiSY` and retry both.
3. Look up its row in the working tree's `cherrypick_log.md` (`grep "commit/<full-hash>" cherrypick_log.md`), so
   notes that aren't committed yet count too. A mihon row can have a `↳ tachiyomiSY` row right above it: SY's copy of
   the same commit (its Details say `pick of mihon …`). Both share one status.
   - `O`: already picked. Stop and say where (`git log --oneline -i --grep=<first 7 characters of the hash>`; a
     citation can use a short hash, and a 7-character prefix can also match another commit, so check each hit).
   - `?` with `partial:` in its Details: part of it is already here (same `git log`). Say which part, and pick only
     the rest.
   - Any other `?`: the script matched only the title or PR number of the commit its Details names, the row is part of
     a revert chain (`Cancels out (script):`), or a partial pick was marked by hand (a `Partial …` note). Compare this
     commit's files with ours (`git diff <hash> HEAD -- <files>`; lines it adds that show as removed aren't here yet),
     say what is already here, and ask before picking.
   - `X`: show the note and ask before picking. If the user says to pick it anyway, step 4's `mark` needs `--force`
     (it refuses to change an `O` or `X` row otherwise).
   - No row: say so (the log starts at `since` in `config.json` and leaves out Renovate commits) and go on. A decision
     about such a commit can't go in the log (`mark` finds no row), so give it in the report.
4. Read the row's **Notes** whatever its status. They can hold a decision made earlier for this commit, such as
   "fold in … from …" or "don't pick just for …". Follow it as part of this pick and say that you did. If a note
   contradicts the request, ask. Read the other copy's row too (an `↳ tachiyomiSY` row's mihon row), since notes
   usually sit on the mihon row. An `Early pick <date>:` note names a later upstream commit already picked here whose
   lines this commit rewrites: step 3 carries its change over.
5. Not already on this branch: `git log --oneline --grep=<full-hash> master..HEAD` is empty.
6. If tracked files already have uncommitted changes, staged or not, list them, stash them with
   `git stash push -m "unrelated to <short-hash>"` and note the stash's commit (`git rev-parse refs/stash`).
   `git cherry-pick` refuses to start with staged changes, and a dirty `cherrypick_log.md` would otherwise be committed
   along with the row. Keep them stashed until the end, so that verification, review and the fixes see only the pick:
   pop the stash once step 7 has carried out the user's answer, or as soon as you stop without committing (a "don't
   pick" decision, or after `git cherry-pick --abort` if the pick is in progress, since `git stash pop` fails while it
   has unresolved conflicts). Pop it by that commit, not by position, since another session may push or drop stashes
   meanwhile: find its entry with `git stash list --format='%gd %H' | grep <commit>`, then
   `git stash pop 'stash@{N}'`. Untracked files can stay, unless the pick adds a file at the same path; then ask.

When the user decides something about a commit that isn't being picked now (pick it later with a change, skip it,
fold part of it into another pick), record it in that commit's row, not only in chat or private memory:
`python3 .claude/skills/cherrypick-log/update_cherrypick_log.py mark <hash> --note 'Plan <YYYY-MM-DD>: …'`. On an `O`
or `X` row `mark` refuses even a note: add `--force` and leave out `--status`, so the status stays. Don't start the note
with `AI checked`, which is reserved for review verdicts. A commit with no row gets its decision in the report instead.

## 1. Read it before picking

- `git show --stat <hash>`, then the whole `git show <hash>`. Note the subject's `(#N)`, and whether the message has a
  `(cherry picked from commit …)` line (an SY commit copied from mihon).
- **Which copy to pick.** For a mihon commit that SY has also picked (a `↳ tachiyomiSY` row, or
  `git log tachiyomiSY/master --format='%h %s' --grep=<full-hash>`), compare the two:
  `git range-diff <mihon>~1..<mihon> <sy>~1..<sy>`, or compare the two patches limited to the commit's files:
  `diff <(git show --format= <mihon-hash> -- <files>) <(git show --format= <sy-hash> -- <files>)`. Don't use
  `git diff <mihon-hash> <sy-hash>`: it compares the two trees, so every other difference between the branches shows up
  as if it were SY's adaptation.
  - **Mostly the same** (the same hunks, apart from context lines, line offsets, formatting, or files SY doesn't have,
    such as `CHANGELOG.md` and `fastlane/`): pick mihon's commit, as most Komikku picks do.
  - **More adaptation in SY's copy** (hunks changed or added to fit SY code: `// SY` / `// EXH` blocks, SY-only types,
    `exh/`; conflicts resolved in code Komikku shares with SY; extra fixes): pick SY's copy. Example: SY's
    `d6250c24bc` (Ensure tachiyomix public API is not removed by R8) adds keep rules for SY's source interfaces
    (`MetadataSource`, `UrlImportableSource`, …) that mihon's `6dfa10ffac` doesn't have, and only SY's copy applies to
    `master` cleanly.
  - When picking SY's copy, bring back the parts of mihon's commit that SY left out and that apply here, such as the
    `CHANGELOG.md` entry (SY has no changelog):
    `git diff --no-color --no-ext-diff <mihon-hash>~1 <mihon-hash> -- <file> | git apply -3`, as `c290472012` (Add
    option for bookmarked chapters to download dropdown) did. Step 4 has the message rules for an SY copy.
- Sort the touched files:
  - **Present here:** adapt them (step 3).
  - **Gone here** (Komikku removed it, or never had it, such as `fastlane/`): drop it.
  - **`CHANGELOG.md`:** Komikku's changelog follows mihon's, and most picks keep the entry. Keep it; if it conflicts
    only because of entries Komikku lacks, keep Komikku's side plus the new entry.
  - **SQLDelight migrations** (`data/src/main/sqldelight/tachiyomi/migrations/N.sqm`): Komikku numbers its own
    migrations (they diverged from SY's at 40 and are unrelated to mihon's numbers). Never apply an upstream migration
    to the file of the same number here; it becomes the next Komikku migration (step 3).
  - **App migrations** (`app/src/main/java/mihon/core/migration/migrations/`): their `version` is an app `versionCode`,
    and Komikku's codes differ from mihon's and SY's (step 3).
  - **`versionCode` / `versionName` in `app/build.gradle.kts`:** upstream's own release numbers, often raised along
    with a new app migration (mihon `f6b2684323` raises its `versionCode` from 17 to 18). Never apply them (step 3).
  - **Strings:** `i18n/` (`MR`) for mihon, `i18n-sy/` (`SYMR`) for SY. Translation commits from Weblate also change
    non-base locales; that is the only kind of pick that may (AGENTS.md "Internationalization").
- For each file present here, see how ours already differs from upstream's parent: `git diff <hash>~1 HEAD -- <file>`.
  Every difference is SY code (`// SY`, `// EXH`), Komikku code (`// KMK`), something upstream has and Komikku
  dropped or reworked on purpose, or a later upstream commit picked early (below). Keep all of them, except Komikku
  code that step 3 finds upstream now covers. `git log --oneline -5 -- <file>` shows why a line is ours.
- **Later upstream commits picked early.** Komikku can already have an upstream commit that comes after this one
  upstream, such as a fix picked before the refactor it follows. List the later upstream commits that touch this
  commit's files, new ones included, on both upstreams (step 6's `<range>`), and keep the ones picked here:

  ```bash
  git log --reverse --format=%H <range> -- <files it touched, including new ones> | while read -r c; do
    git log --format="%h (%s) carries ${c:0:10}" --grep="$c" HEAD
  done
  ```

  Git keeps such a pick's change only where this commit leaves its lines alone. Where this commit moves, deletes or
  rewrites them, the early pick's lines conflict and taking upstream's side drops them, and code moved into a new file
  arrives in its older form, with no conflict at all. Example: mihon `553762fae8` (Fix reading history being double
  counted) was picked into `MangaRestorer`, but upstream it comes after `306befb214` (Restrict raw
  database access to the data module), which moves `restoreHistory` into a new `RestoreRepositoryImpl` without the
  fix. SY picked them in the same order and its copy `09228c00cc` lost the fix. Step 3 carries such a change over.
- **Earlier upstream commits it jumps ahead of.** The other side of the same problem: this pick can change lines that
  earlier upstream commits, not picked here yet, wrote or rewrote upstream. Each of them rewrites those lines when it
  is picked later, and loses this pick's change there. List them. When the commit has a copy on the other upstream (a
  mihon row with a `↳ tachiyomiSY` row), give both hashes, whichever copy you pick: Komikku's files follow SY's, so
  SY's history finds commits mihon's misses (mihon `553762fae8` alone finds only `306befb214`). A commit with no
  copy, such as an SY-only one, takes just its own hash:

  ```bash
  python3 .claude/skills/cherry-pick/find_rewriters.py <hash> [<other-copy-hash>]
  ```

  It traces the lines the commit changes back with `git log -L`, and also lists the commit that created a file it
  changes when that file doesn't exist here (not a rename, which git follows). It leaves out commits without a row and
  commits Komikku has: an `O` / `X` row, or a `cherry picked from` line on `HEAD` unless the row has a `Partial` or
  `Folded` note (the part left out can still rewrite those lines). It prints the row to note: the mihon row for SY's
  copy of a mihon commit. They are no prerequisites as long as the lines they rewrite exist here
  (Prerequisites, below, covers code that doesn't), so they don't stop the pick; step 4 notes their rows so whoever
  picks them later keeps this change. Example: for `553762fae8` (Fix reading history being double counted) and its SY
  copy, it lists `306befb214`, and mihon `8e0c911f93` and `c114028a33` through their SY copies.
- **Prerequisites.** Do this before picking, for every part you would keep:
  - **Context:** if the diff's context refers to code that doesn't exist here, find the earlier upstream commits that
    added it: `git log --oneline -10 <hash>~ -- <file>` lists the last commits that touched the file upstream before
    this one; check which of them Komikku has.
  - **What it uses or adds:** for every class, function, key, preference, string, resource or dependency the commit
    uses but doesn't define itself, find what defines it upstream at that commit (`git grep -n '<name>' <hash>`). Then
    check that the same thing exists here (`git grep -n '<name>' HEAD`). What the commit adds itself is no
    prerequisite; only check that Komikku doesn't already have something different by that name. Example:
    `6dfa10ffac` keeps `eu.kanade.tachiyomi.network.HttpException`, which only exists as a public class since
    `430b13bb81` (Adapt network implementation for TachiyomiX 1.6), here `87a173929b`.
  - **Don't rely on a prerequisite's status.** The log's partial-pick check compares file names, not contents, and
    skips rows already marked `O`: `34102027d1` cites `b543bc089a` (Clean up some build warnings) but carries 14 of its
    21 files, and the row is `O`. For each prerequisite, compare its files with ours
    (`git diff <prerequisite> HEAD -- <those files>`).
  - **If anything is missing, stop before picking.** Tell the user which part needs which commit and what happens
    without it, testing that where you can. Then ask: pick the prerequisite first, pick this commit without that part,
    or wait, recording the plan in the Notes (step 0). Never drop, rewrite or stub out a part on your own to make the
    pick fit, and don't pull in extra commits without asking.
- Check whether Komikku already has its net effect, from another pick or its own work. Example: `64c50c1283` (Require
  Android 8+) is covered by the later `dfb3091e38` (Make the app Android 8+), picked here as `cdac438f1e`. If, after the
  step 3 adaptation, nothing would change, don't make an empty commit. Abort the pick (`git cherry-pick --abort`; once
  git has committed a clean pick, `git reset --hard HEAD~1`, which loses nothing when `git diff --quiet HEAD~1` is clean)
  and propose marking the row `O` with the covering commit, or `X` if it doesn't apply:
  `mark <hash> --status O --note 'covered by <hash> (<subject>)'`. Ask first.
- **Known issues.** `ISSUES.md` (repo root) lists problems Komikku knows of and hasn't fixed, each with its file,
  function and, under **Upstream**, the upstream commit that fixes it, if any. Find the entries this pick solves: those
  whose **Upstream** names this commit or its other copy (`grep -n -e '<short-hash>' -e '<other-copy-short-hash>'
  ISSUES.md`), and those about code the commit changes (`grep -n` the files and functions it touches) whose problem
  its change removes. Read the entry's **What happens** and **Fix** against the diff to decide; naming the commit isn't
  enough when the pick leaves out the part that fixes it. Step 4 clears them.

## 2. Will it break Komikku?

Upstream targets Mihon or TachiyomiSY, so a change that is right for them can break Komikku, its users' data or its
extensions. Check the commit against these, reading the current code rather than trusting this list:

- **Extensions.** Installed extensions run against Komikku's `source-api` and app classes.
  - `ExtensionLoader.kt` accepts the lib versions in `SUPPORTED_LIB_VERSIONS` (1.4 and 1.6 today).
  - Removing or changing public API in `source-api` breaks extensions built against it (AGENTS.md: avoid breaking
    extension ABI). This includes SY's interfaces (`MetadataSource`, `UrlImportableSource`, `FollowsSource`,
    `LoginSource`, `NamespaceSource`, `RandomMangaSource`, `PagePreviewSource`) and Komikku's `EhBasedSource`.
  - R8 keep rules (`source-api/consumer-proguard.pro`, `app/proguard-rules.pro`) must keep every class and member
    extensions call. A class-level `-keep` doesn't keep members: Mihon 0.20.4 crashed with `NoSuchMethodError` on
    `HttpException.getCode()` (mihonapp/mihon#4027) because R8 inlined and dropped the getter. A pick that narrows keep
    rules must still cover the SY and Komikku types.
- **Database.** Users have already run every shipped migration. Never change what an existing migration does to a
  database; a new upstream migration becomes the next Komikku migration. CI runs `verifySqlDelightMigration`, which
  can need old migrations edited to describe the same schema, as `00b9be1ff7` did to `28.sqm` (inside `-- KMK -->`
  markers) and `34.sqm`. Say so when a pick needs that, and wrap such edits in KMK markers.
- **Backups.** `app/.../data/backup/models/` are protobuf models. Keep upstream's `@ProtoNumber`s. SY uses 600 and up
  (and the 800s in `BackupManga`), and Komikku uses 610 in `Backup` and 900 in `BackupCategory`. If a pick adds a
  field at a number already used here, stop: backups would be misread. Backups made by older Komikku versions, and by
  Mihon and SY, must still restore.
- **Preferences.** A renamed or removed preference key loses the user's setting unless a migration moves it. A
  migration picked from upstream needs a Komikku `versionCode` (step 3).
- **Source IDs.** Changing the ID of an in-app source (E-Hentai/ExHentai, NHentai, MangaDex delegates, merged
  `6969`, local) disconnects users' libraries.
- **Komikku features.** A change that removes or overrides behavior Komikku added (`// KMK` code, KMK settings,
  screens under `mihon/feature/`) or that SY added breaks it, even when the upstream change is right for upstream.

If the commit would break Komikku, **stop before picking**. Run `git cherry-pick --abort` if the pick has already
started.
- Tell the user plainly that you recommend against it: what breaks, for whom (all users, or only users of some sources,
  extensions or settings), when (on update, on backup restore, at runtime), and the evidence (the Komikku code involved).
- Offer the alternatives: pick it with an adaptation that keeps Komikku working (say exactly which), take only the
  harmless parts, or mark it `X` with a note.
- Continue only if the user explicitly confirms. Even then, prefer the Komikku-safe adaptation.

If you can't tell, say so, and treat it as a risk to raise before picking, not a reason to stay quiet.

## 3. Pick and adapt

Run `git cherry-pick -x <hash>`, which keeps the `(cherry picked from commit …)` line already in the message (an SY
copy's citation of mihon) and appends one for `<hash>`. If it applies cleanly, git commits it at once and step 4 amends
that commit. Otherwise the pick stays in progress.

Resolve conflicts by keeping upstream's code and layout, so later picks still apply, together with everything step 1
found that is ours. Change only what this list covers:

- **Komikku's own code in a conflict.** When our side of a conflict holds Komikku's own lines, don't take either side
  blindly. Komikku's own lines are usually a `// KMK` block. Unmarked lines count too when the commit that added them
  is Komikku's, such as work from before the markers: no upstream branch contains it
  (`git branch -r --contains <commit>` lists neither `mihon/main` nor `tachiyomiSY/master`; Komikku shares SY's
  history, so SY's own commits are here too) and it has no `cherry picked from` line. Check whether that change is
  still needed with upstream's new code:
  - **Why it exists:** find the commit that added it, with `git log -S '<distinctive line>' -- <file>`, or
    `git log -L <first>,<last>:<file> HEAD` with the line numbers of HEAD's version (`git show HEAD:<file>`), not of
    the working file, whose conflict markers and upstream lines shift them. Read its message and PR, and what it
    fixed or added.
  - **What upstream does there now:** whether this commit (or an earlier upstream one it builds on) fixes the same bug
    or adds the same behavior, makes the change wrong (it relies on code upstream removed or reworked), or leaves it
    untouched.
  - **Who else relies on it:** `git grep` the names the block defines or changes, so dropping or reshaping it doesn't
    break other Komikku code.

  Then decide, and record each decision for the report:
  - **Still needed** (upstream doesn't do what it does): keep it, adapted to upstream's new code, inside KMK markers;
    add them around unmarked Komikku code the resolution changes (AGENTS.md "Fork markers"). This is the usual case.
  - **Covered by upstream** (upstream now does the same, and keeping ours would duplicate or fight it): take
    upstream's side and drop ours, once you've checked that upstream covers every case ours handled. Dropping
    Komikku code drops Komikku behavior (step 2, Komikku features), so ask the user first unless ours is only an
    earlier copy of the same fix.
  - **Unsure:** keep ours, adapted, and say why in the report.

  Clean merges can hit Komikku code too: git doesn't flag upstream lines landing right next to a KMK block, or
  upstream changing code a KMK block relies on. In each file the pick touches, list the markers (`grep -n 'KMK' <file>`)
  and read the staged diff with enough context to reach them (`git diff --cached -U15 <base> -- <file>`; the default
  3 lines of context hide a marker a few lines away), asking the same questions of every hunk near a KMK block.

- **Fork markers** (AGENTS.md "Fork markers"): upstream's own lines go in unwrapped. Every line Komikku has to change
  or add to make the pick fit is wrapped in `// KMK -->` … `// KMK <--` (`-- KMK -->` in `.sq` / `.sqm`, inline
  `/* KMK --> */ … /* KMK <-- */` inside an expression). Keep `// SY` and `// EXH` blocks intact; a change inside one
  gets a nested KMK block. Never add new `// SY` blocks; an SY commit brings its own.
- **SQLDelight:** write an upstream migration as the next file here
  (`ls data/src/main/sqldelight/tachiyomi/migrations | sort -n | tail -1`, plus one), adapted to Komikku's schema.
  Update the `.sq` files and `*RepositoryImpl` mappers the commit touches (AGENTS.md "Database").
- **App migrations:** set `version` to the `versionCode` the next Komikku release will have, not upstream's.
  `VersionRangeMigrationStrategy` runs a migration only when its version is in `(old + 1)..new`, so a version too high
  doesn't run for users updating to that release (only on a later update), and one too low never runs for users who
  already have that `versionCode`.
  - Check whether the `versionCode` in `app/build.gradle.kts` has shipped:
    `git tag --list 'v*' --contains "$(git log -1 --format=%H -G'versionCode = ' -- app/build.gradle.kts)"`.
  - No tag: it hasn't shipped, so use it as is. Example: v1.14.1 shipped 81 and `app/build.gradle.kts` has 82, so
    `override val version: Float = /* KMK --> */82f/* KMK <-- */`.
  - A tag: raise `versionCode` by one in the same pick (the line is Komikku's own, so no KMK markers) and use the new
    number, as `2394ec3594` (Add installation id for feature flags) raised 81 to 82 after v1.14.1.
  - Drop upstream's own `versionCode` / `versionName` change in either case. Mihon's `7f` in
    `TrustExtensionRepositoryMigration` is `67f` here.
  - Keep `Migration.ALWAYS` as it is. Such a migration still runs only when `versionCode` goes up
    (`MigrationStrategyFactory` returns `NoopMigrationStrategy` for `old >= new`), so the raise rule above applies to
    it too: `2394ec3594` adds `InstallationIdMigration` with `Migration.ALWAYS`.
- **Strings:** keep upstream's strings in upstream's module (`i18n/` base for mihon, `i18n-sy/` base for SY). Never
  move them to `i18n-kmk/` and never edit non-base locales, except in a translation commit, which is picked whole.
  Komikku's strings name the app Komikku: a string the pick adds or changes that names Mihon (or TachiyomiSY, as the
  app) becomes Komikku, in base and in the locales the commit touches, as `121d905861` (Replace i18n: Mihon to Komikku)
  did.
- **Branding and URLs:** mihon's website, docs, GitHub and Discord links become Komikku's (docs on
  `komikku-app.github.io`, GitHub `komikku-app/komikku`). Copy them from current files; never type them from memory.
  Package names stay: Komikku keeps `mihon.*` and `tachiyomi.*` packages.
- **Parts SY's copy left out** (step 1, Which copy to pick): when picking SY's copy, apply them from mihon's commit with
  `git diff --no-color --no-ext-diff <mihon-hash>~1 <mihon-hash> -- <file> | git apply -3`, the `CHANGELOG.md` entry
  included.
- **Upstream commits picked early** (step 1): where this commit moves, deletes or rewrites their lines, take upstream's
  side there like any other conflict, then bring each one's change back where this commit puts the code. Take it from
  the later upstream commit itself, which upstream wrote for the new layout:
  `git diff --no-color --no-ext-diff <later>~1 <later> -- <files this pick adds or rewrites> | git apply -3` (plain
  output even when the user's config colors diffs). Upstream commits between the two
  that Komikku doesn't have yet can stop it applying; then port the change by hand, keeping this pick's version of the
  lines those commits change. The carried lines are upstream's, so they go in unwrapped; the early pick's own `// KMK`
  adaptations follow the rest of the file's Komikku code. Check the result: every line the later commit adds to those
  files (`git diff --no-color <later>~1 <later> -- <file>`) is in the staged file (`git show :<file>`), not in the
  staged diff, which leaves out the lines this pick doesn't touch. A line that a commit in between changed shows in
  its ported form; say which.
- **Gone files:** remove them with `git rm`, including modify/delete conflicts. These and the adaptations above are the
  only things you change without asking. Leaving out any other part needs the user's OK first (step 1, Prerequisites).

Files that merged without a conflict can still need these adaptations. Check everything the pick brings, not just the
conflicts. These checks read the index, so stage every adaptation first (`git add <path>` for each). `<base>` is `HEAD`
while the pick is in progress and `HEAD~1` once git has committed it; `git diff --cached HEAD~1` covers the commit plus
the adaptations staged on top:

```bash
git diff --cached <base> --stat -- data/src/main/sqldelight/tachiyomi/migrations app/src/main/java/mihon/core/migration/migrations i18n-kmk
git diff --cached <base> -- . ':!CHANGELOG.md' | grep -n -E '^\+.*(mihon\.app\b|mihonapp/mihon|discord\.gg|\bMihon\b|@ProtoNumber|override val version|versionCode|versionName)'
git diff --cached <base> --stat -- 'i18n*/src/commonMain/moko-resources/*' ':!i18n*/src/commonMain/moko-resources/base/*'
```

Every hit needs a reason: a migration renumbered, a URL that stays upstream's on purpose (a link to the upstream PR or
issue), a proto number checked in step 2, a `versionCode` raised by step 3's rule (never upstream's number), non-base
locales only in a translation commit.

If the adapted result changes nothing (`git diff --cached --quiet <base>`), go back to the "net effect" check in
step 1.

## 4. Message, log row, commit

- **Whose number it is.** A bare `#N` links to Komikku's PR or issue N, so qualify it by the repo it belongs to, which
  is not always the repo of the picked commit: `mihonapp/mihon#N` for mihon, `jobobby04/TachiyomiSY#N` for SY. An SY
  copy of a mihon commit keeps mihon's subject and body, so its numbers are mihon's: a number that is also in the mihon
  commit's message is mihon's, and one only in SY's message is SY's. When unsure, check with
  `gh api repos/mihonapp/mihon/issues/<N> --jq .html_url` (or `repos/jobobby04/TachiyomiSY/…`), which answers for
  PRs and issues alike (`gh pr view` fails on an issue number such as `Fixes #3051`). Example: SY's `185cd923c0`
  (Add option for bookmarked chapters to download dropdown (#2891)) was picked as `c290472012` with
  `(mihonapp/mihon#2891)`.
- **Subject:** qualify a trailing `(#N)`: `(mihonapp/mihon#N)` or `(jobobby04/TachiyomiSY#N)`. A `(#N)` inside the
  subject, such as in a revert's quoted title, becomes `(# N)`, as in `c37e6866a0` (Revert "Add full predictive back
  support (# 2085)" (mihonapp/mihon#2362)). No number: leave the subject unchanged.
- **Body:** keep upstream's body, qualifying its `#N` references the same way (`Fixes #3051` became
  `Fixes mihonapp/mihon#3051` in `c237b645aa`). Drop a `# Conflicts:` block that SY committed in its message. If the pick
  folds in a change from another upstream commit (a Notes plan, or something the user approved), add one line to the
  end of the body, before the `(cherry picked from commit …)` lines, naming that commit by hash and PR, e.g.
  `Also includes <change> from upstream <hash> (mihonapp/mihon#N).` Don't hard-wrap it. For an upstream commit picked
  early and carried over by step 3, write `Keeps <change> from upstream <hash>, picked earlier as <pick-hash>.`
  instead.
- **`(cherry picked from commit …)` lines** end the body, with the full hashes, followed only by the user's co-author
  trailer when the pick needs one (Commit, below). A mihon pick ends with mihon's line. A pick of SY's copy ends with
  two lines, with nothing between them: mihon's next to last and SY's last.

  ```
  (cherry picked from commit <mihon-full-hash>)
  (cherry picked from commit <sy-full-hash>)
  ```

  `-x` appends SY's line to SY's message, which usually ends with mihon's line already. Add mihon's line above SY's
  when SY's message doesn't cite it, and remove any blank line, `# Conflicts:` block or other text between the two.
- **Log rows**, in the same commit: run
  `python3 .claude/skills/cherrypick-log/update_cherrypick_log.py mark <full-hash> --status O` for the picked commit
  and for its other copy, if any (the mihon row and its `↳ tachiyomiSY` row), adding `--force` for an `X` row the user
  said to pick (step 0), then `git add cherrypick_log.md`. `mark` rewrites only that row; the summary counts refresh
  on the next `update`. Never edit the table by hand, except to resolve a stash-pop conflict on the row (step 7). If
  there is no row, skip this and say so.
  - If the pick leaves out a part that applies here (not a gone file), use
    `--status '?' --note 'Partial <YYYY-MM-DD>: picked without <part> (<why>)'` instead, so the row doesn't claim the
    whole commit.
  - When the pick folds in part of another upstream commit (Body, above) and that commit has more to pick later, mark
    its row `--status '?' --note 'Folded <YYYY-MM-DD>: <part> into the pick of <hash>.'`.
  - For each row under `== Rows to note` in step 1's `find_rewriters.py` output (each row once, though both copies'
    lists can name it), add a note, without `--status`:
    `--note 'Early pick <YYYY-MM-DD>: <hash> (<subject>) is already here and this commit rewrites the lines it changed
    in <file> (<function or block>). Keep its change (cherry-pick skill step 3).'`, where `<hash>` is the upstream
    commit being picked (mihon's for SY's copy). Name it by its upstream hash, not by the pick's, which changes on
    every amend or rebase. `mark` appends to a row that already has notes, and `update` keeps them.
  - If `== Rows to note` counts more than five, don't note them all on your own: a pick far ahead of upstream can list
    dozens, mostly broad refactors Komikku may never pick (SY's `7af7a1c136` lists about 20), and each note is a row
    the pick commit touches, so branches picking nearby commits conflict on `cherrypick_log.md`. Before committing,
    show the list with what each one rewrites, and ask with AskUserQuestion which to note: all of them, only the ones
    that rewrite the same function or block as this pick (your pick, named), or none. Say in the report which rows were
    left without a note.
  - `update` always keeps a `?` with either note, even when it finds `O` evidence: it can't see a left-out part under
    `partial_ignore_paths` (a SQLDelight migration). Picking the rest later marks the row `O` here. With any note other
    than these or `AI checked`, `update` re-checks the row and can reset it.
- **`ISSUES.md`**, in the same commit: delete each entry step 1 found the pick solves, from its `## ` heading down to
  the next one (`ISSUES.md` says to delete an entry in the commit that fixes it), then `git add ISSUES.md`. If the pick
  fixes only part of an entry, rewrite the entry to describe what is left, and its **Upstream** line to say what this
  pick fixed. Leave entries the pick doesn't touch alone, even if they look stale; say so in the report instead.
- **Commit:** stage only the pick's own files, the log row and `ISSUES.md`, with `git add <path>` for each one. Never
  use `git add -A`, `git add -u` or `git commit -a`.
  - After a conflict: write the message to a scratchpad file (`.git/MERGE_MSG` without its `# Conflicts:` block), then
    `git commit -F <file>`.
  - After a clean pick: write its message to a scratchpad file (`git log -1 --format=%B > <file>`) and edit it there,
    add the log row, then `git commit --amend -F <file>`.
  - **Co-author trailer.** Never add a `Co-Authored-By` trailer for Claude. Add one for the user when the pick holds
    Komikku work beyond resolving conflicts:
    - **Ported by hand:** upstream's file or code doesn't exist here, so its change is rewritten into other code, as
      `a6ee4bc596` (Fix library range selection crash across categories) went from upstream's `LibraryViewModel` into
      our `LibraryScreenModel`.
    - **Komikku changes folded in:** step 7's fixes folded into the pick, or a change from another upstream commit
      (Body, above).

    A conflict resolved in place (the code is the same, only nearby Komikku lines are in the way) and step 3's routine
    adaptations (fork markers, migration numbers, strings, URLs, gone files) don't count. Take the name and email from
    `git config user.name` and `git config user.email`; never type them from memory or write them into this skill. Put
    the trailer on the message's last line, only once, separated from the `(cherry picked from commit …)` lines by one
    blank line (add it if the message doesn't end with one):

    ```
    (cherry picked from commit <full-hash>)

    Co-authored-by: <name> <email>
    ```
  - Check that the commit holds only the expected files: `git show --stat HEAD`.
  - Leave step 0's stash where it is; it comes back at the end of step 7.
- **Signature:** commits are GPG-signed automatically (`commit.gpgsign`), so `git log -1 --format='%h %G?'` must show
  `G`. Rewrite history only with rebase or amend, never `filter-branch`, which drops signatures.

## 5. Verify

AGENTS.md "Formatting & build verification" applies to every pick that changes code or build files (Kotlin, XML,
`.sq` / `.sqm`, Gradle files and version catalogs, ProGuard rules), not only Kotlin or XML, in this order. Building
runs the pick's Gradle files, plugins and tests with your privileges: if it changes build logic (Gradle files, version
catalogs, `buildSrc/`, plugins, test code), first read those hunks for anything that downloads or runs code from
elsewhere or reads credentials, and ask before building if anything does.

1. `./gradlew spotlessCheck`. CI runs it, so a pick must pass even where upstream's formatting differs. If it fails,
   run `./gradlew spotlessApply`, check that it only reformatted the picked files, amend those changes into the pick,
   and re-run `spotlessCheck`.
2. `./gradlew assembleDebug`. Use `./gradlew :app:compileDebugKotlin` instead only when the user asked for a quick
   compile check (AGENTS.md).
3. Then, by what the pick touches:
   - **Tests:** the unit tests of each touched module that has them, e.g.
     `./gradlew :domain:testReleaseUnitTest :app:testReleaseUnitTest`.
   - **Database:** `./gradlew :data:generateSqlDelightInterface` and `./gradlew verifySqlDelightMigration`.
   - **Backups:** build a backup with the parent commit's debug build and restore it with the new one, or the reverse,
     when the models changed.
   - **R8 / keep rules / `source-api`:** `./gradlew assembleRelease` (or `assemblePreview`) for the pick and for its
     parent commit (in a scratchpad worktree), and compare what R8 removed.
     `app/build/outputs/mapping/<build type>/usage.txt` lists every class and member R8 removed, so a class being in the
     APK isn't enough: diff the two files for the `source-api` and `eu.kanade.tachiyomi.network` classes. A member
     extensions use (a method, a constructor, an enum constant or an object's `INSTANCE`) that only the pick's build
     removes fails at runtime with `NoSuchMethodError` or `NoSuchFieldError`.
   - **Runtime behavior or UI:** say what needs a manual check on a device or emulator (`./gradlew installDebug`), and
     do it when the user asks.

If anything Komikku needs is broken, the commit already exists. Say so plainly, recommend against keeping it, and ask
whether to drop it (`git reset --keep HEAD~1` discards it, so get confirmation first; unlike `--hard`, it refuses
rather than discards uncommitted changes) or keep it and fix it in step 7.

Report what ran, what passed and what wasn't run. AGENTS.md "Common issues" covers Gradle OOM, Spotless and SQLDelight
errors.

The Bash tool runs zsh:
- **Arguments:** an unquoted `$var` isn't word-split, so pass several arguments as an array (`"${T[@]}"`).
- **Colons:** `$var:x` is a history modifier, so write `${var}` before a `:`.
- **Globs:** quote them, as in `--include='*.kt'` and `--status '?'`.

## 6. Later upstream fixes for this commit

Find later upstream commits that fix something this commit introduced, on both upstreams (an SY commit can fix SY's
copy of a mihon commit):

- **Range** (`<range>` below): `<hash>..<branch>` only stops at `<hash>` on the branch that contains it. Use
  `<mihon-hash>..mihon/main` and `<sy-copy-hash>..tachiyomiSY/master` (the `↳ tachiyomiSY` row). Without an SY copy, use
  `--since=<the commit's date> tachiyomiSY/master`: a mihon hash isn't an ancestor of SY's branch, so
  `<mihon-hash>..tachiyomiSY/master` lists SY's whole history (for `8c480c6355` on `data/src/main/java`, 150 commits
  against 51 on mihon).
- **Candidates:**
  `git log --reverse --format='%h %ad %s' --date=short <range> -- <files it touched, including new ones>`.
- **References:** commits that mention it, found with `git log <range>` and `-E --grep='#<N>([^0-9]|$)'` (a bare
  `--grep='#309'` also matches `#3090`), `--grep=<short-hash>` or `--grep='This reverts commit <full-hash>'` (for an
  SY copy, grep for both hashes).
- **Read each candidate's diff:** does it change lines this commit added, and is the change a fix (wrong behaviour,
  crash, race, wrong value) rather than a feature or a refactor? Titles help ("fix", "crash", "potentially",
  "revert"), but decide from the code. Example: `f8e82b9322` (Potentially fix 'database is locked' crash) makes the
  driver creation from `8c480c6355` (Switch to AndroidX bundled sqlite driver) a synchronized singleton, five days
  later; Komikku picked both.

A candidate already picked here (its row is `O`) is one of step 1's early picks, not a fix to recommend: say that
step 3 carried its change over, or that this commit leaves its lines alone.

For each fix, give its hash, date and title, the bug it fixes, its status in `cherrypick_log.md`, and whether it
applies on its own or needs earlier commits (name them). Don't pick them; recommend an order.

- **The pick doesn't work without a later fix.** If the pick fails step 5 on its own and a later upstream commit fixes
  that, say so first in the report, and ask whether to pick the fix right after, as its own commit.
- **Later commits that cancel out.** A later commit can turn the change off and another turn it back on, like
  `3d3bd49d1c` (Disable resumable image downloads) and its revert `b2ca6bc5f3` after `d8c3440d37` (Support resumable
  image downloads if supported by source). Name them so the user can mark both rows together.
- **The fix is buried in a big commit.** If the fix, or any other useful change, is a small part of a large commit
  that is otherwise unrelated or can't be picked, don't recommend picking that commit. Recommend folding that change
  into a related pick instead (step 4, Body), and record the plan in both rows' Notes (step 0). Ask first.

## 7. Review and fix

Review every pick once it passes step 5, fix what is worth fixing, and repeat until nothing worth fixing is left. The
fixes stay **uncommitted**: never amend them into the pick or commit them yourself. The user decides where they go
(Report).

1. **Run the review** with Claude Code's built-in `code-review` skill (Skill tool), at `medium` effort each round,
   however small the pick (`low` reads only the diff, so it misses callers and removed behavior). If it isn't in this
   session's skill list, say so and review by hand for correctness and the checks below.
   - **Never pass `--fix`.** It applies every finding, its cleanups included, and this step decides what is worth
     fixing. Never use `max` or `ultra` unless the user asks: each round would start many reviewers, and `ultra` is a
     billed cloud review only the user can start.
   - **Target:** `code-review` takes the current diff, a PR, a branch or a path, not a single commit. In round 1, give
     it the branch (args `medium <branch>`) and keep the findings on the pick's lines, plus pre-existing bugs
     (upstream's or Komikku's) in the files and functions the pick touches; note which kind each one is. A finding in
     another commit on the branch belongs to that commit's own review: leave it out here, and list it apart in the
     report if it looks real. From round 2 on, the
     uncommitted fixes are the current diff (args `medium`); name the pick commit (`git show <new-hash>`) as their
     context. Always name the upstream commit the pick came from.
   - **Ask for more than correctness:** step 2's Komikku risks, step 3's adaptation rules, step 4's message rules, and
     bugs in the picked change as it runs in Komikku. It already checks CLAUDE.md / AGENTS.md rules such as KMK
     markers and strings.
   - Its findings feed the sorting below, which decides: a confirmed bug can be one a later upstream commit fixes, and
     a cleanup it suggests in upstream's own lines is not worth fixing.
2. **Add your own findings:** the bugs found while reading, resolving and verifying, including ones the adaptation
   could bring in, such as a migration that skips a Komikku table, a missing KMK case in a `when`, or a string still
   naming Mihon. Each finding needs a concrete failure scenario and its evidence (test output, file and line).
3. **Sort each finding:**
   - **Worth fixing:** a wrong or missing adaptation (step 3), something that breaks Komikku (step 2), a bug the pick
     brings that no later upstream commit fixes (step 6), a step 5 check that fails on a pick the user chose to keep.
   - **Not worth fixing**, with the reason, such as: a later upstream commit fixes it (recommend picking that commit
     instead, step 6, so later picks still apply); style in upstream's own lines (changing them makes later picks
     conflict); a false positive (say why); no effect for Komikku users.
   - **Outside the pick's lines:** a finding in code the pick doesn't touch is judged the same way, not dropped. It is
     worth fixing when it is a real bug for Komikku users, no upstream commit Komikku lacks fixes it, and the fix is
     small. Search all of `mihon/main` and `tachiyomiSY/master` for such a fix, before `<hash>` as well as after it
     (`git log -G '<the buggy line>' mihon/main -- <file>`, then its row's status); if one exists, recommend picking
     it instead (step 6), since a Komikku fix would conflict with it later. When the fix is large or belongs to
     another area, don't fix it here: recommend a separate PR (step 8) and say why.
   - **In the commit message:** a message can't stay uncommitted. Write the corrected message to a scratchpad file,
     report it, and use it only if the user chooses to fold.
   - **Kept in `ISSUES.md`:** a real bug left unfixed here (not worth fixing now, left to a separate PR, or waiting
     for an upstream commit Komikku hasn't picked) gets an `ISSUES.md` entry in the file's format (**Severity**,
     **Where**, **What happens**, **How to hit it**, **Fix**, **Upstream**), as an uncommitted change that goes
     wherever the fixes go. A false positive, style in upstream's own lines, or a bug a later pick in this run fixes
     gets none. Put the entry next to the entries it relates to: the same file or class first, then the same feature
     area (extension installer, downloads, WebView, Cloudflare, sync, …) or the same upstream commit. Only an entry
     related to none goes at the end. Name commits by hash and subject, and files and functions, not line numbers.
4. **Fix** the worth-fixing ones in the working tree, outside the pick's lines included. They are Komikku changes, so
   AGENTS.md applies in full: KMK markers on every line of upstream code you change, new strings in `i18n-kmk/`
   (`KMR`). A fix that solves an `ISSUES.md` entry deletes that entry in the same change, as step 4 does for a pick,
   so the entry goes wherever the fix goes. Don't stage or commit them.
   Then re-run the step 5 checks that cover the files you changed (for code, always `spotlessCheck` and
   `assembleDebug`).
5. **Repeat** 1-4 until a round finds nothing worth fixing. A finding already judged not worth fixing doesn't count
   again. If round 5 still finds new things, stop and report what is open.
6. **Several picks:** keep each pick's fixes apart, and within them split by the recommendation below, not by where the
   fix is. Before the next pick, save them as two patches: `<scratchpad>/review-fixes-<short-hash>-fold.patch` for the
   fixes recommended to fold, and `<scratchpad>/review-fixes-<short-hash>-separate.patch` for all the others (a Komikku
   fix on the pick's lines that goes beyond upstream's change, every fix outside them, and the `ISSUES.md` entries
   added). `git add -N` any new file first, then `git diff HEAD -- <files>` for each, splitting a file's hunks between
   the two when it has both. Then stash them:
   `git stash push -u -m "review fixes for <short-hash>" -- <the fixed files>`, noting each stash's commit
   (`git rev-parse refs/stash`). The next pick then starts from a clean tree (step 0). After the last pick's review, pop
   these stashes oldest first, each by its commit as in step 0 (step 0's own `unrelated to …` stash comes back last,
   below), so all the fixes are uncommitted in the working tree. If a pop conflicts, keep both sides, run `git reset -q`
   and `git stash drop 'stash@{N}'` (a conflicted pop keeps the stash), and say so. The patch files keep each pick's
   fixes apart, and the ones recommended to fold apart from the rest.

For every finding, on the pick's lines or not, give a recommendation: fix it or not, with the reason, and for a fix,
where it should go:
- **Fold into the pick** when it corrects the pick itself (a missed adaptation, a marker, a migration number or
  version, a rename, or part of upstream's change applied wrong), so the pick commit is what it should have been and
  builds on its own.
- **A separate commit** when it goes beyond upstream's change (a Komikku fix for a bug upstream still has, or an
  improvement), so the pick stays close to upstream's patch (`git range-diff` stays small) and the fix is visible on
  its own in history. A fix outside the pick's lines always goes here, never in the pick: it isn't part of upstream's
  change, and folding it would hide it in a cherry-pick.
- **A separate PR** (step 8) for a fix that is large, in another area, or unrelated to the branch; it isn't made
  here.
- **Both** when the fixes are of both kinds: fold the first kind and commit the rest separately.

Then carry out the user's answer to the Report's question:
- **Fold:** each pick gets only its own fixes recommended to fold. The rest go in a separate commit (below) for "as
  suggested", or stay uncommitted for "fold and leave the rest". A fold puts Komikku work in the pick, so its message
  gains the user's co-author trailer (step 4, Commit) if it doesn't have it yet. Amend with `-F <file>`, where
  `<file>` is the pick's message (`git log -1 --format=%B`) with the trailer added and the corrected message applied,
  if any; use `--no-edit` only when the message already has the trailer and needs no correction.
  - One pick, at the branch tip: stage only the fixes recommended to fold (`git add <files>`, or
    `git apply --cached <partial patch>` for part of a file), then `git commit --amend -F <file>`. The other fixes
    stay unstaged for the separate commit.
  - Several picks: stash all the fixes (`git stash push -u -m "review fixes, all"`), then for each pick, earliest
    first: `git switch --detach <pick>`, `git apply -3 <its -fold patch>`, `git commit --amend -F <file>` (that
    pick's message, as above), `git rebase --onto HEAD <pick> <branch>`, which replays the later commits and checks
    out `<branch>` again. Each fold rewrites the later picks, so find the next one's new hash by its subject. Git
    before 2.44 has no `--autosquash` without `-i`, and `-i` needs an editor. Keep the tree clean between folds:
    `git switch` and `git rebase` refuse or misplace uncommitted changes. Only after the last fold, apply the
    `-separate` patches (`git apply -3`), for the separate commit (below) or to stay uncommitted for "fold and leave
    the rest". Drop the stash only once every fold is done and checked.
- **Separate commit:** one commit after the picks, with a Komikku subject (`fix(<area>): …`) and a body line naming
  the pick it relates to: `Follow-up to <hash> (<subject>).` for fixes to the pick, and
  `Found while reviewing the pick of <hash> (<subject>).` for fixes outside its lines, which the pick didn't cause.
  With several picks, it takes the `-separate` patches, plus the `-fold` ones the user chose not to fold. It has no
  `(cherry picked from commit …)` line, because the log would count it as part of the pick, and no `Co-Authored-By`
  trailer.
- **Both:** split the changes by file, or by hunk with `git apply --cached <partial patch>` (`git add -p` is
  interactive), then fold one part and commit the other.
- **Nothing:** leave the fixes uncommitted.

After any of these, every new or rewritten commit must show `G` in `git log --format='%h %G? %s' master..HEAD`, and
step 5's checks run again on the branch tip.

Last, if step 0 stashed anything, pop it by its commit (step 0). If it conflicts, keep both sides, and say so. On
`cherrypick_log.md` a conflict usually means an uncommitted note and the new `O` mark are on the same row. To resolve
that: edit the row to keep status `O` and the note. Then run `git reset -q`: a conflicted pop leaves every other popped
file staged, and the stashed changes, the note included, must come back unstaged. Then `git stash drop 'stash@{N}'`,
since a conflicted pop keeps the stash.

## 8. Which PR it belongs in

Decide whether the pick can ship in the same PR as the rest of the branch (`git log --oneline master..HEAD`), should
go in a PR of its own, or has to wait until earlier commits are merged. Give the verdict and the reason.

**Has to wait.** The pick needs a prerequisite (step 1) that is only in another open PR or branch. Name that PR and the
commit, and suggest basing this branch on it or waiting for its merge. Prefer that ordering to compatibility code:
don't add a fallback or shim that upstream doesn't have just so a pick works before its prerequisite is merged.

**Better in its own PR.** Check these:
- **Independent:** the commit neither depends on the branch's other commits nor is needed by them. Test it with a
  throwaway worktree from `master`: if the pick applies cleanly there and passes step 5's checks, it can ship alone.
- **Different area:** it touches an unrelated area, such as a tracker fix on a branch about the reader. A separate PR
  keeps review and reverts simple.

**Same PR.** Keep it here when it builds on, fixes, or is needed by the branch's commits, as with an upstream follow-up
fix for an earlier pick.

**Numbered files.** A new SQLDelight migration must not take a number another open PR also adds. App migrations can
share a `version` (`DisabledRepoMigration` and `SyncPrefKeyMigration` are both `80f`), but two PRs must not both raise
`versionCode` for the same release (step 3). List the open PRs that change either:

```bash
gh pr list -R komikku-app/komikku --state open --limit 1000 --json number,title,files \
  --jq '.[] | {number, title, m: [.files[].path | select(startswith("data/src/main/sqldelight/tachiyomi/migrations/") or . == "app/build.gradle.kts")]} | select(.m != []) | "#\(.number) \(.title): \(.m | join(", "))"'
```

For a PR listed with `app/build.gradle.kts`, check whether it raises `versionCode`:

```bash
gh pr diff <N> -R komikku-app/komikku | grep -n '^+.*versionCode'
```

If two PRs take the same migration number or both raise `versionCode`, say so and propose which one renumbers.

**Open PRs in this repo.** List the open PRs that touch the same files:

```bash
gh pr list -R komikku-app/komikku --state open --limit 1000 --json number,title,files \
  --jq '.[] | select([.files[].path] | any(. == "<file>")) | "#\(.number) \(.title)"'
```

For each one, say what it changes that the branch doesn't, whether the pick supersedes it (so it can be closed once
the branch is merged, carrying over anything still useful), and whether they would conflict.

## Report

Name every commit as `<hash>` (<subject>). For each picked commit, report:
- which copy was picked (mihon's or SY's) and why, and for SY's copy, what was brought back from mihon's commit;
- the Komikku verdict from step 2: safe, safe after the adaptation (say which), or would break it (not picked, or kept
  only after the user confirmed);
- any Notes plan from step 0 that you carried out;
- the new hash and subject, whether it is signed, and whether it carries the user's co-author trailer and why;
- every line that differs from upstream's patch and why (`git range-diff <hash>~1..<hash> <new>~1..<new>` shows them),
  KMK blocks included;
- each conflict (or clean merge) in Komikku's own code from step 3: the commit that added it, what it does, and
  whether it was kept as is, adapted or dropped, with the reason (and, for a drop, that the user agreed);
- the log rows marked `O` (or `?` with a `Partial` note), the rows of other commits marked `?` with a `Folded`
  note, the rows given an `Early pick` note (step 1, Earlier upstream commits it jumps ahead of), and the listed rows
  left without one;
- the `ISSUES.md` entries the pick or its fixes cleared (by heading), any it changed and why, any entry that names
  this commit but stays, with the reason, and the entries step 7 added, with the entry each one was placed next to;
- the verification results;
- the later fixes, from step 6;
- the review, from step 7: whether `code-review` ran (at which effort) or the review was by hand, and how many
  rounds; every finding, on the pick's lines or outside them, with its file and line, whether it is worth fixing and
  why, and for a fix, whether to fold it, commit it separately, or leave it to a separate PR; apart, the findings
  that belong to other commits on the branch, each with the commit it belongs to; and the corrected commit message,
  if any;
- which PR it belongs in, from step 8, with the reason, plus any open PR it overlaps.

If step 7 left fixes or a corrected message, ask the user with AskUserQuestion what to do with them: as suggested (fold
the ones that correct the pick, commit the rest separately), fold the ones that correct the pick and leave the rest
uncommitted, put them all in a separate commit, or nothing (leave them all uncommitted). Fixes outside the pick's
lines are never folded, whichever the answer. Anything else goes in the question's free-text answer. Carry out the
answer as step 7 describes.

Nothing is pushed.
