# 0025 · Сборка фазы 4.1: поиск JDK, проверка заглушек и jar

- **Дата:** 2026-10-19
- **Агент:** build-engineer
- **Версия мода:** 2.4.0
- **Статус:** done (в игре не проверено)
- **Связанные отчёты:** 0022, 0023, 0024

## Задача
Фаза 4.1, шаг build-engineer: (1) чтобы `build.py` находил `javap` без правки PATH; (2) полный прогон; (3) независимо проверить заглушки новых классов Forge и Minecraft из 0024; (4) проверить jar.

## Что сделано
- `tools/build.py`: новая функция `find_tool(name)` для `javac`, `javap`, `java`. Порядок поиска: PATH, затем `JAVA_HOME/bin`, затем типовые каталоги (Windows: `Program Files`, `Program Files (x86)` и `LOCALAPPDATA`, подпапки `Java`, `Eclipse Adoptium`, `Microsoft`, `Zulu`, `BellSoft`, `Amazon Corretto`, `Semeru`; Linux и macOS: `/usr/lib/jvm`, `/Library/Java/JavaVirtualMachines`). Более новые версии идут первыми. Если ничего не найдено, `build.py` завершается с понятным сообщением (что искал и что сделать: поставить JDK 17+ или задать `JAVA_HOME`). Функция `run()` сама подставляет найденный путь, остальной код не менялся.
- Проверено на этой машине: в PATH есть `javac` и `java` (оболочка Oracle `javapath`), но нет `javap`. Он найден как `C:\Program Files\Java\jdk-22\bin\javap.exe`. Сообщение об ошибке проверено искусственно (поиск отключён вручную). Установок и изменений за пределами проекта нет.
- Полный прогон прошёл, в том числе на этой машине без правки PATH.
- Заглушки Forge и Minecraft сверены с настоящими API 1.20.1 (см. «Проверка»). Расхождений, дающих `NoSuchMethodError`, не найдено.
- Jar проверен: 222 записи, 96 классов, в нём нет `net/`, `stubs/`, `mezz`, `com/mojang`. Лишних файлов вне `com/explosionwand`, `assets`, `data`, `META-INF`, `pack.mcmeta` нет.

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `tools/build.py` | `find_tool` / `_jdk_dirs`: поиск javac/javap/java по PATH, JAVA_HOME и типовым каталогам; сообщение об ошибке |
| `reports/0025-2026-10-19-build-engineer-phase-4-1.md` | этот отчёт |

## Проверка
- `python tools/build.py`: 96 классов, SRG исходники/байткод 207/208, неизвестных 0, проблем ID 0, `[3b]` gen_dungeons 2 файла 0 проблем, DungeonTest OK (20092 блока, 3 зала, составной данж 250), MazeTest OK, jar `dist/explosionwand-1.20.1-2.4.0.jar` (426 КБ).
- Jar: `mods.toml` `version="2.4.0"`. Есть оба `.nbt` (`rift_test_entrance`, `rift_test_halls`), рецепт, модель и текстура `seal_shard_rift`, `pack.mcmeta`. `ru_ru.json` и `en_us.json` валидный JSON. `jei` в `mods.toml` — только объявленная зависимость, классов mezz в jar нет.
- Новых SRG-имён по сравнению с 0024 нет. Выборочно сверил обратным поиском 20 имён из 0024 (`m_61124_`, `m_61081_`, `m_6215_`, `m_49965_`, `m_8999_`, `m_7702_`, `m_7983_`, `m_36321_`, `m_21255_`, `m_5661_`, `m_237110_`, `m_8895_`, `m_164861_`, `m_7176_`, `m_77762_`, `m_6883_`, `m_7328_`, `m_128365_`, `m_128431_`, `m_128469_`). Смысл и дескрипторы совпадают: `m_61124_` возвращает `Object` (стирание `S`), как в заглушке `StateHolder<O,S>`.
- Заглушки по настоящему Forge/Minecraft 1.20.1 (по памяти и дескрипторам из таблиц):
  - `net.minecraftforge.event.entity.EntityTeleportEvent` и вложенные `EnderPearl`, `ChorusFruit`: имена и пакет верны, оба вложенных класса отменяемые. `getEntity()` приходит из `EntityEvent` и возвращает `Entity`, `getTargetX/Y/Z` возвращают `double`. Код вызывает методы через `EntityTeleportEvent`, поэтому ковариантное переопределение (в `EnderPearl` есть `getPlayer()`) не мешает.
  - `PlayerEvent.PlayerRespawnEvent` (пакет `event.entity.player`): верно. `getEntity()` у `PlayerEvent` возвращает `Player` (ковариантно, мост есть), дескриптор совпадает.
  - `BlockEvent.EntityPlaceEvent` (пакет `event.level`, в 1.20.1 уже не `event.world`): верно, `getEntity()` возвращает `Entity`, `getPos()` из `BlockEvent`.
  - `LivingDeathEvent`: пакет `event.entity.living` верный, `getEntity()` из `LivingEvent` возвращает `LivingEntity`.
  - `PlayerInteractEvent.RightClickItem` и `RightClickBlock`: верно, `getLevel`, `getPos`, `getHand`, `getItemStack` и `getEntity` совпадают по типам возврата.
  - `ExplosionEvent.Detonate.getAffectedBlocks()`: `List<BlockPos>`, верно.
  - `StateHolder.m_61124_` (стирание в `Object`), `StateDefinition.m_61081_`, `Property.m_6215_` (`Optional`), `Block.m_49965_`, `BlockStateBase extends StateHolder<Block, BlockState>`: верно.
  - `SavedData.m_7176_` (абстрактный) и `m_77762_`, `DimensionDataStorage.m_164861_(Function, Supplier, String)`, `ServerLevel.m_8895_`, `m_7328_`: верно для 1.20.1.
  - `CompoundTag implements Tag`; методы `put`, `getCompound`, `getAllKeys`, `putLong` и др. по стиранию верны.
  - Расхождение только в модификаторе: в заглушке `BlockGetter.m_7702_` объявлен как `default`, в настоящем он абстрактный. На байткод не влияет (владелец в вызове — `ServerLevel`, дескриптор тот же).
  - Для `Loot.isFilledChest`: `ChestBlockEntity` реализует `Container`, проверка `instanceof Container` в игре сработает.

