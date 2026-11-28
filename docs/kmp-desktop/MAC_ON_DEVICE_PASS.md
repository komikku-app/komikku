Status: macOS engineer — done: the menu-bar template icon (C2), the packaged self-test with real extensions, the
click-only part of the pass (run 1) — left: everything that needs typing or the visible screen, blocked while the
Mac's screen was locked (see "Run 1")

# On-device pass of the packaged macOS app (track C1)

The packaged app image (`:desktopApp:createDistributable`, `Komikku.app`) built from `multiplatform`, started on an
Apple silicon Mac with a fresh data folder per run (`JAVA_TOOL_OPTIONS=-Dkomikku.dataDir=<folder>
Komikku.app/Contents/MacOS/Komikku`). Driven through the screen, without changing any system setting. Real public
sources and extension repositories, read-only browsing, no account logged in, no NSFW content.

Result per item: **passed**, **failed** (steps, what happened, log excerpt, suspected cause, owner proposal) or **not
testable** (why).

## Build under test

Run 1 (2026-11-28): `Komikku.app` from `:desktopApp:createDistributable` on `4b8ca53b7e` (it contains the template
icon, `3a21a859b6`) plus other roles' uncommitted work in the shared tree at that moment (compat-layer `Uri` / `UniFile`
changes, `DesktopExtensionManager`, backup service), built with JDK 21 (the default JDK of this Mac is 27, whose
`jlink` lacks `jdk.random`: `createRuntimeImage` fails with "Module jdk.random not found" unless `JAVA_HOME` points to
JDK 21; the lock wrapper does not set it). macOS 27.2.1, Apple silicon, one 1920 × 1080 display at 1x, light
appearance, a dark wallpaper.

### Run 1: the screen was locked

The Mac's screen was locked during the whole run (frontmost process `loginwindow`). What that allows:
- the app's own window can be captured and clicked (app-scoped tools; it keeps drawing);
- **no keyboard input reaches it** (the app is not active, so macOS drops its key events; a focused text field stays
  empty), so nothing that needs typing (extension store address, search, PIN, names) can be done;
- the menu bar, the Dock, notification banners, native panels (Save / Open / folder) and other windows than the main one
  cannot be seen (a capture of the display shows the lock screen).

The remaining items need the maintainer to unlock the screen; the pass continues on a fresh data folder then.

### Headless checks on this Mac (ANSWER 003 item 3)

- Packaged `--self-test` with the four pinned real extensions (`.github/desktop-test-extensions.txt`, fetched and
  SHA-256-checked by `.github/scripts/desktop_test_extensions.sh`): **passed** — runtime modules, zip FS, dex2jar,
  EC crypto, charsets, locale data, SQLite, zstd, QuickJS; Kagane (18 sources), MangaDex (62), Hiperdex (1), Weeb
  Central (1) convert, verify and create their sources (the classes they reference that the desktop lacks are the known
  `android.webkit` / `android.widget.Button` / `ActivityNotFoundException`, on paths not taken by browsing).

## TESTING_MAC.md "First test pass"

"Locked" = not testable in run 1 because the screen was locked (no typing, no menu bar / Dock / panels); to be done
after the unlock.

