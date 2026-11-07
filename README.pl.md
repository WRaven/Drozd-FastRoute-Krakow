# Drozd FastRoute

<img src="./app/src/main/res/drawable-nodpi/drozd_logo.png" width="180" alt="Logo Drozd FastRoute: drozd nad trasą" />

**Polski** | [English](./README.md)

Aplikacja Android do wyszukiwania połączeń komunikacji miejskiej w Krakowie,
podglądu odjazdów i pojazdów na żywo oraz słuchania radia. Dokument opisuje obecne
funkcje aplikacji i sposób jej rozwijania. Interfejs jest obecnie po polsku;
angielska dokumentacja nie zmienia języka interfejsu.

**Rozkład jazdy Kraków — aplikacja nieoficjalna.** Drozd FastRoute jest niezależnym
projektem, nie jest oficjalną aplikacją MPK ani ZTP Kraków i nie jest z nimi
powiązana. Wcześniejsza nazwa to MPK Kraków FastRoute; zmiana nazwy wyświetlanej
nie zmienia istniejącej instalacji ani zapisanych danych.

## Instalacja i pierwszy start

1. Wymagany jest Android 8.0 lub nowszy i internet do pierwszego pobrania rozkładu.
2. Otwórz [wydania na GitHub](https://github.com/WRaven/Drozd-FastRoute-Krakow/releases)
   i pobierz APK z wybranego kanału. Publiczne wersje testowe są oznaczone jako
   prerelease i **Test build N**, a zwykłe wydania mają tag `vMAJOR.MINOR.PATCH`.
   W kanale testowym wybierz najwyższy numer buildu, nie tylko pierwszą pozycję
   na liście wydań.
3. Otwórz APK, w razie potrzeby zezwól przeglądarce lub menedżerowi plików na
   instalację z tego źródła i potwierdź instalację w Androidzie.
4. Uruchom aplikację i poczekaj na pierwszy import rozkładu GTFS. Postęp jest
   widoczny; wyszukiwanie tras staje się dostępne po zakończeniu importu.
5. Zezwól na lokalizację podczas używania aplikacji, aby automatycznie ustawiać
   początek trasy, centrować mapę Live i pobierać pogodę. Początek trasy można
   również wpisać ręcznie.

Stabilne **1.0.0** ma nazwę **Drozd FastRoute**, bez słowa „test”, i aktualizuje
istniejącą wersję testową z zachowaniem pakietu oraz klucza podpisu.
Prerelease `upgrade-v1.0.0` zawiera ten sam stabilny APK dla starszych
aktualizatorów, które szukają tylko wersji testowych. Po instalacji aplikacja
release sprawdza wydania stabilne.

Od **1.0.1** aktualizator, link wydań i kod QR korzystają bezpośrednio z nowej
nazwy repozytorium. Zainstalowane 1.0.0 wykryje aktualizację przez przekierowanie
GitHub ze starego adresu; pakiet, podpis i dane lokalne pozostają bez zmian.

Aktualizacja istniejącej instalacji: **Ustawienia → Sprawdź aktualizacje →
Pobierz i zainstaluj**. Jeśli Android tego wymaga, użyj **Zezwól na instalowanie**
i potwierdź instalację. Nie odinstalowuj aplikacji przed zgodną aktualizacją:
zachowasz lokalny rozkład i ustawienia. Buildy debug i release mają teraz wspólną
stałą tożsamość pakietu; do normalnego korzystania wybierz stabilny APK.

## Obsługa aplikacji

| Funkcja | Sposób użycia |
| --- | --- |
| Wyszukiwanie trasy | Wpisz adres/nazwę początku i celu, wybierz podpowiedzi lub użyj bieżącej lokalizacji. **Wybierz przystanek** umożliwia ręczny wybór przystanku. Edytor daty i godziny pozwala planować wyjazd lub najpóźniejszy przyjazd. Wyniki zawierają odcinki przejazdu, szacowany marsz i dostępne odjazdy na żywo. |
| Dom i praca | Użyj **Ustaw: Do domu** / **Ustaw: Do pracy**, aby zapisać cel, godzinę przyjazdu i dni tygodnia. Kolejne kliknięcia szukają trasy na najbliższy termin harmonogramu. Zapisane harmonogramy zmienisz w Ustawieniach lub szybką akcją harmonogramu pracy. |
| Nazwy skrótów | W Ustawieniach zmień **Nazwa skrótu do domu** i **Nazwa skrótu do pracy**, następnie wybierz **Zapisz nazwy skrótów**. Nazwy nie mogą być puste. Pozostają po restarcie i pojawiają się również w nagłówkach harmonogramów oraz powiadomieniach. Zmiana nazwy nie zmienia celu ani harmonogramu. |
| Własna trasa | Przytrzymaj wynik, aby zapisać jego początek i cel jako pojedynczy skrót **Własny**. Zapis kolejnej własnej trasy zastępuje poprzednią. |
| Mapa Live | Wybierz **Live**. Mapa startuje na Krakowie i centruje się na dostępnej lokalizacji. Kliknij pojazd po całą trasę i przewijaną listę wszystkich przystanków z godziną przyjazdu i minutami do przyjazdu. Kliknij przystanek, wybierz linię/kierunek i dni robocze, sobotę lub niedzielę/święta po pełny rozkład godzinowy. **Pojazd** oznacza numer taborowy, nie numer linii. |
| Komunikaty ZTP | Czytaj przewijany pasek ZTP/aktywnych utrudnień albo kliknij go, aby otworzyć komunikat lub szczegóły. Prędkość wynosi 36 dp/s, o 20% więcej niż wcześniej. Dostępne są również **Komunikaty MPK** oraz skrót do profilu MPK Kraków w X. |
| Pogoda | Wybierz **Pogoda**, aby zobaczyć aktualne warunki i prognozę na najbliższe 24 godziny dla swojej okolicy. Widok pokazuje temperaturę, opady, wiatr i temperaturę gruntu. Ciśnienie usunięto; godzinowy wiersz temperatury gruntu i wiatru jest krótszy. |
| Radio | Użyj **Uruchom radio** / **Zatrzymaj radio**, wybierz stację lub zarządzaj strumieniami przyciskiem z kołem zębatym. Podczas odtwarzania działają systemowe kontrolki multimediów. |
| Przypomnienia o dojeździe | Zapisz harmonogram domu/pracy i wybierz **Włącz powiadomienia**. Nadaj wymagane uprawnienia do powiadomień i lokalizacji w tle. Przypomnienie jest planowane 15 minut przed obliczonym wyjazdem. |
| Rozkład i diagnostyka | W Ustawieniach dostępne są **Odśwież rozkład GTFS** i ograniczony dziennik diagnostyczny z kopiowaniem/czyszczeniem. Automatyczna aktualizacja rozkładu sprawdza dane przy starcie i codziennie. |
| Udostępnianie | Ustawienia zawierają kod QR i link do wydań na GitHub, kontakt oraz szczegóły wydania. |

Przy widoku całego miasta (zoom 13 lub mniejszy) niezaznaczone przystanki mają
kropki 5 dp. Między poziomami 13 i 15 płynnie rosną, a od poziomu 15 odzyskują
dotychczasowe 14 dp. Wybrany przystanek zachowuje wyróżnienie 18 dp przy każdym
powiększeniu. Rozmiar reaguje na gesty i przyciski zoomu bez usuwania przystanków
ani zmiany środka mapy.

Podczas wpisywania szybkie akcje chowają się, aby zostawić miejsce na podpowiedzi.
Interfejs oraz ekran uruchamiania podążają za jasnym/ciemnym trybem telefonu.
Android 12 i nowszy korzysta także z systemowej palety dynamicznej. Aplikacja
nie ma osobnego przełącznika profilu kolorystycznego.

## Uprawnienia, sieć i zapisane dane

- Lokalizacja podczas używania aplikacji służy do ustawienia początku trasy,
  pobliskich przystanków, Live i pogody. Bez zgody można ręcznie wpisać początek
  trasy, a mapa Live zachowuje startowy widok Krakowa.
- Lokalizacja w tle i powiadomienia są wymagane do przypomnień o dojeździe.
  Radio używa systemowej usługi multimedialnej działającej na pierwszym planie.
- Internet jest potrzebny do importu GTFS, danych na żywo, komunikatów, kafelków
  mapy, pogody, radia i aktualizacji aplikacji. Używana jest aktywna sieć;
  pobieranie rozkładów, map i dźwięku może zużywać transfer komórkowy.
- Rozkład, cele, nazwy skrótów, harmonogramy, ostatnie wybory i ustawienia radia
  są zapisywane w lokalnej bazie/ustawieniach aplikacji. Kopie zapasowe Androida
  są włączone, więc ustawienia systemowego backupu mogą wpływać na kopie danych.
- Zapytania o pogodę wysyłają bieżące współrzędne do Open-Meteo. Wyszukiwanie
  adresów korzysta z geokodera dostarczanego przez urządzenie. Kafelki mapy
  pobierane są od ich dostawcy; te funkcje nie działają całkowicie offline.
- Dziennik diagnostyczny jest ograniczony i przechowywany w pamięci; nie zapisuje
  wpisanych adresów ani współrzędnych GPS. Cache pogody i mapowania linii
  tramwajowych również działa w pamięci, nie stanowi trwałej historii lokalizacji.
  Pasujące archiwa GTFS autobusów i tramwajów są przechowywane przez godzinę
  w katalogu cache aplikacji; zawierają publiczny rozkład, nie historię użytkownika.

## Stan implementacji

- Baza Room v6 przechowuje przystanki, linie, kursy, czasy na przystankach,
  przebiegi tras, kalendarze kursowania, wyjątki dat i zmaterializowany indeks
  połączeń między kolejnymi przystankami. Importer pobiera oficjalny GTFS Krakowa
  do pliku tymczasowego, raportuje postęp pobierania/importu/indeksowania i
  transakcyjnie zastępuje dane partiami. Po migracji starsze bazy budują indeks,
  zamiast wykonywać kosztowne złączenia przy każdym wyszukiwaniu.
- Planer filtruje kursy dla wybranej daty, przeszukuje odjazdy w dwugodzinnym
  oknie, wymaga minimum trzech minut na przesiadkę i uwzględnia dojścia między
  przystankami do 500 m. Indeks przestrzenny ogranicza wyznaczanie dojść do
  pobliskich przystanków. Wyniki statyczne pojawiają się przed zakończeniem
  osobnego pobierania danych na żywo.
- Klient TTSS dopasowuje przystanki po kodzie lub znormalizowanej nazwie,
  uwzględnia zgłoszone opóźnienie i wyraźnie oznacza dane statyczne, gdy dane
  na żywo są niedostępne. Dodatnie opóźnienia mają czerwone oznaczenia minut.
- Mapa Live korzysta z pozycji autobusów i tramwajów ZTP na mapie OpenStreetMap.
  Pozycje i prognozy wybranego kursu odświeżają się co 30 sekund, gdy mapa jest
  otwarta. Znaczniki pokazują linię oraz strzałkę kierunku, jeśli ZTP ją zgłasza.
  Kliknięcie pojazdu pokazuje cały przebieg i przewijaną listę wszystkich
  przystanków od początku do końca, z godziną i minutami do przyjazdu, oznaczeniem
  miniętych/bieżącego przystanku oraz dostępną prognozą GTFS-Realtime.
  Lista startuje przy bieżącym przystanku, ale można ją przewinąć wstecz.
  Szacunki statyczne i dane na żywo są odróżnione. Mapa obsługuje przesuwanie,
  gesty i przyciski powiększania. Startuje na Krakowie, następnie centruje i
  oznacza bieżącą lokalizację, jeśli uprawnienie oraz pozycja są dostępne.
  Bez uprawnienia pozostaje na Krakowie.
- Numery linii autobusowych są dopasowywane do znanych nazw linii. Tramwaje
  korzystają z `GTFS_KRK_T.zip`, którego identyfikatory kursów pasują do feedu
  pozycji tramwajowych, w przeciwieństwie do zbiorczego `GTFS_KRK.zip` planera.
  Mapowanie tramwajowe jest przechowywane w pamięci przez godzinę. Brak numerów
  jest jawnie sygnalizowany. Numery taborowe **Pojazd** pozostają osobnym polem.
  Mapa i pojazdy mają widoczne oznaczenia źródeł. Szczegóły kursu korzystają
  z pasującego `GTFS_KRK_A.zip` lub `GTFS_KRK_T.zip`, nie z bazy planera.
  Obaj odbiorcy współdzielą cache archiwum na dysku; brak kursu powoduje jedną
  wymuszoną aktualizację przed zgłoszeniem błędu. Znaczniki czasu przyjazdu mają
  pierwszeństwo przed opóźnieniem; opóźnienie jest przekazywane kolejnym
  przystankom zgodnie z GTFS-Realtime. Pominięcie przystanku, odwołanie kursu
  i brak danych nie tworzą zmyślonych prognoz. Dane Live starsze niż 90 sekund
  są zastępowane wyraźnie oznaczonym czasem rozkładowym.
  Znaczniki przystanków rosną od małych kropek w widoku miasta do zwykłego rozmiaru
  przy zbliżeniu; rozmiary pojazdów i znacznika użytkownika pozostają bez zmian.
- Wybranie przystanku w Live odczytuje pełny rozkład z lokalnego GTFS planera,
  bez oczekiwania na TTSS. Wybierz linię i kierunek, a następnie dni robocze,
  sobotę albo niedzielę/święta. Godziny i minuty obejmują całą dobę kursowania;
  `(+1)` oznacza godziny po północy następnego dnia. Każdy typ pokazuje konkretną
  wzorcową datę z najbliższych 35 dni, z uwzględnieniem kalendarza i wyjątków GTFS.
  Polskie święta są oddzielone od dni roboczych. To tablica rozkładowa, nie prognoza
  Live; szczególne daty i warianty linii mogą różnić się od pokazanego wzorca.
- Radio działa w usłudze MediaSession, obsługuje systemowe kontrolki odtwarzania
  i zapamiętuje wybraną stację. Domyślna edytowalna lista zawiera Eska Rock,
  Antyradio, Radio Pogoda i Radio Kraków.
- Dom/praca są zapisywane jako współrzędne z cykliczną godziną przyjazdu i dniami
  tygodnia. Szybkie akcje planują przyjazd na najbliższy termin harmonogramu.
  Przytrzymanie wyniku zapisuje jego końce jako pojedynczą własną trasę.
  Starsze zapisane identyfikatory przystanków nadal są obsługiwane.
- Interfejs Compose pokazuje postęp pierwszego importu, początek z lokalizacji
  lub wpisanego adresu/nazwy, podpowiedzi z opóźnieniem zapytań, ostatnie wybory,
  opcjonalny wybór przystanku, grupowanie nazw i automatyczny wybór stanowiska.
  Oferuje szybkie akcje domu/pracy/własnej trasy/radia. Osobny skrót harmonogramu
  pracy otwiera edytor godziny i dni tygodnia. Długie listy podpowiedzi przewijają
  się w ograniczonym obszarze. Ekran główny łączy alerty GTFS-RT i oficjalne
  komunikaty RSS ZTP w klikalnym pasku. Podpowiedzi ostatnich miejsc pojawiają się
  podczas wpisywania; szybkie akcje i kontrolki planowania wtedy chowają się.
  Pasek ZTP przewija się z prędkością 36 dp/s, o 20% szybciej niż domyślne Compose.
- Ekran startowy i tło okna używają tego samego jasnego/ciemnego profilu systemu
  co Compose, z kolorami tonalnymi systemu od Androida 12. Ikony pasków
  systemowych mają właściwy kontrast; po starcie Compose tło okna jest
  synchronizowane z dokładnym kolorem interfejsu.
  Przekazany obraz drozda i trasy jest przycięty lokalnie i używany jako ikona
  aplikacji, logo startowe oraz znak w tej dokumentacji. Przezroczyste narożniki
  i bezpieczne marginesy chronią cały znak przed maskami ikon/startu Androida.
  Logo nie dodaje sztucznego czasu oczekiwania przy uruchamianiu.
- Trasy adresowe porównują do sześciu grup pobliskich przystanków w promieniu
  1,5 km. Wynik obejmuje dojście do pierwszego przystanku, przesiadki i dojście
  do celu; dostępne są opcje najszybsza i z mniejszą ilością marszu.
  Odległości są szacowane wzorem Haversine przy 4,5 km/h, nie po drogach pieszych.
- Formularz obsługuje jednorazowy wyjazd lub najpóźniejszy przyjazd według daty
  i godziny. Początek domyślnie korzysta z lokalizacji przy nadanym uprawnieniu;
  szybkie akcje otwierają również oficjalne komunikaty ZTP/KMK.
- Pogoda prosi o lokalizację dopiero po kliknięciu i pokazuje aktualne warunki
  oraz prognozę godzinową na 24 godziny z Open-Meteo. Obejmuje ilość i szansę
  opadów, wiatr oraz temperaturę gruntu przy powierzchni. Ciśnienie nie jest
  pokazywane, aby skrócić godzinowy wiersz gruntu/wiatru. Obsługiwane są m.in.
  mgła i burze. Cache działa przez 15 minut dla przybliżonej lokalizacji i tylko
  w pamięci. Widok ma legendę, źródło i odnośnik do licencji CC BY 4.0.
- Zapisane harmonogramy mogą uruchamiać przypomnienie 15 minut przed obliczonym
  wyjazdem. Zwinięte powiadomienie pokazuje najbliższe odjazdy TTSS; co pięć minut
  odświeżane są alerty GTFS-Realtime istotne dla trasy. Wymagane są uprawnienia
  do powiadomień i lokalizacji w tle. Niedostępność któregoś feedu oznacza
  komunikat o niepełnych danych, nie twierdzenie o braku utrudnień. Dane ZTP
  GTFS-Realtime są udostępniane na CC BY 4.0. Zapytania korzystają z aktywnej
  sieci: Wi-Fi, gdy jest dostępne, lub transmisji komórkowej.
- WorkManager sprawdza oficjalny GTFS przy starcie i codziennie. Importuje nową
  wersję po zmianie ETag lub Last-Modified, czekając na dostępną sieć. Podczas
  importu w pasku aplikacji pojawia się mały procentowy wskaźnik. Istniejący
  rozkład pozostaje dostępny do zatwierdzenia nowego; przy pierwszym starcie
  wyszukiwanie wymaga zakończenia importu. Kalendarze określają obowiązywanie
  nowego rozkładu.
- Kody przystanków pozostają wewnętrznie do dopasowania TTSS, ale nie są
  pokazywane w etykietach. Wyszukiwanie ignoruje polskie znaki diakrytyczne
  i podpowiada do trzech ostatnio wybranych nazw przystanków.
- Ustawienia obsługują aktualizacje z GitHub, stronę wydań, kod QR, harmonogramy
  i kontakt. Nazwy skrótów są zapisywane niezależnie od celów i harmonogramów;
  trafiają także do nagłówków i powiadomień. Starsze instalacje zachowują
  **Do domu** / **Do pracy**, dopóki nazwy nie zostaną zmienione.
- Wersja testowa szuka najnowszego prerelease, a zwykła najnowszego wydania
  stabilnego. Sprawdzanie odbywa się przy starcie. **Pobierz i zainstaluj**
  rozpoczyna aktualizację, a **Pomiń** ukrywa dane wydanie do pojawienia się
  nowszego. APK jest pobierany z odpowiedniego wydania, sprawdzany pod kątem
  przynależności do aplikacji i przekazywany instalatorowi Androida.
  Instalacja wymaga potwierdzenia użytkownika i czasem zgody na instalację
  spoza sklepu. Jeśli brak pasującego APK, aplikacja jawnie to zgłasza.
- Prognozy TTSS są dopasowywane do statycznych odjazdów z wybranego początku.
  Niedopasowane lub niedostępne dane na żywo pozostają oznaczone jako statyczne.
  Ustawienia radia pozwalają zarządzać strumieniami, a akcja radia przełącza
  odtwarzanie.
- Ograniczony dziennik diagnostyczny opisuje etapy i liczności bazy/indeksu/CSA,
  pozwala kopiować/czyścić wpisy i nie zapisuje adresów ani współrzędnych GPS.

## Ograniczenia i rozwiązywanie problemów

- Po pierwszym imporcie lokalny rozkład może służyć do statycznego planowania
  bez ponownego pobierania. Dane Live, komunikaty, kafelki mapy, pogoda i radio
  nadal zależą od zewnętrznych usług.
- Planer przeszukuje dwugodzinne okno odjazdów. Dla adresów sprawdza do sześciu
  grup przystanków w promieniu 1,5 km, wymaga trzech minut na przesiadkę
  i uwzględnia dojścia między przystankami do 500 m. Nie jest pełnym planerem
  wszystkich możliwych połączeń.
- Marsz jest szacowany po odległości w linii prostej przy 4,5 km/h, nie po sieci
  dróg pieszych; przeszkody i rzeczywiste czasy mogą zmieniać wynik.
- Zbiorczy GTFS planera i feedy pojazdów mają różne identyfikatory; szczegóły
  Live używają zatem pasującego archiwum A/T. Nowy lub specjalny kurs może
  nie występować w opublikowanym archiwum ZTP; po odświeżeniu jest zgłaszany błąd.
  Bez geometrii mapa łączy przystanki i oznacza przebieg jako przybliżony.
  Tablica przystanku wymaga aktualnego importu GTFS planera; przy starych danych
  zaktualizuj bazę w ustawieniach.
- Brak metadanych Live/linii jest sygnalizowany jako dane niedostępne lub
  niepełne. Szacunki rozkładowe nie są przedstawiane jako potwierdzone dane Live.
- Przy braku lokalizacji włącz systemową usługę lokalizacji i sprawdź uprawnienia
  aplikacji. Wyszukiwanie adresów zależy także od dostępności geokodera urządzenia.
- Dostarczenie przypomnienia zależy od uprawnień, planowania pracy w tle przez
  Androida i dostępnych danych; nie jest gwarantowanym alarmem.
- Jeśli aktualizacja się nie instaluje, sprawdź kanał, pakiet, podpis, wersję
  i zgodę Androida na instalowanie. Nie kasuj aplikacji ani danych tylko po to,
  aby obejść niezgodny podpis.
- Na wąskich ekranach lub przy większych czcionkach dostępności pogoda może
  zawijać tekst. Usunięcie ciśnienia skraca szczegóły bez ukrywania reszty wartości.

## Źródła danych i oznaczenia

| Dane | Źródło |
| --- | --- |
| Rozkład planera | [Zbiorczy GTFS ZTP](https://gtfs.ztp.krakow.pl/GTFS_KRK.zip) |
| Pojazdy Live i rozkład kursu | [Feedy GTFS-Realtime ZTP](https://gtfs.ztp.krakow.pl/), pasujący [GTFS autobusowy](https://gtfs.ztp.krakow.pl/GTFS_KRK_A.zip) i [tramwajowy](https://gtfs.ztp.krakow.pl/GTFS_KRK_T.zip) |
| Prognozy odjazdów z przystanków | [TTSS Kraków](http://www.ttss.krakow.pl/) |
| Komunikaty | [Feed komunikatów ZTP](https://ztp.krakow.pl/feed?post_type=komunikat) |
| Mapa | [Autorzy OpenStreetMap](https://www.openstreetmap.org/copyright) |
| Pogoda | [Open-Meteo](https://open-meteo.com/), [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) |
| Radio | Dostawcy skonfigurowanych strumieni stacji |

Dane ZTP GTFS-Realtime i Open-Meteo mają w aplikacji oznaczenie CC BY 4.0.
Licencje źródeł danych są odrębne od licencjonowania kodu aplikacji.
Za dostępność i dokładność danych zewnętrznych odpowiadają ich dostawcy.

## Rozwój i walidacja lokalna

[Konfiguracja aplikacji](./app/build.gradle.kts) używa Java 17 oraz Android
SDK 36, z minimum Android 8.0/API 26. Korzystaj z dołączonego wrappera Gradle,
JDK zgodnego z projektem, Android SDK Platform 36 i Build Tools 36.0.0.
[Workflow CI](./.github/workflows/release.yml) korzysta z Java 17.

Przykład dla Windows/PowerShell:

```powershell
git clone https://github.com/WRaven/Drozd-FastRoute-Krakow.git Drozd-FastRoute-Krakow
Set-Location '.\Drozd-FastRoute-Krakow'
$env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Jeśli SDK jest w innym miejscu, ustaw rzeczywisty `ANDROID_HOME`.
Lokalny APK debug powstaje w `app\build\outputs\apk\debug\app-debug.apk`.
Domyślny lokalny klucz podpisu różni się od klucza testowego CI; nie używaj
takiego APK do aktualizacji zainstalowanej aplikacji podpisanej w CI.

Testy ostatnich zmian prezentacji:

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'pl.wraven.mpkfastroute.feature.search.ServiceNewsTickerTest' `
  --tests 'pl.wraven.mpkfastroute.feature.search.WeatherForecastSheetTest' `
  --tests 'pl.wraven.mpkfastroute.feature.search.LiveStopMarkerSizeTest' `
  --tests 'pl.wraven.mpkfastroute.core.update.GitHubUpdateCheckerTest' `
  --tests 'pl.wraven.mpkfastroute.ui.theme.StartupThemeTest'
```

Zestaw testów obejmuje także planer, import GTFS, mapowanie feedów Live, zapytania
pogodowe i trwały zapis nazw skrótów. Testy jednostkowe nie zastępują telefonu:

1. Zainstaluj aktualizację na poprzednią wersję testową; sprawdź zachowanie celów
   i nazw.
2. Wymuś zatrzymanie i uruchom w trybie ciemnym, następnie jasnym; sprawdź kolory
   startu i czytelność ikon pasków systemowych. Sprawdź nową ikonę oraz logo
   startowe: drozd, trasa i ramka nie powinny być obcięte.
3. Otwórz Live z uprawnieniem lokalizacji i bez niego; sprawdź centrowanie oraz
   numery linii na znacznikach i po kliknięciu pojazdu. Oddal mapę do widoku miasta,
   następnie przybliż: kropki przystanków powinny płynnie rosnąć i ponownie maleć
   po oddaleniu; wybrany przystanek pozostaje wyróżniony.
4. Zmień obie nazwy skrótów, zapisz i zrestartuj; sprawdź nazwy, cele i harmonogramy.
5. Sprawdź ruch paska ZTP i wiersz gruntu/wiatru w pogodzie, także przy większej
   czcionce.
6. Kliknij tramwaj (np. 22) i autobus: sprawdź całą trasę, przewiń od pierwszego
   do ostatniego przystanku i porównaj godzinę/minuty z oznaczeniem LIVE lub
   ROZKŁAD. Otwórz ponownie ten kurs; minięte przystanki mają pozostać dostępne.
7. Kliknij przystanek (np. Sołtysowska Zakłady), wybierz linię/kierunek i każdy
   typ dnia. Przewiń tabelę godzin do końca; sprawdź wpisy wieczorne i po północy,
   datę wzorca i jawny komunikat dla linii niekursującej w wybranym dniu.

## Wersjonowanie i zgodne aktualizacje

- Zwykłe wydania używają tagów semantycznych `vMAJOR.MINOR.PATCH`, np. `v1.2.0`.
  Tag staje się Androidowym `versionName`.
- GitHub Actions nadaje numer uruchomienia workflow jako `versionCode`.
  Gradle odczytuje `APP_VERSION_NAME` / `APP_VERSION_CODE`, a lokalnie korzysta
  z `git describe --tags --always`.
- Stały identyfikator aplikacji to `pl.wraven.mpkfastroute.debug`, zachowany
  z zainstalowanej wersji testowej, aby 1.0.0 utrzymało dane użytkownika.
  Wewnętrzna przestrzeń nazw Kotlin pozostaje `pl.wraven.mpkfastroute`.
  Sufiks `.debug` nie włącza debugowania w APK release ani słowa „test” w nazwie.
- Aktualizacje wymagają tego samego `applicationId` i klucza podpisu.
  Zmiana któregoś uniemożliwia instalację na istniejącą aplikację.
- Workflow zwykłego wydania kończy się błędem, jeśli brakuje czterech sekretów
  podpisu; nie publikuje po cichu APK bez podpisu lub z innym kluczem.

Dotychczasowy klucz podpisu jest teraz stałym kluczem zgodnych aktualizacji
stabilnych i testowych. GitHub Actions celowo używa już skonfigurowanych
sekretów poniżej; nie generuj klucza zastępczego dla 1.0.0:

| Sekret | Wartość |
| --- | --- |
| `TEST_KEYSTORE_BASE64` | Dotychczasowy keystore zakodowany w Base64 |
| `TEST_KEYSTORE_PASSWORD` | Dotychczasowe hasło keystore |
| `TEST_KEY_ALIAS` | Dotychczasowy alias klucza |
| `TEST_KEY_PASSWORD` | Dotychczasowe hasło klucza |

Lokalne buildy release nadal odczytują `ANDROID_KEYSTORE_FILE`,
`ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` i `ANDROID_KEY_PASSWORD`.
Wskaż w nich ten sam stały klucz. CI mapuje istniejące sekrety na te parametry
builda bez zmiany samych sekretów.

Nie zmieniaj klucza ani haseł między wydaniami. Utrata klucza uniemożliwia
instalację APK podpisanego inaczej jako aktualizacji.

Publikacja zwykłej wersji przez tag:

```powershell
git tag -a v1.0.0 -m "Release v1.0.0"
git push origin v1.0.0
```

Workflow buduje podpisany APK z wersją w nazwie, dołącza go do wydania GitHub
i zachowuje artefakt w Actions.

APK debug z gałęzi `main` nadal mają nazwę **Drozd FastRoute (test)** i sprawdzają
prerelease. APK release mają nazwę **Drozd FastRoute**, nie pozwalają na debugowanie
i sprawdzają wydania stabilne. Kanał zależy od typu builda (`BuildConfig.DEBUG`),
nie od zachowanego sufiksu pakietu. Oba buildy współdzielą dane lokalne i stały
podpis; nie są osobnymi aplikacjami.
Brak sekretów podpisu nie zatrzymuje walidacji na `main`, ale wyłącza upload
testowego APK; CI release wtedy kończy się błędem. Buildy pull requestów służą
tylko walidacji i nie publikują artefaktów APK.

CI na `main` sprawdza aplikację i przesyła podpisany testowy artefakt, jeśli
sekrety są dostępne; nie tworzy automatycznie wydania testowego. Aby plik był
dostępny na telefonie i przez aktualizator, opublikuj zweryfikowany APK jako
publiczny prerelease, nie draft, z wersją w nazwie pliku. Nie zastępuj go APK
podpisanym lokalnie. Aktualizator odczytuje wersję CI z nazwy, np.
`Drozd-FastRoute-Krakow-v0.1.0-ci.N-buildN-debug.apk`, i wybiera najwyższą wersję
w odpowiednim kanale, nie opiera się na kolejności listy wydań.
Nazwy wcześniej opublikowanych APK nadal są obsługiwane. Nowe artefakty Actions
używają nazwy `Drozd-FastRoute-Krakow-ci-build-N`.

Dla pierwszego przejścia do stabilnego 1.0.0 dołącz zweryfikowany APK release
zarówno do stabilnego wydania `v1.0.0`, jak i niedraftowego prerelease
`upgrade-v1.0.0`. To drugie pozwala starym instalacjom pobrać ten sam APK bez
zmiany ich dotychczasowego kodu aktualizatora. Nie zastępuj go APK debug.

## Zmiana nazwy repozytorium i zgodność

To samo repozytorium GitHub zmieniło nazwę z `WRaven/MPK-Krakow-FastRoute` na
[WRaven/Drozd-FastRoute-Krakow](https://github.com/WRaven/Drozd-FastRoute-Krakow)
po potwierdzeniu instalacji stabilnego 1.0.0. Dotychczasowe wydania, historia,
konfiguracja Actions i sekrety podpisu pozostały w tym repozytorium.

Git remote, dokumentacja oraz główne adresy aktualizatora i QR używają nowej nazwy.
Aktualizator zainstalowanego 1.0.0 korzysta z przekierowania GitHub ze starego API
i już akceptuje pobrania z obu dokładnych ścieżek repozytorium. Wersja 1.0.1
korzysta bezpośrednio z nowego adresu, zachowując tę zgodność.

Nie używaj ponownie starej nazwy repo: przekierowania GitHub wymagają, aby
pozostała wolna. Nie twórz zastępczego repo ani nowych kluczy podpisu.
Zobacz [instrukcję zmiany nazwy repozytorium GitHub](https://docs.github.com/en/repositories/creating-and-managing-repositories/renaming-a-repository).

Starsze APK akceptują wyłącznie starą ścieżkę pobierania wydań. Same przekierowania
GitHub nie sprawią, że ich aktualizator zaakceptuje pliki pod nową nazwą;
użytkownicy takich wersji mogą potrzebować ręcznej instalacji zgodnego APK
przejściowego.

Nie zmieniaj identyfikatorów aplikacji Android, kluczy podpisu, nazw baz ani kluczy
ustawień podczas migracji. Widoczna marka i nazwa repo są niezależne od stałej
tożsamości Androida. Oznaczenie nieoficjalności wyjaśnia niezależność projektu,
ale nie gwarantuje braku kolizji ze znakami towarowymi.
