# FormBridge

*Z papierowego wniosku do eUsługi w kilka sekund.*

FormBridge to usługa REST, która przyjmuje formularz urzędowy (PDF, DOC, DOCX, ODS, XLSX, JPG, JPEG, PNG)
i zwraca gotowy do importu JSON z konfiguracją formularza eUsługi: 
- strony tematyczne,
- sekcje standardowe,
- dane inicjalne z rejestrów,
- słowniki,
- walidacje i warunki dynamiczne w JSONata.

Projekt powstał w ramach realizacji wyzwania *Cyfryzacja formularzy* przygotowanego przez Centralny Ośrodek Informatyki.

## Najważniejsze cechy

  1. Standardy: Wzorcowa konfiguracja to sekcje predefiniowane,
    - adresat, podmiot wniosku,
    - dane z rejestru,
    - adresy, pełnomocnictwo,
    - opłata skarbowa,
    - kontakt,
    - zgody. 
  2. FormBridge buduje je deterministycznie według „Standardów i dobrych praktyk projektowania formularzy”, razem z warunkami JSONata.
  3. Reguły + opcjonalny polski model językowy. 
  4. Reguły rozpoznają pola właściwe dla wniosku:
      - typy,
      - opcje,
      - pola warunkowe,
      - sekcje powtarzające się,
      - oświadczenia. 
  5. Opcjonalnie lokalny model PLLuM może poprawiać etykiety i typy pól, gdy reguły nie są pewne (zalecany serwer z GPU). Gdy model nie odpowie w limicie czasu, zostaje wynik reguł.
  6. Kontrola zgodności: Każdy wynik jest sprawdzany względem „Reguł technicznych konfiguracji formularzy”.
  7. Aplikacja działa w całości lokalnie. Brak karty graficznej, bez zewnętrznych API i usług SaaS. Dane nie opuszczają serwera.
  8. Powtarzalność: Ten sam plik zawsze daje ten sam JSON (deterministyczne identyfikatory UUID).

## Uruchomienie aplikacji

```bash
# 1. Opcjonalnie jeśli wykorzystany jest model językowy. W takim wypadku należy pobrać model np. PLLuM do ./models
./scripts/download-models.sh

# 2. Konfiguracja
cp .env.example .env                     # ustawić FORMBRIDGE_JWT_SECRET, FORMBRIDGE_CLIENT_ID, FORMBRIDGE_CLIENT_SECRET

# 3. Uruchomienie
docker compose up --build                      # same reguły i szablony
# docker compose --profile llm up --build      # z modelem językowym np. PLLuM (ustawiamy FORMBRIDGE_LLM_ENABLED=true w .env)

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
| `FORMBRIDGE_LLM_ENABLED` | użycie lokalnego modelu językowego | `false` |
| `FORMBRIDGE_LLM_ENDPOINTS` | serwery modeli `url=nazwa`, w kolejności użycia | PLLuM |
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

Błędy zwracane są w formacie RFC 7807 (`application/problem+json`): 
400 (brak pliku, niepoprawna nazwa),
401 (brak lub błędny token),
413 (za duży plik),
415 (nieobsługiwany format lub MIME niezgodny z rozszerzeniem),
422 (nie udało się odczytać formularza).

## Model językowy

Rdzeń FormBridge działa bez modelu językowego. Lokalny model PLLuM-4B-instruct-2512 (serwer llama.cpp w osobnym
kontenerze, bez publikowanych portów) można włączyć jako dodatek poprawiający etykiety pól. Pomiar na laptopie
z 6 rdzeniami CPU bez GPU: ok. 20 s na 5 etykiet przy 4–5 tokenach/s, a jakość poprawek była niewystarczająca,
dlatego w domyślnej konfiguracji model jest wyłączony. Na serwerze z GPU lub większą liczbą rdzeni ten sam kontener działa znacznie
szybciej; wtedy wystarczy `FORMBRIDGE_LLM_ENABLED=true` i `docker compose --profile llm up`.

## Architektura

Opis architektury, przepływu żądania i decyzji projektowych: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Wyniki dla formularzy z wyzwania

Wygenerowane pliki JSON: [output/](output/).

## Licencje zależności

Wyłącznie biblioteki open source bez copyleftu (Apache 2.0, MIT, BSD): Spring Boot, Apache Tika (PDFBox, POI),
Tesseract OCR, Jackson, llama.cpp. Modele: PLLuM-4B-instruct-2512 (Apache 2.0)
(licencja według karty modelu). Szczegóły: [THIRD_PARTY.md](THIRD_PARTY.md).

## Wykorzystanie AI przy tworzeniu

Przy analizie materiałów i pisaniu kodu korzystano ze wsparcia kolegów asystentów AI (Claude, Opencode).
Decyzje projektowe i projekt systemu oraz weryfikacja wyników i odpowiedzialność za kod należą do autora.
W działającym rozwiązaniu AI to wyłącznie lokalne modele (Tesseract OCR, PLLuM).

## Licencja

Zobacz [LICENSE](LICENSE).
