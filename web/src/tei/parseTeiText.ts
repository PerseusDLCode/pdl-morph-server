import { parseTeiElement } from "./parser.js";
import type { TeiElement } from "./types.js";

function tryParseXml(xmlText: string): Element | null {
  const doc = new DOMParser().parseFromString(xmlText, "application/xml");
  // Both Chromium and Firefox report malformed XML by embedding a
  // <parsererror> element in the resulting document rather than
  // throwing (unlike lxml.etree.fromstring, which raises
  // XMLSyntaxError) -- this is the standard cross-browser way to detect
  // that. It's a different failure signal than the Python original, but
  // the two-step fallback chain it drives is the same.
  if (doc.getElementsByTagName("parsererror").length > 0) {
    return null;
  }
  return doc.documentElement;
}

// Port of main.py's _parse_tei_text: parses `text` as XML (retrying
// wrapped in <span>...</span> if the bare string doesn't parse, since
// TEI snippets like sense/entry definitions are often not
// single-rooted), then runs it through the TEI element-tree parser --
// falling back to the raw string whenever parsing fails twice, or the
// parse produced no elements at all.
export function parseTeiText(
  text: string | null,
  baseUrn = "",
): string | TeiElement[] | null {
  if (text === null) {
    return null;
  }

  const root = tryParseXml(text) ?? tryParseXml(`<span>${text}</span>`);
  if (root === null) {
    return text;
  }

  const parsed = parseTeiElement(root, baseUrn);
  return parsed.length > 0 ? parsed : text;
}
