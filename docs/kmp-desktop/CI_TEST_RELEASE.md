Status: Test and release engineer — done: run 46120905511 read, Linux smoke install fixed, R.2 cause found — left: R.2 fix and its repeated runs, the Windows unit test failures, the macOS smoke design

# Desktop CI: test and release notes

The Test and release engineer's working file (INSTRUCTION 006): results of the desktop CI runs, open CI findings and
designs not built yet. Forge folds what matters into PROGRESS.md / HANDOVER.md.

## Run 46120905511 (`2ee84bfca1`, trigger run 14)

| Job | Result |
|---|---|
| Package: Linux, Windows, macOS ARM, macOS Intel | passed |
| Self-test with real extensions (`extension-check`), all four | passed: the 4 pinned APKs downloaded and verified by SHA-256, `SELF-TEST PASSED`; token `contents: read` only |
| Link check of the Keiyoushi catalogue (`extension-breadth`) | passed: 1699 extensions checked, 0 download failures, report in the artifact |
| Desktop app (compile + tests) (`pr-check`) | skipped, as it should be on a push: it runs on pull requests only, not seen running yet |
| Smoke, Windows | passed, every check, including the second start (exit code 0, window restored, no second window), the library compared with the start and More screens, the checksum count and the log allow-list (nothing needed it) |
| Smoke, Linux | failed at the install: the package's post-install script (`xdg-desktop-menu install`) exits with 3, "No writable system menu directory found"; fixed in the smoke script (below) |
| Desktop tests (windows-2022, non-blocking) | failed: 484 tests, 48 failed, 1 skipped (60 of 470 in run 46117892032) |
| Attach to release | skipped (not a release run); the artifact allow-list is not exercised before a release run |

- Linux smoke fix (`fd0807e357`): before the install the script creates `/usr/share/applications` when the runner lacks
  it (a desktop system has it) and records the folders and `sudo`'s `XDG_*` variables in `logs/menu-folders.log`. The
  existing "Menu entry registered" row then shows whether the post-install script registered the entry. A product note
  for the Windows and Linux engineer: jpackage's post-install script fails the whole install on a system without that
  folder (a minimal or headless system); low, since desktop systems have it.
- The real-extension job prints the self-test report twice (the launcher's stdout, then the report file, which the step
  prints because the Windows GUI launcher can drop stdout). Cosmetic, left as is.
- The Windows app log of the smoke run had one line (the single-instance hand-over); the automatic backup error of run
  46117892032 (R.1) did not appear.

## R.2: `ReaderWidePagesUiTest` "rotate wide pages to fit" (cause found)

- **Which wait:** the Linux test report of run 46117892032 (artifact `desktop-test-report-linux-X64-…`) has the full
  trace: line 207, the wait for the wide page (`page-2`) to be taller than wide. Everything before it took about 1.4 s,
  so the page was shown and stayed wrong for 15 s; slowness alone does not explain it. In the last 17 desktop runs it
  failed once (the other UI test failures were other tests, once each).
- **Not reproduced by load:** 10 runs of the class under 6 busy loops on this Mac, and runs with the wide page's
  download delayed by 0.3 / 1 / 3 s, all passed.
- **Cause (Android code, `HttpPageLoader`, also on `master`):** two reader threads (`eh_reader_threads`, default 2)
  take pages from one priority queue, and a page can be in it several times (every `loadPage` caller queues it: the
  paged viewer's preload of the neighbours and its shown slot, plus the `preloadNextPages` of earlier pages). A thread
  skips an entry only when the page is no longer `Queue`, and it marks the page as downloading only after a chapter
  cache lookup. Two threads that take the page within that window both download it. The chapter cache's editor goes
  to the first; the second one's `putImageToCache` gets no editor and returns without writing, and that thread sets
  the page's stream and `Ready` while the image is not in the cache. The desktop reader decodes on `Ready`, gets
  `FileNotFoundException` and shows the error placeholder; the first thread's later `Ready` changes nothing (same state),
  so the page never decodes again. Likelier on a cold JVM (first test of a fresh test JVM, first cache access), which
  is where it failed. Android's reader has the same path (decode on `Ready`).
- **Shown deterministically:** a scratch test holding the chapter cache's lock while two `loadPage` calls queue the
  same page: both threads download it (2 requests) and at the first `Ready` the image file does not exist
  (`FileNotFoundException …/chapter_disk_cache/<key>.0`), final state `Ready`.
