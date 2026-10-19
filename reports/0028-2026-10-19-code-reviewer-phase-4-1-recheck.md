# 0028 · Повторное ревью фазы 4.1 (исправления 0027)

- **Дата:** 2026-10-19
- **Агент:** code-reviewer
- **Версия мода:** 2.4.0
- **Статус:** done
- **Связанные отчёты:** 0026 (прошлое ревью), 0027 (исправления), 0022–0025 (не редактировались)

## Задача
Повторное ревью только по исправлениям из 0027. Нужно было проверить: закрыты ли major 1 и 2 и minor 3–6; правки CLAUDE.md; ключ lang `peaceful`; `ServerStoppedEvent`; цену `removeStrays`; нет ли регрессий палочек вне данжей. Отдельно — самому найти через grep оставшиеся обходы защиты, где блоки меняются массово.

## Вердикт: **REQUEST CHANGES**
Major 1 и 2 из 0026 закрыты верно, minor 3–6 тоже. Но grep нашёл ещё один обход защиты: **`Debris.fling`**. Его вызывают Кулак и Комета, а они в данже **разрешены**. Обход ломает защиту даже изнутри данжа и позволяет снять кристаллическую дверь без Осколка. В 0026 я ошибочно записал Кулак и Комету в «безопасные» («блоки бережёт `onDetonate`»): так это моё прошлое замечание и пропустило проблему. Исправляется одной строкой (см. major 1). Повторный полный цикл не нужен, достаточно коммита с исправлением и короткой перепроверки.

## Что сделано
- Прочитал 0026, 0027, `git diff` всех изменённых файлов, `Dungeons.java` и `DungeonGuard.java` целиком.
- Grep по `src/` на `m_7731_`, `m_46597_`, `m_46961_`, `setblock`/`fill` через `Cmd`, взрывы `m_255391_` и `FallingBlockEntity.m_201971_`. Вручную прошёл Comet, ExplosionWandItem, Fist, TimeStop, Possess, Tesseract, Portal*, Ember, Debris.
- Прогнал `python tools/build.py`, сделал обратный поиск новых SRG.

## Замечания

### major
1. **[major] `src/com/explosionwand/Debris.java:24-27` (вызовы: `Fist.java:168`, `Comet.java:87`, `Rift.java:162`, `EclipseStaff.java:197`): разлёт обломков вырывает блоки данжа.** `FallingBlockEntity.m_201971_` (yarn `spawnFromBlock`, Mojang `fall`) сам ставит на место блока его жидкость, обычно воздух. `BreakEvent` и `Detonate` при этом не срабатывают. В `Debris.movable` защиты данжа нет. Кулак и Комета в `DungeonGuard.forbidden` не входят, поэтому:
   - изнутри данжа Кулак (140 попыток в оболочке 4–9 блоков) выбивает стены, пол и ступени из каменного и глубинносланцевого кирпича (твёрдость < 50);
   - снаружи удар Кулака или Кометы по двери из `amethyst_block` (`gen_dungeons.py:106`, твёрдость 1.5) вырывает дверь, и вход открывается без Осколка;
   - у Разлом-каньона и Посоха Затмения через этот же путь щит, добавленный в 0027, обходится.
   Взрыв в этих местах блоки уже не рушит (`onDetonate` их отфильтровал), а следующая строка `fling` достаёт их. Выходит, что `history.md` 2.4.0 («взрывы не рушат блоки», «эффекты снаружи блоки данжа не трогают») пока не соответствует правде.
   → В `Debris.fling` перед циклом взять `Dungeons.Shield sh = Dungeons.shield(l);`, в цикле `if (sh.at(p)) continue;` (по правилу из CLAUDE.md). `BlackHole.ripBlocks` уже защищён, Boss2 работает в своём измерении.

