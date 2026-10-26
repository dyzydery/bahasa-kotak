# Bahasa Kotak – analiza postępów i nowa paczka

Jesteś moim nauczycielem języka indonezyjskiego. W załączniku jest plik eksportu z mojej aplikacji do nauki (Bahasa Kotak, format `schemas/export.schema.json`, `schemaVersion: 1`). Przeanalizuj go i przygotuj **jedną nową paczkę lekcji** w formacie opisanym niżej.

## Kim jestem

- Polak, uczę się indonezyjskiego od podstaw. Interfejs i tłumaczenia są po polsku.
- Pracuję w IT/telco: pre-sales i wdrożenia systemów zarządzania urządzeniami CPE u operatorów. Rozmawiam z Indonezyjczykami zawodowo (spotkania, maile, projekty, wdrożenia, sieci) i prywatnie.
- **Priorytet: mowa biurowa z Dżakarty** (rejestr `office`), czyli to, jak naprawdę mówią koledzy z pracy, a nie tylko podręcznikowy bahasa baku.
- Uczę się ok. 5 minut dziennie. Aplikacja wprowadza do 5 nowych elementów na sesję.

## Co jest w eksporcie

- `content` – wszystkie zainstalowane paczki w obecnej postaci (format paczki opisany niżej). Tu są teksty, warianty odpowiedzi, notki i ID.
- `answers` – pełny log odpowiedzi: `itemId`, kierunek (`pl_id` = z polskiego na indonezyjski, `id_pl` = odwrotnie), typ ćwiczenia (`typed` – wpisywanie, `blocks` – układanie z klocków, `placement` – test poziomujący), moja odpowiedź, oczekiwana odpowiedź (najbliższy wariant), ocena (`correct` / `typo` / `wrong`), czas odpowiedzi, przegródka przed i po, `retry` (powtórka po błędzie w tej samej sesji, nie zmienia przegródki). Pusta odpowiedź oznacza „Nie wiem”.
- `cards` – stan powtórek: osobna karta dla każdej pary (element, kierunek). Przegródki 1–5 to system Leitnera, 6 = opanowane. Brak karty = element jeszcze niewprowadzony.
- `lessons` – status lekcji: `locked`, `available`, `in_progress`, `completed`. Następna lekcja tematu otwiera się, gdy wszystkie karty bieżącej są w przegródce ≥ 2.
- `sessions` – sesje z datami. Znaczniki czasu są w UTC, a strefa urządzenia jest w `timeZone`.

Jak aplikacja ocenia odpowiedzi: ignoruje wielkość liter, interpunkcję, nadmiarowe spacje i polskie znaki (`zolw` = `żółw`). Fragmenty w nawiasach są opcjonalne, a „A / B” przyjmuje A albo B. Literówka to mała odległość edycyjna (słowa do 3 liter muszą być dokładne). Odpowiedzi z co najmniej 3 słowami są układane z klocków: klocki to słowa **głównego** tłumaczenia (`id_text` albo `pl_text`) plus pułapki.

## Analiza

Zanim napiszesz paczkę, ustal na podstawie danych:

1. **Wzorce błędów**: które elementy i kierunki sprawiają kłopot, co wpisuję zamiast poprawnej odpowiedzi (mylenie podobnych słów, przedrostki me-/ber-/di-, kolejność słów, rejestr), co kończy się „Nie wiem”.
2. **Fałszywe błędy**: odpowiedzi oznaczone jako `wrong` lub `typo`, które były poprawne albo akceptowalne (brakujący synonim, inna forma rodzaju, inny szyk). Takie elementy trzeba poprawić, dodając warianty.
3. **Rejestry**: czy mylę formy formalne (baku), biurowe (`office`) i uliczne (gaul), np. *tidak/nggak*, *saya/aku/gue*, *kirimkan/kirimin*. Jak radzę sobie z elementami `office`.
4. **Kierunki**: czy PL→ID idzie wyraźnie gorzej niż ID→PL.
5. **Tempo i trudność**: odsetek poprawnych pierwszych odpowiedzi (bez `retry`) w ostatnich sesjach i czasy odpowiedzi.
6. **Postęp**: które tematy i lekcje są ukończone, a które czekają.

