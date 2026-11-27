# M2 review: open finding (needs a Mac)

The code review of `cbdfdff..365f98a` (the M2 fixes) found seven problems. Six were fixed by the cloud session on
2026-11-27 (PROGRESS.md "Review fixes (M2)"). This one needs a Mac to check, so it was left for the session on the
maintainer's Mac ([`MAC_SESSION_HANDOVER.md`](MAC_SESSION_HANDOVER.md)).

## Open: the folder panel may stay open when the app locks

- **File, line:** `desktopApp/src/main/kotlin/app/komikku/desktop/platform/DesktopFileDialogs.kt`, ~line 78 in the
  reviewed range (`chooseDirectoryWithMacPanel`, `whileUnlocked(locked, close = dialog::dispose)`; ~line 85 after the
  review fixes).
- **Problem:** the folder panel possibly staying open when the app locks (`dialog::dispose` vs the native
  NSOpenPanel). On macOS the folder picker is an AWT `FileDialog` shown with `apple.awt.fileDialogForDirectories`,
  which AWT backs with a native `NSOpenPanel` run modally. When the app lock engages while it is open, `whileUnlocked`
  calls `dialog.dispose()` on the event thread. It is not known whether disposing the AWT peer closes the running
  native panel. If it does not, the panel stays on screen over the lock (the user could still browse folders), and
  `isVisible = true` keeps blocking until the user closes it. The result is dropped either way (`takeUnless { locked }`),
  so no folder is applied while locked.
- **How to check on a Mac:** in the packaged app (or `./gradlew :desktopApp:run` with a separate data folder, see
  TESTING_MAC.md "Data and logs"), set an app lock with a short timeout, open Settings → downloads folder so the
  native folder panel shows, then make the app lock (minimise or Cmd+H and come back, or wait for the timeout). Check
  whether the panel disappears when the lock screen appears, and whether choosing a folder in a panel that stayed open
  changes the setting (it must not). Repeat with the open-file dialog (Settings → restore backup) and the save dialog
  (create backup), which use the same `dialog::dispose`.
- **Suggested fix:** close the panel natively (for example by sending it a cancel through the native window, or by
  closing the AWT window through its peer so the `NSOpenPanel` ends its modal session), or keep `JFileChooser` (which
  `cancelSelection()` closes reliably) while a lock is configured.
