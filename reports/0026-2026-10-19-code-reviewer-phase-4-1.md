# 0026 · Ревью фазы 4.1 «Конвейер данжей (техника)»

- **Дата:** 2026-10-19
- **Агент:** code-reviewer
- **Версия мода:** 2.4.0
- **Статус:** done
- **Связанные отчёты:** 0022, 0023, 0024, 0025 (не редактировались)

## Задача
Ревью фазы 4.1 перед коммитом. Что смотрел: падения и риски в игре (`Dungeons.tick`, `DungeonData`, `DungeonGuard`), уборку мобов залов, жаровни, дверь, Осколок Печати: Разлом, регрессии палочек, `Loot.chest` (0023), рецепты (0022), версию 2.4.0, правила кода. Сверка с критерием ROADMAP: «тестовый данж находится Осколком и честно проходится; всё в „Проверить в игре“; 2.4.0».

## Вердикт: **REQUEST CHANGES**
Два major, оба чинятся в несколько строк. Первый: защиту данжа можно обойти снаружи, если колдовать палочками, которые правят блоки напрямую. Второй: при перезаходе зал может засчитаться зачищенным, хотя мобы живы. Пока они не исправлены, критерий «честно проходится» не выполнен, и фазу закрывать нельзя. Шаги 0022 и 0023 готовы, их можно коммитить уже сейчас.

## Что сделано
- Прочитал CLAUDE.md, ROADMAP (критерий 4.1), отчёты 0022–0025, `docs/dungeons.md`, `git status` и `git diff`. Новые файлы смотрел целиком: `Dungeons`, `DungeonGuard`, `DungeonData`, `DungeonTemplate`, `SealShardItem`. Diff смотрел по `ExplosionWandMod`, `Ticker`, `Loot`, `Boss*`, lang, рецептам, `mods.toml`, `history.md`, `ids.py`.
- Посмотрел, как существующие палочки правят блоки и телепортируют (`Rift`, `BlackHole`, `IceStorm`, `Chronos`, `Mimic`, `TimeStop`, `PortalGun`, `Tesseract`). Это нужно было, чтобы понять, оправдан ли список `DungeonGuard.forbidden` и где защиту можно обойти.
- Прогнал `python tools/build.py` и обратный поиск SRG по выборке.

## Замечания

### major
1. **[major] `src/com/explosionwand/Rift.java:95`, `BlackHole.java:214`, `IceStorm.java:158`, `Chronos.java:126`, `Mimic.java:156` (+ `DungeonGuard.java:54-67`): защита данжа обходится снаружи.** `DungeonGuard.onUseItem` отменяет палочку, только если **игрок** стоит в границах данжа. Эффекты же ставят блоки напрямую через `m_7731_`, а это не вызывает ни `BreakEvent`, ни `Detonate`. Значит, игрок может встать в паре блоков за границей и:
   - Чёрной дырой (прицел 64, кратер радиусом 45) выжечь вход, лестницу и залы вместе с дверью и провалом;
   - Разлом-каньоном (30 блоков вниз) прорезать путь прямо к логову.
   Данж ломается навсегда: части не перестраиваются, `partsDone` уже стоит. Это противоречит ROADMAP («блоки внутри не ломаются») и `history.md` 2.4.0.
   → Сделать общий хелпер, например `Dungeons.protectedAt(ServerLevel, BlockPos)` (по образцу `inside`, но с уже полученным `DungeonData`). В циклах `Rift.cut`, `BlackHole.carve`, `IceStorm` (установка льда и шипов), `Chronos` (восстановление), `Mimic` (стены) пропускать позиции внутри данжа, как сейчас пропускается бедрок (`m_60800_ < 0`). В `DungeonGuard.onUseItem` по желанию проверять ещё и точку прицела (`Aim`). Тогда «нельзя палочкой изнутри» и «нельзя снаружи в данж» будут работать одинаково.
