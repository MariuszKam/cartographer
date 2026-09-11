# VS Cartographer - Roadmapa projektu

## Cel projektu

VS Cartographer to weekendowy projekt w Javie do offline'owej analizy save'ow Vintage Story Homo Sapiens.

Pierwszy cel jest bardzo praktyczny: otworzyc plik `.vcdbs` jako baze SQLite w trybie read-only, odczytac pozycje gracza, zapisac HOME, policzyc dystans i kierunek, a nastepnie wygenerowac obraz PNG z mapa oraz markerami HOME / PLAYER.

Docelowo projekt moze rozwinac sie w wydajny offline world analyzer: narzedzie, ktore potrafi renderowac eksplorowany swiat, pokazywac teren, bloki powierzchniowe, geologie, znaczniki, struktury i ogromne atlasy kafelkowe.

## Stack technologiczny

- Java 21 lub Java 25
- `sqlite-jdbc` do odczytu `.vcdbs`
- `BufferedImage` oraz `ImageIO` do generowania PNG
- standardowe API Javy, bez Springa
- prosty CLI jako pierwszy interfejs
- architektura modulowa, ale bez ciezkich frameworkow

## Zasady projektu

- Najpierw dzialajacy `whereami`, dopiero potem mapa.
- `.vcdbs` otwieramy read-only.
- Nie modyfikujemy save'a gry.
- Każdy etap powinien miec maly, sprawdzalny wynik.
- Parsery binarne budujemy defensywnie: walidacja, czytelne bledy, testy na fixture'ach.
- MVP ma byc proste i uzyteczne, nie kompletne.
- Wydajnosc rozwijamy stopniowo po ustabilizowaniu formatu danych.

## Co da sie odczytac z `.vcdbs`

Plik `.vcdbs` jest baza SQLite uzywana przez save Vintage Story. W zaleznosci od wersji gry i save'a moze zawierac m.in. tabele:

- `playerdata` - dane gracza, w tym serializowana pozycja encji gracza
- `chunk` - dane chunkow swiata, zwykle pelniejsze i ciezsze niz mapa
- `mapchunk` - dane mapy dla kolumn swiata
- `mapregion` - wieksze regiony mapy, przydatne dla renderowania i indeksowania
- `gamedata` - dane globalne save'a

Zakres mozliwosci zalezy od tego, co gra zapisala w save'ie. VS Cartographer powinien traktowac `.vcdbs` jako zrodlo offline, a nie jako zywy stan gry. Narzedzie moze analizowac tylko dane obecne w save'ie: odkryte regiony, zapisane chunki, dane mapy i stan gracza z ostatniego zapisu.

## Ograniczenia `.vcdbs`

- Save moze nie zawierac danych dla nieodkrytych obszarow.
- Nie kazda informacja widoczna w grze musi byc latwo dostepna wprost w SQLite.
- Format danych binarnych moze zmieniac sie miedzy wersjami Vintage Story.
- `mapchunk` moze byc szybszy do renderowania mapy, ale mniej szczegolowy niz pelne dane `chunk`.
- Pelny skan blokow powierzchniowych bedzie kosztowniejszy niz renderowanie z danych mapowych.
- Projekt powinien miec mechanizm wykrywania wersji/ksztaltu danych i czytelnie komunikowac brak wsparcia.

## Proponowana architektura

```text
vs-cartographer/
└── src/main/java/
    └── cartographer/
        ├── Main.java
        ├── cli/
        │   ├── CommandRouter.java
        │   ├── WhereamiCommand.java
        │   ├── HomeCommand.java
        │   └── RenderMapCommand.java
        ├── save/
        │   ├── VcdbsReader.java
        │   ├── SqliteSaveConnection.java
        │   └── ProtobufWireReader.java
        ├── parser/
        │   ├── PlayerDataParser.java
        │   ├── EntityPlayerParser.java
        │   ├── MapChunkParser.java
        │   ├── ChunkParser.java
        │   └── RegistryParser.java
        ├── model/
        │   ├── WorldPosition.java
        │   ├── ChunkCoordinate.java
        │   ├── RegionCoordinate.java
        │   ├── HomeLocation.java
        │   ├── MapTile.java
        │   └── BlockInfo.java
        ├── navigation/
        │   ├── DirectionCalculator.java
        │   └── HomeStore.java
        ├── render/
        │   ├── MapRenderer.java
        │   ├── TerrainPalette.java
        │   ├── MarkerRenderer.java
        │   └── PngWriter.java
        ├── atlas/
        │   ├── TilePyramid.java
        │   ├── LodRenderer.java
        │   └── IncrementalRenderIndex.java
        └── perf/
            ├── RenderCache.java
            └── ParallelChunkScanner.java
```

