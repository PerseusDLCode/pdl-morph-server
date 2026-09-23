import type { TeiElement, TeiNode, TeiTextRun } from "./types.js";

// Port of kodon_py.tei_parser.TEIParser. The Python version is a SAX
// ContentHandler driven by lxml.sax.saxify, which replays a tree as a
// standard depth-first sequence of startElementNS/characters/endElementNS
// events; a plain recursive walk over the DOM produces that exact same
// event order, so there's no need to simulate SAX here.
//
// Not ported: the Python class's `_pending_speaker` field. It's written
// on entering/leaving a <speaker> element but never read anywhere (not
// exported, not used to influence `elements` or `primary_text`), so it
// has no observable effect on this function's output.
const PARATEXTUAL_ELEMENTS = new Set(["note", "noteGrp", "speaker"]);

// unicodedata.category(c)[0] in ("P", "S") -- punctuation or symbol,
// which JS's \p{P}/\p{S} Unicode property escapes match directly.
const PUNCTUATION_OR_SYMBOL = /^[\p{P}\p{S}]/u;

function isSpace(ch: string | undefined): boolean {
  return ch !== undefined && /^\s$/u.test(ch);
}

function getAttrs(element: Element): Record<string, string> {
  const attrs: Record<string, string> = {};
  for (const attr of Array.from(element.attributes)) {
    // lxml.sax (namespace-aware SAX) never exposes xmlns declarations as
    // regular attributes; the DOM's raw .attributes list does, so these
    // need to be filtered out to match remove_ns_from_attrs' input.
    if (attr.name === "xmlns" || attr.name.startsWith("xmlns:")) {
      continue;
    }
    attrs[attr.localName ?? attr.name] = attr.value;
  }
  return attrs;
}