2. **[major] `src/com/explosionwand/Dungeons.java:386-391`: зал засчитывается зачищенным раньше, чем загрузились его мобы.** Проверка «загружено ли» — `m_46805_` (чанк в статусе FULL). Но сущности чанка в 1.17+ подгружаются из своего хранилища асинхронно, иногда на несколько тиков позже. После перезапуска `SPAWNED_AT` пуст, а `getOrDefault(tag, tk - 1000L)` даёт отсрочку 0. Сценарий: игрок вышел из игры в логове с недобитыми мобами (или телепортировался к данжу) → при входе `alive()` на первом же 10-тиковом проходе может вернуть пустой список → `hallsCleared` записывается навсегда, «Зал зачищен», сундук награды без боя, а живые мобы появляются следом. Для 4.4 (логово с боссом) это та же дыра.
   → (а) Если записи нет, считать, что зал только что разбужен: `SPAWNED_AT.computeIfAbsent(tag, k -> tk)` вместо `getOrDefault(..., tk - 1000)`, то есть 30 тиков отсрочки после загрузки. (б) Лучше проверять загрузку сущностей, а не чанка: `ServerLevel.m_143319_(long chunkPos)` (yarn `ServerWorld.isChunkLoaded(J)Z`, в Mojang это `areEntitiesLoaded`; смысл проверить через `lookup.py --srg m_143319_` и по исходникам) для чанка центра зала, а лучше для всех чанков зала. Та же проверка нужна перед `spawnHall` (строка 415): иначе при незагруженных сущностях `have = 0`, и недобитые продублируются.

### minor
3. **[minor] `Dungeons.java:408-411, 421`: мобы, ушедшие из рамки, копятся.** У мобов `PersistenceRequired:1b`, а `alive()` ищет только в рамке «границы + 24». Зомби, пошедший за игроком наверх и дальше, никогда не исчезает и не учитывается в `have`. При следующем пробуждении зал доспавнит полный набор, хотя часть мобов жива. → В «все ушли» (строка 393) и раз в N секунд убирать всех с тегом `ewd`, которые вне рамки любого данжа. Либо возвращать сбежавших на точку спавна.
4. **[minor] `Dungeons.java:388`: в мирном режиме зал зачищается сам.** Враждебные мобы, созданные `summon`, в мирном режиме сразу исчезают, через 30 тиков зал «зачищен», а логово выдаёт сундук. → Не будить и не зачищать залы при `m_46791_()` == PEACEFUL (`Difficulty.valueOf("PEACEFUL")`), показывать сообщение «в мирном режиме данж спит». Или хотя бы указать это в `docs/dungeons.md`.
5. **[minor] `Dungeons.java:315-323`: данж в океане.** Если все 8 точек мокрые, остаётся последняя, и данж ставится в воду. Высота берётся по `MOTION_BLOCKING_NO_LEAVES`, а это поверхность воды. Части ставятся с флагом 18 (без обновления соседей), поэтому вода стоит, но первое же обновление (открытие двери флагом 3) пускает её внутрь. → Если сухой точки нет, в этой клетке данж не регистрировать (Осколок поведёт к соседней клетке). Либо добавить в шаблон оболочку или «пробку» против воды.
6. **[minor] `Dungeons.java:179-184`: статические `PLACING`, `SPAWNED_AT`, `LAST_INSIDE`, `PENDING` не чистятся при остановке сервера.** В одиночной игре после «Выйти в меню» во время постройки `Placing` держит старый `ServerLevel` (утечка всего мира) и больше не удаляется. Ключи вида `rift_0_0` одинаковы во всех мирах, так что состояние переходит в следующий открытый мир. Это мелочь, но для 4.2+ лучше сразу. → Чистить их на `ServerStoppedEvent` (и `Later.TASKS` тоже, это старый долг).
7. **[minor] `Dungeons.java:206`: флаг 18 при постановке частей.** Без обновления соседей на стыке с миром остаются висящая вода и лава, песок и гравий. Позже они «проснутся» от любого обновления. Для теста терпимо, для больших данжей 4.2 → после постановки части один проход `m_7731_(..., 3)` или `updateNeighborsAt` по внешней оболочке. Либо оболочка из камня в шаблоне (генератор).
8. **[minor] `ROADMAP.md` (строка 4.6), `docs/world.md:104`, `docs/concepts/mobs_city.png`: правки main без отчёта.** К 4.1 не относятся, отчёта на шаг нет (CLAUDE.md, «Отчёты: ОБЯЗАТЕЛЬНО»). → main оформляет их своим отчётом и коммитом (см. Git ниже: в коммиты 4.1 не включены).
9. **[minor] Ведро воды или лавы в данже работает** (Forge шлёт `FillBucketEvent`, а не `EntityPlaceEvent`), естественный спавн мобов внутри не отключён. Это уже есть в 0024 и 0025 («Проверить в игре») и для теста не блокирует. → В 4.2 отменять `FillBucketEvent` с целью внутри данжа и `MobSpawnEvent.FinalizeSpawn` (или `CheckSpawn`) в границах.

