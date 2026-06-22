import tempfile

from fastapi.testclient import TestClient
from sqlmodel import Session, SQLModel, create_engine

from new_morpheus.db import engine
from new_morpheus.main import app
from new_morpheus.models import Entry, Lemma, Parse
from new_morpheus.morph import (
    form_frequency_scores,
    lookup_entries,
    lookup_parses,
    lookup_senses,
    prior_frequency_scores,
    select_winning_parse,
    word_frequency_scores,
)

client = TestClient(app)


def test_lookup_by_exact_form_finds_known_lemma():
    response = client.get("/morph", params={"word": "abeuntibus", "language": "lat"})

    assert response.status_code == 200
    body = response.json()
    assert body["word"] == "abeuntibus"
    assert body["language_code"] == "lat"
    assert [lemma["headword"] for lemma in body["lemmas"]] == ["abeo"]
    assert len(body["lemmas"][0]["parses"]) > 0


def test_lookup_falls_back_to_bare_form_when_accented_form_has_no_exact_match():
    # "enuw/" is itself Beta Code for Ἐνύω -- morph.db only stores Unicode
    # now, so the direct/bare_form/form_normalized tiers all miss, and this
    # only succeeds via lookup_parses's Beta Code fallback (which converts
    # to "ἐνύω" via beta_code.beta_code_to_greek and retries).
    response = client.get("/morph", params={"word": "enuw/", "language": "grc"})

    assert response.status_code == 200
    body = response.json()
    assert any(lemma["headword"] == "Ἐνυώ" for lemma in body["lemmas"])


def test_lookup_falls_back_to_form_normalized_for_accented_unicode_variant():
    # Same legacy fixture shape as the test above: form_unicode keeps its
    # original case/accents, so an accented variant that doesn't match it
    # exactly -- but does match once diacritics are stripped and both sides
    # are lowercased -- only matches via this third tier.
    with tempfile.NamedTemporaryFile(suffix=".db") as db_file:
        test_engine = create_engine(f"sqlite:///{db_file.name}")
        SQLModel.metadata.create_all(test_engine, tables=[Lemma.__table__, Parse.__table__])
        with Session(test_engine) as session:
            lemma = Lemma(headword="*)enuw/", language_code="grc")
            session.add(lemma)
            session.commit()
            session.add(
                Parse(
                    lemma_id=lemma.id,
                    form="ἐνύω",
                    form_normalized="ενυω",
                    bare_form="enuw",
                    dedup_key="x",
                )
            )
            session.commit()

        with Session(test_engine) as session:
            grouped = lookup_parses(session, "Ἐνύώ", "grc")
            assert [headword for headword, _ in grouped] == ["*)enuw/"]


def test_lookup_is_case_insensitive_for_non_arabic_languages():
    lower = client.get("/morph", params={"word": "abeuntibus", "language": "lat"}).json()
    upper = client.get("/morph", params={"word": "ABEUNTIBUS", "language": "lat"}).json()

    assert [lemma["headword"] for lemma in lower["lemmas"]] == [
        lemma["headword"] for lemma in upper["lemmas"]
    ]


def test_unknown_word_returns_empty_lemma_list():
    response = client.get("/morph", params={"word": "nonexistentxyz", "language": "lat"})

    assert response.status_code == 200
    assert response.json()["lemmas"] == []


def test_document_id_attaches_weighted_frequency_for_matching_lemma():
    response = client.get(
        "/morph",
        params={
            "word": "dulce",
            "language": "lat",
            "document_id": "phi0959.phi010.perseus-lat2",
        },
    )

    assert response.status_code == 200
    lemmas = response.json()["lemmas"]
    dulcis = next(lemma for lemma in lemmas if lemma["headword"] == "dulcis")
    assert dulcis["document_frequency"] == 1.0


def test_document_frequency_is_none_when_document_id_omitted():
    response = client.get("/morph", params={"word": "dulce", "language": "lat"})

    assert response.status_code == 200
    lemmas = response.json()["lemmas"]
    assert all(lemma["document_frequency"] is None for lemma in lemmas)


