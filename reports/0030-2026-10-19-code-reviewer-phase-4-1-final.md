# 0030 · Финальная перепроверка фазы 4.1 (Debris)

- **Дата:** 2026-10-19
- **Агент:** code-reviewer
- **Версия мода:** 2.4.0
- **Статус:** done
- **Связанные отчёты:** 0028 (major 1, minor 2), 0029 (исправление), 0022–0027

## Задача
Короткая перепроверка фазы 4.1. Нужно убедиться, что major 1 из 0028 закрыт: `Debris.fling` пропускает позиции данжа до того, как читает или убирает блок, и остальные вызовы `FallingBlockEntity.m_201971_` защищены. Проверить правку CLAUDE.md, прогнать сборку и выдать финальные git-команды всей фазы 4.1.

## Вердикт: **APPROVE WITH NITS**
Major 1 из 0028 закрыт. Blocker и major больше нет. Открытые minor и nit переносятся в 4.2 (список ниже). Фазу 4.1 можно закрывать.

## Что сделано
- Прочитал 0028 и 0029, посмотрел `git diff` по `Debris.java`, `CLAUDE.md` и `ROADMAP.md`.
- Grep по `src/` на `m_201971_`: три вызова.
  - `Debris.java:29`: перед ним стоит `if (sh.at(p)) continue;` (строка 26), до `m_8055_` и `movable`. `Shield` берётся один раз перед циклом (строка 20).
  - `BlackHole.java:144`: `shield.at(p)` на строке 139, до чтения блока.
  - `Boss2.java:560`: арена Boss2 в своём измерении. Там `Dungeons.shield` всё равно вернул бы `NONE` (`Dungeons.java:281`, только обычный мир).
- Проверил `Dungeons.Shield`/`shield()` (`Dungeons.java:269-292`): NPE нет, у пустого списка `NONE` и быстрый выход, `def()==null` и отсутствие шаблона обработаны. Вне данжей `fling` ведёт себя как раньше.
- CLAUDE.md: правило про `m_201971_` добавлено к правилу о `Dungeons.shield`. Это закрывает пожелание из 0028 («Проблемы»).
- Прогнал `python tools/build.py`.

## Замечания
### Перенос в 4.2 (не блокеры)
1. **[minor] Падающие блоки (`Debris`, `BlackHole`) приземляются внутрь данжа** (minor 2 из 0028, «Проблемы» 0029). Такой блок ставится без события, а `DungeonGuard.onBreak` не даёт его сломать, поэтому он может закупорить проход. → В 4.2: раз в 10 тиков удалять `FallingBlockEntity` в границах данжа +2 или разрешить ломать блоки, которых нет в шаблоне.
2. Из 0028 переносятся также: minor 3 (зачистка смотрит только на чанки зала), nit 4 (`shield()` собирает все коробки, линейный `at`), nit 5 и 6 (принять как есть). Из 0026: minor 7, minor 9, nit 12, 13, 15.

### nit
3. **[nit] `reports/` — нет отчёта main на docs-правки** (`ROADMAP.md` 4.6, `docs/world.md`, `docs/concepts/mobs_city.png`; minor 8 из 0026). В 0029 это зафиксировано как факт. → main кладёт свой отчёт вместе с закрытием 4.1 в ROADMAP, его можно добавить в последний коммит.
4. **[nit] Теста на `Debris.fling` нет**: нужны `ServerLevel` и сущности. Сам `Shield.at` покрыт `DungeonTest`, этого достаточно.

### Проверено, замечаний нет
- Правила кода: новых SRG-имён нет, enum, `Fx` и `Blocks` не затронуты, `Cmd` не нужен.
- Риски: исключений в тике нет, `Shield` создаётся один раз на вызов `fling`, а не на каждую попытку. Утечек сущностей нет: блоки в данже теперь не поднимаются вообще.

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `reports/0030-2026-10-19-code-reviewer-phase-4-1-final.md` | этот отчёт (код не менялся) |