### nit
10. **[nit] `src/com/explosionwand/Dungeons.java:215-238`: отступление от правила CLAUDE.md** «setblock со свойствами — через `Cmd`». Здесь свойства ставятся через `StateDefinition`. Решение оправдано (около 20 тыс. блоков, команды медленные, в 0024 это записано в «Отклонено»), но правило теперь расходится с кодом. → main дописывает в «Правила кода» исключение: «массовая постановка частей данжа — `Dungeons.state` + `m_7731_`».
11. **[nit] Область коммита `dungeon`** в git-командах 0024 не входит в список областей CLAUDE.md. → Ниже используется `dims`. Если нужна своя область, main добавляет `dungeon` в CLAUDE.md.
12. **[nit] `Dungeons.java:373`: `int bit = 1 << h.id`** ограничивает данж 31 залом (`int` в `DungeonData`), и никакой проверки нет. Для Разлома (7 залов и побочные комнаты) хватит, но стоит проверять `h.id < 31` в `gen_dungeons.py --check` или перейти на `long`.
13. **[nit] `DungeonData.java:32`: если шаблона нет, защита молча выключается.** Об этом есть только одна трассировка из `tick` и то, если до неё дойдёт. → Один раз писать в лог понятную строку («данж rift: нет шаблона, защита выключена»).
14. **[nit] `resources/data/explosionwand/recipes/seal_shard_rift.json:3`: `"category": "equipment"`.** Для ключа-указателя логичнее `"misc"`. На игру не влияет.
15. **[nit] `SealShardItem.java:33`:** `m_19092_` возвращает успех на любой ПКМ, и в выживании рука махает впустую. → Вне творческого режима возвращать `pass`.

