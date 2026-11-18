package app.komikku.sourcejs

/**
 * The JavaScript prelude evaluated before every extension: Komikku's own text exposing Mangayomi's names, signatures
 * and behaviour as documented in docs/kmp-desktop/MANGAYOMI_CONTRACT.md (section numbers below).
 *
 * Security boundary (PROGRESS.md, "Review fixes"): `quickjs-kt` turns a JS `Error` that reaches Kotlin into an
 * instance of the Java class named by the error's `name`. The prelude therefore keeps every JS value but strings
 * away from Kotlin:
 * - the two host bindings (`__kmk_host_sync` / `__kmk_host_async`, see `JsSession`) are captured in this closure and
 *   deleted from `globalThis`; every call passes a string checked with `typeof` right before the call;
 * - the call entry (`__kmk_entry`) loads the extension and calls the provider member inside `try` / `catch` and
 *   reports the outcome through the binding as a string envelope; it never throws and its promises are handled
 *   before they can reject; `Promise.prototype.constructor` / `then` and `Promise[Symbol.species]` are locked so
 *   the host's own awaits cannot be made to throw;
 * - `Object.prototype` is made non-extensible, so an extension cannot add the `then` / `value` accessors through which
 *   QuickJS's own completion object of an evaluation could still reject with an extension's error.
 *
 * Errors are classified by brands kept in a closure `WeakMap`, never by message text: "not implemented" (the
 * `MProvider` defaults), "unsupported" (stubs naming the feature) and "host" (a host failure the host recorded under
 * an id).
 *
 * Helper functions are installed as configurable `globalThis` properties, so an extension that declares its own
 * function or `const` with the same name still loads.
 */
internal object JsPrelude {

    /** Captured intrinsics, the host bindings, brands and the reply protocol of `JsSession`. */
    private const val CORE = """
"use strict";
const global = globalThis;
const hostSync = global.__kmk_host_sync;
const hostAsync = global.__kmk_host_async;
delete global.__kmk_host_sync;
delete global.__kmk_host_async;

const uncurry = Function.prototype.bind.bind(Function.prototype.call);
const JsonStringify = JSON.stringify;
const JsonParse = JSON.parse;
const StringCtor = String;
const NumberCtor = Number;
const ErrorCtor = Error;
const PromiseCtor = Promise;
const ObjectCreate = Object.create;
const ObjectDefineProperty = Object.defineProperty;
const ObjectGetPrototypeOf = Object.getPrototypeOf;
const ObjectPreventExtensions = Object.preventExtensions;
const ObjectPrototype = Object.prototype;
const InternalErrorPrototype = InternalError.prototype;
const PromisePrototype = Promise.prototype;
const promiseThen = uncurry(Promise.prototype.then);
const weakMapGet = uncurry(WeakMap.prototype.get);
const weakMapSet = uncurry(WeakMap.prototype.set);
const stringSlice = uncurry(String.prototype.slice);
const stringIndexOf = uncurry(String.prototype.indexOf);
const brands = new WeakMap();

// `await` reads `constructor` and resolving reads `then`: an extension must not be able to make the host's own
// awaits throw, which in QuickJS rejects the awaiting function's promise directly (bypassing its try / catch).
ObjectDefineProperty(PromisePrototype, "constructor", { writable: false, configurable: false });
ObjectDefineProperty(PromisePrototype, "then", { writable: false, configurable: false });
ObjectDefineProperty(Promise, Symbol.species, { configurable: false });

function defineGlobal(name, value) {
  ObjectDefineProperty(global, name, { value: value, writable: true, configurable: true, enumerable: false });
}

function brand(error, info) {
  weakMapSet(brands, error, info);
  return error;
}
function brandOf(value) {
  if (value === null || (typeof value !== "object" && typeof value !== "function")) return undefined;
  return weakMapGet(brands, value);
}
function unsupported(feature, reason) {
  return brand(new ErrorCtor(feature + " is not supported: " + reason), { kind: "unsupported", feature: feature, reason: reason });
}
function notImplemented(member) {
  return brand(new ErrorCtor(member + " not implemented"), { kind: "not_implemented" });
}
function isNotImplemented(error) {
  const info = brandOf(error);
  return info !== undefined && info.kind === "not_implemented";
}

// Host replies: "v" + JSON, "s" + raw string, "h" + response JSON + "\n" + raw body, "e" + {id, message} (host
// failure), "c" (dropped after the call settled).
const CANCELLED = {};
function decode(reply) {
  if (typeof reply !== "string" || reply.length === 0) return null;
  const tag = reply[0];
  const body = stringSlice(reply, 1);
  if (tag === "s") return body;
  if (tag === "v") return JsonParse(body);
  if (tag === "h") {
    const newline = stringIndexOf(body, "\n");
    const response = JsonParse(stringSlice(body, 0, newline));
    response.body = stringSlice(body, newline + 1);
    return response;
  }
  if (tag === "c") return CANCELLED;
  if (tag === "e") {
    const failure = JsonParse(body);
    throw brand(new ErrorCtor(StringCtor(failure.message)), { kind: "host", id: NumberCtor(failure.id) });
  }
  return null;
}
function json(value) {
  const out = JsonStringify(value);
  return typeof out === "string" ? out : "null";
}
function sync(channel, payload) {
  return decode(hostSync(channel, typeof payload === "string" ? payload : ""));
}
async function asyncCall(channel, payload) {
  const value = decode(await hostAsync(channel, typeof payload === "string" ? payload : ""));
  if (value === CANCELLED) return new PromiseCtor(function () {});
  return value;
}
function describeShort(error) {
  try {
    return StringCtor(error);
  } catch (e) {
    return "<unprintable error>";
  }
}
"""

