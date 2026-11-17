package app.komikku.sourcejs

/**
 * The JavaScript prelude evaluated before every extension: Komikku's own text exposing Mangayomi's names, signatures
 * and behaviour as documented in docs/kmp-desktop/MANGAYOMI_CONTRACT.md (section numbers below). It talks to the
 * host only through `__kmk_host_sync(channel, argsJson)` and `__kmk_host_async(channel, argsJson)` (JSON text in
 * and out, see `JsSession`).
 *
 * Helper functions are installed as `globalThis` properties (configurable), so an extension that declares its own
 * function or `const` with the same name still loads; the classes are global `class` declarations like Mangayomi's.
 */
internal object JsPrelude {

    /** Bridge plumbing (4.1), console (4.5), `setTimeout` (2.2), string helpers (4.5). */
    private const val CORE = """
globalThis.__kmk_sync = function (channel, ...args) {
  const result = __kmk_host_sync(channel, JSON.stringify(args));
  return result == null ? null : JSON.parse(result);
};
globalThis.__kmk_async = async function (channel, ...args) {
  const result = await __kmk_host_async(channel, JSON.stringify(args));
  return result == null ? null : JSON.parse(result);
};
globalThis.__kmk_elements = function (keys) {
  return (keys || []).map((key) => new Element(key));
};
globalThis.__kmk_unsupported = function (name, reason) {
  return __kmk_sync("unsupported", name, reason);
};
globalThis.__kmk_unsupported_async = async function (name, reason) {
  return __kmk_sync("unsupported", name, reason);
};

// 4.5: one argument; objects are JSON-encoded. (Mangayomi throws for console.log(undefined); Komikku logs it.)
globalThis.__kmk_log = function (level, message) {
  const text = message !== null && typeof message === "object" ? JSON.stringify(message) : String(message);
  __kmk_sync("log", level, text);
};
globalThis.console = {
  log(message) { __kmk_log("info", message); },
  warn(message) { __kmk_log("warn", message); },
  error(message) { __kmk_log("error", message); },
};

// 2.2: setTimeout(fn, ms) only; no clearTimeout / setInterval / extra arguments.
globalThis.setTimeout = function (callback, ms) {
  __kmk_async("sleep", Number(ms) || 0).then(() => callback());
  return 0;
};

// 4.5: Kotlin-style substring helpers; the whole string when the delimiter is absent.
String.prototype.substringAfter = function (pattern) {
  const index = this.indexOf(pattern);
  return index === -1 ? this.toString() : this.substring(index + pattern.length);
};
String.prototype.substringAfterLast = function (pattern) {
  return this.split(pattern).pop();
};
String.prototype.substringBefore = function (pattern) {
  const index = this.indexOf(pattern);
  return index === -1 ? this.toString() : this.substring(0, index);
};
String.prototype.substringBeforeLast = function (pattern) {
  const index = this.lastIndexOf(pattern);
  return index === -1 ? this.toString() : this.substring(0, index);
};
String.prototype.substringBetween = function (left, right) {
  const start = this.indexOf(left);
  if (start === -1) return "";
  const from = start + left.length;
  const end = this.indexOf(right, from);
  return end === -1 ? "" : this.substring(from, end);
};
"""

    /** HTTP client (4.2). Body encoding and the response object are built by the host. */
    private const val CLIENT = """
class Client {
  constructor(reqcopyWith) {
    this.reqcopyWith = reqcopyWith ?? null;
  }
  async get(url, headers) { return this.__send("GET", url, headers, undefined); }
  async head(url, headers) { return this.__send("HEAD", url, headers, undefined); }
  async post(url, headers, body) { return this.__send("POST", url, headers, body); }
  async put(url, headers, body) { return this.__send("PUT", url, headers, body); }
  async delete(url, headers, body) { return this.__send("DELETE", url, headers, body); }
  async patch(url, headers, body) { return this.__send("PATCH", url, headers, body); }
  __send(method, url, headers, body) {
    return __kmk_async("http", method, this.reqcopyWith, String(url), headers ?? null, body === undefined ? null : body);
  }
}
"""

