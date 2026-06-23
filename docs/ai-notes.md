# Notes on AI Use

AI—specifically, Claude Code and DeepSeek (through OpenCode)—were used
to resuscitate the parts of Hopper's Morpheus found here. In particular,
Claude Code was able to identify opportunities for reuse and then refactor
of Hopper's Java code in the Clojure backend, which builds the SQLite
database from which the Python frontend reads. Claude and DeepSeek both
contributed to porting the scoring functionality from Hopper's Java to
the Python found in `src/new_morpheus/morph.py`.

Part of the challenge of working with AI on a project like this is that
the models default to wanting to implement every feature from scratch.
But when we are working on reviving decades-old functionality, we want
to be able to reuse as much as possible. With guidance, Claude Code worked
through the dependency graph of Hopper and identified opportunities for
reuse, such as the Beta Code to Unicode library. Claude Code also correctly
identified when modules were so interconnected with features that we did
not want to implement—such as Hopper's Morpheus being closely coupled to
rendering—that we were better off porting the logic rather than reusing
the code wholesale.

Once the basic functionality had been ported to Clojure, Claude Code
needed to be guided to reorganize the repo in a way that would make sense
to human developers. For Claude, repetition is cheap, so functions tend
to be repeated verbatim across namespaces. With prompting, however, Claude
recognized this repetition and refactored the codebase into the small
footprint that currently builds the SQLite database.

The time saved with Claude Code meant that we had extra time to migrate
the morphology lists to Unicode and to refactor the the code that reads
from and ingests them. This change finally cleared Beta Code from the
entire Perseus codebase—Beta Code is still accepted as user input, but all
non-Latin characters are now encoded in Unicode instead.
