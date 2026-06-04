"""
Shared fixtures for the pdl-morph-server test suite.

The `client` fixture starts the full FastAPI app including the lifespan
(which loads both XML morphology indexes into memory). This takes 30-60
seconds the first time and is cached for the entire pytest session.

All data files (*.pkl, *.json, *.morph.xml) must be present in
pdl-morph-server/ before running the integration tests. Run:
  python tools/setup_morph_data.py
  python pdl-morph-server/build_indexes.py
"""

import pytest
from fastapi.testclient import TestClient


@pytest.fixture(scope="session")
def client():
    from main import app
    with TestClient(app) as c:
        yield c