### minor
2. **[minor] Падающие блоки (Debris, Чёрная дыра) приземляются внутрь данжа.** После приземления блок ставится без события, а сломать его в выживании нельзя: `DungeonGuard.onBreak` отменяет поломку всего в границах. Обломок, упавший в лестницу или проход, может навсегда закупорить данж. → В 4.2 (или вместе с major 1) раз в 10 тиков в `inDungeon` удалять `FallingBlockEntity` в границах +2. Другой вариант: разрешить ломать блоки, которых нет в шаблоне (`assemble`).
3. **[minor] `Dungeons.java:449, 462`: зачистка смотрит только на чанки самого зала.** Если моб ушёл из зала в коридор, а чанк коридора ещё не загрузил сущности, `alive()` его не увидит, и зал засчитается. Для маленького Тестового разлома это почти невозможно, но в 4.2 залы и коридоры будут длиннее. → Проверять `m_143319_` для всех чанков рамки `alive()` (границы + 24), а не только зала. Можно кэшировать список чанков в `Rec`.

### nit
4. **[nit] `Dungeons.java:280-292`: `shield()` на каждый вызов собирает коробки всех когда-либо зарегистрированных данжей.** `Shield.at` линейный. За долгую игру записей станут десятки, и в `BlackHole.carve` и `Nova.carveStep` получится тысячи блоков × N коробок за тик. Сейчас это терпимо. → Позже: `shield(level, cx, cz, r)` берёт только коробки рядом с эффектом.
5. **[nit] `Dungeons.java:345-352`: `removeStrays` обходит все сущности раз в 200 тиков и на каждую с тегом `ewd` вызывает `at()` (линейно по записям).** Дорогая часть, проверка тега через `Set.contains`, нужна только для мобов с тегом. Цена O(сущностей), раз в 10 с, только при наличии данжей. Приемлемо. Запасной путь со списком UUID в 0027 уже описан.
6. **[nit] `Tesseract.java:139-145`: комната из полок ставится на высоте земля + 22 над игроком в любые пустые клетки.** Если рамка данжа выше поверхности, полки могут ненадолго встать в его воздух. Уборка ставит воздух только на свои блоки, поэтому вреда нет. Череп и розы на месте смерти двойника уже записаны в 0027. Оставить как есть.