    /** Console (4.5), timers (2.2), string helpers (4.5). */
    private const val LANGUAGE = """
// 4.5: one argument; objects are JSON-encoded. (Mangayomi throws for console.log(undefined); Komikku logs it.)
function log(level, message) {
  const line = message !== null && typeof message === "object" ? JsonStringify(message) : message;
  sync("log", json([level, typeof line === "string" ? line : StringCtor(line)]));
}
defineGlobal("console", {
  log(message) { log("info", message); },
  warn(message) { log("warn", message); },
  error(message) { log("error", message); },
});

// 2.2: setTimeout(fn, ms); no extra arguments or setInterval. Komikku adds clearTimeout. When the provider call
// settles the host ends pending timers without running them; a throwing callback is logged, never fails the call.
let timerSeq = 0;
defineGlobal("setTimeout", function (callback, ms) {
  const id = ++timerSeq;
  const delay = NumberCtor(ms) || 0;
  (async function () {
    try {
      const fired = await asyncCall("sleep", json([id, delay]));
      if (fired !== true || typeof callback !== "function") return;
      callback();
    } catch (error) {
      try {
        log("error", "setTimeout callback failed: " + describeShort(error));
      } catch (e) {}
    }
  })();
  return id;
});
defineGlobal("clearTimeout", function (id) {
  sync("clear_timer", json([NumberCtor(id) || 0]));
});

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
function send(method, options, url, headers, body) {
  return asyncCall("http", json([method, options, StringCtor(url), headers ?? null, body === undefined ? null : body]));
}
class Client {
  constructor(reqcopyWith) {
    this.reqcopyWith = reqcopyWith ?? null;
  }
  async get(url, headers) { return send("GET", this.reqcopyWith, url, headers, undefined); }
  async head(url, headers) { return send("HEAD", this.reqcopyWith, url, headers, undefined); }
  async post(url, headers, body) { return send("POST", this.reqcopyWith, url, headers, body); }
  async put(url, headers, body) { return send("PUT", this.reqcopyWith, url, headers, body); }
  async delete(url, headers, body) { return send("DELETE", this.reqcopyWith, url, headers, body); }
  async patch(url, headers, body) { return send("PATCH", this.reqcopyWith, url, headers, body); }
}
defineGlobal("Client", Client);
"""