// Assumes the input text stays within the Unicode Basic Multilingual
// Plane (true for Greek/Latin classical text, which is all this project
// handles) -- UTF-16 code unit indexing/length then matches Python's
// code-point semantics exactly, without needing code-point-aware
// iteration.
export function parseTeiElement(root: Element, baseUrn: string): TeiElement[] {
  let globalElementIndex = 0;
  let paratextDepth = 0;
  let primaryText = "";
  let primaryTextOffset = 0;
  const citableStack: Record<string, string>[] = [];
  let currentUrn: string | null = null;
  const elements: TeiElement[] = [];

  // The most recently pushed text_run anywhere in the tree (regardless of
  // nesting), so a needed space can be appended to its *tail* instead of
  // inserted as a new leading child ahead of whatever comes next. That
  // distinction matters: the next content is often nested inside an
  // element several levels deeper than where the gap conceptually is
  // (e.g. a bare "," at the <sense> level followed by <cit><quote>text),
  // and several of this app's wrapper components (e.g. Quote.svelte) are
  // `display: inline-block` `<div>`s -- which, per CSS whitespace
  // collapsing, silently strip a leading space that's the very first
  // content of their own inline formatting context. A trailing space
  // appended to the previous run never lands in that position.
  let lastTextRun: TeiTextRun | null = null;

  // Extends lastTextRun's content with a single space (or, if there's no
  // previous run to extend -- e.g. leading whitespace at the very start
  // of the document -- falls back to pushing a new node into
  // parentChildren). `offsetBumped` must be true exactly when the caller
  // just advanced primaryTextOffset for this same space, so lastTextRun.end
  // (when it has one) is only resynced alongside a matching offset move --
  // otherwise content.length would outrun end-start (e.g. a whitespace-only
  // text node encountered at paratextDepth > 0, which never touches
  // primaryTextOffset, extending a lastTextRun pushed earlier at depth 0).
  function appendSpace(parentChildren: TeiNode[], offsetBumped: boolean): void {
    if (lastTextRun !== null) {
      lastTextRun.content += " ";
      if (offsetBumped && lastTextRun.end !== undefined) {
        lastTextRun.end = primaryTextOffset;
      }
    } else {
      parentChildren.push({ tagname: "text_run", content: " " });
    }
  }

  function characters(content: string, parentChildren: TeiNode[]): void {
    if (content.trim() === "") {
      // Whitespace-only text nodes don't affect primaryText (its own
      // auto-space heuristic below already fills any gap they'd leave),
      // but they still need to survive as a child here: renderers that
      // walk `children` directly (e.g. the Svelte definition renderer)
      // just concatenate each text_run's content with no separator of
      // their own, so dropping this node entirely would glue adjacent
      // sibling elements together with no space between them.
      if (content.length > 0) {
        // Feed this into the same last-char tracking the synthetic-space
        // heuristic below reads, so a real (but whitespace-only) text
        // node here stops that heuristic from *also* inserting its own
        // space for the next real text run -- otherwise the two would
        // stack into a double space.
        const offsetBumped =
          paratextDepth === 0 && primaryText.length > 0 && !isSpace(primaryText[primaryText.length - 1]);
        if (offsetBumped) {
          primaryText += " ";
          primaryTextOffset += 1;
        }
        appendSpace(parentChildren, offsetBumped);
      }
      return;
    }

    const normalizedContent = content.replace(/\s+/gu, " ");
    const textRun: TeiTextRun = { tagname: "text_run", content: normalizedContent };

    if (paratextDepth === 0) {
      if (
        primaryText.length > 0 &&
        !isSpace(primaryText[primaryText.length - 1]) &&
        !isSpace(normalizedContent[0]) &&
        !PUNCTUATION_OR_SYMBOL.test(normalizedContent[0] as string)
      ) {
        // The source has no whitespace at all here (e.g. `<quote>foo</quote><bibl>bar</bibl>`),
        // but primaryText still needs a separating space -- and so does the
        // `children` tree.
        primaryText += " ";
        primaryTextOffset += 1;
        appendSpace(parentChildren, true);
      }
      const start = primaryTextOffset;
      primaryText += normalizedContent;
      primaryTextOffset += normalizedContent.length;
      textRun.start = start;
      textRun.end = primaryTextOffset;
    }

    parentChildren.push(textRun);
    lastTextRun = textRun;
  }

  function walk(node: Element, parentChildren: TeiNode[] | null): void {
    const localname = node.localName ?? node.tagName;
    const attrs = getAttrs(node);
    const elementIndex = globalElementIndex;
    globalElementIndex += 1;

    if (attrs.type !== undefined && attrs.n !== undefined) {
      citableStack.push(attrs);
      const location = citableStack.filter((c) => c.n).map((c) => c.n);
      currentUrn = `${baseUrn}:${location.join(".")}`;
    }

    const el: TeiElement = {
      ...attrs,
      children: [],
      index: elementIndex,
      tagname: localname,
      urn: currentUrn,
    };

    if (parentChildren !== null) {
      parentChildren.push(el);
    }

    if (PARATEXTUAL_ELEMENTS.has(localname)) {
      paratextDepth += 1;
    }

    for (const child of Array.from(node.childNodes)) {
      if (child.nodeType === child.TEXT_NODE || child.nodeType === child.CDATA_SECTION_NODE) {
        characters(child.textContent ?? "", el.children);
      } else if (child.nodeType === child.ELEMENT_NODE) {
        walk(child as Element, el.children);
      }
    }

    if (PARATEXTUAL_ELEMENTS.has(localname)) {
      paratextDepth -= 1;
    }
    // Note: currentUrn is deliberately NOT restored to the parent's urn
    // here -- this mirrors the Python original exactly (endElementNS
    // pops citable_stack but never recomputes current_urn), so sibling
    // elements after a citable element closes keep inheriting its urn
    // until the next citable element opens.
    if (attrs.type !== undefined && attrs.n !== undefined) {
      citableStack.pop();
    }

    const alreadyAChild =
      parentChildren !== null && parentChildren.some((c) => "index" in c && c.index === el.index);
    if (!alreadyAChild) {
      elements.push(el);
    }
  }

  walk(root, null);
  return elements;
}
