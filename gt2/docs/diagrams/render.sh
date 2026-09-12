#!/usr/bin/env bash
# Render every *.puml in this folder to *.svg, and delete SVGs whose .puml is gone.
#
# The generated *.svg files are committed so they render inline on GitHub — GitHub does not
# render PlantUML source, only the resulting image. Edit the *.puml source, re-run this, and
# commit both.
#
#   ./render.sh
#
# Two backends, tried in order:
#   1. A local plantuml.jar + any JRE. Cached under ~/.cache/plantuml and downloaded once.
#      Uses PlantUML's built-in Smetana layout engine, so Graphviz is NOT required.
#   2. The dockerized PlantUML (bundles Graphviz), if Docker is running and no JRE is around.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cache="${PLANTUML_CACHE:-$HOME/.cache/plantuml}"
jar="${PLANTUML_JAR:-$cache/plantuml.jar}"
version="1.2025.4"
url="https://github.com/plantuml/plantuml/releases/download/v${version}/plantuml-${version}.jar"

shopt -s nullglob
pumls=("$here"/*.puml)
if [ ${#pumls[@]} -eq 0 ]; then
  echo "No .puml files in $here" >&2
  exit 1
fi

# --- 1. Delete SVGs that no longer have a source ------------------------------------------
# A renamed or removed diagram otherwise leaves a stale image behind that still looks current
# in the docs, which is worse than no image at all.
for svg in "$here"/*.svg; do
  base="$(basename "${svg%.svg}")"
  if [ ! -f "$here/$base.puml" ]; then
    echo "Removing stale: $(basename "$svg")  (no $base.puml)"
    rm -f "$svg"
  fi
done

# --- 2. Render ----------------------------------------------------------------------------
if command -v java >/dev/null 2>&1; then
  if [ ! -f "$jar" ]; then
    echo "Fetching plantuml ${version} → $jar"
    mkdir -p "$(dirname "$jar")"
    curl -fsSL -o "$jar" "$url"
  fi
  # Graphviz when it is installed (nicer orthogonal lines); otherwise PlantUML's built-in
  # Smetana layout, pure Java. The old -Smetana flag stopped selecting it in 1.2025.x and
  # silently produced "Dot Executable not found" images instead.
  if command -v dot >/dev/null 2>&1; then
    java -jar "$jar" -tsvg -nbthread auto "$here"/*.puml
  else
    java -jar "$jar" -tsvg -Playout=smetana -nbthread auto "$here"/*.puml
  fi
elif docker info >/dev/null 2>&1; then
  docker run --rm -v "${here}:/data" plantuml/plantuml -tsvg /data/*.puml
else
  echo "Need either a JRE (preferred) or a running Docker daemon to render." >&2
  exit 1
fi

echo "Rendered:"
ls -1 "$here"/*.svg
