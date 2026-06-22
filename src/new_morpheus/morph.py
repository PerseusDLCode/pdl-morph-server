import beta_code
from sqlalchemy import func, text
from sqlmodel import Session, select

from .language import bare_form, normalize_form, normalize_unicode
from .models import DocumentFrequency, Entry, Lemma, Parse, Sense

LemmaKey = tuple[str, int]

# Lexica ingested into morph.db, by language (see
# clojure/src/perseus_morph/lexica/ingest.clj). A language can have more
# than one; add to its list as more lexica are ingested (e.g. Middle
# Liddell for "grc").
LEXICA_BY_LANGUAGE = {"grc": ["lsj"], "lat": ["lewis-short"]}

# Stable column order for folding a parse's morphological features into a
# single key, matching perseus-morph.features/feature-columns (and so
# morph_frequencies/prior_frequencies.feature_key, which were written using
# that same fold) -- see clojure/src/perseus_morph/features.clj.
FEATURE_COLUMNS = [
    "definite",
    "degree",
    "dialect",
    "gender",
    "grammatical_case",
    "mood",
    "number",
    "object",
    "other",
    "part_of_speech",
    "person",
    "possessive",
    "prefix",
    "tense",
    "voice",
]


def _group_by_lemma(rows: list[tuple[Parse, Lemma]]) -> dict[LemmaKey, list[Parse]]:
    grouped: dict[LemmaKey, list[Parse]] = {}
    for parse, lemma in rows:
        grouped.setdefault((lemma.headword, lemma.sequence_number), []).append(parse)
    return grouped


def _parses_matching(
    session: Session, language_code: str, column: str, value: str
) -> list[tuple[Parse, Lemma]]:
    statement = (
        select(Parse, Lemma)
        .join(Lemma, Parse.lemma_id == Lemma.id)
        .where(getattr(Parse, column) == value, Lemma.language_code == language_code)
    )
    return list(session.exec(statement))


def _parses_matching_unicode_form(
    session: Session, language_code: str, value: str
) -> list[tuple[Parse, Lemma]]:
    statement = (
        select(Parse, Lemma)
        .join(Lemma, Parse.lemma_id == Lemma.id)
        .where(
            Parse.form == value,
            Lemma.language_code == language_code,
        )
    )
    return list(session.exec(statement))


def _lookup_parses_for_word(
    session: Session, language_code: str, word: str
) -> list[tuple[Parse, Lemma]]:
    """The three-tier Unicode lookup mirroring MorphController's lookup: try
    `word` as typed (normalized for case) against form, then
    fall back to bare_form with diacritics stripped (matching
    MorphController's second getParses() call), and beyond that to
    form_normalized, so accented/cased Unicode Greek input matches too,
    mirroring how lemmas.headword_normalized supports Unicode lemma
    lookups."""
    normalized = normalize_form(language_code, word)
    rows = _parses_matching_unicode_form(session, language_code, normalized)
    if not rows:
        rows = _parses_matching(session, language_code, "bare_form", bare_form(word))
    if not rows:
        rows = _parses_matching(
            session, language_code, "form_normalized", normalize_unicode(word)
        )
    return rows


def lookup_parses(
    session: Session, word: str, language_code: str
) -> dict[LemmaKey, list[Parse]]:
    """Looks up `word`'s candidate parses (see _lookup_parses_for_word).
    morph.db only stores Unicode now, but some callers (old clients,
    copy-pasted Perseus URLs, ...) may still send Greek as Beta Code -- if
    the direct lookup comes up empty, retry once with `word` converted from
    Beta Code to Unicode via the `beta_code` package (the same library
    scripts/convert_morph_to_unicode.py used to build
    greek.morph.unicode.xml). beta_code_to_greek leaves genuine Unicode
    input unchanged, so this is skipped rather than wasting a duplicate
    lookup when `word` wasn't Beta Code to begin with. Only attempted for
    Greek -- beta_code_to_greek blindly transliterates any ASCII letters,
    so running it for Latin/Arabic input would risk a bogus match instead
    of correctly coming up empty."""
    rows = _lookup_parses_for_word(session, language_code, word)
    if not rows and language_code == "grc":
        converted = beta_code.beta_code_to_greek(word)
        if converted != word:
            rows = _lookup_parses_for_word(session, language_code, converted)
    return _group_by_lemma(rows)


