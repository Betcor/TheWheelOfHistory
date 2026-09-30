# 0043. Протокол клієнт-сервер: повідомлення, JSON і фрейми

- **Статус:** прийнято
- **Дата:** 2026-10-01

## Контекст

Навіть в одиночній грі клієнт говорить із вбудованим сервером повідомленнями (через Netty `LocalChannel`), а не
викликає рушій напряму. Модуль `protocol` досі був порожній. Перш ніж будувати транспорт і `SessionActor`, потрібні
самі повідомлення, їхній формат на дроті, рукостискання з версією протоколу й хешем контенту, передача карти й
формат помилок для гравця (відкрите питання: як підставляти подробиці помилки в текст).

## Рішення

- **Обсяг — лише протокол** (рішення автора): повідомлення, JSON, фрейми, рукостискання, карта частинами. Транспорт
  (`server/transport`, `LocalChannel`/TCP), `SessionActor` і перехід клієнта з прямого `EmbeddedServer.newWorld` на
  повідомлення — наступними кроками.
- **Повідомлення** (`kolo.protocol.message`) — `sealed interface` з вкладеними `record`: `ClientMessage.Hello
  (protocolVersion, contentHash)`, `ClientMessage.CreateWorld(seed, players, npcShare)`; `ServerMessage.Welcome
  (protocolVersion, contentHash)`, `ServerMessage.Error(code, details)`, `ServerMessage.MapStart(seed, width, height,
  cellCount, countries)`, `ServerMessage.MapCells(first, cells)`. Вміст — типи рушія (`CellView`, `CountryView`,
  `ErrorCode`), без окремих DTO: представлення світу (`engine/view`) уже й є тим, що бачить клієнт. Межі параметрів
  світу перевіряє сервер, не повідомлення.
- **Версія.** `Protocol.VERSION` = 1; несумісна зміна повідомлень чи JSON — нова версія.
- **Рукостискання** (`Handshake`): перше повідомлення — `Hello`; сервер (`accept`) і клієнт (`confirm`) звіряють спершу
  версію протоколу (з іншою версією решті повідомлення не можна вірити), потім хеш контенту. Розбіжність —
  `VersionMismatchException` з `part` (`protocol` / `content`), `client`, `server`.
- **Карта — частинами** (рішення автора): `MapStart` (усе, крім комірок) і `MapCells` по `Protocol.MAP_CHUNK_CELLS` =
  1000 комірок (`MapChunks.split`). `MapAssembler` на клієнті вимагає порядку без пропусків і повторів (TCP і
  `LocalChannel` порядок зберігають, інший — помилка сервера): `map_not_started`, `map_already_started`,
  `unexpected_first`, `too_many_cells`, `map_incomplete`; невалідна зібрана карта — з первинною помилкою. Частини
  дають малий фрейм і можливість показати прогрес.
- **JSON** (`kolo.protocol.codec.MessageJson`) — вручну `JsonGenerator`, як у снапшотах (ADR 0041), без анотацій
  Jackson на типах рушія й без модуля для `Optional`: UTF-8 без пробілів, `type` першим (`hello`, `create_world`,
  `welcome`, `error`, `map_start`, `map_cells`), поля `snake_case` у сталому порядку, відсутнє — `null`, enum —
  `snake_case`, точка — `[x, y]`, многокутник — плаский масив. Читання суворе (`MessageNode`): невідомий `type` для
  цього напрямку, невідоме чи відсутнє поле, дублікат ключа, зайве після кореня, не той тип — `PROTOCOL_ERROR` з
  `location` (`cells[3].site`) і `problem`; невалідне значення — з `cause` і подробицями первинної помилки.
  Прочитане повідомлення дорівнює записаному.
- **Фрейми** (`ProtocolPipeline.server/client`) — однакові для TCP і `LocalChannel`: 4 байти довжини (big-endian) +
  JSON, не довше `Protocol.MAX_FRAME_BYTES` = 4 МБ. Вхідний завеликий фрейм — `TooLongFrameException` до читання;
  вихідне завелике повідомлення — невдалий запис (`EncoderException` з `PROTOCOL_ERROR`, `frame_too_large`), щоб
  відправник знав причину. Jackson і Netty — лише в `kolo.protocol.codec` (ArchUnit `ProtocolBoundariesTest`);
  `netty-codec` — `api`, бо `ChannelPipeline` у публічному API.
- **Помилки для гравця.** `ServerMessage.Error.of(GameException)`; подробиці на дроті — `String`, `Boolean` або `Long`
  (JSON не розрізняє `int` і `long`, а прочитане має дорівнювати надісланому), нецілі числа й інше — рядком.
  Підстановка в текст — **іменовані плейсхолдери** `{назва}` (рішення автора): `Texts.error(code, details)` у клієнті,
  проста заміна без `MessageFormat`. Щоб гравець не побачив сирого `{field}`, текст може підставляти лише
  **обов'язкові подробиці коду**: `ErrorCode.requiredDetails()` (новий параметр enum), `GameException` перевіряє їх
  при створенні (`IllegalArgumentException` — помилка програміста), `ServerMessage.Error` — при читанні
  (`MISSING_DEFINITION` → `PROTOCOL_ERROR`). Поки обов'язкові: `VERSION_MISMATCH` — `part`, `client`, `server`;
  `SAVE_VERSION_TOO_NEW` — `version`, `supported`. Решта кодів додасть свої разом із текстами, що їх показують.
  `PROTOCOL_ERROR` отримав опис подробиць (`location`, `problem` або `cause`).

## Наслідки

- Найбільший світ (16 гравців, багато NPC, 5670 комірок): карта — 7 повідомлень, ~1,5 МБ JSON, найбільший фрейм
  ~270 КБ (межа 4 МБ); запис + фрейми + читання + збирання ~55 мс (бюджет-тест < 500 мс).
- Смок-тест через справжній `LocalChannel`: рукостискання й карта частинами. **Помічено:** коли сервер і клієнт
  ділять один `IoEventLoop` (одна група з одним потоком), відповідь клієнта, записана з `channelRead` під час
  читання відповіді сервера, не доходила до сервера до закриття каналу; з окремими групами (як і буде у вбудованому
  сервері) усе працює. З'ясувати при побудові транспорту — або завжди давати серверу й клієнту окремі групи.
- Нові повідомлення (лобі, накази, представлення стану, записи коліс) додаються до `sealed`-ієрархій — компілятор
  вимагає обробити їх у кодеку.
- `Budget` тестів сервера перенесено в `kolo.server` (публічний) — ним користуються й тести протоколу, й збереження.
