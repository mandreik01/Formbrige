# Komponenty zewnętrzne i licencje

FormBridge korzysta wyłącznie z oprogramowania open source na licencjach
bez copyleftu. Nie używa zewnętrznych API, usług SaaS ani MCP.

## Biblioteki Java (w pliku `formbridge.jar`)

| Komponent | Do czego | Licencja |
|---|---|---|
| Spring Boot 4.2 (Web, Security, OAuth2 Resource Server, Validation, Actuator) | REST API, JWT, walidacja, health | Apache 2.0 |
| Nimbus JOSE + JWT (przez Spring Security) | podpis i weryfikacja JWT HS256 | Apache 2.0 |
| Apache Tika 3.3 (tika-core, tika-parsers-standard-package) | rozpoznanie typu MIME, tekst z DOC, DOCX, ODS, XLSX | Apache 2.0 |
| Apache PDFBox 3 | tekst z PDF, renderowanie stron skanów do OCR | Apache 2.0 |
| Apache POI (przez Tikę) | odczyt dokumentów Office | Apache 2.0 |
| Jackson 2 | budowa i zapis JSON | Apache 2.0 |
| JUnit 5, AssertJ, Mockito (tylko testy) | testy | EPL 2.0, Apache 2.0, MIT |

## Programy w obrazie Docker

| Komponent | Do czego | Licencja |
|---|---|---|
| Eclipse Temurin JRE 21 (oficjalny obraz `eclipse-temurin`, Alpine) | środowisko uruchomieniowe Java | GPL 2.0 z Classpath Exception |
| Tesseract OCR + dane języka polskiego (`tesseract-ocr`, `tesseract-ocr-data-pol`) | OCR skanów i zdjęć | Apache 2.0 |

Classpath Exception pozwala uruchamiać na JRE dowolny kod bez przejmowania
licencji GPL. To standardowy sposób dystrybucji Javy. Obraz bazowy Alpine
zawiera też narzędzia systemowe na własnych licencjach. Aplikacja z nich nie
korzysta i nie łączy się z nimi.

## Opcjonalny model językowy (profil `llm`, domyślnie wyłączony)

| Komponent | Do czego | Licencja |
|---|---|---|
| llama.cpp server (obraz `ghcr.io/ggml-org/llama.cpp`) | lokalny serwer modelu z API zgodnym z OpenAI | MIT |
| PLLuM-4B-instruct-2512 (CYFRAGOVPL), kwantyzacja GGUF od mradermacher | poprawa słabych etykiet | Apache 2.0 według [karty modelu](https://huggingface.co/CYFRAGOVPL/PLLuM-4B-instruct-2512) |

Pliku modelu nie ma w repozytorium. Pobiera go `scripts/download-models.sh`
do katalogu `models/`, który jest w `.gitignore`.

## Materiały organizatora

Teksty sekcji standardowych w `src/main/resources/core/standard-texts.json`
i struktura wyniku pochodzą z materiałów COI udostępnionych na potrzeby
zadania „Cyfryzacja formularzy” (HackNation 2026).
