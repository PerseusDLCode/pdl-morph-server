import json

from pathlib import Path

import beta_code
from lxml import etree

MORPH_XML = Path(__file__).parent.parent / "data" / "greek.morph.xml"
OUT = Path(__file__).parent.parent / "data" / "greek.morph.jsonl"


def main():
    tree = etree.parse(MORPH_XML)

    with OUT.open("w", encoding="utf-8") as f:
        for analysis in tree.iterfind(".//analysis"):
            form = analysis.find("form")
            lemma = analysis.find("lemma")

            form.text = beta_code.beta_code_to_greek(form.text)
            lemma.text = beta_code.beta_code_to_greek(lemma.text)

            entry = {}
            for child in analysis:
                entry[child.tag] = child.text

            print(json.dumps(entry, ensure_ascii=False), file=f)

if __name__ == "__main__":
    main()
