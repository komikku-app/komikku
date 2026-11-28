# Komikku Desktop — plan to test the Windows and Linux apps

Status: Windows and Linux engineer — done: the plan (sections 0–4, first smoke jobs built from it by the Test and
release engineer), section 5 with `--self-test-notify`, section 6 (the Windows temporary tray icon, built), section 7
(the .deb's install) — left: what only a real Windows run can confirm (section 6), the .deb fix proven by CI
(section 7, .deb and RPM), section 5 in the Linux smoke job (Test and release engineer). The plan was written
before anything was installed or run (ATLAS_INSTRUCTIONS.md, INSTRUCTION 001, track D). The code facts below are
read from `multiplatform` at `9a649a5683`; the facts about products, licences and runners were read from their
vendors' pages on 2026-11-28 (links inline) and can change. Every statement is marked **[known]** (read in the
repository or on a vendor page) or **[guess]** (from general experience, to be checked by the first run). The host for the virtual-machine options is an Apple silicon Mac.

Background: [`BUILDING.md`](BUILDING.md), [`TESTING_MAC.md`](TESTING_MAC.md) (the pass to reproduce),
`.github/workflows/build_desktop.yml`, `desktopApp/build.gradle.kts`, `desktopApp/.../selftest/DesktopSelfTest.kt`.

## 0. Where we stand

