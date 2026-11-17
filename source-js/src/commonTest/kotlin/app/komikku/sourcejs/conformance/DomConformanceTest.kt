package app.komikku.sourcejs.conformance

import app.komikku.sourcejs.TestHost
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test

/** Contract 4.3: `Document` / `Element` with Mangayomi's semantics. */
class DomConformanceTest {

    private val fixture = """
const HTML = `<!DOCTYPE html><html><head><title>T</title><script>var data = {"pages": [1, 2]};</script></head>
<body class="main page">
<div id="list">
<div class="item" data-id="1"><a href="/m/1?a=1&amp;b=2"><img data-src="https://i.test/1.jpg" src="https://i.test/blank.gif" alt="One"></a><span class="title"> One  Piece </span></div>
<div class="item" data-id="2"><a href="/m/2"><img src="https://i.test/2.jpg"></a><span class="title">Two</span></div>
<p class="note">Note <b>bold</b><!-- comment --></p>
</div>
<ul><li>a</li><li class="sel">b</li><li>c</li></ul>
<script>window.chapterImages = ["x.jpg"];</script>
</body></html>`;

async function conformance(provider) {
  const doc = new Document(HTML);
  const r = {};
  r.html = doc.html === HTML;

  // Document getters (body, documentElement, head, parent, text, outerHtml, children).
  r.body = [doc.body.localName, doc.body.className, doc.body.attr("class")];
  r.documentElement = doc.documentElement.localName;
  r.head = doc.head.selectFirst("title").text;
  r.parent = [doc.parent.text, doc.parent.localName, doc.parent.select("*").length];
  r.docText = [doc.text.includes("One  Piece"), doc.text.includes('"pages"'), doc.text.includes("comment")];
  r.docOuterHtml = doc.outerHtml.includes('<body class="main page">');
  r.docChildren = doc.children.map((e) => e.localName);
  r.docAttr = [doc.attr("class"), doc.hasAttr("class")];
  r.generic = [doc.getElement("head").localName, doc.getString("text") === doc.text, doc.getElementsListBy("tag", "li").length];

  // select / selectFirst, invalid selectors, never-null elements.
  const items = doc.select("div.item");
  r.items = items.map((e) => e.attr("data-id"));
  r.invalid = [doc.select("div.#post-body p").length, doc.selectFirst("div.#post-body").text];
  const missing = doc.selectFirst(".missing");
  r.missing = [missing !== null, missing.text, missing.attr("href"), missing.select("a").length, missing.selectFirst("a").text,
    missing.nextElementSibling.text, missing.children.length, missing.hasAttr("x"), missing.getSrc];

  // Element strings.
  const first = items[0];
  r.text = [first.selectFirst("span.title").text, doc.selectFirst("p.note").text];
  r.names = [first.className, first.localName, first.namespaceUri];
  r.regexGetters = [first.getSrc, first.getDataSrc, first.getHref, first.getImg, items[1].getSrc];
  r.attr = [first.selectFirst("a").attr("href"), first.selectFirst("img").attr("alt"), first.attr("nope")];
  r.hasAttr = [first.hasAttr("data-id"), first.hasAttr("href")];
  const sel = doc.selectFirst("li.sel");
  r.htmlStrings = [sel.innerHtml, sel.outerHtml, doc.selectFirst("p.note").innerHtml];

  // Navigation.
  r.siblings = [sel.previousElementSibling.text, sel.nextElementSibling.text, doc.selectFirst("li").previousElementSibling.text];
  r.children = doc.selectFirst("ul").children.map((e) => e.text);
  r.byTag = [doc.getElementsByTagName("li").length, doc.getElementById("list").getElementsByTagName("img").length];
  r.byClass = [doc.getElementsByClassName("item").length, doc.getElementById("list").getElementsByClassName("title").map((e) => e.text)];
  r.byId = [doc.getElementById("list").attr("id"), doc.getElementById("nope").text];
  r.elementSelect = [doc.getElementById("list").select("div").length, doc.getElementById("list").selectFirst("div").attr("data-id")];

  // Selector vocabulary used by sources: attributes, +, :contains (incl. script text), :has.
  r.attributeSelector = doc.select("img[data-src]").map((e) => e.attr("alt"));
  r.adjacent = [doc.select("div.item + div.item").length, doc.select("a + span").length];
  r.contains = [
    doc.select("span:contains(one  piece)").length,
    doc.selectFirst("script:contains(chapterImages)").text,
    doc.select("body:contains(chapterImages)").length,
    doc.select("li:contains('b')").map((e) => e.text),
  ];
  r.has = doc.select("div.item:has(img[data-src])").map((e) => e.attr("data-id"));
  return r;
}
"""

    @Test
    fun documentAndElementBehaveLikeMangayomi() = runTest {
        val r: JsonObject = TestHost().runConformance(fixture)
        assertJson("true", r["html"])
        assertJson("""["body", "main page", "main page"]""", r["body"])
        assertJson("\"html\"", r["documentElement"])
        assertJson("\"T\"", r["head"])
        assertJson("""["", "", 0]""", r["parent"])
        // textContent: unnormalised, script data included, comments excluded.
        assertJson("[true, true, false]", r["docText"])
        assertJson("true", r["docOuterHtml"])
        assertJson("""["html"]""", r["docChildren"])
        assertJson("""["", false]""", r["docAttr"])
        assertJson("""["head", true, 3]""", r["generic"])

        assertJson("""["1", "2"]""", r["items"])
        assertJson("""[0, ""]""", r["invalid"])
        assertJson("""[true, "", "", 0, "", "", 0, false, ""]""", r["missing"])

        assertJson("""[" One  Piece ", "Note bold"]""", r["text"])
        assertJson("""["item", "div", "http://www.w3.org/1999/xhtml"]""", r["names"])
        // Regex over the outer HTML: `src="` also matches inside data-src, values stay HTML-escaped.
        assertJson(
            """["https://i.test/1.jpg", "https://i.test/1.jpg", "/m/1?a=1&amp;b=2", "", "https://i.test/2.jpg"]""",
            r["regexGetters"],
        )
        assertJson("""["/m/1?a=1&b=2", "One", ""]""", r["attr"])
        assertJson("[true, false]", r["hasAttr"])
        assertJson("""["b", "<li class=\"sel\">b</li>", "Note <b>bold</b><!-- comment -->"]""", r["htmlStrings"])

        assertJson("""["a", "c", ""]""", r["siblings"])
        assertJson("""["a", "b", "c"]""", r["children"])
        assertJson("[3, 2]", r["byTag"])
        assertJson("""[2, [" One  Piece ", "Two"]]""", r["byClass"])
        assertJson("""["list", ""]""", r["byId"])
        assertJson("""[2, "1"]""", r["elementSelect"])

        assertJson("""["One"]""", r["attributeSelector"])
        assertJson("[1, 2]", r["adjacent"])
        assertJson("""[1, "window.chapterImages = [\"x.jpg\"];", 1, ["b"]]""", r["contains"])
        assertJson("""["1"]""", r["has"])
    }
}
