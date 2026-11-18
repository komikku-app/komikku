package app.komikku.sourcejs.conformance

import app.komikku.sourcejs.TestHost
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** Contract 4.4 / 4.5 / 7: crypto helpers, string helpers, `jsonStringify`, globals and out-of-scope stubs. */
class UtilsConformanceTest {

    private val cryptoFixture = """
const PLAIN = "Hello, 世界! cryptoHandler";
async function conformance(provider) {
  const r = {};
  r.cbcDecrypt = [
    cryptoHandler("NDIa3Weis9upl3OmVvd4jxumz5azW8tRnVCfL3J57mk=", "fedcba9876543210", "0123456789abcdef", false),
    cryptoHandler("jZSHXlX7DuwQ4+QAbTgOJcrGHKdLmSbOydgfNz1z05E=", "fedcba9876543210", "0123456789abcdef01234567", false),
    cryptoHandler("K6sBLWskRAQ7ynng+u3X15Kh2M23ewZMqo4MSQIpJug=", "fedcba9876543210", "0123456789abcdef0123456789abcdef", false),
  ];
  r.cbcEncrypt = cryptoHandler(PLAIN, "fedcba9876543210", "0123456789abcdef", true);
  // Any error returns the input unchanged.
  r.cbcErrors = [
    cryptoHandler("not base64!", "fedcba9876543210", "0123456789abcdef", false),
    cryptoHandler("NDIa3Weis9upl3OmVvd4jxumz5azW8tRnVCfL3J57mk=", "fedcba9876543210", "short key", false),
  ];
  r.cryptoJsDecrypt = decryptAESCryptoJS("U2FsdGVkX18BAgMEBQYHCNHZTXZHfy4taq/E4JeGSEo=", "my passphrase");
  const encrypted = encryptAESCryptoJS("round trip", "pw");
  r.cryptoJsRoundTrip = [encrypted.startsWith("U2FsdGVkX1"), decryptAESCryptoJS(encrypted, "pw")];
  try { decryptAESCryptoJS("bm90IHNhbHRlZA==", "pw"); r.cryptoJsError = "no error"; } catch (e) { r.cryptoJsError = "thrown"; }
  r.gcm = [
    decryptAESGCM("XSEDha9nwI673MuDQLqGbY07orMYG3eR4NVZDQ==", "000102030405060708090a0b0c0d0e0f", "6465666768696a6b6c6d6e6f"),
    decryptAESGCM("XSEDha9nwI673MuD", "000102030405060708090a0b0c0d0e0f", "6465666768696a6b6c6d6e6f", "40ba866d8d3ba2b3181b7791e0d5590d"),
    decryptAESGCM("XSEDha9nwI673MuD", "000102030405060708090a0b0c0d0e0f", "6465666768696a6b6c6d6e6f", "00ba866d8d3ba2b3181b7791e0d5590d"),
  ];
  r.deobfuscate = deobfuscateJsPassword("[!+[]+!+[]][+[]](+[])[!+[]+!+[]+!+[]][+!+[]+[]]");
  r.deobfuscateRules = ["[+[]]", "[+[]+[]]", "[]", "x[!+[]]y", "(a)[!+[]](b)c", "(a)x[!+[]]", "[" + "!+[]+".repeat(10) + "]"].map(deobfuscateJsPassword);
  r.deobfuscateErrors = ["[!+[]", "(+[])", "(a)[!+[]", "]["].map((input) => {
    try { return deobfuscateJsPassword(input); } catch (e) { return "thrown"; }
  });
  // Mangayomi's parsing details: no "Salted__" check, passphrase = low byte of each UTF-16 code unit, Dart base64.
  r.cryptoJsDetails = [
    decryptAESCryptoJS("R2FyYmFnZSEBAgMEBQYHCNHZTXZHfy4taq/E4JeGSEo=", "my passphrase"),
    decryptAESCryptoJS(encryptAESCryptoJS("low byte", "Ax"), "\u0141x"),
  ];
  r.base64 = [
    cryptoHandler("jZSHXlX7DuwQ4-QAbTgOJcrGHKdLmSbOydgfNz1z05E=", "fedcba9876543210", "0123456789abcdef01234567", false),
    cryptoHandler("jZSHXlX7DuwQ4+QAbTgOJcrGHKdLmSbOydgfNz1z05E", "fedcba9876543210", "0123456789abcdef01234567", false),
    cryptoHandler(" NDIa3Weis9upl3OmVvd4jxumz5azW8tRnVCfL3J57mk=", "fedcba9876543210", "0123456789abcdef", false),
  ];
  r.malformedUtf8 = [
    cryptoHandler("3C_U4LxjrBu5IwmhpI5AFQ==", "fedcba9876543210", "0123456789abcdef", false),
    decryptAESGCM("W6FhFlsCARCC1w0Y+v5gLH4H", "000102030405060708090A0B0C0D0E0F", "6465666768696a6b6c6d6e6f"),
  ];
  r.hex = [
    decryptAESGCM("XSEDha9nwI673MuD", "+00102030405060708090a0b0c0d0e0f", "6465666768696a6b6c6d6e6f", "40ba866d8d3ba2b3181b7791e0d5590d"),
    decryptAESGCM("XSEDha9nwI673MuD", "000102030405060708090a0b0c0d0e0f", "6465666768696a6b6c6d6e6", "40ba866d8d3ba2b3181b7791e0d5590d"),
  ];
  return r;
}
"""

