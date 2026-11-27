# Known issues

Problems found in Komikku's code that are known but not fixed yet, usually because they are rare, minor or need a bigger change than the work that found them. Each entry says where the problem is, how to hit it, what a fix looks like, and whether upstream (mihon / TachiyomiSY) has fixed it.

When you fix an entry, delete it in the same commit. When you find a problem you won't fix now, add it here instead of leaving it in a review or a commit message. Keep each entry self-contained: name files and functions rather than line numbers, which go stale. Entries are grouped by area (downloads, WebView, Cloudflare): add a new one next to the entries it relates to, the same file or class first, and only at the end when it relates to none.

Severity: **race** = only under a thread-timing window, **minor** = small user impact, **leak** = resources kept longer than needed, **convention** = breaks a rule in `AGENTS.md` without a user-visible bug.

---

## `DownloadCache.addChapter` doesn't link source ids that share a download folder

- **Severity:** minor (needs a manga with the same title under two sources with the same folder name)
- **Where:** `addChapter` in `app/src/main/java/eu/kanade/tachiyomi/data/download/DownloadCache.kt`.
- **What happens:** a stored stub source and an installed source can share a download folder name. `renewCache` (mihon `deeb9b2584`, picked as `df0bb4e5c3`) and the disk-cache load (`e7879d8219`) map every id that shares a folder to one `SourceDirectory`. When neither id has a folder at renew time, `addChapter` creates a `SourceDirectory` for `manga.source` alone. The other id stays unmapped until the next renew, and `getTotalDownloadCount` (`distinctBy { it.dir?.uri }`) counts only one copy if the two ever end up as separate objects for the same folder.
- **How to hit it:** have a stub and an installed source with the same name, neither with downloads yet. Download a chapter under the installed one, then look at a same-titled manga of the stub before the cache renews.
- **Fix:** in `addChapter`, before creating a `SourceDirectory`, reuse an existing one whose `dir.uri` matches the folder `provider.findSourceDir(source)` returned, or register the new one for every source id with the same folder name.
- **Upstream:** not fixed; mihon/main and SY master have the same `addChapter`.

## Komikku's WebView renderer-crash handlers log with `logcat`, not `xLogE`

- **Severity:** convention
- **Where:** `onRenderProcessGone` in `app/src/main/java/eu/kanade/presentation/webview/EhLoginWebViewScreen.kt` (inside a `// KMK` block) and in `app/src/main/java/eu/kanade/tachiyomi/ui/setting/connections/DiscordLoginScreen.kt` (a Komikku-only file). Both were added by `d67b3b39b5` when picking mihon `649fc83ffa`, which only had the handlers in `WebViewScreenContent` and `CloudflareInterceptor`.
- **What happens:** nothing user-visible; `AGENTS.md` says "Logging – Komikku code: `xLogE()` / `xLog()` from `exh.log`. Mihon code: `logcat { }`".
- **Fix:** use `xLogE(...)` in both handlers. `DiscordLoginScreen` uses `logcat` elsewhere too (it came from Anikku), so switch those at the same time or leave the file consistent and note why.
- **Upstream:** not applicable (Komikku's own code). SY's `EhLoginWebViewScreen` has no renderer-crash handler.

## The WebView screen never destroys the WebViews of popup windows

- **Severity:** leak (pre-existing, from mihon)
- **Where:** the `WebView` `onDispose` and the `windowStack` in `app/src/main/java/eu/kanade/presentation/webview/WebViewScreenContent.kt`.
- **What happens:** every window a page opens with `window.open` (`onCreateWindow`) gets its own `WebView`, kept in `WebViewWindow.webView` so it can be shown again after going back. Only the WebView currently composed gets `onDispose`, and it is destroyed only when its window is no longer in `windowStack`. When the screen closes with popups open, the window is still on the stack, so no WebView is destroyed. They keep their page, clients and renderer resources until they are garbage collected. Because they stay alive with the shared `webClient`, all of them also get `onRenderProcessGone` when the renderer dies, so that handler must act only once per screen.
- **Fix:** when the composable leaves composition (a `DisposableEffect` over the screen), destroy every window's `webView`.
- **Upstream:** not fixed; mihon/main's dispose logic is unchanged, including after its Navigation 3 rewrite (`c0d3aeaeb5`).

## `CloudflareInterceptor.hostLocks` is never pruned

- **Severity:** leak (small)
- **Where:** the per-host lock map in `core/common/src/main/kotlin/eu/kanade/tachiyomi/network/interceptor/CloudflareInterceptor.kt` (mihon `5a5ff22c37`, picked as `cd262d11d9`).
- **What happens:** `intercept` creates an entry for every host the shared client contacts, before knowing whether a challenge happens, and nothing removes it. Every CDN and image host the app ever reaches keeps a lock object for the life of the process.
- **Fix:** only keep state for hosts that returned a challenge (take the lock only when an entry exists, create it on the first challenge), or use a bounded or weak-valued map.
- **Upstream:** not fixed in mihon/main.

## Animated AVIF pages do not play on Android 8–11

- **Severity:** minor (older Android only; Android 12+ animates them)
- **Where:** `ImageUtil.isAnimatedAndSupported()` in `core/common/src/main/kotlin/tachiyomi/core/common/util/system/ImageUtil.kt` and `AnimatedAvifDecoderFactory` in `app/src/main/java/eu/kanade/tachiyomi/data/coil/AnimatedAvifDecoderFactory.kt`.
- **What happens:** AVIF image sequences (motion webtoons) are decoded by Android's platform `ImageDecoder`, which only supports them from Android 12 (API 31). On Android 8–11 the bundled `tachiyomi.decoder.ImageDecoder` rejects `avis` files, so the page still shows a decode error. Animated AVIF pages above 4 MP are also shown as a static first frame so the two full-size frame buffers stay around 32 MB.
- **How to hit it:** open a chapter with an animated AVIF page on Android 8–11, or an animated AVIF page larger than 4 MP on any version.
- **Fix:** bundle an animated AVIF decoder (for example `io.github.awxkee:avif-coder`) or migrate to `ca.mpreg:imagedecoder`, then feed its frames to a drawable; raise the 4 MP budget if devices can afford it.
- **Upstream:** mihon/main still classifies AVIF as static in its reader (`ImageUtil.isAnimatedAndSupported`), so it has the same limitation.
