import sqlite3
from collections.abc import Generator
from pathlib import Path

from sqlmodel import Session, create_engine

DB_PATH = Path(__file__).resolve().parents[2] / "clojure" / "morph.db"


def _connect() -> sqlite3.Connection:
    # morph.db is rebuilt offline by the Clojure ingestion pipeline and then
    # served read-only, so there's never a concurrent writer to lock against.
    return sqlite3.connect(f"file:{DB_PATH}?mode=ro", uri=True)


engine = create_engine("sqlite://", creator=_connect)


def get_session() -> Generator[Session, None, None]:
    with Session(engine) as session:
        yield session