### Проверено, замечаний нет
- **Падения в тике:** `Dungeons.tick` обёрнут в `try/catch`, трассировка печатается один раз. Отложенный телепорт возрождения идёт через `Later`, где исключения тоже ловятся. В обработчиках событий пути до NPE не нашёл: `rec != null` ⇒ `def != null` (`contains` проверяет), `Fx.state("soul_campfire[...]")` проверяется на `null`. Тяжёлого каждый тик нет: элитры — цикл по игрокам и записям, остальное раз в 10 тиков, постановка ограничена 1500 блоками за тик.
- **SavedData:** `m_164861_(Function, Supplier, String)` — сигнатура 1.20.1, `m_77762_` вызывается при каждом изменении, хранится только в обычном мире (`Dims.get`). Обратный поиск `m_128359_/m_128405_/m_128461_/m_128451_/m_36321_/m_6325_/m_46805_/m_6249_/m_7731_/m_8999_/m_7500_` по смыслу совпадает.
- **Телепорты:** жемчуг и хорус отменяются, и если игрок внутри, и если цель внутри. Палочки из списка используют только `m_7203_`, поэтому `RightClickItem` ловит все. Портал-ган и Тессеракт снаружи в данж не проводят: точка выхода перед гранью портала остаётся снаружи стены.
- **Список `forbidden` оправдан:** в нём каждая палочка либо телепортирует (`Portal*`, Тессеракт, Мимик, Вселение, Посох Кита, печати и Ключ Разлома переносят в другие измерения), либо правит блоки напрямую (Разлом, Чёрная дыра, Ледяная буря, Хронос, Посох Затмения). Взрывные (Взрывная, Кулак, Комета) работают, блоки бережёт `onDetonate`, огонь от взрыва на убранные позиции не ставится. Остановка времени (`TimeStop`) держит игрока на месте, а не переносит, поэтому оставлена правильно. Регрессий вне данжей нет.
- **Жаровни:** только обычный мир (`onDeath` проверяет измерение), только смерть в границах (pad 1), ключ `uuid|данж`. При возрождении берётся новый `ServerPlayer` из события, перенос через 2 тика. ПКМ лопатой или водой по жаровне отменяется (`RightClickBlock` по метке).
- **Дверь:** отменяется любой ПКМ по ней, открывает только осколок своего данжа (`ss.dungeon.equals(m.a)`), сломать её нельзя (`onBreak`).
- **Осколок:** регистрация, вкладка, `stacksTo 1`, `RARE`; рецепт валиден; модель `item/generated` → текстура 16×16 RGBA на месте; все 14 ключей есть в обоих lang, наборы ключей ru и en совпадают.
- **0023 `Loot.chest`:** `isFilledChest` через `Container.m_7983_`, столб ставится под выбранный x, вызовы `pillar` из четырёх боссов убраны. Баланс боёв не менялся.
- **0022:** Портал-ган `IEI/IRI/ I ` = 5 слитков; нагрудник `E E/ADA/AAA`; у шлема и поножей содержимое не отличается (в `git status` M только из-за stat); апостроф в «Dreamer's Seal» в обоих местах; `history.md` 2.3.0 поправлен.
- **Правила кода:** enum через `valueOf` (`Heightmap.Types`, `InteractionHand`, `Rarity`); блоки, частицы и звуки — через `Fx` по ID (их проверяет `ids.py`, в том числе новое правило `Dungeons.state`); `summon` — через `Cmd`. Ванильные зомби и скелеты вместо `block_display` — временная заглушка до 4.3, это записано.
- **Версия:** `mods.toml` 2.4.0, раздел 2.4.0 в `history.md`, jar `dist/explosionwand-1.20.1-2.4.0.jar`.
- **Отчёты 0022–0025** заполнены по шаблону, во всех есть «Проверить в игре», в 0024 и 0025 этот раздел подробный.

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `reports/0026-2026-10-19-code-reviewer-phase-4-1.md` | этот отчёт (код не менялся) |

## Проверка
- `python tools/build.py`: 96 классов, SRG исходники/байткод 207/208, неизвестных 0, проблем ID 0, `[3b]` gen_dungeons — 2 файла, 0 проблем, DungeonTest OK (20092 блока, составной данж 250), MazeTest OK, jar `dist/explosionwand-1.20.1-2.4.0.jar` (426 КБ). Exit 0, `javap` нашёлся сам (`find_tool` из 0025 работает).
- Новые SRG-имена (выборка, обратный поиск): `m_36321_` → `PlayerEntity.stopFallFlying`, `m_6325_` → `getChunk(II)`, `m_46805_` → `isChunkLoaded(BlockPos)`, `m_6249_` → `getOtherEntities`, `m_7731_` → `setBlockState`, `m_8999_` → `ServerPlayerEntity.teleport(ServerWorld,DDDFF)`, `m_7500_` → `isCreative`, `m_128359_/m_128405_/m_128461_/m_128451_` → NBT `putString/putInt/getString/getInt`. Для исправления major 2 предложено `m_143319_` → `ServerWorld.isChunkLoaded(J)Z` (= Mojang `areEntitiesLoaded`).
- JSON: оба lang, `seal_shard_rift` (рецепт, модель) загружаются, наборы ключей ru и en совпадают.