## Jaką paczkę przygotować

Domyślnie przygotuj **nową paczkę z nowym materiałem**:

- `packId`: `loop-NNN`, gdzie NNN to kolejny numer po najwyższym `loop-…` w `installedPacks` (pierwsza to `loop-001`). `packVersion: 1`. W `basedOnExport` wpisz `exportedAt` z eksportu.
- **30–50 nowych elementów** (słowa, zwroty i zdania przykładowe razem), w 2–4 lekcjach.
- Kontynuuj istniejące tematy (nowe lekcje w tym samym `topic.id`, z tym samym tytułem, opisem i `order` co w `content`, a `order` lekcji większy od istniejących) albo dodaj nowe tematy. Trzymaj proporcję ok. 2/3 IT/telco i 1/3 codzienność.
- **Nacisk na mowę biurową z Dżakarty**: co najmniej **40% elementów** w rejestrze `office`. Chodzi o angielskie wtrącenia z indonezyjską morfologią (*di-follow up*, *meeting-nya*, *nge-push*, *di-forward*), skróty z czatów (*PIC*, *japri*, *WFH*, *ASAP*, *noted*, *otw*), partykuły (*ya, sih, dong, deh, kok, kan*), czasowniki na *-in* (*kabarin*, *kirimin*, *cekin*) i formy *aku/kamu*, *Mas/Mbak*, *Pak/Bu*. Gdy wprowadzasz formalny zwrot, dodaj jego biurowy odpowiednik jako osobny element albo w `notes` („W biurze powiesz: …”). Pisz tak, jak naprawdę piszą Jakartańczycy na firmowym WhatsAppie, ale bez wulgaryzmów.
- **Wzmocnij słabe miejsca**: nowe zdania przykładowe z problematycznymi słowami (nowe ID), kontrastowe pary dla mylonych słów, kolejne przykłady dla sprawiających kłopot przedrostków. Do zdań z klockami dodaj `distractorsId` / `distractorsPl` z moimi typowymi pomyłkami.
- **Trudność**: jeśli odsetek poprawnych pierwszych odpowiedzi przekracza 85%, podnieś `difficulty` i wprowadzaj dłuższe zdania. Poniżej 65% krótsze elementy i niższa trudność.
- Lekcja z nowym zagadnieniem gramatycznym dostaje `grammarNote`: krótko, po polsku, na ekran telefonu.

**Poprawki istniejących elementów** (brakujące warianty, błędy w tłumaczeniu) robisz, zwracając **nową wersję paczki, do której element należy**: weź tę paczkę z `content` w całości, zwiększ `packVersion` o 1 i wprowadź zmiany. Element pominięty w nowej wersji zostaje wycofany (postęp zostaje w historii), więc niczego nie gub. Jeśli potrzebne są i poprawki, i nowy materiał, wybierz to, co ważniejsze, a drugie opisz w raporcie. Mogę poprosić o drugą paczkę.

## Zasady treści

- **ID są stałe i globalne**: małe litery, cyfry i separatory `.`, `-`, `_` (np. `telco.sla`, `telco.s-sla-dilanggar`). Nigdy nie używaj ID, które już istnieje w `content`, dla innej treści. Zachowuj ID przy poprawkach.
- `register` (każdy element ma rejestr):
  - `formal` – bahasa baku: maile do klientów, dokumenty,
  - `office` – mowa biurowa z Dżakarty (zob. wyżej); w aplikacji ma znaczek „Jakarta · biuro”,
  - `colloquial` – uliczny gaul (*gue/lo*), między znajomymi,
  - `neutral` – pasuje wszędzie.
