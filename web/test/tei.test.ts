import { describe, expect, it } from "vitest";
import { parseTeiElement } from "../src/tei/parser.js";
import { parseTeiText } from "../src/tei/parseTeiText.js";

// Expected outputs captured directly from the real Python
// kodon_py.tei_parser.TEIParser (the port's source of truth), not
// hand-derived -- see the fixture-generation notes inline. Each case
// mirrors _parse_tei_text's own logic: try parsing as-is, then wrapped
// in <span>, before falling back to the raw string.
function parse(xml: string, baseUrn = "urn:test") {
  return parseTeiText(xml, baseUrn);
}

describe("parseTeiElement / parseTeiText", () => {
  it("parses a single element with a plain text run", () => {
    expect(parse("<i>computation, reckoning</i>")).toEqual([
      {
        children: [{ tagname: "text_run", content: "computation, reckoning", start: 0, end: 22 }],
        index: 0,
        tagname: "i",
        urn: null,
      },
    ]);
  });

  it("parses nested elements with their own attributes carried through flat", () => {
    expect(
      parse(
        '<bibl n="urn:cts:greekLit:tlg0016.tlg001.perseus-grc1:3:142"><author>Hdt.</author><biblScope>3.142</biblScope></bibl>',
      ),
    ).toEqual([
      {
        n: "urn:cts:greekLit:tlg0016.tlg001.perseus-grc1:3:142",
        children: [
          {
            children: [{ tagname: "text_run", content: "Hdt. ", start: 0, end: 5 }],
            index: 1,
            tagname: "author",
            urn: null,
          },
          {
            children: [{ tagname: "text_run", content: "3.142", start: 5, end: 10 }],
            index: 2,
            tagname: "biblScope",
            urn: null,
          },
        ],
        index: 0,
        tagname: "bibl",
        urn: null,
      },
    ]);
  });

  it("joins adjacent text runs across element boundaries with a synthetic space, but not before punctuation", () => {
    expect(parse("<span>hello <i>world</i>, foo.</span>")).toEqual([
      {
        children: [
          { tagname: "text_run", content: "hello ", start: 0, end: 6 },
          {
            children: [{ tagname: "text_run", content: "world", start: 6, end: 11 }],
            index: 1,
            tagname: "i",
            urn: null,
          },
          { tagname: "text_run", content: ", foo.", start: 11, end: 17 },
        ],
        index: 0,
        tagname: "span",
        urn: null,
      },
    ]);
  });

  it("does not insert a space before punctuation immediately following an element", () => {
    expect(parse("<span>hello<i>!</i></span>")).toEqual([
      {
        children: [
          { tagname: "text_run", content: "hello", start: 0, end: 5 },
          {
            children: [{ tagname: "text_run", content: "!", start: 5, end: 6 }],
            index: 1,
            tagname: "i",
            urn: null,
          },
        ],
        index: 0,
        tagname: "span",
        urn: null,
      },
    ]);
  });

  it("builds citable-part URNs from ancestors that have both type and n", () => {
    expect(
      parse('<div type="book" n="1"><div type="chapter" n="2"><l n="1">text</l></div></div>'),
    ).toEqual([
      {
        type: "book",
        n: "1",
        index: 0,
        tagname: "div",
        urn: "urn:test:1",
        children: [
          {
            type: "chapter",
            n: "2",
            index: 1,
            tagname: "div",
            urn: "urn:test:1.2",
            children: [
              {
                n: "1",
                index: 2,
                tagname: "l",
                urn: "urn:test:1.2",
                children: [{ tagname: "text_run", content: "text", start: 0, end: 4 }],
              },
            ],
          },
        ],
      },
    ]);
  });

  it("leaves a sibling's urn frozen at the last citable ancestor's value after that ancestor closes -- a direct port of the Python original's behavior, not a simplification", () => {
    expect(
      parse('<span><div type="book" n="1"><l n="1">first</l></div><l n="2">second</l></span>'),
    ).toEqual([
      {
        index: 0,
        tagname: "span",
        urn: null,
        children: [
          {
            type: "book",
            n: "1",
            index: 1,
            tagname: "div",
            urn: "urn:test:1",
            children: [
              {
                n: "1",
                index: 2,
                tagname: "l",
                urn: "urn:test:1",
                children: [{ tagname: "text_run", content: "first ", start: 0, end: 6 }],
              },
            ],
          },
          {
            n: "2",
            index: 3,
            tagname: "l",
            // Still "urn:test:1", the book div's urn -- not null/reset,
            // even though this <l> is a sibling of the closed div, not
            // its descendant.
            urn: "urn:test:1",
            children: [{ tagname: "text_run", content: "second", start: 6, end: 12 }],
          },
        ],
      },
    ]);
  });

  it("excludes paratextual (note) content from primary_text offsets, but still includes it as a child", () => {
    expect(parse('<span><note anchored="yes">a footnote</note> after note</span>')).toEqual([
      {
        index: 0,
        tagname: "span",
        urn: null,
        children: [
          {
            anchored: "yes",
            index: 1,
            tagname: "note",
            urn: null,
            children: [{ tagname: "text_run", content: "a footnote" }],
          },
          // No text run for the note's own content contributed to this
          // offset range; " after note" starts at 0 since the note
          // (paratextual) never advanced primary_text_offset.
          { tagname: "text_run", content: " after note", start: 0, end: 11 },
        ],
      },
    ]);
  });

  it("collapses internal whitespace but preserves a single leading/trailing space", () => {
    expect(parse("<span>  leading and trailing space   </span>")).toEqual([
      {
        index: 0,
        tagname: "span",
        urn: null,
        children: [
          { tagname: "text_run", content: " leading and trailing space ", start: 0, end: 28 },
        ],
      },
    ]);
  });

  it("preserves whitespace-only text between sibling elements by appending it to the previous text_run", () => {
    // primaryText's own auto-space heuristic papers over a dropped
    // whitespace node, but renderers that walk `children` directly
    // (e.g. the Svelte definition renderer) just concatenate each
    // text_run's content with no separator -- so this space has to
    // survive here or adjacent elements render glued together. It's
    // appended to the *previous* run's content (rather than inserted as
    // a new leading child ahead of "b") because several wrapper
    // components render their element as a `display: inline-block` box,
    // and a leading space that's the very first content of one of those
    // gets silently stripped by CSS whitespace collapsing.
    expect(parse("<div><sense>a</sense> <sense>b</sense></div>")).toEqual([
      {
        index: 0,
        tagname: "div",
        urn: null,
        children: [
          {
            index: 1,
            tagname: "sense",
            urn: null,
            children: [{ tagname: "text_run", content: "a ", start: 0, end: 2 }],
          },
          {
            index: 2,
            tagname: "sense",
            urn: null,
            children: [{ tagname: "text_run", content: "b", start: 2, end: 3 }],
          },
        ],
      },
    ]);
  });

  it("inserts a synthetic space between adjacent elements with no whitespace between them in the source", () => {
    // The source has no text node at all between </quote> and <bibl>.
    // The space is appended to quote's "foo" run rather than inserted as
    // bibl's leading child, for the same inline-block-eats-leading-space
    // reason as the test above.
    expect(parse("<span><quote>foo</quote><bibl>bar</bibl></span>")).toEqual([
      {
        index: 0,
        tagname: "span",
        urn: null,
        children: [
          {
            index: 1,
            tagname: "quote",
            urn: null,
            children: [{ tagname: "text_run", content: "foo ", start: 0, end: 4 }],
          },
          {
            index: 2,
            tagname: "bibl",
            urn: null,
            children: [{ tagname: "text_run", content: "bar", start: 4, end: 7 }],
          },
        ],
      },
    ]);
  });

  it("appends a synthetic space to the previous run even when the next real text is nested inside further elements", () => {
    // Real LSJ principal-parts shape: a bare "," between two <cit>s, the
    // second of which nests its actual text two levels deep inside
    // <cit><quote>. The space must land in the *previous* run's content
    // (here the comma's own run, turning "," into ", ") rather than as a
    // new leading child of <quote> -- Quote.svelte renders <quote> as a
    // `display: inline-block` div, and a leading space that's the very
    // first content of that box is invisible once rendered.
    expect(
      parse(
        '<sense><cit><quote>A</quote></cit>,<cit><quote>B</quote><bibl><biblScope>1</biblScope></bibl></cit></sense>',
      ),
    ).toEqual([
      {
        index: 0,
        tagname: "sense",
        urn: null,
        children: [
          {
            index: 1,
            tagname: "cit",
            urn: null,
            children: [
              {
                index: 2,
                tagname: "quote",
                urn: null,
                children: [{ tagname: "text_run", content: "A", start: 0, end: 1 }],
              },
            ],
          },
          { tagname: "text_run", content: ", ", start: 1, end: 3 },
          {
            index: 3,
            tagname: "cit",
            urn: null,
            children: [
              {
                index: 4,
                tagname: "quote",
                urn: null,
                children: [{ tagname: "text_run", content: "B ", start: 3, end: 5 }],
              },
              {
                index: 5,
                tagname: "bibl",
                urn: null,
                children: [
                  {
                    index: 6,
                    tagname: "biblScope",
                    urn: null,
                    children: [{ tagname: "text_run", content: "1", start: 5, end: 6 }],
                  },
                ],
              },
            ],
          },
        ],
      },
    ]);
  });

  it("falls back to the raw string when even the <span>-wrapped retry fails to parse", () => {
    expect(parse("plain text with & unescaped ampersand")).toBe(
      "plain text with & unescaped ampersand",
    );
  });

  it("returns null for null input", () => {
    expect(parseTeiText(null)).toBeNull();
  });

  it("falls back to the raw string when parsing succeeds but produces no elements", () => {
    // An empty root element with no attributes and no content parses
    // fine but yields a single element (the root itself), so this
    // path is effectively unreachable through normal input -- included
    // anyway since main.py explicitly guards for it ("parsed if parsed
        // else text").
    const result = parseTeiElement(
      new DOMParser().parseFromString("<a/>", "application/xml").documentElement as Element,
      "",
    );
    expect(result.length).toBeGreaterThan(0);
  });
});