## Проверить в игре
Всё из 0024 и 0025 остаётся в силе. Плюс то, на чём построены замечания (после исправлений):
- Встать в 5–10 блоках за границей данжа и бить по нему Чёрной дырой, Разлом-каньоном, Ледяной бурей, Хроносом, Мимиком: блоки данжа целы, мир вокруг меняется как обычно.
- Разбудить логово, оставить мобов живыми, сохранить и выйти, зайти снова (стоя в логове): зал **не** засчитан, сундука нет, мобы на месте и не продублированы. Повторить с `/tp` к данжу издалека.
- Вытащить зомби из зала на поверхность и увести на 40+ блоков, уйти на 30 с, вернуться: в зале снова 4 моба, а не 4 + сбежавший. Сбежавший убран (после исправления minor 3).
- Мирный режим: залы не засчитываются сами (после исправления minor 4).

## Решения и почему
- Major 1 не сводится к «добавить палочки в список». Список работает только для игрока внутри, а эффекты с большим радиусом колдуются снаружи. Правильное место — проверка в циклах, которые ставят блоки, по образцу пропуска бедрока.
- Major 2 оценён как major, а не minor: он ломает «честное прохождение», а логово 4.4 с боссом будет стоять на той же логике зачистки. Исправление дешёвое.
- Вне данжей поведение палочек не меняется, поэтому список `forbidden` как регрессию не считаю.

## Проблемы и открытые вопросы
- Сомнительные места из 0024 и 0025 (имена событий Forge, `getResourceAsStream` из jar) проверяются только в игре. По `getResourceAsStream`: jar мода Forge грузит как автоматический модуль, а у такого модуля все пакеты открыты, так что чтение `/data/...` должно работать. Если нет, запасной путь — `ResourceManager` сервера.
- В 4.1 не входят и коммитятся отдельно (main): `ROADMAP.md` (строка 4.6), `docs/world.md`, `docs/concepts/mobs_city.png`.