    /**
     * DOM (4.3). Elements are handles into a per-call table on the host; a lookup that finds nothing still gives an
     * `Element` (handle -1) whose strings are "" and whose lists are empty, so `selectFirst` never returns null.
     * The document is parsed once, on first use (Mangayomi re-parses per call; the results are the same).
     */
    private const val DOM = """
function elements(keys) {
  const out = [];
  if (keys) for (let i = 0; i < keys.length; i++) out[i] = new Element(keys[i]);
  return out;
}
function domString(key, type) { return sync("dom_string", json([key, type])); }
function domElement(key, type) { return new Element(sync("dom_get", json([key, type]))); }
function domList(key, type, name) { return elements(sync("dom_list", json([key, type, name]))); }
function domSelectFirst(key, css) { return new Element(sync("dom_select_first", json([key, StringCtor(css)]))); }
function domSelect(key, css) { return elements(sync("dom_select", json([key, StringCtor(css)]))); }

class Element {
  constructor(key) { this._key = key; }
  get text() { return domString(this._key, "text"); }
  get innerHtml() { return domString(this._key, "innerHtml"); }
  get outerHtml() { return domString(this._key, "outerHtml"); }
  get className() { return domString(this._key, "className"); }
  get localName() { return domString(this._key, "localName"); }
  get namespaceUri() { return domString(this._key, "namespaceUri"); }
  get getSrc() { return domString(this._key, "getSrc"); }
  get getImg() { return domString(this._key, "getImg"); }
  get getHref() { return domString(this._key, "getHref"); }
  get getDataSrc() { return domString(this._key, "getDataSrc"); }
  get previousElementSibling() { return domElement(this._key, "previousElementSibling"); }
  get nextElementSibling() { return domElement(this._key, "nextElementSibling"); }
  get children() { return domList(this._key, "children", ""); }
  attr(name) { return sync("dom_attr", json([this._key, StringCtor(name)])); }
  hasAttr(name) { return sync("dom_has_attr", json([this._key, StringCtor(name)])); }
  selectFirst(css) { return domSelectFirst(this._key, css); }
  select(css) { return domSelect(this._key, css); }
  getElementsByTagName(name) { return domList(this._key, "tag", StringCtor(name)); }
  getElementsByClassName(name) { return domList(this._key, "class", StringCtor(name)); }
  xpath(xpath) { throw unsupported("XPath (Element.xpath)", "no Mangayomi JavaScript manga source uses it"); }
  xpathFirst(xpath) { throw unsupported("XPath (Element.xpathFirst)", "no Mangayomi JavaScript manga source uses it"); }
}

class Document {
  constructor(html) {
    this.html = html;
    this._key = null;
  }
  get _doc() {
    // The HTML travels raw, not inside JSON.
    if (this._key === null) this._key = sync("dom_parse", this.html == null ? "" : StringCtor(this.html));
    return this._key;
  }
  get body() { return this.getElement("body"); }
  get documentElement() { return this.getElement("documentElement"); }
  get head() { return this.getElement("head"); }
  get parent() { return this.getElement("parent"); }
  get text() { return this.getString("text"); }
  get outerHtml() { return this.getString("outerHtml"); }
  get children() { return this.getElementsListBy("children", ""); }
  getElement(type) { return domElement(this._doc, StringCtor(type)); }
  getString(type) { return domString(this._doc, StringCtor(type)); }
  getElementsListBy(type, name) { return domList(this._doc, StringCtor(type), StringCtor(name ?? "")); }
  selectFirst(css) { return domSelectFirst(this._doc, css); }
  select(css) { return domSelect(this._doc, css); }
  getElementsByTagName(name) { return this.getElementsListBy("tag", name); }
  getElementsByClassName(name) { return this.getElementsListBy("class", name); }
  getElementById(id) { return new Element(sync("dom_by_id", json([this._doc, StringCtor(id)]))); }
  attr(name) { return ""; }
  hasAttr(name) { return false; }
  xpath(xpath) { throw unsupported("XPath (Document.xpath)", "no Mangayomi JavaScript manga source uses it"); }
  xpathFirst(xpath) { throw unsupported("XPath (Document.xpathFirst)", "no Mangayomi JavaScript manga source uses it"); }
}
defineGlobal("Element", Element);
defineGlobal("Document", Document);
"""

