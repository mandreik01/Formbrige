#!/usr/bin/env bash
# Test szybkości i jakości lokalnego modelu na zadaniu takim, jakie wysyła FormRefiner.
# Użycie: bash scripts/llm-smoke.sh [adres serwera, domyślnie http://localhost:8081]
set -euo pipefail
URL="${1:-http://localhost:8081}"
BODY="$(dirname "$0")/smoke-body.json"
echo "Serwer: $URL"
start=$(date +%s.%N)
RESP=$(curl -s -X POST "$URL/v1/chat/completions" -H 'Content-Type: application/json' --data-binary "@$BODY")
end=$(date +%s.%N)
echo "Czas: $(echo "$end - $start" | bc) s"
echo "$RESP" | python3 -c '
import json,sys
r=json.load(sys.stdin)
if "choices" not in r:
    print("Błąd serwera:", json.dumps(r, ensure_ascii=False)); sys.exit(1)
for it in json.loads(r["choices"][0]["message"]["content"])["items"]: print(" ", it["id"], "->", it["label"], it["type"])
t=r.get("timings",{})
if t: print("Prompt: %d tok, %.1f tok/s | generowanie: %d tok, %.1f tok/s" % (t.get("prompt_n",0), t.get("prompt_per_second",0), t.get("predicted_n",0), t.get("predicted_per_second",0)))
'