### Проверено, замечаний нет
- **Major 1 (0026) в своём объёме закрыт.** `Shield` и `protectedAt` стоят во всех циклах, которые я перечислял: `Rift.cut`, `BlackHole.carve` и `ripBlocks`, `IceStorm.freeze` и `spike`, `Chronos.restoreBlocks`, `Mimic` (центр в данже → «в небо», стены по одной), `EclipseStaff` (`burn`, огонь, `Nova.carveStep`). Пропуск идёт по образцу бедрока, границы те же, что у `onDetonate` (pad 0). `IceStorm.shatterStep` и сброс шипов трогают только свой лёд, а он в данж уже не ставится. Остальные места с `m_7731_` (Altar, Maze, Arena*, Boss*, Loot, Tesseract) ставят своё в своих местах и измерениях. Comet, ExplosionWandItem, Fist, Ember, Possess: взрывы фильтрует `onDetonate`, огонь ставится только на оставшиеся позиции. В TimeStop и Portal* блоки не меняются. Единственный найденный обход — `Debris` (major 1 выше).
- **Major 2 (0026) закрыт.** `lookup.py --srg m_143319_` → `ServerWorld.isChunkLoaded(J)Z`. Yarn делегирует в `entityManager.isLoaded`, то есть это Mojang `ServerLevel.areEntitiesLoaded`: смысл правильный. Упаковка ключа `x & 0xFFFFFFFF | z << 32` совпадает с `ChunkPos.asLong`. Проверка стоит до будения зала (то есть перед `spawnHall`), до зачистки и до сброса в мирном режиме. `computeIfAbsent(tag, k -> tk)` вызывается, только когда игрок внутри, поэтому отсрочка 30 тиков отсчитывается от момента, когда сущности загрузились. Если игрок ушёл далеко, зал остаётся «разбуженным» с живыми сохранёнными мобами, и после возвращения дублей нет.
- **Minor 3:** `removeStrays` собирает список и только потом делает `discard`, так что `ConcurrentModification` не будет. Тег `ewd` ищется точно (`Set.contains`), а у DreamStaff тег `ewds`, они не пересекаются. Запись `SPAWNED_AT` чистится при «все ушли».
- **Minor 4:** `m_46791_` → `WorldAccess.getDifficulty` (у `Level` есть реализация), `Difficulty.valueOf("PEACEFUL")`. В мирном режиме мобы не создаются, разбуженные залы сбрасываются, сундука нет. Ключ `explosionwand.dungeon.peaceful` есть в `ru_ru.json:49` и `en_us.json:49`, оба файла — валидный JSON.
- **Minor 5:** у мёртвой клетки `dead` сохраняется в NBT, `discover` её пропускает, `nearest` (Осколок, `locate`) тоже. Тест есть.
- **Minor 6:** `clearState` чистит `PLACING`, `SPAWNED_AT`, `LAST_INSIDE`, `PENDING`, `Later.TASKS` и `warned`. Вызывается на `ServerStoppedEvent`, после остановки тика, так что гонок с итерацией `Later` нет. Заглушка `net.minecraftforge.event.server.ServerStoppedEvent` совпадает с настоящим классом Forge 1.20.1. В настоящем Forge он наследует `ServerLifecycleEvent`, но для `addListener` важен только сам класс. Слушатель зарегистрирован в `ExplosionWandMod`.
- **Регрессии:** `DungeonGuard.forbidden` срабатывает, только если игрок внутри границ (`Dungeons.at(...) == null → return`). У всех `Shield` вне обычного мира или без записей `NONE`, у `at()` пустой список даёт быстрый выход. Вне данжей палочки ведут себя как раньше.
- **CLAUDE.md:** исключение для `Dungeons.state` + `m_7731_`, правило про `Dungeons.shield` и область `dungeon`. Правки допустимы, их просил main. В правиле стоит упомянуть ещё `FallingBlockEntity.m_201971_` (см. major 1). Это решает main.
- **nit 14:** `seal_shard_rift.json` — `"category": "misc"`.
- **Правила кода** в исправлениях соблюдены: enum через `valueOf`, блоки через `Fx`/`Dungeons.state`, новых прямых ссылок на `Blocks` нет.

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `reports/0028-2026-10-19-code-reviewer-phase-4-1-recheck.md` | этот отчёт (код не менялся) |

## Проверка
- `python tools/build.py`: 97 классов, SRG исходники/байткод 209/210, неизвестных 0, проблем ID 0, `[3b]` gen_dungeons — 2 файла, 0 проблем, DungeonTest OK (20092 блока, составной 250), MazeTest OK, jar `dist/explosionwand-1.20.1-2.4.0.jar` (429 КБ).
- Обратный поиск: `m_143319_` → `ServerWorld.isChunkLoaded(J)Z` (areEntitiesLoaded), `m_46791_` → `WorldAccess.getDifficulty`, `m_8583_` → `ServerWorld.iterateEntities`, `m_19880_` → `Entity.getCommandTags`, `m_146870_` → `Entity.discard`, `m_7328_` → `getSeed`, `m_8895_` → `getPersistentStateManager`, `m_201971_` → `FallingBlockEntity.spawnFromBlock` (основа major 1).

## Проверить в игре
Всё из 0024, 0025 и 0027 остаётся в силе. Плюс, после исправления major 1:
- Встать у кристаллической двери снаружи и ударить по ней Кулаком и Кометой: дверь цела, руина цела, земля вокруг разлетается.
- Внутри зала ударить Кулаком в стену и пол: блоки данжа на месте, обломки не летят из стен.
- Если в данж всё же упал падающий блок (Чёрная дыра рядом): проверить, проходится ли лестница (minor 2).
- `m_143319_`: при первом входе в логово мобы появляются. Если не появляются никогда, проверка всегда возвращает `false` (запасной вариант из 0027: `m_46805_`).

## Решения и почему
- Новый major ставлю как major, а не minor: он ломает тот же критерий ROADMAP («блоки внутри не ломаются», «честно проходится») и позволяет обойти дверь, то есть всю механику Осколка. Исправление в одну строку, так что держать фазу открытой из-за него недорого.
- Остальные исправления 0027 принимаю. Повторно ревьюить весь код не нужно, после исправления `Debris` хватит короткой перепроверки.
- Фазу 4.1 можно коммитить уже сейчас (коммиты ниже). Исправление `Debris` пойдёт отдельным коммитом вместе с закрытием 4.1 в ROADMAP.

