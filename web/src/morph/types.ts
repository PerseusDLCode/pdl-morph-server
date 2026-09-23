import type { TeiElement } from "../tei/types.js";

// Mirrors src/new_morpheus/models.py's SQLModel table shapes (the columns
// actually read by the query layer -- not a full schema mirror).

export interface Lemma {
  id: number;
  headword: string;
  bare_headword: string | null;
  sequence_number: number;
  language_code: string;
}

export interface Parse {
  id: number;
  lemma_id: number;
  form: string;
  form_normalized: string | null;
  expanded_form: string | null;
  bare_form: string | null;
  part_of_speech: string | null;
  person: string | null;
  number: string | null;
  tense: string | null;
  mood: string | null;
  voice: string | null;
  gender: string | null;
  grammatical_case: string | null;
  degree: string | null;
  dialect: string | null;
  other: string | null;
  prefix: string | null;
  object: string | null;
  definite: string | null;
  possessive: string | null;
  dedup_key: string;
}

export interface Sense {
  id: number;
  entry_id: string;
  sense_id: string;
  document_id: string;
  lemma: string;
  sense: string | null;
  level: number | null;
  definition: string | null;
}

export interface Entry {
  document_id: string;
  key: string;
  text: string | null;
}

export interface DocumentFrequency {
  document_id: string;
  lemma_id: number;
  weighted_frequency: number;
}

// Mirrors src/new_morpheus/morph.py's `LemmaKey = tuple[str, int]`.
export type LemmaKey = readonly [headword: string, sequenceNumber: number];

// Mirrors src/new_morpheus/schemas.py's response shapes, minus the
// TEI-parsed `definition`/`text`/`short_definition` fields (Phase 4's
// concern) -- here they stay as the raw string (or null) straight off the
// row, matching what /api/morph (the JSON endpoint, which skips
// _parse_tei_text) returns.

export interface ParseOut {
  form: string;
  expanded_form: string | null;
  part_of_speech: string | null;
  person: string | null;
  number: string | null;
  tense: string | null;
  mood: string | null;
  voice: string | null;
  gender: string | null;
  grammatical_case: string | null;
  degree: string | null;
  dialect: string | null;
  other: string | null;
  prefix: string | null;
  object: string | null;
  definite: string | null;
  possessive: string | null;
  is_winner: boolean;
}

// definition/text/short_definition are raw strings from /api/morph (the
// JSON endpoint, which skips TEI parsing, matching schemas.py's `Any`
// field and main.py's api_morph), or a parsed TeiElement[]/string when
// run through parseMorphResponseText (the /morph HTML endpoint's
// _parse_tei_text pass -- see morph/renderText.ts).
export interface SenseOut {
  document_id: string;
  sense: string | null;
  level: number | null;
  definition: string | TeiElement[] | null;
}

export interface EntryOut {
  document_id: string;
  text: string | TeiElement[] | null;
}

export interface LemmaResult {
  headword: string;
  sequence_number: number;
  parses: ParseOut[];
  senses: SenseOut[];
  entries: EntryOut[];
  document_frequency: number | null;
  short_definition: string | TeiElement[] | null;
}

export interface MorphResponse {
  word: string;
  language_code: string;
  lemmas: LemmaResult[];
}
