# Mangayomi JavaScript manga sources: usage survey

Static scan for step I0 of [`PLAN.md`](PLAN.md) section 8. It counts what the published JavaScript
manga extensions use from the host described in [`MANGAYOMI_CONTRACT.md`](MANGAYOMI_CONTRACT.md), to
size and order the bridge work (I2). Run on 2026-11-17 against the same commits as the contract:
`kodjodevf/mangayomi-extensions` `6004f1f8d1` (2025-10-01, last commit of its `main`) and
`entityJY/mangayomi-extensions-eJ` `f21a60bf02` (2026-01-08).

## Method

- A Python script (kept outside the repository) reads `index.json` of both repositories, resolves each
  JavaScript manga entry's `sourceCodeUrl` to the file in the entityJY clone (the URLs all point there),
  falling back to the kodjodevf repository's copy for files missing upstream, and scans each file once.
- Per file: comments removed, quoted string contents blanked (template literals kept, since `${…}`
  holds code), then regular expressions for each bridge wrapper, provider member, `type_name` value,
  preference type and language feature. A provider member counts as implemented unless its body only
  throws "… not implemented". Counts are weighted two ways: by **file** and by **index entry** (one
  file can serve many entries).
- Ambiguous hits (local `fetch`/`atob` helpers, `eval` inside strings, page-list return shapes) were
  checked by reading the code. Nothing was executed, and no request was made to any manga website. The
  only network checks were the published index (identical to the repository file) and the two
  `raw.githubusercontent.com` file URLs reported as missing below (both HTTP 404).
- Limits: a static scan cannot see calls built dynamically, and it says nothing about whether the
  websites still work.

## 1. Index counts

### kodjodevf index (the one the README tells users to add)

| | Dart (0) | JavaScript (1) | Total |
|---|---:|---:|---:|
| manga (`itemType` 0) | 249 | **114** | 363 |
| anime / novel | 0 | 0 | 0 |

- The manga index contains only manga. `novel_index.json` has 5 JavaScript novel entries; the anime
  index is no longer offered by this repository (its README lines are commented out).
- **114 JavaScript manga entries come from 18 files.** 86 of them are language variants of two
  files: MangaDex (45 languages) and Comick (41). Mangafire and Webtoons have 7 each; the other 14
  files serve one entry each.
- By language (JS manga entries): `en` 9, `zh` 8, `ar` 4, `fr` 4, `es` 4, `de` 3, `id` 3, `it` 3,
  `ja` 3, `pt-br` 3, `pt` 3, `ru` 3, `th` 3, 35 more languages with 1–2 each (almost all MangaDex and
  Comick variants). Single-site files: 5 Chinese, 5 English, 2 Arabic, 1 Italian, 1 Russian.
- NSFW: 1 of 114 (Mangalib, `ru`); 2 of 363 overall.
- All JS manga entries: `typeSource` `single`, `appMinVerReq` `0.5.0`, `hasCloudflare` false,
  `isFullData` false, `dateFormat` and `additionalParams` empty; `apiUrl` set on 88; `notes` on 1.
- `id` is unique among JS entries; two Dart entries share ids (`EvaScans` tr, `Império Scans` pt-BR).
- **2 entries are dead:** `en/mangapill.js` and `en/readcomiconline.js` are listed with entityJY URLs
  that return 404 (the files were removed from that repository). Copies remain in the kodjodevf
  repository itself; the survey scanned those copies.
- The index lags its files: Mangafire is `0.1.25` in the index and `0.2.20` in the file it points to.

### entityJY index (its own fork's index)

364 entries: 249 Dart + **115 JavaScript manga from 19 files**. Same Dart part and ids; drops the two
dead entries; adds `zh/rumanhua1.js`, `zh/baozimh.js` and `all/batoto.js`. It also publishes an anime
index (61 entries: 39 Dart, 22 JS) and 5 novel entries.

Unless stated otherwise, the tables below use the kodjodevf index (18 files / 114 entries); the
entityJY figures differ only through the files named above.

## 2. Provider members implemented

| Member | Files (of 18) | Entries (of 114) |
|--------|---:|---:|
| `getPopular`, `getLatestUpdates`, `getDetail`, `getPageList` | 18 | 114 |
| `search` | 17 | 113 |
| `getFilterList` | 15 | 67 |
| `getSourcePreferences` | 13 | 69 |
| `getHeaders` | 9 | 93 |
| `supportsLatest` (getter returning a value) | 1 | 1 |
| `getVideoList`, `getHtmlContent`, `cleanHtmlContent` | 0 | 0 |

**Correction (I2 smoke run):** MangaDex's class declares `getFilterList` twice; the first throws "not
implemented", but the later declaration wins in JavaScript and returns 11 filters. So `getFilterList` is
implemented in 16 files / 112 entries, not 15 / 67, and MangaDex needs the filter models too (the coverage
table in section 7 understates the filter step accordingly).

