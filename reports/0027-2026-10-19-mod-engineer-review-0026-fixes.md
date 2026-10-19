# 0027 · Исправления по ревью 0026 (фаза 4.1)

- **Дата:** 2026-10-19
- **Агент:** mod-engineer
- **Версия мода:** 2.4.0 (не выпущена, остаётся)
- **Статус:** done (в игре не проверено)
- **Связанные отчёты:** 0026 (ревью), 0024 (конвейер данжей)

## Задача
Закрыть замечания ревью 0026: major 1 и 2, minor 3-6, nit 10, 11, 14.

## Что сделано
- **Major 1.** В `Dungeons`: `Shield` (границы всех данжей обычного мира, `at(x,y,z)` и `at(BlockPos)`), `shield(level)` (получать один раз на проход цикла), `protectedAt(level, pos)`. Позиции данжей пропускаются так же, как бедрок, в: `Rift.cut`, `BlackHole.carve` и `ripBlocks` (вырывание падающих блоков), `IceStorm.freeze` и `spike`, `Chronos.restoreBlocks`, `Mimic` (стены гроба; если центр в данже, игрок отправляется «в небо», как при отсутствии блоков), `EclipseStaff` (`burn`, огонь, `Nova.carveStep`). Остальные циклы `m_7731_` (Altar, Maze, Arena*, Boss*, Tesseract) проверены: ставят своё в своих местах и измерениях, не тронуты.
- **Major 2.** `entitiesLoaded(sl, rec, hall)` проверяет `ServerLevel.m_143319_(long)` для всех чанков зала; пока не загружены, зал не будится (перед `spawnHall`) и не засчитывается. `SPAWNED_AT.computeIfAbsent(tag, k -> tk)` вместо `tk - 1000`: после перезапуска отсрочка 30 тиков с момента загрузки.
- **Minor 3.** Раз в 200 тиков `removeStrays`: сущности с тегом `ewd` вне границ любого данжа + 24 блока убираются. Запись `SPAWNED_AT` чистится при «все ушли».
- **Minor 4.** Мирный режим (`m_46791_() == PEACEFUL`): залы не будятся и не засчитываются; уже разбуженные незачищенные сбрасываются (мобы убираются, бит `hallsSpawned` снимается); в данже раз в 10 секунд сообщение «В мирном режиме данж спит» (ключ `explosionwand.dungeon.peaceful`, ru и en).
- **Minor 5.** Если все 8 точек клетки в воде, данж не ставится; клетка запоминается в `DungeonData.dead` (хранится в мире) и пропускается в `nearest` (Осколок и `locate` ведут к другой клетке).
- **Minor 6.** `Dungeons.clearState()` на `ServerStoppedEvent` (`Dungeons::onStopped`): `PLACING`, `SPAWNED_AT`, `LAST_INSIDE`, `PENDING`, `Later.TASKS` (старый долг), флаг `warned`.
- **Nit 10, 11.** CLAUDE.md: исключение про `Dungeons.state` + `m_7731_`, правило про `Dungeons.shield` для новых эффектов, область коммита `dungeon`.
- **Nit 14.** `seal_shard_rift.json`: `category` -> `misc`.
- **Тест.** `DungeonTest`: граница `Shield` (углы внутри, на 1 блок за границей снаружи по каждой из 6 граней), два данжа, пустой щит, щит настоящего разлома (сундук и дверь внутри), пропуск мёртвой клетки в `nearest`.
- Поведение существующих предметов вне данжей не менялось. Внутри данжа эффекты палочек, колдуемые снаружи, больше не трогают его блоки.

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `src/com/explosionwand/Dungeons.java` | Shield/shield/protectedAt, entitiesLoaded, мирный режим, removeStrays, океан, clearState/onStopped, `nearest` с `dead` |
| `src/com/explosionwand/DungeonData.java` | `dead` (клетки без данжа) в NBT |
| `src/com/explosionwand/Rift.java`, `BlackHole.java`, `IceStorm.java`, `Chronos.java`, `Mimic.java`, `EclipseStaff.java` | пропуск позиций данжей |
| `src/com/explosionwand/ExplosionWandMod.java` | слушатель `Dungeons::onStopped` |
| `stubs/net/minecraft/server/level/ServerLevel.java` | `m_143319_(long)` |
| `stubs/net/minecraft/world/level/Level.java` | `m_46791_()` |
| `stubs/net/minecraft/world/Difficulty.java`, `stubs/net/minecraftforge/event/server/ServerStoppedEvent.java` | новые заглушки |
| `tests/DungeonTest.java` | тесты Shield и пропуска мёртвой клетки |
| `resources/assets/explosionwand/lang/ru_ru.json`, `en_us.json` | ключ `explosionwand.dungeon.peaceful` |
| `resources/data/explosionwand/recipes/seal_shard_rift.json` | category misc |
| `CLAUDE.md`, `docs/dungeons.md`, `docs/history.md` | правила, описание, 2.4.0 |

