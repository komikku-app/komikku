# Compat layer registry

Every class of `:desktop:android-compat` (under its Android / library name), with its kind and how many extensions
need it (ATLAS_INSTRUCTIONS.md, ANSWER 010, design point 1). `CompatRegistryTest` fails for a class that is not
listed here, for an entry that is not a class of the layer, and for any other kind. The classes under
`app.komikku.desktop.compat` are the desktop implementation behind the layer (contexts, preference stores, cookies,
logs) and are not listed.

**Kinds**

- *type only*: exists so classes link (interfaces, annotations, constants, types of fields); nothing to do at run time.
- *no-op*: calls are accepted and do nothing; its KDoc says so.
- *real behaviour*: does on desktop what it does on Android, as far as stated in the notes. **A new class of this kind
  needs the Architect's approval** (a `QUESTION` with the number of extensions that need it and its size).

A member that cannot work on desktop and would mislead if it did nothing throws `UnsupportedOnDesktop` (class and
member); the extension manager then lists the extension as "Unsupported on desktop: needs …" in the Extensions tab.

**Extensions** is the number of extensions of Keiyoushi's catalogue that reference the class anywhere in their code
(also in methods they may never call), measured by the link check with `--compat-usage` (BUILDING.md "Extension link
check"; numbers and index commit in `docs/kmp-desktop/EXTENSION_COMPAT.md`). 0 means only the app itself uses the class
(shared Komikku code, the source settings screen).

Every path a context hands to an extension's code (`dataDir`, `filesDir`, `cacheDir`, `getDir`, `getDatabasePath`,
`applicationInfo.dataDir`, `Environment.getDataDirectory()`, …) lies in the extension's own folder
`extensions-data/<package>/`, never in the app's data folder; shared preferences stay in the app's store, which the
source settings screen shares with the extension.