    /** Utility functions (4.4) and the out-of-scope stubs (6). */
    private const val UTILS = """
defineGlobal("cryptoHandler", function (text, iv, key, encrypt) {
  return sync("crypto_handler", json([StringCtor(text), StringCtor(iv), StringCtor(key), !!encrypt]));
});
defineGlobal("encryptAESCryptoJS", function (plainText, passphrase) {
  return sync("crypto_js_encrypt", json([StringCtor(plainText), StringCtor(passphrase)]));
});
defineGlobal("decryptAESCryptoJS", function (encrypted, passphrase) {
  return sync("crypto_js_decrypt", json([StringCtor(encrypted), StringCtor(passphrase)]));
});
defineGlobal("decryptAESGCM", function (encrypted, keyHex, ivHex, tagHex = "") {
  return sync("crypto_aes_gcm", json([StringCtor(encrypted), StringCtor(keyHex), StringCtor(ivHex), StringCtor(tagHex ?? "")]));
});
defineGlobal("deobfuscateJsPassword", function (input) {
  return sync("deobfuscate_js_password", json([StringCtor(input)]));
});
defineGlobal("unpackJs", function (packed) {
  throw unsupported("unpackJs", "not implemented yet; no Mangayomi JavaScript manga source uses it");
});
defineGlobal("unpackJsAndCombine", function (script) {
  throw unsupported("unpackJsAndCombine", "not implemented yet; no Mangayomi JavaScript manga source uses it");
});
defineGlobal("parseDates", function (values, dateFormat, dateFormatLocale) {
  throw unsupported("parseDates", "not implemented yet; no Mangayomi JavaScript manga source uses it");
});
defineGlobal("evaluateJavascriptViaWebview", async function (url, headers, scripts, time = 30) {
  return asyncCall("webview", json([StringCtor(url), headers ?? {}, scripts ?? [], NumberCtor(time) || 30]));
});
for (const name of ["createHlsProxyUrl", "parseEpub", "parseEpubChapter"]) {
  defineGlobal(name, async function () { throw unsupported(name, "only used by anime and novel sources"); });
}
for (const name of [
  "sibnetExtractor", "myTvExtractor", "okruExtractor", "voeExtractor", "vidBomExtractor", "streamlareExtractor",
  "sendVidExtractor", "yourUploadExtractor", "gogoCdnExtractor", "doodExtractor", "streamTapeExtractor",
  "mp4UploadExtractor", "streamWishExtractor", "filemoonExtractor", "quarkVideosExtractor", "ucVideosExtractor",
  "quarkFilesExtractor", "ucFilesExtractor",
]) {
  defineGlobal(name, async function () { throw unsupported(name, "video extractors are only used by anime sources"); });
}
"""

    /** `SharedPreferences` (4.6): `get` materialises the declared default on first read. */
    private const val PREFERENCES = """
// The declared preferences, from the provider (or, while it is being constructed, an uninitialised instance).
function declaredPreferences() {
  const target = provider !== undefined ? provider : ProviderClass !== undefined ? ObjectCreate(ProviderClass.prototype) : undefined;
  if (target === undefined) return [];
  try {
    return target.getSourcePreferences();
  } catch (error) {
    if (isNotImplemented(error)) return [];
    throw error;
  }
}
class SharedPreferences {
  get(key) {
    const name = StringCtor(key);
    const stored = sync("pref_get", json([name]));
    if (stored.found) return stored.value;
    return sync("pref_default", json([name, declaredPreferences()]));
  }
  getString(key, defaultValue) {
    return sync("pref_get_string", json([StringCtor(key), defaultValue == null ? null : StringCtor(defaultValue)]));
  }
  setString(key, value) {
    sync("pref_set_string", json([StringCtor(key), value == null ? "" : StringCtor(value)]));
    return null;
  }
}
defineGlobal("SharedPreferences", SharedPreferences);
"""

