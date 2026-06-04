"""
Unit tests for Beta Code detection and conversion functions.

These tests exercise pure functions in main.py that have no side effects
and do not depend on the morphology indexes.
"""

import unicodedata

from main import (
    _beta_to_unicode,
    _greek_key,
    _is_unicode_greek,
    _latin_key,
    _normalize_greek,
    _unicode_to_betacode,
)


class TestIsUnicodeGreek:
    def test_unicode_greek_word(self):
        assert _is_unicode_greek("λόγος") is True

    def test_unicode_greek_with_breathings(self):
        assert _is_unicode_greek("ἄνθρωπος") is True

    def test_greek_with_rough_breathing(self):
        assert _is_unicode_greek("ὁδός") is True

    def test_latin_word_is_false(self):
        assert _is_unicode_greek("amor") is False

    def test_betacode_string_is_false(self):
        assert _is_unicode_greek("lo/gos") is False

    def test_plain_ascii_is_false(self):
        assert _is_unicode_greek("hello world") is False

    def test_empty_string_is_false(self):
        assert _is_unicode_greek("") is False

    def test_mixed_greek_and_latin(self):
        assert _is_unicode_greek("λόγος and logos") is True

    def test_digits_and_punctuation(self):
        assert _is_unicode_greek("123!@#") is False


class TestUnicodeToBetaCode:
    def test_logos(self):
        assert _unicode_to_betacode("λόγος") == "lo/gos"

    def test_anthropos(self):
        assert _unicode_to_betacode("ἄνθρωπος") == "a)/nqrwpos"

    def test_uppercase_letter_prefixed_with_star(self):
        result = _unicode_to_betacode("Λ")
        assert result == "*l"

    def test_uppercase_word(self):
        result = _unicode_to_betacode("ΛΟΓΟΣ")
        assert result.startswith("*")

    def test_rough_breathing(self):
        # ὁδός: omicron with rough breathing + acute
        result = _unicode_to_betacode("ὁ")
        assert "(" in result

    def test_smooth_breathing(self):
        result = _unicode_to_betacode("ἀ")
        assert ")" in result

    def test_circumflex(self):
        result = _unicode_to_betacode("ῆ")
        assert "=" in result

    def test_iota_subscript(self):
        result = _unicode_to_betacode("ᾧ")
        assert "|" in result

    def test_diaeresis(self):
        result = _unicode_to_betacode("ϊ")
        assert "+" in result

    def test_non_greek_passthrough(self):
        assert _unicode_to_betacode("abc") == "abc"

    def test_space_passthrough(self):
        assert " " in _unicode_to_betacode("λόγος εἰμί")

    def test_latin_letters_unchanged(self):
        assert _unicode_to_betacode("abc123") == "abc123"


class TestNormalizeGreek:
    def test_grave_converted_to_acute(self):
        assert _normalize_greek("lo\\gos") == "lo/gos"

    def test_acute_unchanged(self):
        assert _normalize_greek("lo/gos") == "lo/gos"

    def test_circumflex_unchanged(self):
        assert "=" in _normalize_greek("a)=ndra")

    def test_square_bracket_removal(self):
        assert _normalize_greek("[logos]") == "logos"

    def test_opening_bracket_removal(self):
        assert _normalize_greek("[logos") == "logos"

    def test_closing_bracket_removal(self):
        assert _normalize_greek("logos]") == "logos"

    def test_secondary_accent_removed(self):
        # A second slash after the first one (enclitic secondary accent) is stripped
        result = _normalize_greek("lo/go/s")
        assert result == "lo/gos"

    def test_no_change_for_plain_form(self):
        assert _normalize_greek("logos") == "logos"


class TestGreekKey:
    def test_produces_lowercase(self):
        assert _greek_key("LO/GOS") == "lo/gos"

    def test_capital_star_stripped(self):
        assert _greek_key("*lo/gos") == "lo/gos"

    def test_uppercase_with_star(self):
        assert _greek_key("*LO/GOS") == "lo/gos"

    def test_grave_normalized_to_acute(self):
        assert _greek_key("lo\\gos") == "lo/gos"

    def test_unicode_and_betacode_same_key(self):
        # Unicode and Beta Code of the same word must hash to the same key
        unicode_key = _greek_key(_unicode_to_betacode("λόγος"))
        betacode_key = _greek_key("lo/gos")
        assert unicode_key == betacode_key

    def test_anthropos_unicode_and_betacode_same_key(self):
        unicode_key = _greek_key(_unicode_to_betacode("ἄνθρωπος"))
        betacode_key = _greek_key("a)/nqrwpos")
        assert unicode_key == betacode_key


class TestLatinKey:
    def test_uppercase_lowercased(self):
        assert _latin_key("AMOR") == "amor"

    def test_already_lowercase(self):
        assert _latin_key("amor") == "amor"

    def test_mixed_case(self):
        assert _latin_key("Puella") == "puella"

    def test_all_caps(self):
        assert _latin_key("EST") == "est"


class TestBetaToUnicode:
    def test_logos_contains_lambda(self):
        result = _beta_to_unicode("lo/gos")
        assert "λ" in result

    def test_logos_contains_gamma(self):
        result = _beta_to_unicode("lo/gos")
        assert "γ" in result

    def test_roundtrip_logos(self):
        original = "λόγος"
        betacode = _unicode_to_betacode(original)
        roundtrip = _beta_to_unicode(betacode)
        assert unicodedata.normalize("NFC", roundtrip) == unicodedata.normalize("NFC", original)

    def test_roundtrip_anthropos(self):
        original = "ἄνθρωπος"
        betacode = _unicode_to_betacode(original)
        roundtrip = _beta_to_unicode(betacode)
        assert unicodedata.normalize("NFC", roundtrip) == unicodedata.normalize("NFC", original)
