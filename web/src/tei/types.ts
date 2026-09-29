// Mirrors the nested dict shape kodon_py.tei_parser.TEIParser builds:
// each element is its own XML attributes spread flat, plus tagname/
// index/urn/children. TEI attribute names (type, n, lang, id, ...)
// aren't known statically, hence the index signature.
export interface TeiTextRun {
  tagname: "text_run";
  content: string;
  // Present only outside paratextual elements (note/noteGrp/speaker) --
  // see parser.ts's characters().
  start?: number;
  end?: number;
}

export interface TeiElement {
  tagname: string;
  index: number;
  urn: string | null;
  children: Array<TeiElement | TeiTextRun>;
  [attribute: string]: unknown;
}

export type TeiNode = TeiElement | TeiTextRun;
