# FormBridge

**Z papierowego wniosku do eUsługi w kilka sekund.**

FormBridge to usługa REST, która przyjmuje formularz urzędowy (PDF, DOC, DOCX, ODS, XLSX, JPG, JPEG, PNG)
i zwraca gotowy do importu JSON z konfiguracją formularza eUsługi: strony tematyczne, sekcje standardowe,
dane inicjalne z rejestrów, słowniki, walidacje i warunki dynamiczne w JSONata.

Projekt powstał na hackathonie **HackNation 2026**, wyzwanie *Cyfryzacja formularzy* (Centralny Ośrodek Informatyki).

## Najważniejsze cechy

- Standardy: Wzorcowa konfiguracja to sekcje predefiniowane (adresat, podmiot wniosku,
  dane z rejestru, adresy, pełnomocnictwo, opłata skarbowa, kontakt, zgody). FormBridge buduje je deterministycznie
  według „Standardów i dobrych praktyk projektowania formularzy”, razem z warunkami JSONata.
- Reguły + polski model językowy: Reguły rozpoznają pola właściwe dla wniosku (typy, opcje, pola warunkowe,
  sekcje powtarzające się, oświadczenia). Lokalny model PLLuM poprawia etykiety i typy pól,
  gdy reguły nie są pewne. Gdy model nie odpowie w limicie czasu, zostaje wynik reguł.
- Kontrola zgodności: Każdy wynik jest sprawdzany względem „Reguł technicznych konfiguracji formularzy”.
- W całości lokalnie. Brak karty graficznej, bez zewnętrznych API i usług SaaS. Dane nie opuszczają serwera.
- Powtarzalność: Ten sam plik zawsze daje ten sam JSON (deterministyczne identyfikatory UUID).

## Szybki start

```bash
# 1. Jednorazowo: pobranie modeli językowych do ./models (ok. 4 GB)
./scripts/download-models.sh            # Windows: .\scripts\download-models.ps1

# 2. Konfiguracja
cp .env.example .env                     # ustawić FORMBRIDGE_JWT_SECRET, FORMBRIDGE_CLIENT_ID, FORMBRIDGE_CLIENT_SECRET

# 3. Uruchomienie
docker compose up --build

# 4. Frontend: http://localhost:8080
```

## Budowanie obrazu Docker

```bash
docker build -t formbridge:1.0.0 .
```

<!-- APP: szczegóły budowania i uruchamiania z APP_NOTES.md -->

## Konfiguracja

Wszystkie parametry ustawia się zmiennymi środowiskowymi; sekrety podaje się wyłącznie przy uruchomieniu kontenera.

| Zmienna | Opis | Domyślnie |
|---|---|---|
| `FORMBRIDGE_UPLOAD_MAX_FILE_SIZE` | maksymalny rozmiar przesyłanego pliku | `20MB` |
| `FORMBRIDGE_SECURITY_ENABLED` | wymóg tokenu JWT | `true` |
| `FORMBRIDGE_JWT_SECRET` | klucz HS256 (min. 32 bajty) | wymagany |
| `FORMBRIDGE_JWT_TTL` | ważność tokenu | `1h` |
| `FORMBRIDGE_CLIENT_ID`, `FORMBRIDGE_CLIENT_SECRET` | dane klienta do pobrania tokenu | wymagane |
| `FORMBRIDGE_LLM_ENABLED` | użycie lokalnego modelu językowego | `true` |
| `FORMBRIDGE_LLM_ENDPOINTS` | serwery modeli `url=nazwa`, w kolejności użycia | PLLuM, potem Bielik |
| `FORMBRIDGE_LLM_TIMEOUT` | łączny limit pracy modelu na formularz | `45s` |
| `FORMBRIDGE_OCR_LANGUAGE` | język OCR (Tesseract) | `pol` |

<!-- APP: pełna lista z APP_NOTES.md -->

## REST API

| Metoda | Ścieżka | Opis |
|---|---|---|
| `POST` | `/api/v1/auth/token` | wydaje token JWT na podstawie `clientId` i `clientSecret` |
| `POST` | `/api/v1/forms/convert` | multipart, pole `file` – zwraca JSON konfiguracji formularza |
| `POST` | `/api/v1/forms/convert-batch` | multipart, wiele pól `files` – wyniki dla każdego pliku |
| `GET` | `/actuator/health` | stan usługi |

Przykład:

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/v1/auth/token -H 'Content-Type: application/json' \
  -d '{"clientId":"demo","clientSecret":"demo-secret"}' | jq -r .access_token)
curl -s -X POST localhost:8080/api/v1/forms/convert -H "Authorization: Bearer $TOKEN" \
  -F 'file=@Wniosek o wydanie zaświadczenia.pdf' -o wynik.json
```

Błędy zwracane są w formacie RFC 7807 (`application/problem+json`): 400 (brak pliku, niepoprawna nazwa),
401 (brak lub błędny token), 413 (za duży plik), 415 (nieobsługiwany format lub MIME niezgodny z rozszerzeniem),
422 (nie udało się odczytać formularza).

## Architektura

Opis architektury, przepływu żądania i decyzji projektowych: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Wyniki dla formularzy z wyzwania

Wygenerowane pliki JSON: [output/](output/).

## Licencje zależności

Wyłącznie biblioteki open source bez copyleftu (Apache 2.0, MIT, BSD): Spring Boot, Apache Tika (PDFBox, POI),
Tesseract OCR, Jackson, llama.cpp. Modele: PLLuM-4B-instruct-2512 (Apache 2.0), Bielik-1.5B-v3.0-Instruct
(licencja według karty modelu). Szczegóły: [THIRD_PARTY.md](THIRD_PARTY.md).

## Wykorzystanie AI przy tworzeniu

Przy analizie materiałów, projektowaniu i pisaniu kodu korzystano z asystentów AI (Claude, opencode).
Decyzje projektowe, weryfikacja wyników i odpowiedzialność za kod należą do autora.
W działającym rozwiązaniu AI to wyłącznie lokalne modele (Tesseract OCR, PLLuM, Bielik).

## Licencja

Zobacz [LICENSE](LICENSE).
