import { describe, expect, it } from "vitest";
import { betaCodeToGreek } from "../src/morph/betaCode.js";

// Expected values captured directly from the Python `beta_code` package
// (`beta_code.beta_code_to_greek(...)`), the source of truth this is a
// port of -- not hand-derived, so a mismatch here means a real port bug.
describe("betaCodeToGreek", () => {
  it("converts common Beta Code word forms", () => {
    expect(betaCodeToGreek("lo/gos")).toBe("λόγος");
    expect(betaCodeToGreek("a)/nqrwpos")).toBe("ἄνθρωπος");
    expect(betaCodeToGreek("mh=nis")).toBe("μῆνις");
    expect(betaCodeToGreek("a)ei/dw")).toBe("ἀείδω");
  });

  it("handles the capital marker '*' combined with breathing/accent", () => {
    expect(betaCodeToGreek("*a)ga/qwn")).toBe("Ἀγάθων");
  });

  it("transliterates every ASCII letter regardless of whether the input 'looks like' Beta Code -- callers are responsible for only invoking this as a fallback", () => {
    expect(betaCodeToGreek("ARISTOTELES")).toBe("αριστοτελες");
    expect(betaCodeToGreek("plain text no beta code")).toBe("πλαιν τεχτ νο βετα ξοδε");
  });

  it("applies final-sigma correction before punctuation and at end of string", () => {
    expect(betaCodeToGreek("lo/gos, kai\\ a)nqrwpos.")).toBe("λόγος, καὶ ἀνθρωπος.");
  });

  it("applies final-sigma correction before trailing whitespace", () => {
    expect(betaCodeToGreek("a)/nqrwpos ")).toBe("ἄνθρωπος ");
  });

  it("leaves genuine Unicode Greek unchanged (no Beta Code characters to match)", () => {
    expect(betaCodeToGreek("λόγος")).toBe("λόγος");
  });
});
