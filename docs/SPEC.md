# Bahasa Kotak – specyfikacja

Wersja: 0.1 (2026-10-26). Źródło wymagań: `bahasa-kotak-prompt.md`.

Aplikacja na Androida do nauki indonezyjskiego dla jednego użytkownika. Działa offline, instalowana przez sideload, UI po polsku. Treść pochodzi wyłącznie z paczek JSON, które powstają w pętli: eksport z apki → analiza w Claude → nowa paczka → import.

## 1. Technologia

| Obszar | Wybór | Dlaczego |
|---|---|---|
| Język i UI | Kotlin + Jetpack Compose | Natywny Android, pełne wsparcie Google, build samym Gradle z CLI. Flutter czy React Native dokładają drugi toolchain (Dart/Node) bez żadnego zysku przy jednej platformie. |
| Baza | Room (SQLite) | Offline-first, migracje schematu, zapytania do statystyk w SQL. Backup to kopia jednego pliku. |
| JSON | kotlinx.serialization | Bez refleksji, typowane modele paczki i eksportu. |
| Walidacja importu | JSON Schema (draft 2020-12), biblioteka `networknt/json-schema-validator` | Ten sam plik schematu waliduje paczkę w apce i opisuje format dla Claude. |
| Wykresy | Vico (Compose) | Lekka biblioteka natywna dla Compose. |
| Testy | JUnit 5 na JVM | Logika domenowa nie zależy od Androida, więc testy nie wymagają emulatora. |
| Build | Gradle wrapper, AGP, JDK 21 | JDK 21 to wersja LTS wspierana przez AGP. Systemowy JDK 25 jest za nowy jako JDK dla Gradle/AGP. |

SDK: `minSdk 26` (spełnia wymóg ≤ 31 z zapasem), `targetSdk`/`compileSdk 35`.

## 2. Architektura

Jeden moduł Gradle `app`, podzielony na pakiety:

```
app/src/main/java/.../bahasakotak/
  domain/      czysty Kotlin: AnswerChecker, Leitner, SessionPlanner, PlacementTest, modele
  data/        Room: encje, DAO, repozytoria, migracje
  io/          PackImporter, ExportBuilder, BackupManager, transport/
  ui/          Compose: ekrany, ViewModele, nawigacja
```

Zasada: `domain` nie importuje nic z `android.*`. Wszystkie testy wymagane briefem (sprawdzanie odpowiedzi, Leitner, import/eksport) to testy JVM.

### 2.1 Warstwa transportu

Eksport i import są rozdzielone, bo przy pliku to dwie niezależne czynności użytkownika, a przy API jedno żądanie:

```kotlin
interface ExportSink   { suspend fun send(export: ExportPayload): SendResult }
interface PackSource   { suspend fun receive(): PackResult }        // surowy JSON → walidacja w PackImporter
interface ContentTransport : ExportSink, PackSource { val id: String; val displayName: String }
```

- `FileTransport` (teraz): `send` zapisuje plik i otwiera systemowe udostępnianie (`ACTION_SEND`, FileProvider). `receive` otwiera wybór pliku (`ACTION_OPEN_DOCUMENT`).
- `FrogApiTransport` (później): `send` robi POST eksportu do serwisu na VPS, a odpowiedzią jest paczka, którą `receive` zwraca od razu. Serwis na Mikrus Frog (Alpine, 256 MB RAM, za Cloudflare) dostaje eksport, dokleja `ANALYSIS_PROMPT.md`, woła Claude API i zwraca paczkę. Uwierzytelnianie: statyczny token w nagłówku.

Wybór transportu to ustawienie w apce. Import zawsze przechodzi przez tę samą walidację niezależnie od źródła.

## 3. Model danych

### 3.1 Treść (z paczek)

- **Pack** – `id`, `version`, `title`, data importu.
- **Topic** – `id`, `title`, `order`.
- **Lesson** – `id`, `topicId`, `title`, `order`, `difficulty` (1–5), opcjonalna `grammarNote`.
- **Item** – `id`, `lessonId`, `kind` (`word` | `phrase` | `sentence`), `id_text`, `pl_text`, `register`, `acceptedId[]`, `acceptedPl[]`, `notes`, `tags[]`, `placementOnly`, `retired`.

`register`: `formal` (baku), `colloquial` (gaul/jakartański) lub `neutral`. Wartość `neutral` dodaję, bo słowa takie jak *komputer* czy *jaringan* nie należą do żadnego rejestru, a wymuszenie wyboru fałszowałoby dane. Pole jest obowiązkowe.

