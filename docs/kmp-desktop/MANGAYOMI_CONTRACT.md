# Mangayomi JavaScript extension contract

What a host must implement to run Mangayomi's JavaScript **manga** extensions. Written for step I0 of
[`PLAN.md`](PLAN.md) section 8, from reading the sources below; nothing here is copied code. Names of
classes, methods, message channels and JSON fields are listed because they are the interface.

| Source read | Commit | Date |
|-------------|--------|------|
| `kodjodevf/mangayomi` (app) | `bc0b410abeb4fb306fc29d7d38cb171c20320e9f` | 2026-11-16 |
| `kodjodevf/mangayomi-extensions` (index) | `6004f1f8d1a56f882dadb734ce26f50c626a3850` | 2025-10-01 |
| `entityJY/mangayomi-extensions-eJ` (the JS files the index points to, and its own index) | `f21a60bf026dd122f9b80042e1c3d220a38aa07a` | 2026-01-08 |
| `kodjodevf/flutter_qjs` (the QuickJS binding the app uses, branch `main`) | `b64d2e3a8b2bf3b32ab4c3acef34a11636d5b87f` | 2026-10-08 |

App files: `lib/eval/javascript/*.dart` (host and JS prelude), `lib/eval/model/*.dart` (result and
filter/preference models, `m_bridge.dart` helpers), `lib/services/http/m_client.dart` (HTTP),
`lib/utils/extensions/dom_extensions.dart` (selectors), `lib/services/fetch_sources_list.dart` and
`lib/services/extension_store_service.dart` (index, install, update), `lib/eval/lib.dart` (service
lifetime). The published index `https://kodjodevf.github.io/mangayomi-extensions/index.json` was
byte-identical to the repository's `index.json` at the commit above (checked 2026-11-17).

Statements marked **unverified** could not be confirmed from source or metadata.

