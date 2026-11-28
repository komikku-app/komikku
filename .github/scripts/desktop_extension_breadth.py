#!/usr/bin/env python3
"""KMK: summary of the link check over the whole Keiyoushi catalogue (docs/kmp-desktop/ATLAS_INSTRUCTIONS.md, ANSWER 010),
run by .github/workflows/desktop_extension_breadth.yml.

    desktop_extension_breadth.py <linkCheck report> <index commit> > summary.md

Reads the text report of `:desktop:extension-runtime:linkCheck` (one `== <apk>: ...` block per extension) and writes
Markdown: how many extensions resolve fully, which `android.*` (and other missing) classes the catalogue references
and how many extensions each blocks, the most common missing members, and the extensions that could not be checked.
"""
import re
import sys
from collections import Counter

ANDROID_PREFIXES = ("android.", "androidx.", "dalvik.", "com.google.android.")
LISTED = 60

HEADER = re.compile(r"^== (?P<name>.+?): (?:(?P<classes>\d+) classes checked|failed: (?P<error>.*))$")
SECTION = re.compile(r"^Missing (?P<kind>classes|methods|fields) \(\d+\):$")
ENTRY = re.compile(r"^  (?P<ref>\S+)  <- ")
UNTRANSLATED = re.compile(r"^\((?P<count>\d+) method\(s\) of (?P<name>.+?) could not be translated by dex2jar\)$")


def parse(lines):
    """Per extension: {'classes': set, 'members': set, 'error': str or None, 'untranslated': int}."""
    extensions = {}
    untranslated = {}
    current = None
    kind = None
    for line in lines:
        line = line.rstrip("\n")
        match = UNTRANSLATED.match(line)
        if match:
            untranslated[match["name"]] = int(match["count"])
            continue
        match = HEADER.match(line)
        if match:
            current = {"classes": set(), "members": set(), "error": match["error"], "untranslated": 0}
            extensions[match["name"]] = current
            kind = None
            continue
        match = SECTION.match(line)
        if match and current is not None:
            kind = match["kind"]
            continue
        match = ENTRY.match(line)
        if match and current is not None and kind:
            ref = match["ref"]
            if kind == "classes":
                current["classes"].add(ref)
            else:
                current["members"].add(ref)
                if kind == "methods" and "(" in ref:
                    # owner.name(descriptor): the owner is a class the extension needs as well
                    current["classes"].add(ref.split("(", 1)[0].rsplit(".", 1)[0])
    for name, count in untranslated.items():
        if name in extensions:
            extensions[name]["untranslated"] = count
    return extensions


def is_android(name):
    return name.startswith(ANDROID_PREFIXES)


def table(title, counter, total):
    rows = [f"### {title}", "", "| Extensions | Share | Reference |", "|---:|---:|---|"]
    for ref, count in counter.most_common(LISTED):
        rows.append(f"| {count} | {100.0 * count / total:.1f} % | `{ref}` |")
    if len(counter) > LISTED:
        rows.append(f"| | | … {len(counter) - LISTED} more in the report |")
    if not counter:
        rows.append("| 0 | | none |")
    return rows + [""]


def main():
    if len(sys.argv) != 3:
        sys.exit("usage: desktop_extension_breadth.py <linkCheck report> <index commit>")
    with open(sys.argv[1], encoding="utf-8") as report:
        extensions = parse(report)
    total = len(extensions)
    if total == 0:
        sys.exit("no extension in the report")
    failed = sorted(name for name, ext in extensions.items() if ext["error"] is not None)
    checked = {name: ext for name, ext in extensions.items() if ext["error"] is None}
    clean = [name for name, ext in checked.items() if not ext["classes"] and not ext["members"]]
    android_only = [
        name for name, ext in checked.items()
        if (ext["classes"] or ext["members"])
        and all(is_android(ref) for ref in ext["classes"])
        and all(is_android(ref) for ref in ext["members"])
    ]
    host_gaps = [
        name for name, ext in checked.items()
        if any(not is_android(ref) for ref in ext["classes"] | ext["members"])
    ]
    untranslated = sorted(name for name, ext in checked.items() if ext["untranslated"])

    android_classes = Counter()
    other_classes = Counter()
    members = Counter()
    for ext in checked.values():
        for ref in ext["classes"]:
            (android_classes if is_android(ref) else other_classes)[ref] += 1
        members.update(ext["members"])

    out = [
        "## Link check of the Keiyoushi catalogue",
        "",
        f"Index: `keiyoushi/extensions` repo branch at `{sys.argv[2]}`. Static check only (dex2jar conversion and",
        "reference resolution against the packaged app's jars and the runner's JDK); no extension code was run.",
        "\"Resolves\" means every class, method and field the extension references exists on the desktop, not that",
        "its sources work.",
        "",
        "| Extensions | Count |",
        "|---|---:|",
        f"| In the report | {total} |",
        f"| Every reference resolves | {len(clean)} |",
        f"| Missing only `android.*` / `androidx.*` / `dalvik.*` symbols | {len(android_only)} |",
        f"| Missing symbols of other libraries (host gaps) | {len(host_gaps)} |",
        f"| With methods dex2jar could not translate | {len(untranslated)} |",
        f"| Could not be checked | {len(failed)} |",
        "",
    ]
    out += table(
        "Android classes the desktop lacks, or lacks members of, by the number of extensions that reference them",
        android_classes,
        total,
    )
    out += table("Classes of other libraries the desktop lacks, or lacks members of (host gaps)", other_classes, total)
    out += table("Most common missing members", members, total)
    if failed:
        out += ["### Could not be checked", ""]
        out += [f"- `{name}`: {extensions[name]['error']}" for name in failed[:LISTED]]
        if len(failed) > LISTED:
            out.append(f"- … {len(failed) - LISTED} more in the report")
        out.append("")
    print("\n".join(out))


if __name__ == "__main__":
    main()