## Проверка
- `python tools/build.py`: 97 классов, SRG исходники/байткод 209/210, неизвестных 0, проблем ID 0, `[3b]` gen_dungeons — 2 файла, 0 проблем, DungeonTest OK (20092 блока, составной 250), MazeTest OK, jar `dist/explosionwand-1.20.1-2.4.0.jar` (429 КБ).
- Новые SRG-имена: нет (`m_201971_` уже проверен в 0028 → `FallingBlockEntity.spawnFromBlock`).

## Проверить в игре
Всё из 0024, 0025, 0027 и 0028 остаётся в силе. Главное по этому исправлению:
- Ударить Кулаком и Кометой по кристаллической двери снаружи: дверь и руина целы, земля вокруг разлетается.
- Внутри зала ударить Кулаком в стену и пол: блоки данжа на месте, обломки из стен не летят.
- Разлом-каньон и Посох Затмения у стены данжа: блоки данжа не вырываются.
- Вне данжей разлёт обломков выглядит как раньше.
- Чёрная дыра рядом с данжем: если обломок упал на лестницу, проходится ли она (minor 1, 4.2).

## Решения и почему
- Minor про закупорку не держит фазу: критерий 4.1 («блоки данжа не ломаются») выполнен, приземление — отдельный сценарий, его чинят в 4.2.
- Git-команды ниже отличаются от 0028 в двух местах:
  - `ROADMAP.md` убран из docs-коммита 3 и перенесён в последний коммит. Сейчас в нём только строка 4.6, а закрытие 4.1 добавит main. Так весь ROADMAP идёт одним коммитом, и при повторном `git add` после коммита 3 не останется пустого шага.
  - `CLAUDE.md` перенесён из коммита 4 в последний. В нём теперь лежат правки 0027 и 0029 вместе, а правило уже ссылается на `Debris.fling`, то есть на исправление из последнего коммита.

## Проблемы и открытые вопросы
- Строка «Текущая фаза» в `ROADMAP.md` ещё указывает на 4.1. Обновить её должен main до последнего коммита. Если main этого не сделает, последний коммит всё равно корректен, но в нём будет только строка 4.6.

