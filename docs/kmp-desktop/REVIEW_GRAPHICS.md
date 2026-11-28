# Review of the compat layer's `android.graphics`, the class-loader close and `RuleBasedCollator` (ANSWER 016)

Status: Reviewer — done: `103b454499`, `22ad992aad`, `f90dc70377`, `ae71d1ded1`, `9b5c9d811c` on `multiplatform`
(tip `48055a2d71`) reviewed against ANSWER 016 and 017 and Forge's eight focus points — left: re-check the fixes when
the owners report; the items under "Not checked"

**Verdict: land after fixes.** The graphics group itself is sound: pixels are stored and read back the way Android's
Skia does it, the sampling, rectangle edges, locking and recycling behave like Android (checked by reading and with
Skiko probes), the 101 tests pass and the loopback check really runs Shonen Jump+'s interceptor, green in CI on all
four systems. One High finding
blocks closing ANSWER 017's Windows item: an update or uninstall closes the old version's class loader while a
download or page fetch may still be running on it, and the test asserts exactly that. Two Mediums follow: every drawn
or compressed bitmap keeps a second full-size copy of its pixels in native memory, and the honest "unsupported" page
state ANSWER 017 asks for is not there yet. Nothing needs reverting.

## Summary

| Area | Owner | High | Medium | Low |
|------|-------|------|--------|-----|
| 1. Pixel fidelity (`Bitmap`, `BitmapFactory`, `Canvas`, `Paint`, `Rect`, `ColorMatrix`) | Extension runtime engineer | 0 | 0 | 1 |
| 2. Native memory | Extension runtime engineer | 0 | 1 | 0 |
| 3. Threads and locks | Extension runtime engineer | 0 | 0 | 0 |
| 4. Unsupported members and the registry | Extension runtime engineer | 0 | 1 | 0 |
| 5. Loopback check through Shonen Jump+ | Extension runtime engineer | 0 | 0 | 1 |
| 6. Closing extension class loaders | Extension runtime engineer | 1 | 0 | 1 |
| 7. `RuleBasedCollator` | Extension runtime engineer | 0 | 0 | 1 |
| 8. Markers, strings, files `master` owns, docs | Extension runtime engineer | 0 | 0 | 1 |
| Tests | Extension runtime engineer | 0 | 0 | 1 |

High:
- 6.1: `ae71d1ded1` closes the old `URLClassLoader` as soon as the update or uninstall publishes, so a running
  download or page fetch of that version gets `NoClassDefFoundError` for any class it had not loaded yet; ANSWER 017
  asks for the opposite, and `DesktopExtensionManagerTest` line 212 pins the closing.

Medium:
- 2.1: `Pixels.snapshot()` copies the whole pixel buffer (the Skia bitmap is never immutable) and keeps the copy until
  the bitmap changes or is recycled: twice the native memory for every drawn or compressed bitmap, uncounted.
- 4.1: ANSWER 017's honest failure for pages that need text drawing (or `RectF` / `Matrix` / `Path`) is not done: they
  now fail at page time with `NoClassDefFoundError` / `NoSuchMethodError` or a bare `UnsupportedOnDesktop`.

Code review, high effort, 2026-11-28. Reviewer role; the owning role fixes. Reviewed read-only on the refs; file and
line numbers are at `48055a2d71`. Evidence beyond reading: `:desktop:android-compat:test` (101 tests, 0 failures,
through `.git/forge/gradlew-locked reviewer`); two Skiko 0.150.2 probes (plain Java against the jar the layer uses,
under `/tmp`, nothing committed) for EXIF, truncated input, a source rectangle outside the image, drawing a bitmap
onto itself, and whether Skiko's images share or copy pixels; the engineer's catalogue scan
(`.git/forge/scratch/gfx/refs.json`, every `android.graphics` member each of the 1,701 extensions references) re-tallied;
`aquellosquetraducen` and `webtoonlib` disassembled with `dexdump` for their collator and `RGBA_F16` use.

**The working tree has uncommitted work in progress**, not reviewed here: `ExtensionClassLoader.kt` reads the jar into
memory and is no longer a `URLClassLoader` (6.1, the resource part of 6.2), `Main.kt` / `DesktopInjekt.kt` change exit
handling (6.2), and a new `UnsupportedPage.kt` with a reader test and an `i18n-kmk` string (4.1). Review it when it is
committed.