    /** `MProvider` and `jsonStringify` (2.2 step 3, 3). */
    private const val PROVIDER = """
class MProvider {
  get source() { return JsonParse(sourceJson); }
  get supportsLatest() { throw notImplemented("supportsLatest"); }
  getHeaders(url) { throw notImplemented("getHeaders"); }
  async getPopular(page) { throw notImplemented("getPopular"); }
  async getLatestUpdates(page) { throw notImplemented("getLatestUpdates"); }
  async search(query, page, filters) { throw notImplemented("search"); }
  async getDetail(url) { throw notImplemented("getDetail"); }
  async getPageList() { throw notImplemented("getPageList"); }
  getFilterList() { throw notImplemented("getFilterList"); }
  getSourcePreferences() { throw notImplemented("getSourcePreferences"); }
  async getVideoList(url) { throw notImplemented("getVideoList"); }
  async getHtmlContent(name, url) { throw notImplemented("getHtmlContent"); }
  async cleanHtmlContent(html) { throw notImplemented("cleanHtmlContent"); }
}
defineGlobal("MProvider", MProvider);
defineGlobal("jsonStringify", async function (fn) {
  return JSON.stringify(await fn());
});
"""

    /**
     * Loading and calling (2.2 step 4, 2.4). The extension text is compiled inside a function (`wrap`) whose
     * definition is handed to `__kmk_define`; `__kmk_entry` runs it, creates the provider, calls the member and
     * reports one envelope through the `settle` channel: `"o" + JSON` (the result), `"e" + JSON` (the error),
     * `"x"` (the error could not even be described), or for `metadata` `"m" + JSON` (one such envelope per
     * member). Both globals are non-writable and non-configurable, and each runs once per context.
     */
    private const val ENTRY = """
let sourceJson = "null";
let ProviderClass = undefined;
let provider = undefined;
let defineExtension = null;
let entered = false;
let settled = false;

function settle(payload) {
  if (settled) return;
  settled = true;
  try {
    hostSync("settle", payload);
  } catch (error) {
    try {
      hostSync("settle", "x");
    } catch (e) {}
  }
}

function describeError(error, phase) {
  try {
    const out = ObjectCreate(null);
    out.phase = phase;
    out.kind = "script";
    const info = brandOf(error);
    if (info !== undefined) {
      out.kind = info.kind;
      if (info.kind === "unsupported") {
        out.feature = StringCtor(info.feature);
        out.reason = StringCtor(info.reason);
      } else if (info.kind === "host") {
        out.hostId = info.id;
      }
    } else if (error === null || error === undefined) {
      out.kind = "nullish";
    } else {
      try {
        if (ObjectGetPrototypeOf(error) === InternalErrorPrototype && error.message === "out of memory") out.kind = "oom";
      } catch (e) {}
    }
    out.message = describeShort(error);
    try {
      if (error !== null && typeof error === "object") {
        const stack = error.stack;
        if (typeof stack === "string") out.stack = stack;
      }
    } catch (e) {}
    return "e" + JsonStringify(out);
  } catch (e) {
    return "x";
  }
}

let phase = "load";
// A promise returned by a sync member is not awaited (Mangayomi stringifies it to {}, the host then uses the
// default); its rejection is ignored.
function ignoreRejection(value) {
  if (value !== null && typeof value === "object") {
    try {
      promiseThen(value, undefined, function () {});
    } catch (e) {}
  }
}
function outcomeOf(read) {
  try {
    const value = read();
    ignoreRejection(value);
    const out = JsonStringify(value);
    return "o" + (typeof out === "string" ? out : "");
  } catch (error) {
    return describeError(error, "call");
  }
}

async function run(member, argsJson, mode) {
  // Lets the entry attach its rejection handler before any extension code runs (see __kmk_entry).
  await undefined;
  try {
    try {
      const factory = defineExtension;
      defineExtension = null;
      if (factory === null) throw new ErrorCtor("The extension did not load");
      ProviderClass = factory();
      provider = new ProviderClass();
      defineGlobal("extention", provider);
      sync("loaded", "");
      phase = "call";
    } catch (error) {
      settle(describeError(error, "load"));
      return;
    }
    const args = JsonParse(argsJson);
    if (mode === "async") {
      const value = await provider[member](...args);
      const out = JsonStringify(value);
      settle("o" + (typeof out === "string" ? out : ""));
    } else if (mode === "metadata") {
      // All sync members in one context, each with its own outcome.
      const out = ObjectCreate(null);
      out.supportsLatest = outcomeOf(function () { return provider.supportsLatest; });
      out.getFilterList = outcomeOf(function () { return provider.getFilterList(); });
      out.getSourcePreferences = outcomeOf(function () { return provider.getSourcePreferences(); });
      out.getHeaders = outcomeOf(function () { return provider.getHeaders(args[0]); });
      settle("m" + JsonStringify(out));
    } else if (mode === "getter") {
      settle(outcomeOf(function () { return provider[member]; }));
    } else {
      settle(outcomeOf(function () { return provider[member](...args); }));
    }
  } catch (error) {
    try {
      settle(describeError(error, "call"));
    } catch (e) {}
  }
}

ObjectDefineProperty(global, "__kmk_define", {
  value: function (factory) {
    if (defineExtension === null && typeof factory === "function") defineExtension = factory;
  },
  writable: false,
  configurable: false,
  enumerable: false,
});
ObjectDefineProperty(global, "__kmk_entry", {
  value: function (member, argsJson, mode, source) {
    try {
      if (entered) return;
      entered = true;
      sourceJson = StringCtor(source);
      const pending = run(StringCtor(member), StringCtor(argsJson), StringCtor(mode));
      // Only reached when an await in run failed on an extension's value (QuickJS rejects run's promise directly).
      promiseThen(pending, undefined, function (error) { settle(describeError(error, phase)); });
    } catch (e) {}
  },
  writable: false,
  configurable: false,
  enumerable: false,
});

// Keeps `then` / `value` accessors off every plain object (see the class comment).
ObjectPreventExtensions(ObjectPrototype);
"""

