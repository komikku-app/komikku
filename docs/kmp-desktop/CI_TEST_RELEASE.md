Status: Test and release engineer — done: run 46120905511 read, Linux smoke install fixed — left: R.2 (ReaderWidePagesUiTest), the Windows unit test failures, the macOS smoke design

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