## Проблемы и открытые вопросы
- В 0026 я пропустил `Debris`, потому что посчитал Кулак и Комету «только взрывом». На будущее: при ревью защиты грепать ещё и `m_201971_` (падающие блоки). Это стоит добавить в правило CLAUDE.md, решает main.
- Minor 8 из 0026 (правки main без отчёта: `ROADMAP.md` 4.6, `docs/world.md`, `mobs_city.png`) ещё открыт: коммит 3 ниже идёт без отчёта main. Желательно, чтобы main добавил к нему свой отчёт.
- Остаются на 4.2 из 0026: minor 7 (флаг 18), minor 9 (ведро, естественный спавн), nit 12, 13, 15. Из этого ревью: minor 2 и 3, nit 4–6.

## Git (выполняет пользователь)
Состояние рабочей копии на момент ревью. `arcane_helmet.json` и `arcane_leggings.json` помечены M только из-за окончаний строк (содержимое не изменилось), они не включены. В `docs/history.md` и lang-файлах лежат правки 0022, 0024 и 0027, а в `CLAUDE.md` — только правки 0027. Поэтому эти файлы идут в коммиты 4 и 5 целиком. `ROADMAP.md` сейчас отличается только строкой 4.6 и идёт в docs-коммит 3. Закрытие 4.1 в ROADMAP будет позже, вместе с исправлением `Debris`.
```
git add resources/data/explosionwand/recipes/portal_gun.json resources/data/explosionwand/recipes/arcane_chestplate.json reports/0022-2026-10-19-asset-engineer-review-0016-assets.md
git commit -m "fix(assets): рецепты Портал-гана и нагрудника Архимага" -m "- Портал-ган снова стоит 5 железных слитков" -m "- нагрудник вернул шаблон E E / ADA / AAA"

git add src/com/explosionwand/Loot.java src/com/explosionwand/Boss.java src/com/explosionwand/Boss2.java src/com/explosionwand/Boss3.java src/com/explosionwand/Boss4.java stubs/net/minecraft/world/level/BlockGetter.java stubs/net/minecraft/world/level/block/entity/BlockEntity.java stubs/net/minecraft/world/Container.java reports/0023-2026-10-19-boss-engineer-loot-empty-chest.md
git commit -m "fix(boss): пустой сундук добычи считается свободным" -m "- Loot.isFilledChest: обчищенный сундук перезаписывается" -m "- столб глоустоуна ставится под выбранный x внутри Loot.chest"

git add docs/world.md docs/concepts/mobs_city.png ROADMAP.md
git commit -m "docs(agents): концепты мобов Города Спящих" -m "- docs/concepts/mobs_city.png, ссылка в world.md" -m "- ROADMAP 4.6: облик по листу концептов"

git add src/com/explosionwand/Nbt.java src/com/explosionwand/DungeonTemplate.java src/com/explosionwand/DungeonData.java src/com/explosionwand/Dungeons.java src/com/explosionwand/DungeonGuard.java src/com/explosionwand/SealShardItem.java src/com/explosionwand/ExplosionWandMod.java src/com/explosionwand/Ticker.java src/com/explosionwand/Rift.java src/com/explosionwand/BlackHole.java src/com/explosionwand/IceStorm.java src/com/explosionwand/Chronos.java src/com/explosionwand/Mimic.java src/com/explosionwand/EclipseStaff.java tests/DungeonTest.java resources/data/explosionwand/structures/rift_test_entrance.nbt resources/data/explosionwand/structures/rift_test_halls.nbt tools/gen_dungeons.py tools/build.py tools/ids.py stubs/net/minecraft/nbt/Tag.java stubs/net/minecraft/nbt/CompoundTag.java stubs/net/minecraft/network/chat/Component.java stubs/net/minecraft/server/level/ServerLevel.java stubs/net/minecraft/world/Difficulty.java stubs/net/minecraft/world/entity/LivingEntity.java stubs/net/minecraft/world/entity/player/Player.java stubs/net/minecraft/world/item/Item.java stubs/net/minecraft/world/level/Level.java stubs/net/minecraft/world/level/block/Block.java stubs/net/minecraft/world/level/block/state/BlockBehaviour.java stubs/net/minecraft/world/level/block/state/StateDefinition.java stubs/net/minecraft/world/level/block/state/StateHolder.java stubs/net/minecraft/world/level/block/state/properties/Property.java stubs/net/minecraft/world/level/saveddata/SavedData.java stubs/net/minecraft/world/level/storage/DimensionDataStorage.java stubs/net/minecraftforge/event/entity/EntityTeleportEvent.java stubs/net/minecraftforge/event/entity/living/LivingDeathEvent.java stubs/net/minecraftforge/event/entity/player/PlayerEvent.java stubs/net/minecraftforge/event/entity/player/PlayerInteractEvent.java stubs/net/minecraftforge/event/server/ServerStoppedEvent.java docs/dungeons.md CLAUDE.md reports/0024-2026-10-19-mod-engineer-dungeon-pipeline.md reports/0025-2026-10-19-build-engineer-phase-4-1.md reports/0027-2026-10-19-mod-engineer-review-0026-fixes.md
git commit -m "feat(dungeon): конвейер данжей и Тестовый разлом" -m "- данж по сетке из частей .nbt, реестр в SavedData обычного мира" -m "- защита блоков, взрывов, телепортов; эффекты палочек пропускают данж" -m "- жаровни-якоря, мобы по залам, зачистка после загрузки сущностей" -m "- мирный режим, сбежавшие мобы, океан, очистка на ServerStoppedEvent" -m "- tools/gen_dungeons.py, шаг 3b в build.py; правила данжей в CLAUDE.md"

git add resources/data/explosionwand/recipes/seal_shard_rift.json resources/assets/explosionwand/models/item/seal_shard_rift.json resources/assets/explosionwand/textures/item/seal_shard_rift.png resources/assets/explosionwand/lang/ru_ru.json resources/assets/explosionwand/lang/en_us.json resources/META-INF/mods.toml docs/history.md reports/0026-2026-10-19-code-reviewer-phase-4-1.md reports/0028-2026-10-19-code-reviewer-phase-4-1-recheck.md
git commit -m "feat(items): Осколок Печати: Разлом, версия 2.4.0" -m "- рецепт, модель, текстура, тексты ru и en (Dreamer's Seal)" -m "- history.md: 2.4.0 и поправка 2.3.0" -m "- ревью 0026 и 0028"
```
После исправления major 1 (mod-engineer, отчёт 0029) и перепроверки (0030) — финальный коммит, в него войдёт закрытие 4.1 в ROADMAP от main:
```
git add src/com/explosionwand/Debris.java ROADMAP.md reports/0029-2026-10-19-mod-engineer-debris-shield.md reports/0030-2026-10-19-code-reviewer-phase-4-1-final.md
git commit -m "fix(dungeon): обломки Кулака и Кометы не вырывают блоки данжа" -m "- Debris.fling пропускает позиции Dungeons.shield" -m "- ROADMAP: фаза 4.1 закрыта"
```
(Имена файлов 0029 и 0030 условные: взять фактические.)

## Следующий шаг
- **mod-engineer:** major 1: `Dungeons.shield` в `Debris.fling`, отчёт 0029. По желанию сразу minor 2 (падающие блоки в данже). В правило CLAUDE.md добавить `m_201971_`, если main согласен.
- **code-reviewer:** короткая перепроверка `Debris` (0030). Если всё в порядке, APPROVE.
- **main:** закрыть 4.1 в ROADMAP, отчёт на docs-коммит (minor 8 из 0026). Затем `/clear` и `/next-phase` (4.2), в первый шаг 4.2 входят minor 7 и 9 и nit 12, 13, 15 из 0026, а также minor 2 и 3 и nit 4 из 0028.