Zdania przykładowe są pełnoprawnymi elementami (`kind: sentence`) w tej samej lekcji co słowo. Dzięki temu mają własne ID, warianty i postęp, i to z nich powstają ćwiczenia z klocków.

### 3.2 Postęp (lokalny)

- **Card** – stan Leitnera dla pary **(element, kierunek)**: `box` (0 = jeszcze nie wprowadzona, 1–5, 6 = opanowane), `dueAt`, `masteredIntervalDays`, `introducedAt`. Kierunki PL→ID i ID→PL mają osobne karty, bo czynna znajomość słowa jest znacznie trudniejsza niż bierna i należy ją powtarzać osobno.
- **AnswerLog** – każda odpowiedź: `itemId`, `direction`, `exerciseType`, `prompt`, `userAnswer`, `expected`, `grade` (`correct` | `typo` | `wrong`), `answeredAt`, `durationMs`, `boxBefore`, `boxAfter`, `sessionId`, `retry` (powtórka po błędzie w tej samej sesji).
- **Session** – `id`, `startedAt`, `endedAt`, liczba zadań.

## 4. Sprawdzanie odpowiedzi (`AnswerChecker`)

Normalizacja odpowiedzi i każdego akceptowanego wariantu:

1. małe litery,
2. usunięcie polskich znaków diakrytycznych: ą→a, ć→c, ę→e, ł→l, ń→n, ó→o, ś→s, ź→z, ż→z (dla indonezyjskiego to nic nie zmienia),
3. usunięcie interpunkcji (`.,!?;:"'()-…` itp.); myślnik wewnątrz słowa zamieniany na spację, żeby `kira-kira` = `kira kira`,
4. zwinięcie wielu spacji do jednej i obcięcie z obu stron.

Zbiór poprawnych odpowiedzi = główne tłumaczenie + `accepted*` z paczki. Każdy wariant jest dodatkowo rozwijany o formy bez podpowiedzi: fragmenty w nawiasach są opcjonalne („dzień dobry (rano)” przyjmuje „dzień dobry”), a „A / B” przyjmuje osobno A i B. Ocena:

- **correct** – znormalizowana odpowiedź równa któremuś wariantowi.
- **typo** („prawie dobrze”) – odległość Damerau-Levenshteina do najbliższego wariantu ≤ progu. Pokazujemy poprawną pisownię najbliższego wariantu.
- **wrong** – w pozostałych przypadkach.

Próg zależy od długości wariantu po normalizacji:

| Długość | Próg |
|---|---|
| ≤ 3 | 0 (krótkie słowa: jedna zmiana daje często inne słowo, np. *ini/itu*) |
| 4–7 | 1 |
| 8–14 | 2 |
| ≥ 15 | `floor(len / 7)` |

Damerau (z transpozycją), bo zamiana sąsiednich liter to najczęstsza literówka na klawiaturze telefonu.

## 5. Powtórki – Leitner

| Karta trafia do przegródki | Następna powtórka za |
|---|---|
| 1 | 1 dzień |
| 2 | 2 dni |
| 3 | 4 dni |
| 4 | 8 dni |
| 5 | 16 dni |
| opanowane | 60 dni, potem 120, 240… (podwajane) |

Uzasadnienie: podwajanie odstępów to klasyczny schemat Leitnera i odpowiada krzywej zapominania. Start od 1 dnia wynika z rytmu „raz dziennie przez 5 minut”: krótszy odstęp i tak nie wystąpi. Przy 16 dniach w ostatniej przegródce słowo, które przejdzie bezbłędnie całą drabinkę, zostaje opanowane po 30 dniach (2 + 4 + 8 + 16), co pasuje do horyzontu startowej paczki (2–3 tygodnie nauki plus powtórki).

Przejścia:

- nowa karta → przegródka 1, termin: dziś,
- terminy są liczone w dniach kalendarzowych (północ czasu lokalnego), więc karta „za 1 dzień” pojawi się w jutrzejszej sesji niezależnie od pory dnia,
- **correct**: przegródka +1; z przegródki 5 → **opanowane** (termin +60 dni); przy opanowanym słowie odstęp się podwaja,
- **wrong**: przegródka 1 (również ze stanu opanowane), termin: jutro; zadanie wraca jeszcze w tej sesji,
- **typo**: przegródka bez zmian, termin wyznaczony jak dla tej przegródki (bez awansu).

Powrót zadania w tej samej sesji nie zmienia już przegródki. Liczy się tylko pierwsza odpowiedź na daną kartę w danej sesji. Powtórka służy utrwaleniu, a w logu jest zapisana z `boxBefore = boxAfter`.

