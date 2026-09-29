import type { Parse } from "./types.js";

// Stable column order for folding a parse's morphological features into a
// single key, matching perseus-morph.features/feature-columns (alphabetical
// order of the column names) and so morph.py's FEATURE_COLUMNS -- see
// clojure/src/perseus_morph/features.clj. Getting this order wrong silently
// breaks every morph_frequencies/prior_frequencies lookup, since the folded
// feature_key string must match rows already baked into morph.db.
export const FEATURE_COLUMNS = [
  "definite",
  "degree",
  "dialect",
  "gender",
  "grammatical_case",
  "mood",
  "number",
  "object",
  "other",
  "part_of_speech",
  "person",
  "possessive",
  "prefix",
  "tense",
  "voice",
] as const satisfies readonly (keyof Parse)[];

export type FeatureColumn = (typeof FEATURE_COLUMNS)[number];
export type Features = Partial<Record<FeatureColumn, string>>;

// Mirrors morph.py's _parse_features: picks the feature columns off a
// parse row, dropping absent (null) ones.
export function parseFeatures(parse: Parse): Features {
  const features: Features = {};
  for (const col of FEATURE_COLUMNS) {
    const value = parse[col];
    if (value !== null) {
      features[col] = value;
    }
  }
  return features;
}

// Same NOT-NULL fold as perseus-morph.features/fold-key and morph.py's
// _feature_key: every column, in FEATURE_COLUMNS order, missing ones
// folding to the empty string.
export function featureKey(features: Features): string {
  return FEATURE_COLUMNS.map((col) => features[col] ?? "").join("");
}