## Git (выполняет пользователь)
Финальные команды всей фазы 4.1, по порядку. `arcane_helmet.json` и `arcane_leggings.json` помечены M только из-за окончаний строк (`git diff --stat` пуст), они не включены.
```
git add resources/data/explosionwand/recipes/portal_gun.json resources/data/explosionwand/recipes/arcane_chestplate.json reports/0022-2026-10-19-asset-engineer-review-0016-assets.md
git commit -m "fix(assets): рецепты Портал-гана и нагрудника Архимага" -m "- Портал-ган снова стоит 5 железных слитков" -m "- нагрудник вернул шаблон E E / ADA / AAA"

git add src/com/explosionwand/Loot.java src/com/explosionwand/Boss.java src/com/explosionwand/Boss2.java src/com/explosionwand/Boss3.java src/com/explosionwand/Boss4.java stubs/net/minecraft/world/level/BlockGetter.java stubs/net/minecraft/world/level/block/entity/BlockEntity.java stubs/net/minecraft/world/Container.java reports/0023-2026-10-19-boss-engineer-loot-empty-chest.md
git commit -m "fix(boss): пустой сундук добычи считается свободным" -m "- Loot.isFilledChest: обчищенный сундук перезаписывается" -m "- столб глоустоуна ставится под выбранный x внутри Loot.chest"

git add docs/world.md docs/concepts/mobs_city.png
git commit -m "docs(agents): концепты мобов Города Спящих" -m "- docs/concepts/mobs_city.png, ссылка в world.md"

git add src/com/explosionwand/Nbt.java src/com/explosionwand/DungeonTemplate.java src/com/explosionwand/DungeonData.java src/com/explosionwand/Dungeons.java src/com/explosionwand/DungeonGuard.java src/com/explosionwand/SealShardItem.java src/com/explosionwand/ExplosionWandMod.java src/com/explosionwand/Ticker.java src/com/explosionwand/Rift.java src/com/explosionwand/BlackHole.java src/com/explosionwand/IceStorm.java src/com/explosionwand/Chronos.java src/com/explosionwand/Mimic.java src/com/explosionwand/EclipseStaff.java tests/DungeonTest.java resources/data/explosionwand/structures/rift_test_entrance.nbt resources/data/explosionwand/structures/rift_test_halls.nbt tools/gen_dungeons.py tools/build.py tools/ids.py stubs/net/minecraft/nbt/Tag.java stubs/net/minecraft/nbt/CompoundTag.java stubs/net/minecraft/network/chat/Component.java stubs/net/minecraft/server/level/ServerLevel.java stubs/net/minecraft/world/Difficulty.java stubs/net/minecraft/world/entity/LivingEntity.java stubs/net/minecraft/world/entity/player/Player.java stubs/net/minecraft/world/item/Item.java stubs/net/minecraft/world/level/Level.java stubs/net/minecraft/world/level/block/Block.java stubs/net/minecraft/world/level/block/state/BlockBehaviour.java stubs/net/minecraft/world/level/block/state/StateDefinition.java stubs/net/minecraft/world/level/block/state/StateHolder.java stubs/net/minecraft/world/level/block/state/properties/Property.java stubs/net/minecraft/world/level/saveddata/SavedData.java stubs/net/minecraft/world/level/storage/DimensionDataStorage.java stubs/net/minecraftforge/event/entity/EntityTeleportEvent.java stubs/net/minecraftforge/event/entity/living/LivingDeathEvent.java stubs/net/minecraftforge/event/entity/player/PlayerEvent.java stubs/net/minecraftforge/event/entity/player/PlayerInteractEvent.java stubs/net/minecraftforge/event/server/ServerStoppedEvent.java docs/dungeons.md reports/0024-2026-10-19-mod-engineer-dungeon-pipeline.md reports/0025-2026-10-19-build-engineer-phase-4-1.md reports/0027-2026-10-19-mod-engineer-review-0026-fixes.md
git commit -m "feat(dungeon): конвейер данжей и Тестовый разлом" -m "- данж по сетке из частей .nbt, реестр в SavedData обычного мира" -m "- защита блоков, взрывов, телепортов; эффекты палочек пропускают данж" -m "- жаровни-якоря, мобы по залам, зачистка после загрузки сущностей" -m "- мирный режим, сбежавшие мобы, океан, очистка на ServerStoppedEvent" -m "- tools/gen_dungeons.py, шаг 3b в build.py, docs/dungeons.md"

git add resources/data/explosionwand/recipes/seal_shard_rift.json resources/assets/explosionwand/models/item/seal_shard_rift.json resources/assets/explosionwand/textures/item/seal_shard_rift.png resources/assets/explosionwand/lang/ru_ru.json resources/assets/explosionwand/lang/en_us.json resources/META-INF/mods.toml docs/history.md reports/0026-2026-10-19-code-reviewer-phase-4-1.md reports/0028-2026-10-19-code-reviewer-phase-4-1-recheck.md
git commit -m "feat(items): Осколок Печати: Разлом, версия 2.4.0" -m "- рецепт, модель, текстура, тексты ru и en (Dreamer's Seal)" -m "- history.md: 2.4.0 и поправка 2.3.0" -m "- ревью 0026 и 0028"

git add src/com/explosionwand/Debris.java CLAUDE.md ROADMAP.md reports/0029-2026-10-19-mod-engineer-debris-shield.md reports/0030-2026-10-19-code-reviewer-phase-4-1-final.md
git commit -m "fix(dungeon): обломки Кулака и Кометы не вырывают блоки данжа" -m "- Debris.fling пропускает позиции Dungeons.shield" -m "- CLAUDE.md: правила данжей, Dungeons.state, m_201971_, область dungeon" -m "- ROADMAP: 4.6 по mobs_city.png, фаза 4.1 закрыта"
```
Если main добавит отчёт о закрытии 4.1 (и о docs-правках, nit 3), его файл нужно дописать в последний `git add`.

## Следующий шаг
- **main:** в `ROADMAP.md` закрыть 4.1 и поставить «Текущая фаза: 4.2», написать свой отчёт, выполнить коммиты выше. Затем `/clear` и `/next-phase`.
- **4.2, первый шаг:** minor 1 этого отчёта (падающие блоки в данже), minor 3 и nit 4 из 0028, minor 7 и 9 и nit 12, 13, 15 из 0026.