## Git (выполняет пользователь)
Шаги 0022 и 0023 независимы и готовы, их можно коммитить сейчас. Коммиты 3–4 (фаза 4.1) лучше делать после исправления major: тогда в них же войдут правленые файлы и отчёт исправления. Если хочется зафиксировать текущее состояние, можно и сейчас, исправление пойдёт отдельным коммитом. `ROADMAP.md` не включён: фаза не закрывается. В `docs/history.md` и `en_us.json` лежат правки двух шагов (0022 и 0024), поэтому они идут в коммит 4 целиком, а не по частям.
```
git add resources/data/explosionwand/recipes/portal_gun.json resources/data/explosionwand/recipes/arcane_chestplate.json reports/0022-2026-10-19-asset-engineer-review-0016-assets.md
git commit -m "fix(assets): рецепты Портал-гана и нагрудника Архимага" -m "- Портал-ган снова стоит 5 железных слитков" -m "- нагрудник вернул шаблон E E / ADA / AAA" -m "- Dreamer's Seal и history.md 2.3.0 — в коммите 2.4.0"

git add src/com/explosionwand/Loot.java src/com/explosionwand/Boss.java src/com/explosionwand/Boss2.java src/com/explosionwand/Boss3.java src/com/explosionwand/Boss4.java stubs/net/minecraft/world/level/BlockGetter.java stubs/net/minecraft/world/level/block/entity/BlockEntity.java stubs/net/minecraft/world/Container.java reports/0023-2026-10-19-boss-engineer-loot-empty-chest.md
git commit -m "fix(boss): пустой сундук добычи считается свободным" -m "- Loot.isFilledChest: обчищенный сундук перезаписывается" -m "- столб глоустоуна ставится под выбранный x внутри Loot.chest"

git add src/com/explosionwand/Nbt.java src/com/explosionwand/DungeonTemplate.java src/com/explosionwand/DungeonData.java src/com/explosionwand/Dungeons.java src/com/explosionwand/DungeonGuard.java src/com/explosionwand/SealShardItem.java src/com/explosionwand/ExplosionWandMod.java src/com/explosionwand/Ticker.java tests/DungeonTest.java resources/data/explosionwand/structures/rift_test_entrance.nbt resources/data/explosionwand/structures/rift_test_halls.nbt tools/gen_dungeons.py tools/build.py tools/ids.py stubs/net/minecraft/nbt/Tag.java stubs/net/minecraft/nbt/CompoundTag.java stubs/net/minecraft/network/chat/Component.java stubs/net/minecraft/server/level/ServerLevel.java stubs/net/minecraft/world/entity/LivingEntity.java stubs/net/minecraft/world/entity/player/Player.java stubs/net/minecraft/world/item/Item.java stubs/net/minecraft/world/level/block/Block.java stubs/net/minecraft/world/level/block/state/BlockBehaviour.java stubs/net/minecraft/world/level/block/state/StateDefinition.java stubs/net/minecraft/world/level/block/state/StateHolder.java stubs/net/minecraft/world/level/block/state/properties/Property.java stubs/net/minecraft/world/level/saveddata/SavedData.java stubs/net/minecraft/world/level/storage/DimensionDataStorage.java stubs/net/minecraftforge/event/entity/EntityTeleportEvent.java stubs/net/minecraftforge/event/entity/living/LivingDeathEvent.java stubs/net/minecraftforge/event/entity/player/PlayerEvent.java stubs/net/minecraftforge/event/entity/player/PlayerInteractEvent.java docs/dungeons.md reports/0024-2026-10-19-mod-engineer-dungeon-pipeline.md reports/0025-2026-10-19-build-engineer-phase-4-1.md
git commit -m "feat(dims): конвейер данжей и Тестовый разлом" -m "- данж по сетке из частей .nbt, реестр в SavedData обычного мира" -m "- защита блоков, взрывов, телепортов и палочек внутри" -m "- жаровни-якоря, мобы по залам, зачистка и сундук" -m "- tools/gen_dungeons.py, шаг 3b в build.py, поиск javap (find_tool)"

git add resources/data/explosionwand/recipes/seal_shard_rift.json resources/assets/explosionwand/models/item/seal_shard_rift.json resources/assets/explosionwand/textures/item/seal_shard_rift.png resources/assets/explosionwand/lang/ru_ru.json resources/assets/explosionwand/lang/en_us.json resources/META-INF/mods.toml docs/history.md reports/0026-2026-10-19-code-reviewer-phase-4-1.md
git commit -m "feat(items): Осколок Печати: Разлом, версия 2.4.0" -m "- рецепт, модель, текстура, тексты ru и en" -m "- en: Dreamer's Seal с апострофом" -m "- history.md: 2.4.0 и поправка 2.3.0" -m "- ревью 0026: REQUEST CHANGES, 2 major"
```

## Следующий шаг
- **mod-engineer:** исправить major 1 (хелпер `Dungeons.protectedAt` и пропуск в `Rift`, `BlackHole`, `IceStorm`, `Chronos`, `Mimic`) и major 2 (отсрочка `SPAWNED_AT` и проверка загрузки сущностей перед зачисткой и спавном). Желательно сразу minor 3, 4, 6. Новый отчёт со ссылкой на 0026, версия 2.4.0 остаётся, так как 2.4.0 ещё не выпущена.
- **build-engineer:** новые SRG (`m_143319_`, `m_46791_` и т.п.) проверить по смыслу, полный `build.py`.
- **code-reviewer:** повторное ревью только по исправлениям. После APPROVE main закрывает 4.1 в ROADMAP, остальные minor и nit — первым шагом 4.2.
- **main:** свой отчёт и коммит на `ROADMAP.md` 4.6, `docs/world.md`, `mobs_city.png`; исключение для `Dungeons.state` в «Правилах кода» (nit 10); при желании — область `dungeon` (nit 11).
