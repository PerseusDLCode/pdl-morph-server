import type { MorphDb } from "./db.js";
import { queryMorphFrequency, queryPriorFrequencySum } from "./db.js";
import { type FeatureColumn, type Features, featureKey, parseFeatures } from "./features.js";
import { flattenParses, lemmaKeyToString, type LemmaGroups } from "./lemmaGroups.js";
import type { Parse } from "./types.js";

export type ScoreMap = Map<number, number>;

// Mirrors morph.py's _exclusive_features (which mirrors
// FormFrequencyEvaluator.findSpecialFeatures): the subset of
// `currentFeatures` not shared, value-for-value, by every other candidate
// parse -- e.g. among "1st sg imperf ind act" / "3rd pl imperf ind act" /
// "3rd sg imperf ind act", each parse's exclusive features are just its
// person+number.
export function exclusiveFeatures(
  otherFeatures: Features[],
  currentFeatures: Features,
): Features {
  const result: Features = {};
  for (const feature of Object.keys(currentFeatures) as FeatureColumn[]) {
    const value = currentFeatures[feature];
    const sharedByAll = otherFeatures.every((other) => other[feature] === value);
    if (!sharedByAll) {
      result[feature] = value;
    }
  }
  return result;
}

// Mirrors morph.py's form_frequency_scores: scores each candidate parse by
// how often its features (or, when ambiguous, just the features that set
// it apart from its sibling candidates) occur corpus-wide, via the
// morph_frequencies table, which only ever needs one exact feature_key
// lookup per parse.
export async function formFrequencyScores(
  db: MorphDb,
  languageCode: string,
  lemmaGroups: LemmaGroups,
): Promise<ScoreMap> {
  const parses = flattenParses(lemmaGroups);
  const featuresById = new Map<number, Features>();
  for (const parse of parses) {
    featuresById.set(parse.id, parseFeatures(parse));
  }

  const scores: ScoreMap = new Map();
  for (const parse of parses) {
    const current = featuresById.get(parse.id) as Features;
    const others = parses
      .filter((other) => other.id !== parse.id)
      .map((other) => featuresById.get(other.id) as Features);
    const exclusive = exclusiveFeatures(others, current);
    const key = Object.keys(exclusive).length === 0 ? current : exclusive;
    const count = await queryMorphFrequency(db, languageCode, featureKey(key));
    scores.set(parse.id, count);
  }
  return scores;
}

// Mirrors morph.py's prior_frequency_scores (a simplified port of
// PriorFrequencyEvaluator that drops its indeclinable-prior-word fallback,
// since prior_frequencies only stores feature keys, not lemmas): scores
// each candidate parse by the corpus-wide count of it following any of the
// preceding word's own candidate parses.
export async function priorFrequencyScores(
  db: MorphDb,
  languageCode: string,
  lemmaGroups: LemmaGroups,
  priorLemmaGroups: LemmaGroups,
): Promise<ScoreMap> {
  if (priorLemmaGroups.size === 0) {
    return new Map();
  }

  const parses = flattenParses(lemmaGroups);
  const previousKeys = [
    ...new Set(
      flattenParses(priorLemmaGroups).map((parse) => featureKey(parseFeatures(parse))),
    ),
  ].sort();

  const scores: ScoreMap = new Map();
  for (const parse of parses) {
    const currentKey = featureKey(parseFeatures(parse));
    const sum = await queryPriorFrequencySum(db, languageCode, currentKey, previousKeys);
    scores.set(parse.id, sum);
  }
  return scores;
}

// Mirrors morph.py's word_frequency_scores (via LexicalParseEvaluator,
// hence the same "skip unless there's more than one candidate lemma"
// guard): broadcasts each lemma's document_frequencies score to every one
// of its candidate parses.
export function wordFrequencyScores(
  lemmaGroups: LemmaGroups,
  documentFrequencies: Map<string, number | null>,
): ScoreMap {
  if (lemmaGroups.size < 2) {
    return new Map();
  }

  const scores: ScoreMap = new Map();
  for (const group of lemmaGroups.values()) {
    const frequency = documentFrequencies.get(lemmaKeyToString(group.key)) ?? 0.0;
    for (const parse of group.parses) {
      scores.set(parse.id, frequency);
    }
  }
  return scores;
}

// Mirrors morph.py's _normalize (ParseEvaluator.normalizeScores): each
// score divided by the sum of all of its evaluator's scores, so evaluators
// on different scales contribute comparably to the combined average.
export function normalizeScores(scores: ScoreMap): ScoreMap {
  let total = 0.0;
  for (const score of scores.values()) {
    total += score;
  }
  const normalized: ScoreMap = new Map();
  if (total === 0.0) {
    for (const parseId of scores.keys()) {
      normalized.set(parseId, 0.0);
    }
    return normalized;
  }
  for (const [parseId, score] of scores) {
    normalized.set(parseId, score / total);
  }
  return normalized;
}

// Mirrors morph.py's select_winning_parse (ParseSelector.
// ParseVotingResults.updateTotal with voting excluded): averages each
// parse's normalized scores across whichever evaluators actually scored
// it, and returns the id of the highest-averaging parse -- or null if no
// evaluator scored anything. Ties are broken by first occurrence in
// insertion order, matching Python's max() over a dict.
export function selectWinningParse(...scoreMaps: ScoreMap[]): number | null {
  const totals = new Map<number, number>();
  const counts = new Map<number, number>();

  for (const scores of scoreMaps) {
    if (scores.size === 0) {
      continue;
    }
    for (const [parseId, score] of normalizeScores(scores)) {
      totals.set(parseId, (totals.get(parseId) ?? 0.0) + score);
      counts.set(parseId, (counts.get(parseId) ?? 0) + 1);
    }
  }

  if (totals.size === 0) {
    return null;
  }

  let bestParseId: number | null = null;
  let bestAverage = -Infinity;
  for (const [parseId, total] of totals) {
    const average = total / (counts.get(parseId) as number);
    if (bestParseId === null || average > bestAverage) {
      bestParseId = parseId;
      bestAverage = average;
    }
  }
  return bestParseId;
}

export type { Parse };
