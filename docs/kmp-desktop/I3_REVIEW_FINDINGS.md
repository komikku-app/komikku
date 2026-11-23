# I3 review findings (open)

Max review of `c259852190^..f49bc4e909` (I3 source adapter). Single-pass review; findings 2–6, 8 and 9 were
reproduced with probes, the rest come from reading the code. **Nothing is fixed yet.** Next action: hand this list
to one fix agent, then re-report each finding with its outcome and delete this file (move what stays open to
`PROGRESS.md`).

Not verified by the review: the R8 shrink of the app with `:source-js` on the classpath.

Paths are under `source-js/src/jvmSharedMain/kotlin/app/komikku/sourcejs/mihon/` unless stated.

## Important

1. **`MangayomiJsSource.kt:80` — shared download folder.** `name` + normalised `lang` make `toString()` (the
   download directory name) equal a Mihon source's for the same site; `DownloadCache` keeps one source id per
   directory name. Fix: a name / `toString()` that cannot equal a Mihon source's, before any download directory
   exists. Latent until I4 registers the source.
2. **`JsPreferenceScreens.kt:53` — `%` in a list summary crashes settings.** AndroidX runs `String.format` on a
   list preference's summary. Fix: a `SummaryProvider` (declared summary, or current entry when blank), or escape
   `%`.
3. **`JsFilters.kt:75` — select filter without options crashes the filter sheet.** `options[0]` on an empty
   array (`SettingsItems.kt:303`). Fix: map an empty select to a header-like JS-backed filter whose `toJs()`
   returns `js` unchanged.
4. **`JsPreferenceScreens.kt:58` — stored value of another type crashes settings.** The widgets read with typed
   getters (`ClassCastException`). Fix: remove / reset a stored value that does not fit the declared kind, by the
   same rule as `SharedPreferencesHostPreferences.getValue`.
5. **`MangayomiJsRuntime.kt:74` — initial metadata read with untyped preferences.** The source is not yet in
   `sources`, so the `declared` lookup is empty or the previous version's; a failing `getHeaders` then leaves the
   headers empty until a preference changes. Fix: type the initial reads with the new extension's declarations, or
   refresh once after registering.
6. **`MangayomiJsSource.kt:170` (and `:178`) — any `NotImplemented` counts as "no latest / no search".** Fix:
   only `member == "getLatestUpdates"` (respectively `"search"`); rethrow the others.

## Nits

7. **`MangayomiJsSource.kt:72` — no runtime-wide limit on concurrent calls** (per-source semaphore only; each
   call is a QuickJS context on its own thread). Fix: a shared semaphore in `MangayomiJsRuntime`.
8. **`source-js/src/commonMain/kotlin/app/komikku/sourcejs/mihon/JsSourceUrls.kt:23` — cover URLs accept
   `file://` / `content://`**; `data:` page URLs fail in OkHttp. Fix: only `http` / `https` and
   protocol-relative.
9. **`MangayomiJsSource.kt:309` — header values with CR / LF / NUL are sent** (`addUnsafeNonAscii` checks only
   the name). Fix: fall back only for values without control characters.
10. **`MangayomiJsSource.kt:148` — a failed metadata refresh is never retried.** Fix: mark stale again, with a
    retry limit or delay.
11. **`MangayomiJsSource.kt:149`, `:311`, `MangayomiJsRuntime.kt:108` — `logcat` instead of `exh.log`**
    (AGENTS.md logging rule). Fix: `xLog` / `xLogW`, or record the exception in AGENTS.md.
12. **`desktopApp/src/test/kotlin/app/komikku/desktop/ui/browse/JsSourceBrowseUiTest.kt:127` — the test never
    closes its `MangayomiJsRuntime`.** Fix: keep it in a field and close it in `stop()`.
