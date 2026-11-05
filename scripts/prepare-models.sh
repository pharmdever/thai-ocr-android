#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
for model in fast best; do
  directory="app/src/main/assets/models/${model}/tessdata"
  mkdir -p "$directory"
  for language in tha eng; do
    target="${directory}/${language}.traineddata"
    curl --fail --location --retry 3 "https://raw.githubusercontent.com/tesseract-ocr/tessdata_${model}/4.1.0/${language}.traineddata" -o "${target}.tmp"
    test "$(wc -c < "${target}.tmp")" -gt 100000
    mv "${target}.tmp" "$target"
  done
  curl --fail --location --retry 3 "https://raw.githubusercontent.com/tesseract-ocr/tessdata_${model}/4.1.0/LICENSE" -o "${directory}/LICENSE"
done
