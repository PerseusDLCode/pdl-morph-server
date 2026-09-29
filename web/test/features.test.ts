import { describe, expect, it } from "vitest";
import { FEATURE_COLUMNS, featureKey, parseFeatures } from "../src/morph/features.js";
import type { Parse } from "../src/morph/types.js";

function makeParse(overrides: Partial<Parse> = {}): Parse {
  return {
    id: 1,
    lemma_id: 1,
    form: "form",
    form_normalized: null,
    expanded_form: null,
    bare_form: null,
    part_of_speech: null,
    person: null,
    number: null,
    tense: null,
    mood: null,
    voice: null,
    gender: null,
    grammatical_case: null,
    degree: null,
    dialect: null,
    other: null,
    prefix: null,
    object: null,
    definite: null,
    possessive: null,
    dedup_key: "",
    ...overrides,
  };
}

describe("FEATURE_COLUMNS", () => {
  it("is in alphabetical order, matching clojure/src/perseus_morph/features.clj", () => {
    expect(FEATURE_COLUMNS).toEqual([...FEATURE_COLUMNS].sort());
  });

  it("has exactly the 15 columns morph.py's FEATURE_COLUMNS declares", () => {
    expect(FEATURE_COLUMNS).toEqual([
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
    ]);
  });
});

describe("parseFeatures", () => {
  it("drops null feature columns", () => {
    const parse = makeParse({ part_of_speech: "noun", grammatical_case: "genitive" });
    expect(parseFeatures(parse)).toEqual({
      part_of_speech: "noun",
      grammatical_case: "genitive",
    });
  });

  it("is empty when every feature column is null", () => {
    expect(parseFeatures(makeParse())).toEqual({});
  });
});

describe("featureKey", () => {
  it("folds a single feature to just its value (matching the Clojure aggregator test fixture)", () => {
    expect(featureKey({ part_of_speech: "noun" })).toBe("noun");
    expect(featureKey({ part_of_speech: "verb" })).toBe("verb");
  });

  it("folds two features in FEATURE_COLUMNS order, not insertion order", () => {
    // grammatical_case sorts before part_of_speech alphabetically.
    expect(featureKey({ part_of_speech: "noun", grammatical_case: "genitive" })).toBe(
      "genitivenoun",
    );
  });

  it("folds the empty feature map to the empty string", () => {
    expect(featureKey({})).toBe("");
  });
});