## Проверить в игре
Всё из «Проверить в игре» отчёта 0024 остаётся в силе, плюс подозрения, найденные при сверке:
- Подписка `addListener(Consumer<T>)` через ссылки на методы с вложенными событиями (`EntityTeleportEvent.EnderPearl`, `ChorusFruit`, `PlayerRespawnEvent`) — шаблон такой же, как у старых слушателей, но с новыми классами. Если в логе `IllegalArgumentException` при старте — сначала смотреть сюда.
- `EntityTeleportEvent.EnderPearl` в 1.20.1 принимает `ServerPlayer`, а не любую сущность. Для других сущностей (например, эндермена) событие не шлётся; защита рассчитана на игрока, это нормально. Проверить, что жемчуг в данже реально отменяется, а не просто возвращает жемчуг без телепорта.
- `PlayerRespawnEvent` приходит и при возвращении из Энда; код использует только `PENDING`, поэтому лишнего не сделает, но смерть и возрождение в другом измерении (Измерения мода) стоит проверить: жаровня работает только в обычном мире.
- `BlockEvent.EntityPlaceEvent` для ведра воды или лавы: Forge в 1.20.1 не шлёт его для ведра (шлёт `FillBucketEvent`). Это уже отмечено в 0024 (пункт г): ведро в данже ставить можно, проверить.
- `.nbt` в `data/explosionwand/structures/` ванильный загрузчик шаблонов тоже видит (`/place template explosionwand:rift_test_halls`). Формат с `DataVersion`, но ни разу не загружался настоящей игрой: если `/place` выдаст ошибку, вид данжа (нашим парсером) это не ломает.
- Чтение `.nbt` через `getResourceAsStream` из jar в Forge — по-прежнему первая вещь для проверки (в логе `данж rift: нет шаблона`).

## Решения и почему
- Поиск JDK сделан только на стороне `build.py`, без правки PATH и переменных окружения пользователя: ничего не нужно ставить или удалять.
- `javac`/`java` тоже идут через `find_tool`: иначе при отсутствии JDK в PATH сборка падала бы с `FileNotFoundError` уже на шаге компиляции.
- Заглушку `BlockGetter.m_7702_` не менял: расхождение `default` против абстрактного на совместимость в игре не влияет.

## Проблемы и открытые вопросы
- Заглушки сверены с настоящими API по памяти и по таблицам имён; настоящих jar Forge под рукой нет, так что полная гарантия возможна только запуском игры.
- ROADMAP не менял (по правилам: закрывает main после ревью).

## Git (выполняет пользователь)
```
git add tools/build.py reports/0025-2026-10-19-build-engineer-phase-4-1.md
git commit -m "build(build): поиск javac/javap по JAVA_HOME и типовым каталогам JDK" -m "- find_tool: PATH, JAVA_HOME, Program Files/Java, Adoptium и др." -m "- понятное сообщение, если JDK не найден" -m "- отчёт 0025: проверка заглушек и jar фазы 4.1"
```

## Следующий шаг
code-reviewer: ревью фазы 4.1 (отчёты 0024 и 0025). После ревью main закрывает 4.1 и открывает 4.2.
