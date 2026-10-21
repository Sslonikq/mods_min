# 0092 · Посадка Разлома, палочки на мобах данжей, барьер до смерти босса

- **Дата:** 2026-10-21
- **Агент:** mod-engineer
- **Версия мода:** 2.15.0 (не менялась)
- **Статус:** done
- **Связанные отчёты:** 0085, 0090

## Задача
A) Разлом иногда криво спавнится: подземная часть видна над землёй. B) Палочки не работают на мобах в данжах: сделать политику (боевые работают, ломающие баланс/обходящие данж нет), таблица в docs. C) Блоки данжа ломаются/ставятся только после убийства босса этого данжа.

## Что сделано
- **A.** Причина: высота входа бралась по одной колонне (`groundY`), а Разлом ~170x200, у Разлома `flat = 0` (перепад не проверялся). Теперь `Def.underground(-4, 5, 16)`: по центрам колонн 16x16 маски, где есть блоки ниже кровли, берётся высота земли (загруженные чанки - реально, незагруженные - оценкой `ChunkGenerator.getHeightOnGround` без генерации); `DungeonSite.exposure` считает, насколько кровля торчит. Точка годна при <= 2, иначе лучшая из 16 (не хуже 5), иначе клетка `dead`. Чистая логика в `DungeonSite`, тесты в `DungeonTest.siteTests`.
- **B.** Причины: `RiftMobs.onHurt` гасил `onFire/inFire/lightningBolt`, `RiftMobs.step` тушил хост каждый тик; цели с тегами `ew*` исключались из толчка, притяжения, лозы, ветра, удара Кометы и Стоп-Кадра. Исправлено: `DungeonSite.envBlockedForMobs` (гасятся только стены/падение/утопление/сдавливание), `RiftMobs.ignite` (поджиг палочкой держится, солнце по-прежнему гасится), `Minions.dungeonMob`, `Earth.shovable`, Комета и Стоп-Кадр берут мобов данжей, `RiftMobs.step` не думает, пока хост заморожен. `DungeonGuard.forbidden` разделён на `teleports` (всегда) и `breaksBalance` (до смерти босса). Таблица - `docs/dungeons.md`.
- **C.** `Rec.bossDead` (сохраняется, сбрасывается в `DungeonData.reset`), `Dungeons.markCleared` из `lairWon` (общий для Сердца Разлома и Монолита: правок в `Boss*` не потребовалось), `Dungeons.guardedAt/guarded`; `DungeonGuard.onBreak/onPlace/onBucket/onDetonate`, `Dungeons.shield`, `purgeFalling`, `onUseItem` учитывают флаг.

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `src/com/explosionwand/DungeonSite.java` | новый: чистая логика высоты и политика урона среды |
| `src/com/explosionwand/Dungeons.java` | `Def.roof/tries/underground`, `Mask.colMin`, `exposure/surfaceAt`, `register`, `markCleared/guardedAt/guarded`, Shield, purgeFalling, RIFT |
| `src/com/explosionwand/DungeonData.java` | поле `bossDead`: хранение, сброс |
| `src/com/explosionwand/DungeonGuard.java` | `teleports/breaksBalance/forbidden`, барьер по `guarded` |
| `src/com/explosionwand/RiftMobs.java` | фильтр среды, `ignite`, `burnUntil`, заморозка |
| `src/com/explosionwand/Minions.java`, `Earth.java`, `Comet.java`, `TimeStop.java` | мобы данжей как цели; `RiftMobs.ignite` |
| `stubs/...` | `LevelHeightAccessor`, `ChunkGenerator`, `RandomState`, `ServerChunkCache`, `BlockGetter extends LevelHeightAccessor`, `ServerLevel.m_7726_` |
| `tests/DungeonTest.java`, `tests/EarthTest.java` | новые проверки |
| `docs/dungeons.md`, `docs/world.md` | барьер, посадка, таблица палочек |

