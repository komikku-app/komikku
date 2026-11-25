# Komikku Desktop — testing on macOS

How to get the macOS build of the desktop app (`:desktopApp`), open it, and what to try first. The build is
**unsigned and not notarized** (no Apple Developer certificate exists yet, see [`BUILDING.md`](BUILDING.md#signing)),
so macOS warns before the first start.

Status of the CI build: see [Getting the build](#getting-the-build) (the green run is linked there) and
[`PROGRESS.md`](PROGRESS.md#m1--first-macos-ci-build).

## Getting the build

### a) From CI (no build tools needed)

The workflow **Desktop builder** (`.github/workflows/build_desktop.yml`) packages the app on GitHub's macOS runners.
While the port is on the branch `claude/kmp-multiplatform-support`, a run starts when a push to that branch changes
`.github/desktop-build-trigger` (bump the counter in it) or the workflow file; branch runs never touch a release.

**Build to test:** <https://github.com/komikku-app/komikku/actions/runs/45625758564> (commit `a1d10c5`, all four jobs
green; the first green run was <https://github.com/komikku-app/komikku/actions/runs/45623103294>, without the P23
review fixes). Later runs of the branch are listed under Actions → "Desktop builder" → filter by branch.

1. Open the run on GitHub (Actions → "Desktop builder" → the run), scroll to **Artifacts** (you must be signed in to
   download them).
2. Download the one for your Mac:

   | Mac | Artifact | File inside |
   |-----|----------|-------------|
   | Apple silicon (M1 and later) | `Komikku-desktop-macos-ARM64-<commit>` | `Komikku-<version>-macos-arm64.dmg` |
   | Intel | `Komikku-desktop-macos-X64-<commit>` | `Komikku-<version>-macos-x64.dmg` |

   (Apple menu → About This Mac shows "Chip: Apple M…" or "Processor: Intel".) Each artifact is a `.zip` with the
   `.dmg` and a `…-SHA256SUMS.txt`; check it with `shasum -a 256 -c Komikku-*-SHA256SUMS.txt` in the unzipped folder.
3. Open the `.dmg` and drag **Komikku** into **Applications**.

Artifacts are kept for 90 days (GitHub's default); after that, start a new run.

### b) Building it yourself on a Mac

- **JDK 21** (the version in `.github/.java-version`), e.g. Temurin: `brew install --cask temurin@21`, then
  `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`. The packaged app brings its own Java runtime; the JDK is only
  for building. `jpackage` comes with the JDK; the DMG step also uses macOS's own `hdiutil`.
- Git and the branch:

  ```bash
  git clone https://github.com/komikku-app/komikku.git   # or, in an existing clone: git fetch origin
  cd komikku
  git checkout claude/kmp-multiplatform-support          # existing clone: git checkout -B claude/kmp-multiplatform-support origin/claude/kmp-multiplatform-support
  ```

- Run it straight from the sources (no packaging, no Gatekeeper prompt; the first build downloads about 1 GB):

  ```bash
  ./gradlew :desktopApp:run
  ```

- Build the installer:

  ```bash
  ./gradlew :desktopApp:packageDmg
  ```

  Output: `desktopApp/build/compose/binaries/main/dmg/Komikku-<version>.dmg` (for the CPU of the JDK that ran the
  build). `./gradlew :desktopApp:createDistributable` gives the plain `Komikku.app` in
  `desktopApp/build/compose/binaries/main/app/`, `./gradlew :desktopApp:runDistributable` starts that one.

## Opening an unsigned app

- **macOS 14 and older:** in Finder, right-click (or Control-click) Komikku in Applications → **Open** → **Open** in
  the dialog. Needed once.
- **macOS 15 (Sequoia) and newer:** the right-click way is gone. Double-click Komikku, close the "cannot be opened /
  could not verify" dialog, then **System Settings → Privacy & Security**, scroll to Security: "Komikku was blocked…"
  → **Open Anyway**, confirm with your password, then open the app again and choose **Open**.
- **Fallback** (also when macOS says the app "is damaged and can't be opened", which is what a quarantined unsigned
  app downloaded with a browser can show):

  ```bash
  xattr -dr com.apple.quarantine /Applications/Komikku.app
  ```

A build made on the Mac itself (b) is not quarantined and opens without any of this.

## Data and logs

| What | Where |
|------|-------|
| Everything the app keeps | `~/Library/Application Support/Komikku` |
| Library database | `…/Komikku/databases/tachiyomi.db` |
| Settings | `…/Komikku/preferences/*.json` |
| Extensions (APKs, converted jars) | `…/Komikku/extensions/` |
| Downloads / local source (default, changeable in Settings) | `…/Komikku/storage/` |
| Caches (covers, pages) | `…/Komikku/cache/` |
| **Log** | `…/Komikku/logs/komikku.log` (and the previous `komikku.1.log`) |

In Finder: Go → Go to Folder… (Shift-Cmd-G) → `~/Library/Application Support/Komikku`. Settings → Advanced → "Open
the log folder" opens the log folder; "Log level" in the same screen set to "extra" or "extreme" gives a more detailed
log.

**Clean state:** quit Komikku, then move the folder away (keeps it for later) or delete it:

```bash
mv ~/Library/Application\ Support/Komikku ~/Library/Application\ Support/Komikku.bak   # or: rm -rf …/Komikku
```

To try a second profile without touching the first one, start the app with another data folder:
`JAVA_TOOL_OPTIONS=-Dkomikku.dataDir=/tmp/komikku-test /Applications/Komikku.app/Contents/MacOS/Komikku`
(from the sources: `JAVA_TOOL_OPTIONS=-Dkomikku.dataDir=/tmp/komikku-test ./gradlew :desktopApp:run`). Started from
Terminal like this, the app's log also goes to the terminal.

**Uninstall:** drag `/Applications/Komikku.app` to the Bin, and delete the data folder above.

## First test pass

In this order; note for each step whether it worked. Shortcuts use **Cmd** on macOS (Cmd+1…5 switch Library /
Updates / History / Browse / More).

1. **Start-up:** the app opens a window titled Komikku with the Komikku icon in the Dock; no error dialog; the log file exists.
2. **Window:** resize, move, full screen (green button) and back; quit and relaunch: the size and position are kept.
3. **Extension repo:** Browse → Extensions → add an extension store (its `index.min.json` address) → the list of available extensions fills.
4. **Extension:** install one extension (it may ask you to trust it) → its source shows under Browse → Sources.
5. **JavaScript source repo:** Browse → JavaScript sources → Repositories → add a Mangayomi repository → install one source.
6. **Browse:** open a source → popular / latest load with covers; search for a title; Cmd-click selects in lists.
7. **Library:** open a title → add it to the library (pick a category) → it shows in Library.
8. **Read:** open a chapter → pages load; keyboard arrows / Space turn pages; Cmd+scroll or the reader settings zoom; go back.
9. **Download:** download a chapter → it shows in the download queue and finishes; read it with Wi-Fi off.
10. **Backup:** More → Settings → Data and storage → create a backup (a Save dialog appears) → restore it (an Open dialog) → the library is the same.
11. **Settings:** change the theme and a reader setting; choose another downloads folder (a folder chooser appears); relaunch: the changes are kept.
12. **Tray / notifications:** with the tray icon on (Settings), Komikku has an icon in the menu bar; its menu opens the window and quits; a library update shows a notification.
13. **App lock:** set a PIN in More → Settings → Security and privacy → lock now / minimise with the yellow window button and click the Dock icon → the lock screen asks for the PIN.
14. **Quit / relaunch:** quit with Cmd+Q, start again → library, downloads and settings are still there, and the log shows no exception at the end of the previous run.

## What to send back on a failure

- The log: `~/Library/Application Support/Komikku/logs/komikku.log` (and `komikku.1.log` if it is there).
- For a crash at start-up or anything odd: the Terminal output of the app started from Terminal
  (`/Applications/Komikku.app/Contents/MacOS/Komikku`), or of `./gradlew :desktopApp:run` when built from the sources.
- macOS version (Apple menu → About This Mac) and chip (Apple M… / Intel).
- Which build: the `.dmg` file name and the run (or the commit you built).
- The steps from the list above that failed, what you did and what happened instead.
- A screenshot (Shift-Cmd-4, or Shift-Cmd-5 for a recording) when it is about something on screen.
