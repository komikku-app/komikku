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

## Checked on the Mac (M3, 2026-11-28): confirmed, accepted

- **Result:** a standalone probe on JDK 21 (macOS 27, Apple silicon) did what `whileUnlocked` does: the folder panel
  (`FileDialog` + `apple.awt.fileDialogForDirectories`), then `dispose()` from the event thread after 3 s. The modal
  `isVisible = true` returned at once (`file = null`, the dialog no longer displayable), so the app is not blocked and
  drops the result as before. The native panel stayed on screen: the system window list still showed the process's
  889 × 440 window 5 s after `dispose()`, until the probe exited. Same with the open-file dialog. JDK's
  `CFileDialog.dispose()` has no native close (the panels are never released), and nothing else in AWT ends the
  panel's modal run.
- **Decision: keep the native panel, accept the leftover panel.** The lock still hides the app's content, and a folder
  or file picked in the leftover panel is dropped (`takeUnless { locked }`); what stays possible is browsing folders,
  which Finder on the same unlocked Mac allows anyway. Closing it natively needs an Objective-C bridge the app does not
  have (no JNA; a helper binary for this is out of proportion). Going back to `JFileChooser` while a lock is set would
  bring back the M2 bug (folders made with New Folder not shown) for exactly the users who set a lock. Revisit if the
  app gains a native bridge (e.g. for notifications under its own name).
