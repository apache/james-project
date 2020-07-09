#!/bin/bash
#
# https://matthewsetter.com/technical-documentation/asciidoc/convert-markdown-to-asciidoc-with-kramdoc/
#

echo "Migrate site and mailet"

mkdir -p docs/modules/migrated/pages
find ./src/site/markdown -name "*.md" -type f -exec sh -c \
    'echo "Convert {}" ; kramdoc --format=GFM --wrap=ventilate --output=./docs/modules/migrated/pages/{}.adoc {}' \;

echo "Migrate ADR"
mkdir -p docs/modules/development/pages/adr
find ./src/adr -name "*.md" -type f -exec sh -c \
    'echo "Convert {}" ; kramdoc --format=GFM --wrap=ventilate --output=./docs/modules/development/pages/adr/{}.adoc {}' \;
