"""Start the Perseus Morphology API server.

Usage:
    python run.py              # default port 8000
    python run.py 9000         # custom port
    python run.py --port 9000  # explicit flag
"""

import argparse
import sys
from pathlib import Path

# add it to sys.path directly so `import main` resolves.
sys.path.insert(0, str(Path(__file__).parent / "src"))

import uvicorn

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Run the Perseus Morphology API")
    parser.add_argument("port", nargs="?", type=int, default=8000, help="Port to listen on (default: 8000)")
    parser.add_argument("--port", dest="port_flag", type=int, help="Port to listen on")
    args = parser.parse_args()

    port = args.port_flag if args.port_flag is not None else args.port

    from main import app  # type: ignore[import]
    uvicorn.run(app, host="0.0.0.0", port=port)