**ANSWER 016 and 017, point by point:**
- Scope is what the catalogue calls: met. Re-tallied from the scan, every member the catalogue references on the seven
  classes exists, except those that need a class outside the group (`drawBitmap(Bitmap, Rect, RectF, Paint)` 3,
  `drawBitmap(Bitmap, Matrix, Paint)` 1, `drawPath` 5, `drawRoundRect` 1, `Paint.setTypeface` 31,
  `getFontMetrics` 3). Not recorded in the repository (8.1).
- Pixel fidelity tested against known answers: met (decode of JPEG, PNG, WebP, the first GIF frame; unpremultiplied
  `getPixel(s)` / `setPixels`; `drawBitmap` with rectangles, scaling and a null paint; `createBitmap` of a rectangle,
  the matrix form is not referenced; `compress` with stated tolerances; four schemes end to end, bit for bit).
- Memory and threads: met for correctness (3 below); the memory cost is 2.1.
- Registry: met; `CompatRegistryTest` enumerates every public class of the layer, so the new ones cannot be missing.
- Done means seen: the CI part is met (`f90dc70377`): run `46139150350` at `48055a2d71` passed "Self-test with real
  extensions" on Linux, Windows, macOS arm64 and macOS Intel, each reporting "GigaViewer page unscrambled: 0.49
  levels from the page on average (scrambled: 52.8)". The on-device pass is still open, as ANSWER 017 says.
- `ColorMatrix` rides along: met. `RuleBasedCollator` in proportion with numeric tests: met (7.1 is a corner).
- ANSWER 017, text drawing failing honestly: open (4.1). Windows jar lock: update, uninstall and a failed load close
  and delete, with tests; app exit does not (6.2); a loader in use is pulled out from under its download (6.1).

## 1. Pixel fidelity (Extension runtime engineer)

Checked and right, so no finding:
- **Premultiplied storage, unpremultiplied access.** N32 premultiplied Skia bitmaps (`NativeBitmaps.allocate`, line
  28); `readArgb` / `writeArgb` (lines 58–81) convert through Skia to and from `BGRA_8888` unpremultiplied, the same
  `readPixels` / `writePixels` conversion Android's `Bitmap_getPixels` / `setPixels` use, so rounding matches. Alpha 0
  reads back as `0`. `copyPixelsFromBuffer` takes Android's memory layout (RGBA bytes, premultiplied; `IntBuffer` in
  native order) and advances the buffer like Android; no mutability check, as on Android.
- **No silent colour conversion.** Every `ImageInfo` has no colour space, so decode, draw, read and encode never
  convert. Android tags the same bitmaps sRGB, so for untagged and sRGB images the values agree (see "Not checked" for
  images with another embedded profile).
- **EXIF orientation not applied, like `BitmapFactory`.** Probe: a JPEG with orientation 6 decodes through `Codec` at
  its stored size (40 × 20, origin `RIGHT_TOP` reported but not applied) with its stored pixel layout.
- **GIF and animated WebP:** frame 0 (`BitmapFactory.kt` line 51), tested exact for GIF.
- **Config:** only `ARGB_8888`; every other config, in `createBitmap`, `copy` and `inPreferredConfig`, throws
  `UnsupportedOnDesktop` naming it. The six extensions that reference `RGBA_F16` (Keiyoushi's image-decoder helper:
  `webtoonlib`, `toonlook`, `galleryepic`, …) only pick it for HDR frames of a JNI decoder that cannot load on desktop.
- **Sampling.** Nearest without a paint, linear with a filtering paint (a new `Paint` has `FILTER_BITMAP_FLAG`, as on
  Android 9 and later), `drawImageRect` with the fast constraint as Android's `SkiaCanvas` (`Canvas.kt` lines 100,
  107). Same-size copies are exact either way (tested with `null`, `Paint()` and an anti-aliasing paint).
- **Integer rectangle edges.** `Rect` is converted to float edges exactly; right and bottom exclusive. Probe: a source
  rectangle reaching past the image is clipped and the destination shrunk in proportion, which is Skia's raster
  behaviour on Android too.
- **The same bitmap as source and destination.** The source's snapshot is a copy (see 2.1), so an overlapping
  self-draw reads the old pixels. Probe: shifting an 8-pixel row by 2 onto itself gives `0 30 0 30 60 90 120 150`.
- **`compress`.** JPEG drops alpha by encoding the premultiplied colours (over black), as Android's encoder does; PNG
  ignores the quality; WebP is lossy only, documented (Info below).