## Проверка
- `python tools/build.py`: 97 классов, SRG 209/210, неизвестных 0, ID 0, gen_dungeons 0 проблем, DungeonTest OK, MazeTest OK, jar `dist/explosionwand-1.20.1-2.4.0.jar`.
- Новые SRG обратным поиском (`lookup.py --srg`): `m_143319_` = `ServerWorld.isChunkLoaded(J)Z` (в Mojang это `ServerLevel.areEntitiesLoaded`, как в 0026: yarn называет так проверку загрузки сущностей), `m_46791_` = `WorldAccess.getDifficulty` (Mojang `LevelAccessor.getDifficulty`), `m_8583_` = `iterateEntities` (getAllEntities), `m_19880_` = `Entity.getCommandTags`, `m_146870_` = `discard`.
- `ServerStoppedEvent` (`net.minecraftforge.event.server`) — имя класса Forge, SRG не нужен; проверяется только в игре.
- Оба lang-файла валидны как JSON.

## Проверить в игре
- Встать в 5-10 блоках за границей данжа и бить Чёрной дырой, Разлом-каньоном, Ледяной бурей, Хроносом (с откатом), Мимиком (гроб), Посохом Затмения (сверхновая, поджог): блоки данжа целы, мир вокруг меняется как обычно, кратеры и лёд обрываются по границе данжа.
- Разбудить логово, оставить мобов, сохранить и выйти, зайти снова в логове: зал не засчитан сразу, сундука нет, мобы не продублированы (подождать около 2 секунд). `/tp` к данжу издалека: то же.
- Вытащить зомби на 40+ блоков от данжа, подождать около 10 секунд: моб убирается.
- Мирный режим: в данже сообщение «В мирном режиме данж спит», мобы не появляются, сундук не выдаётся. Переключить на другую сложность: зал будится при входе.
- Океан: клетка с данжем под водой: данж не появляется, Осколок ведёт в другую клетку.
- Выйти в меню во время постройки данжа и открыть другой мир: чужой данж не достраивается, ошибок в логе нет.
- Если `m_143319_` в игре всегда `false`, залы никогда не оживут: это видно сразу при первом входе (тогда заменить проверку на `m_46805_`).

## Решения и почему
- `Shield` вместо запроса `DungeonData` на каждый блок: в Чёрной дыре сотни тысяч позиций; щит берётся раз на проход.
- Мёртвая клетка хранится в мире (`dead`), а не в памяти: иначе при каждом заходе заново грузились бы 8 чанков.
- В мирном режиме мобы не создаются вообще, а не «создаются и тут же исчезают»: иначе бесплатный сундук.
- Не сделано (вне задачи, остаётся в 0026): minor 7 (флаг 18), minor 8 (отчёт main), minor 9 (ведро, спавн), nit 12, 13, 15.

## Проблемы и открытые вопросы
- `removeStrays` обходит все сущности мира раз в 10 секунд; при тысячах сущностей это заметно, но только если данжи зарегистрированы. Если будет просадка, перейти на список UUID.
- В Tesseract череп и розы на месте смерти двойника могут встать в данж (одиночные блоки, палочка запрещена внутри). Не защищено.

## Git (выполняет пользователь)
```
git add src/com/explosionwand/Dungeons.java src/com/explosionwand/DungeonData.java src/com/explosionwand/Rift.java src/com/explosionwand/BlackHole.java src/com/explosionwand/IceStorm.java src/com/explosionwand/Chronos.java src/com/explosionwand/Mimic.java src/com/explosionwand/EclipseStaff.java src/com/explosionwand/ExplosionWandMod.java stubs/net/minecraft/server/level/ServerLevel.java stubs/net/minecraft/world/level/Level.java stubs/net/minecraft/world/Difficulty.java stubs/net/minecraftforge/event/server/ServerStoppedEvent.java tests/DungeonTest.java reports/0027-2026-10-19-mod-engineer-review-0026-fixes.md
git commit -m "fix(dungeon): защита от палочек снаружи, зачистка после загрузки сущностей" -m "- Dungeons.shield/protectedAt: Разлом, Чёрная дыра, Ледяная буря, Хронос, Мимик, Затмение пропускают блоки данжа" -m "- зал не засчитывается, пока сущности чанков не загружены; отсрочка после перезапуска" -m "- мирный режим: данж спит; сбежавшие мобы убираются; океан без данжа" -m "- очистка статического состояния на ServerStoppedEvent"
git add resources/assets/explosionwand/lang/ru_ru.json resources/assets/explosionwand/lang/en_us.json resources/data/explosionwand/recipes/seal_shard_rift.json docs/dungeons.md docs/history.md CLAUDE.md
git commit -m "docs(agents): правила для данжей, область dungeon" -m "- исключение Dungeons.state и правило Dungeons.shield в CLAUDE.md" -m "- текст про мирный режим, рецепт осколка в категории misc"
```
`docs/history.md` и lang-файлы содержат и правки прошлых шагов (0022, 0024): сверьте `git diff` и коммит 4 из 0026 (если он ещё не сделан, лучше объединить).

## Следующий шаг
- code-reviewer: повторное ревью только по исправлениям 0026 (major 1-2, minor 3-6).
- Затем main закрывает 4.1 в ROADMAP; остальные minor и nit (7, 9, 12, 13, 15) идут первым шагом 4.2.
