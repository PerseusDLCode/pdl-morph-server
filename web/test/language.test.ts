import { describe, expect, it } from "vitest";
import { bareForm, matchCase, normalizeForm, normalizeUnicode, toLowercase } from "../src/morph/language.js";

describe("bareForm", () => {
  it("strips beta-code diacritic markers", () => {
    expect(bareForm("lo/gos")).toBe("logos");
    expect(bareForm("a)/nqrwpos")).toBe("anqrwpos");
    expect(bareForm("mh=nis")).toBe("mhnis");
  });

  it("leaves plain text untouched", () => {
    expect(bareForm("logos")).toBe("logos");
  });
});

describe("normalizeUnicode", () => {
  it("returns null for null input", () => {
    expect(normalizeUnicode(null)).toBeNull();
  });

  it("NFD-decomposes, strips combining marks, and lowercases", () => {
    expect(normalizeUnicode("λόγος")).toBe("λογος");
    expect(normalizeUnicode("ἌΝΘΡΩΠΟΣ")).toBe(normalizeUnicode("άνθρωποσ".toUpperCase()));
  });

  it("is idempotent on already-bare lowercase text", () => {
    expect(normalizeUnicode("λογος")).toBe("λογος");
  });
});

describe("toLowercase / greek lowercasing", () => {
  it("lowercases only the first character for Greek", () => {
    expect(toLowercase("grc", "Ελλας")).toBe("ελλας");
    expect(toLowercase("grc", "λογος")).toBe("λογος");
  });

  it("strips the beta-code capital marker '*' without lowercasing what follows it -- a direct port of morph.py's _greek_lowercase, which only inspects s[0] (the '*' itself, not the letter after it)", () => {
    expect(toLowercase("grc", "*Ελλας")).toBe("Ελλας");
  });

  it("leaves an already-lowercase Greek word untouched but for '*' stripping", () => {
    expect(toLowercase("grc", "λογος")).toBe("λογος");
  });

  it("plain-lowercases non-Greek languages", () => {
    expect(toLowercase("lat", "PUELLA")).toBe("puella");
  });
});

describe("matchCase / normalizeForm", () => {
  it("skips case-folding only for Arabic", () => {
    expect(matchCase("ara")).toBe(true);
    expect(matchCase("grc")).toBe(false);
    expect(matchCase("lat")).toBe(false);
  });

  it("normalizeForm passes Arabic through untouched", () => {
    expect(normalizeForm("ara", "ABC")).toBe("ABC");
  });

  it("normalizeForm case-folds Greek/Latin", () => {
    expect(normalizeForm("lat", "PUELLA")).toBe("puella");
    expect(normalizeForm("grc", "Ελλας")).toBe("ελλας");
  });
});