**Contents:** [1 Index format](#1-index-format) · [2 Loading and lifecycle](#2-loading-and-lifecycle) ·
[3 Provider methods](#3-provider-methods) · [4 Host bridge](#4-host-bridge) ·
[5 Filters and preferences](#5-filters-and-preferences) · [6 Out of scope](#6-out-of-scope-and-how-to-fail) ·
[7 Compatibility notes](#7-compatibility-notes) · [8 Mapping to Komikku](#8-mapping-to-komikku-input-for-i3)

---

## 1. Index format

### 1.1 Repository

- A repository is added by the URL of its index file (for example `…/index.json`). Separate files exist
  per item type (`index.json` manga, `anime_index.json`, `novel_index.json`); the host reads only the
  manga index.
- The app downloads the URL (gzip is accepted). If the body starts with `[` it is the **legacy JSON
  array** described below. Then it also fetches `<index URL without file name>/repo.json`: optional
  object `{ "name", "website", "index_v2" }` (or the same under `meta`). `index_v2`, when set, redirects
  to another store format (Mihon protobuf / JSON stores, not relevant here). Name and website label the
  repository.
- There is **no default repository** in the app; users add URLs (deep links `mangayomi://add-repo?…`).

### 1.2 Entry fields (legacy array, one object per source)

All 20 fields below occur in every entry of both indexes read. Types are JSON types.

| Field | Type | Req. | Meaning and use by the app | Example |
|-------|------|------|----------------------------|---------|
| `id` | int | yes | Source identity: primary key of the installed source, key for update matching across repositories, key for per-source preferences. Positive, below 2^31 in all entries. **Must be taken from the index, never recomputed** (see 1.4). | `652112892` |
| `name` | string | yes | Display name; entries with empty name are dropped. | `"新新漫画"` |
| `lang` | string | yes | Language code as the source spells it (`en`, `pt-BR`, `zh-hk`, `es-419`, `all`, `All`). Also visible to the extension as `this.source.lang`, which multi-language files use to pick content. | `"zh"` |
| `baseUrl` | string | yes | Site root. Passed to the extension as `this.source.baseUrl`, used to resolve relative cover URLs, passed to `getHeaders(url)`, opened by the "open in WebView" action. | `"http://www.77mh.xyz"` |
| `apiUrl` | string | yes (may be `""`) | Secondary base URL exposed as `this.source.apiUrl`. Set on 88 of 114 JS manga entries (MangaDex, Comick, …). | `"https://api.mangadex.org"` |
| `iconUrl` | string | yes | Icon image URL. | raw GitHub URL |
| `typeSource` | string | yes | Family/theme name (`single`, `madara`, `mangareader`, `mangabox`, `mmrcms`, `nepnep`). Informational for the host; all JS entries are `single`. | `"single"` |
| `itemType` | int | yes | 0 manga, 1 anime, 2 novel. The app keeps only entries matching the index's item type. | `0` |
| `isManga` | bool | yes | Legacy duplicate of `itemType == 0`. Ignore. | `true` |
| `sourceCodeLanguage` | int | yes | 0 Dart, 1 JavaScript, 2 Mihon APK, 3 LNReader plugin, 4 Aidoku (enum order in the app). Host accepts only 1. | `1` |
| `sourceCodeUrl` | string | yes | URL of the source file, downloaded as text at install/update and stored. For JS manga it points to `raw.githubusercontent.com/entityJY/mangayomi-extensions-eJ/main/javascript/manga/src/<lang>/<file>.js` (a third-party fork, see the survey). | |
| `version` | string | yes | Dotted version, compared numerically per component (see 1.3). | `"0.0.35"` |
| `appMinVerReq` | string | yes | Minimum app version; entries with `appMinVerReq` greater than the running app's version are hidden. All JS manga entries say `"0.5.0"` (the app is 0.9.96). | `"0.5.0"` |
| `isNsfw` | bool | yes | Hidden from the extension list unless the user shows NSFW sources. | `false` |
| `hasCloudflare` | bool | yes | Only passed to the WebView screen as a hint. Not used by the HTTP stack. `false` on all JS manga entries. | `false` |
| `isFullData` | bool | yes | Source returns its whole catalogue on page 1; the app then pages locally in steps of 50 without calling the source again. `false` on all JS manga entries. | `false` |
| `dateFormat` | string | yes (may be `""`) | Date pattern (Dart `intl` `DateFormat` syntax) for `parseDates`; exposed as `this.source.dateFormat`. Empty on all JS manga entries. | `"MMMM dd, yyyy"` |
| `dateFormatLocale` | string | yes (may be `""`) | Locale for that pattern (`en_us`, `pt-br`, …); `this.source.dateFormatLocale`. | `"en_us"` |
| `additionalParams` | string | yes (may be `""`) | Free text for theme files; exposed as `this.source.additionalParams`. Empty on all JS manga entries. | `""` |
| `notes` | string | yes (may be `""`) | Shown as a banner on the source's browse screen. Set on 1–2 JS entries. | `"This Source Just For Boruto"` |

There is **no content hash, size or signature** in the index. The downloaded text can differ from what
the `version` field says (seen: Mangafire is `0.1.25` in the kodjodevf index, the file it points to says
`0.2.20`).

### 1.3 Install, update, obsolete

- **Install:** GET `sourceCodeUrl`, store the body as text together with all index fields. Then the app
  evaluates the source once and calls `getHeaders(baseUrl)`; the result is stored as the source's default
  headers (see 3.2).
- **Update detection:** for each installed source, the offers with the same `id` from all repositories
  are compared; an update exists when `compareVersions(installed.version, offer.version) < 0`.
  `compareVersions`: strip a leading `v`/`V` and any `+build` suffix, split off a `-pre-release` part,
  compare the dot-separated parts as integers (missing or non-numeric parts count as 0); if equal, a
  version without pre-release is greater than one with; pre-release parts compare numerically when both
  are numbers, a number sorts before text, text compares lexically, then the longer list wins.
  Auto-update (a setting) installs updates from the source's own repository directly.
- **Obsolete:** an installed source whose `id` is no longer listed by the repository it came from is
  flagged obsolete (kept); a not-installed one is removed from the list.
- **Duplicate ids:** ids are unique among the JS manga entries, but two Dart entries share ids within
  the index, and the kodjodevf and entityJY indexes list the same JS sources under the same ids. A
  host must treat "same id" as "same source" across repositories and pick one offer (Mangayomi picks by
  version).

### 1.4 How ids are formed (by the index generator, not the app)

The generator (`source_generator.dart` in the extensions repositories) reads the
`const mangayomiSources = [ … ];` array literal at the top of each `.js` file (a JSON array; fields as
in 1.2 plus `pkgPath`/`pkgName`, `langs` and `ids`). For each object:

- If it has `langs`, it emits one entry per language; the id is `ids[lang]` when given, otherwise the
  Dart `String.hashCode` of the text `mangayomi-js-"<lang>"."<name>"`.
- Otherwise the id is the object's `id`, or the Dart `String.hashCode` of `mangayomi-js-"<lang>"."<name>"`.

Dart's `String.hashCode` is not a documented, portable function, so a host cannot reproduce ids: it must
use the index `id`. `sourceCodeUrl` is built from the generator's fixed repository prefix plus `pkgPath`.

## 2. Loading and lifecycle

### 2.1 Engine

- QuickJS, version string `2026-06-04` (Bellard's QuickJS, built with `CONFIG_VERSION="2026-06-04"`),
  through the app's fork of `flutter_qjs` (MIT). One runtime + one context per service, max stack size
  4 MiB, **no memory limit and no timeout** set by the app. The context has the standard intrinsics
  only (base objects, Date, eval, String.normalize, RegExp, JSON, Proxy, Map/Set, typed arrays,
  Promise, WeakRef) — no `std`/`os` modules.
- The source is evaluated as a **global script**, not a module.

### 2.2 Load sequence (one service)

1. Create the runtime. The binding installs a global `sendMessage(channel, jsonString)` function (see
   4.1), a `console` object routed to channel `ConsoleLog`, a `setTimeout(fn, ms)` polyfill (channel
   `SetTimeout`; no `clearTimeout`, no `setInterval`, no extra arguments) and promise bookkeeping
   helpers.
2. Evaluate the prelude pieces, in this order: the HTTP client wrapper (4.2), the DOM wrappers (4.3),
   the utility functions plus `console.log/warn/error` and the `String.prototype.substring*` helpers
   (4.4, 4.5), the video extractor wrappers (anime only, 6), the `SharedPreferences` wrapper (4.6).
3. Evaluate the base class `MProvider` and a helper `jsonStringify(fn)` that awaits `fn()` and returns
   `JSON.stringify` of the result. `MProvider` has a getter `source` that returns a fresh object built
   from the installed source: `{ apiUrl, baseUrl, dateFormat, dateFormatLocale, hasCloudflare, id,
   isFullData, lang, name, additionalParams, notes }` (values from the index entry; `notes` is always
   `null` at runtime). Every other member throws `Error("<member> not implemented")`: the getter
   `supportsLatest`, and the methods listed in section 3.
4. Evaluate, as **one** script, the extension text followed by a statement that creates the provider:
   a global variable `extention` (sic) assigned `new DefaultExtension()`. So the extension must declare
   a global class `DefaultExtension` that extends `MProvider`; its constructor takes no arguments (some
   call `super()` and set fields such as `this.client = new Client()`). The top-level
   `const mangayomiSources = […]` declaration is evaluated too and is otherwise unused at runtime.
   A failure here makes every later call fail with "Extension "<name>" failed while loading the source".

### 2.3 Lifetime

- Data calls (popular, latest, search, detail, page list) each **create a fresh service** (fresh
  runtime, prelude and extension evaluation) and dispose it afterwards. Metadata calls (headers, filter
  list, preferences, base URL) use a small LRU cache of up to 6 live services keyed by source id,
  version and source-code hash. Extensions therefore cannot rely on in-memory state surviving between
  data calls; persistent state goes through `SharedPreferences` (4.6). A host may reuse an engine across
  calls, but must not depend on extensions tolerating either choice beyond that.
- Disposal clears the element handle table (4.3). (The app's dispose does not free the QuickJS runtime
  itself; a host should.)

### 2.4 Calling convention and values crossing the boundary

- **Host → JS:** the host builds a call expression as source text, with every argument encoded as a JSON
  literal (strings JSON-escaped, the filter list as a JSON array), for example
  `search("one piece",2,[…])`.
- **Async methods** (`getPopular`, `getLatestUpdates`, `search`, `getDetail`, `getPageList`, …): the host
  evaluates `jsonStringify(() => extention.<call>)`, which returns a promise; it then drives the
  QuickJS job queue until the promise settles and reads the resolved **JSON string**, which it decodes.
  A rejection or thrown error becomes an exception with the message
  `Extension "<source name>" failed while <call>: <JS error text>`.
- **Sync members** (`getHeaders`, `supportsLatest`, `getFilterList`, `getSourcePreferences`): the host
  evaluates `JSON.stringify(extention.<call>)` synchronously and decodes the string. If the JS throws an
  error whose text contains `not implemented` and the host has a default, the default is used
  (`{}`, `true`, `[]`, `[]`); any other error is reported. If the member returns a promise, the result
  is `{}` (a promise stringifies to an empty object), so these members must be synchronous. (Komikku's host
  awaits such a promise instead; no surveyed source returns one.)
- **JS → host** (bridge): see 4.1. Arguments always travel as one JSON string; results come back as
  native JS values (string, number, bool, null, array, object) or as a promise for async channels.

## 3. Provider methods

Signatures as the host calls them. "Page result" is `{ "list": [Manga…], "hasNextPage": bool }`.

| Member | Kind | Called with | Returns | Notes |
|--------|------|-------------|---------|-------|
| `getPopular(page)` | async | page number, 1-based | page result | |
| `getLatestUpdates(page)` | async | page number | page result | Only offered when `supportsLatest` is true. |
| `search(query, page, filters)` | async | string, number, filter array (5.1) | page result | Mangayomi passes the current filter list; its **global search and migration pass `[]`**. |
| `getDetail(url)` | async | the manga's `link` | Manga with `chapters` | One call gives details and chapters. |
| `getPageList(url)` | async | the chapter's `url` | array of page entries | The base class declares it without a parameter; the host passes the URL. |
| `getHeaders(url)` | **sync** | always `source.baseUrl` | object of header name → value | Called at install and when loading covers/pages. |
| `supportsLatest` | **sync getter** | — | bool | Read as a property, not called. Default `true`. |
| `getFilterList()` | **sync** | — | filter array (5.1) | Default `[]`; parse errors also give `[]`. |
| `getSourcePreferences()` | **sync** | — | preference array (5.2) | Default `[]`. |
| `getVideoList(url)` | async | episode URL | video list | Anime: out of scope. |
| `getHtmlContent(name, url)`, `cleanHtmlContent(html)` | async | — | string | Novels: out of scope. |

### 3.1 Manga object (list items and detail)

| Field | Type | Meaning |
|-------|------|---------|
| `name` | string | Title. |
| `link` | string | Opaque key the source understands: absolute URL, path, or an id. Passed back to `getDetail`. |
| `imageUrl` | string | Cover. If it does not start with `http`, the app prefixes `baseUrl + "/"` + its path. |
| `description` | string | |
| `author`, `artist` | string | |
| `status` | int | 0 ongoing, 1 completed, 2 on hiatus, 3 cancelled, 4 publishing finished, anything else or missing (sources use 5) unknown. Some sources send 4 for "unreleased". |
| `genre` | array of strings | Values are trimmed and de-duplicated by the app. |
| `chapters` | array of Chapter | Detail only. `episodes` is accepted as an alias. |

List items normally carry only `name`, `imageUrl`, `link`. In `getDetail` every field is optional: a
missing or empty field keeps the stored value (status `unknown` keeps the stored status).
`hasNextPage` must be a boolean: if it is missing Mangayomi's decoding fails (a host should treat missing
as `false`).

### 3.2 Chapter object

| Field | Type | Meaning |
|-------|------|---------|
| `name` | string | Required in practice (a missing name crashes Mangayomi's merge). |
| `url` | string | Required; entries with empty URL are skipped. Passed back to `getPageList`. |
| `dateUpload` | string | Milliseconds since the epoch **as a decimal string** (sources write `new Date(x).valueOf().toString()`). May be `null` (→ "now" in Mangayomi) or the string `"NaN"` when the source's date parse failed. A JSON number is rejected by Mangayomi's decoder; a host should accept both. |
| `scanlator` | string | Optional. |
| `isFiller`, `thumbnailUrl`, `description`, `downloadSize`, `duration` | | Anime fields; ignore. |

**Ordering:** newest first (index 0 = latest chapter). Mangayomi de-duplicates by URL without domain and
by (name, scanlator), and inserts new chapters in reverse so the oldest gets the lowest id. This matches
Mihon's convention that source order 0 is the top of the source's list.

### 3.3 Page list entries

Each element is either a **string** (the image URL; trimmed) or an **object** `{ "url": string,
"headers": { name: value } }` (other keys are ignored). `null` entries are skipped. Entries are
de-duplicated by URL, keeping the first. Page images are requested with the source's `getHeaders`
result merged with the entry's own headers. 4 of 18 JS manga files return objects with headers.

### 3.4 Headers

`getHeaders(baseUrl)` returns a plain object; values are converted to strings. The app uses it for
cover and page image requests. It is evaluated at install time too, so it may read preferences
(defaults are created on first read, 4.6). It is **not** applied automatically to `Client` requests:
extensions pass headers to each `Client` call themselves.

### 3.5 Base URL override

There is no host API for it. `sourceBaseUrl` is always the index `baseUrl`. Sources that let the user
change the domain do it themselves through an `editTextPreference` (keys seen: `domain_url`, `url`,
`overrideBaseUrl1`, `mobile_url`) and read it with `SharedPreferences.get`.

## 4. Host bridge

### 4.1 Channel mechanism

- `sendMessage(channel, jsonString)` is the only native entry point. The host JSON-decodes the string
  (always a JSON array for the channels below) and calls the handler registered for `channel`.
- A handler's return value is converted to a JS value: string, number, bool, null, list → array,
  map → object. If the handler is asynchronous, `sendMessage` returns a **promise** of that value.
- An unknown channel logs "No channel … registered" and returns `null` (no error).
- A handler error becomes a thrown JS exception (synchronous handlers) or a rejected promise (async
  handlers). The thrown value is the converted error, typically a string.
- The wrappers below are global classes/functions defined by the prelude; extensions use those, never
  `sendMessage` directly (none of the 21 surveyed files does).

### 4.2 HTTP: `Client`

`new Client(reqcopyWith?)` then `await client.get(url, headers?)`, `head(url, headers?)`,
`post(url, headers?, body?)`, `put(…)`, `delete(…)`, `patch(…)`. Each is `async` and sends channel
`http_<method>` (`http_get`, `http_post`, `http_head`, `http_put`, `http_delete`, `http_patch`) with
`[null, reqcopyWith, url, headers, body]` (no body element for get/head), and returns the parsed
response object. Network failures reject; **HTTP error statuses do not** (the response is returned).

**`reqcopyWith` options** (all optional; none is used by any JS manga source surveyed):

| Key | Effect in Mangayomi |
|-----|---------------------|
| `useDartHttpClient` | `true`: use Dart's `dart:io` client instead of the default Rust `reqwest`-based client (`rhttp`). |
| `noProxy` | Default client: bypass the system proxy. |
| `timeout` | Default client: overall timeout in **seconds**. Default none. |
| `connectTimeout` | Default client: connect timeout in seconds. |
| `verifyCertificates` | Default client: TLS certificate verification. **Default `false`** (certificates are not verified unless a source asks). |
| `followRedirects`, `maxRedirects` | Copied onto the request; only honoured by the Dart client. The default client always follows up to 10 redirects. |
| `contentLength`, `persistentConnection` | Copied onto the request. |

**Request handling:**

- `headers`: object; values stringified. Missing → none.
- Body: a string is sent as text (UTF-8; `text/plain` content type unless set); an object is sent
  form-URL-encoded; an array is sent as raw bytes. **If the request's `Content-Type` contains
  `application/json`, the body is always JSON-encoded first** — an object becomes JSON, and a string is
  encoded again as a JSON string literal (double encoding).
- **Cookies:** responses' `Set-Cookie` headers are **not** stored (no cookie jar; `reqwest` is built
  without its cookie feature). The only automatic cookies are those the app saved from its WebView or
  Cloudflare solving, attached per host when the request has no `Cookie` header of its own. Extensions
  that need session cookies pass them in headers themselves.
- **User agent:** the app's stored WebView user agent is added only when such cookies are attached, or on
  Linux; otherwise no user agent is sent unless the extension sets one.
- DNS-over-HTTPS and a custom DNS server are applied when the user enabled them.
- **Cloudflare:** a response with header `cf-mitigated: challenge` triggers up to 2 retries after
  solving: through a FlareSolverr/Byparr-compatible proxy when configured, else a headless WebView that
  waits up to 15 s for a `cf_clearance` cookie (not on Linux). On failure a toast is shown and the
  challenge response is returned to the extension.

**Response object** (all fields always present):

| Field | Type | Meaning |
|-------|------|---------|
| `body` | string | Decoded body. With the default path, decoded by Dart's `package:http` from the `Content-Type` charset; without a charset it falls back to Latin-1 (**unverified** in detail; a special case for JSON may exist in newer `package:http`). In the JSON-request branch above the body is always decoded as UTF-8. |
| `statusCode` | int | |
| `headers` | object | Response headers, lower-case names, one string per name (how repeated headers are joined is **unverified**; Komikku joins them with `", "`). |
| `isRedirect` | bool | Always `false` with the default client. |
| `persistentConnection` | bool | |
| `reasonPhrase` | string or null | `null` with the default client. |
| `request` | object | `{ contentLength, finalized, followRedirects, headers, maxRedirects, method, persistentConnection, url }` — `url` is the **original** request URL, not the final URL after redirects. |

Surveyed sources read only `body` and `statusCode`.

### 4.3 DOM: `Document` and `Element`

Parser: Dart `package:html` (HTML5). CSS selectors: the `pseudom` package plus custom pseudo-classes;
XPath: `xpath_selector_html_parser`.

**Handles.** `new Document(html)` only stores the string; **every** method re-parses it. Methods that
return elements make the host store the element in a per-service table and return an integer key;
the JS `Element` wraps that key. A lookup that finds nothing still stores an entry (holding null) and
returns a key, so **`selectFirst`, `getElementById`, siblings, `body`, … never return `null` in JS**:
they return an `Element` whose string getters give `""`, whose `attr` gives `""`, and whose `select`
gives `[]`. Code such as `if (doc.selectFirst(x))` is therefore always true; sources test
`.text != ""` instead. The table is cleared when the service is disposed.

`Document` members (each a sync message carrying `this.html`):

| Member | Channel | Result |
|--------|---------|--------|
| `body`, `documentElement`, `head`, `parent` (getters) | `get_doc_element` `[html, type]` | Element (`parent` of a document is always the empty element) |
| `text`, `outerHtml` (getters) | `get_doc_string` `[html, type]` | string (`""` if none). What `text` returns for a document is **unverified**; Komikku returns the `textContent` of the whole document |
| `selectFirst(css)` | `doc_select_first` `[html, css]` | Element |
| `select(css)` | `doc_select` `[html, css]` | array of Element (`[]` on no match **or on an invalid selector**) |
| `xpathFirst(xpath)` | `doc_xpath_first` `[html, xpath]` | string: the attribute/text value of the first match, `""` if none |
| `xpath(xpath)` | `doc_xpath` `[html, xpath]` | array of strings (trimmed values) — **only when more than one node matches; a single match gives `[]`** |
| `children`, `getElementsByTagName(n)`, `getElementsByClassName(n)` | `doc_get_elements_by` `[html, type, name]` | array of Element |
| `getElementById(id)` | `doc_get_element_by_id` `[html, id]` | Element |
| `attr(name)`, `hasAttr(name)` | `doc_attr`, `doc_has_attr` | always `""` / `false` (a document has no attributes) |
| `getElement(type)`, `getString(type)`, `getElementsListBy(type, name)` | — | the generic forms behind the getters above |

`Element` members (each a sync message carrying the key):

| Member | Channel | Result |
|--------|---------|--------|
| `text` | `get_element_string` `["text", key]` | DOM `textContent`: all descendant text concatenated, **not** whitespace-normalised, including `<script>`/`<style>` content |
| `innerHtml`, `outerHtml`, `className`, `localName`, `namespaceUri` | same, with that type | string |
| `getSrc`, `getImg`, `getHref`, `getDataSrc` (getters) | same | First match of the regex `src="…"`, `img="…"`, `href="…"`, `data-src="…"` in the element's **serialised outer HTML** (so it can come from a descendant, `getSrc` also matches inside `data-src="…"`, and the value is still HTML-escaped, e.g. `&amp;`); `""` if none |
| `attr(name)` | `ele_attr` `[name, key]` | attribute value or `""` |
| `hasAttr(name)` | `ele_has_attr` | **broken**: the wrapper sends `[undefined, name]`, so it always returns `false` |
| `selectFirst(css)`, `select(css)` | `ele_selectFirst`, `ele_select` `[css, key]` | Element / array of Element, matched within the element (whether the element itself can match is **unverified**; Komikku matches descendants only, like DOM `querySelectorAll`) |
| `previousElementSibling`, `nextElementSibling` | `ele_element_sibling` `[type, key]` | Element |
| `children` (getter), `getElementsByTagName(n)`, `getElementsByClassName(n)` | `ele_get_elements_by` `[type, name, key]` | array of Element |
| `xpath(x)`, `xpathFirst(x)` | channels `xpath` / `xpathFirst` | **broken**: those channels are not registered (the host registers `ele_xpath` / `ele_xpathFirst`), so they return `null` |

**CSS support.** `:not(…)` is rewritten to a custom implementation; custom pseudo-classes:
`:nth-child`, `:nth-last-child`, `:nth-of-type`, `:nth-last-of-type`, `:first-child`, `:last-child`,
`:first-of-type`, `:last-of-type`, `:only-child`, `:only-of-type`, `:empty`, `:root`, `:has`, `:is`,
`:contains` and `:containsOwn` (case-insensitive substring of the element's `text`, i.e. `textContent`,
so `script:contains(x)` matches script code — unlike jsoup), `:containsData` (script/style text),
`:containsWholeText`, `:containsWholeOwnText`, `:matches`, `:matchesWholeText`,
`:matchesWholeOwnText` (regex), `:eq(n)`, `:lt(n)`, `:gt(n)` (0-based index among the parent's element
children). This is jsoup's selector vocabulary; jsoup (Android/desktop) and Ksoup (iOS) cover it.
Surveyed sources use plain selectors, `+` combinators, `:contains` (6 uses) and `:has` (1 use).

### 4.4 Utility functions (globals)

| Function | Sync | Channel and behaviour |
|----------|------|-----------------------|
| `cryptoHandler(text, iv, key, encrypt)` | sync | AES-CBC with PKCS#7 padding; key and IV are the **UTF-8 bytes** of the strings (key 16/24/32 bytes, IV 16). Encrypt: UTF-8 text → base64. Decrypt: base64 → UTF-8 text. **On any error returns `text` unchanged.** |
| `encryptAESCryptoJS(plain, passphrase)` | sync | CryptoJS/OpenSSL passphrase format: random 8-byte salt, key (32 bytes) + IV (16 bytes) from MD5-based `EVP_BytesToKey` (one iteration) over trimmed passphrase + salt, AES-256-CBC PKCS#7, output base64 of `"Salted__" + salt + ciphertext`. Input trimmed. Errors throw. |
| `decryptAESCryptoJS(b64, passphrase)` | sync | Inverse of the above (salt from bytes 8–16). Errors throw. |
| `decryptAESGCM(b64, keyHex, ivHex, tagHex = "")` | sync | AES-GCM; `tagHex` is appended to the ciphertext when given (empty when the tag is already appended). Returns UTF-8 plaintext, **or the input on failure**. |
| `deobfuscateJsPassword(s)` | sync | Decodes a JSFuck-style digit string: each `[...]` group becomes the count of `!+[]` in it (1–9; a lone `+[]` is 0; else `-`), each `(...)` group becomes `.`. |
| `unpackJs(packed)` | sync | Dean Edwards *p.a.c.k.e.r.* unpacker (Dart `js_packer` package); `""` on failure. |
| `unpackJsAndCombine(script)` | sync | Regex-based unpacker: finds every `eval(function(p,a,c,k,e,…` payload, substitutes words from the symbol table (radix up to 36 numeric, 52/54/62/95 with fixed alphabets), joins all unpacked payloads with a space; `""` if none. |
| `parseDates(values, dateFormat, locale)` | sync | See 4.7. Returns an array of millisecond strings. |
| `evaluateJavascriptViaWebview(url, headers, scripts, time = 30)` | async | Loads `url` with `headers` in a headless WebView, runs each script after load, waits up to `time` seconds for the page to call `window.flutter_inappwebview.callHandler('setResponse', value)`; resolves to that string, `""` on timeout. |
| `createHlsProxyUrl(url, headers = {})` | async | Anime (local HLS proxy). |
| `parseEpub(bookName, url, headers)`, `parseEpubChapter(bookName, url, headers, chapterTitle)` | async | Novels (EPUB download and parse). |

### 4.5 Language helpers installed by the prelude

- `String.prototype.substringAfter(p)`, `substringAfterLast(p)`, `substringBefore(p)`,
  `substringBeforeLast(p)`: Kotlin semantics, returning the whole string when `p` is absent —
  except `substringAfterLast`, which is `split(p).pop()` (same result).
- `String.prototype.substringBetween(left, right)`: text between the first `left` and the next `right`;
  `""` if either is missing.
- `console.log/warn/error(message)`: **one argument only** (further arguments are dropped); objects are
  `JSON.stringify`-ed; the message's `toString()` is sent on channel `log` (so `console.log(undefined)`
  throws). Output goes to the app log only when logging is enabled.

### 4.6 `SharedPreferences`

`new SharedPreferences()` with three **synchronous** methods:

| Method | Channel | Behaviour |
|--------|---------|-----------|
| `get(key)` | `get` `[key]` | Current value of the source preference `key` declared in `getSourcePreferences()`: list → the selected `entryValues[valueIndex]` (string); checkbox / switch → bool; edit text → its `value` string; multi-select → array of selected value strings. If nothing is stored yet, the app evaluates the source's `getSourcePreferences()`, stores the declared default and returns it. **An unknown key throws** ("Error when getting source preference"). Extra arguments (some sources pass a default) are ignored. |
| `getString(key, default)` | `getString` | Free key/value string store per source, separate from the declared preferences; returns the stored string or stores and returns `default`. |
| `setString(key, value)` | `setString` | Stores the string; returns `null`. |

Storage is per source id, persistent, and survives updates.

### 4.7 `parseDates` semantics

Input values are stringified and trimmed; empty values are dropped. For each value, in order:

1. All digits: 10 digits → seconds ×1000; 12–14 digits → milliseconds.
2. Contains `T` and parses as ISO-8601 → that instant.
3. Starts with a "today" word (`today`, `hoy`, `aujourd'hui`, `oggi`, `heute`, `bugün`, `hari ini`,
   Arabic, Chinese, Vietnamese …) → local midnight today; "yesterday" words → midnight yesterday;
   "day before yesterday" words → two days ago.
4. Ends with an "ago" word (`ago`, `atrás`, `önce`, `قبل`, `fa`, `vor`, `trước`) or starts with
   `hace`/`il y a`/`vor` → now minus the first number found × the unit found by keyword (days, hours,
   minutes, seconds, weeks, months = 30 days, years = 365 days; keywords in ~10 languages, including
   one-letter `d`/`h`/`m`/`s`/`w`).
5. Remove ordinal suffixes (`1st` → `1`).
6. If `dateFormat` is set, parse with it (Dart `intl` `DateFormat`, locale `dateFormatLocale`).
7. Otherwise: digits and separators only → try 19 fixed numeric patterns (`yyyy-MM-dd`, `dd/MM/yyyy`,
   `MM/dd/yyyy`, …) in English; with letters → try 26 textual patterns (`dd MMMM yyyy`,
   `MMMM dd, yyyy`, `MMM d, yyyy`, `dd 'de' MMMM 'de' yyyy`, `yyyy'年'M'月'd`, …) in `dateFormatLocale`,
   then 18 common locales, then every locale `intl` knows. A successful pattern/locale is reused for
   the following values.
8. Fallbacks: relative parse anywhere in the string, then ISO-8601; otherwise "now".

**Once one value fails completely, every later value in the same call becomes "now".** Result strings
are milliseconds. Patterns are Dart `intl` syntax (`yyyy`, `MM`, `MMM`, `MMMM`, `LLLL`, `d`, quoted
literals): close to `java.text.SimpleDateFormat`, but `LLLL` (stand-alone month) and the locale data
differ; a port maps patterns 1:1 and uses CLDR month names (**unverified** for edge cases). No surveyed
manga source calls `parseDates`.

## 5. Filters and preferences

### 5.1 Filters

`getFilterList()` returns an array of filter objects; the same objects, with their `state` changed by the
UI, are passed to `search`. Every object has `type_name` (the discriminator) and usually `type` (a free
string id sources use to find the filter) and `name` (label). Unknown `type_name` values are dropped.

| `type_name` | Fields | `state` sent to `search` |
|-------------|--------|--------------------------|
| `HeaderFilter` | `name` | — (no state) |
| `SeparatorFilter` | — | — |
| `TextFilter` | `name` | string, default `""` |
| `CheckBox` | `name`, `value` | bool, default `false` |
| `TriState` | `name`, `value` | int: 0 ignore, 1 include, 2 exclude (UI cycles 0→1→2) |
| `SelectFilter` | `name`, `values`: array of `SelectOption` | int, index into `values`, default 0 |
| `SelectOption` | `name` (label), `value` (string) | — (only inside `values`) |
| `SortFilter` | `name`, `values`: array of `SelectOption` | object `{ "type_name": "SortState", "index": int, "ascending": bool }`, default index 0 ascending false |
| `GroupFilter` | `name` | array of child filters (in practice `CheckBox` / `TriState`) with their own `state` |

On the way back, the app re-serialises each filter with the fields above: `type`, `name`, `state`,
`values`, `value`, and the canonical `type_name`. Other keys a source put on a filter are lost
(quirk: a `HeaderFilter`'s `type` is read back from a `value` key, so it usually becomes `null`).

Sources address filters **by position** (`filters[3].state`) or by `type`; a few mutate the list
returned by their own `getFilterList()` and call `search` from `getPopular`. A host must therefore send
the complete list in the original order, and should send the defaults (not `[]`) when the user has not
touched the filters — Mangayomi's global search sends `[]`, which makes position-based sources throw.

### 5.2 Source preferences

`getSourcePreferences()` returns an array of `{ "key": string, <one type field>: {…} }`:

| Type field | Fields | Value read back by `get(key)` |
|------------|--------|-------------------------------|
| `checkBoxPreference` | `title`, `summary`, `value` (bool) | bool |
| `switchPreferenceCompat` | `title`, `summary`, `value` (bool) | bool |
| `listPreference` | `title`, `summary`, `valueIndex` (int, clamped ≥ 0), `entries` (labels), `entryValues` | `entryValues[valueIndex]` |
| `multiSelectListPreference` | `title`, `summary`, `entries`, `entryValues`, `values` (selected) | array of strings |
| `editTextPreference` | `title`, `summary`, `value`, `dialogTitle`, `dialogMessage`, `text` | `value` |

Declared values are defaults; the user's choice is stored per source and key and wins afterwards.

## 6. Out of scope and how to fail

| Item | Host behaviour |
|------|----------------|
| Entries with `sourceCodeLanguage` ≠ 1 or `itemType` ≠ 0 | Not listed by the JS store. |
| `getVideoList`, `getHtmlContent`, `cleanHtmlContent` | Never called. |
| Video extractors (`sibnetExtractor`, `myTvExtractor`, `okruExtractor`, `voeExtractor`, `vidBomExtractor`, `streamlareExtractor`, `sendVidExtractor`, `yourUploadExtractor`, `gogoCdnExtractor`, `doodExtractor`, `streamTapeExtractor`, `mp4UploadExtractor`, `streamWishExtractor`, `filemoonExtractor`, `quarkVideosExtractor`, `ucVideosExtractor`, `quarkFilesExtractor`, `ucFilesExtractor`), `createHlsProxyUrl`, `parseEpub`, `parseEpubChapter` | Define the globals so a source still loads; calling one rejects with an error that names the function and says it is not supported. |
| `evaluateJavascriptViaWebview` | Stub at first (`HostWebView`): reject with a clear "needs a WebView, not supported yet" error. No surveyed manga source uses it. |
| `Element.xpath`/`xpathFirst`/`hasAttr` | No surveyed source calls them. Komikku (I2): `hasAttr` implemented properly; `xpath` / `xpathFirst` on `Document` and `Element` throw an "unsupported" error (Ksoup has no XPath). |
| Unknown `sendMessage` channel | Return `null` like Mangayomi, and log. |
| Engine limits | Mangayomi sets none; the host adds a per-call timeout and memory limit (PLAN 8.6), reported as source errors. |

## 7. Compatibility notes

- **Engine:** QuickJS `2026-06-04`, standard intrinsics only (see 2.1): ES2023+ syntax including
  classes, `async`/`await`, optional chaining, `??`, regex named groups and lookbehind, BigInt, typed
  arrays, `Proxy`, `WeakRef`, `eval` and `new Function`. One surveyed source (Batoto, only in the
  entityJY index) `eval`s script text taken from the website to get its image list — in-engine, and
  acceptable only because the engine has no capabilities beyond the bridge. Another unpacks packed
  scripts with its own JavaScript code instead of `unpackJs`.
- **`Date.parse` / `new Date(…)`** appears in 13 of 18 files, mostly to build `dateUpload`; its leniency
  is QuickJS's, so the host engine should be the same QuickJS line to get the same dates.
- **Absent globals:** `atob`, `btoa`, `fetch`, `XMLHttpRequest`, `TextEncoder`/`TextDecoder`, `URL`,
  `URLSearchParams`, `structuredClone`, `queueMicrotask`, `clearTimeout`, `setInterval`, `require`,
  `module`, `window`, `document`. Sources that need base64 bring their own (Mangafire defines local
  `atob`/`btoa`-like helpers; one Chinese source defines `atob` inside obfuscated code). A host may add
  more globals; it must not remove these semantics: `setTimeout(fn, ms)` exists.
- **Script mode:** the extension and the provider creation are one global script; top-level `const`,
  `let` and `class` declarations become global lexical bindings, so the same text cannot be evaluated
  twice in one context. Such a declaration also fails ("redeclaration") when the prelude made the same name a
  non-configurable global (a global `function` or `var`); a host should install its helper functions as
  configurable `globalThis` properties so extensions may declare their own (several define `atob`-like helpers).
- **Out of memory:** when QuickJS cannot even allocate the `InternalError`, the pending exception is `null`
  (seen with `quickjs-kt` at a 16 MiB limit); a host should treat a bare `null` error as out-of-memory.
- **Strings:** values cross the bridge as JSON text, so any Unicode survives; lone surrogates do not
  (**unverified**). Response bodies are decoded before they reach JS (4.2); there is no binary body
  access from JS.
- **Console:** see 4.5 (single argument).
- **Timers and promise jobs:** the host drives the QuickJS job queue until the call's promise settles;
  `setTimeout` callbacks run from the host's event loop.

## 8. Mapping to Komikku (input for I3)

| Mangayomi | Komikku |
|-----------|---------|
| Manga `name`, `link`, `imageUrl`, `description`, `author`, `artist`, `genre` | `SManga.title`, `url` (opaque), `thumbnail_url` (relative → `baseUrl` + path), `description`, `author`, `artist`, `genre` (comma-joined) |
| `status` 0/1/2/3/4/other | `SManga.ONGOING` / `COMPLETED` / `ON_HIATUS` / `CANCELLED` / `PUBLISHING_FINISHED` / `UNKNOWN` |
| `getDetail` (details + chapters in one call) | `getMangaDetails` and `getChapterList` both served from one `getDetail` call (short-lived cache per URL) |
| Chapter `name`, `url`, `dateUpload`, `scanlator` | `SChapter.name`, `url`, `date_upload` (string or number → Long; invalid/`NaN` → 0), `scanlator`; same newest-first order |
| Page string / `{url, headers}` | `Page(index, imageUrl = url)`; per-page headers must be kept with the page (e.g. encoded in `Page.url`) so downloads and retries use them |
| `getHeaders(baseUrl)` | `HttpSource.headers` for cover and image requests |
| `supportsLatest` | `CatalogueSource.supportsLatest` |
| Filters (5.1) | `Filter.Header`, `Separator`, `Text`, `CheckBox`, `TriState` (same 0/1/2 numbering as `Filter.TriState`), `Select`, `Sort` (`Selection(index, ascending)`), `Group`; keep the original JSON per filter to round-trip `type`, `value`, `values` |
| Preferences (5.2) | `ConfigurableSource.setupPreferenceScreen`: `CheckBoxPreference`, `SwitchPreferenceCompat`, `ListPreference`, `MultiSelectListPreference`, `EditTextPreference`, stored in the source's `SharedPreferences` and read back by the bridge `get` |
| Index `id` (int) | Komikku source id derived deterministically from it plus a fixed namespace (PLAN I-D4); never from name/lang |
| No hash in index | Record a SHA-256 of the downloaded text at install; show "changed" when it differs on update (PLAN I-D5 as corrected in 8.9) |
