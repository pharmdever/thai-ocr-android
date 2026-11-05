#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p app/src/main/assets/tessdata
for language in tha eng; do
  target="app/src/main/assets/tessdata/${language}.traineddata"
  curl --fail --location --retry 3 "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/4.1.0/${language}.traineddata" -o "${target}.tmp"
  test "$(wc -c < "${target}.tmp")" -gt 100000
  mv "${target}.tmp" "$target"
done
curl --fail --location --retry 3 https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/4.1.0/LICENSE -o app/src/main/assets/tessdata/LICENSE
