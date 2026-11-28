# Review of step D1 (instance lock, Linux notifications, new desktop CI jobs)

Status: Reviewer — done: step 1 (one instance per data folder) — left: step 2 (Linux notifications), step 3 (CI jobs)

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