## 3. Bridge and language features used

| Feature | Files (of 18) | Entries (of 114) |
|---------|---:|---:|
| `Client.get` (`new Client()` without options) | 18 | 114 |
| `async`/`await` | 18 | 114 |
| `new Date(…)` / `Date.parse` (mostly to build `dateUpload`) | 13 | 109 |
| `SharedPreferences.get` | 13 | 69 |
| `this.source.baseUrl` | 13 | 65 |
| `Document` + `select`/`selectFirst` + `Element.text` | 12 | 24 |
| `??` | 11 | 107 |
| `JSON.parse` of response bodies | 10 | 100 |
| `Element.attr` | 10 | 22 |
| `Element.getSrc` / `getHref` | 8 | 20 |
| `?.` | 7 | 97 |
| `encodeURIComponent` | 5 | 51 |
| `this.source.lang` | 4 | 100 |
| `this.source.apiUrl` | 4 | 88 |
| `response.statusCode` | 3 | 9 |
| `String.prototype.substring*` helpers | 2 | 2 |
| `Element.children` | 1 | 7 |
| `Element.nextElementSibling` | 1 | 1 |
| `Element.className` | 1 | 1 |
| `cryptoHandler` (AES-CBC) | 1 | 1 |
| entityJY only: `Client.post`, `console.log/error`, `eval` of site script text | 1 each | 1 each |

**Not used by any JavaScript manga source:** XPath (`xpath`, `xpathFirst`), `parseDates`,
`evaluateJavascriptViaWebview`, `unpackJs`/`unpackJsAndCombine`, `deobfuscateJsPassword`,
`encryptAESCryptoJS`/`decryptAESCryptoJS`, `decryptAESGCM`, all video extractors,
`createHlsProxyUrl`, `parseEpub*`, `getString`/`setString`, `Client` options (`reqcopyWith`),
`put`/`patch`/`delete`/`head`, `Element.innerHtml`/`outerHtml`/`hasAttr`/`getImg`/`getDataSrc`,
`previousElementSibling`, `getElementsBy*`/`getElementById`, `Document.body`/`head`, response
`headers`/`request`, `setTimeout`, global `atob`/`btoa`/`fetch`/`URL`/`TextDecoder` (one file defines
a local `fetch` helper, one a local base64 helper), BigInt, typed arrays.

**CSS selectors:** plain selectors, attribute selectors, `+` combinators, `:contains(…)` (6 uses) and
`:has(…)` (1 use). One source uses an invalid selector (`div.#post-body …`), which Mangayomi silently
turns into an empty result; jsoup would throw, so the host must catch selector errors.

