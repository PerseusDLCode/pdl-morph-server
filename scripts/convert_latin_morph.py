import json

from pathlib import Path

import beta_code
from lxml import etree

MORPH_XML = Path(__file__).parent.parent / "data" / "latin.morph.xml"
OUT = Path(__file__).parent.parent / "data" / "latin.morph.jsonl"


def main():
    tree = etree.parse(MORPH_XML)

    with OUT.open("w", encoding="utf-8") as f:
        for analysis in tree.iterfind(".//analysis"):
            entry = {}
            for child in analysis:
                entry[child.tag] = child.text

            print(json.dumps(entry, ensure_ascii=False), file=f)

if __name__ == "__main__":
    main()
