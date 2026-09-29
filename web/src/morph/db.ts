import type { DocumentFrequency, Entry, Lemma, LemmaKey, Parse, Sense } from "./types.js";

// Thin, engine-agnostic query interface. In the browser (Phase 2) this is
// implemented against a sql.js-httpvfs worker; for tests and the parity
// harness (Phase 8) it's implemented against a local morph.db copy via
// node:sqlite. Both just need to answer parameterized SELECTs.
export interface MorphDb {
  all<T>(sql: string, params?: Record<string, unknown>): Promise<T[]>;
  get<T>(sql: string, params?: Record<string, unknown>): Promise<T | undefined>;
}

// Lexica ingested into morph.db, by language (see
// clojure/src/perseus_morph/lexica/ingest.clj). A language can have more
// than one; add to its list as more lexica are ingested (e.g. Middle
// Liddell for "grc").
export const LEXICA_BY_LANGUAGE: Record<string, string[]> = {
  grc: ["LSJ", "Middle Liddell", "Logeion-Greek-Shortdef"],
  lat: ["lewis-short", "Lewis & Short", "Logeion-Latin-Shortdef"],
};

// Which of a language's LEXICA_BY_LANGUAGE document_id holds the one-line
// Logeion short definition to surface in the headword summary, as opposed
// to the fuller LSJ/Lewis & Short glosses also present in lookupSenses'
// results.
export const SHORT_DEF_DOCUMENT_ID: Record<string, string> = {
  grc: "Logeion-Greek-Shortdef",
  lat: "Logeion-Latin-Shortdef",
};

async function parsesMatching(
  db: MorphDb,
  languageCode: string,
  column: "form" | "bare_form" | "form_normalized",
  value: string,
): Promise<Array<{ parse: Parse; lemma: Lemma }>> {
  const rows = await db.all<Record<string, unknown>>(
    `SELECT
       parses.id AS parse_id, parses.lemma_id AS parse_lemma_id, parses.form AS parse_form,
       parses.form_normalized AS parse_form_normalized, parses.expanded_form AS parse_expanded_form,
       parses.bare_form AS parse_bare_form, parses.part_of_speech AS parse_part_of_speech,
       parses.person AS parse_person, parses.number AS parse_number, parses.tense AS parse_tense,
       parses.mood AS parse_mood, parses.voice AS parse_voice, parses.gender AS parse_gender,
       parses.grammatical_case AS parse_grammatical_case, parses.degree AS parse_degree,
       parses.dialect AS parse_dialect, parses.other AS parse_other, parses.prefix AS parse_prefix,
       parses.object AS parse_object, parses.definite AS parse_definite,
       parses.possessive AS parse_possessive, parses.dedup_key AS parse_dedup_key,
       lemmas.id AS lemma_id, lemmas.headword AS lemma_headword,
       lemmas.bare_headword AS lemma_bare_headword, lemmas.sequence_number AS lemma_sequence_number,
       lemmas.language_code AS lemma_language_code
     FROM parses
     JOIN lemmas ON parses.lemma_id = lemmas.id
     WHERE parses.${column} = :value AND lemmas.language_code = :language_code`,
    { value, language_code: languageCode },
  );
  return rows.map((row) => rowToParseLemma(row));
}

function rowToParseLemma(row: Record<string, unknown>): { parse: Parse; lemma: Lemma } {
  return {
    parse: {
      id: row.parse_id as number,
      lemma_id: row.parse_lemma_id as number,
      form: row.parse_form as string,
      form_normalized: row.parse_form_normalized as string | null,
      expanded_form: row.parse_expanded_form as string | null,
      bare_form: row.parse_bare_form as string | null,
      part_of_speech: row.parse_part_of_speech as string | null,
      person: row.parse_person as string | null,
      number: row.parse_number as string | null,
      tense: row.parse_tense as string | null,
      mood: row.parse_mood as string | null,
      voice: row.parse_voice as string | null,
      gender: row.parse_gender as string | null,
      grammatical_case: row.parse_grammatical_case as string | null,
      degree: row.parse_degree as string | null,
      dialect: row.parse_dialect as string | null,
      other: row.parse_other as string | null,
      prefix: row.parse_prefix as string | null,
      object: row.parse_object as string | null,
      definite: row.parse_definite as string | null,
      possessive: row.parse_possessive as string | null,
      dedup_key: row.parse_dedup_key as string,
    },
    lemma: {
      id: row.lemma_id as number,
      headword: row.lemma_headword as string,
      bare_headword: row.lemma_bare_headword as string | null,
      sequence_number: row.lemma_sequence_number as number,
      language_code: row.lemma_language_code as string,
    },
  };
}

