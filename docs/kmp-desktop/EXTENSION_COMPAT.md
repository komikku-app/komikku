Status: Extension runtime engineer — done: path traversal, converter, image formats (REVIEW_M3 findings 3, 2, 4); compat layer policy (registry, `UnsupportedOnDesktop`, per-extension data folders, the Extensions tab's unsupported state); catalogue measurement (1,701 Keiyoushi extensions); `Collator`, `Spannable`, `SpannableString` — left: `android.graphics` and `RuleBasedCollator` (wait for the Architect, see below)

# Desktop extension compatibility

What APK extensions need from the desktop host, measured over the Keiyoushi catalogue, and the work on
`:desktop:extension-runtime` / `:desktop:android-compat` that follows ATLAS_INSTRUCTIONS.md ANSWER 010 and
REVIEW_M3.md. Owner: Extension runtime engineer.

## Review findings (REVIEW_M3.md)

Each fix was test-first: the new test was run against the unfixed code and failed there.

- **Finding 3, path traversal (`e702c27`).** A package name from an APK manifest (or a store's index) is accepted
  only with Android's grammar, two or more dotted ASCII Java identifiers (`ExtensionPaths.isValidPackageName`);
  `ExtensionPackage.from` rejects anything else ("'…' is not a valid package name"). Every file the manager builds
  from a package name (the installed APK, its `.tmp`, the APK on trust and on uninstall) comes from
  `ExtensionPaths.packageFile`, which also resolves the path (symbolic links included) and refuses one that is not
  directly in its folder. An install from a store refuses an invalid index package name before downloading. Tests:
  `ExtensionPackageTest` (`../`, absolute, Windows separators and drive letters, `\\server\share`, empty segments,
  non-ASCII, NUL, spaces), `ExtensionPathsTest` (the same names, a symbolic link pointing outside),
  `DesktopExtensionManagerTest` (an APK with package `../../evil` is not installed and leaves the file two levels up
  alone; `uninstall` of `../../evil`, an absolute path and `..\..\evil` deletes nothing). Unfixed: the manifest name
  was `Valid` and `uninstall("../../evil")` deleted `<dataDir>/evil.apk`.
- **Finding 2, converter (`f535381`).** The receiver of an inlined constructor call now carries whether it is the
  constructor's own `this`; only that one's `super(…)` call is left alone, so a `new C` built inside `C`'s
  constructor is re-targeted like any other. Converted-jar cache version 3 (jars converted before are converted
  again). Test: `DexPipelineTest` (`Node extends Base`, whose constructor stores a `new Node` built by `Base.<init>`,
  dexed with dx): unfixed, `Node` failed with `VerifyError: Bad type on operand stack`; fixed, it verifies, and
  `new Node(5)` has a `Node` child.
- **Finding 4, image formats (`96f9312`).** The single source of truth is `SkiaPageDecoder.DECODED` in `:app-shared`
  (every type the desktop `ImageUtil` detects except `SkiaPageDecoder.UNSUPPORTED`, which the reader and the cover
  decoder already use to name AVIF / JPEG XL / HEIF failures). `eu.kanade.tachiyomi.AppInfo` is no longer borrowed
  from `master`: the desktop has its own copy in `app-shared/src/desktopMain` (header names `master`'s file and commit
  `d126f27`), whose `getSupportedImageMimeTypes()` returns `image/gif`, `image/jpeg`, `image/png`, `image/webp`.
  `master`'s file is unchanged. Test: `ExtensionHostApiTest` (unfixed: also `image/avif`, `image/heif`, `image/jxl`).

## Compat layer policy (ANSWER 010, design point 1)

- **Registry** (`023f633`): [`desktop/android-compat/COMPAT_REGISTRY.md`](../../desktop/android-compat/COMPAT_REGISTRY.md)
  lists every class of the layer with its kind (*type only*, *no-op*, *real behaviour*), how many Keiyoushi
  extensions reference it (the measurement below) and what it does on desktop. `CompatRegistryTest` reads the
  layer's jar: it fails for a public class missing from the registry, for an entry that is not a class of the layer
  and for any other kind. Activity lifecycle callbacks registered at start-up are *no-op* by design (accepted, kept,
  never invoked: there are no activities on desktop).
- **`UnsupportedOnDesktop`** (`187503f`): `UnsupportedOnDesktop(className, member)` replaces the generic exceptions of
  members that cannot work on desktop and would mislead if they did nothing: `Looper.prepare` / `prepareMainLooper` /
  `loop`, `Toast` and `Preference` with Android resource ids, `UniFile.fromResource`.
- **Unsupported state** (`ca17cea`): when creating an extension's sources throws `UnsupportedOnDesktop`, the
  extension manager keeps the extension installed and lists it in the Extensions tab under "Unsupported on desktop",
  with "needs <class>.<member>" and an uninstall button (strings in `i18n-kmk`, `KMR`). Tests:
  `DesktopExtensionManagerTest`, `ExtensionsUiTest`.
- **Per-extension data folders** (`187503f`): each extension class loader names its folder
  `extensions-data/<package>/`; `Context.dataDir`, `filesDir`, `cacheDir`, `getDir`, `getDatabasePath`,
  `ApplicationInfo.dataDir`, `Environment.getDataDirectory()` and `ContextCompat`'s folders resolve to it when the
  caller is extension code (found on the call stack), and to the app's folders for the app itself. Shared preferences
  stay in the app's store (the source settings screen shares them with the extension). Tests:
  `ExtensionDataFoldersTest`, `DexPipelineTest`.
- **New classes** (`470daf5`): `android.icu.text.Collator` on `java.text.Collator` (*real behaviour*, named in
  ANSWER 010), `android.text.Spannable` (*type only*) and `android.text.SpannableString` (plain text, spans dropped;
  `SpannableStringBuilder` now implements `Spannable` too). Tests: `CollatorTest`, `SpannableTest`. 23 extensions
  reference `SpannableString` and 2 `Collator` somewhere in their code. Checked again on this tree with the four
  affected APKs: `mehgazone` (needed `Spannable`) and `projectsuki` (uses `Collator`) create their sources;
  `aquellosquetraducen` now stops at `android.icu.text.RuleBasedCollator` (below); `tenkaiscan` still needs
  `android.graphics.ColorMatrix`. Sources created: 1,699 of 1,701.
- **Not added, needs the Architect: `android.icu.text.RuleBasedCollator`.** `aquellosquetraducen` builds its sort
  order with `(Collator.getInstance(locale) as RuleBasedCollator)`, `setStrength`, `setNumericCollation(true)` and
  `freeze()`; `projectsuki` references it too, in code its sources do not reach. It would be a *real behaviour*
  subclass of `Collator` returned by `getInstance`, about 60 lines: numeric collation (digit runs compared as numbers)
  does not exist in `java.text.Collator` and has to be written on top of it. 1 extension blocked, 2 referencing.

## Catalogue measurement (ANSWER 010, design point 2)

Every extension of Keiyoushi's store index (`index.pb` at commit `37749703df` of the `repo` branch, 28 November 2026),
checked one APK at a time with the link check (`:desktopApp:extensionLinkCheck --all-checks`, BUILDING.md "Extension
link check") on the desktop app's class path, before `Collator` and `Spannable` were added (since then 1,699 create
their sources, see above). The index has 1,701 APKs.

What the numbers mean:

- **Converted**: dex2jar turned the APK into a jar with every method translated.
- **Every reference resolves**: every class, method and field any class of the extension names (method bodies
  included) exists on the desktop host. No extension passes: every one references `android.os.LocaleList` from an
  R8-renamed class that creating the sources never reaches, and most reference Keiyoushi's deep-link `UrlActivity`
  (`Intent`, `ActivityNotFoundException`, `Activity.getIntent`, `startActivity`), which never runs on desktop.
- **Every class verifies**: every class loads, links and passes the JVM verifier. 115,011 classes verify, none is
  rejected; 2,270 classes in 1,271 extensions cannot link because of a missing class, and in 1,013 of those
  extensions only `UrlActivity` fails. The others: Android widgets and WebView code (login and Cloudflare helpers),
  `android.graphics` (image descrambling) and text layout.
- **Sources created**: the sources are instantiated as the extension manager does; a failure there means the
  extension cannot be used at all. A missing reference elsewhere fails only when that code runs (for example an
  interceptor that redraws a page with `android.graphics` fails when the page loads).
- **… sources not created**: of the extensions referencing the class, how many could not create their sources (for
  whatever reason); the "Sources not created" table names the class that stopped each one.

| | Extensions |
|---|---:|
| Checked | 1701 |
| Converted (dex2jar) | 1701 |
| … with every method translated | 1701 |
| Every reference resolves (link check, method bodies included) | 0 |
| Every class verifies | 430 |
| Sources created | 1698 of 1701 |
| Sources not created: unsupported on desktop | 0 |
| Reference a missing `android.*` class anywhere | 1701 |
| Reference a missing member of an existing `android.*` class | 1239 |

### Missing `android.*` classes (103)

| Reference | Extensions | … sources not created | Which (when 5 or fewer) |
|---|---:|---:|---|
| `android.os.LocaleList` | 1701 | 3 |  |
| `android.content.Intent` | 1205 | 1 |  |
| `android.content.ActivityNotFoundException` | 1204 | 1 |  |
| `android.graphics.Bitmap` | 270 | 2 |  |
| `android.graphics.Bitmap$CompressFormat` | 194 | 2 |  |
| `android.graphics.Bitmap$Config` | 184 | 2 |  |
| `android.graphics.Canvas` | 184 | 2 |  |
| `android.graphics.Paint` | 182 | 2 |  |
| `android.graphics.BitmapFactory` | 169 | 1 |  |
| `android.graphics.Rect` | 162 | 0 |  |
| `android.widget.Button` | 131 | 0 |  |
| `android.view.ViewGroup` | 96 | 1 |  |
| `android.view.ViewGroup$LayoutParams` | 95 | 0 |  |
| `android.view.ViewParent` | 90 | 1 |  |
| `android.graphics.Point` | 89 | 0 |  |
| `android.view.View$MeasureSpec` | 89 | 0 |  |
| `android.webkit.ConsoleMessage` | 89 | 0 |  |
| `android.webkit.ConsoleMessage$MessageLevel` | 89 | 0 |  |
| `android.webkit.RenderProcessGoneDetail` | 89 | 0 |  |
| `android.webkit.WebChromeClient` | 89 | 0 |  |
| `android.webkit.WebResourceError` | 89 | 0 |  |
| `android.webkit.WebResourceRequest` | 89 | 0 |  |
| `android.webkit.WebResourceResponse` | 89 | 0 |  |
| `android.webkit.WebSettings` | 89 | 0 |  |
| `android.webkit.WebView` | 89 | 0 |  |
| `android.webkit.WebViewClient` | 89 | 0 |  |
| `android.app.Dialog` | 39 | 0 |  |
| `android.content.DialogInterface` | 36 | 0 |  |
| `android.content.DialogInterface$OnDismissListener` | 36 | 0 |  |
| `android.graphics.Typeface` | 31 | 1 |  |
| `android.text.Layout` | 31 | 1 |  |
| `android.text.Layout$Alignment` | 31 | 1 |  |
| `android.text.StaticLayout` | 31 | 1 |  |
| `android.text.TextPaint` | 31 | 1 |  |
| `android.content.DialogInterface$OnCancelListener` | 30 | 0 |  |
| `android.graphics.drawable.ColorDrawable` | 30 | 0 |  |
| `android.view.Window` | 30 | 0 |  |
| `android.view.WindowManager$LayoutParams` | 30 | 0 |  |
| `android.content.res.Configuration` | 26 | 0 |  |
| `android.text.SpannableString` | 23 | 1 |  |
| `android.text.style.MetricAffectingSpan` | 23 | 1 |  |
| `android.widget.FrameLayout` | 12 | 0 |  |
| `android.widget.FrameLayout$LayoutParams` | 12 | 0 |  |
| `android.graphics.Paint$Style` | 10 | 0 |  |
| `android.graphics.BitmapFactory$Options` | 8 | 0 |  |
| `android.graphics.Matrix` | 7 | 1 |  |
| `android.graphics.Paint$Join` | 7 | 0 |  |
| `android.app.AlertDialog` | 6 | 0 |  |
| `android.app.AlertDialog$Builder` | 6 | 0 |  |
| `android.app.Presentation` | 6 | 0 |  |
| `android.content.DialogInterface$OnClickListener` | 6 | 0 |  |
| `android.graphics.ColorFilter` | 6 | 1 |  |
| `android.graphics.pdf.PdfRenderer` | 6 | 0 |  |
| `android.graphics.pdf.PdfRenderer$Page` | 6 | 0 |  |
| `android.hardware.display.DisplayManager` | 6 | 0 |  |
| `android.hardware.display.VirtualDisplay` | 6 | 0 |  |
| `android.media.Image` | 6 | 0 |  |
| `android.media.ImageReader` | 6 | 0 |  |
| `android.media.ImageReader$OnImageAvailableListener` | 6 | 0 |  |
| `android.os.HandlerThread` | 6 | 0 |  |
| `android.os.ParcelFileDescriptor` | 6 | 0 |  |
| `android.view.Display` | 6 | 0 |  |
| `android.view.Surface` | 6 | 0 |  |
| `android.view.ViewGroup$OnHierarchyChangeListener` | 6 | 0 |  |
| `android.graphics.Paint$Cap` | 5 | 0 | vi.medamtruyen-v1.6.15, vi.panomic-v1.6.12, vi.soaicacomic-v1.6.11, vi.teamlanhlung-v1.6.45, vi.tusachxinhxinh-v1.6.8 |
| `android.graphics.Path` | 5 | 0 | vi.medamtruyen-v1.6.15, vi.panomic-v1.6.12, vi.soaicacomic-v1.6.11, vi.teamlanhlung-v1.6.45, vi.tusachxinhxinh-v1.6.8 |
| `android.text.StaticLayout$Builder` | 5 | 0 | all.dragonballmultiverse-v1.6.1, all.manhuarm-v1.6.4, en.mangamtl-v1.6.3, en.thefarside-v1.6.1, en.webtoonlib-v1.6.2 |
| `android.text.method.PasswordTransformationMethod` | 5 | 0 | vi.medamtruyen-v1.6.15, vi.panomic-v1.6.12, vi.soaicacomic-v1.6.11, vi.teamlanhlung-v1.6.45, vi.tusachxinhxinh-v1.6.8 |
| `android.text.method.TransformationMethod` | 5 | 0 | vi.medamtruyen-v1.6.15, vi.panomic-v1.6.12, vi.soaicacomic-v1.6.11, vi.teamlanhlung-v1.6.45, vi.tusachxinhxinh-v1.6.8 |
| `android.view.MotionEvent` | 5 | 0 | en.comix-v1.6.65, en.theblank-v1.6.6, es.catharsisworld-v1.6.11, fr.epsilonscan-v1.6.6, fr.softepsilonscan-v1.6.6 |
| `android.view.MotionEvent$PointerCoords` | 5 | 0 | en.comix-v1.6.65, en.theblank-v1.6.6, es.catharsisworld-v1.6.11, fr.epsilonscan-v1.6.6, fr.softepsilonscan-v1.6.6 |
| `android.view.MotionEvent$PointerProperties` | 5 | 0 | en.comix-v1.6.65, en.theblank-v1.6.6, es.catharsisworld-v1.6.11, fr.epsilonscan-v1.6.6, fr.softepsilonscan-v1.6.6 |
| `android.view.View$OnClickListener` | 5 | 0 | vi.medamtruyen-v1.6.15, vi.panomic-v1.6.12, vi.soaicacomic-v1.6.11, vi.teamlanhlung-v1.6.45, vi.tusachxinhxinh-v1.6.8 |
| `android.view.ViewGroup$MarginLayoutParams` | 5 | 0 | vi.medamtruyen-v1.6.15, vi.panomic-v1.6.12, vi.soaicacomic-v1.6.11, vi.teamlanhlung-v1.6.45, vi.tusachxinhxinh-v1.6.8 |
| `android.webkit.WebView$VisualStateCallback` | 5 | 0 | en.comix-v1.6.65, en.theblank-v1.6.6, es.catharsisworld-v1.6.11, fr.epsilonscan-v1.6.6, fr.softepsilonscan-v1.6.6 |
| `android.widget.ImageView` | 5 | 0 | vi.medamtruyen-v1.6.15, vi.panomic-v1.6.12, vi.soaicacomic-v1.6.11, vi.teamlanhlung-v1.6.45, vi.tusachxinhxinh-v1.6.8 |
| `android.graphics.RectF` | 4 | 0 | all.lunaranime-v1.6.14, all.rawkumame-v1.6.4, en.alphamanga-v1.6.3, ja.alphapolis-v1.6.4 |
| `dalvik.system.DexClassLoader` | 4 | 0 | ar.procomic-v1.6.11, en.templescan-v1.6.70, es.catharsisworld-v1.6.11, es.templescanesp-v1.6.80 |
| `android.graphics.Color` | 3 | 0 | all.rawkumame-v1.6.4, ar.mangatek-v1.6.14, en.webtoonlib-v1.6.2 |
| `android.graphics.Paint$FontMetrics` | 3 | 0 | all.manhuarm-v1.6.4, ar.mangatek-v1.6.14, en.mangamtl-v1.6.3 |
| `android.media.Image$Plane` | 3 | 0 | en.theblank-v1.6.6, fr.epsilonscan-v1.6.6, fr.softepsilonscan-v1.6.6 |
| `android.content.ComponentName` | 2 | 0 | fr.japscan-v1.6.3, fr.scanmanga-v1.6.4 |
| `android.graphics.Paint$Align` | 2 | 0 | ar.mangatek-v1.6.14, tr.mangatr-v1.6.28 |
| `android.icu.text.Collator` | 2 | 1 | all.projectsuki-v1.6.2, es.aquellosquetraducen-v1.6.0 |
| `android.icu.text.RuleBasedCollator` | 2 | 1 | all.projectsuki-v1.6.2, es.aquellosquetraducen-v1.6.0 |
| `android.app.ActivityManager` | 1 | 0 | ar.mangatek-v1.6.14 |
| `android.app.ActivityManager$MemoryInfo` | 1 | 0 | ar.mangatek-v1.6.14 |
| `android.content.res.TypedArray` | 1 | 0 | all.networkshare-v1.6.0 |
| `android.graphics.ColorMatrix` | 1 | 1 | es.tenkaiscan-v1.6.1 |
| `android.graphics.ColorMatrixColorFilter` | 1 | 1 | es.tenkaiscan-v1.6.1 |
| `android.icu.text.BreakIterator` | 1 | 0 | all.projectsuki-v1.6.2 |
| `android.icu.text.Normalizer2` | 1 | 0 | all.projectsuki-v1.6.2 |
| `android.icu.text.SearchIterator` | 1 | 0 | all.projectsuki-v1.6.2 |
| `android.icu.text.StringSearch` | 1 | 0 | all.projectsuki-v1.6.2 |
| `android.media.MediaDrm` | 1 | 0 | all.networkshare-v1.6.0 |
| `android.media.MediaMetadataRetriever` | 1 | 0 | all.networkshare-v1.6.0 |
| `android.preference.PreferenceManager` | 1 | 0 | all.mangadex-v1.6.5 |
| `android.text.Spannable` | 1 | 1 | en.mehgazone-v1.6.1 |
| `android.text.method.LinkMovementMethod` | 1 | 1 | en.mehgazone-v1.6.1 |
| `android.text.method.MovementMethod` | 1 | 1 | en.mehgazone-v1.6.1 |
| `android.text.util.Linkify` | 1 | 1 | en.mehgazone-v1.6.1 |
| `android.util.Patterns` | 1 | 0 | zh.hanabimanga-v1.6.7 |
| `android.webkit.URLUtil` | 1 | 0 | all.myreadingmanga-v1.6.1 |

### Missing members of existing `android.*` classes (36)

| Reference | Extensions | … sources not created | Which (when 5 or fewer) |
|---|---:|---:|---|
| `android.content.ContextWrapper.startActivity(Landroid.content.Intent;)V` | 1205 | 1 |  |
| `android.app.Activity.getIntent()Landroid.content.Intent;` | 1204 | 1 |  |
| `android.view.View.findViewById(I)Landroid.view.View;` | 129 | 0 |  |
| `android.view.View.getRootView()Landroid.view.View;` | 129 | 0 |  |
| `android.view.View.post(Ljava.lang.Runnable;)Z` | 125 | 0 |  |
| `android.widget.TextView.setHorizontallyScrolling(Z)V` | 125 | 0 |  |
| `android.view.View.getParent()Landroid.view.ViewParent;` | 90 | 1 |  |
| `android.view.View.layout(IIII)V` | 89 | 0 |  |
| `android.view.View.measure(II)V` | 89 | 0 |  |
| `android.view.View.getHeight()I` | 30 | 0 |  |
| `android.view.View.getWidth()I` | 30 | 0 |  |
| `android.view.View.setImportantForAccessibility(I)V` | 30 | 0 |  |
| `android.view.View.setTranslationX(F)V` | 30 | 0 |  |
| `android.view.View.setTranslationY(F)V` | 30 | 0 |  |
| `android.content.res.Resources.getConfiguration()Landroid.content.res.Configuration;` | 26 | 0 |  |
| `android.app.Activity.runOnUiThread(Ljava.lang.Runnable;)V` | 6 | 0 |  |
| `android.view.View.getResources()Landroid.content.res.Resources;` | 6 | 0 |  |
| `android.view.View.setPadding(IIII)V` | 6 | 0 |  |
| `android.graphics.drawable.Drawable.getBounds()Landroid.graphics.Rect;` | 5 | 0 | vi.medamtruyen-v1.6.15, vi.panomic-v1.6.12, vi.soaicacomic-v1.6.11, vi.teamlanhlung-v1.6.45, vi.tusachxinhxinh-v1.6.8 |
| `android.graphics.drawable.Drawable.invalidateSelf()V` | 5 | 0 | vi.medamtruyen-v1.6.15, vi.panomic-v1.6.12, vi.soaicacomic-v1.6.11, vi.teamlanhlung-v1.6.45, vi.tusachxinhxinh-v1.6.8 |
| `android.view.View.dispatchTouchEvent(Landroid.view.MotionEvent;)Z` | 5 | 0 | en.comix-v1.6.65, en.theblank-v1.6.6, es.catharsisworld-v1.6.11, fr.epsilonscan-v1.6.6, fr.softepsilonscan-v1.6.6 |
| `android.view.View.getPaddingBottom()I` | 5 | 0 | vi.medamtruyen-v1.6.15, vi.panomic-v1.6.12, vi.soaicacomic-v1.6.11, vi.teamlanhlung-v1.6.45, vi.tusachxinhxinh-v1.6.8 |
| `android.view.View.getPaddingStart()I` | 5 | 0 | vi.medamtruyen-v1.6.15, vi.panomic-v1.6.12, vi.soaicacomic-v1.6.11, vi.teamlanhlung-v1.6.45, vi.tusachxinhxinh-v1.6.8 |
| `android.view.View.getPaddingTop()I` | 5 | 0 | vi.medamtruyen-v1.6.15, vi.panomic-v1.6.12, vi.soaicacomic-v1.6.11, vi.teamlanhlung-v1.6.45, vi.tusachxinhxinh-v1.6.8 |
| `android.view.View.setOnClickListener(Landroid.view.View$OnClickListener;)V` | 5 | 0 | vi.medamtruyen-v1.6.15, vi.panomic-v1.6.12, vi.soaicacomic-v1.6.11, vi.teamlanhlung-v1.6.45, vi.tusachxinhxinh-v1.6.8 |
| `android.view.View.setPaddingRelative(IIII)V` | 5 | 0 | vi.medamtruyen-v1.6.15, vi.panomic-v1.6.12, vi.soaicacomic-v1.6.11, vi.teamlanhlung-v1.6.45, vi.tusachxinhxinh-v1.6.8 |
| `android.widget.TextView.getCurrentTextColor()I` | 5 | 0 | vi.medamtruyen-v1.6.15, vi.panomic-v1.6.12, vi.soaicacomic-v1.6.11, vi.teamlanhlung-v1.6.45, vi.tusachxinhxinh-v1.6.8 |
| `android.widget.TextView.getSelectionEnd()I` | 5 | 0 | vi.medamtruyen-v1.6.15, vi.panomic-v1.6.12, vi.soaicacomic-v1.6.11, vi.teamlanhlung-v1.6.45, vi.tusachxinhxinh-v1.6.8 |
| `android.widget.TextView.getSelectionStart()I` | 5 | 0 | vi.medamtruyen-v1.6.15, vi.panomic-v1.6.12, vi.soaicacomic-v1.6.11, vi.teamlanhlung-v1.6.45, vi.tusachxinhxinh-v1.6.8 |
| `android.widget.TextView.setTransformationMethod(Landroid.text.method.TransformationMethod;)V` | 5 | 0 | vi.medamtruyen-v1.6.15, vi.panomic-v1.6.12, vi.soaicacomic-v1.6.11, vi.teamlanhlung-v1.6.45, vi.tusachxinhxinh-v1.6.8 |
| `android.content.ContextWrapper.getCodeCacheDir()Ljava.io.File;` | 4 | 0 | ar.procomic-v1.6.11, en.templescan-v1.6.70, es.catharsisworld-v1.6.11, es.templescanesp-v1.6.80 |
| `android.view.View.setHorizontalScrollBarEnabled(Z)V` | 4 | 0 | en.theblank-v1.6.6, es.catharsisworld-v1.6.11, fr.epsilonscan-v1.6.6, fr.softepsilonscan-v1.6.6 |
| `android.view.View.setVerticalScrollBarEnabled(Z)V` | 4 | 0 | en.theblank-v1.6.6, es.catharsisworld-v1.6.11, fr.epsilonscan-v1.6.6, fr.softepsilonscan-v1.6.6 |
| `android.view.View.invalidate()V` | 3 | 0 | en.theblank-v1.6.6, fr.epsilonscan-v1.6.6, fr.softepsilonscan-v1.6.6 |
| `android.app.Application.getSystemService(Ljava.lang.Class;)Ljava.lang.Object;` | 1 | 0 | ar.mangatek-v1.6.14 |
| `android.widget.TextView.setMovementMethod(Landroid.text.method.MovementMethod;)V` | 1 | 1 | en.mehgazone-v1.6.1 |

### Other missing classes (14)

| Reference | Extensions | … sources not created | Which (when 5 or fewer) |
|---|---:|---:|---|
| `ca.mpreg.imagedecoder.ImageDecoder` | 6 | 0 |  |
| `ca.mpreg.imagedecoder.ImageDecoder$Companion` | 6 | 0 |  |
| `ca.mpreg.imagedecoder.ImageDecoder$Frame` | 6 | 0 |  |
| `tachiyomi.decoder.ImageDecoder` | 6 | 0 |  |
| `tachiyomi.decoder.ImageDecoder$Companion` | 6 | 0 |  |
| `javax.el.BeanELResolver` | 1 | 0 | all.networkshare-v1.6.0 |
| `javax.el.ELContext` | 1 | 0 | all.networkshare-v1.6.0 |
| `javax.el.ExpressionFactory` | 1 | 0 | all.networkshare-v1.6.0 |
| `javax.el.FunctionMapper` | 1 | 0 | all.networkshare-v1.6.0 |
| `javax.el.ValueExpression` | 1 | 0 | all.networkshare-v1.6.0 |
| `javax.el.VariableMapper` | 1 | 0 | all.networkshare-v1.6.0 |
| `me.zhanghai.android.libarchive.Archive` | 1 | 0 | all.jellyfin-v1.6.0 |
| `me.zhanghai.android.libarchive.ArchiveEntry` | 1 | 0 | all.jellyfin-v1.6.0 |
| `org.json.JSONObject` | 1 | 0 | en.comix-v1.6.65 |

### Sources not created (3)

| Extension | Result |
|---|---|
| en.mehgazone-v1.6.1 | failed: java.lang.ClassNotFoundException: android.text.Spannable |
| es.aquellosquetraducen-v1.6.0 | failed: java.lang.ClassNotFoundException: android.icu.text.Collator |
| es.tenkaiscan-v1.6.1 | failed: java.lang.ClassNotFoundException: android.graphics.ColorMatrix |

### Compat classes used

| Class | Extensions |
|---|---:|
| `android.net.Uri` | 1701 |
| `android.content.ContextWrapper` | 1449 |
| `android.util.Log` | 1321 |
| `android.app.Activity` | 1205 |
| `android.os.Bundle` | 1205 |
| `android.app.Application` | 1129 |
| `android.content.Context` | 572 |
| `android.content.SharedPreferences` | 538 |
| `androidx.preference.PreferenceScreen` | 532 |
| `androidx.preference.Preference` | 530 |
| `android.util.Base64` | 525 |
| `android.os.SystemClock` | 511 |
| `androidx.preference.SwitchPreferenceCompat` | 314 |
| `android.view.View` | 206 |
| `androidx.preference.EditTextPreference` | 191 |
| `android.widget.EditText` | 157 |
| `android.widget.TextView` | 157 |
| `android.text.Editable` | 131 |
| `android.text.TextWatcher` | 131 |
| `androidx.preference.ListPreference` | 109 |
| `android.os.Build` | 106 |
| `android.content.res.Resources` | 95 |
| `android.util.DisplayMetrics` | 95 |
| `android.os.Handler` | 92 |
| `android.os.Looper` | 92 |
| `android.webkit.CookieManager` | 77 |
| `android.webkit.ValueCallback` | 49 |
| `android.graphics.drawable.Drawable` | 35 |
| `android.widget.Toast` | 34 |
| `androidx.preference.CheckBoxPreference` | 24 |
| `androidx.preference.MultiSelectListPreference` | 24 |
| `android.text.Html` | 23 |
| `android.text.Spanned` | 23 |
| `android.util.LruCache` | 19 |
| `android.content.pm.ApplicationInfo` | 1 |

### `android.graphics` (for the Architect; not started)

275 extensions (16 %) reference a missing `android.graphics` class; 1 of them cannot create its sources
(`tenkaiscan`, `ColorMatrix` and `ColorMatrixColorFilter`). The rest create their sources and fail only when the code
runs, mostly page interceptors that unscramble images: `Bitmap` 270, `Bitmap.CompressFormat` 194, `Bitmap.Config` 184,
`Canvas` 184, `Paint` 182, `BitmapFactory` 169, `Rect` 162; then `Point` 89, `Typeface` 31, `ColorDrawable` 30 and 15
classes used by 10 extensions or fewer (25 classes in all, plus `Drawable.getBounds` / `invalidateSelf` in 5).
Covering the first seven means decoding, cropping, drawing and encoding bitmaps on desktop (Skia, which the reader
already uses), a *real behaviour* group of about seven classes; `Typeface` and text drawing (`StaticLayout`,
`TextPaint`, 31 extensions) would be a second step.

### Other observations

- The tool's `--index` reads the APK URLs from the protobuf fields. The text search it used before split
  `tachiyomi-id.apkomik-v1.6.2.apk` at `.apk` (two bogus inputs, one missed extension); fixed in `84245f7`.
- The scheduled breadth job's `grep` in `build_desktop.yml` finds 1,686 of the 1,701 APKs: its pattern
  (`tachiyomi-[a-z0-9_.]+`) misses names with capitals (`all.vinnieVeritas`, `id.Luvyaa`) and 13 more. Running the
  tool with `--index` instead gives the full list (Test and release engineer's file).
