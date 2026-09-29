// Port of the `beta_code` Python package's beta_code_to_greek (the only
// direction morph.py's lookup_parses uses, as a fallback retry for Greek
// input that turns out to be legacy Beta Code rather than genuine
// Unicode). See https://github.com/perseids-tools/beta-code-py (MIT,
// also a perseids project) -- betaCodeToUnicode.json is that package's
// vendored vendor/beta-code-json/beta_code_to_unicode.json data, copied
// verbatim rather than re-derived, since it's the actual mapping table
// morph.db's source data was transliterated with.
import betaCodeToUnicodeMapJson from "./betaCodeToUnicode.json";

const BETA_CODE_TO_UNICODE_MAP: Record<string, string> = betaCodeToUnicodeMapJson;

const MAX_KEY_LENGTH = Math.max(...Object.keys(BETA_CODE_TO_UNICODE_MAP).map((k) => k.length));

// Mirrors sigma_to_end_of_word_sigma: a lone sigma (σ) immediately before
// punctuation, whitespace, or the end of the string becomes a final
// sigma (ς), matching Greek orthography.
const FINAL_SIGMA_CONTEXT = /σ(?=[,.:;·\s]|$)/gu;

function applyFinalSigma(s: string): string {
  return s.replace(FINAL_SIGMA_CONTEXT, "ς");
}

// Mirrors beta_code_to_greek: a greedy longest-match scan over the input
// (by Unicode code point, not UTF-16 code unit -- Array.from splits on
// code points the way Python's `list(str)` does), trying every substring
// length from the current position down to 1, preferring the longest
// key present in the map, and falling through to the original character
// unchanged when nothing matches (so genuine Unicode input, which has no
// entries in this table, passes through untouched).
export function betaCodeToGreek(betaCode: string): string {
  const chars = Array.from(betaCode.normalize("NFC"));
  const result: string[] = [];
  let start = 0;

  while (start < chars.length) {
    let current = chars[start] as string;
    let newStart = start + 1;
    const maxLength = Math.min(chars.length, start + MAX_KEY_LENGTH);

    for (let last = newStart; last <= maxLength; last++) {
      const slice = chars.slice(start, last).join("");
      const mapped = BETA_CODE_TO_UNICODE_MAP[slice];
      if (mapped !== undefined) {
        current = mapped;
        newStart = last;
      }
    }

    result.push(current);
    start = newStart;
  }

  return applyFinalSigma(result.join(""));
}
