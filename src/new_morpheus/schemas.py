from typing import Any

from pydantic import BaseModel, field_validator


class ParseOut(BaseModel):
    model_config = {"from_attributes": True}

    form: str
    expanded_form: str | None
    part_of_speech: str | None
    person: str | None
    number: str | None
    tense: str | None
    mood: str | None
    voice: str | None
    gender: str | None
    grammatical_case: str | None
    degree: str | None
    dialect: str | None
    other: str | None
    prefix: str | None
    object: str | None
    definite: str | None
    possessive: str | None
    is_winner: bool = False


class SenseOut(BaseModel):
    model_config = {"from_attributes": True}

    document_id: str
    sense: str | None
    level: int | None
    definition: Any = None

    @field_validator("level")
    @classmethod
    def no_level_sentinel(cls, level: int | None) -> int | None:
        # Clojure writes -1 (not NULL) when a <sense> has no level attribute.
        return None if level == -1 else level


class EntryOut(BaseModel):
    model_config = {"from_attributes": True}

    document_id: str
    text: Any = None


class LemmaResult(BaseModel):
    headword: str
    sequence_number: int
    parses: list[ParseOut]
    senses: list[SenseOut] = []
    entries: list[EntryOut] = []
    document_frequency: float | None = None


class MorphResponse(BaseModel):
    word: str
    language_code: str
    lemmas: list[LemmaResult]