Na starcie ta struktura powinna powstawac stopniowo. Nie trzeba tworzyc wszystkich pakietow od razu. Najpierw minimalny pionowy przeplyw: CLI -> SQLite -> parser gracza -> wynik w konsoli.

## Milestone 0.1 - `whereami`

Cel: odczytac pozycje gracza z `.vcdbs`.

Zakres:

- otwarcie pliku `.vcdbs` jako SQLite read-only
- odczyt pierwszego lub wskazanego rekordu z `playerdata`
- wyciagniecie danych gracza z binarnego payloadu
- sparsowanie pozycji X/Y/Z
- wypisanie pozycji w konsoli

Przykladowy CLI:

```text
vs-cartographer whereami ancestorrex.vcdbs
```

Przykladowy wynik:

```text
PLAYER
X: 512341.4
Y: 112.0
Z: 511782.7
```

Definition of Done:

- dziala na znanym save'ie testowym
- nie zapisuje nic do `.vcdbs`
- ma czytelny blad, gdy brakuje tabeli `playerdata`
- ma maly test parsera lub fixture z oczekiwanymi wspolrzednymi

## Milestone 0.2 - HOME i nawigacja

Cel: zapisac lokalny HOME i policzyc dystans/kierunek od gracza.

Zakres:

- komenda `home set`
- komenda `home show`
- komenda `nav home`
- lokalny plik konfiguracji poza save'em
- dystans w blokach na plaszczyznie X/Z
- kierunek tekstowy: N, NE, E, SE, S, SW, W, NW
- opcjonalnie kat/bearing w stopniach

Przykladowy CLI:

```text
vs-cartographer home set 512100 511900
vs-cartographer nav home ancestorrex.vcdbs
```

Przykladowy wynik:

```text
PLAYER: 512341, 511782
HOME:   512100, 511900

Distance: 268 blocks
Direction: NW
```

Definition of Done:

- HOME nie jest zapisywany do save'a gry
- dystans i kierunek sa deterministyczne
- program dobrze obsluguje brak ustawionego HOME

## Milestone 0.3 - koordynaty chunkow i regionow

Cel: ustabilizowac matematyke wspolrzednych.

Zakres:

- konwersja world X/Z -> chunk X/Z
- konwersja world X/Z -> mapchunk
- konwersja mapchunk -> mapregion
- model wspolrzednych z testami
- przygotowanie pod renderowanie mapy

Definition of Done:

- testy obejmuja dodatnie i ujemne wspolrzedne
- zasady zaokraglania sa jawne
- output `whereami` moze pokazac tez chunk/region

## Milestone 0.4 - parser `mapchunk`

Cel: odczytac dane mapowe nadajace sie do pierwszego renderu.

Zakres:

- lista dostepnych `mapchunk`
- odczyt zakresu mapchunkow wokol gracza
- parser wysokosci/koloru/warstw, o ile dane sa dostepne w save'ie
- model powierzchni mapy
- diagnostyka: ile chunkow znaleziono, ile pominieto, ile nieudanych parse'ow

Definition of Done:

- mozna odczytac obszar wokol gracza
- program rozroznia brak danych od bledu parsera
- parser jest izolowany od renderera

## Milestone 0.5 MVP - PNG z mapa

Cel: wygenerowac pierwszy obraz mapy z markerami PLAYER i HOME.

Zakres:

- render prostokatnego obszaru wokol gracza
- zapis PNG przez `BufferedImage` i `ImageIO`
- marker PLAYER
- marker HOME, jesli ustawiony
- prosta paleta kolorow
- skala np. 1 piksel = 1 blok lub 1 piksel = 1 map cell, zaleznie od danych

