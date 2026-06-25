import json

from pathlib import Path

import beta_code
from lxml import etree

MIDDLE_LIDDELL = Path(__file__).parent.parent / "data" / "viaf66541464.001.perseus-eng1.xml"
OUT = Path(__file__).parent.parent / "data" / "viaf66541464.001.perseus-eng1.xml"


def main():
    tree = etree.parse(MIDDLE_LIDDELL)

    seen = {}

    for entry in tree.iterfind(".//entry"):
        # entry.attrib["key"] = beta_code.beta_code_to_greek(entry.attrib["key"])

        key = entry.attrib["key"]

        if key in seen:
            seen[key] += 1
            key = f"{key}{seen[key]}"
            entry.attrib["key"] = key
        else:
            seen[key] = 1

    with OUT.open("w", encoding="utf-8") as f:
        f.write(etree.tostring(tree, encoding="unicode", pretty_print=True))



if __name__ == "__main__":
    main()
