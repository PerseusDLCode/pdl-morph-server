"""
API validity tests for the Perseus Morphology API.

Covers endpoint contracts: correct status codes, response shapes,
error handling, and content-type headers. Word-level content is in
test_words.py.
"""


class TestAnalyzeEndpoint:
    def test_valid_latin_returns_200(self, client):
        r = client.get("/analyze", params={"form": "amor", "lang": "la"})
        assert r.status_code == 200

    def test_valid_greek_unicode_returns_200(self, client):
        r = client.get("/analyze", params={"form": "λόγος", "lang": "grc"})
        assert r.status_code == 200

    def test_valid_greek_betacode_returns_200(self, client):
        r = client.get("/analyze", params={"form": "lo/gos", "lang": "grc"})
        assert r.status_code == 200

    def test_response_shape(self, client):
        r = client.get("/analyze", params={"form": "amor", "lang": "la"})
        body = r.json()
        assert "form" in body
        assert "language" in body
        assert "analyses" in body
        assert isinstance(body["analyses"], list)

    def test_response_echoes_form(self, client):
        r = client.get("/analyze", params={"form": "amor", "lang": "la"})
        assert r.json()["form"] == "amor"

    def test_response_echoes_language(self, client):
        r = client.get("/analyze", params={"form": "amor", "lang": "la"})
        assert r.json()["language"] == "la"

    def test_analysis_has_lemma(self, client):
        r = client.get("/analyze", params={"form": "amor", "lang": "la"})
        analyses = r.json()["analyses"]
        assert len(analyses) > 0
        assert all("lemma" in a for a in analyses)

    def test_unsupported_language_returns_400(self, client):
        r = client.get("/analyze", params={"form": "amor", "lang": "xx"})
        assert r.status_code == 400

    def test_missing_form_returns_422(self, client):
        r = client.get("/analyze", params={"lang": "la"})
        assert r.status_code == 422

    def test_missing_lang_returns_422(self, client):
        r = client.get("/analyze", params={"form": "amor"})
        assert r.status_code == 422

    def test_unknown_word_returns_empty_analyses(self, client):
        r = client.get("/analyze", params={"form": "zzzzqqqq", "lang": "la"})
        assert r.status_code == 200
        assert r.json()["analyses"] == []

    def test_unicode_and_betacode_same_analyses(self, client):
        unicode_r = client.get("/analyze", params={"form": "λόγος", "lang": "grc"})
        beta_r = client.get("/analyze", params={"form": "lo/gos", "lang": "grc"})
        assert unicode_r.json()["analyses"] == beta_r.json()["analyses"]

    def test_content_type_is_json(self, client):
        r = client.get("/analyze", params={"form": "amor", "lang": "la"})
        assert "application/json" in r.headers["content-type"]


class TestMorphEndpoint:
    def test_valid_latin_returns_200(self, client):
        r = client.get("/morph", params={"form": "amor", "lang": "la"})
        assert r.status_code == 200

    def test_valid_greek_unicode_returns_200(self, client):
        r = client.get("/morph", params={"form": "λόγος", "lang": "grc"})
        assert r.status_code == 200

    def test_response_is_html(self, client):
        r = client.get("/morph", params={"form": "amor", "lang": "la"})
        assert "text/html" in r.headers["content-type"]

    def test_html_contains_word(self, client):
        r = client.get("/morph", params={"form": "amor", "lang": "la"})
        assert "amor" in r.text

    def test_html_contains_language_name(self, client):
        r = client.get("/morph", params={"form": "amor", "lang": "la"})
        assert "Latin" in r.text

    def test_unsupported_language_returns_400(self, client):
        r = client.get("/morph", params={"form": "amor", "lang": "xx"})
        assert r.status_code == 400

    def test_missing_form_returns_422(self, client):
        r = client.get("/morph", params={"lang": "la"})
        assert r.status_code == 422

    def test_unknown_word_shows_no_analyses_message(self, client):
        r = client.get("/morph", params={"form": "zzzzqqqq", "lang": "la"})
        assert r.status_code == 200
        assert "No analyses found" in r.text

    def test_html_is_valid_doctype(self, client):
        r = client.get("/morph", params={"form": "amor", "lang": "la"})
        assert r.text.startswith("<!DOCTYPE html>")