def document_frequency(
    session: Session,
    language_code: str,
    document_id: str,
    headword: str,
    sequence_number: int,
) -> float | None:
    lemma = session.exec(
        select(Lemma).where(
            Lemma.language_code == language_code,
            Lemma.headword == headword,
            Lemma.sequence_number == sequence_number,
        )
    ).first()
    if lemma is None:
        return None
    statement = select(DocumentFrequency).where(
        DocumentFrequency.document_id == document_id,
        DocumentFrequency.lemma_id == lemma.id,
    )
    row = session.exec(statement).first()
    return row.weighted_frequency if row else None


def lookup_senses(
    session: Session, language_code: str, headword: str, sequence_number: int
) -> list[Sense]:
    """Senses are keyed by "entry=" + the lexicon entry's `key` attribute,
    which equals headword + sequence_number (omitted when -1) -- see
    clojure/src/perseus_morph/lexica/core.clj. Queries every lexicon
    ingested for `language_code`, since a lemma can have entries in more
    than one (e.g. both LSJ and Middle Liddell for Greek)."""
    document_ids = LEXICA_BY_LANGUAGE.get(language_code, [])
    if not document_ids:
        return []

    key = headword if sequence_number == -1 else f"{headword}{sequence_number}"
    statement = select(Sense).where(
        Sense.document_id.in_(document_ids), Sense.lemma == f"entry={key}"
    )
    return list(session.exec(statement))


def lookup_entries(
    session: Session, language_code: str, headword: str, sequence_number: int
) -> list[Entry]:
    """Entries are keyed by the lexicon entry's `key` attribute directly
    (unlike senses.lemma, entries.key has no "entry=" prefix -- see
    clojure/src/perseus_morph/lexica/schema.clj), equal to headword +
    sequence_number (omitted when -1). Queries every lexicon ingested for
    `language_code`, same as lookup_senses."""
    document_ids = LEXICA_BY_LANGUAGE.get(language_code, [])
    if not document_ids:
        return []

    key = headword if sequence_number == -1 else f"{headword}{sequence_number}"
    statement = select(Entry).where(
        Entry.document_id.in_(document_ids), Entry.key == key
    )
    return list(session.exec(statement))


def _parse_features(parse: Parse) -> dict[str, str]:
    return {
        col: value
        for col in FEATURE_COLUMNS
        if (value := getattr(parse, col)) is not None
    }


def _feature_key(features: dict[str, str]) -> str:
    """Same NOT-NULL fold as perseus-morph.features/fold-key: every column,
    in FEATURE_COLUMNS order, missing ones folding to the empty string."""
    return "".join(features.get(col, "") for col in FEATURE_COLUMNS)


def _flatten(lemma_groups: dict[LemmaKey, list[Parse]]) -> list[Parse]:
    return [parse for parses in lemma_groups.values() for parse in parses]


def _exclusive_features(
    other_features: list[dict[str, str]], current_features: dict[str, str]
) -> dict[str, str]:
    """Mirrors FormFrequencyEvaluator.findSpecialFeatures: the subset of
    `current_features` not shared, value-for-value, by every other
    candidate parse -- e.g. among "1st sg imperf ind act" / "3rd pl imperf
    ind act" / "3rd sg imperf ind act", each parse's exclusive features are
    just its person+number."""
    return {
        feature: value
        for feature, value in current_features.items()
        if not all(other.get(feature) == value for other in other_features)
    }


def form_frequency_scores(
    session: Session, language_code: str, lemma_groups: dict[LemmaKey, list[Parse]]
) -> dict[int, float]:
    """Mirrors FormFrequencyEvaluator: scores each candidate parse by how
    often its features (or, when ambiguous, just the features that set it
    apart from its sibling candidates) occur corpus-wide, via the
    morph_frequencies table that perseus-morph.frequencies.aggregator
    precomputes for every feature subset (see all-submaps), so this only
    ever needs one exact feature_key lookup per parse."""
    parses = _flatten(lemma_groups)
    features_by_id = {parse.id: _parse_features(parse) for parse in parses}
    scores: dict[int, float] = {}
    for parse in parses:
        current = features_by_id[parse.id]
        others = [features_by_id[other.id] for other in parses if other.id != parse.id]
        exclusive = _exclusive_features(others, current) or current
        row = session.exec(
            text(
                "SELECT count FROM morph_frequencies "
                "WHERE language_code = :language_code AND feature_key = :feature_key"
            ),
            params={
                "language_code": language_code,
                "feature_key": _feature_key(exclusive),
            },
        ).first()
        scores[parse.id] = row[0] if row else 0.0
    return scores


