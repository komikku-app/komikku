# core/common/ Module

Foundation module providing networking, security, storage, and shared utilities. No Android framework dependencies except `Context`.

## Network layer

**`NetworkHelper`** – OkHttp client factory with:
- Interceptor chain: `UncaughtExceptionInterceptor` → `UserAgentInterceptor` → optional logging → DoH resolver
- `CloudflareInterceptor`: WebView-based challenge solving (30s timeout)
- KMK: MangaDex cover interceptor, download-with-resume (exponential backoff)
- 12 DNS-over-HTTPS providers in `DohProviders.kt`

**Key files:**
- `Requests.kt` – GET/POST/PUT/PATCH/DELETE builders
- `OkHttpExtensions.kt` – `Call.await()`, `Response.parseAs<T>()`, progress tracking
- `NetworkPreferences.kt` – DoH provider, user agent config

## Security

**`SecurityPreferences`** – Biometric lock, secure screen, DB/CBZ encryption (SY)

**`CbzCrypto`** (in core:archive) – AES-256/128/ZipCrypto via Android KeyStore

**Preference key conventions:**
- `__PRIVATE_` prefix: Excluded from backups
- `__APP_STATE_` prefix: Internal app state, not user-facing

## Storage

**`FolderProvider`** interface + `AndroidStorageFolderProvider` (`/storage/emulated/0/<app_name>`)

**`DiskUtil`** – Disk space, filenames, `.nomedia`, media scan, hash

**`UniFileExtensions`** – `.extension`, `.nameWithoutExtension`, `.displayablePath`

## Shared utilities

| Utility | Purpose |
|---------|---------|
| `CoroutinesExtensions.kt` | `launchUI`, `launchIO`, `withIOContext` |
| `RxCoroutineBridge.kt` | RxJava 1 ↔ coroutines bridge |
| `ImageUtil.kt` | Image detection, splitting, rotation, background color |
| `Hash.kt` | MD5, SHA-256 |
| `StringExtensions.kt` | `chop()`, `truncateCenter()`, natural sort |
| `QuerySanitizer.kt` | Search query normalization |
| `UrlUtils.kt` | URL classification (online/local/embedded) |
| `LogcatExtensions.kt` | `logcat {}` wrapper |
| `Localize.kt` | Moko Resources string resolution |

## Logging

- **KMK code:** `xLogE()` / `xLog()` from `exh.log` (XLog with safe pre-init fallback)
- **Mihon code:** `logcat {}` from `tachiyomi.core.common.util.system`
- **NEVER** use raw `android.util.Log`

## Conventions

- No Android framework dependencies except `Context`
- `InMemoryPreferenceStore` for tests/previews
- Fork markers: `// KMK -->` for Komikku additions