    @Test
    fun cryptoHelpers() = runTest {
        val r = TestHost().runConformance(cryptoFixture)
        assertJson("""["Hello, 世界! cryptoHandler", "Hello, 世界! cryptoHandler", "Hello, 世界! cryptoHandler"]""", r["cbcDecrypt"])
        assertJson("\"NDIa3Weis9upl3OmVvd4jxumz5azW8tRnVCfL3J57mk=\"", r["cbcEncrypt"])
        assertJson("""["not base64!", "NDIa3Weis9upl3OmVvd4jxumz5azW8tRnVCfL3J57mk="]""", r["cbcErrors"])
        assertJson("\"secret message\"", r["cryptoJsDecrypt"])
        assertJson("""[true, "round trip"]""", r["cryptoJsRoundTrip"])
        assertJson("\"thrown\"", r["cryptoJsError"])
        assertJson("""["GCM text ✓", "GCM text ✓", "XSEDha9nwI673MuD"]""", r["gcm"])
        assertJson("\"20.1\"", r["deobfuscate"])
        assertJson("""["0", "-", "-", "1", "..", ".1", "-"]""", r["deobfuscateRules"])
        assertJson("""["thrown", "thrown", "thrown", "thrown"]""", r["deobfuscateErrors"])
        assertJson("""["secret message", "low byte"]""", r["cryptoJsDetails"])
        assertJson(
            """["Hello, 世界! cryptoHandler", "jZSHXlX7DuwQ4+QAbTgOJcrGHKdLmSbOydgfNz1z05E", " NDIa3Weis9upl3OmVvd4jxumz5azW8tRnVCfL3J57mk="]""",
            r["base64"],
        )
        assertJson("""["\uFFFDA", "A\uFFFD"]""", r["malformedUtf8"])
        assertJson("""["XSEDha9nwI673MuD", "XSEDha9nwI673MuD"]""", r["hex"])
    }

    private val helpersFixture = """
async function conformance(provider) {
  const r = {};
  const s = "a-b-c";
  r.after = [s.substringAfter("-"), s.substringAfter("+"), s.substringAfterLast("-"), s.substringAfterLast("+")];
  r.before = [s.substringBefore("-"), s.substringBefore("+"), s.substringBeforeLast("-"), s.substringBeforeLast("+")];
  r.between = ["x<y>z<w>".substringBetween("<", ">"), "x<y".substringBetween("<", ">"), "xy>".substringBetween("<", ">")];
  r.jsonStringify = await jsonStringify(async () => ({ a: [1, "b"] }));
  r.globals = ["MProvider", "Client", "Document", "Element", "SharedPreferences", "console", "setTimeout", "jsonStringify",
    "cryptoHandler", "parseDates", "unpackJs", "evaluateJavascriptViaWebview", "sibnetExtractor",
    "atob", "btoa", "fetch", "URL", "TextDecoder", "clearTimeout", "setInterval", "window", "document", "require",
    "__kmk_host_sync", "__kmk_host_async"]
    .map((name) => name + ":" + eval("typeof " + name));
  r.engine = [typeof BigInt, typeof Proxy, typeof WeakRef, new Date("2024-01-02").valueOf(), "x".padStart(3, "-"),
    /(?<y>\d{4})/.exec("in 2024").groups.y, null ?? "n", ({ a: { b: 1 } })?.a?.b];
  const errors = [];
  for (const call of [
    () => parseDates(["today"], "", ""),
    () => unpackJsAndCombine("eval(function(p,a,c,k,e,d){})"),
    () => sibnetExtractor("https://video.test"),
    () => createHlsProxyUrl("https://video.test/x.m3u8"),
    () => parseEpub("book", "https://book.test", {}),
  ]) {
    try { await call(); errors.push("no error"); } catch (e) { errors.push(e.message); }
  }
  r.unsupported = errors;
  return r;
}
"""

    @Test
    fun languageHelpersGlobalsAndStubs() = runTest {
        val r = TestHost().runConformance(helpersFixture)
        assertJson("""["b-c", "a-b-c", "c", "a-b-c"]""", r["after"])
        assertJson("""["a", "a-b-c", "a-b", "a-b-c"]""", r["before"])
        assertJson("""["y", "", ""]""", r["between"])
        assertJson("\"{\\\"a\\\":[1,\\\"b\\\"]}\"", r["jsonStringify"])
        assertJson(
            """[
              "MProvider:function", "Client:function", "Document:function", "Element:function", "SharedPreferences:function",
              "console:object", "setTimeout:function", "jsonStringify:function", "cryptoHandler:function",
              "parseDates:function", "unpackJs:function", "evaluateJavascriptViaWebview:function", "sibnetExtractor:function",
              "atob:undefined", "btoa:undefined", "fetch:undefined", "URL:undefined", "TextDecoder:undefined",
              "clearTimeout:function", "setInterval:undefined", "window:undefined", "document:undefined", "require:undefined",
              "__kmk_host_sync:undefined", "__kmk_host_async:undefined"
            ]""",
            r["globals"],
        )
        assertJson("""["function", "function", "function", 1704153600000, "--x", "2024", "n", 1]""", r["engine"])
        assertJson(
            """[
              "parseDates is not supported: not implemented yet; no Mangayomi JavaScript manga source uses it",
              "unpackJsAndCombine is not supported: not implemented yet; no Mangayomi JavaScript manga source uses it",
              "sibnetExtractor is not supported: video extractors are only used by anime sources",
              "createHlsProxyUrl is not supported: only used by anime and novel sources",
              "parseEpub is not supported: only used by anime and novel sources"
            ]""",
            r["unsupported"],
        )
    }
}
