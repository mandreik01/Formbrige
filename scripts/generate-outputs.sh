#!/usr/bin/env bash
# Konwertuje wszystkie formularze z katalogu wejściowego przez działające API i zapisuje JSON do output/.
# Użycie: bash scripts/generate-outputs.sh <katalog z formularzami> [adres API, domyślnie http://localhost:8080]
set -euo pipefail
IN="${1:?Podaj katalog z formularzami}"
API="${2:-http://localhost:8080}"
OUT="$(dirname "$0")/../output"
mkdir -p "$OUT"
set -a; [[ -f .env ]] && source .env; set +a

AUTH=()
if [[ "${FORMBRIDGE_SECURITY_ENABLED:-true}" == "true" ]]; then
  TOKEN=$(curl -sf -X POST "$API/api/v1/auth/token" -H 'Content-Type: application/json' \
    -d "{\"clientId\":\"$FORMBRIDGE_CLIENT_ID\",\"clientSecret\":\"$FORMBRIDGE_CLIENT_SECRET\"}" \
    | python3 -c 'import json,sys; print(json.load(sys.stdin)["access_token"])')
  AUTH=(-H "Authorization: Bearer $TOKEN")
fi

find "$IN" -maxdepth 2 -type f -iregex '.*\.\(pdf\|doc\|docx\|ods\|xlsx\|jpg\|jpeg\|jpe\|png\)' | sort | while read -r f; do
  name="$(basename "$f")"
  target="$OUT/${name%.*}.$(echo "${name##*.}" | tr 'A-Z' 'a-z').json"
  code=$(curl -s -o "$target" -w '%{http_code}' -X POST "$API/api/v1/forms/convert" "${AUTH[@]}" -F "file=@$f")
  printf '%-4s %s\n' "$code" "$name"
  [[ "$code" == "200" ]] || mv "$target" "$target.error"
done