## 6. Sesja (`SessionPlanner`)

- Sesja ma limit **zadań**, na start 20. Po każdej sesji apka liczy medianę czasu odpowiedzi z ostatnich 7 dni i ustawia limit tak, żeby wyszło ok. 300 s (zakres 10–40).
- Kolejność doboru:
  1. zaległe karty (`dueAt ≤ teraz`), od najstarszych; opanowane na końcu kolejki,
  2. jeśli zostało miejsce: nowe elementy z bieżącej lekcji (max 5 nowych elementów na sesję). Nowy element wprowadza obie karty kierunkowe, z kierunkiem ID→PL najpierw. Każdy nowy element najpierw pojawia się jako karta „Nowe” (oba teksty, rejestr, notka), bo nie da się odpytać ze słowa, którego się jeszcze nie widziało. Pytanie ID→PL pada 2–3 zadania później, a pytania PL→ID zamykają sesję. Karta „Nowe” nie jest oceniana i nie wlicza się do limitu.
- Kierunki są losowane tak, żeby w sesji było ich po równo (±1).
- Typ ćwiczenia: zdania (`sentence`) dostają klocki, a słowa i frazy fiszki z wpisywaniem. Zdanie co trzeci raz trafia na fiszkę, a fraza z ≥ 3 słowami na klocki, żeby oba typy się przeplatały.
- Błędne zadanie wraca po 3–5 kolejnych zadaniach i nie wlicza się do limitu. Wraca najwyżej 3 razy, żeby sesja się nie zapętliła.
- Lekcja jest „zaliczona”, gdy wszystkie jej karty są w przegródce ≥ 2. Następna lekcja tematu odblokowuje się wtedy automatycznie. Tematy idą równolegle według `order`.
- Ekran startowy ma jeden przycisk „Zacznij”, a sesja ładuje się bez ekranów pośrednich. Jeśli nowa lekcja ma notkę gramatyczną, pokazuje się ona raz przed jej pierwszym zadaniem.

## 7. Ćwiczenia

**Fiszka z wpisywaniem** – pokazuje tekst źródłowy i rejestr (znaczek „formalny”/„potoczny”), użytkownik wpisuje tłumaczenie. Wynik jest oceniany wg §4.

**Klocki słów** – rozsypane tokeny zdania docelowego plus pułapki. Liczba pułapek zależy od `difficulty` lekcji: 1→0, 2→1, 3→2, 4→3, 5→4. Pułapki pochodzą z pola `distractors` elementu, jeśli istnieje, a w przeciwnym razie z innych elementów tej samej lekcji lub tematu w tym samym języku. Odpowiedź jest poprawna, gdy ułożona sekwencja po normalizacji równa się któremuś wariantowi. Ocena `typo` tu nie występuje.

## 8. Test poziomujący

Przy pierwszym uruchomieniu (z możliwością pominięcia) apka zadaje ok. 20 fiszek ID→PL:

- najpierw elementy `placementOnly` (podstawy pominięte w lekcjach: powitania, liczby, zaimki),
- potem po 2 elementy z pierwszych lekcji każdego tematu.

Wynik:

- **correct** – obie karty elementu idą do przegródki 4 (termin: za 8 dni, czyli weryfikacja w normalnym trybie),
- **typo** – przegródka 2,
- **wrong** lub „Nie wiem” – element zostaje nowy. Wyjątek: element `placementOnly` nigdy nie wchodzi jako nowy materiał, więc jego obie karty idą od razu do przegródki 1 (termin: dziś) i trafiają do zwykłych powtórek.

Pominięcie testu oznacza, że podstawy `placementOnly` uznajemy za znane: nie powstają dla nich karty.

Jeśli w lekcji poprawnie odpowiedziano na wszystkie testowane elementy, cała lekcja jest oznaczana jako przejrzana i jej pozostałe elementy startują z przegródki 2. Elementy `placementOnly` po teście są zwykłymi kartami w powtórkach, ale nie pojawiają się jako nowy materiał.

## 9. Paczki i import

Schemat: `schemas/pack.schema.json`, `schemaVersion: 1`.

Zasady idempotencji:

- ID tematów, lekcji, elementów i notek są stabilne i globalne (np. `it.meeting.rapat`). Claude musi zachowywać ID przy aktualizacji.
- Import to upsert według ID: treść (teksty, warianty, rejestr, notki, kolejność) jest aktualizowana, a **karty i log odpowiedzi nietknięte**.
- Element obecny w bazie, ale brak go w nowej wersji **tej samej** paczki → `retired = true`. Znika z sesji, ale zostaje w statystykach i logu. Ponowne pojawienie się go przywraca.
- Ta sama paczka (`packId` + `packVersion`) zaimportowana ponownie → brak zmian. Niższa wersja niż zainstalowana → odrzucona z komunikatem.
- Import działa w jednej transakcji: błąd walidacji albo zapisu oznacza, że baza zostaje bez zmian.
- Przed zapisem apka pokazuje podsumowanie: „nowe: X, zaktualizowane: Y, wycofane: Z”.

## 10. Eksport dla Claude

Schemat: `schemas/export.schema.json`, `schemaVersion: 1`. Zawartość:

- metadane: wersja apki, data eksportu, zainstalowane paczki z wersjami,
- **pełny log odpowiedzi** (wszystkie pola z §3.2),
- bieżący stan kart (przegródka, termin),
- postęp lekcji i tematów,
- sesje.

Eksport jest zawsze pełny, bez przyrostów. Przy 5 minutach dziennie to ok. 30 odpowiedzi dziennie, czyli rząd ~1 MB rocznie, więc rozmiar nie jest problemem.

`docs/ANALYSIS_PROMPT.md` to gotowy prompt do wklejenia razem z eksportem. Zawiera zasady analizy (wzorce błędów, rejestry, IT/telco, dobór trudności), wymaga zachowania istniejących ID i zwrócenia jednej paczki zgodnej z `pack.schema.json`.

## 11. Backup

Osobna funkcja w ustawieniach:

- **Backup**: checkpoint WAL, a potem kopia pliku bazy SQLite do pliku wybranego przez użytkownika (`ACTION_CREATE_DOCUMENT`, nazwa `bahasa-kotak-YYYYMMDD-HHMM.db`).
- **Przywracanie**: wybór pliku, sprawdzenie, czy to baza tej apki (nagłówek SQLite, `user_version`, obecność tabel), zamknięcie bazy, podmiana pliku, restart apki. Backup ze starszej wersji schematu przechodzi normalne migracje Room przy otwarciu.

## 12. Statystyki

- aktywność: odpowiedzi i sesje dziennie (wykres słupkowy, 30/90 dni),
- postęp w czasie: liczba opanowanych kart i kart w przegródkach ≥ 3 (wykres liniowy),
- rozkład kart w przegródkach 1–5 i opanowanych,
- najtrudniejsze słowa: najwięcej błędów i najniższy odsetek poprawnych (min. 3 próby), z podziałem na kierunek,
- postęp w tematach: odsetek kart w przegródce ≥ 3 na temat.

## 13. Build i instalacja

Narzędzia CLI na Fedorze (fish):

```fish
sudo dnf install java-21-openjdk-devel android-tools   # JDK dla Gradle + adb/fastboot
set -Ux ANDROID_HOME ~/Android/Sdk
# commandlinetools z developer.android.com → $ANDROID_HOME/cmdline-tools/latest
fish_add_path $ANDROID_HOME/cmdline-tools/latest/bin $ANDROID_HOME/platform-tools
sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

Gradle ma wskazany JDK 21 w `gradle.properties` (`org.gradle.java.home`) albo przez toolchain, więc systemowy JDK 25 może zostać domyślny.

Instalacja na telefonie (MIUI 14):

1. Ustawienia → O telefonie → 7× „Wersja MIUI” → opcje programisty.
2. Opcje programisty: „Debugowanie USB” oraz **„Instaluj przez USB”**. Ta druga wymaga zalogowanego konta Mi i często włożonej karty SIM oraz danych komórkowych przy włączaniu.
3. `./gradlew installDebug` albo `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

Alternatywa bez „Instaluj przez USB”: przesłanie APK (KDE Connect, kabel) i instalacja z menedżera plików po zezwoleniu na „nieznane źródła”.

Na co dzień wystarcza build debug. Podpis debug jest stały na danym laptopie, więc aktualizacje instalują się bez utraty danych.

## 14. Kamienie milowe

1. Toolchain CLI, szkielet apki (Compose, pusty ekran), `installDebug` działa na telefonie.
2. Room, modele, `pack.schema.json`, importer z testami, startowa paczka (~100 słów + zdania).
3. Fiszki, `AnswerChecker`, Leitner, `SessionPlanner`, testy.
4. Klocki słów, notki gramatyczne, test poziomujący.
5. Eksport, `export.schema.json`, `ANALYSIS_PROMPT.md`, backup/przywracanie.
6. Statystyki.

Po każdym kamieniu: commit i push.