**Element text:** two sources read `<script>` content through `Element.text`, and one finds the script
with `script:contains(…)`. Mangayomi's `text` (and its `:contains`) work on DOM `textContent`, which
includes script data; jsoup's `text()` and `:contains` do not, so the host must implement `textContent`
itself and make `:contains` match it (jsoup's `:containsData` covers the script case).

## 4. Filters and preferences used

| Filter `type_name` | Files | Entries | | Preference type | Files | Entries |
|--------------------|---:|---:|---|-----------------|---:|---:|
| `SelectFilter` + `SelectOption` | 15 | 111 | | `editTextPreference` | 8 | 58 |
| `GroupFilter` | 8 | 98 | | `listPreference` | 7 | 57 |
| `CheckBox` | 7 | 97 | | `multiSelectListPreference` | 1 | 45 |
| `TriState` | 4 | 94 | | `switchPreferenceCompat` | 1 | 1 |
| `HeaderFilter` | 2 | 48 | | `checkBoxPreference` | 0 | 0 |
| `SortFilter` + `SortState` | 2 | 46 | | | | |
| `TextFilter` | 1 | 41 | | | | |
| `SeparatorFilter` | 1 | 7 | | | | |

(Counts include filter literals inside `search` code.) Preference keys seen: `domain_url` (5 files,
user-editable base URL), `url`, `overrideBaseUrl1`, `mobile_url`, `custom_user_agent`, `cover_quality`,
`original_languages`, `imghost`, `hd`, `uid`, content-view and title-language choices.

Search reads filters **by position** in 6 files (`filters[3].state`, `filters[0].values[filters[0].state].value`),
by `type` in 4, and one source (Weeb Central) builds `getPopular`/`getLatestUpdates` by changing its own
filter list and calling `search`.

## 5. Results returned

- **Page lists:** 10 files return URL strings; 8 return `{ url }` objects, 4 of them with per-page
  `headers` (Mangafire, Weeb Central, Mangalib, ReadComicOnline).
- **`dateUpload`:** strings built with `new Date(x).valueOf().toString()` or `getTime().toString()`,
  site-specific relative-date code, one `"<seconds>" + "000"`, and `null` in a few branches. None uses
  `parseDates`.
- **Status:** integers 0–3 and 5 for unknown; three sources send 4 for "unreleased / coming soon",
  which Mangayomi displays as "publishing finished".

## 6. Multi-source files

Only language fan-out exists among JS manga files; there are no theme files (the Dart side has them:
`madara` 151 entries, `mangareader` 87, …). A multi-language file declares `langs` (and usually a fixed
`ids` map) in its `mangayomiSources` header; the generator emits one entry per language with the same
`sourceCodeUrl`, and the file reads `this.source.lang` (MangaDex, Comick, Mangafire, Webtoons) to pick
the content language. `apiUrl` is the only other field these files read. `additionalParams`,
`dateFormat` and `dateFormatLocale` are not used by any JS manga file.

## 7. Coverage by bridge feature

Every file needs the core: engine, the prelude (`MProvider`, `Client` wrapper, string helpers,
console), `Client.get`, and the result mapping. On top of it, each file needs some of the groups
below. Cumulative coverage, greedy order (each step adds the group that unlocks the most entries):

| Host implements | Files (of 18) | Entries (of 114) |
|-----------------|---:|---:|
| core only | 0 | 0 |
| + filter models round-trip | 2 | 42 (37%) |
| + `SharedPreferences.get` and preference models | 5 | 89 (78%) |
| + DOM: `Document`, CSS `select`/`selectFirst`, `text`, `attr`, `getSrc`/`getHref`, `children`, siblings, `className` | 17 | 113 (99%) |
| + `cryptoHandler` (AES-CBC) | 18 | 114 (100%) |

For the entityJY index (19 files / 115 entries) the same order reaches 89 → 112 → 113, then
`Client.post` and `console` complete it (Batoto also needs `eval`, which the engine provides).

## 8. Recommended I2 order

1. **Prelude and core** (part of I1/I2): `MProvider`, `jsonStringify`, `Client` with GET (and POST, it
   is the same code path), response object, string helpers, single-argument `console`, `setTimeout`.
2. **`SharedPreferences.get`** with default materialisation from `getSourcePreferences()`, plus
   `getString`/`setString` (cheap, same store).
3. **DOM, CSS only:** `Document`/`Element` handles with Mangayomi's "never null" semantics, `select`,
   `selectFirst`, `text` as `textContent`, `attr`, `getSrc`/`getHref`/`getImg`/`getDataSrc` with the
   outer-HTML regex semantics, `children`, siblings, `className`/`localName`, `getElementsBy*`,
   `innerHtml`/`outerHtml`; selector errors → empty result.
4. **`cryptoHandler`** (AES-CBC with UTF-8 key/IV).
5. **Stubs** that reject clearly: `evaluateJavascriptViaWebview`, extractors, HLS, EPUB.
6. **Later, only if a source needs them:** XPath, `parseDates`, CryptoJS AES, AES-GCM, unpackers,
   `deobfuscateJsPassword`, other HTTP verbs and `Client` options.

Filters and preferences are adapter work (I3), but they gate 78% of entries, so I3 should land them
before the DOM-heavy single-site sources are tested.

## 9. Surprises

- **The JS catalogue is small and concentrated:** 18 distinct sites behind 114 entries; MangaDex and
  Comick are 75% of the entries. Several of these sites (MangaDex, Comick, Webtoons, Mangafire, Asura,
  Weeb Central, Batoto, …) are well-known Mihon extension targets too, so on Android and desktop the JS
  host adds little catalogue; its value is iOS (overlap not checked against a Mihon index here:
  **unverified**).
- **The JS sources live in a third-party fork.** The kodjodevf index points every JS manga entry at
  `entityJY/mangayomi-extensions-eJ`; the kodjodevf repository's own `javascript/` copies are not used.
  The kodjodevf repository's last commit is 2025-10-01, the fork's 2026-01-08. Two entries are
  already dead links.
- **No integrity data:** the index has no hash; files change under an unchanged version and the index
  version can lag the file.
- **The needed bridge is much smaller than the contract:** no XPath, no WebView, no date parsing, no
  unpacker, no extractor, one crypto call, no `Client` options.
- **Engine-sensitive dates:** 13 files use `new Date(…)`, mostly to parse chapter dates with QuickJS's own
  `Date` parser.
- **No app-version checks** (`appMinVerReq` is `0.5.0` everywhere), no Dart-only helpers and no
  anime-only API calls in manga files. `mangapill.js` (dead entry) stubs `getVideoList`.
- **Mangayomi quirks sources depend on:** `selectFirst` never returns null; invalid selectors give an
  empty list; `Element.text` and `:contains` include script content; global search passes an empty filter list, which
  the position-based sources do not survive (so in Mangayomi those sources fail in global search).
