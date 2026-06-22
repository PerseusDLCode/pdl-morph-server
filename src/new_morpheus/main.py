import os

import uvicorn
from fastapi import Depends, FastAPI
from sqlmodel import Session

from .db import get_session
from .morph import (
    document_frequency,
    form_frequency_scores,
    lookup_entries,
    lookup_parses,
    lookup_senses,
    prior_frequency_scores,
    select_winning_parse,
    word_frequency_scores,
)
from .schemas import EntryOut, LemmaResult, MorphResponse, ParseOut, SenseOut

app = FastAPI(title="new-morpheus")


@app.get("/morph", response_model=MorphResponse)
def morph(
    word: str,
    language: str = "grc",
    document_id: str | None = None,
    prior_word: str | None = None,
    session: Session = Depends(get_session),
) -> MorphResponse:
    grouped = lookup_parses(session, word, language)

    document_frequencies = {
        key: document_frequency(session, language, document_id, *key)
        if document_id
        else None
        for key in grouped
    }
    prior_grouped = lookup_parses(session, prior_word, language) if prior_word else {}

    winner_id = select_winning_parse(
        word_frequency_scores(grouped, document_frequencies),
        form_frequency_scores(session, language, grouped),
        prior_frequency_scores(session, language, grouped, prior_grouped),
    )

    # FIXME: We're using Giuseppe Celano's Unicode LSJ, so we need
    # to look up senses and entries—but not document_frequency—
    # with the Unicode form of the headword. We should fix this
    # by standardizing on Unicode everywhere.
    lemmas = [
        LemmaResult(
            headword=headword,
            sequence_number=sequence_number,
            parses=[
                ParseOut.model_validate(parse).model_copy(
                    update={"is_winner": parse.id == winner_id}
                )
                for parse in parses
            ],
            senses=[
                SenseOut.model_validate(sense)
                for sense in lookup_senses(
                    session,
                    language,
                    headword,
                    sequence_number,
                )
            ],
            entries=[
                EntryOut.model_validate(entry)
                for entry in lookup_entries(
                    session,
                    language,
                    headword,
                    sequence_number,
                )
            ],
            document_frequency=document_frequencies[(headword, sequence_number)],
        )
        for (headword, sequence_number), parses in grouped.items()
    ]

    return MorphResponse(word=word, language_code=language, lemmas=lemmas)


def dev() -> None:
    """Entry point for `uv run new-morpheus-dev`: autoreloading local server."""
    uvicorn.run(
        "new_morpheus.main:app",
        host="127.0.0.1",
        port=int(os.environ.get("PORT", 8000)),
        reload=True,
    )


def serve() -> None:
    """Entry point for `uv run new-morpheus`: production server, no reload."""
    uvicorn.run(
        "new_morpheus.main:app",
        host="0.0.0.0",
        port=int(os.environ.get("PORT", 8000)),
        workers=int(os.environ.get("WEB_CONCURRENCY", 4)),
    )
