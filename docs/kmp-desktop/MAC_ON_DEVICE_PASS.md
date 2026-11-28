Status: macOS engineer — done: the menu-bar template icon (C2), the pass plan — left: the on-device pass itself

# On-device pass of the packaged macOS app (track C1)

The packaged app image (`:desktopApp:createDistributable`, `Komikku.app`) built from `multiplatform`, started on an
Apple silicon Mac with a fresh data folder per run (`JAVA_TOOL_OPTIONS=-Dkomikku.dataDir=<folder>
Komikku.app/Contents/MacOS/Komikku`). Driven through the screen, without changing any system setting. Real public
sources and extension repositories, read-only browsing, no account logged in, no NSFW content.

Result per item: **passed**, **failed** (steps, what happened, log excerpt, suspected cause, owner proposal) or **not
testable** (why).

## Build under test

To be filled in when the pass starts.

## TESTING_MAC.md "First test pass"

| # | Item | Result | Note |
|---|------|--------|------|
| 1 | Start-up | pending | |
| 2 | Window | pending | |
| 3 | Extension repo | pending | |
| 4 | Extension | pending | |
| 5 | JavaScript source repo | pending | |
| 6 | Browse | pending | |
| 7 | Library | pending | |
| 8 | Read | pending | |
| 9 | Download | pending | |
| 10 | Backup | pending | |
| 11 | Settings | pending | |
| 12 | Tray / notifications | pending | |
| 13 | App lock | pending | |
| 14 | Quit / relaunch | pending | |

## TESTING_MAC.md "Re-test after M2"

| # | Item | Result | Note |
|---|------|--------|------|
| 1 | APK extension | pending | |
| 2 | Narrow window | pending | |
| 3 | Folder picker | pending | |
| 4 | Library update notification | pending | |

## TESTING_MAC.md "Re-test after M3"

| # | Item | Result | Note |
|---|------|--------|------|
| 1 | APK extensions (Hiperdex, Kagane, MangaDex) | pending | |
| 2 | Quit during a library update / backup | pending | |
| 3 | App lock and Cmd+H | pending | |
| 4 | Folder panel and notification (from M2) | pending | |

## HANDOVER.md "Done" screens

| Screen | Result | Note |
|--------|--------|------|
| Library | pending | |
| Manga details | pending | |
| Reader, paged | pending | |
| Reader, webtoon | pending | |
| Updates | pending | |
| History | pending | |
| Browse → Sources | pending | |
| Browse → Extensions | pending | |
| Browse → JavaScript sources | pending | |
| Browse → Migrate | pending | |
| Browse → Feed | pending | |
| Global search | pending | |
| Downloads | pending | |
| Backup and restore | pending | |
| Trackers screens (no login) | pending | |
| Settings pages | pending | |
| Settings search | pending | |
| App lock | pending | |
| Settings → Advanced | pending | |
| Tray | pending | |

## Failures

None recorded yet.

## Fixed during the pass

None yet.

## System prompts for the maintainer

None yet.