    const val FILE_NAME = "komikku-prelude.js"

    val text: String = "(function () {\n$CORE$LANGUAGE$CLIENT$DOM$UTILS$PREFERENCES$PROVIDER$ENTRY})();\n"

    /**
     * The extension text as the body of the function handed to `__kmk_define`: its top-level declarations stay
     * visible to each other, a top-level throw happens inside the entry's `try`, and the line numbers are those of
     * the file. The host compiles [code] alone first, so a syntax error is reported as such and the text cannot close
     * the function early.
     */
    fun wrapExtension(code: String): String = "__kmk_define(function () {$code\n;return DefaultExtension;\n});\n"

    /**
     * The call script: `member` with the JSON array [argsJson], `mode` one of `async`, `sync`, `getter`,
     * `metadata`; [sourceJson] is the object `this.source` returns (Mangayomi builds it from a JSON literal too).
     */
    fun entryCall(member: String, argsJson: String, mode: String, sourceJson: String): String =
        "__kmk_entry(${jsString(member)}, ${jsString(argsJson)}, ${jsString(mode)}, ${jsString(sourceJson)});\n"

    private fun jsString(value: String): String = buildString {
        append('"')
        value.forEach { c ->
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c < ' ' || c == ' ' || c == ' ' -> append("\\u").append(c.code.toString(16).padStart(4, '0'))
                else -> append(c)
            }
        }
        append('"')
    }
}
