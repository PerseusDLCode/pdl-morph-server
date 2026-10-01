import { betaCodeToGreek as defaultBetaCodeToGreek } from "./betaCode.js";
import type { MorphDb } from "./db.js";
import { documentFrequency, lookupEntries, lookupLemmasByHeadword, lookupParsesForWord, lookupSenses, SHORT_DEF_DOCUMENT_ID } from "./db.js";
import { bareForm, normalizeForm, normalizeUnicode } from "./language.js";
import { groupByLemma, lemmaKeyToString, type LemmaGroups } from "./lemmaGroups.js";
import { formFrequencyScores, priorFrequencyScores, selectWinningParse, wordFrequencyScores } from "./scoring.js";
import type { Entry, LemmaKey, EntryOut, LemmaResult, MorphResponse, Parse, ParseOut, Sense, SenseOut } from "./types.js";

export interface LookupParsesOptions {
  betaCodeToGreek?: (word: string) => string;
}

// Mirrors morph.py's lookup_parses: looks up `word`'s candidate parses,
// and -- for Greek only -- retries once with `word` run through Beta
// Code -> Unicode transliteration if the direct lookup came up empty and
// the conversion actually changed the string (so genuine Unicode input
// isn't wastefully looked up twice).
export async function lookupParses(
  db: MorphDb,
  word: string,
  languageCode: string,
  options: LookupParsesOptions = {},
): Promise<LemmaGroups> {
  const betaCodeToGreek = options.betaCodeToGreek ?? defaultBetaCodeToGreek;

  let rows = await lookupParsesForWord(db, languageCode, word, normalizeForm, bareForm, normalizeUnicode);
  if (rows.length === 0 && languageCode === "grc") {
    const converted = betaCodeToGreek(word);
    if (converted !== word) {
      rows = await lookupParsesForWord(db, languageCode, converted, normalizeForm, bareForm, normalizeUnicode);
    }
  }
  const grouped = groupByLemma(rows);
  if (rows.length === 0) {
    // No parse has this form; it may still be a lemma's headword. Those
    // lemmas get an empty parse list so senses/entries are still shown.
    const lemmas = await lookupLemmasByHeadword(db, languageCode, normalizeForm(languageCode, word), bareForm(word));
    for (const lemma of lemmas) {
      const key: LemmaKey = [lemma.headword, lemma.sequence_number];
      grouped.set(lemmaKeyToString(key), { key, parses: [] });
    }
  }
  return grouped;
}

// Mirrors morph.py's short_definition: picks the one-line Logeion gloss
// out of `senses` (as returned by lookupSenses), i.e. the sense whose
// document_id is SHORT_DEF_DOCUMENT_ID[languageCode], if any.
export function shortDefinition(senses: Sense[], languageCode: string): string | null {
  const documentId = SHORT_DEF_DOCUMENT_ID[languageCode];
  if (documentId === undefined) {
    return null;
  }
  const sense = senses.find((s) => s.document_id === documentId);
  return sense?.definition ?? null;
}

function toParseOut(parse: Parse, winnerId: number | null): ParseOut {
  return {
    form: parse.form,
    expanded_form: parse.expanded_form,
    part_of_speech: parse.part_of_speech,
    person: parse.person,
    number: parse.number,
    tense: parse.tense,
    mood: parse.mood,
    voice: parse.voice,
    gender: parse.gender,
    grammatical_case: parse.grammatical_case,
    degree: parse.degree,
    dialect: parse.dialect,
    other: parse.other,
    prefix: parse.prefix,
    object: parse.object,
    definite: parse.definite,
    possessive: parse.possessive,
    is_winner: parse.id === winnerId,
  };
}

// Mirrors schemas.py's SenseOut: Clojure writes -1 (not NULL) when a
// <sense> has no level attribute, so that sentinel is stripped here.
function toSenseOut(sense: Sense): SenseOut {
  return {
    document_id: sense.document_id,
    sense: sense.sense,
    level: sense.level === -1 ? null : sense.level,
    definition: sense.definition,
  };
}

function toEntryOut(entry: Entry): EntryOut {
  return { document_id: entry.document_id, text: entry.text };
}

export interface BuildMorphResponseOptions extends LookupParsesOptions {
  documentId?: string;
  priorWord?: string;
}

// Mirrors main.py's shared pipeline (both /api/morph and /morph run this
// before /morph additionally runs sense/entry/short-def text through
// _parse_tei_text -- Phase 4's concern, not this function's).
export async function buildMorphResponse(
  db: MorphDb,
  word: string,
  languageCode: string,
  options: BuildMorphResponseOptions = {},
): Promise<MorphResponse> {
  const grouped = await lookupParses(db, word, languageCode, options);

  const documentFrequencies = new Map<string, number | null>();
  if (options.documentId !== undefined) {
    for (const group of grouped.values()) {
      const [headword, sequenceNumber] = group.key;
      const frequency = await documentFrequency(
        db,
        languageCode,
        options.documentId,
        headword,
        sequenceNumber,
      );
      documentFrequencies.set(lemmaKeyToString(group.key), frequency);
    }
  }

  const priorGrouped: LemmaGroups =
    options.priorWord !== undefined
      ? await lookupParses(db, options.priorWord, languageCode, options)
      : new Map();

  const winnerId = selectWinningParse(
    wordFrequencyScores(grouped, documentFrequencies),
    await formFrequencyScores(db, languageCode, grouped),
    await priorFrequencyScores(db, languageCode, grouped, priorGrouped),
  );

  const lemmas: LemmaResult[] = [];
  for (const group of grouped.values()) {
    const [headword, sequenceNumber] = group.key;
    const senses = await lookupSenses(db, languageCode, headword, sequenceNumber);
    const entries = await lookupEntries(db, languageCode, headword, sequenceNumber);
    lemmas.push({
      headword,
      sequence_number: sequenceNumber,
      parses: group.parses.map((parse) => toParseOut(parse, winnerId)),
      senses: senses.map(toSenseOut),
      entries: entries.map(toEntryOut),
      document_frequency: documentFrequencies.get(lemmaKeyToString(group.key)) ?? null,
      short_definition: shortDefinition(senses, languageCode),
    });
  }

  return { word, language_code: languageCode, lemmas };
}
