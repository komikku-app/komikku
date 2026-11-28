# Review of step D1 (instance lock, Linux notifications, new desktop CI jobs)

Status: Reviewer — done: steps 1, 2 and 3 reviewed up to `f28941bc1f`, first run of the new jobs (46117892032) read
— left: the `windows-test` result of that run (still running), the containment commits `dc2995cd3d` / `5c6de95a50`
(not covered), re-check the fixes when the owners report

## Summary

| Step | Owner | High | Medium | Low |
|------|-------|------|--------|-----|
| 1. One instance per data folder | Shared-code engineer | 0 | 1 | 3 |
| 2. Linux notifications without a tray | Windows and Linux engineer | 0 | 0 | 3 |
| 3. New desktop CI jobs | Test and release engineer | 0 | 0 | 4 |
| Found by the first run of the new jobs (outside the range) | see each | 0 | 2 | 0 |

Medium:
- 1.1 (Shared-code engineer): a start during the running instance's shutdown is told "shown" and exits, then the old
  process exits too, so nothing runs.
- R.1 (Windows and Linux engineer): the automatic backup fails on Windows ("Couldn't create a backup file"), found by
  the Windows smoke test.
- R.2 (Forge to assign, the reader's test owner): `ReaderWidePagesUiTest` timed out in the blocking Linux `package`
  job, so that run has no Linux packages and the Linux smoke test could not run.

Code review, high effort, 2026-11-28. Reviewer role; the owning roles fix. Reviewed against the committed state of
`origin/multiplatform` at `f28941bc1f` (the commits of `03b9ab6..f28941bc1f`); file and line numbers are at that
commit. Specification: ATLAS_INSTRUCTIONS.md ANSWER 005 items 1, 3 and 4, WINDOWS_LINUX_TEST_PLAN.md section 4.

## Step 1: one running instance per data folder (Shared-code engineer)

**Range reviewed:** `d25196d7bf` feat(kmp): Add a lock that allows one desktop instance per data folder,
`89dd30ad21` test(kmp): Test the one-instance-per-data-folder lock across processes, `ad3411bbee` feat(kmp): Allow one
running desktop instance per data folder.

**Specification check (ANSWER 005 item 4, point by point):** exclusive `FileChannel.tryLock` on a file in the data
folder, held for the life of the process: met (`instance.lock`, released by `close()` or by the OS). Loopback socket
on `127.0.0.1`, random port: met (`ServerSocket(0, 8, 127.0.0.1)`). Port and random token next to the lock file with
owner-only permissions: met on POSIX (`rw-------` at creation), best effort on Windows (finding 1.4). The request
carries the token and only means "show the window": met (one exact line, constant-time compare, nothing parsed).
Exit code 0 after signalling: met. No answer within two seconds → dialog → exit: met (exit code 1). `--self-test`
unaffected: met (checked before `ensure`, `Main.kt` line 75). Different `-Dkomikku.dataDir` unaffected: met (lock in the
data folder). Required tests: second start exits and signals (child JVM), stale lock after a crash (child killed,
stale port file replaced), two data folders side by side: all present. Not in the specification and not met: what a
start does while the running instance is shutting down (finding 1.1) and what happens when the data folder cannot
be locked (finding 1.3).

### 1.1 Medium: a start during the running instance's shutdown is told "shown", exits, and then nothing runs

- **File, line:** `desktopApp/src/main/kotlin/app/komikku/desktop/Main.kt` lines 115–116 (`stopDesktopWork`, up to
  10 s, before `singleInstance.close()`) and 186 (`onShowRequest = null` when the window is disposed);
  `desktopApp/src/main/kotlin/app/komikku/desktop/instance/InstanceSignal.kt` lines 145–154 (answers `OK` before
  looking for a listener; no listener means `pending = true`).
- **Problem:** after the window closes (Cmd+Q, the close button, the tray's Exit), the process keeps the lock and the
  server for the bounded shutdown (backup, library update; up to 10 s). A start in that window (the user closed the
  app and opens it again, or the OS restarts it) gets `OK`, the running instance records a pending request nobody will
  deliver, the new start exits with 0, and a moment later the old process exits too: no Komikku runs and the user saw
  nothing. On Windows and Linux the window is gone at once, so "closed it, open it again" is exactly the case the
  user meets. The class comment (lines 34–37) says the retry loop covers an instance that "may be quitting", but the
  quitting instance answers, so the retry never happens.
- **Proposed fix:** stop answering as soon as the window is gone: close the server (not the lock) when the
  `application` block returns, before `stopDesktopWork` (for example a `stopListening()` that closes the server and
  deletes the port file). The new start then keeps retrying the lock and gets it when the old process exits; if the
  shutdown takes longer than `ANSWER_TIMEOUT` it shows the busy dialog, which is honest. A longer retry while the
  holder is quitting would need a "quitting" answer in the protocol (an Atlas call, since the protocol is specified as
  "only ever means show the window"). Test: a primary whose server was stopped while still holding the lock, a start
  that gets the lock once the primary closes within the timeout.
- **Owner:** Shared-code engineer. **Design-level:** no (the "quitting" answer variant would be).

### 1.2 Low: any local process can hold the single-threaded listener for minutes with a slow request

- **File, line:** `desktopApp/src/main/kotlin/app/komikku/desktop/instance/InstanceSignal.kt` lines 74–86
  (`readRequestLine`), 120–141 (`acceptLoop` / `handle`).
- **Problem:** connections are handled one at a time on one thread, and the read timeout is the socket's
  `soTimeout`, which bounds each `read`, not the request. A client that sends one byte every 0.9 s keeps `handle`
  busy for up to 128 reads (about two minutes); repeating it keeps the listener busy for good. The loopback port is
  reachable by every local user and process without the token. Effect: a real second start waits, gets no answer
  within 2 s, and shows the busy dialog instead of the window. No data risk (the lock still keeps the second start
  out), so low.
- **Proposed fix:** one deadline per connection: compute it when accepted and set `soTimeout` to the time left before
  each `read` (closing at the deadline); optionally hand each connection to a short-lived thread with a small cap.
  Test: a client dripping bytes slower than the timeout, then a real request answered within 2 s.
- **Owner:** Shared-code engineer. **Design-level:** no.

### 1.3 Low: a data folder that cannot be locked runs unguarded, with only a log line

- **File, line:** `desktopApp/src/main/kotlin/app/komikku/desktop/instance/DesktopSingleInstance.kt` lines 144–147
  and 159–173 (`LockAttempt.Unsupported`).
- **Problem:** an `IOException` from opening the lock file or from `tryLock` makes the process the "primary" without a
  lock or listener, so two starts both run on the same database: the data loss the decision exists to prevent. Real
  cases: a home or data folder on NFS without lock support (`ENOLCK`), some SMB / FUSE mounts, a data folder the user
  cannot write. Only a warning in the log, which the user never sees.
- **Proposed fix:** a decision for Atlas: either refuse to start with a dialog naming the reason (strict, but the app
  is unusable on such a mount), or start and show a one-time warning in the app that a second start is not prevented.
  In both cases add a test with a lock file that cannot be created (a read-only folder on POSIX).
- **Owner:** Shared-code engineer. **Design-level:** yes → Atlas (strict or warn).

### 1.4 Low: on Windows the owner-only access list is untested and may name the wrong account

- **File, line:** `desktopApp/src/main/kotlin/app/komikku/desktop/instance/OwnerOnlyFiles.kt` lines 44–62;
  `desktopApp/src/test/kotlin/app/komikku/desktop/instance/DesktopSingleInstanceTest.kt` lines 178–186 (POSIX only).
- **Problem:** the principal is looked up by the bare `user.name`. Windows resolves an unqualified name local
  accounts first, so for a domain user who also has a local account of the same name the single ACL entry names the
  other account: the real user then cannot read its own port file (every second start shows the busy dialog) and the
  other account can. The fallback `Files.getOwner` is not the user either when the account is an elevated
  administrator (new objects are then owned by the Administrators group). The only permission test is skipped on
  Windows, and the new Windows test job would run it.
- **Proposed fix:** look the user up qualified (`%USERDOMAIN%\user.name`, falling back to the bare name), and add a
  Windows test that reads the port file's ACL back: exactly one `ALLOW` entry for the current user, nothing inherited.
- **Owner:** Shared-code engineer. **Design-level:** no.

### Step 1: checked and found fine

- Token: 32 bytes from `SecureRandom`, hex; compared with `MessageDigest.isEqual`; another user cannot read it (POSIX
  `rw-------` set at creation, no window where the file is readable); a wrong token, another command, a missing line
  end, garbage or a 64 KiB request is dropped without an answer (tested).
- Bounded reads: request line at most 128 bytes, port file read with `readNBytes(257)` and rejected above 256 bytes;
  port parsed as 1–65535, token must be 64 lowercase hex digits; garbage in the port file is "no answer" (tested).
- Start-up races: the port file is deleted before the new token is written and is replaced atomically
  (`ATOMIC_MOVE`, plain move as fallback); a start that finds the lock taken but no port file yet keeps retrying until
  the deadline; a request that comes before the window exists is kept and delivered when the callback is set (tested).
- Stale files after a crash: the OS releases the lock; the stale port file is never trusted, because a start only
  reads it when the lock is held by a live process, which has replaced it (tested with `destroyForcibly`). The lock
  file is never deleted (deleting it would be racy), which is right.
- Within one JVM the `held` set prevents a second channel on the lock file (POSIX would release the first lock when
  it closes); keyed by the real path, so different spellings of one folder match.
- Order in `Main.kt`: checked before the log file and before `DesktopInjekt.init()` opens the database; the
  self-test path returns before it. Showing the window runs on the event thread (`invokeLater`), un-minimises,
  `toFront`, and asks macOS for the foreground; behind an app lock the lock screen is what is shown.
- The busy dialog only shows with a display; headless starts exit with 1 after the log line. The new string is in
  `i18n-kmk` base only.
- The client side: `connect` and the answer are each bounded by the time left (on loopback `connect` returns at once,
  so the 2 s hold in practice); any `IOException` is "no answer".
- Tests run the second start in a real child JVM (the lock is per process), with timeouts on every wait and the
  children killed in `@AfterEach`.

## Step 2: Linux notifications without a tray (Windows and Linux engineer)

**Range reviewed:** `835fc05288` feat(kmp): Add a Linux notifier through notify-send or gdbus, `6014c403e1` feat(kmp):
Post Linux notifications without a tray icon, `ea59fe89c4` test(kmp): Test the Linux notifier's commands, fallbacks and
timeouts, `88021f40b9` docs(kmp): Describe the Linux notification checks for the smoke test. Last commit of this role
covered: `88021f40b9` (nothing newer from it on `origin/multiplatform` at `f28941bc1f`).

**Specification check (ANSWER 005 item 3):** the "Show system notifications" setting governs, the tray icon is
separate: met (`DesktopSystemNotifier.show` checks `enabled()` first, the Linux notifier is chosen whatever the tray
setting; tested both ways). `notify-send` when on the `PATH`, otherwise `gdbus call` to
`org.freedesktop.Notifications`: met (`gdbus` is also tried after a failed `notify-send`, which is a sensible
extension). As arguments, never through a shell string: met (`ProcessBuilder(List)`, absolute executable from the
`PATH`, relative `PATH` entries skipped). Time-limited: met (10 s, then `destroyForcibly`). Never blocking the caller:
met (one daemon worker, bounded queue; a full queue goes to the tray at once). Silent fallback to the tray message and
then to nothing: met (logged as warnings only). No new library: met. Windows keeps the tray message: met (tested).

### 2.1 Low: without a notification service each notification costs up to 20 s and the queue falls minutes behind

- **File, line:** `desktopApp/src/main/kotlin/app/komikku/desktop/notification/LinuxNotifier.kt` lines 60–75
  (`post`), 80 (`TIMEOUT_SECONDS = 10`), 125–144 (`gdbusCommand`, no `--timeout`), 219–225 (one worker, queue 16).
- **Problem:** on a session where `org.freedesktop.Notifications` is not answering (no daemon, a hung daemon, D-Bus
  activation that stalls, a minimal window manager), every notification waits for `notify-send` to be killed at 10 s,
  then for `gdbus` (whose own D-Bus timeout is 25 s) to be killed at 10 s, before the tray fallback. With one worker,
  a burst (library update result, several download errors, backup) is shown by the tray minutes late, and the first
  16 are delayed while later ones overflow straight to the tray, out of order. Nothing remembers that the service is
  missing. Not blocking and no data risk, so low.
- **Proposed fix:** pass `--timeout 5` to `gdbus call`; after a command times out (not after a plain non-zero exit),
  skip that command for a while (for example ten minutes, or until the next app start) so later notifications go
  straight to the next one or the tray. Test with the existing fake runner: a timed-out runner is not called again for
  the next notification.
- **Owner:** Windows and Linux engineer. **Design-level:** no.

### 2.2 Low: notification markup entities show literally on servers without `body-markup`

- **File, line:** `desktopApp/src/main/kotlin/app/komikku/desktop/notification/LinuxNotifier.kt` lines 26–27,
  150–152 (`body`).
- **Problem:** the body is always entity-escaped (`&` → `&amp;`). The Desktop Notifications specification makes
  markup a server capability (`body-markup` from `GetCapabilities`); a server without it shows the text as sent, so
  a title like "Kaguya & Shirogane" or "Re:Zero <Ex>" arrives as `&amp;` / `&lt;Ex&gt;`. Most current servers (GNOME
  Shell, KDE Plasma, dunst, xfce4-notifyd, mako) advertise it; minimal or older ones do not.
- **Proposed fix:** ask `GetCapabilities` once (`gdbus call … --method org.freedesktop.Notifications.GetCapabilities`,
  time-limited like the rest) on the worker thread and escape only when `body-markup` is listed; or accept and note
  it in the test plan's section 5 as a known limitation. Either is fine; record the choice.
- **Owner:** Windows and Linux engineer. **Design-level:** no.

### 2.3 Low: the escaping is checked only against hand-written models of GLib, not the real tools

- **File, line:** `desktopApp/src/test/kotlin/app/komikku/desktop/notification/LinuxNotifierTest.kt` lines 320–392
  (`parseGVariantString`, `gStrcompress`); `docs/kmp-desktop/WINDOWS_LINUX_TEST_PLAN.md` section 5 (runs A–D, not
  implemented in `.github/scripts/desktop_smoke_linux.sh`, which records "Notifications: not observed yet").
- **Problem:** the GVariant text and `g_strcompress` handling were read correctly by this review (GLib's string
  parser accepts `\uXXXX` / `\UXXXXXXXX`, `g_strcompress` turns `\\` into `\`, `--` ends GLib option parsing), but the
  unit tests compare against the author's own re-implementation of those parsers, so a shared misreading would pass.
  Section 5 describes the real check (dbus-monitor + dunst under `dbus-run-session`, runs with and without
  `notify-send`), and it is the only proof on a real `gdbus`.
- **Proposed fix:** implement section 5 runs A and B in the Linux smoke test (the trigger question in section 5 item 4
  is open; until there is an in-app trigger, the script can call the exact command lines the unit test pins, with the
  awkward strings of `GVariant strings are ASCII and parse back to the text`, and compare what `dbus-monitor` sees).
- **Owner:** Test and release engineer (workflow and script), with the Windows and Linux engineer's command lines.
  **Design-level:** no (the in-app trigger in section 5 item 4 is a question for Forge / Atlas).

### Step 2: checked and found fine

- No shell anywhere: `notify-send` gets `--app-name=Komikku`, `--`, summary, body; a title starting with `-` is after
  `--`. `gdbus` arguments are double-quoted GVariant strings (never starting with `-`), typed numbers and empty
  containers (`uint32 0`, `@as []`, `@a{sv} {}`, `int32 -1`) matching `Notify(susssasa{sv}i)`.
- GVariant escaping: `"` and `\` escaped, line break as `\n`, everything outside printable ASCII as `\u` / `\U`, so
  the argument is plain ASCII whatever the JVM's argument encoding; control characters other than line breaks are
  removed before (shared `MacOsNotifier.argument`), lone surrogates become U+FFFD, the cut never splits a pair or an
  entity. `notify-send`'s body backslashes are doubled after the entity escaping (entities have no backslash);
  the summary, which `notify-send` does not expand, is left alone. Empty summary becomes "Komikku" (notify-send
  refuses an empty one).
- Process handling: output discarded (no full pipe can stall the child), exit code 0 means posted, a hang is killed
  and waited for (tested with a real child process and its pid checked dead).
- Executor: `ThreadPoolExecutor(0, 1, 30 s, queue 16)` starts its one daemon worker on demand (core size 0 with a
  queue still starts a worker), rejects when full; the rejection and a throwing runner or fallback are caught.
- "Hide notification content" and the locked app: unchanged; titles are removed in `DesktopNotifications` before any
  notifier sees them, so the Linux path inherits it.
- macOS: `MacOsNotifier` itself is unchanged; only `DesktopSystemNotifier` was generalised (`macOs` → `system`), and
  `create` still picks `MacOsNotifier` first when `osascript` is there; the Linux factory is never called elsewhere
  (tested for Windows and macOS with a factory that throws). The changed `MacOsNotifierTest` line only pins the OS.
- Discovery: `notify-send` / `gdbus` looked up once at start-up in the absolute `PATH` entries (a `PATH` with `.` cannot
  make it run a file from the working folder); none found means the tray as before.

## Step 3: the new desktop CI jobs (Test and release engineer)

**Range reviewed:** `bd3e1a9f03` ci(kmp): Run the desktop tests on Windows in a non-blocking job, `71eea8c29d` ci(kmp):
Smoke-test the installed desktop app on Linux and Windows (with `.github/scripts/desktop_smoke_linux.sh` and
`desktop_smoke_windows.ps1`), `13178b7547` docs(kmp): Describe the non-blocking desktop CI jobs. Workflow state
reviewed: `.github/workflows/build_desktop.yml` as of `71eea8c29d` (the last commit touching it on
`origin/multiplatform` at `f28941bc1f`); the containment change for REVIEW_M3.md finding 1 (High) was not committed
yet, so that finding is still open in this state and is not repeated here. It landed afterwards (`dc2995cd3d`
ci(kmp): Run the real-extension self-test in a job without secrets, `5c6de95a50` ci(kmp): Take only the package
matrix's artifacts into a release) and is **not** covered by this review; a quick look at `ec0a061f97` only confirms
that `release` still does not depend on `windows-test` or `smoke` (it now needs `plan`, `package` and the new
`extension-check`). The scripts were reviewed by reading
first, then checked against the first run with these jobs (46117892032, results in R.1, R.2 and below).

**Specification check (ANSWER 005 item 1, plan section 4):** non-blocking `:desktopApp:test` on `windows-2022`: met
(job-level `continue-on-error: true`, so a red job leaves the run green). Smoke test on `ubuntu-24.04` and
`windows-2022` as designed in section 4: met in structure, with deviations (findings 3.2 and 3.3; the fresh data
folder instead of the default one is a deliberate and better deviation). `release` depends only on `package`: met
(`needs: [plan, package]`, line 524; implicit `success()` on those two only). No secrets reachable: met (neither new
job references `secrets.*`; `permissions: contents: read`; the smoke checkout is sparse with
`persist-credentials: false`).

### 3.1 Low: the strict log scan is right, but a network failure of the update check would also fail it

- **File, line:** `.github/scripts/desktop_smoke_linux.sh` lines 485–495; `.github/scripts/desktop_smoke_windows.ps1`
  lines 621–633; the app side: `desktopApp/src/main/kotlin/app/komikku/desktop/updater/DesktopAppUpdateChecker.kt`
  line 121 (`xLogE("Unable to check for an app update", e)`, with the stack trace).
- **Problem:** the scripts fail on any error-level or stack-trace line, stricter than section 4 (which asserts only
  uncaught exceptions and says "network failure must not fail the job"). The strictness paid off at once: in run
  46117892032 it is the only failed check on Windows, and it caught a real app bug (R.1). But the CI packages are
  built with `-Penable-updater`, so a start can check GitHub for an update (`checkOnStart` in `Main.kt`, then the
  scheduler's catch-up); an unauthenticated GitHub API call from a shared hosted-runner address that hits the rate
  limit or a network error logs an error with a stack trace and fails the job for a reason that is not the app's.
  Such runs break the "green on ten consecutive runs" gate of ANSWER 005 item 1.
- **Proposed fix:** keep the strict scan, with a short commented allow-list for known environment errors (the update
  check's network failure), or turn the start-up update check off in the fresh data folder before the start (a
  preset preference file), which also keeps the smoke test off the network, and record that the update check is not
  covered. Update plan section 4 to say errors fail the job.
- **Owner:** Test and release engineer. **Design-level:** no.

### 3.2 Low: the second-instance step of section 4 is now possible but still recorded as "not tried yet"

- **File, line:** `.github/scripts/desktop_smoke_linux.sh` line 438; `.github/scripts/desktop_smoke_windows.ps1`
  line 573.
- **Problem:** section 4 step 7 starts a second instance and records what happens. The scripts skip it because the
  lock "is being built"; it was committed in the same range (`ad3411bbee`). The installed app is the only place where
  the lock meets a real launcher (jpackage on Windows may start the JVM in a child process, which the Windows script
  already detects), so this is where it should be checked.
- **Proposed fix:** while the first instance runs, start the launcher again with the same `JAVA_TOOL_OPTIONS`; assert
  it exits with 0 within about 5 s, that no second `Komikku` window appeared, and record whether the first one came to
  the front. Optionally a third start on another data folder that does open a window (closed again at once).
- **Owner:** Test and release engineer. **Design-level:** no.

### 3.3 Low: the smoke assertions are much weaker than the approved design, and the plan was not updated

- **File, line:** `.github/scripts/desktop_smoke_linux.sh` lines 33–37, 393, 431–435; the same in
  `desktop_smoke_windows.ps1` lines 30–34, 526, 566–571; plan section 4 "Asserted".
- **Problem:** the approved design asserts more than 1000 distinct colours in screenshot 1, a change of more than 2 %
  of the window after each shortcut, and `library.png` similar to `start.png`. The scripts assert 32 colours and
  0.1 % (one thousandth: about 1000 pixels on a 1280×800 window, a caret or a focus ring), and the "back to the
  library" comparison is INFO only. The comment explains why (an empty screen differs from another one in little
  more than its title), which is a fair reason, but a smoke test whose input checks pass on a focus change gives
  little evidence that the shortcuts navigated. Section 4 still lists the old values. Run 46117892032 on Windows
  shows both sides: 259 colours, tab changes of 0.45 %, 0.45 %, 0.75 %, 1.42 % and 1.15 % (the design's 2 % would have
  failed all five), and the library after Ctrl+1 differs from the first screenshot by only 0.15 % against 1.15 % from
  the More screen, so a relative check separates them well.
- **Proposed fix:** assert "library after Ctrl+1 is closer to the first screenshot than to the More screenshot"
  (relative, robust against empty screens) instead of the INFO line, keep the low absolute threshold, and update plan
  section 4 to the values used, so the reader of a green run knows what it proved.
- **Owner:** Test and release engineer (plan section 4 with the Windows and Linux engineer, who owns the plan).
  **Design-level:** no.

### 3.4 Low: the Windows checksum check passes when no line of the sums file could be read

- **File, line:** `.github/scripts/desktop_smoke_windows.ps1` lines 413–421.
- **Problem:** lines that do not match `^<64 hex> [ *]<name>$` are skipped, and the check passes when there are no
  mismatches; a sums file in another format (or one listing only other files) verifies nothing and still passes.
  The Linux script uses `sha256sum -c`, which fails on a file with no valid line.
- **Proposed fix:** count the verified lines; fail when there are none or when the `.msi` that is installed is not
  among them.
- **Owner:** Test and release engineer. **Design-level:** no.

### Step 3: checked and found fine

- First run (46117892032, Windows smoke): every check passed except the log scan (R.1). The window appeared in 4 s
  with the default renderer, keyboard focus held, the shortcuts changed the window, the close gave exit code 0 within
  1 s, uninstall removed the launcher, the default data folder was not created. The `windows-2022` image
  (20261123) has ImageMagick 7.1.2 on the `PATH`, so the Windows script's reliance on `magick` holds (it records the
  version, so an image change shows). The jpackage launcher started the JVM in a child process (launcher pid 7656,
  window pid 7408), which the script detected and followed, so the exit code it checked is the JVM's.

- Non-blocking for real: job-level `continue-on-error: true` on `windows-test` and on the `smoke` matrix; no other
  job `needs` either of them; `release` keeps `needs: [plan, package]` with its comment saying why.
- `smoke` runs `needs: package` with `!cancelled() && needs.package.result != 'skipped'`, so a failed macOS package
  does not stop the Linux / Windows smoke; a missing artifact for its own OS fails the download step, which is right.
  Artifact names match the `package` job's upload (`Komikku-desktop-<asset-os>-<runner.arch>-<sha>`).
- Secrets: no `secrets.*` in either new job, read-only token, `persist-credentials: false` on the sparse script
  checkout. (`windows-test` uses the default checkout, which keeps the read-only token in `.git/config` during the
  test run; harmless with `contents: read`, but `persist-credentials: false` would match the rest.)
- Data folder isolation: both scripts start the installed launcher with `-Dkomikku.dataDir` under `RUNNER_TEMP`
  through `JAVA_TOOL_OPTIONS`, refuse a path with a space (which `JAVA_TOOL_OPTIONS` cannot carry), and check that the
  default data folder (`$XDG_DATA_HOME/komikku`, `%APPDATA%\Komikku`) was not created. The second attempt
  (`SKIKO_RENDER_API=SOFTWARE`) uses its own folder, so the new instance lock does not interfere.
- Uploaded contents: screenshots of a fresh app, the app's debug log on a fresh data folder, launcher stdout / stderr,
  apt / msiexec logs, installed file lists, window lists, a thread dump on a hang. No environment dump, no token, no
  secret in the job; nothing personal on a hosted runner. `windows-test` uploads the test reports only on failure.
- Bash script: `set -uo pipefail` without `-e` by design, every check recorded, an `EXIT` trap that stops the app,
  collects logs, kills Xvfb / openbox, uninstalls and writes the summary also after a fatal check; the app's pid is the
  JVM (`exec` in the subshell), so `wait` gives the real exit code; `compare` exit codes (0 / 1 / 2) and scientific
  notation handled; timeouts on every wait (window 120 s, exit 30 s, settle 30 s, step 20 min, job 30 min).
- PowerShell script (read only): `Set-StrictMode 3`, `Stop` preference with one outer `try` / `catch` / `finally`
  that always stops the app, collects logs, uninstalls and writes the summary; native programs through
  `ProcessStartInfo.ArgumentList` (no quoting through a string), stdout read asynchronously while stderr is read, so
  no pipe deadlock; `msiexec` is waited for (unlike a bare call from PowerShell); the process handle is taken so
  `ExitCode` is available; a JVM started by the launcher in another process is detected through the window's pid. The
  C# block compiles by reading (P/Invoke signatures match user32 / kernel32, `ClientToScreen` with `ref POINT`,
  `WM_CLOSE` posted, not sent). The `.msi` is `perUserInstall = true`, which matches `/qn` without elevation and the
  per-user Start-menu path checked.
- BUILDING.md bullet: accurate for the committed workflow.

## Found by the first run of the new jobs (outside the reviewed range)

Run 46117892032 (`1ea2cd728e`, the build Forge started for this step), read with `gh` while `windows-test` was still
running. Package: macOS (both), Windows passed, Linux failed; smoke: Windows failed on one check, Linux could not run.

### R.1 Medium: the automatic backup fails on Windows

- **File, line:** `desktopApp/src/main/kotlin/app/komikku/desktop/backup/DesktopBackupService.kt` lines 52–54
  (`storageManager.getAutomaticBackupsDirectory()?.uri?.path?.let(::File) ?: throw IllegalStateException(...)`).
- **Evidence:** Windows smoke, `log-problems.txt` of the artifact `desktop-smoke-windows-X64-1ea2cd7…`: `E/DeferredCoroutine:
  java.lang.IllegalStateException: Couldn't create a backup file at DesktopBackupService.createAutomatic
  (DesktopBackupService.kt:54)`, called from `DesktopBackupJobs.kt:40/100` (the scheduler's catch-up on the first
  start of a fresh data folder). So on Windows the automatic backups directory resolves to nothing and no automatic
  backup is ever written, silently for the user (a log line only). Linux and macOS do not show it in their runs so
  far.
- **Proposed fix:** find why `getAutomaticBackupsDirectory()` is `null` on Windows; my guess, unverified, is the
  `file:` URI / path round trip of the storage folder (`Uri.parse(...).path` of a `C:\…` path or `file:///C:/…` URI,
  here and in `DataStorageScreen.kt` lines 113–116 and `DesktopBackupService.defaultBackupFile`). Add a unit test with a
  Windows-style storage folder (it runs in `windows-test`).
- **Owner:** Windows and Linux engineer. **Design-level:** no.

### R.2 Medium: a reader UI test timed out in the blocking Linux `package` job

- **File, line:** `desktopApp/src/test/kotlin/app/komikku/desktop/reader/ReaderWidePagesUiTest.kt` line 200
  (`rotate wide pages to fit turns the wide page upright`).
- **Evidence:** Package (ubuntu-24.04), step "Test desktop app": `ComposeTimeoutException`, "433 tests completed, 1
  failed", so the job failed, the run has no Linux packages, and the Linux smoke test failed at "Download packages".
  The previous run on the branch (46099469363) passed with the same test and no reader code changed in this range,
  so it is a flaky test, possibly made likelier by the new tests that start child JVMs in the same Gradle test run
  (more load on a 4-core runner). The other new tests (single instance, Linux notifier) passed on Linux.
- **Proposed fix:** per TEAM_RULES (never loosen, investigate a flaky test): reproduce with the test alone and under
  load, look at which wait timed out, and fix the wait or the code; record it. Until then the Linux `package` job,
  which `release` depends on, can fail on it.
- **Owner:** Forge to assign (the desktop reader's owner). **Design-level:** no.