// Mirrors morph.py's _lookup_parses_for_word: the three-tier Unicode
// lookup -- try `word` as typed (case-normalized) against form, then fall
// back to bare_form with diacritics stripped, and beyond that to
// form_normalized -- mirroring MorphController's lookup chain.
export async function lookupParsesForWord(
  db: MorphDb,
  languageCode: string,
  word: string,
  normalizeForm: (languageCode: string, form: string) => string,
  bareForm: (s: string) => string,
  normalizeUnicode: (s: string | null) => string | null,
): Promise<Array<{ parse: Parse; lemma: Lemma }>> {
  const normalized = normalizeForm(languageCode, word);
  let rows = await parsesMatching(db, languageCode, "form", normalized);
  if (rows.length === 0) {
    rows = await parsesMatching(db, languageCode, "bare_form", bareForm(word));
  }
  if (rows.length === 0) {
    rows = await parsesMatching(
      db,
      languageCode,
      "form_normalized",
      String(normalizeUnicode(word)),
    );
  }
  return rows;
}

// Mirrors morph.py's document_frequency: two sequential lookups (lemma id,
// then that lemma's weighted frequency within `documentId`).
export async function documentFrequency(
  db: MorphDb,
  languageCode: string,
  documentId: string,
  headword: string,
  sequenceNumber: number,
): Promise<number | null> {
  const lemma = await db.get<{ id: number }>(
    `SELECT id FROM lemmas
     WHERE language_code = :language_code AND headword = :headword
       AND sequence_number = :sequence_number`,
    { language_code: languageCode, headword, sequence_number: sequenceNumber },
  );
  if (lemma === undefined) {
    return null;
  }
  const row = await db.get<{ weighted_frequency: number }>(
    `SELECT weighted_frequency FROM document_frequencies
     WHERE document_id = :document_id AND lemma_id = :lemma_id`,
    { document_id: documentId, lemma_id: lemma.id },
  );
  return row === undefined ? null : row.weighted_frequency;
}

function lexicaKey(headword: string, sequenceNumber: number): string {
  return sequenceNumber === -1 ? headword : `${headword}${sequenceNumber}`;
}

function inClausePlaceholders(prefix: string, values: string[]): {
  clause: string;
  params: Record<string, string>;
} {
  const params: Record<string, string> = {};
  const names: string[] = [];
  values.forEach((value, i) => {
    const name = `${prefix}_${i}`;
    names.push(`:${name}`);
    params[name] = value;
  });
  return { clause: names.join(", "), params };
}

// Mirrors morph.py's lookup_senses: senses are keyed by "entry=" + the
// lexicon entry's `key` attribute (headword + sequence_number, omitted
// when -1). Queries every lexicon ingested for `languageCode`.
export async function lookupSenses(
  db: MorphDb,
  languageCode: string,
  headword: string,
  sequenceNumber: number,
): Promise<Sense[]> {
  const documentIds = LEXICA_BY_LANGUAGE[languageCode] ?? [];
  if (documentIds.length === 0) {
    return [];
  }
  const key = lexicaKey(headword, sequenceNumber);
  const { clause, params } = inClausePlaceholders("doc", documentIds);
  return db.all<Sense>(
    `SELECT id, entry_id, sense_id, document_id, lemma, sense, level, definition
     FROM senses
     WHERE document_id IN (${clause}) AND lemma = :lemma`,
    { ...params, lemma: `entry=${key}` },
  );
}

// Mirrors morph.py's lookup_entries: entries are keyed by the lexicon
// entry's `key` attribute directly (no "entry=" prefix, unlike
// senses.lemma). Queries every lexicon ingested for `languageCode`.
export async function lookupEntries(
  db: MorphDb,
  languageCode: string,
  headword: string,
  sequenceNumber: number,
): Promise<Entry[]> {
  const documentIds = LEXICA_BY_LANGUAGE[languageCode] ?? [];
  if (documentIds.length === 0) {
    return [];
  }
  const key = lexicaKey(headword, sequenceNumber);
  const { clause, params } = inClausePlaceholders("doc", documentIds);
  return db.all<Entry>(
    `SELECT document_id, key, text
     FROM entries
     WHERE document_id IN (${clause}) AND key = :key`,
    { ...params, key },
  );
}

// Mirrors morph.py's form_frequency_scores' per-parse SQL: exact
// (language_code, feature_key) lookup against the precomputed
// morph_frequencies table.
export async function queryMorphFrequency(
  db: MorphDb,
  languageCode: string,
  featureKey: string,
): Promise<number> {
  const row = await db.get<{ count: number }>(
    "SELECT count FROM morph_frequencies WHERE language_code = :language_code AND feature_key = :feature_key",
    { language_code: languageCode, feature_key: featureKey },
  );
  return row === undefined ? 0.0 : row.count;
}

// Mirrors morph.py's prior_frequency_scores' per-parse SQL: sums
// prior_frequencies.count across every one of the prior word's distinct
// feature keys, for this parse's current_feature_key.
export async function queryPriorFrequencySum(
  db: MorphDb,
  languageCode: string,
  currentFeatureKey: string,
  previousFeatureKeys: string[],
): Promise<number> {
  const { clause, params } = inClausePlaceholders("previous", previousFeatureKeys);
  const row = await db.get<{ total: number | null }>(
    `SELECT SUM(count) AS total FROM prior_frequencies
     WHERE language_code = :language_code
       AND current_feature_key = :current_feature_key
       AND previous_feature_key IN (${clause})`,
    { ...params, language_code: languageCode, current_feature_key: currentFeatureKey },
  );
  return row?.total ?? 0.0;
}

export type { DocumentFrequency, LemmaKey };