def prior_frequency_scores(
    session: Session,
    language_code: str,
    lemma_groups: dict[LemmaKey, list[Parse]],
    prior_lemma_groups: dict[LemmaKey, list[Parse]],
) -> dict[int, float]:
    """Mirrors PriorFrequencyEvaluator, simplified to drop its
    indeclinable-prior-word fallback (matching the preceding word's lemma
    rather than its morph code): prior_frequencies only stores feature
    keys, not lemmas (see clojure/src/perseus_morph/frequencies/schema.clj),
    so there is no lemma column left to fall back to here. Scores each
    candidate parse by the corpus-wide count of it following any of the
    preceding word's own candidate parses."""
    if not prior_lemma_groups:
        return {}

    parses = _flatten(lemma_groups)
    previous_keys = sorted(
        {_feature_key(_parse_features(parse)) for parse in _flatten(prior_lemma_groups)}
    )
    placeholders = ", ".join(f":previous_{i}" for i in range(len(previous_keys)))
    previous_params = {f"previous_{i}": key for i, key in enumerate(previous_keys)}

    scores: dict[int, float] = {}
    for parse in parses:
        current_key = _feature_key(_parse_features(parse))
        row = session.exec(
            text(
                "SELECT SUM(count) FROM prior_frequencies "
                "WHERE language_code = :language_code "
                "AND current_feature_key = :current_feature_key "
                f"AND previous_feature_key IN ({placeholders})"
            ),
            params={
                "language_code": language_code,
                "current_feature_key": current_key,
                **previous_params,
            },
        ).first()
        scores[parse.id] = (row[0] or 0.0) if row else 0.0
    return scores


def word_frequency_scores(
    lemma_groups: dict[LemmaKey, list[Parse]],
    document_frequencies: dict[LemmaKey, float | None],
) -> dict[int, float]:
    """Mirrors WordFrequencyEvaluator (via LexicalParseEvaluator, hence the
    same "skip unless there's more than one candidate lemma" guard as
    LexicalParseEvaluator.evaluateParses): broadcasts each lemma's
    document_frequencies score to every one of its candidate parses."""
    if len(lemma_groups) < 2:
        return {}

    scores: dict[int, float] = {}
    for key, parses in lemma_groups.items():
        frequency = document_frequencies.get(key) or 0.0
        for parse in parses:
            scores[parse.id] = frequency
    return scores


def _normalize(scores: dict[int, float]) -> dict[int, float]:
    """Mirrors ParseEvaluator.normalizeScores: each score divided by the
    sum of all of its evaluator's scores, so evaluators on different scales
    (raw corpus counts vs. weighted document frequencies) contribute
    comparably to the combined average."""
    total = sum(scores.values())
    if total == 0.0:
        return {parse_id: 0.0 for parse_id in scores}
    return {parse_id: score / total for parse_id, score in scores.items()}


def select_winning_parse(*score_maps: dict[int, float]) -> int | None:
    """Mirrors ParseSelector.ParseVotingResults.updateTotal with voting
    excluded: averages each parse's normalized scores across whichever
    evaluators actually scored it, and returns the id of the highest-
    averaging parse -- or None if no evaluator (e.g. no document_id, no
    prior_word, and only one unambiguous candidate) scored anything."""
    totals: dict[int, float] = {}
    counts: dict[int, int] = {}
    for scores in score_maps:
        if not scores:
            continue
        for parse_id, score in _normalize(scores).items():
            totals[parse_id] = totals.get(parse_id, 0.0) + score
            counts[parse_id] = counts.get(parse_id, 0) + 1

    if not totals:
        return None

    averages = {parse_id: totals[parse_id] / counts[parse_id] for parse_id in totals}
    return max(averages, key=averages.get)
