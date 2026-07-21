#!/bin/sh
#
# Regenerates the .png diagrams in this directory from their Mermaid (.mmd) sources.
#
#   ./docs/diagrams/generate.sh
#
# Requires Node.js. Renders via the Mermaid CLI fetched through npx; the first run
# also downloads the headless Chrome build that mermaid-cli renders with (both are
# cached for subsequent runs).

set -eu
cd "$(dirname "$0")"

for source in *.mmd; do
    npx --yes "@mermaid-js/mermaid-cli@11" \
        --input "$source" \
        --output "${source%.mmd}.png" \
        --backgroundColor white \
        --scale 2
done
