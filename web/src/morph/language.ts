// Port of src/new_morpheus/language.py.

// Mirrors BARE_WORD_PATTERN / perseus-morph.language/bare-word-pattern:
// strips beta-code diacritic markers to produce an accent-free form.
const BARE_WORD_PATTERN = /[()\\/*=|+']/g;

export function bareForm(s: string): string {
  return s.replace(BARE_WORD_PATTERN, "");
}

// Unicode general-category "M*" (Mn/Mc/Me, i.e. combining marks) is exactly
// what JS's \p{M} property escape matches, so this needs no per-character
// category lookup the way the Python port would.
const COMBINING_MARK = /\p{M}/gu;

export function normalizeUnicode(s: string | null): string | null {
  if (s === null) {
    return null;
  }
  const decomposed = s.normalize("NFD");
  const stripped = decomposed.replace(COMBINING_MARK, "");
  return stripped.toLowerCase();
}

function isUpper(ch: string): boolean {
  return ch !== ch.toLowerCase() && ch === ch.toUpperCase();
}

function greekLowercase(s: string): string {
  const first = s.length > 0 ? s[0] : undefined;
  const lowered =
    first !== undefined && isUpper(first) ? first.toLowerCase() + s.slice(1) : s;
  return lowered.replaceAll("*", "");
}

export function toLowercase(languageCode: string, s: string): string {
  if (languageCode === "grc") {
    return greekLowercase(s);
  }
  return s.toLowerCase();
}

export function matchCase(languageCode: string): boolean {
  return languageCode === "ara";
}

// Mirrors `adapter.matchCase() ? form : adapter.toLowerCase(form)`.
export function normalizeForm(languageCode: string, form: string): string {
  if (matchCase(languageCode)) {
    return form;
  }
  return toLowercase(languageCode, form);
}
