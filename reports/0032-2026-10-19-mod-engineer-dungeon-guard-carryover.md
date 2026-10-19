# 0032 · Фаза 4.2, шаг 1: закрытие замечаний ревью по данжам

- **Дата:** 2026-10-19
- **Агент:** mod-engineer
- **Версия мода:** 2.4.0 (не менялась, 2.5.0 в шаге 3)
- **Статус:** done
- **Связанные отчёты:** 0030, 0028, 0026, 0024

## Задача
Закрыть перенесённые из 4.1 замечания: закупорка данжа падающими блоками (0030 minor 1), чанки рамки `alive()` (0028 minor 3), ведро и естественный спавн (0026 minor 9), обновление соседей на стыке (0026 minor 7), nit 12, 13, 15.

## Что сделано
- **Падающие блоки.** `Dungeons.purgeFalling` раз в 10 тиков: для каждого данжа с построенной частью, если игрок ближе 160 блоков, один запрос `m_6249_` по рамке данжа +2 с фильтром `FallingBlockEntity`, найденные убираются. Обхода блоков нет, цена — один запрос по сущностям на данж рядом.
- **alive()/зачистка.** Рамка поиска мобов — зал +24 (`ALIVE_PAD`), а не весь данж +24. `entitiesLoaded` проверяет `m_143319_` для всех чанков именно этой рамки; `alive(sl, rec, Hall, tag)`. Для всего данжа условие «все чанки загружены» могло не выполниться никогда (данж до 250 блоков, а сущности грузятся лишь рядом с игроком), и залы бы встали. Мобы с тегом зала дальше 24 блоков от зала всё равно убирает `removeStrays`. Кэш списка чанков не нужен: 25-50 проверок на зал раз в 10 тиков.
- **Ведро.** `DungeonGuard.onBucket` (`FillBucketEvent`): вне креатива отменяется, если клик по блоку в данже или в 1 блоке от границы (вода ставится в соседний блок).
- **Спавн.** `DungeonGuard.onSpawn` (`MobSpawnEvent.FinalizeSpawn`): в границах данжа отменяются `NATURAL`, `CHUNK_GENERATION`, `REINFORCEMENT`, `PATROL`, `JOCKEY`; мобы с тегом `ewd` исключены. Мобы залов ставятся командой `summon`, так что под отмену не попадают.
- **Стык.** `Placing` получил вторую фазу `settle`: после постановки части для блоков, у которых хоть один из 6 соседей отсутствует в шаблоне части (по `HashSet` упакованных координат), вызывается `updateNeighborsAlways`. Бюджет `BUDGET` общий (проверка блока стоит 1, обновление 3). Часть засчитывается в `partsDone` только после этой фазы.
- **nit 12:** `gen_dungeons.py --check` ругается на номер зала вне 1..30.
- **nit 13:** `Dungeons.noTemplate` пишет в stderr одну строку на тип данжа («нет шаблона, защита выключена»), вызывается из `shield`, `Rec.contains`, `purgeFalling`; сбрасывается в `clearState`.
- **nit 15:** `SealShardItem.m_7203_` вне креатива возвращает `pass`, если в 8 блоках нет данжа (`Dungeons.near`); Shift+ПКМ в креативе по-прежнему пишет координаты.
- `docs/dungeons.md`: раздел «Ограничения 4.1» заменён на «Что закрыто в 4.2 (шаг 1) и что осталось».

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `src/com/explosionwand/Dungeons.java` | `purgeFalling`, фаза `settle` в `Placing`, `alive`/`entitiesLoaded` по рамке зала, `noTemplate`, `near` |
| `src/com/explosionwand/DungeonGuard.java` | `onBucket`, `onSpawn` |
| `src/com/explosionwand/DungeonData.java` | лог при отсутствии шаблона |
| `src/com/explosionwand/SealShardItem.java` | `pass` вне креатива без данжа рядом |
| `src/com/explosionwand/ExplosionWandMod.java` | подписка на два новых события |
| `stubs/net/minecraft/world/level/Level.java` | `m_46672_` |
| `stubs/net/minecraft/world/InteractionResultHolder.java` | `m_19098_` |
| `stubs/net/minecraft/world/level/ServerLevelAccessor.java` | новый, `m_6018_` |
| `stubs/net/minecraft/world/entity/MobSpawnType.java` | новый |
| `stubs/net/minecraftforge/event/entity/living/MobSpawnEvent.java` | новый, `FinalizeSpawn` |
| `stubs/net/minecraftforge/event/entity/player/FillBucketEvent.java` | новый |
| `tools/gen_dungeons.py` | проверка номера зала 1..30 |
| `docs/dungeons.md` | что закрыто, что осталось |