| Class | Kind | Extensions | Notes |
|---|---|---:|---|
| `android.annotation.SuppressLint` | type only | 0 | Annotation. |
| `android.app.Activity` | type only | 1205 | No activity is ever created on desktop; Keiyoushi's `UrlActivity` (deep links) links against it. |
| `android.app.Application` | real behaviour | 1129 | The context over the data folder; `onCreate` / `onTerminate` never called. |
| `android.app.Application$ActivityLifecycleCallbacks` | no-op | — | Lifecycle callbacks registered at start-up are no-op by design: accepted, kept, never invoked (no activities on desktop). |
| `android.content.ContentResolver` | real behaviour | 0 | Streams of `file:` URIs only. |
| `android.content.Context` | real behaviour | 572 | Paths per extension (`extensions-data/<package>/`); no system services (`getSystemService` returns `null`). |
| `android.content.ContextWrapper` | real behaviour | 1449 | Delegates to its base context. |
| `android.content.SharedPreferences` | real behaviour | 538 | Backed by the desktop preference store (one JSON file per name). |
| `android.content.pm.ActivityInfo` | type only | 0 | Screen-orientation constants. |
| `android.content.pm.ApplicationInfo` | real behaviour | 1 | `packageName`; `dataDir` is the extension's own folder. |
| `android.content.res.Resources` | real behaviour | 95 | Display metrics only; no `R.*` resources. |
| `android.graphics.Bitmap` | real behaviour | 270 | Over a Skia bitmap (ANSWER 016): `ARGB_8888` only, premultiplied inside, unpremultiplied `getPixel(s)` / `setPixel(s)`; `createBitmap` (size, rectangle, colours), `copy`, `eraseColor`, `copyPixelsFromBuffer`, `compress` (JPEG, PNG; WebP always lossy, Skiko has no lossless encoder). `recycle()` frees the native memory, a cleaner frees what an extension forgets; usable from any thread. Other configs and members throw `UnsupportedOnDesktop`. Nested `Config` 184, `CompressFormat` 194. |
| `android.graphics.BitmapFactory` | real behaviour | 169 | Skia's codecs (JPEG, PNG, WebP, GIF first frame, BMP) to `ARGB_8888`, no EXIF rotation, `null` for data that does not decode; `Options` (8): `inMutable`, `inPreferredConfig` (`ARGB_8888` only). |
| `android.graphics.Canvas` | real behaviour | 184 | Draws into a mutable `Bitmap` with Skia: `drawBitmap` (rectangles, position; nearest without a paint, filtered with a filtering paint), `drawColor`, `drawLine`, `drawCircle`, translate / scale / rotate / save / restore / `clipRect`. `drawText` throws `UnsupportedOnDesktop` (text drawing not approved). |
| `android.graphics.ColorFilter` | type only | 6 | Base of `ColorMatrixColorFilter`; what `Paint.setColorFilter` takes. |
| `android.graphics.ColorMatrix` | real behaviour | 1 | 4 x 5 matrix (constructors, array, `set`, `reset`); `tenkaiscan` builds one when its source is created (ANSWER 016: rides along). |
| `android.graphics.ColorMatrixColorFilter` | real behaviour | 1 | Skia's matrix colour filter, Android's 0..255 offsets scaled to Skia's 0..1. |
| `android.graphics.Paint` | real behaviour | 182 | Colour, alpha, flags (a new paint filters bitmaps, as on Android 9+), style, stroke width / cap / join / miter, colour filter. Text members (`setTextSize`, `measureText`, …) throw `UnsupportedOnDesktop`; `setTypeface` needs `Typeface`, which the layer does not have. |
| `android.graphics.Rect` | real behaviour | 162 | Integer rectangle (fields, `set`, `width` / `height`, centres, `isEmpty`, `offset`, `contains`, `equals`). |
| `android.graphics.drawable.Drawable` | type only | 35 | Type of the extension icon; no drawing. |
| `android.icu.text.Collator` | real behaviour | 2 | On `java.text.Collator` (strength, decomposition, `compare`, `getInstance`); named in ANSWER 010. Count: extensions that referenced it before it existed. |
| `android.media.MediaScannerConnection` | no-op | 0 | No media index; the callback gets a `null` URI per path. |
| `android.net.Uri` | real behaviour | 1701 | Parsing and building (hierarchical and opaque URIs). |
| `android.os.Build` | type only | 106 | Constants: a generic device on SDK 34. |
| `android.os.Bundle` | real behaviour | 1205 | A string-keyed map (only seen in never-invoked activity callbacks). |
| `android.os.Environment` | real behaviour | 0 | External storage is the home folder; the data directory is the extension's own folder. |
| `android.os.FileUtils` | real behaviour | 0 | `copy`. |
| `android.os.Handler` | real behaviour | 92 | `Runnable` posts on the looper thread; no `Message`s. |
| `android.os.Looper` | real behaviour | 92 | One main looper thread; `prepare` / `prepareMainLooper` / `loop` throw `UnsupportedOnDesktop`. |
| `android.os.StatFs` | real behaviour | 0 | Space of the file system holding a path. |
| `android.os.SystemClock` | real behaviour | 511 | Monotonic clocks, `sleep`. |
| `android.security.keystore.KeyProperties` | type only | 0 | Constants. |
| `android.text.Editable` | type only | 131 | Interface. |
| `android.text.Html` | real behaviour | 23 | `fromHtml` to plain text (jsoup); no spans. |
| `android.text.InputType` | type only | 0 | Constants. |
| `android.text.Spannable` | type only | 1 | Interface; `setSpan` / `removeSpan` are no-ops in its implementations. Count: as for `Collator`. |
| `android.text.SpannableString` | real behaviour | 23 | Immutable plain text; spans are dropped. Count: as for `Collator`. |
| `android.text.SpannableStringBuilder` | real behaviour | 0 | Mutable plain text; spans are dropped. |
| `android.text.Spanned` | type only | 23 | Interface; desktop text carries no spans. |
| `android.text.TextWatcher` | type only | 131 | Interface; never called (no text views). |
| `android.text.format.Formatter` | real behaviour | 0 | `formatFileSize` / `formatShortFileSize`. |
| `android.util.Base64` | real behaviour | 525 | Android's flags (no-wrap, URL-safe, padding). |
| `android.util.DisplayMetrics` | real behaviour | 95 | Density 1 (160 dpi). |
| `android.util.Log` | real behaviour | 1321 | To the desktop log. |
| `android.util.LruCache` | real behaviour | 19 |  |
| `android.view.View` | type only | 206 | Keeps `isEnabled`, `visibility`, `tag`; no view tree. |
| `android.view.ViewConfiguration` | real behaviour | 0 | AOSP default timeouts and sizes. |
| `android.webkit.CookieManager` | real behaviour | 77 | The desktop cookie jar shared with the network stack. |
| `android.webkit.MimeTypeMap` | real behaviour | 0 | Extension ↔ MIME type table. |
| `android.webkit.ValueCallback` | type only | 49 | Interface. |
| `android.widget.EditText` | type only | 157 | Text and input type state for `EditTextPreference`'s bind listener; no widget. |
| `android.widget.TextView` | type only | 157 | Text state; no widget. |
| `android.widget.Toast` | real behaviour | 34 | Shown by the desktop UI (logged by default); `setText(resId)` / `makeText(…, resId, …)` throw `UnsupportedOnDesktop`. |
| `androidx.appcompat.app.AppCompatDelegate` | real behaviour | 0 | Night mode as a flow for the desktop theme. |
| `androidx.core.content.ContextCompat` | real behaviour | 0 | Folders and data dir of the context. |
| `androidx.core.content.SharedPreferencesKt` | real behaviour | 0 | `SharedPreferences.edit { }`. |
| `androidx.core.net.UriKt` | real behaviour | 0 | `File.toUri()`, `Uri.toFile()`, `String.toUri()`. |
| `androidx.core.text.HtmlKt` | real behaviour | 0 | `String.parseAsHtml()`. |
| `androidx.preference.CheckBoxPreference` | real behaviour | 24 | Shown by the source settings screen. |
| `androidx.preference.DialogPreference` | real behaviour | 0 | Shown by the source settings screen. |
| `androidx.preference.DropDownPreference` | real behaviour | 0 | Shown by the source settings screen. |
| `androidx.preference.EditTextPreference` | real behaviour | 191 | Shown by the source settings screen. |
| `androidx.preference.ListPreference` | real behaviour | 109 | Shown by the source settings screen. |
| `androidx.preference.MultiSelectListPreference` | real behaviour | 24 | Shown by the source settings screen. |
| `androidx.preference.Preference` | real behaviour | 530 | Shown by the source settings screen; `setTitle(@StringRes)` / `setSummary(@StringRes)` throw `UnsupportedOnDesktop`. |
| `androidx.preference.PreferenceCategory` | real behaviour | 0 | Shown by the source settings screen. |
| `androidx.preference.PreferenceDataStore` | real behaviour | 0 | Persistence of a preference hierarchy. |
| `androidx.preference.PreferenceGroup` | real behaviour | 0 | Shown by the source settings screen. |
| `androidx.preference.PreferenceGroupKt` | real behaviour | 0 | `PreferenceGroup` helpers (`forEach`, `children`). |
| `androidx.preference.PreferenceManager` | real behaviour | 0 | Default shared preferences; preference screens. |
| `androidx.preference.PreferenceScreen` | real behaviour | 532 | Shown by the source settings screen. |
| `androidx.preference.SwitchPreference` | real behaviour | 0 | Shown by the source settings screen. |
| `androidx.preference.SwitchPreferenceCompat` | real behaviour | 314 | Shown by the source settings screen. |
| `androidx.preference.TwoStatePreference` | real behaviour | 0 | Shown by the source settings screen. |
| `com.elvishew.xlog.LogLevel` | type only | 0 | Constants. |
| `com.elvishew.xlog.Logger` | real behaviour | 0 | To the desktop log. |
| `com.elvishew.xlog.XLog` | real behaviour | 0 | To the desktop log. |
| `com.hippo.unifile.FileRandomAccessFile` | real behaviour | 0 | `UniFile`'s random access over a file (internal). |
| `com.hippo.unifile.FileUniFile` | real behaviour | 0 | `UniFile` over a file (internal). |
| `com.hippo.unifile.FilenameFilter` | type only | 0 | Interface. |
| `com.hippo.unifile.UniFile` | real behaviour | 0 | Files and `file:` URIs; `fromResource` throws `UnsupportedOnDesktop`. |
| `com.hippo.unifile.UniRandomAccessFile` | type only | 0 | Interface. |
| `com.hippo.unifile.UriHandler` | type only | 0 | Interface. |
| `nl.adaptivity.xmlutil.core.AndroidXmlReader` | real behaviour | 0 | xmlutil's Android reader over the JVM reader. |
