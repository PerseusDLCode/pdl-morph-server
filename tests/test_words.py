"""
Content tests for common Latin and Ancient Greek words.

These tests verify that the API returns correct morphological analyses
for well-known forms, checking POS, lemma, and grammatical features
against expected Morpheus data.
"""


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def analyze(client, form, lang):
    r = client.get("/analyze", params={"form": form, "lang": lang})
    assert r.status_code == 200
    return r.json()["analyses"]


def lemmas(analyses):
    return [a["lemma"] for a in analyses]


def any_lemma(analyses, prefix):
    return any(lem.startswith(prefix) for lem in lemmas(analyses))


# ---------------------------------------------------------------------------
# Common Latin words
# ---------------------------------------------------------------------------

class TestLatinWords:
    def test_amor_has_analyses(self, client):
        assert len(analyze(client, "amor", "la")) > 0

    def test_amor_lemma(self, client):
        assert any_lemma(analyze(client, "amor", "la"), "amor")

    def test_amor_is_noun(self, client):
        analyses = analyze(client, "amor", "la")
        assert any(a.get("pos") == "noun" for a in analyses)

    def test_amor_nominative_singular(self, client):
        # Morpheus uses abbreviated values: "nom" and "sg"
        analyses = analyze(client, "amor", "la")
        matches = [a for a in analyses if a.get("case") == "nom" and a.get("number") == "sg"]
        assert len(matches) > 0

    def test_est_has_analyses(self, client):
        assert len(analyze(client, "est", "la")) > 0

    def test_est_lemma_is_sum(self, client):
        assert any_lemma(analyze(client, "est", "la"), "sum")

    def test_est_is_verb(self, client):
        analyses = analyze(client, "est", "la")
        assert any(a.get("pos") == "verb" for a in analyses)

    def test_est_third_person_singular_present(self, client):
        # Morpheus abbreviations: number="sg", tense="pres", mood="ind"
        analyses = analyze(client, "est", "la")
        matches = [
            a for a in analyses
            if a.get("person") == "3rd"
            and a.get("number") == "sg"
            and a.get("tense") == "pres"
        ]
        assert len(matches) > 0

    def test_puella_has_analyses(self, client):
        assert len(analyze(client, "puella", "la")) > 0

    def test_puella_lemma(self, client):
        assert any_lemma(analyze(client, "puella", "la"), "puella")

    def test_puella_is_feminine(self, client):
        # Morpheus abbreviation: "fem"
        analyses = analyze(client, "puella", "la")
        assert any(a.get("gender") == "fem" for a in analyses)

    def test_puella_has_multiple_analyses(self, client):
        # nominative, vocative, and ablative singular are all "puella"
        assert len(analyze(client, "puella", "la")) >= 2

    def test_rex_has_analyses(self, client):
        assert len(analyze(client, "rex", "la")) > 0

    def test_rex_lemma(self, client):
        assert any_lemma(analyze(client, "rex", "la"), "rex")

    def test_laudat_has_analyses(self, client):
        assert len(analyze(client, "laudat", "la")) > 0

    def test_laudat_lemma_is_laudo(self, client):
        assert any_lemma(analyze(client, "laudat", "la"), "laud")


# ---------------------------------------------------------------------------
# Common Ancient Greek words
# ---------------------------------------------------------------------------

class TestGreekWords:
    def test_logos_unicode_has_analyses(self, client):
        assert len(analyze(client, "λόγος", "grc")) > 0

    def test_logos_betacode_has_analyses(self, client):
        assert len(analyze(client, "lo/gos", "grc")) > 0

    def test_logos_unicode_betacode_identical(self, client):
        unicode_a = analyze(client, "λόγος", "grc")
        beta_a = analyze(client, "lo/gos", "grc")
        assert unicode_a == beta_a

    def test_logos_lemma(self, client):
        lems = lemmas(analyze(client, "lo/gos", "grc"))
        assert any("lo/gos" in lem or "lo/g" in lem for lem in lems)

    def test_logos_nominative_singular(self, client):
        # Morpheus abbreviations: case="nom", number="sg"
        analyses = analyze(client, "λόγος", "grc")
        matches = [a for a in analyses if a.get("case") == "nom" and a.get("number") == "sg"]
        assert len(matches) > 0

    def test_anthropos_unicode_has_analyses(self, client):
        assert len(analyze(client, "ἄνθρωπος", "grc")) > 0

    def test_anthropos_betacode_has_analyses(self, client):
        assert len(analyze(client, "a)/nqrwpos", "grc")) > 0

    def test_anthropos_unicode_betacode_identical(self, client):
        unicode_a = analyze(client, "ἄνθρωπος", "grc")
        beta_a = analyze(client, "a)/nqrwpos", "grc")
        assert unicode_a == beta_a

    def test_anthropos_is_noun(self, client):
        analyses = analyze(client, "ἄνθρωπος", "grc")
        assert any(a.get("pos") == "noun" for a in analyses)

    def test_esti_has_analyses(self, client):
        # e)sti/ — 3rd sg present of εἰμί (to be), Beta Code
        assert len(analyze(client, "e)sti/", "grc")) > 0

    def test_esti_lemma_is_eimi(self, client):
        lems = lemmas(analyze(client, "e)sti/", "grc"))
        # lemma for εἰμί in Morpheus is "ei)mi/"
        assert any("ei)mi" in lem for lem in lems)

    def test_polis_has_analyses(self, client):
        # po/lis — city/state, a very common Greek noun
        assert len(analyze(client, "po/lis", "grc")) > 0

    def test_polis_is_noun(self, client):
        analyses = analyze(client, "po/lis", "grc")
        assert any(a.get("pos") == "noun" for a in analyses)