def test_document_frequency_is_none_for_document_with_no_recorded_frequency():
    response = client.get(
        "/morph",
        params={
            "word": "dulce",
            "language": "lat",
            "document_id": "no-such-document",
        },
    )

    assert response.status_code == 200
    lemmas = response.json()["lemmas"]
    assert all(lemma["document_frequency"] is None for lemma in lemmas)


def test_senses_are_attached_from_the_lexicon_for_the_word_s_language():
    response = client.get("/morph", params={"word": "dulce", "language": "lat"})

    assert response.status_code == 200
    lemmas = response.json()["lemmas"]
    dulcis = next(lemma for lemma in lemmas if lemma["headword"] == "dulcis")
    assert len(dulcis["senses"]) > 0
    assert all(sense["document_id"] == "lewis-short" for sense in dulcis["senses"])


def test_lookup_senses_is_empty_for_a_language_with_no_ingested_lexicon():
    with Session(engine) as session:
        assert lookup_senses(session, "xyz", "dulcis", -1) == []


def test_lookup_entries_is_empty_for_a_language_with_no_ingested_lexicon():
    with Session(engine) as session:
        assert lookup_entries(session, "xyz", "dulcis", -1) == []


def test_lookup_entries_returns_the_full_text_for_a_known_entry():
    # entries has no fixture data in morph.db yet (it's new -- see
    # clojure/src/perseus_morph/lexica/schema.clj), so this exercises the
    # query against a throwaway db of its own rather than the shared one.
    with tempfile.NamedTemporaryFile(suffix=".db") as db_file:
        test_engine = create_engine(f"sqlite:///{db_file.name}")
        SQLModel.metadata.create_all(test_engine, tables=[Entry.__table__])
        with Session(test_engine) as session:
            session.add(Entry(document_id="lsj", key="mh=nis", text="<orth>mh=nis</orth>"))
            session.commit()

        with Session(test_engine) as session:
            entries = lookup_entries(session, "grc", "mh=nis", -1)
            assert [entry.text for entry in entries] == ["<orth>mh=nis</orth>"]


def test_form_frequency_scores_returns_a_count_for_every_candidate_parse():
    with Session(engine) as session:
        grouped = lookup_parses(session, "dulce", "lat")
        scores = form_frequency_scores(session, "lat", grouped)

        parse_ids = {parse.id for parses in grouped.values() for parse in parses}
        assert scores.keys() == parse_ids
        assert all(score >= 0 for score in scores.values())


def test_word_frequency_scores_is_skipped_for_a_single_candidate_lemma():
    with Session(engine) as session:
        grouped = lookup_parses(session, "abeuntibus", "lat")
        assert len(grouped) == 1
        assert word_frequency_scores(grouped, {}) == {}


def test_prior_frequency_scores_is_empty_without_a_prior_word():
    with Session(engine) as session:
        grouped = lookup_parses(session, "dulce", "lat")
        assert prior_frequency_scores(session, "lat", grouped, {}) == {}


def test_select_winning_parse_is_none_when_no_evaluator_scored_anything():
    assert select_winning_parse({}, {}, {}) is None


def test_select_winning_parse_averages_normalized_scores_across_evaluators():
    # evaluator A normalizes to {1: 0.25, 2: 0.75}; evaluator B to
    # {1: 5/6, 2: 1/6}; parse 1's average (~0.54) beats parse 2's (~0.46).
    assert select_winning_parse({1: 1.0, 2: 3.0}, {1: 5.0, 2: 1.0}) == 1


def test_morph_response_marks_exactly_one_winning_parse_when_ambiguous():
    response = client.get("/morph", params={"word": "dulce", "language": "lat"})

    assert response.status_code == 200
    parses = [parse for lemma in response.json()["lemmas"] for parse in lemma["parses"]]
    assert sum(parse["is_winner"] for parse in parses) == 1
