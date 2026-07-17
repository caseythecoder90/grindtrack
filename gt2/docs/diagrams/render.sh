#!/usr/bin/env bash
# Render every *.puml in this folder to *.svg using the dockerized PlantUML
# (bundles Graphviz, so nothing needs installing locally besides Docker).
#
# The generated *.svg files are committed so they render inline on GitHub —
# GitHub does not render PlantUML source, only the resulting image. Edit the
# *.puml source, re-run this, and commit both.
#
#   ./render.sh
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
docker run --rm -v "${here}:/data" plantuml/plantuml -tsvg /data/*.puml
echo "Rendered:"
ls -1 "${here}"/*.svg