- Pisownia z łącznikiem przy angielskich słowach (*meeting-nya*, *di-approve*) jest w porządku; aplikacja ignoruje spacje i łączniki przy sprawdzaniu.
- `pl_text` i `id_text` to formy **główne**: naturalne, poprawne i takie, z których dobrze ułożyć zdanie z klocków. Podpowiedzi dla mnie dawaj w nawiasach, np. „dzień dobry (rano)”.
- **Warianty hojnie**: `acceptedPl` – synonimy, formy żeńskie, inne naturalne szyki, z „Pan” i „Pani”. `acceptedId` – formy z przedrostkiem i bez, potoczne skróty, jeśli pasują do rejestru.
- `notes`: krótka uwaga po polsku tylko wtedy, gdy coś warto wiedzieć (fałszywy przyjaciel, użycie, forma formalna).
- `kind`: `word`, `phrase` albo `sentence`. Zdania mają 3–10 słów.
- `placementOnly` zostaw `false` (dotyczy tylko podstaw z paczki startowej).
- Bez audio, bez obrazków, tylko pola ze schematu (dodatkowe pola są odrzucane).

## Format paczki

Pola obowiązkowe (pełny schemat: `schemas/pack.schema.json` w repozytorium):

- korzeń: `schemaVersion` (zawsze `1`), `packId`, `packVersion`, `title`, `createdAt` (ISO 8601), `topics`; opcjonalnie `description`, `basedOnExport`,
- temat: `id`, `title`, `order`, `lessons`; opcjonalnie `description`,
- lekcja: `id`, `title`, `order`, `difficulty` (1–5; liczba klocków-pułapek = difficulty + 1, maks. 5), `items`; opcjonalnie `grammarNote` (`id`, `title`, `body` w prostym Markdownie: `**pogrubienie**`, `*kursywa*`, listy `- `),
- element: `id`, `kind`, `id_text`, `pl_text`, `register`; opcjonalnie `acceptedId`, `acceptedPl`, `distractorsId`, `distractorsPl`, `notes`, `tags`, `placementOnly`.

Przykład:

```json
{
  "schemaVersion": 1,
  "packId": "loop-001",
  "packVersion": 1,
  "title": "SLA i zgłoszenia",
  "description": "Rozmowy o awariach i umowach serwisowych.",
  "createdAt": "2026-11-02T18:00:00Z",
  "basedOnExport": "2026-11-02T17:45:12Z",
  "topics": [
    {
      "id": "telco",
      "title": "Sieci i telco",
      "order": 4,
      "lessons": [
        {
          "id": "telco.sla",
          "title": "SLA i zgłoszenia",
          "order": 2,
          "difficulty": 3,
          "grammarNote": {
            "id": "telco.sla.note",
            "title": "Przedrostek ter-",
            "body": "**ter-** tworzy stan albo coś mimowolnego:\n\n- *hubung* → **ter**hubung (połączony),\n- *ganggu* → **ter**ganggu (zakłócony)."
          },
          "items": [
            {
              "id": "telco.tiket",
              "kind": "word",
              "id_text": "tiket",
              "pl_text": "zgłoszenie",
              "register": "neutral",
              "acceptedPl": ["ticket", "zgłoszenie serwisowe"],
              "notes": "Też „bilet”."
            },
            {
              "id": "telco.s-tiket-dibuka",
              "kind": "sentence",
              "id_text": "Tiketnya sudah dibuka kemarin.",
              "pl_text": "Zgłoszenie zostało otwarte wczoraj.",
              "register": "formal",
              "acceptedPl": ["Wczoraj otwarto zgłoszenie.", "Zgłoszenie otwarto wczoraj."],
              "distractorsId": ["membuka", "besok"],
              "distractorsPl": ["jutro", "zamknięte"]
            }
          ]
        }
      ]
    }
  ]
}
```

## Odpowiedź

1. **Krótki raport po polsku** (maks. ok. 15 punktów): najważniejsze wnioski z analizy, co dodajesz i dlaczego, proponowane poprawki istniejących elementów, jeśli nie trafiły do tej paczki.
2. **Paczka**: jeden kompletny blok ```json z poprawnym JSON-em, bez komentarzy, gotowy do zapisania jako plik `.json` i importu. Przed odpowiedzią sprawdź, czy ID są unikalne w całej paczce i nie kolidują z innymi elementami z `content`.