- **`ColorMatrixColorFilter`.** Android's 0..255 offsets divided by 255 for Skia's matrix filter, applied to
  unpremultiplied colours like Android; the matrix is copied.
- **`Paint` defaults and Android's validation messages** (`Paint()`, `Paint(int)`, `Paint(Paint)`, `setAlpha` clamp,
  negative stroke widths ignored; `checkPixelsAccess`, negative strides, `createBitmap` of an immutable whole source
  returning the source) match AOSP.

### 1.1 Low: a truncated image decodes to `null`; Android returns the part that decoded

- **File, line:** `desktop/android-compat/src/main/kotlin/android/graphics/BitmapFactory.kt` lines 50–55.
- **Problem:** Skiko's `Codec.readPixels` throws `IllegalArgumentException("Incomplete input: A partial image was
  generated.")` for a cut-off file (probe: a JPEG cut at 60 % and at 95 %); the catch returns `null`. Android's
  `BitmapFactory` treats `kIncompleteInput` as success and returns the partial bitmap. A page served truncated (it
  happens with some hosts' uploads) then shows half a page on Android and fails on desktop, usually as an NPE inside
  the extension's interceptor (GigaViewer, for one, passes `decodeStream(...)` on without a null check).
- **Proposed fix:** when the exception's message starts with `Incomplete input`, keep `skia` and return the bitmap;
  only other failures return `null`. Test with a truncated JPEG: a bitmap of the full size whose top rows match.

## 2. Native memory (Extension runtime engineer)

Checked and right: `recycle()` frees the Skia bitmap, its canvases and its snapshot at once
(`NativeBitmaps.Pixels.run`, lines 115–126, idempotent); the cleaner's action is the `Pixels` object, a nested class
that holds only Skia objects, never the `Bitmap`, so a forgotten bitmap is collected and freed (tested); a recycled
bitmap throws `IllegalStateException` with Android's messages for pixel access, `compress`, `copy` and `Canvas`, and
`drawBitmap` of a recycled source throws `RuntimeException("Canvas: trying to use a recycled bitmap …")` like Android;
the free waits for the bitmap's lock, so a recycle or a cleaner run during a draw cannot free memory in use.

### 2.1 Medium: every drawn or compressed bitmap keeps a full copy of its pixels

- **File, line:** `desktop/android-compat/src/main/kotlin/app/komikku/desktop/compat/NativeBitmaps.kt` lines 89,
  103–111; `Canvas.kt` line 104; `Bitmap.kt` line 131; `BitmapFactory.kt` line 44.
- **Problem:** `snapshot()` makes the image to draw or encode from with `Image.makeFromBitmap(skia)`. The Skia bitmap
  is never marked immutable, so Skia copies the whole buffer (probe: an image made this way does not see a later
  change of the bitmap; one made with `Image.makeFromPixmap(peekPixels())` does). The copy is cached on the `Pixels`
  until the bitmap changes or is freed. So a decoded page that is drawn, and a result that is compressed, each hold
  two buffers: GigaViewer's interceptor, which recycles both bitmaps, still peaks at three page-size buffers where
  Android has two (a 1,000 × 15,000 strip: 180 MB instead of 120 MB), on several OkHttp threads at once. `liveBytes()` counts neither copy. A forgotten bitmap
  keeps both until a GC happens to run: the JVM does not know about this native memory (Android tells its GC through
  `NativeAllocationRegistry`), and 28 of the 195 extensions that create or decode bitmaps never call `recycle()`
  (catalogue scan). Smaller: `BitmapFactory` never closes the `Data` holding a native copy of the encoded bytes.
- **Proposed fix:** draw and encode from `Image.makeFromPixmap(skia.peekPixels())`, which shares the pixels; it is
  valid while the bitmap's lock is held, which every caller already holds. Make it per call and close it after the
  draw or encode (no cache). Keep a copying image only for a bitmap drawn onto itself
  (`bitmap.pixels === target.pixels` in `Canvas.drawImage`), closed after the draw. Close the `Data` in `BitmapFactory`
  (`Data.makeFromBytes(…).use { Codec.makeFromData(it) }`; the codec keeps its own reference). Optionally, when
  `liveBytes()` grows by more than a few hundred MB since the last check, ask for a GC, which is what Android's
  registry does. Extend `BitmapMemoryTest`: `liveBytes()` (counting any copies) returns to its start after a draw, a
  compress and a recycle.

## 3. Threads and locks (Extension runtime engineer)

No finding. Every access to a bitmap's native objects holds that bitmap's `Pixels` lock (`Bitmap.use`, lines
146–149). `drawBitmap` takes the source's and the target's locks in `Pixels.id` order (`NativeBitmaps.locked`, lines
40–44), so two threads drawing A into B and B into A cannot deadlock; the same bitmap as source and target takes one
(reentrant) lock. `Canvas`'s inner `target.change` re-enters a lock already held. The cleaner's free takes only the one
lock, and `Cleaner.register` / `clean` hold their list lock only to add and remove, never while running the action, so
there is no inversion with a thread that allocates a bitmap while holding another's lock. Nothing refers to the AWT
event queue or any UI thread; the concurrency test runs 32 interceptions on 8 threads and checks none ran on the event
queue. A single `Canvas` used from two threads at once is serialised by the target's lock, which is more than Android
promises.

## 4. Unsupported members and the registry (Extension runtime engineer)

Checked and right: `Bitmap.copyPixelsToBuffer`, `setHasAlpha`, `setPremultiplied`, `extractAlpha`,
`createScaledBitmap`, other configs, `Canvas.drawText`, and `Paint`'s text members throw `UnsupportedOnDesktop`
naming the member (`needs` = `android.graphics.Bitmap.createScaledBitmap`, …; tested). `COMPAT_REGISTRY.md` rows 42–52
register every new class with the catalogue count, `ColorFilter` as type only; `CompatRegistryTest` fails for any
public class of the layer without a row.

### 4.1 Medium: pages that need text drawing (or `RectF`, `Matrix`, `Path`) do not fail honestly yet

- **File, line:** `desktop/android-compat/src/main/kotlin/android/graphics/Paint.kt` lines 122–136; the reader's page
  loading on desktop; ANSWER 017 "Text drawing".
- **Problem:** with the group in, the 31 extensions that draw text get further and now fail at page time in three
  ways: `Paint.setTypeface` (31) and `getFontMetrics` (3) do not exist, so `NoSuchMethodError` /
  `NoClassDefFoundError: android/graphics/Typeface`; `setTextSize` (34) and `hasGlyph` (23) throw `UnsupportedOnDesktop`
  from inside an OkHttp interceptor, where nothing turns it into the "needs …" state (that mapping exists only for
  source creation); `lunaranime`, `alphamanga` and one more (`drawBitmap(…, RectF, …)`), `tenkaiscan`
  (`drawBitmap(…, Matrix, …)`) and five Vietnamese sources (`drawPath`) fail the same way. ANSWER 017 asks for the
  reader's "unsupported" state naming what is missing, not a generic load error or an endless retry, with this
  review's fixes.
- **Proposed fix:** where the desktop reader loads a page, catch `UnsupportedOnDesktop` and `LinkageError` whose class
  is under `android.` (walk the causes; OkHttp may wrap them in an `IOException`) and show the unsupported state with
  the member or class, and do not retry it. A test: an interceptor that calls `Paint().setTextSize(1f)` and one that
  throws `NoClassDefFoundError("android/graphics/Typeface")` both give that state.

## 5. Loopback check through Shonen Jump+ (Extension runtime engineer)

Checked and right: the check fetches through the extension's own `HttpSource.client` with GigaViewer's `#scramble`
fragment, so the extension's `ImageInterceptor` (the real one, from the pinned APK) decodes, redraws and re-encodes the
page on the compat layer; the server binds the loopback interface only; if the interceptor did not run, or ran and gave
up, the page comes back scrambled at 52.8 levels and fails (`ScrambledPageCheckTest` "a page left scrambled fails").
The pinned APK's hash is checked before it runs, and the job stays contained (no secret, no checkout).

### 5.1 Low: the tolerance passes a page whose blocks are a few pixels off

- **File, line:** `desktopApp/src/main/kotlin/app/komikku/desktop/selftest/ScrambledPageCheck.kt` lines 59–61, 92–96,
  105–109.
- **Problem:** the page is a smooth gradient (red rises 0.43 levels per pixel, green 0.30, blue 0.18) and the check is
  one mean over the whole page against 2.0. A still-scrambled page cannot pass (52.8), but blocks placed a few pixels
  off can: a 5-pixel shift of the 12 moved blocks adds about 0.7 to the 0.49 the real extension gives, well under
  2.0, and that is a visible seam on a real page. The unit tests catch such errors in the compat layer bit for bit; this
  check is the only one on the extension's own arithmetic run through dex2jar.
- **Proposed fix:** compare per block: the mean of each of the 16 grid blocks and of the margin under, say, 1.5, and
  add a high-contrast feature JPEG keeps, e.g. a 1-pixel dark line on every block's edge, so a shift of one pixel
  changes a block's mean by tens of levels.

## 6. Closing extension class loaders (Extension runtime engineer)

Checked and right: a load that fails after the loader is created closes it (`ExtensionRuntime.load`, `classLoader.close()`
in the catch); `URLClassLoader.close` is idempotent and `closeLoader` catches `IOException`, so a double close is
harmless; `close()` racing a load cannot leak (the load re-checks `closed` after publishing its loader); update and
uninstall delete the old jar after closing, tested (`jars().size shouldBe 1`, `oldJar.exists() shouldBe false`).

### 6.1 High: an update or uninstall pulls the class loader out from under a running download

- **File, line:** `desktopApp/src/main/kotlin/app/komikku/desktop/extensions/DesktopExtensionManager.kt` lines 491–500
  (update) and 541–543 (uninstall); `desktop/extension-runtime/…/loader/ExtensionClassLoader.kt` line 33;
  `desktopApp/src/test/…/extensions/DesktopExtensionManagerTest.kt` line 212.
- **Problem:** the old version's loader is closed right after the new entry is published. A download, a page fetch or
  a library update that already holds the old `Source` keeps running on it, and the first class it has not loaded yet
  (an interceptor first used on page 1, a DTO or its serializer, a Kotlin lambda, an exception class on an error path)
  fails with `NoClassDefFoundError`, an `Error` that `catch (e: Exception)` handlers let through. Resources fail the
  same way. On Android the old version keeps working until it is no longer referenced. ANSWER 017 asks exactly for
  this not to happen ("the old one is closed when its last source is released, or the update waits"), and the test
  asserts the opposite (`oldLoader.getResource(…) shouldBe null`).
- **Proposed fix:** do not close a loader that may be in use; free the jar instead. Either read the jar into memory when
  the loader is created and keep no file open (classes not loaded yet stay loadable; the old version is freed with its
  last use; deletion works on Windows at once), which is what the uncommitted working-tree change appears to do, or
  keep the loader open and delete the jar when a `Cleaner` registered on the loader runs. Change the test to: after the
  update the old jar is gone and a class of the old version not loaded before still loads from the old loader.

### 6.2 Low: app exit does not close the loaders, and `getResource(...).openStream()` still locks the jar

- **File, line:** `ExtensionClassLoader.kt` lines 64–65 and 72–83; `desktopApp/src/main/kotlin/app/komikku/desktop/Main.kt`
  (no `DesktopInjekt.closeOpenFiles()` call); `DesktopModules.kt` line 286.
- **Problem:** ANSWER 017 lists app exit; the commit says it happens "once Main.kt calls it", and Main.kt does not.
  Harmless in itself (the process ending releases the files and the next start prunes), but the item is not done as
  written. More relevant: only `getResourceAsStream` was fixed. An extension that does
  `javaClass.getResource("/assets/…")!!.readText()` or `.openStream()` gets a `jar:` URL whose connection uses the JDK's
  shared jar cache, which `close()` does not touch, so that jar stays open and Windows cannot delete it.
- **Proposed fix:** answered by 6.1's in-memory loader if it serves resources through its own URL handler (no
  `jar:` URLs). Otherwise return URLs from `findResource` with `useCaches = false`. Then either call
  `closeOpenFiles()` on exit or record that exit relies on the process ending and the start-up prune.