    /**
     * DOM (4.3). Elements are handles into a per-call table on the host; a lookup that finds nothing still gives an
     * `Element` (handle -1) whose strings are "" and whose lists are empty, so `selectFirst` never returns null.
     * The document is parsed once, on first use (Mangayomi re-parses per call; the results are the same).
     */
    private const val DOM = """
class Element {
  constructor(key) { this._key = key; }
  get text() { return __kmk_sync("dom_string", this._key, "text"); }
  get innerHtml() { return __kmk_sync("dom_string", this._key, "innerHtml"); }
  get outerHtml() { return __kmk_sync("dom_string", this._key, "outerHtml"); }
  get className() { return __kmk_sync("dom_string", this._key, "className"); }
  get localName() { return __kmk_sync("dom_string", this._key, "localName"); }
  get namespaceUri() { return __kmk_sync("dom_string", this._key, "namespaceUri"); }
  get getSrc() { return __kmk_sync("dom_string", this._key, "getSrc"); }
  get getImg() { return __kmk_sync("dom_string", this._key, "getImg"); }
  get getHref() { return __kmk_sync("dom_string", this._key, "getHref"); }
  get getDataSrc() { return __kmk_sync("dom_string", this._key, "getDataSrc"); }
  get previousElementSibling() { return new Element(__kmk_sync("dom_get", this._key, "previousElementSibling")); }
  get nextElementSibling() { return new Element(__kmk_sync("dom_get", this._key, "nextElementSibling")); }
  get children() { return __kmk_elements(__kmk_sync("dom_list", this._key, "children", "")); }
  attr(name) { return __kmk_sync("dom_attr", this._key, String(name)); }
  hasAttr(name) { return __kmk_sync("dom_has_attr", this._key, String(name)); }
  selectFirst(css) { return new Element(__kmk_sync("dom_select_first", this._key, String(css))); }
  select(css) { return __kmk_elements(__kmk_sync("dom_select", this._key, String(css))); }
  getElementsByTagName(name) { return __kmk_elements(__kmk_sync("dom_list", this._key, "tag", String(name))); }
  getElementsByClassName(name) { return __kmk_elements(__kmk_sync("dom_list", this._key, "class", String(name))); }
  xpath(xpath) { return __kmk_unsupported("XPath (Element.xpath)", "no Mangayomi JavaScript manga source uses it"); }
  xpathFirst(xpath) { return __kmk_unsupported("XPath (Element.xpathFirst)", "no Mangayomi JavaScript manga source uses it"); }
}

class Document {
  constructor(html) {
    this.html = html;
    this._key = null;
  }
  get _doc() {
    if (this._key === null) this._key = __kmk_sync("dom_parse", this.html == null ? "" : String(this.html));
    return this._key;
  }
  get body() { return this.getElement("body"); }
  get documentElement() { return this.getElement("documentElement"); }
  get head() { return this.getElement("head"); }
  get parent() { return this.getElement("parent"); }
  get text() { return this.getString("text"); }
  get outerHtml() { return this.getString("outerHtml"); }
  get children() { return this.getElementsListBy("children", ""); }
  getElement(type) { return new Element(__kmk_sync("dom_get", this._doc, String(type))); }
  getString(type) { return __kmk_sync("dom_string", this._doc, String(type)); }
  getElementsListBy(type, name) { return __kmk_elements(__kmk_sync("dom_list", this._doc, String(type), String(name ?? ""))); }
  selectFirst(css) { return new Element(__kmk_sync("dom_select_first", this._doc, String(css))); }
  select(css) { return __kmk_elements(__kmk_sync("dom_select", this._doc, String(css))); }
  getElementsByTagName(name) { return this.getElementsListBy("tag", name); }
  getElementsByClassName(name) { return this.getElementsListBy("class", name); }
  getElementById(id) { return new Element(__kmk_sync("dom_by_id", this._doc, String(id))); }
  attr(name) { return ""; }
  hasAttr(name) { return false; }
  xpath(xpath) { return __kmk_unsupported("XPath (Document.xpath)", "no Mangayomi JavaScript manga source uses it"); }
  xpathFirst(xpath) { return __kmk_unsupported("XPath (Document.xpathFirst)", "no Mangayomi JavaScript manga source uses it"); }
}
"""

