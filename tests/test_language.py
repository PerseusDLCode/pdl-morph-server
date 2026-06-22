from new_morpheus.language import normalize_unicode


def test_normalize_unicode_strips_accents_and_lowercases():
    assert normalize_unicode("Ἐνύω") == "ενυω"


def test_normalize_unicode_is_a_no_op_for_already_bare_lowercase_text():
    assert normalize_unicode("ενυω") == "ενυω"


def test_normalize_unicode_passes_through_none():
    assert normalize_unicode(None) is None
