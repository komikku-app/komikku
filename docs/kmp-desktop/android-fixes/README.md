Status: Upstream integrator — done: release tag, library category filter and MdList patches — left: nothing

# Android fixes for `master`

Fixes for bugs that are on `master` (Android) too, found while porting to the desktop app (ANSWER 003, DECISION 004).
One bug per patch. Each patch is plain `git format-patch` output of one commit on top of `master`
`15610e67e4a13e20f104bc515923ddcdc6a92fdc`; its last lines carry that commit as
`base-commit: 15610e67e4a13e20f104bc515923ddcdc6a92fdc`.

Apply on `master`:

```bash
git am --3way docs/kmp-desktop/android-fixes/<name>.patch
```

The patches have no leading "Applies to master …" line: `git am` reads the file as a mailbox and takes such a line as
the start of a message without headers, then stops with `fatal: empty ident name (for <>) not allowed`. The
`base-commit:` trailer names the commit instead. Every patch was checked with `git am --3way` on a detached
`origin/master`; the result is identical to the commit it was made from. The patches are exported with
`--no-signature` (the default signature line names the local git build). Blank context lines in a patch are a single
space, so the `.editorconfig` here keeps editors from trimming them.

Gates for each patch, run in a separate `master` checkout: `spotlessApply`, `spotlessCheck`, `:app:compileDebugKotlin`
and the patch's tests. The new tests were written before the fix.

## `app-release-odd-tag.patch`

`fix(updater): Skip a release whose tag is not a version instead of failing the update check`

- **Bug:** `GetApplicationRelease.isNewVersion` turns a tag into numbers with `toInt()` and indexes the new version's
  parts by the old version's. A tag without a number (`nightly`), a preview tag with a dot (`r12.4`) or a shorter tag
  that is equal so far (`v1.0` against `v1.0.1`) throws (`NumberFormatException`, `IndexOutOfBoundsException`).
  `isNewVersion` runs over every fetched release, so one such release makes the whole check fail.
- **On Android:** while such a release is among the fetched ones, no update is ever offered. "Check for updates" in
  About shows the exception's message as a toast; the automatic check at startup only logs it.
- **Reproduced on `master`:** by the unit tests only (no odd tag is published at the moment).
- **Fix:** such a tag counts as not newer; a missing version part counts as 0. Valid tags compare as before.
- **Test:** three new cases in `domain/src/test/.../release/interactor/GetApplicationReleaseTest.kt`: a `nightly` tag
  next to a valid `v2.0.0`, a `v1.0` tag against `v1.0.1`, a preview `r12.4` tag next to `r2000`. Before the fix all
  three failed with the exceptions above; the four existing cases passed.
- **Gates:** `:domain:testDebugUnitTest --tests '…GetApplicationReleaseTest'` 7 tests, 0 failed; `spotlessApply`,
  `spotlessCheck`, `:app:compileDebugKotlin` successful.

## `library-update-category-filter.patch`

`fix(library): Stop updating only the Default category when refreshing under a category filter`

- **Bug:** with a category filter that includes categories, the library shows one ungrouped page (a placeholder
  category with id 0), but `LibraryScreenModel.State.groupType` keeps the preference (by default `BY_DEFAULT`).
  `LibraryTab`'s refresh passes the shown category to `LibraryUpdateJob` when `groupType` is `BY_DEFAULT`, so it
  passes the placeholder, and the job updates the manga in category 0, the Default category.
- **On Android:** filter the library by a category, then pull to refresh or use "Update category": the snackbar says
  the category is updating, but only the manga in the Default category are updated (none of the filtered ones,
  unless they are also in Default).
- **Reproduced on `master`:** by reading the code path (`LibraryScreenModel` grouping, `LibraryTab.onClickRefresh`,
  `LibraryUpdateJob.addMangaToQueue`); not run on a device.
- **Fix:** the state exposes `effectiveGroupType`, the grouping actually shown (ungrouped under a category filter with
  included categories, otherwise the preference), and the refresh uses it. The refresh then starts the same update
  as any ungrouped page: the whole library, following the "update library" grouping setting, not only the filtered
  entries. Updating only the filtered entries would be a new behaviour and is left to the maintainer.
- **Test:** new `app/src/test/.../ui/library/LibraryScreenModelStateTest.kt`, four cases for `effectiveGroupType`
  (filter with included categories over `BY_DEFAULT` and over `BY_SOURCE`, no filter, filter without included
  categories). Before the fix it did not compile (the property did not exist). The wiring in `LibraryTab` is a
  Composable and is covered by the compile only.