| # | Item | Result | Note |
|---|------|--------|------|
| 1 | Start-up | **failed** (minor) | The window "Komikku" opens on the library ("Your library is empty"), no error dialog. **No log file**: see failure F1. Dock icon: locked |
| 2 | Window | locked | Resize / move / full screen need the screen |
| 3 | Extension repo | locked | The "Extension stores" dialog opens and its field takes focus; typing the address does not arrive |
| 4 | Extension | locked | Needs item 3. Headless: the packaged self-test converts and loads Hiperdex, Kagane, MangaDex and Weeb Central |
| 5 | JavaScript source repo | locked | The tab and "Add repository" are there; needs typing |
| 6 | Browse | locked | Needs a source |
| 7 | Library | locked | Needs a source |
| 8 | Read | locked | Needs a source |
| 9 | Download | locked | Needs a source |
| 10 | Backup | locked | The Save / Open panels cannot be seen |
| 11 | Settings | locked | Pages open (see below); persistence after relaunch not yet checked |
| 12 | Tray / notifications | partly passed | The menu-bar item exists (accessibility: one item, 28 × 26 points, in the app's extras menu bar) and is removed / added again when "Show an icon in the system tray" is switched off / on. Its look, its menu and notifications: locked |
| 13 | App lock | locked | Setting a PIN needs typing |
| 14 | Quit / relaunch | locked | |

## TESTING_MAC.md "Re-test after M2"

| # | Item | Result | Note |
|---|------|--------|------|
| 1 | APK extension | locked | Headless self-test: passed (no `cant find zipfs support`) |
| 2 | Narrow window | locked | |
| 3 | Folder picker | locked | |
| 4 | Library update notification | locked | |

## TESTING_MAC.md "Re-test after M3"

| # | Item | Result | Note |
|---|------|--------|------|
| 1 | APK extensions (Hiperdex, Kagane, MangaDex) | locked | Headless self-test: all three (and Weeb Central) convert, verify and create their sources |
| 2 | Quit during a library update / backup | locked | |
| 3 | App lock and Cmd+H | locked | |
| 4 | Folder panel and notification (from M2) | locked | |

## HANDOVER.md "Done" screens

| Screen | Result | Note |
|--------|--------|------|
| Library | passed (empty state) | Content: locked |
| Manga details | locked | |
| Reader, paged | locked | |
| Reader, webtoon | locked | |
| Updates | passed (empty state) | Title, refresh button, "No recent updates" |
| History | passed (empty state) | Title, clear button, "Nothing read recently" |
| Browse → Sources | passed (empty state) | Local source only, global search and filter buttons |
| Browse → Extensions | passed (empty state) | "Extension stores" dialog opens and closes |
| Browse → JavaScript sources | passed (empty state) | "Check for extensions", "Add repository" |
| Browse → Migrate | passed (empty state) | |
| Browse → Feed | passed (empty state) | Select, add, order and refresh buttons |
| Global search | locked | Needs typing |
| Downloads | locked | |
| Backup and restore | locked | |
| Trackers screens (no login) | passed | More → Tracking: every tracker listed with Login; MDList, Komga and Kavita disabled with the reason shown |
| Settings pages | partly passed | Settings list, Appearance (theme, tray and notification switches) and Advanced open and render |
| Settings search | locked | Needs typing |
| App lock | locked | Needs typing |
| Settings → Advanced | partly passed | Renders; "Open the log folder" creates the folder and opens it (in Finder, behind the lock screen) |
| Tray | partly passed | See item 12 |

## Failures

### F1 — a fresh start writes no log file (all desktop OSes)

- **Steps:** start the packaged app on an empty data folder, use it for a while (browse the tabs, open settings).
- **What happened:** `<dataDir>/logs/komikku.log` does not exist; "Open the log folder" opens an empty `logs` folder.
  TESTING_MAC.md step 1 ("the log file exists") and the "what to send back" list expect it.
- **Log excerpt:** none (that is the failure); the launcher's stderr shows only the JVM's `Picked up JAVA_TOOL_OPTIONS`.
- **Suspected cause:** `DesktopLogFile` opens its file on the first message, and at the default log level "Minimal"
  (info) nothing is logged during a normal start, so the file is only created once something goes wrong.
- **Owner proposal (Forge; `Main.kt` / `DesktopLogFile` are shared by every desktop OS):** log one info line right after
  `DesktopLogFile.install` in `main` (app version, OS and architecture, Java version, data folder), e.g.
  `xLogI("Komikku <version> on <os> <arch>, Java <version>, data folder <path>")`. The file then always exists and
  every log starts with what a bug report needs. A test in `DesktopLogFileTest`: after `install` and the start-up line,
  the file exists and contains the version.

## Fixed during the pass

None yet (the template icon of C2 was finished before the pass: `3a21a859b6`).

## System prompts for the maintainer

None appeared in run 1. To unblock the rest of the pass, the maintainer needs to **unlock the Mac's screen** and leave
it unlocked while the pass runs. After the unlock, a Finder window showing the empty test `logs` folder may be open (from
"Open the log folder"); it can be closed.