    /** Utility functions (4.4) and the out-of-scope stubs (6). */
    private const val UTILS = """
globalThis.cryptoHandler = function (text, iv, key, encrypt) {
  return __kmk_sync("crypto_handler", text, iv, key, !!encrypt);
};
globalThis.encryptAESCryptoJS = function (plainText, passphrase) {
  return __kmk_sync("crypto_js_encrypt", plainText, passphrase);
};
globalThis.decryptAESCryptoJS = function (encrypted, passphrase) {
  return __kmk_sync("crypto_js_decrypt", encrypted, passphrase);
};
globalThis.decryptAESGCM = function (encrypted, keyHex, ivHex, tagHex = "") {
  return __kmk_sync("crypto_aes_gcm", encrypted, keyHex, ivHex, tagHex);
};
globalThis.deobfuscateJsPassword = function (input) {
  return __kmk_sync("deobfuscate_js_password", String(input));
};
globalThis.unpackJs = function (packed) {
  return __kmk_unsupported("unpackJs", "not implemented yet; no Mangayomi JavaScript manga source uses it");
};
globalThis.unpackJsAndCombine = function (script) {
  return __kmk_unsupported("unpackJsAndCombine", "not implemented yet; no Mangayomi JavaScript manga source uses it");
};
globalThis.parseDates = function (values, dateFormat, dateFormatLocale) {
  return __kmk_unsupported("parseDates", "not implemented yet; no Mangayomi JavaScript manga source uses it");
};
globalThis.evaluateJavascriptViaWebview = async function (url, headers, scripts, time = 30) {
  return __kmk_async("webview", String(url), headers ?? {}, scripts ?? [], Number(time) || 30);
};
for (const name of ["createHlsProxyUrl", "parseEpub", "parseEpubChapter"]) {
  globalThis[name] = async function () { return __kmk_unsupported_async(name, "only used by anime and novel sources"); };
}
for (const name of [
  "sibnetExtractor", "myTvExtractor", "okruExtractor", "voeExtractor", "vidBomExtractor", "streamlareExtractor",
  "sendVidExtractor", "yourUploadExtractor", "gogoCdnExtractor", "doodExtractor", "streamTapeExtractor",
  "mp4UploadExtractor", "streamWishExtractor", "filemoonExtractor", "quarkVideosExtractor", "ucVideosExtractor",
  "quarkFilesExtractor", "ucFilesExtractor",
]) {
  globalThis[name] = async function () { return __kmk_unsupported_async(name, "video extractors are only used by anime sources"); };
}
"""

    /** `SharedPreferences` (4.6): `get` materialises the declared default on first read. */
    private const val PREFERENCES = """
class SharedPreferences {
  get(key) {
    const stored = __kmk_sync("pref_get", String(key));
    if (stored.found) return stored.value;
    const provider = typeof extention !== "undefined" && extention ? extention : Object.create(DefaultExtension.prototype);
    return __kmk_sync("pref_default", String(key), provider.getSourcePreferences());
  }
  getString(key, defaultValue) {
    return __kmk_sync("pref_get_string", String(key), defaultValue == null ? null : String(defaultValue));
  }
  setString(key, value) {
    __kmk_sync("pref_set_string", String(key), value == null ? "" : String(value));
    return null;
  }
}
"""

    /** `MProvider` and `jsonStringify` (2.2 step 3, 3), plus the host's call entry point. */
    private const val PROVIDER = """
class MProvider {
  get source() { return __kmk_sync("source"); }
  get supportsLatest() { throw new Error("supportsLatest not implemented"); }
  getHeaders(url) { throw new Error("getHeaders not implemented"); }
  async getPopular(page) { throw new Error("getPopular not implemented"); }
  async getLatestUpdates(page) { throw new Error("getLatestUpdates not implemented"); }
  async search(query, page, filters) { throw new Error("search not implemented"); }
  async getDetail(url) { throw new Error("getDetail not implemented"); }
  async getPageList() { throw new Error("getPageList not implemented"); }
  getFilterList() { throw new Error("getFilterList not implemented"); }
  getSourcePreferences() { throw new Error("getSourcePreferences not implemented"); }
  async getVideoList(url) { throw new Error("getVideoList not implemented"); }
  async getHtmlContent(name, url) { throw new Error("getHtmlContent not implemented"); }
  async cleanHtmlContent(html) { throw new Error("cleanHtmlContent not implemented"); }
}
globalThis.jsonStringify = async function (fn) {
  return JSON.stringify(await fn());
};
globalThis.__kmk_invoke = function (member, args) {
  if (member === "supportsLatest") return extention.supportsLatest;
  return extention[member](...args);
};
"""

    const val FILE_NAME = "komikku-prelude.js"

    /** The provider creation appended to the extension text (2.2 step 4; `extention` is Mangayomi's spelling). */
    const val CREATE_PROVIDER = "\n;var extention = new DefaultExtension();\n"

    val text: String = CORE + CLIENT + DOM + UTILS + PREFERENCES + PROVIDER
}
