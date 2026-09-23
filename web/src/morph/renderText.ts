import { parseTeiText } from "../tei/parseTeiText.js";
import type { MorphResponse } from "./types.js";

// Mirrors main.py's morph() HTML route: /api/morph (buildMorphResponse)
// returns senses/entries/short_definition as raw TEI-XML strings; this
// additionally runs each through _parse_tei_text, matching what the
// server-rendered /morph page does before handing them to Jinja's
// ReadableTextContainer templates (Phase 6's concern on this side).
// Takes an already-built MorphResponse rather than duplicating
// buildMorphResponse's whole query/scoring pipeline, since parsing text
// is an orthogonal, purely-synchronous post-processing step.
export function parseMorphResponseText(response: MorphResponse, baseUrn = ""): MorphResponse {
  return {
    ...response,
    lemmas: response.lemmas.map((lemma) => ({
      ...lemma,
      senses: lemma.senses.map((sense) => ({
        ...sense,
        definition:
          typeof sense.definition === "string"
            ? parseTeiText(sense.definition, baseUrn)
            : sense.definition,
      })),
      entries: lemma.entries.map((entry) => ({
        ...entry,
        text: typeof entry.text === "string" ? parseTeiText(entry.text, baseUrn) : entry.text,
      })),
      short_definition:
        typeof lemma.short_definition === "string"
          ? parseTeiText(lemma.short_definition, baseUrn)
          : lemma.short_definition,
    })),
  };
}
