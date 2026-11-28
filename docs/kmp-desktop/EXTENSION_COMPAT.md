Status: Extension runtime engineer — done: path traversal, converter, image formats (REVIEW_M3 findings 3, 2, 4) — left: compat layer policy (ANSWER 010 design point 1), catalogue measurement (design point 2)

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

Not started.

## Catalogue measurement (ANSWER 010, design point 2)

Not started.