- **Gates:** `:app:testDebugUnitTest --tests '…LibraryScreenModelStateTest'` 4 tests, 0 failed; `spotlessApply`,
  `spotlessCheck`, `:app:compileDebugKotlin` successful.

## `mdlist-missing-source.patch`

`fix(mdlist): Look up a missing MangaDex source again instead of until a restart`

- **Bug:** `MdList` held `MdUtil.getEnabledMangaDex()` in a `by lazy`, so a `null` (no enabled MangaDex source at
  the first use) was kept for the whole session.
- **On Android:** sources load asynchronously (`AndroidSourceManager` collects the installed extensions in a
  coroutine), so MDList used before MangaDex is loaded at startup (a library update refreshing tracks, for
  example), or before MangaDex is installed or enabled, fails with "Mangadex not enabled" on every refresh, search
  and metadata lookup until the app restarts.
- **Fix:** a `null` is no longer cached; a found source still is. The change is the same text as the `multiplatform`
  branch's commit `7aee688539` (comment included), so merging `master` into `multiplatform` after this patch needs
  no resolution in that block.
- **Test:** a new case in `app/src/test/.../track/mdlist/MdListTest.kt`: `getEnabledMangaDex()` returns `null` and
  then a source; the first `refresh` throws `MangaDexNotFoundException`, the next two reach the source, and the source
  is looked up twice in all (kept once found). Before the fix the second `refresh` threw `MangaDexNotFoundException`
  (the cached `null`).
- **Reproduced on `master`:** by the unit test; not run on a device.
- **Gates:** `:app:testDebugUnitTest --tests '…MdListTest'` 2 tests, 0 failed; `spotlessApply`, `spotlessCheck`,
  `:app:compileDebugKotlin` successful.

## `httppageloader-race.patch`

`fix(reader): Load an online page on one thread at a time so it is never ready before its image is cached`

- **Bug:** `HttpPageLoader` runs `eh_reader_threads` (default 2) threads over one priority queue, and a page is in it
  once per `loadPage` call (the viewer's preload of the neighbours, its shown page, the earlier pages' preload). A
  thread skips an entry only when the page is no longer `Queue`, and marks it `DownloadImage` only after the chapter
  cache lookup, so two threads that take the page in that window both download it. The cache's editor goes to the
  first; the second's `putImageToCache` gets none and returns without writing, and that thread sets the page `Ready`
  while the image is not in the cache yet.
- **On Android:** the viewer decodes on `Ready`, fails (no file yet) and shows the page's error; the first thread's
  later `Ready` is the same state, so nothing retries until the user taps "Retry". Also one wasted download.
- **Reproduced:** on the `multiplatform` branch, where the desktop reader runs this same file: `ReaderWidePagesUiTest`
  failed on it once in CI, and a test that holds the chapter cache's lock while two `loadPage` calls queue one page
  showed two downloads and `Ready` before the file existed. On `master` by the new unit test; not run on a device.
- **Fix:** a set of the pages a thread is loading now: a thread that takes a page another thread is loading skips
  that entry, and one that takes it after the other finished loads it only while it is still `Queue`. The file is
  byte-identical to `multiplatform`'s (commit `0a77c5bc21`, KMK block included), so merging `master` back after this
  patch needs no resolution there.
- **Test:** new `app/src/test/.../ui/reader/loader/HttpPageLoaderTest.kt`: two `loadPage` calls for one page with a
  mocked chapter cache that, like the real one, lets only one writer per key write, and whose lookup waits (at most
  1 s) for a second caller. It checks that the image is cached when the page first turns `Ready` and that it was
  downloaded once. Before the fix it failed (`Ready` with the image not cached); after it, 5 of 5 runs passed. On
  `multiplatform` the reader test package passed 15 of 15 runs under load with the fix.
- **Gates:** `:app:testDebugUnitTest --tests '…HttpPageLoaderTest'` 1 test, 0 failed; `spotlessApply`,
  `spotlessCheck`, `:app:compileDebugKotlin` successful.

## Local branches

The commits are on local branches of the `master` checkout used for the gates (never pushed):
`fix/app-release-odd-tag`, `fix/library-update-category-filter`, `fix/mdlist-missing-source`. The
`httppageloader-race.patch` commit is on the local branch `fix/httppageloader-race` of this repository (made in a
temporary worktree, since removed).