## 7. `RuleBasedCollator` (Extension runtime engineer)

Checked and right: `Collator.getInstance` returns a `RuleBasedCollator` like Android (both catalogue users cast);
numeric collation orders digit runs by value with leading zeros ignored, any script's decimal digits, numbers of any
length; `freeze()` returns the collator and makes every setter throw `UnsupportedOperationException("Attempt to modify
frozen object")` like ICU's `Freezable`; `cloneAsThawed()` copies the settings, numeric included; a frozen collator is
safe to share because the JDK's `RuleBasedCollator.compare` is synchronized (tested on 8 threads).
`aquellosquetraducen` (disassembled) uses `Locale.ROOT`, `PRIMARY`, numeric on, then `freeze()`: covered exactly.

### 7.1 Low: with numeric collation, an earlier case or accent difference beats a later letter difference

- **File, line:** `desktop/android-compat/src/main/kotlin/android/icu/text/RuleBasedCollator.kt` lines 29–44, 62.
- **Problem:** the comparison goes run by run, each run at the full strength. ICU compares all primary weights of the
  whole string first, then secondary, then tertiary. At `SECONDARY` or `TERTIARY` (ICU's default) that differs:
  `"a1b"` vs `"A1a"` is greater in ICU (the `b`/`a` primary difference decides) and less here (the `a`/`A` tertiary
  difference of the first run decides; the JDK's whole-string `compare("ab", "Aa")` is `1`, `compare("a", "A")` is
  `-1`). Also, a run of zeros becomes the empty string, so `"0"` against a punctuation or space run orders before it
  where ICU puts digits after spaces. No catalogue user is affected (the one user runs at `PRIMARY`).
- **Proposed fix:** compare in two passes: the run-wise order computed with a `PRIMARY` copy of the delegate first,
  then, only if all runs are equal there, the run-wise order at the real strength. Keep `"0"` as `"0"` for the
  comparison against a non-digit run. A test with `"a1b"` / `"A1a"` at `TERTIARY`.

## 8. Markers, strings, files `master` owns, docs (Extension runtime engineer)

Checked and right: the only file outside desktop-only modules and desktop CI is `gradle/libs.versions.toml`, one
entry (`skiko-awt`) inside the existing KMK block that `master` does not have; every other file is new or desktop-only.
No strings were added (no `i18n*` change). The commits carry the maintainer's author and no trailers.

### 8.1 Low: the compat docs still describe the state before this work

- **File, line:** `docs/kmp-desktop/EXTENSION_COMPAT.md` line 1 (status: "left: `android.graphics` and
  `RuleBasedCollator`"), lines 323–332 ("`android.graphics` (for the Architect; not started)"); `HANDOVER.md` lines
  556–557 ("the 2 left need `ColorMatrix` and `RuleBasedCollator`").
- **Problem:** ANSWER 016 asked for HANDOVER.md and PROGRESS.md to be updated to the new numbers. The member
  measurement that decided the scope (which members, how many extensions each), the decode and encode tolerances and
  the "seen headless, not in the packaged app" state live only in commit messages and the scratch folder.
- **Proposed fix:** a "Graphics group (ANSWER 016)" section in EXTENSION_COMPAT.md with the member table from the scan,
  the tolerances, the memory and thread model and the open on-device check; refresh the status line and HANDOVER.md.

## Tests

### T.1 Low: behaviour verified here by reading or probe has no test

- **Problem:** the review found these right, but nothing pins them: two threads drawing A into B and B into A at once
  (the lock order); a bitmap drawn onto itself with overlapping rectangles; a JPEG with an EXIF orientation (decoded
  at its stored size and layout); a recycle on one thread during a draw on another; a source rectangle reaching past
  the image.
- **Proposed fix:** add them to `CanvasTest`, `BitmapCodecTest` and `BitmapMemoryTest`; the EXIF case needs only an
  APP1 segment spliced into an ImageIO JPEG (the probe did it in 20 lines).

## Info

- WebP is always lossy (Skiko exposes no lossless encoder), so `WEBP_LOSSLESS` and `WEBP` at 100 lose a few levels
  where Android 10+ is exact. If a descrambler that re-encodes to WebP ever shows artefacts, encoding PNG for those two
  requests would be exact (the reader sniffs the format).
- `getPixels` / `setPixels` go through two full-size intermediate arrays (bytes and ints); for a long strip that is
  about 2 × 60 MB of garbage per call. Reading into an `IntBuffer` view of one `ByteArray` would halve it.
- `Canvas.clipRect` returns whether the rectangle is non-empty, Android whether the resulting clip is; one extension
  calls it.
- `libs.skiko.awt` is pinned to the Skiko Compose 1.12.2 brings. The app resolves the higher of the two, so only the
  compat tests would run on an older Skiko after a Compose bump; a build check that they are equal would catch it.

## Not checked

- Images with an embedded colour profile other than sRGB: the desktop keeps the stored values, Android decodes them in
  their own colour space and converts when drawing into an sRGB result. Not measured; it changes colours slightly, not
  geometry.
- The on-device pass (two unscrambling extensions reading a chapter in the packaged app on the Mac): still pending.
- Windows itself: the jar deletion code and its tests were read, not run on Windows here.
- Text drawing, as deferred by ANSWER 017.
