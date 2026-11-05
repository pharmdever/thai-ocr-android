#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
check_dir=$(mktemp -d)
trap 'rm -rf "$check_dir"' EXIT
javac -d "$check_dir" app/src/main/java/com/thaio/ocr/PixelPreparation.java app/src/main/java/com/thaio/ocr/PrintedTextFusion.java app/src/main/java/com/thaio/ocr/CtcDecoder.java checks/PixelPreparationCheck.java checks/PrintedRecognitionCheck.java
java -cp "$check_dir" PixelPreparationCheck
java -cp "$check_dir" PrintedRecognitionCheck