## Проверка
- `python tools/build.py`: 0 неизвестных SRG, 0 проблем ID, все тесты OK (однажды BossCityTest/DungeonTest падали из-за параллельной правки boss-engineer, повтор прошёл).
- Новые SRG: `m_223221_` -> `ChunkGenerator.getHeightOnGround`, `m_8481_` -> `ServerChunkManager.getChunkGenerator`, `m_214994_` -> `getNoiseConfig`, `m_7726_` -> `getChunkManager`, `m_128379_/m_128471_` -> `putBoolean/getBoolean` (все через `lookup.py --srg`).
- Heightmap `WORLD_SURFACE_WG` берётся через `valueOf`, в рантайме не проверен.

## Проверить в игре
- Новый мир/новые Разломы: несколько раз подойти к Разлому в холмистой местности и у склона: крыша под землёй, вход на уровне земли. На сильных склонах/горах Разлома в клетке может не быть вовсе.
- Нет лага при подходе к Разлому (оценка ~150 колонн на точку, до 16 точек).
- Огонь Кометы и Искр жжёт мобов Разлома и Города; молния их бьёт; днём в Городе мобы без палочки не горят.
- Толкаются/притягиваются/держатся лозой обычные мобы данжа (боссы нет); Стоп-Кадр замораживает мобов данжа и размораживает без поломки поведения.
- Убить Сердце Разлома и Монолит: после победы блоки данжа ломаются киркой, взрывами и Копателем; до победы нет; Возврат и порталы по-прежнему нет.
- Загрузить старый мир с уже убитым боссом: флага нет (поле новое), барьер на месте; победа в новом бою снимает его.

## Решения и почему
- Layout Разлома **не поднят**: сброс не перемещает данж и не стирает построенные блоки, но стёр бы залы, секреты и сундуки у игроков. Кривые уже построенные остаются; новые - по новым правилам. Если нужен перенос, это отдельная команда/миграция.
- Мобы данжей не становятся `controllable` целиком (иначе Посох Сна и Кит включали бы им ИИ через `setNoAi(false)`): отдельный `shovable`.
- Двойник: мобы данжа его игнорируют, ИИ не ломаем.
- Боссы по-прежнему не горят и не берут молнии (`Boss3.environmental` не тронут).
- Игроки, убившие босса до этого релиза: старые миры без флага, барьер остаётся (нет способа узнать). Можно считать `hallsCleared` зала с боссом миграцией: решить отдельно.

## Проблемы и открытые вопросы
- Мало-вероятно: оценка высоты генератором не учитывает деревья и правки игрока в незагруженных чанках.
- Побеждённые данжи ломаются, а мобы остальных залов продолжают появляться и ходят в разрушенных залах.

## Git (выполняет пользователь)
```
git add src/com/explosionwand/DungeonSite.java src/com/explosionwand/Dungeons.java src/com/explosionwand/DungeonData.java src/com/explosionwand/DungeonGuard.java src/com/explosionwand/RiftMobs.java src/com/explosionwand/Minions.java src/com/explosionwand/Earth.java src/com/explosionwand/Comet.java src/com/explosionwand/TimeStop.java stubs/net/minecraft/world/level/LevelHeightAccessor.java stubs/net/minecraft/world/level/chunk/ChunkGenerator.java stubs/net/minecraft/world/level/levelgen/RandomState.java stubs/net/minecraft/server/level/ServerChunkCache.java stubs/net/minecraft/world/level/BlockGetter.java stubs/net/minecraft/server/level/ServerLevel.java tests/DungeonTest.java tests/EarthTest.java docs/dungeons.md docs/world.md reports/0092-2026-10-21-mod-engineer-rift-spawn-wands-dungeons.md
git commit -m "fix(dungeon): Разлом под землёй, барьер до смерти босса" -m "- высота по всей рамке, лучшая из 16 точек" -m "- блоки данжа ломаются после смерти босса" -m "- огонь, молнии и толчки палочек действуют на мобов данжей"
```

## Следующий шаг
Проверить в игре; при необходимости миграция флага `bossDead` для старых миров и перенос уже кривых Разломов.
