#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p models

PLLUM_URL="${PLLUM_URL:-https://huggingface.co/mradermacher/PLLuM-4B-instruct-2512-i1-GGUF/resolve/main/PLLuM-4B-instruct-2512.i1-Q4_K_M.gguf}"

fetch() {
  local url="$1" out="models/$2"
  if [[ -s "$out" ]]; then
    echo "Jest już: $out"
    return
  fi
  echo "Pobieram $out"
  curl -fL --retry 3 -C - ${HF_TOKEN:+-H "Authorization: Bearer $HF_TOKEN"} -o "$out.part" "$url"
  mv "$out.part" "$out"
}

fetch "$PLLUM_URL" "PLLuM-4B-instruct-2512.i1-Q4_K_M.gguf"
ls -lh models
