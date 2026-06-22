from sqlmodel import Field, SQLModel


class Lemma(SQLModel, table=True):
    __tablename__ = "lemmas"

    id: int | None = Field(default=None, primary_key=True)
    headword: str
    bare_headword: str | None = None
    sequence_number: int = -1
    language_code: str


class Parse(SQLModel, table=True):
    __tablename__ = "parses"

    id: int | None = Field(default=None, primary_key=True)
    lemma_id: int = Field(foreign_key="lemmas.id")
    # No language_code column: it's a transitive duplicate of lemma_id ->
    # Lemma.language_code (see clojure/src/perseus_morph/loader/schema.clj).
    form: str
    form_normalized: str | None = None
    expanded_form: str | None = None
    bare_form: str | None = None
    part_of_speech: str | None = None
    person: str | None = None
    number: str | None = None
    tense: str | None = None
    mood: str | None = None
    voice: str | None = None
    gender: str | None = None
    grammatical_case: str | None = None
    degree: str | None = None
    dialect: str | None = None
    other: str | None = None
    prefix: str | None = None
    object: str | None = None
    definite: str | None = None
    possessive: str | None = None
    dedup_key: str


class Sense(SQLModel, table=True):
    __tablename__ = "senses"

    id: int | None = Field(default=None, primary_key=True)
    # Strings, not ints: lettered ids (e.g. "14773a", "9b") distinguish
    # homonym entries and lettered sub-senses. See
    # clojure/src/perseus_morph/lexica/core.clj.
    entry_id: str = "-1"
    sense_id: str = "-1"
    document_id: str
    # Despite the name, this holds "entry=" + the lexicon entry's `key`
    # attribute, not a headword. See clojure/src/perseus_morph/lexica/schema.clj.
    lemma: str
    sense: str | None = None
    level: int | None = None
    definition: str | None = None


class Entry(SQLModel, table=True):
    __tablename__ = "entries"

    # No surrogate id column: (document_id, key) is already the entry's
    # natural key (see clojure/src/perseus_morph/lexica/schema.clj).
    document_id: str = Field(primary_key=True)
    key: str = Field(primary_key=True)
    text: str | None = None


class DocumentFrequency(SQLModel, table=True):
    __tablename__ = "document_frequencies"

    document_id: str = Field(primary_key=True)
    # No headword/sequence_number/language_code columns: that's the lemma's
    # natural key, already captured by lemma_id (see
    # clojure/src/perseus_morph/frequencies/document.clj).
    lemma_id: int = Field(foreign_key="lemmas.id", primary_key=True)
    weighted_frequency: float = 0