- **[known]** CI packages Windows x64 (`windows-2022`: `.msi`, `.exe`) and Linux x64 (`ubuntu-24.04`: `.deb`, `.rpm`,
  `.tar.gz` of the app image) and runs `--self-test` against the packaged launcher on both
  (`build_desktop.yml`, last edit `6bc87481ba`). No Windows arm64 and no Linux arm64 installer is built.
  `:desktopApp:test` (headless Compose UI tests) runs on Linux only; it has never run on Windows (BUILDING.md "Known
  limitations").
- **[known]** The self-test starts no window. It checks the runtime modules, zipfs, dex2jar, crypto, charsets, locale
  data, SQLite, zstd and QuickJS natives and four real extensions. Nobody has installed an installer or opened the
  window on either OS. Untested there: icons, first start, window, tray, notifications, file dialogs, shortcuts,
  uninstall, upgrade.
- **[known]** Native libraries and architectures: QuickJS (`quickjs-kt`) ships natives for Windows x64, Linux x64 and
  arm64, macOS x64 and arm64 but **not Windows arm64** (PROGRESS.md, "1.4 — JavaScript engine choice"). The Compose
  runtime is resolved for the build machine (`ComposePlugin.DesktopDependencies.currentOs`, `desktopApp/build.gradle.kts`),
  so an installer is built on, and for, one OS and CPU. A native Windows arm64 app is therefore not possible today; on
  Windows on Arm the **x64 installer under Windows' own x64 emulation** is what a user would run.
- **[known]** Hosted arm64 runners are free in public repositories: `ubuntu-24.04-arm` and `windows-11-arm`, 4 vCPU,
  16 GB memory ([runner reference](https://docs.github.com/en/actions/reference/runners/github-hosted-runners),
  [changelog 2025-08-07](https://github.blog/changelog/2025-08-07-arm64-hosted-runners-for-public-repositories-are-now-generally-available/)).
  Adding a `ubuntu-24.04-arm` entry to the `package` matrix would give a Linux arm64 `.deb` for a virtual machine on
  the Mac at no cost (the update check already understands `arm64` in asset names, BUILDING.md "App update check").
  Not needed for the first steps; it is the key to the Linux VM option below.
- **[known]** Runner images today: `ubuntu-24.04` image 20261122.23.1 has `Xvfb` but no window manager, no `xdotool`,
  no `scrot` (its readme lists only `xvfb`); `windows-2022` image 20261123.376.1 (Windows Server 2022, build 20348) has
  JDK 21 (`JAVA_HOME_21_X64`), ImageMagick 7 and WiX 3.14 preinstalled.

### Platform differences in the code that the test pass must exercise

All **[known]**, paths relative to the repository root.

| Area | Windows | Linux | Source |
|------|---------|-------|--------|
| Data folder | `%APPDATA%\Komikku` | `$XDG_DATA_HOME/komikku`, else `~/.local/share/komikku` | `core/preference/src/desktopMain/.../DesktopDirs.kt` (`b04aa953a0`) |
| Installer | per-user MSI / EXE (menu group "Komikku", desktop shortcut, directory chooser, fixed upgrade UUID) | `.deb` / `.rpm` (package `komikku`, categories `Graphics;Viewer;`, shortcut), `.tar.gz` app image with no installation | `desktopApp/build.gradle.kts` (`a5bff9973f`) |
| Tray | AWT `SystemTray` / `TrayIcon` (Windows: double click opens the window) | AWT `SystemTray` (XEmbed tray). **GNOME without an AppIndicator extension reports no tray**; then no icon and **no system notification at all** (the code has no fallback to `notify-send`) | `desktopApp/.../notification/DesktopTray.kt` (`57023979ac`) |
| Notifications | `TrayIcon.displayMessage` (balloon / toast) | `TrayIcon.displayMessage` (AWT draws its own balloon next to the tray icon [guess]) | `DesktopTray.kt`, `DesktopModules.kt` (osascript only on macOS) |
| File dialogs | AWT `FileDialog` = the native Win32 dialog; folders: Swing `JFileChooser` | AWT `FileDialog` (look depends on the JDK's toolkit [guess]); folders: Swing `JFileChooser` | `desktopApp/.../platform/DesktopFileDialogs.kt` (`81c414ff2a`) |
| Secret storage | **none**: app-lock PIN is a salted PBKDF2 hash in the preferences; CBZ / database keys are files in `<data>/keys/` with an owner-only ACL | same, with POSIX `rw-------` / `rwx------` | `core/archive/src/desktopMain/.../CbzKeyStore.kt` (`ab4960b11e`), `desktopApp/.../security/DesktopAppLock.kt`; preference files are plain JSON (no encryption code in `core/preference/src/desktopMain`) |
| Single-instance lock | **none found** (no lock file, no socket): double-clicking twice starts a second process on the same data folder, and `DesktopPreferenceStore` says two instances over one file do not see each other's writes | same | search of `desktopApp`, `core`, `app-shared` for a lock / `ServerSocket` found nothing relevant |
| Shortcuts | Ctrl+1…5 switch Library / Updates / History / Browse / More (Meta is ignored) | same | `desktopApp/.../window/DesktopShortcuts.kt` (`f9059e55e9`) |
| Quit | window close button, tray "Exit", Alt+F4; no OS quit handler (macOS only) | window close button, tray "Exit"; no quit handler | `desktopApp/.../platform/DesktopAppEvents.kt`, `Main.kt` (`80d70c97dc`) |
| App lock triggers | window minimised and restored (no Cmd+H) | same; depends on the window manager reporting "iconified" | `Main.kt` `KomikkuWindow` |
| Window | default 1280 x 800, minimum 480 x 360, size / position / state persisted | same | `desktopApp/.../window/DesktopWindowPreferences.kt` |
| Window icon | `Window(...)` in `Main.kt` sets **no `icon`**: the title-bar / taskbar icon may be Java's default instead of the Komikku icon (the installer's `.ico` covers the launcher exe) | the `.desktop` icon is set by the package; the running window's icon and its `WM_CLASS` matching are unverified | `Main.kt`; **[guess]**, first thing to look at |
| System browser / folder | `Desktop.browse` / `Desktop.open` (Explorer) | same through `xdg-open`; fails quietly with no handler (headless, some minimal desktops) | `desktopApp/.../platform/DesktopBrowser.kt` |

## 1. Options

Common to all: the app needs no network to start. A small fixture library can be made without a source: the local
source reads manga folders from the storage folder (`<data>/storage/local/<series>/<chapter>/<images>`; **[guess]**
the exact folder, check `LocalSourceFileSystem` in the first run). Real sources, extension repositories and trackers
need the Internet and are not part of an automated check.

### 1.1 Hosted runners with a scripted UI smoke test (recommended start)

| | `ubuntu-24.04` (x64) + Xvfb | `windows-2022` (x64) |
|-|------|------|
| Needs | the package job's `.deb`; `apt-get install` of `xdotool openbox` (or another window manager), `imagemagick` or `scrot`, optionally `stalonetray`, `fonts-noto-cjk`; JDK 21 only for the helper (`java Tool.java`) | the package job's `.msi`; PowerShell (built in), JDK 21 (preinstalled) for the helper |
| Licence / cost | free (public repository: standard hosted runners are free and unlimited) | free (same) |
| Disk / memory | runner: 4 vCPU, 16 GB, about 14 GB free SSD; the Linux artifact is about 500 MB | same limits; the Windows artifact is about 340 MB |
| Architecture | **x64 only** (what CI builds). arm64 via `ubuntu-24.04-arm` once a matrix entry exists | **x64 only**. `windows-11-arm` is a free runner too, but no native arm64 app exists (QuickJS), so it would run the x64 installer in emulation: a cheap later check of exactly that case [guess that emulation works] |
| Real window, screenshot | **[known]** Xvfb gives a real X11 screen; **[guess]** AWT / Skiko render to it (software rendering may be needed: Skiko reads the `SKIKO_RENDER_API` environment variable [guess]); `import -window root`, `scrot` or `java.awt.Robot` capture it; `xdotool` types, clicks, moves, minimises | **[guess]** the hosted runner has an interactive desktop on which headed browsers run, so a `java.awt.Robot` or `System.Drawing.Graphics.CopyFromScreen` screenshot works; the screen size (maybe 1024 x 768, below the 1280 x 800 default window) and any GPU absence (Skiko's Direct3D falling back to a software adapter) are unknown |
| Driving the UI | `xdotool` (key, mouse, window search by name, geometry, minimise) | PowerShell: `Get-Process` main window title, `user32` through `Add-Type` (move, resize, minimise, close), `SendKeys` / `SendInput`; **UI Automation sees the top-level window only**: Compose draws its own controls and exposes no UIA tree to my knowledge [guess], so controls are reached by keyboard (Ctrl+1…5, Tab) or by coordinates, not by name |
| Faithful to a user's machine | medium: real installer, real launcher, real JRE, but no desktop environment (no GNOME, no real tray, no real file-dialog look), one resolution, no GPU | medium: real installer and launcher; Windows Server, not a client edition; no SmartScreen / Mark-of-the-Web, no Defender prompts as on a downloaded file |
| Known to work | **[known]** the same runner runs `:desktopApp:test` (headless Compose scenes) and the packaged `--self-test` | **[known]** the same runner packages and self-tests; **never** run `:desktopApp:test` |

A cross-platform driver written once in Java (`java.awt.Robot`: screenshots, keys, mouse) and run with the JDK that the
runners already have avoids two scripts. `xdotool` and PowerShell then only find the window, read its geometry and
minimise / close it.

**Variant A, cheap and independent of any display:** run `./gradlew :desktopApp:test` on `windows-2022` as a non-blocking
job. The existing headless Compose UI tests (about 90 classes) would exercise Skiko off-screen rendering, file paths,
SQLite file locking and temporary-folder deletion on Windows. They prove nothing about the packaged app or a real
window, but they are free to try and likely to find path / file-lock bugs (the tests fork one JVM per class and delete
`build/tmp/test-data`; open SQLite files cannot be deleted on Windows [guess]).

### 1.2 UTM (QEMU, Apple Hypervisor / Virtualization framework) virtual machines on the Mac

UTM is free from its [GitHub releases](https://github.com/utmapp/UTM) (the App Store copy is a paid convenience
version, same code [guess]). Both guests run **natively arm64** (fast); an x64 guest would run
under QEMU emulation, which UTM's own guide calls single-core and slow
([UTM Windows guide](https://docs.getutm.app/guides/windows/)). A real window can be driven and screenshotted in all
VM cases: inside the guest as in 1.1 (`xdotool`, Robot), from outside with the host's `screencapture` (a macOS
permission is needed [guess]) or the guest reached over SSH.

**Linux arm64 guest (Ubuntu 24.04 or a similar desktop image)**

- **Needs:** UTM (about 1 GB), an arm64 ISO (Ubuntu Server about 3 GB, Desktop about 6 GB), a virtual disk that grows
  with use: roughly **10 GB** with a light desktop (Xubuntu / GNOME on Xorg), more for full Ubuntu Desktop; delete the
  ISO after the install. Memory 4 GB for the guest [guess]. All free.
- **Architecture:** the Linux arm64 `.deb` needs the `ubuntu-24.04-arm` matrix entry (1.1); the x64 `.deb` does not run
  natively. x64 under Rosetta in a Linux guest is offered by UTM's Apple-Virtualization backend [guess], not worth it:
  CI covers x64.
- **Fidelity:** high for "a Linux desktop user": a real window manager, a real desktop environment, tray / no tray as
  on that desktop, real file dialogs, real keyboard, HiDPI scaling, fonts. Choose GNOME on Xorg to match the most
  common default and to see the missing-tray behaviour; a second small image with XFCE shows the working tray. Wayland
  sessions run the app through XWayland, where `xdotool` does not drive native windows reliably [guess]: use Xorg for
  automation.
- **Known / guess:** UTM running Ubuntu arm64 is a mainstream, documented use **[known]**; JVM and Skiko on virtio-gpu
  with software GL (llvmpipe) **[guess]**.

**Windows 11 on Arm guest**

- **Needs:** UTM, an ISO (Microsoft's Arm64 ISO, [download page](https://www.microsoft.com/en-us/software-download/windows11arm64),
  about 5 GB; CrystalFetch builds one too), the SPICE / UTM guest tools, and roughly **25–30 GB** of virtual disk after
  install and updates [guess], 4 GB memory minimum for the guest [guess]. UTM's guide says Windows must be
  licensed ([UTM guide](https://docs.getutm.app/guides/windows/)).
- **Licence and cost (current, 2026-11-28):**
  - Microsoft's own page ([Options for using Windows 11 with Macs](https://support.microsoft.com/en-us/windows/experience/platform-variants/options-for-using-windows-11-with-mac-computers-with-apple-m1-m2-and-m3-chips))
    names **Parallels Desktop** (a paid product) as the authorised solution for Windows 11 Pro / Enterprise on Apple
    silicon, and says each Windows 11 Pro instance, physical or virtual, needs its own licence (retail purchase).
  - Installing from the free ISO without a key runs, with a watermark, no personalisation and delayed updates, but
    Microsoft's licence terms (section 5) authorise use only with a properly activated licence
    ([Microsoft Q&A](https://learn.microsoft.com/en-us/answers/questions/3995129/can-i-use-windows-11-on-vmware-without-activating));
    **I would not do this without the maintainer's decision.**
  - **Free and licensed: the evaluation edition.** The [Evaluation Center](https://www.microsoft.com/en-us/evalcenter/evaluate-windows-11-enterprise)
    offers **Windows 11 Enterprise 26H2 as an Arm64 ISO** (and x64) for a **90-day evaluation**, no product key,
    registration with a Microsoft account required, "for IT professionals evaluating for an organisation". After 90
    days (or without activation) the desktop turns black and the machine shuts down every hour. The fit for a
    maintainer testing software is arguable; the maintainer should confirm. Microsoft's old free developer VM
    downloads are gone (the pages were removed in 2026 [per a third-party report, unchecked]).
  - Azure also offers Windows on Arm VMs, which is paid.
- **Architecture:** the guest is arm64; Komikku has **no arm64 Windows installer** (and the QuickJS native is missing
  for it), so the guest runs the **x64 `.msi` through Windows 11's x64 emulation** (Prism on 24H2 and later
  [guess]). That is also what a real Windows on Arm user would do, so it is a faithful test of that case, and the
  only way to run the Windows app in a VM here. Whether the JVM and Skiko work under emulation is a **[guess]**
  until tried.
- **Fidelity:** high for the window, installer, SmartScreen, Start menu, taskbar icon, tray overflow, toast
  notifications, native file dialogs, HiDPI. Drive it with PowerShell inside the guest (OpenSSH server is available
  as a Windows feature [guess]) and screenshot with `CopyFromScreen`; or by hand.
- **Disk:** the Windows image plus ISO is the largest item in this document, roughly 30 GB on the host (QUESTION in
  section 2).

### 1.3 Containers with a virtual display (Linux only)

- **What:** Colima (free, MIT, a Lima VM) with the Docker CLI, or Docker Desktop (free for personal and small-business
  use under its licence, paid above its size limits [guess]); a container image (Ubuntu 24.04) with Xvfb, a window
  manager, `x11vnc` + noVNC, `xdotool`; the `.deb` installed inside.
- **Needs:** `colima` and `docker` (about 0.5 GB), a Colima VM with a small disk (`--disk 10`, grows on use) and 4 GB
  memory, the image (about 1–2 GB with the JRE-free `.deb` installed). Free.
- **Architecture:** native arm64 containers on the Mac; x64 containers via Rosetta (`colima start --vm-type vz
  --vz-rosetta`) or QEMU user emulation [guess; a JVM under emulation is slow and has had crashes]. The `.deb` for arm64
  must come from the CI arm64 entry (1.1) or be built in the container (Gradle plus about 1 GB of downloads and the
  build tree: more disk than the whole VM option; not recommended).
- **Real window, screenshot:** yes, as in 1.1 (Xvfb), plus a live view from the host browser through noVNC and
  `docker cp` for screenshots. Same limits as the runner (no desktop environment, no real tray unless
  `stalonetray` is started).
- **Fidelity:** the same as the Linux runner, on arm64; it adds nothing the runner does not give, except a fast local
  loop without pushing to CI and a place to try the `.rpm` (`dnf install` in a Fedora image) and `.tar.gz` (plain run).
- **Known / guess:** Colima with Docker on Apple silicon is common **[known]**; a JVM Compose app on Xvfb in a container
  is **[guess]** (the same question as 1.1).

### 1.4 Not recommended

- **Remote access to a hosted runner** (an SSH or tmate action in the workflow): third-party code with network access
  to the runner and its token; it would also be an outward change to the workflows. Screenshots as artifacts give the
  same evidence.
- **Wine** for the Windows app on Linux or the Mac: tests Wine, not Windows.
- **x64 Windows or x64 Linux guests under QEMU emulation:** single-core and very slow with a JVM, no benefit over the
  hosted x64 runners.
- **Boot Camp / dual boot:** not available on Apple silicon.

## 2. Recommendation

No purchase, no licence, no large download is needed for steps 1 to 4. Steps 5 and 6 are options for the maintainer.

1. **Run `:desktopApp:test` on `windows-2022`** as a non-blocking job (variant A of 1.1). Smallest change, finds
   Windows path and file-lock bugs in the existing tests, and the report shows which tests are Linux-only.
2. **First CI smoke test on both x64 runners** (section 4): install the real installer, start it, screenshot, drive
   Ctrl+1…5, close it, upload the screenshots and the app log. This puts a first real window of the packaged
   Windows and Linux apps in front of us for the price of a few runner minutes.
3. **Fix what step 2 finds**, starting with the likely ones: no window icon, Skiko without a GPU, the single-instance
   gap, the tray on a desktop without one. Extend the smoke test to window persistence, the app lock by minimise, and
   a fixture library from the local source (reading a chapter).
4. **Add `ubuntu-24.04-arm` (and, as a check only, `windows-11-arm` running the x64 installer) to CI.** Free; gives the
   Linux arm64 `.deb` for step 5 and a first answer to "does the x64 app run under Windows' emulation".
5. **Linux arm64 VM in UTM** (GNOME on Xorg; XFCE as a second image if disk allows): the first look at real file
   dialogs, the real tray (or its absence), HiDPI, fonts and the window manager. Free, about 10 GB of disk. This is the
   only option that adds fidelity at no cost; it needs the arm64 `.deb` from step 4 and UTM from its GitHub releases.
6. **Windows 11 on Arm VM** only with a decision from the maintainer (QUESTIONs below).

### Questions for the maintainer

1. **A Windows VM (UTM, free) needs a Windows licence and a lot of disk.** Options: (a) the Microsoft Evaluation Center
   Windows 11 Enterprise Arm64 ISO, free, 90 days, registration with a Microsoft account, meant for IT professionals;
   (b) a retail Windows 11 Pro licence (a purchase); (c) no Windows VM, relying on the hosted `windows-2022` and
   `windows-11-arm` runners plus a person with a Windows PC. Which is acceptable? Does the maintainer want to
   register with Microsoft for (a)?
2. **Disk:** Windows needs roughly 30 GB (ISO, disk image); Linux about 10 GB. Is that space available for the VMs,
   or should the VM option be skipped?
3. **Download and install permission:** UTM (about 1 GB) and an Ubuntu arm64 ISO (3–6 GB) for step 5. Colima / Docker
   are not recommended (they add nothing over step 2 plus step 5).
4. **Is a person with a real Windows PC and a Linux PC available** for the items in section 3 that only a screen can
   judge (tray, toasts, file-dialog look, SmartScreen)? The Mac cannot provide Windows client editions natively.

Not a question, but for Atlas to decide: the **Linux notification gap** (no tray, hence no notification, on default
GNOME) and the **missing single-instance lock** are product behaviours, outside this plan's role; they are listed
because the test pass will hit them.

## 3. The test pass per platform

Reference: [`TESTING_MAC.md`](TESTING_MAC.md) "First test pass" (14 steps) and its two re-test lists. Legend for "Where":
**CI-1** = the first smoke test of section 4; **CI-2** = a later extension of the smoke test (fixture library through
the local source, window geometry, minimise); **VM** = needs a machine with a screen and a real desktop (UTM Linux,
a Windows VM, or a person's PC); **Net** = needs the Internet and real sites (never in CI, a person tries it).

| # | TESTING_MAC step | Windows | Linux | Where |
|---|------------------|---------|-------|-------|
| 1 | Start-up | Installer: `msiexec /i … /qn` (per-user, to `%LOCALAPPDATA%\Komikku`; check Start-menu entry, desktop shortcut, `Komikku.exe`). Window titled "Komikku", taskbar icon, log in `%APPDATA%\Komikku\logs\komikku.log`. Look at the title-bar icon (see "Window icon") | `sudo apt-get install ./Komikku-*.deb` (resolves the package's dependencies: tests the `Depends:` list), then the launcher found with `dpkg -L komikku` (**[guess]** `/opt/komikku/bin/`). Window, log in `~/.local/share/komikku/logs/komikku.log`. `.rpm` in a Fedora container; `.tar.gz`: unpack and run `bin/Komikku`. Look at the dock / taskbar icon | window, process, log, screenshot: **CI-1**; icons and shortcuts in menus: **VM** |
| 2 | Window: resize, move, full screen, persisted | Maximise / snap / resize by `user32` calls or by hand; HiDPI 125 / 150 % | `xdotool windowsize / windowmove`; no macOS "full screen" button: the reader's own fullscreen toggle | geometry and persistence: **CI-2** (Linux first, Windows through PowerShell); HiDPI and snapping: **VM** |
| 3 | Extension repo (index) | same; needs the Internet | same | **Net** |
| 4 | Extension install | APK install already covered by the packaged `--self-test` (conversion, verification); the UI path needs Internet | same | self-test: already in CI; UI: **Net** |
| 5 | JavaScript source repo | same | same | **Net** |
| 6 | Browse: popular / latest / search; Cmd-click selects | **Ctrl**-click selects; Ctrl+4 opens Browse | same | shortcut: **CI-1**; rest **Net** |
| 7 | Library: add a title | with the local-source fixture: Browse → local → open → add to library → Library shows it | same | **CI-2** (by keyboard / coordinates) or **VM** |
| 8 | Read: arrows / Space, zoom | Ctrl+scroll instead of Cmd+scroll; check mouse-wheel and key repeat | same | fixture chapter: **CI-2** (key input, screenshots differ per page); feel: **VM** |
| 9 | Download, read offline | disable the network adapter | `nmcli` / VM network off; offline needs real downloads | **Net** and **VM** |
| 10 | Backup and restore (Save / Open dialogs) | **native Win32 common dialog**, standard UIA-visible controls; file name field, Save button | AWT's Linux dialog (look unknown); type the path into the name field with `xdotool` | **VM**; automatable later (Windows UIA, Linux `xdotool`) |
| 11 | Settings: theme, reader setting, folder chooser | folder chooser is a **Swing `JFileChooser`** (Metal look, no native "New Folder" behaviour of macOS's panel); test creating a folder inside it, which is what failed on the Mac in M2 | same Swing chooser | **VM** |
| 12 | Tray / notifications | tray icon lands in the hidden-icons overflow by default [guess]; menu "Open Komikku" / "Exit"; a library update shows a toast / balloon from "Java" or "Komikku" (name unverified); "Show system notifications" off → none | tray works on KDE / XFCE / GNOME with an AppIndicator extension; **on stock GNOME there is no tray and no notification** (expected, document it); AWT draws the balloon itself [guess]. Under Xvfb with `stalonetray` the tray exists and the balloon is in the screenshot | tray present / balloon visible on Xvfb: **CI-2**; real desktops: **VM** |
| 13 | App lock (PIN, minimise, restore) | minimise button, taskbar click: lock screen asks for the PIN; no Cmd+H | minimise through the window manager or `xdotool windowminimize`; depends on the WM reporting "iconified" | needs a PIN set through the UI: **CI-2**; WM variations: **VM** |
| 14 | Quit and relaunch | close button, Alt+F4, tray Exit (no Ctrl+Q); log tail clean, no half-written backup; second launch **with the first still running** (no lock exists) | close button, tray Exit; same checks | close + log + clean exit code: **CI-1**; relaunch persistence: **CI-2**; double launch: **CI-1** (record what happens) |
| M2-1 | APK extension install | self-test covers it | same | CI (existing) |
| M2-2 | Narrow window | 480 x 360 minimum; check tab row | same | screenshot at minimum size: **CI-2**; judging it: person |
| M2-3 | Folder picker | the Swing chooser (no macOS panel) | same | **VM** |
| M2-4 | Library-update notification | as step 12 | as step 12 | **VM** |
| M3-1…4 | Real extensions, quit during update, app lock after hide, folder + notification | extensions: **Net** + the self-test's four real APKs; quit during a library update / backup: needs a running job (**CI-2** with the fixture, kill-time measured); no Cmd+H on these OSes | same | mixed |

Additional items that only exist on Windows / Linux (add to the pass):

- **Uninstall and upgrade:** Windows `msiexec /x` or Apps and features; the user data in `%APPDATA%\Komikku` must
  remain; installing a newer MSI over an older one must upgrade it (fixed `upgradeUuid`: needs two builds, **CI-2**).
  Linux `apt-get remove komikku`, then check the menu entry is gone; data in `~/.local/share/komikku` remains.
- **Unsigned installer warnings:** Windows SmartScreen on a downloaded `.msi` / `.exe`; Linux none. **VM / person**.
- **Default data folder and the `komikku.dataDir` override:** run once with the default folder (disposable on a runner)
  and once with `-Dkomikku.dataDir=…` via `JAVA_TOOL_OPTIONS`. **CI-1.**
- **Key file permissions:** after setting an app-lock PIN or an encrypted setting that creates `<data>/keys/*.key`:
  `icacls` (owner-only ACL, and the app still reads it) on Windows, `stat -c %a` (600 / 700) on Linux. **CI-2.**
- **Fonts:** Japanese / Chinese / Korean titles need a CJK font on Linux (`fonts-noto-cjk`): a minimal install shows
  empty boxes. A local-source fixture with a Japanese folder name tests it. **CI-2** with the font installed, and
  without it as a recorded negative.
- **Updater:** the update check picks `.msi` / `.exe` on Windows and `.deb` / `.rpm` / `.tar.gz` on Linux
  (`DesktopAppUpdateCheckerTest`, existing); only a person sees the "update available" dialog.
- **Window icon, `WM_CLASS` and the taskbar group on Linux:** **VM**.
- **Rendering without a GPU:** Xvfb and a VM have none; record whether the app falls back to software Skia or fails.
  **CI-1 / VM.**

## 4. First CI smoke test (design only, nothing implemented)

**Where:** a new job `smoke` in `.github/workflows/build_desktop.yml`, `needs: package`, matrix `ubuntu-24.04` and
`windows-2022`, started by the same marker bump as the package jobs (a separate dispatch-only workflow cannot be started
off the default branch, see the comment in the workflow). It downloads the package job's artifact
`Komikku-desktop-<os>-X64-<sha>` instead of building again, so it needs no Gradle, no JDK setup for building and no
signing; JDK 21 only runs the helper. Owner: the Test and release engineer role, who also owns the workflow file.

**Steps (both OS unless noted):**

1. Download the artifact, verify `*-SHA256SUMS.txt`.
2. Install. Windows: `msiexec /i Komikku-*.msi /qn /l*v install.log`; Linux: `sudo apt-get update`, then
   `sudo apt-get install -y ./Komikku-*.deb xdotool openbox imagemagick fonts-noto-cjk` (the `.deb`'s dependencies are
   resolved by apt, which is itself a check). Record `dpkg -L komikku` / the installed file list to the log.
3. Linux: `Xvfb :99 -screen 0 1920x1080x24 &`, `DISPLAY=:99`, start the window manager (`openbox`). Without a window
   manager AWT focus and minimise do not behave; start `stalonetray` too in the CI-2 tray check, not in CI-1.
4. Start the installed launcher in the background with the **default** data folder (the runner is disposable) and a
   JVM argument to log at debug level if useful; capture its stdout / stderr to a file. First run without any Skiko
   renderer override; if no window appears within the timeout, one retry with `SKIKO_RENDER_API=SOFTWARE`, and the
   job records which one was needed (a finding, not a pass).
5. Wait (poll, at most 90 s) for a top-level window titled `Komikku`: Linux `xdotool search --name '^Komikku$'`,
   Windows `Get-Process` main-window title through PowerShell. Read its geometry.
6. Screenshot 1 (`start.png`). Drive **Ctrl+4, Ctrl+5, Ctrl+1** with the Robot helper (or `xdotool key` /
   `SendKeys`), a screenshot after each (`browse.png`, `more.png`, `library.png`).
7. Start a **second instance** of the launcher and record what happens (second window or not) in the log; this
   documents the missing single-instance lock without asserting a behaviour.
8. Close the window (`xdotool windowclose` / `WM_CLOSE` through PowerShell / Alt+F4), wait up to 20 s.
9. Uninstall (`msiexec /x`, `apt-get remove`) and check the install folder is gone and the data folder is still there.

**Asserted** (each a separate named check in a summary written to `$GITHUB_STEP_SUMMARY`; the job fails on any of them):

- the installer exits 0, the launcher file exists, a menu / Start-menu shortcut exists (Windows: `.lnk`; Linux: the
  `.desktop` file from `dpkg -L`);
- a window titled `Komikku` appears within the timeout, with a width and height at least 480 x 360;
- the log file exists at the OS default path and has no line with an uncaught exception (`Exception in thread`, `FATAL`);
  the lines `System tray: …` are recorded, not asserted;
- screenshot 1 is not blank: more than 1000 distinct colours inside the window rectangle;
- after each of the three shortcuts the window region differs from the previous screenshot by more than 2 % of its
  pixels (input reached the app and it recomposed), and `library.png` is similar to `start.png`;
- the process exits within 20 s of the close request, exit code 0, and the log's tail shows no exception;
- uninstall leaves the data folder.

**Not asserted** in the first version: exact pixels (no golden images: clock, animation, fonts and the runner image
change), text in the screenshots (no OCR in CI-1; `tesseract` can be added on Linux later), tray, notifications, dialogs.

**Uploaded** (`if: always()`, artifact `smoke-<os>-<sha>`, 14 days): the four screenshots plus one of the desktop if the
window did not appear, `komikku.log` and the previous log, the launcher's stdout / stderr, `install.log` / the apt log,
the installed file list, Xvfb and window-manager logs (Linux), the summary file. A few MB per run.

**Runtime cost:** no build; per OS about 2–4 minutes of artifact download (340–500 MB), 1–2 minutes of install, 1–2
minutes of waiting for the first start, under 1 minute of driving and upload: about **5–9 minutes per OS, run in
parallel**, so a run adds under 10 minutes of wall time after the package jobs, on free runners. Rough **[guess]**
until the first run.

**Risks:**

- *No display on a runner:* Skiko may not initialise OpenGL on Xvfb or Direct3D on a GPU-less Windows runner; handled
  by the software fallback attempt and recorded as a finding. If the Windows runner has no usable interactive desktop,
  the screenshot step fails and the Windows smoke test is reduced to install, process start, log check and window
  title (still useful).
- *Flakiness:* first start after install is slow (database creation, extension store, update check; the app contacts
  GitHub for the update check with the updater on: network failure must not fail the job); the timeouts are
  generous, the thresholds are relative (2 %), no golden images. A failed run uploads everything needed to tell a real
  failure from a late window.
- *Runner image changes:* images are re-released weekly; apt package versions, the default JDK, ImageMagick,
  screen size and fonts can change; `windows-2022` is an older Windows Server image that GitHub will eventually retire
  (the workflow would need to move to `windows-2025`). Pin by label as today and read the image version in the job
  log (it is printed in the "Set up job" step).
- *Different from a user's machine:* no desktop environment, no real tray, no SmartScreen, Windows Server edition,
  one resolution, no HiDPI, no GPU. A green smoke test means "the packaged app starts, draws and takes keyboard
  input"; it does not replace the VM and person steps of section 3.
- *Coordinate- and key-driven UI* is brittle for anything past the shortcuts (Compose exposes no control tree through
  UI Automation to my knowledge [guess]); CI-2 needs either a stable keyboard route or a small test hook in the app,
  which is a design question for Atlas (and a change in app code: not part of the first smoke test).
- *Cost to other work:* the workflow file is shared with the release pipeline; a failing smoke job must not block the
  release job (keep `release` depending only on `package`) until the test has proven stable.

## 5. Linux notifications in the smoke test (for the Test and release engineer)

Written by the Windows and Linux engineer after ANSWER 005 item 3 and ANSWER 011 (a). **[known]** from the code
(`LinuxNotifier.kt`, `NotificationSelfTest.kt`, `DesktopSystemNotifier` in `MacOsNotifier.kt`); everything about the
runner is **[guess]** until the first run. The workflow and script change is the Test and release engineer's (through
Forge); nothing here is implemented in `.github/` yet.

**What the app does on Linux:** with "Show system notifications" on, every notification goes to
`notify-send --app-name=Komikku -- <title> <body>` when `notify-send` is on the `PATH`, otherwise (or when it fails) to
`gdbus call --session --timeout 5 --dest org.freedesktop.Notifications --object-path /org/freedesktop/Notifications
--method org.freedesktop.Notifications.Notify "Komikku" "uint32 0" "" <title> <body> "@as []" "@a{sv} {}" "int32 -1"`
(GVariant text); when both fail or time out (10 s), the tray balloon, then nothing. A command that timed out is skipped
for ten minutes (review finding 2.1). The body is markup-escaped (`&` → `&amp;`); a server without the `body-markup`
capability shows the entities as they are, a known limitation (finding 2.2: GNOME Shell, KDE Plasma, dunst,
xfce4-notifyd and mako have it; asking `GetCapabilities` would cost a second process per start for the rare others).

**The trigger: `--self-test-notify[=<backend>]`** (ANSWER 011 (a)). Added to `--self-test[=<report>]`, never on its
own (like `--self-test-extensions`; `--self-test-notify` alone starts the app). It runs the usual checks plus one more,
`system notification`: one fixed notification, posted with the app's own command lines, each command killed after
10 s. Title `Komikku self-test`, text `Test notification: "quotes" & <tags> \ ü 漫画 😀` (quotes, markup, a backslash,
non-ASCII and a character outside the BMP, so both encodings are exercised). Without `=<backend>` it is the app's order
(`notify-send`, then `gdbus` after a failure); `=notify-send` or `=gdbus` runs only that one, so both encodings are
checked on one runner without moving files. The check passes when a command exits with 0; the self-test then exits
with 0. A refusal (non-zero exit), a timeout or neither tool on the `PATH` fails the check and the self-test exits
with 1. Report lines (the line before the last one):

```
OK   system notification: notify-send exited with 0 (accepted)
OK   system notification: notify-send exited with 1; gdbus exited with 0 (accepted)
FAIL system notification: java.lang.IllegalStateException: notify-send exited with 1; gdbus timed out
```

Accepted means `org.freedesktop.Notifications` answered `Notify`; that the text arrived intact is what `dbus-monitor`
shows. The plain `--self-test` posts nothing, which the job checks as well.

On the other systems (for later smoke jobs, ANSWER 011 (a)): macOS runs `osascript` and reports its exit code (0 also
when notifications are not allowed for Script Editor, which then shows nothing); Windows adds a temporary tray icon,
calls `TrayIcon.displayMessage`, keeps the icon 8 s and removes it, and reports whether there was a tray, the icon
could be added and the call returned (Windows does not say whether the toast appeared). No `=<backend>` there.

**Setup, inside the Xvfb + openbox step of section 4** (`DISPLAY` set, the `.deb` installed, `$launcher` from the
installed file list as in `desktop_smoke_linux.sh`):

1. `sudo apt-get install -y libnotify-bin libglib2.0-bin dbus-daemon dbus-bin` first, **not dunst yet** (run C needs a
   bus without a notification service; dunst's package installs a D-Bus activation file, so once it is installed any
   `Notify` starts it). `dbus-run-session` is in `dbus-daemon`, `dbus-monitor` in `dbus-bin` [guess: Ubuntu 24.04
   package names; the runner image may have them already].
2. One function runs one self-test inside its own session bus, with a monitor, and keeps the report, the exit code and
   the monitor log apart per run:

```bash
# notify_run <name> <self-test arguments...>; writes $out/logs/notify-<name>.{txt,exit,monitor.log}
notify_run() {
  local name=$1; shift
  dbus-run-session -- bash -c '
    out=$1 name=$2 launcher=$3; shift 3
    dbus-monitor --session "type=method_call,interface=org.freedesktop.Notifications,member=Notify" \
      > "$out/logs/notify-$name.monitor.log" 2>&1 &
    monitor=$!
    sleep 1
    timeout 120 "$launcher" "--self-test=$out/logs/notify-$name.txt" "$@"
    echo $? > "$out/logs/notify-$name.exit"
    sleep 2
    kill "$monitor"
  ' notify-run "$out" "$name" "$launcher" "$@"
}
```

3. Runs, in this order:
   - **C, no notification service:** `notify_run c --self-test-notify`. Expect exit code 1, the report's line
     `FAIL system notification: … notify-send exited with 1; gdbus exited with 1` [guess: a missing service is a
     quick D-Bus error, not a timeout; a timeout would show as `timed out` and is acceptable]; the monitor log may
     show the unanswered `Notify` calls [guess], record it as INFO. This proves the non-zero exit.
   - Then `sudo apt-get install -y dunst` (it draws on the Xvfb display; D-Bus activation starts it on the first
     `Notify`, so no `dunst &` is needed, but starting it in the function before the monitor is harmless).
   - **Silent, plain self-test:** `notify_run silent` (no `--self-test-notify`). Expect exit code 0 and **no**
     `member=Notify` line in `notify-silent.monitor.log`.
   - **A, the app's order:** `notify_run a --self-test-notify`. Expect exit code 0, the report line
     `OK   system notification: notify-send exited with 0 (accepted)`.
   - **B, gdbus:** `notify_run b --self-test-notify=gdbus`. Expect exit code 0,
     `OK   system notification: gdbus exited with 0 (accepted)`. This is the check on a real `gdbus` that the unit
     tests can only mirror (review finding 2.3).
4. **Check in the monitor logs of A and B** (strings only: notify-send adds its own hints, so the arrays differ). Each
   has one `member=Notify` call with `string "Komikku"` (the app name), `string "Komikku self-test"` and a body string
   that contains `&amp; &lt;tags&gt;`, `ü 漫画 😀` and `"quotes"`. Use `grep -F` for each piece rather than the whole
   line: [guess] `dbus-monitor` prints a string between quotes without escaping the ones inside, and the backslash
   should arrive as one `\`; record the body line as INFO, and if `dbus-monitor` escapes, adjust the expectation, not
   the app. A and B must show the same body.
5. A screenshot of the root window after run A or B (`import -window root`) shows dunst's popup: uploaded, not
   asserted. Upload the four `notify-*` triples with the other logs.

**Still for the UI smoke test (UI_SMOKE_DESIGN.md), not the self-test:** the real new-chapter path (`14-new-chapter`:
"Smoke Fixture & Co" gains a chapter, the library update posts "New chapters found") and run D, the setting off
(`desktop_system_notifications` false in the preset: no `Notify` call at all). The self-test checks the backend only;
it does not read the setting.

## 6. Windows notifications with the tray icon off (ANSWER 011 (b), built)

**Why it was needed** (run 46117892032 and the code): **[known]** on Windows `DesktopSystemNotifier` hands every
notification to `DesktopTray.show`, and AWT's `TrayIcon.displayMessage` does nothing for an icon that is not in the
tray (JDK 21 `java.awt.TrayIcon`: the call reaches the peer only when there is one). With the tray icon setting off,
Windows showed no notification at all.

**What was built** (`desktopApp/.../notification/DesktopTray.kt`, `TrayBackend.kt`):
- `DesktopTray` keeps the user's icon (the tray setting, `install` / `remove` from `KomikkuWindow`) and a temporary
  one. `show` with no user's icon adds the temporary icon (the same image, tooltip "Komikku", no menu; a click on it
  or on its toast opens the window with the action `install` was last given — none when the setting was off since
  the start, see the open points), calls `displayMessage` and takes the icon out 10 s after the last notification
  (`TEMPORARY_ICON_MILLIS`) **[guess: Windows shows a toast about 5 s, and taking the icon away may take its toast
  with it, so not earlier]**. A notification during that time reuses the icon and restarts the 10 s (one icon per
  burst). `install` while a temporary icon is up takes it out before adding the user's icon, so there are never two.
  `remove` (the setting switched off, the window gone at the app's exit) takes both out; `quit` does the same and
  adds none afterwards. The first temporary icon registers a JVM shutdown hook that calls `quit` (waits at most 2 s),
  so a notification raised after the window is gone (the exit stops the work in progress) leaves no icon behind.
- Where: Windows, and the Linux fallback after `notify-send` / `gdbus` failed (which so far also needed the user's
  icon). Not on macOS (`osascript`; `temporaryIcons` is off there). "Show system notifications" still decides first
  (`DesktopSystemNotifier`).
- Threads: every change of the icons runs on AWT's event thread (`TrayBackend.serialized`: `invokeAndWait`, or at
  once on that thread), so the window's thread (`install` / `remove`) and a notifying thread never wait for each
  other's lock. The removal timer is one daemon thread (`DaemonTrayTimer`). This differs from the sketch (a separate
  worker): `show` stays synchronous, so its result still says whether the message was handed to AWT.
- The AWT calls (`SystemTray.isSupported`, `add`, `remove`, `TrayIcon.displayMessage`, `setToolTip`) sit behind
  `TrayBackend` (`AwtTrayBackend` in the app); the timer behind `TrayTimer`.
- `--self-test-notify` on Windows now goes through the same temporary icon (no `install`), held 8 s, then `quit`.

**Tests** (`DesktopTrayTest`, a fake backend and timer, any OS): no user's icon (temporary icon added, message shown,
removed when the 10 s end; the exit hooked once); a second message during the wait (icon reused, wait restarted, the
first wait's removal running late changes nothing); the setting switched on during the wait (the temporary icon
removed before the user's is added, at most one icon at any time, the old wait ending changes nothing); the app's
exit (the hook removes it, nothing added afterwards); `remove` with a temporary icon; its click uses the window's open
action; with the user's icon (messages and tooltip go through it, no temporary icon); no temporary icon on macOS or
without a tray; an icon that cannot be added (not shown, tried again next time). The existing tray tests
(`DesktopNotificationsTest`, `MacTrayIconTest`, `NotificationSelfTestTest`) still run against the AWT backend.

**What only a real Windows run can confirm:**
1. That the toast actually appears with the tray setting off (AWT reports nothing back; the self-test passes when
   the icon was added and `displayMessage` returned). The eye, or a screenshot in the Windows smoke job taken within
   5 s of `--self-test-notify`.
2. That a toast shown right after `SystemTray.add` is not lost (no delay between adding and showing is built in).
3. What removing the icon after 10 s does to the toast: whether it vanishes from the screen and from the
   notification centre with it (`NIM_DELETE`), and whether 10 s is enough for a user who set "show notifications
   for" longer in Windows' accessibility settings.
4. That no icon is left in the tray after the app exits during the 10 s (the shutdown hook), and that a burst of
   notifications shows one icon, not a flicker of several.
5. Clicking the temporary icon or its toast opens the window (only after the setting was on once in this run).

**Open:**
- A temporary icon's click does nothing when the tray setting was off since the start: the window's open action is
  only handed to `DesktopTray` by `install`. Giving it always needs a line in `Main.kt` (the Shared-code engineer's).
- The Windows smoke job could run `--self-test-notify` and a step with the tray setting off that raises a
  notification (Test and release engineer).
- ANSWER 011 (c): the setting's summary (`KMR.strings.desktop_tray_icon_summary`, only in `i18n-kmk` base) still
  says notifications appear through the icon. The new text, "Quick access to the app from the system tray or menu
  bar", waits for `i18n-kmk/.../base/strings.xml`, which the Extension runtime engineer holds.

## 7. The Linux package's install (the .deb's maintainer scripts)

**What failed** (run 46120905511, Linux smoke job): `Setting up komikku (1.14.1) ... xdg-desktop-menu: No writable
system menu directory found.`, then `post-installation script subprocess returned error exit status 3`.

**Cause [known]:** jpackage's own maintainer scripts (read from that run's `.deb`, `control.tar`; nothing of ours was
in them) run `xdg-desktop-menu install /opt/komikku/lib/komikku-Komikku.desktop` in `postinst` and `xdg-desktop-menu
uninstall ...` in `prerm`, both under `set -e`. xdg-desktop-menu (xdg-utils 1.1.3-4.1ubuntu3, Ubuntu 24.04; read in
the script) in system mode exits with 3 ("No writable system menu directory found.") unless `XDG_DATA_DIRS`
(default `/usr/local/share/:/usr/share/`) has **both** a writable `applications` and a writable
`desktop-directories` folder; the `desktop-directories` check runs even when no `.directory` file is installed. A
system without a desktop (the runner's server image, containers, minimal installs) lacks them, so the install fails
there, and the removal would fail the same way. The runner environment only exposes it; any such system fails.

**Fix:** `desktopApp/packaging/linux/postinst` and `prerm` replace jpackage's in the built `.deb`
(`deb-maintainer-scripts.gradle.kts`, applied from `desktopApp/build.gradle.kts` since `299b94603a`). `postinst` still tries
`xdg-desktop-menu install`; when that fails it copies the entry to `/usr/share/applications` (creating the folder),
and when that fails too it warns; it never fails the install. `prerm` runs `xdg-desktop-menu uninstall` without
failing and removes the copied entry. Compose passes jpackage its own `--resource-dir` after any extra argument
(jpackage keeps the last) and clears that folder in the task, so jpackage cannot be given the scripts as templates:
the `packageDeb` / `packageReleaseDeb` tasks unpack the `.deb` with `dpkg-deb` (which jpackage needs to build one),
replace the two scripts and pack it again (`--root-owner-group`). The build fails if jpackage's scripts run anything
but the one menu command (a later jpackage, file associations, services), so nothing is dropped unnoticed.

**Evidence on this Mac** (no container runtime here, and a `.deb` is only built on Linux): the scripts run against
Ubuntu 24.04's real `xdg-desktop-menu` on a scratch root (`XDG_DATA_DIRS` pointing into it, `--mode system` for root,
the absolute paths mapped into it in a copy):

| Folders on the system | jpackage's postinst / prerm | ours |
|---|---|---|
| neither (the runner) | exit 3 / exit 3 | exit 0, entry copied / exit 0, entry gone |
| `/usr/share/applications` only | exit 3 / exit 3 | exit 0, entry copied / exit 0, entry gone |
| both (a desktop) | exit 0 / exit 0 | exit 0 through xdg-desktop-menu / exit 0 |
| neither, `/usr/share` not writable | — | exit 0 with a warning / exit 0 |

The second row means the smoke runner's workaround (fd0807e357 creates only `/usr/share/applications`) does not get
jpackage's script through unless the runner already has `desktop-directories`.

**What CI must prove:** the `.deb` from a build with the fix installs and uninstalls on a system without either
folder: the Linux smoke job's "Install (.deb with apt-get)" and uninstall steps with the workaround removed (the
runner lacks them, run 46120905511), checking `/usr/share/applications/komikku-Komikku.desktop` after the install and
its absence after the removal; plus `dpkg-deb -e` of the built package showing our scripts.

The Gradle part was compiled and applied on this Mac through an init script, but it configures nothing here (macOS
registers no `.deb` task), so its unpacking and packing has not run yet: the first Linux package build shows it.

**The RPM** (we build and release one: `TargetFormat.Rpm`, `Komikku-<v>-linux-x64.rpm`). Its scriptlets, read from
run 46120905511's RPM header (`/bin/sh`, no `-e`): `%post` is `xdg-desktop-menu install ...`, `%preun` is
`xdg-desktop-menu uninstall ...`. Without the two folders `%post` exits with 3 (rpm warns, dnf reports an error)
and `%preun` exits with 3, which stops the erase: the package could not be removed. jpackage's `%preun` also runs in
an upgrade, after the new package's `%post`, and takes away the entry that was just installed. Fix (`ceb5f5b93c`):
`packaging/linux/rpm-post.sh` and `rpm-preun.sh` replace the two scriptlets (the same fallback as `postinst`;
`%preun` only on an erase, `$1 = 0`). The `packageRpm` / `packageReleaseRpm` tasks pass jpackage `--temp` so it
keeps its working folder, replace the scriptlets in its spec (`SPECS/komikku.spec`) and run `rpmbuild` again with
jpackage's own arguments (`_sourcedir` = its `images/`, `_rpmdir`, `_topdir`, `_rpmfilename`), then delete the
folder. The build fails when the spec's `%pre`, `%post` or `%preun` differ from what jpackage 21 writes for us, or
any line runs another `xdg-` command.

Evidence on this Mac: the scriptlets against Ubuntu's real `xdg-desktop-menu` (same scratch root as above):
jpackage's exit 3 for `%post` and both `%preun` cases without the folders; ours exit 0, copy the entry and remove it
on an erase only; with both folders both pass, and jpackage's `%preun` in an upgrade loses the entry. The Gradle part
ran in a stand-alone build against a stand-in `packageRpm` (a spec rendered from jpackage 21's `template.spec`, a
fake `rpmbuild` recording its arguments): the spec handed to `rpmbuild` has our scriptlets and `%pre` unchanged, the
arguments are jpackage's, and a spec with an extra `xdg-mime` line stops the build with the line in the message.
What only Linux CI shows: that the real `rpmbuild` rebuilds the package from jpackage's folder (`rpm -qp --scripts`
on the built RPM shows ours), and an install / erase on a system without the folders (no smoke job installs the RPM
yet; a Fedora container on the runner would).