## Проверка
- `python tools/build.py`: 97 классов, SRG исходники/байткод 212/213, неизвестных 0, проблем ID 0, `[3b]` gen_dungeons 2 файла и 0 проблем, DungeonTest OK, MazeTest OK, jar `dist/explosionwand-1.20.1-2.4.0.jar` (431 КБ).
- Новые SRG-имена (`lookup.py --srg`): `m_46672_` → `World.updateNeighborsAlways`; `m_19098_` → `TypedActionResult.pass`; `m_6018_` → `ServerWorldAccess.toServerWorld`. Остальные (`m_146870_`, `m_143319_`, `m_60734_`, `m_82425_`) уже были.
- Forge-методы (`FillBucketEvent.getTarget`, `MobSpawnEvent.FinalizeSpawn.getSpawnType/getX/getY/getZ/getLevel/getEntity/setSpawnCancelled`) пишутся без SRG. Сверить с jar Forge 47.x нельзя (его в проекте нет), заглушки сделаны по памяти API 1.20.1; см. «Проблемы».

## Проверить в игре
- Кулак/Комета и Чёрная дыра у стены данжа: обломок, залетевший внутрь, исчезает, проход не закупорен.
- Ведро воды и лавы (выживание) у входа и внутри: не выливается и не набирается; в креативе работает.
- Ночью у входа и в залах: самопроизвольных зомби и скелетов нет; мобы залов появляются как раньше.
- Построение данжа у воды, лавы, песка: на стыке ничего не висит. Если вода потекла внутрь через стык, это дефект шаблона (воздух шаблона касается воды).
- Зачистка зала на краю данжа, пока дальний конец не загружен: зал будится и засчитывается.
- Осколок вне данжа: клик в воздух не машет рукой; у двери работает.
- Нагрузка тика рядом с данжем во время постановки (нет ли подтормаживаний из-за `settle`).

## Решения и почему
- Рамка `alive()` сужена до зала +24, потому что условие «все чанки данжа загружены» могло блокировать залы. Это отступление от буквы задачи («для всех чанков рамки alive()»): проверяются все чанки рамки, но рамка стала меньше.
- Спавн отменяется по типу (`NATURAL` и подобные), а не весь: спавнеры, яйца, команды и так в данже не нужны, но их я не трогаю.
- Для стыка выбрано `updateNeighborsAlways` на оболочке, а не повторный `m_7731_(…,3)`: второй вариант пересчитывает форму блока самого данжа (ступени, стены), первый будит только соседей.

## Проблемы и открытые вопросы
- Не уверен, что в Forge 1.20.1 `FinalizeSpawn` помечен `@Cancelable`; взят `setSpawnCancelled(true)`, который есть и там, и там. Если в jar метода нет, это поймает только игра/Gradle-сборка (build-engineer при первой сборке с Forge).
- Теста на новые обработчики нет (нужны `ServerLevel` и события). Чистая логика `settle` не выделена.

## Git (выполняет пользователь)
```
git add src/com/explosionwand/Dungeons.java src/com/explosionwand/DungeonGuard.java src/com/explosionwand/DungeonData.java src/com/explosionwand/SealShardItem.java src/com/explosionwand/ExplosionWandMod.java stubs/net/minecraft/world/level/Level.java stubs/net/minecraft/world/InteractionResultHolder.java stubs/net/minecraft/world/level/ServerLevelAccessor.java stubs/net/minecraft/world/entity/MobSpawnType.java stubs/net/minecraftforge/event/entity/living/MobSpawnEvent.java stubs/net/minecraftforge/event/entity/player/FillBucketEvent.java tools/gen_dungeons.py docs/dungeons.md reports/0032-2026-10-19-mod-engineer-dungeon-guard-carryover.md
git commit -m "fix(dungeon): защита данжа от падающих блоков, ведра и спавна" -m "- purgeFalling убирает падающие блоки у данжа раз в 10 тиков" -m "- отмена FillBucketEvent и естественного спавна в границах" -m "- зачистка по чанкам рамки зала, обновление соседей на стыке частей" -m "- gen_dungeons: номер зала 1..30, лог об отсутствии шаблона, Осколок отдаёт pass" -m "" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

## Следующий шаг
Шаг 2 фазы 4.2: генерация данжа «Разлом» (asset-engineer + mod-engineer), затем build-engineer и code-reviewer.