Przykladowy CLI:

```text
vs-cartographer map render ancestorrex.vcdbs --radius 1024 --out map.png
```

Definition of Done:

- powstaje czytelny PNG
- PLAYER i HOME sa widoczne
- brak HOME nie blokuje renderowania
- program nie zuzywa niekontrolowanie pamieci przy typowym promieniu

## Milestone 0.6 - terrain map

Cel: mapa zaczyna przekazywac charakter terenu.

Zakres:

- rozroznienie ladu, wody i wysokosci
- cieniowanie reliefu
- paleta dla biomow/typow powierzchni, jesli dane sa dostepne
- legenda kolorow
- opcje renderowania: topographic, simple, high-contrast

Definition of Done:

- mapa jest czytelniejsza niz surowy zrzut danych
- render nadal dziala na slabszym sprzecie
- paleta jest konfigurowalna lub latwa do wymiany

## Milestone 0.7 - block registry

Cel: zmapowac identyfikatory blokow na zrozumiale nazwy.

Zakres:

- odczyt rejestrow blokow z save'a, jesli sa dostepne
- model `BlockInfo`
- mapowanie ID -> code, np. `game:soil-*`, `game:rock-*`, `game:water-*`
- fallback dla nieznanych ID

Definition of Done:

- parser nie zaklada na sztywno ID blokow
- nieznane bloki nie przerywaja analizy
- registry moze byc uzyte przez surface scanner

## Milestone 0.8 - surface scanner

Cel: wykryc prawdziwe bloki powierzchniowe na podstawie danych chunkow.

Zakres:

- odczyt pelnych `chunk`
- skan kolumn X/Z od gory w dol
- wykrywanie pierwszego istotnego bloku powierzchniowego
- ignorowanie powietrza i opcjonalnie roslinnosci
- wynik: surface block, wysokosc, typ materialu

Definition of Done:

- mozna porownac surface scanner z `mapchunk`
- wynik jest wolniejszy, ale bardziej szczegolowy
- skaner dziala zakresowo, nie musi ladowac calego swiata naraz

## Milestone 1.0 - Detailed Cartographer

Cel: pelniejsza mapa eksplorowanego swiata.

Zakres:

- warstwy renderowania: teren, woda, bloki powierzchniowe, markery
- eksport PNG dla duzego obszaru
- konfiguracja promienia, skali, stylu i warstw
- indeks dostepnych regionow
- czytelne raporty o brakujacych danych

Definition of Done:

- narzedzie jest realnie uzyteczne po sesji gry
- mozna wygenerowac mape bazy i okolicy
- architektura pozwala dodawac kolejne warstwy bez przepisywania renderera

## Milestone 1.5 - mapa geologiczna

Cel: analiza skal, wysokosci i przekrojow.

Zakres:

- wykrywanie typow skal
- mapa geologiczna powierzchni
- opcjonalne przekroje pionowe
- wykrywanie nachylenia i form terenu
- eksport osobnych warstw geologicznych

Definition of Done:

- mapa pomaga planowac eksploracje i wydobycie
- geologia jest osobna warstwa, nie miesza sie z podstawowa mapa terenu
- brak danych geologicznych jest komunikowany, nie zgadywany

## Milestone 2.0 - world analyzer

Cel: przejsc od mapy do analizy swiata.

Zakres:

- wykrywanie struktur i nietypowych blokow, jesli sa zapisane w chunkach
- wyszukiwanie blokow po nazwie lub wzorcu
- wlasne markery uzytkownika
- eksport raportow tekstowych/CSV
- statystyki regionow

Przykladowe komendy:

```text
vs-cartographer scan blocks ancestorrex.vcdbs --match copper
vs-cartographer markers add "Base cave" 512120 511870
```

Definition of Done:

- narzedzie potrafi odpowiedziec nie tylko "gdzie jestem", ale tez "co jest w okolicy"
- skanowanie ma limity zakresu i nie blokuje maszyny na ogromnym save'ie

## Milestone 3.0 - performance engine

Cel: wydajne przetwarzanie duzych save'ow.

Zakres:

- cache sparsowanych chunkow/mapchunkow
- rownolegle skanowanie zakresow
- streaming danych z SQLite
- ograniczenie pamieci przez przetwarzanie kafelkowe
- profilowanie hot pathow
- format cache zalezy od wersji save'a i wersji parsera

Definition of Done:

- render duzego obszaru nie wymaga ladowania wszystkiego do RAM
- kolejne uruchomienia moga korzystac z cache
- bledy pojedynczych chunkow nie przerywaja calego renderu

## Milestone 3.5 - incremental rendering

Cel: renderowac tylko to, co sie zmienilo.

Zakres:

- indeks ostatnio przetworzonych mapchunkow/chunkow
- wykrywanie nowych lub zmienionych rekordow
- aktualizacja tylko wybranych kafelkow
- cache obrazow posrednich
- szybkie dogenerowanie mapy po kolejnej sesji gry

Definition of Done:

- drugi render tego samego save'a jest znacznie szybszy
- zmiana HOME/markerow nie wymaga ponownego parsowania calego terenu
- cache mozna bezpiecznie usunac i odbudowac

## Milestone 4.0 - LOD, tiles i atlas

Cel: atlas ogromnego swiata zamiast jednego wielkiego PNG.

Zakres:

- renderowanie kafelkow
- poziomy szczegolowosci LOD
- piramida tile'i podobna do map webowych
- eksport katalogu atlasu
- opcjonalny prosty viewer HTML
- indeks regionow i zakresow eksploracji

Przykladowa struktura wyjscia:

```text
atlas/
├── index.html
├── metadata.json
└── tiles/
    ├── z0/
    ├── z1/
    └── z2/
```

Definition of Done:

- bardzo duzy swiat da sie przegladac bez jednego gigantycznego obrazu
- LOD pozwala szybko zobaczyc calosc i przyblizyc szczegoly
- atlas moze byc przenoszony jako zwykly katalog plikow

## Kolejnosc implementacji

Rekomendowana kolejnosc:

1. Minimalny projekt Java z CLI.
2. Otwieranie `.vcdbs` read-only.
3. `whereami`: pozycja gracza.
4. Testy parsera gracza na znanym save'ie.
5. HOME jako lokalna konfiguracja.
6. Dystans i kierunek do HOME.
7. Konwersje world/chunk/mapchunk/region.
8. Parser `mapchunk`.
9. Pierwszy render PNG.
10. Markery PLAYER/HOME.
11. Paleta terenu i relief.
12. Registry blokow.
13. Surface scanner.
14. Warstwy szczegolowej mapy.
15. Geologia i przekroje.
16. Skaner swiata i raporty.
17. Cache oraz rownolegle przetwarzanie.
18. Incremental rendering.
19. Tile rendering, LOD i atlas.

## Minimalne MVP

MVP konczy sie wtedy, gdy mozna uruchomic:

```text
vs-cartographer whereami ancestorrex.vcdbs
vs-cartographer home set 512100 511900
vs-cartographer nav home ancestorrex.vcdbs
vs-cartographer map render ancestorrex.vcdbs --radius 1024 --out map.png
```

I otrzymac:

- pozycje gracza
- dystans i kierunek do HOME
- plik PNG z mapa
- marker PLAYER
- marker HOME

## Pytania techniczne do rozstrzygniecia po drodze

- Czy `playerdata` moze zawierac wielu graczy i jak wybieramy aktywnego?
- Ktore pola `mapchunk` sa stabilne dla uzywanej wersji Vintage Story?
- Czy kolor mapy lepiej brac z danych mapowych, czy budowac z registry blokow?
- Jak rozpoznawac wersje formatu save'a?
- Gdzie trzymac lokalna konfiguracje HOME i markerow?
- Jakie sa bezpieczne limity promienia renderu dla pierwszego MVP?
- Czy atlas powinien miec prosty viewer HTML, czy tylko katalog tile'i?

## Priorytet na najblizszy krok

Najblizszy krok to `0.1 whereami`.

Nie zaczynamy od renderowania mapy. Najpierw trzeba miec pewny odczyt pozycji gracza, bo ten sam przeplyw techniczny bedzie fundamentem dla HOME, markerow, zakresu renderowania i pozniejszej diagnostyki save'a.
