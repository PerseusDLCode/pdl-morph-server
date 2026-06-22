import re
import unicodedata

# Mirrors perseus.morph.Lemma.BARE_WORD_PATTERN / perseus-morph.language/bare-word-pattern:
# strips beta-code diacritic markers to produce an accent-free form.
BARE_WORD_PATTERN = re.compile(r"[()\\/*=|+']")

def bare_form(s: str) -> str:
    return BARE_WORD_PATTERN.sub("", s)


def normalize_unicode(s: str | None) -> str | None:
    """Mirrors perseus-morph.language/normalize-unicode: NFD-decomposes
    already-composed Unicode text (e.g. form_unicode), strips combining
    diacritics (Unicode category "M*"), and lowercases, so accented Unicode
    Greek search input can match parses.form_normalized regardless of
    accents/case."""
    if s is None:
        return None
    decomposed = unicodedata.normalize("NFD", s)
    stripped = "".join(c for c in decomposed if not unicodedata.category(c).startswith("M"))
    return stripped.lower()


def _greek_lowercase(s: str) -> str:
    if s and s[0].isupper():
        s = s[0].lower() + s[1:]
    return s.replace("*", "")


def to_lowercase(language_code: str, s: str) -> str:
    if language_code == "grc":
        return _greek_lowercase(s)
    return s.lower()


def match_case(language_code: str) -> bool:
    return language_code == "ara"


def normalize_form(language_code: str, form: str) -> str:
    """Mirrors `adapter.matchCase() ? form : adapter.toLowerCase(form)`."""
    if match_case(language_code):
        return form
    return to_lowercase(language_code, form)
