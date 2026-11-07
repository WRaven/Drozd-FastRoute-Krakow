# Drozd FastRoute

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
2. Otwórz [wydania na GitHub](https://github.com/WRaven/MPK-Krakow-FastRoute/releases)
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

Aktualizacja istniejącej instalacji: **Ustawienia → Sprawdź aktualizacje →
Pobierz i zainstaluj**. Jeśli Android tego wymaga, użyj **Zezwól na instalowanie**
i potwierdź instalację. Nie odinstalowuj aplikacji przed zgodną aktualizacją:
zachowasz lokalny rozkład i ustawienia. Wersje testowa i zwykła są oddzielnymi
aplikacjami; nie aktualizują się wzajemnie.

## Obsługa aplikacji

| Funkcja | Sposób użycia |
| --- | --- |
| Wyszukiwanie trasy | Wpisz adres/nazwę początku i celu, wybierz podpowiedzi lub użyj bieżącej lokalizacji. **Wybierz przystanek** umożliwia ręczny wybór przystanku. Edytor daty i godziny pozwala planować wyjazd lub najpóźniejszy przyjazd. Wyniki zawierają odcinki przejazdu, szacowany marsz i dostępne odjazdy na żywo. |
| Dom i praca | Użyj **Ustaw: Do domu** / **Ustaw: Do pracy**, aby zapisać cel, godzinę przyjazdu i dni tygodnia. Kolejne kliknięcia szukają trasy na najbliższy termin harmonogramu. Zapisane harmonogramy zmienisz w Ustawieniach lub szybką akcją harmonogramu pracy. |
| Nazwy skrótów | W Ustawieniach zmień **Nazwa skrótu do domu** i **Nazwa skrótu do pracy**, następnie wybierz **Zapisz nazwy skrótów**. Nazwy nie mogą być puste. Pozostają po restarcie i pojawiają się również w nagłówkach harmonogramów oraz powiadomieniach. Zmiana nazwy nie zmienia celu ani harmonogramu. |
| Własna trasa | Przytrzymaj wynik, aby zapisać jego początek i cel jako pojedynczy skrót **Własny**. Zapis kolejnej własnej trasy zastępuje poprzednią. |
| Mapa Live | Wybierz **Live**. Mapa startuje na Krakowie, a następnie centruje się na dostępnej lokalizacji użytkownika. Znaczniki pojazdów pokazują numery linii i zgłoszony kierunek. Kliknij pojazd lub przystanek, aby zobaczyć szczegóły; mapę można przesuwać i przybliżać. **Pojazd** oznacza numer taborowy, nie numer linii. Ograniczenia szczegółów kursu opisano niżej. |
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
  Kliknięcie przystanku pokazuje najbliższe odjazdy; kliknięcie pojazdu próbuje
  pokazać przebieg i rozkład kursu z dostępnymi opóźnieniami GTFS-Realtime.
  Szacunki statyczne i dane na żywo są odróżnione. Mapa obsługuje przesuwanie,
  gesty i przyciski powiększania. Startuje na Krakowie, następnie centruje i
  oznacza bieżącą lokalizację, jeśli uprawnienie oraz pozycja są dostępne.
  Bez uprawnienia pozostaje na Krakowie.
- Numery linii autobusowych są dopasowywane do znanych nazw linii. Tramwaje
  korzystają z `GTFS_KRK_T.zip`, którego identyfikatory kursów pasują do feedu
  pozycji tramwajowych, w przeciwieństwie do zbiorczego `GTFS_KRK.zip` planera.
  Mapowanie tramwajowe jest przechowywane w pamięci przez godzinę. Brak numerów
  jest jawnie sygnalizowany. Numery taborowe **Pojazd** pozostają osobnym polem.
  Mapa i pojazdy mają widoczne oznaczenia źródeł. Naprawa nazw linii nie migruje
  geometrii ani szczegółowego rozkładu wybranego kursu na osobne feedy A/T.
  Znaczniki przystanków rosną od małych kropek w widoku miasta do zwykłego rozmiaru
  przy zbliżeniu; rozmiary pojazdów i znacznika użytkownika pozostają bez zmian.
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
- Zbiorczy GTFS planera oraz feedy autobusowe/tramwajowe mają różne identyfikatory.
  Numery linii tramwajowych są rozwiązywane z pasującego `GTFS_KRK_T.zip`, lecz
  wybrany kurs Live może nadal zgłaszać brak w bazie GTFS planera. Poprawny numer
  linii nie gwarantuje dostępności szczegółowego rozkładu ani geometrii kursu.
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
| Pojazdy Live i opóźnienia | [Feedy GTFS-Realtime ZTP](https://gtfs.ztp.krakow.pl/) i pasujący [GTFS tramwajowy](https://gtfs.ztp.krakow.pl/GTFS_KRK_T.zip) |
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
git clone https://github.com/WRaven/MPK-Krakow-FastRoute.git Drozd-FastRoute-Krakow
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
   startu i czytelność ikon pasków systemowych.
3. Otwórz Live z uprawnieniem lokalizacji i bez niego; sprawdź centrowanie oraz
   numery linii na znacznikach i po kliknięciu pojazdu. Oddal mapę do widoku miasta,
   następnie przybliż: kropki przystanków powinny płynnie rosnąć i ponownie maleć
   po oddaleniu; wybrany przystanek pozostaje wyróżniony.
4. Zmień obie nazwy skrótów, zapisz i zrestartuj; sprawdź nazwy, cele i harmonogramy.
5. Sprawdź ruch paska ZTP i wiersz gruntu/wiatru w pogodzie, także przy większej
   czcionce.

## Wersjonowanie i zgodne aktualizacje

- Zwykłe wydania używają tagów semantycznych `vMAJOR.MINOR.PATCH`, np. `v1.2.0`.
  Tag staje się Androidowym `versionName`.
- GitHub Actions nadaje numer uruchomienia workflow jako `versionCode`.
  Gradle odczytuje `APP_VERSION_NAME` / `APP_VERSION_CODE`, a lokalnie korzysta
  z `git describe --tags --always`.
- Stały identyfikator zwykłej aplikacji to `pl.wraven.mpkfastroute`. Nie zmieniaj
  go po opublikowaniu pierwszego APK.
- Aktualizacje wymagają tego samego `applicationId` i klucza podpisu.
  Zmiana któregoś uniemożliwia instalację na istniejącą aplikację.
- Workflow zwykłego wydania kończy się błędem, jeśli brakuje czterech sekretów
  podpisu; nie publikuje po cichu APK bez podpisu lub z innym kluczem.

Przed pierwszym zwykłym wydaniem utwórz jeden keystore i bezpiecznie go zachowaj.
Ustaw sekrety repozytorium GitHub Actions bez udostępniania ich wartości w czacie
lub commitowania do repozytorium:

| Sekret | Wartość |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Plik keystore zakodowany w Base64 |
| `ANDROID_KEYSTORE_PASSWORD` | Hasło keystore |
| `ANDROID_KEY_ALIAS` | Alias klucza podpisu |
| `ANDROID_KEY_PASSWORD` | Hasło klucza podpisu |

Nie zmieniaj klucza ani haseł między wydaniami. Utrata klucza uniemożliwia
instalację APK podpisanego inaczej jako aktualizacji.

Publikacja zwykłej wersji przez tag:

```powershell
git tag -a v1.0.0 -m "Release v1.0.0"
git push origin v1.0.0
```

Workflow buduje podpisany APK z wersją w nazwie, dołącza go do wydania GitHub
i zachowuje artefakt w Actions.

Testowe APK z gałęzi `main` używają osobnego pakietu
`pl.wraven.mpkfastroute.debug` i nazwy **Drozd FastRoute (test)**.
Dotychczasowe identyfikatory pakietów celowo pozostają bez zmian dla zgodności
aktualizacji. Ustaw osobny, stały keystore testowy w sekretach:

| Sekret | Wartość |
| --- | --- |
| `TEST_KEYSTORE_BASE64` | Testowy keystore zakodowany w Base64 |
| `TEST_KEYSTORE_PASSWORD` | Hasło testowego keystore |
| `TEST_KEY_ALIAS` | Alias testowego klucza |
| `TEST_KEY_PASSWORD` | Hasło testowego klucza |

Przykład utworzenia testowego keystore poza repozytorium w Windows/PowerShell:

```powershell
$keyPath = Join-Path $HOME "mpk-fastroute-test.jks"
$keytool = "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"
& $keytool -genkeypair -v -keystore $keyPath -storetype JKS `
  -alias mpk-fastroute-test -keyalg RSA -keysize 2048 -validity 10000 `
  -dname "CN=MPK FastRoute Test, OU=Development, O=WRaven, C=PL"
[Convert]::ToBase64String([IO.File]::ReadAllBytes($keyPath)) | Set-Clipboard
```

Wartość ze schowka wpisz do `TEST_KEYSTORE_BASE64`, alias `mpk-fastroute-test`
do `TEST_KEY_ALIAS`, a wybrane hasła do odpowiednich sekretów.
Zachowaj keystore i nie zmieniaj go: CI używa go do aktualizacji jednej wersji
testowej na drugą bez usuwania lokalnej bazy. Wersja testowa nie współdzieli
danych ze zwykłą. Brak sekretów testowych nie zatrzymuje walidacji, ale wyłącza
upload testowego APK. Buildy pull requestów służą tylko walidacji i nie publikują
artefaktów APK.

CI na `main` sprawdza aplikację i przesyła podpisany testowy artefakt, jeśli
sekrety są dostępne; nie tworzy automatycznie wydania testowego. Aby plik był
dostępny na telefonie i przez aktualizator, opublikuj zweryfikowany APK jako
publiczny prerelease, nie draft, z wersją w nazwie pliku. Nie zastępuj go APK
podpisanym lokalnie. Aktualizator odczytuje wersję CI z nazwy, np.
`Drozd-FastRoute-Krakow-v0.1.0-ci.N-buildN-debug.apk`, i wybiera najwyższą wersję
w odpowiednim kanale, nie opiera się na kolejności listy wydań.
Nazwy wcześniej opublikowanych APK nadal są obsługiwane. Nowe artefakty Actions
używają nazwy `Drozd-FastRoute-Krakow-ci-build-N`.

## Etapowa zmiana nazwy repozytorium

Na czas wersji przejściowej repo pozostaje pod nazwą `WRaven/MPK-Krakow-FastRoute`.
Planowana nowa nazwa to `WRaven/Drozd-FastRoute-Krakow`.

1. Opublikuj i zainstaluj przejściowy APK, gdy repo ma jeszcze starą nazwę.
   Aktualizator tej wersji akceptuje adresy pobierania wydań dokładnie z tych dwóch
   repozytoriów. Główne API, link wydań i kod QR pozostają przy obecnym adresie
   do czasu zmiany nazwy.
2. Potwierdź instalację przed zmianą nazwy tego samego repozytorium GitHub.
   Sekrety repo, konfiguracja Actions i klucze podpisu pozostają w tym repozytorium;
   nie twórz nowego repo i nie generuj nowych kluczy podpisu.
3. Po zmianie nazwy zaktualizuj git remote, główne adresy aktualizatora,
   dokumentację i linki wydań. Sprawdź API, pobranie APK i zgodną aktualizację
   z zainstalowanej wersji przejściowej.
4. Nie używaj ponownie starej nazwy repo: przekierowania GitHub wymagają, aby
   pozostała wolna. Zobacz [instrukcję zmiany nazwy repozytorium GitHub](https://docs.github.com/en/repositories/creating-and-managing-repositories/renaming-a-repository).

Starsze APK akceptują wyłącznie starą ścieżkę pobierania wydań. Same przekierowania
GitHub nie sprawią, że ich aktualizator zaakceptuje pliki pod nową nazwą;
użytkownicy takich wersji mogą potrzebować ręcznej instalacji zgodnego APK
przejściowego.

Nie zmieniaj identyfikatorów aplikacji Android, kluczy podpisu, nazw baz ani kluczy
ustawień podczas migracji. Widoczna marka i nazwa repo są niezależne od stałej
tożsamości Androida. Oznaczenie nieoficjalności wyjaśnia niezależność projektu,
ale nie gwarantuje braku kolizji ze znakami towarowymi.
