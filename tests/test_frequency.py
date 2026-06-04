"""
Tests for corpus-frequency statistics used to rank lemma analyses.

`_lookup_freqs` returns a {lemma: score} dict where scores represent the
percentage of corpus token weight attributable to that lemma. The dict is
ordered by descending score. These tests require the app lifespan to have
run (the `client` fixture handles that).
"""

import pytest

import main


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def lookup(form, lang, client):
    """Call _lookup_freqs in-process after the lifespan has populated app.state."""
    _ = client  # ensure lifespan has run
    return main._lookup_freqs(form, lang)


# ---------------------------------------------------------------------------
# Latin frequency tests
# ---------------------------------------------------------------------------

class TestLatinFrequency:
    def test_known_word_returns_scores(self, client):
        scores = lookup("amor", "la", client)
        assert len(scores) > 0

    def test_scores_are_floats(self, client):
        scores = lookup("amor", "la", client)
        assert all(isinstance(v, float) for v in scores.values())

    def test_scores_sum_to_100(self, client):
        scores = lookup("amor", "la", client)
        total = sum(scores.values())
        assert abs(total - 100.0) < 0.01

    def test_scores_ordered_descending(self, client):
        scores = lookup("est", "la", client)
        values = list(scores.values())
        assert values == sorted(values, reverse=True)

    def test_top_lemma_for_amor_is_amor(self, client):
        scores = lookup("amor", "la", client)
        top_lemma = next(iter(scores))
        assert top_lemma.startswith("amor")

    def test_est_includes_sum_lemma(self, client):
        # "est" is ambiguous: sum#1 (to be) and edo#1 (to eat) both have this form.
        # Corpus frequency may rank edo#1 first; test that sum#1 is present at all.
        scores = lookup("est", "la", client)
        assert any(l.startswith("sum") for l in scores)

    def test_unknown_latin_form_returns_empty(self, client):
        scores = lookup("zzzzqqqq", "la", client)
        assert scores == {}

    def test_all_scores_nonnegative(self, client):
        scores = lookup("puella", "la", client)
        assert all(v >= 0 for v in scores.values())


# ---------------------------------------------------------------------------
# Greek frequency tests
# ---------------------------------------------------------------------------

class TestGreekFrequency:
    def test_known_unicode_word_returns_scores(self, client):
        scores = lookup("λόγος", "grc", client)
        assert len(scores) > 0

    def test_known_betacode_word_returns_scores(self, client):
        scores = lookup("lo/gos", "grc", client)
        assert len(scores) > 0

    def test_unicode_and_betacode_same_scores(self, client):
        unicode_scores = lookup("λόγος", "grc", client)
        beta_scores = lookup("lo/gos", "grc", client)
        assert unicode_scores == beta_scores

    def test_greek_scores_sum_to_100(self, client):
        scores = lookup("λόγος", "grc", client)
        total = sum(scores.values())
        assert abs(total - 100.0) < 0.01

    def test_greek_scores_ordered_descending(self, client):
        scores = lookup("λόγος", "grc", client)
        values = list(scores.values())
        assert values == sorted(values, reverse=True)

    def test_greek_lemmas_are_unicode(self, client):
        # _lookup_freqs converts Greek lemmas from Beta Code to Unicode
        scores = lookup("λόγος", "grc", client)
        for lemma in scores:
            assert main._is_unicode_greek(lemma), f"Expected Unicode lemma, got: {lemma!r}"

    def test_unknown_greek_form_returns_empty(self, client):
        scores = lookup("zzzzqqqq", "grc", client)
        assert scores == {}

    def test_anthropos_returns_scores(self, client):
        scores = lookup("ἄνθρωπος", "grc", client)
        assert len(scores) > 0
