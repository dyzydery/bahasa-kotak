# Bahasa Kotak – prompt dla Claude Code

Zbuduj aplikację na Androida **Bahasa Kotak** do nauki języka indonezyjskiego (bahasa Indonesia) w stylu Duolingo, ale z naciskiem na naukę, a nie grywalizację. Zanim zaczniesz pisać kod, przedstaw plan (architektura, wybór technologii z uzasadnieniem, schematy JSON, kamienie milowe) i zadaj pytania, jeśli coś jest niejasne. Zapisz specyfikację w repo jako `docs/SPEC.md`.

## Kontekst użytkownika
- Jedyny użytkownik: ja (Maciek). Brak kont, logowania, backendu i publikacji w Google Play. Instalacja przez sideload.
- Interfejs i tłumaczenia: **język polski**.
- Poziom: podstawy (powitania, liczby).
- Cel: kontakty zawodowe i prywatne z Indonezyjczykami. Pracuję w IT/telco (pre-sales, wdrożenia systemów zarządzania urządzeniami CPE u operatorów).
- Czas: ok. **5 minut dziennie**. Sesja ma startować natychmiast i być krótka.

## Treść kursu
- Dwa rejestry: formalny (baku) i potoczny (gaul/jakartański). Każdy element ma oznaczony rejestr.
- Nacisk na słownictwo **IT i telco** (spotkania, maile, projekty, wdrożenia, sieci, operatorzy, CPE), obok języka codziennego.
- Struktura: **tematy**, a w każdym temacie lekcje ułożone wg rosnącej trudności.
- Przed nową lekcją pokazuj **krótką notkę gramatyczną** (np. przedrostki me-/ber-, szyk zdania), jeśli lekcja ją zawiera.
- Bez audio i bez TTS: tylko tekst.
- Treść pochodzi **wyłącznie z paczek** (bez dodawania słówek w apce).
- **Startowa paczka**: wygeneruj ją sam, ok. 100 słów plus zdania przykładowe (na 2–3 tygodnie), w formacie paczki opisanym niżej. Pomiń trywialne podstawy, ale dodaj je do testu poziomującego.

## Ćwiczenia
1. **Tłumaczenie z klocków słów**: układanie zdania z rozsypanych słów. Liczba słów-pułapek rośnie z poziomem trudności.
2. **Fiszki z wpisywaniem odpowiedzi**: wpisuję tłumaczenie, a apka sprawdza.
- Kierunek: **PL → ID i ID → PL po równo**.

## Sprawdzanie odpowiedzi
- Ignoruj wielkość liter, interpunkcję i nadmiarowe spacje.
- **Polskie znaki ignorowane** (zolw = żółw).
- Paczka zawiera **listę akceptowanych wariantów** dla każdego elementu. Porównuj ze wszystkimi.
- **Literówka** (mała odległość edycyjna, próg zależny od długości słowa) daje ocenę „prawie dobrze”: pokaż poprawną pisownię.
- Po błędnej odpowiedzi pokaż poprawną i **wróć do tego zadania później w tej samej sesji**.

## Powtórki – system Leitnera (5 przegródek)
- Nowe słowo trafia do przegródki 1.
- Poprawnie: przechodzi o jedną przegródkę wyżej.
- Źle: **wraca do przegródki 1**.
- Literówka („prawie dobrze”): **zostaje w tej samej przegródce**.
- Poprawna odpowiedź w przegródce 5 oznacza, że słowo jest opanowane.
- Odstępy między powtórkami dla przegródek dobierz sam i uzasadnij w SPEC.md.
- Sesja: najpierw zaległe powtórki, nowy materiał dobieraj tak, żeby zmieścić się w ok. 5 minutach.

## Start: test poziomujący
Krótki test przy pierwszym uruchomieniu. Elementy, które znam, trafiają od razu do wyższych przegródek, zamiast blokować postęp.

## Grywalizacja
Minimum. Bez XP, serduszek i mapy. Bez powiadomień.

## Statystyki (szczegółowe)
Wykresy aktywności i postępu w czasie, rozkład słów w przegródkach, najtrudniejsze słowa (najczęstsze błędy), postęp w tematach.

## Pętla z Claude: eksport → analiza → nowa paczka
To kluczowy element. Treść rośnie iteracyjnie:
1. Apka eksportuje plik JSON z **pełnym logiem każdej odpowiedzi** (element, kierunek, typ ćwiczenia, moja odpowiedź, ocena, znacznik czasu, przegródka przed i po) oraz bieżącym stanem przegródek i postępów.
2. Ja ręcznie przekazuję plik do Claude (udostępnianie Androida do aplikacji Claude albo na laptopa).
3. Claude analizuje błędy i generuje **nową paczkę lekcji** (JSON).
4. Importuję paczkę w apce. Import ma być idempotentny (stabilne ID elementów, aktualizacja istniejących bez utraty postępów).

Wymagania:
- Zdefiniuj **wersjonowane schematy JSON** (`schemas/export.schema.json`, `schemas/pack.schema.json`) i waliduj import.
- Przygotuj plik `docs/ANALYSIS_PROMPT.md`: gotowy prompt, który wklejam do Claude razem z eksportem. Ma on zawierać zasady analizy (wzorce błędów, rejestry, tematy IT/telco, dobór trudności) oraz wymagać zwrócenia paczki zgodnej ze schematem.
- Transport (na teraz): **plik** przez systemowe udostępnianie i wybór pliku (eksport i import).
- **Warstwa transportu musi być wymienna** (interfejs plus implementacja „plik”). W przyszłości dojdzie implementacja „Frog API”: apka wyśle eksport do małego serwisu HTTP na moim VPS (Mikrus Frog, Alpine Linux, 256 MB RAM, domena za Cloudflare), który wywoła API Claude i odeśle paczkę. Nie implementuj tego teraz, tylko nie blokuj tej drogi.

## Backup
Osobna funkcja **pełnego backupu bazy do pliku** i odtwarzania z niego (niezależnie od eksportu dla Claude).

## Technologia i środowisko
- Technologię wybierz sam i uzasadnij. Dane trzymaj lokalnie w bazie (offline-first).
- Telefon: Xiaomi z **MIUI 14 (Android 12/13)**, więc minSdk nie wyżej niż 31.
- Laptop: Fedora 44, KDE Plasma, shell **fish**. **Nie mam Android Studio**: skonfiguruj build przez CLI (JDK, Android command-line tools, SDK), z poleceniami dla fish.
- Zaproponuj sposób instalacji APK na telefonie. Uwzględnij, że na MIUI instalacja przez adb wymaga włączenia „Install via USB” w Developer options (czasem z kontem Mi i kartą SIM).
- Kod i commity po angielsku, teksty UI po polsku.
- Testy jednostkowe co najmniej dla: sprawdzania odpowiedzi (warianty, literówki, polskie znaki), logiki Leitnera i importu/eksportu.

## Kamienie milowe (propozycja)
1. Środowisko CLI i szkielet apki, który się buduje i instaluje na telefonie.
2. Model danych, schematy JSON, import startowej paczki.
3. Fiszki z wpisywaniem, sprawdzanie odpowiedzi, Leitner, sesja 5-minutowa.
4. Klocki słów, notki gramatyczne, test poziomujący.
5. Eksport, ANALYSIS_PROMPT.md, backup.
6. Statystyki.
