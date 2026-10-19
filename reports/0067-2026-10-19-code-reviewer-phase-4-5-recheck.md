# 0067 · Повторное ревью фазы 4.5 (исправления 0066)

- **Дата:** 2026-10-19
- **Агент:** code-reviewer
- **Версия мода:** 2.8.0
- **Статус:** done
- **Связанные отчёты:** 0065 (ревью 4.5), 0066 (исправления), 0063 (город), 0060 (повторное ревью 4.4)

## Задача
Повторно проверить пункты 0065, которые 0066 должен был закрыть: major 1–2, minor 3 и 5, nit 7–9. Проверить, нет ли регрессий в `City.tick` (возврат игрока), `Dungeons.register/variation` (`getInhabitedTime`, отложенная регистрация) и в тумане. Выдать итоговые git-команды всей незакоммиченной работы поверх HEAD `ee28f20`.

## Вердикт: **REQUEST CHANGES**
Major 1–2, minor 3, nit 7–9 закрыты по сути. Но исправления принесли две новые проблемы уровня major:
- `City.gateOpen` читает блок ворот каждого достроенного города раз в 10 тиков, даже если рядом никого нет, а значит синхронно грузит чужие чанки;
- отложенная регистрация навсегда блокирует клетки, у которых запасная точка лежит далеко.

Обе правятся в несколько строк.

## Что сделано
- Прочитаны 0065, 0066, весь `City.java`, `Dungeons.register/variation/site/site0/tick0`, `RiftStaff.java:62`, `docs/history.md` 2.8.0, `gen_city.py` (стена, ворота, метка `start`, склеп). Прочитан `git status -uall`.
- SRG через `lookup.py --srg`: `m_6319_` → `Chunk.getInhabitedTime` (новое, совпадает). `m_8999_` → `ServerPlayerEntity.teleport(ServerWorld,DDDFF)`, `f_19789_` → `fallDistance`, `m_146908_/m_146909_` → `getYaw/getPitch`, `m_46805_` → `isChunkLoaded`. Эти пять уже были в проекте, смысл совпадает.
- Lang: ru 97 и en 97 ключей, валидный JSON. `explosionwand.city.gate_closed` есть в обоих.
- Своя проверка (временный харнесс в scratchpad, вне проекта, на `build/classes`): 4000 случайных (seed, клетка) для `SLEEP`, расстояние от `site(…,a)` до `site(…,0)` для a = 1..7. Результат: в 1289 клетках (32 %) хотя бы одна запасная точка дальше 120 блоков, максимум 661. В 1106 клетках (28 %) есть точка дальше 200 блоков, из них в 379 (9,5 %) уже первая запасная. Причина: в `site()` при обходе Разломов `salt` перебрасывает базовую точку в любое место клетки 768, а не в окрестность `site(…,0)`.

## Статус пунктов 0065
| Пункт | Статус | Комментарий |
|---|---|---|
| major 1 (обход ворот) | закрыт | `inWalls` (±45,5, y −40..+22) при закрытых воротах переносит игрока к `start` (z = 47, это мост вне стены, петли нет). Падение обнуляется, подсказка в actionbar. Центр метки ворот (0, 3, 44) — `chiseled_deepslate`, после Осколка там воздух. Высота 22 перекрывает маску до 20 и платформу на 21. Опрос раз в 0,5 с ловит падение с элитр. Новые замечания: п. 1 и 3 ниже |
| major 2 (обжитые места) | закрыт | `inHabitedTime > 6000` в любой из 9 точек → `SETTLED`, точка пропускается. Если пропущены все → `dead`. В 1.20.1 время копится во всех чанках в 128 блоках от игрока, поэтому база игрока гарантированно попадает хотя бы в одну точку замера. Предупреждение есть в history |
| minor 3 (перепад) | закрыт | `ground(5, 8)`, фундамент 10 ≥ 8 |
| minor 5 (синхронная генерация) | закрыт, но с регрессией | `m_46805_` перед `m_6325_` у наземных данжей. Регрессия — п. 2 ниже |
| nit 7 (пороги) | закрыт | `>=`, тест 60/120 |
| nit 8 (RiftStaff) | закрыт | `RiftStaff.java:62`: одно условие, один комментарий |
| nit 9 (history) | закрыт | `docs/history.md:13` |

## Замечания

### major
1. **[major] `City.java:109,133-139`: `gateOpen` синхронно грузит чанк ворот каждого города, даже далёкого.** `gateOpen(sl, rec, d)` вызывается для каждой достроенной записи `SLEEP` раз в 10 тиков, до цикла по игрокам и без проверки расстояния. `sl.m_8055_` на незагруженном чанке в `ServerLevel` грузит его в главном потоке (`getChunk(…, FULL, true)`) и ставит временный тикет. Чанк вскоре выгружается, а через 10 тиков всё повторяется. Нагрузка растёт с каждым исследованным городом: постоянная загрузка и выгрузка с диска в главном потоке. Рядом (`Dungeons.java:788`) так не делают, там стоит `m_46805_`. → Считать `gateOpen` лениво: только когда первый игрок попал в `inWalls` (например, `Boolean gate = null; … if (inWalls(...)) { if (gate == null) gate = gateOpen(...); if (!gate) {...} }`). Игрок в 45 блоках от центра сам держит этот чанк загруженным. Для надёжности добавить `if (!sl.m_46805_(pos)) return true;` в `gateOpen`.
2. **[major] `Dungeons.java:663,668` + `site():249-257`: отложенная регистрация навсегда блокирует ~28 % клеток.** `register` вызывается, только пока игрок в 80 блоках от `site(…,0)`. Если `site(…,0)` (и предыдущие запасные) отбракованы (перепад > 5, вода, `SETTLED`), а следующая запасная точка лежит в 200–660 блоках, её чанк не загружен. Тогда срабатывает `return`, записи нет и `dead` нет. На следующем проходе всё повторяется, и так, пока игрок стоит у `site(…,0)`. Дальний чанк одновременно с ним не загрузится, поэтому города в клетке не будет никогда (харнесс: в 9,5 % клеток так ведёт себя уже первая запасная точка). До 0066 такая клетка хотя бы регистрировалась, пусть и через синхронную генерацию. → (а) В `site()` при `attempt > 0` смещать от уже найденной `site(…,0)` (с учётом salt), а точки, которые `clashes`, отбрасывать в `register` через `continue`. Записей `sleep` в мирах ещё нет (2.8.0 не выпущен), значит менять безопасно, а Разлома (`avoid == null`) это не касается. (б) Незагруженная запасная точка (a > 0) → `continue`, а не `return`. `return` оставить только для a = 0 и только когда ни одна точка ещё не оценена. Тест в `CityTest`: все `site(…,a)` в пределах ±48 от `site(…,0)` и ни одна не `clashes` с Разломом.

### minor
3. **[minor] `City.java:41-43` (`ry >= -40`): игроков переносит из шахт под городом.** Маска кончается на фундаменте (`FOUND = -10`), снизу его не сломать, поэтому зона −40..−11 ничего не защищает. Под закрытым городом это 91×91 блоков на типичной глубине добычи и пещер. Игрока, который копает там, раз в 0,5 с выбрасывает на мост у ворот. → Нижнюю границу поднять до `ry >= -11.5` (или до нижней границы `bounds()`).
4. **[minor] `Dungeons.java:663` (перенос из 0065 п. 4/6 не нужен, это отдельное).** Пока клетка ждёт (п. 2), `register` для неё вызывается каждые 10 тиков: до 8 × 9 проверок. Это дёшево, но после исправления п. 2 ожидание станет коротким и пункт закроется сам. Записано, чтобы не потерялось.

### nit
5. **[nit] `City.java:118`:** подсказка уходит раз в 0,5 с. Для игрока на столбе у стены это ровно один перенос, повторов нет. Ок, правки не требуется.

### Без изменений с 0065 (по плану в 4.6/4.7)
Minor 4 (повторная выдача Печати Храма), minor 6 (вексы-заглушки сквозь стены), nit 10 (`safeY`), nit 11 (`int`-маски залов).

### Проверено и в порядке
- **Туман:** логика не задета. Ветка возврата делает `continue` до `seen.add`, поэтому `TRACK` у перенесённого чистится через `retainAll`. При закрытых воротах в `inTown` никого нет (`inTown ⊂ inWalls`), туман не действует.
- **Перенос:** `m_8999_` в тот же уровень снимает игрока с транспорта (`stopRiding`) и не ломает тик. Исключение в `City.tick` поймает общий `try` в `Dungeons.tick` (как в 0065). Мёртвые, творческий режим и наблюдатель пропускаются.
- **`getInhabitedTime`:** у Разлома (`flat = 0`) поведение прежнее: `variation` не вызывается, проверки `m_46805_` нет. Шаблоны и места Разлома не менялись.
- **Правила кода:** enum, `Fx`, `Cmd` соблюдены, прямых ссылок на `Blocks`/`SoundEvents` нет. Новых `m_7731_` нет.

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `reports/0067-2026-10-19-code-reviewer-phase-4-5-recheck.md` | этот отчёт (код не менялся; харнесс — только во временном scratchpad вне проекта) |

## Проверка
- `python tools/build.py` (запущен сам, exit 0): 125 классов; SRG исходники 226 / байткод 227, не найдено 0; проблем ID 0; gen_dungeons 7 / 0; тесты BossRiftTest, CityTest (туман 3 с / 6 с), DungeonTest (312 871 блок), MazeTest, RiftMobsTest, RiftStaffTest — OK; jar `dist/explosionwand-1.20.1-2.8.0.jar` (1059 КБ).
- Новое SRG `m_6319_` проверено.

## Проверить в игре
- **Ворота:** без Осколка поставить столб у рва и прыгнуть на стену. Должен быть перенос на мост с подсказкой и без урона от падения. С элитрами над городом то же. С Осколком (ворота открыты) вход свободный.
- **Шахта под городом (п. 3):** копать на 20–30 блоков ниже закрытого города. Сейчас игрока выбросит к воротам, после правки не должно.
- **Далёкие города (п. 1):** открыть 5–10 городов и уйти на 1000+ блоков. Посмотреть в F3 или по логам, нет ли подвисаний и роста числа загруженных чанков.
- **Появление города (п. 2):** пройти несколько клеток 768 на холмистой местности. В каждой ли клетке без воды и Разлома находится город (Shift+ПКМ Осколком Сна в творческом показывает координаты)?
- **Старый мир 2.7.0:** город не встаёт рядом с базой (база в 128 блоках от точки замера).
- **Туман:** замедление ровно через 3 с, слепота через 6 с.
- Остальное из «Проверить в игре» отчётов 0063, 0065, 0066.

## Решения и почему
- П. 1 — major: нагрузка растёт с каждым городом в мире и не зависит от того, где игрок, а проблему ловят только в долгой игре.
- П. 2 — major: город не появляется в заметной доле клеток (по харнессу), и в игре это незаметно (ни ошибки, ни `dead`). Корень в `site()`: запасные точки не рядом с основной.
- П. 3 — minor: неудобство, а не потеря данных или прогресса.

## Проблемы и открытые вопросы
- Как в 0065: общие файлы несут правки и 4.4, и 4.5, коммит 2 по отдельности не собирается (`Boss.java` ссылается на `Dungeons.lairWon/clearable` из коммита 3). Если нужна сборка каждого коммита, объединить 2 и 3.
- `arcane_helmet.json`/`arcane_leggings.json` отличаются только концами строк. Если git не увидит изменений, они не войдут в коммит.
- Правки по п. 1–3 этого отчёта и их отчёт можно добавить к коммиту 3 (если он ещё не сделан) или выпустить отдельным `fix(dungeon)` после коммита 4.

## Git (выполняет пользователь)
Поверх HEAD `ee28f20`, по порядку, в PowerShell. `ROADMAP.md` не включён. Сюда вошли все незакоммиченные отчёты 0051–0060 и 0062–0067 (0061 уже в HEAD).
```
git add tools/ids.py tools/build.py reports/0052-2026-10-19-build-engineer-ids-jar-cleanup.md reports/0059-2026-10-19-build-engineer-ids-particles-fix.md
git commit -m "build(build): ids.py видит sound(), частицы и Kind; mech_test вне jar" -m "- проверка голых sound() и хостов RiftModels.Kind (только в RiftModels.java)" -m "- вернута проверка частиц (кортеж выпадал в комментарий)" -m "- mech_test.nbt исключён из jar, тесты берут его из resources/"

git rm src/com/explosionwand/Arena.java src/com/explosionwand/RiftKeyItem.java resources/assets/explosionwand/models/item/rift_key.json resources/assets/explosionwand/textures/item/rift_key.png resources/data/explosionwand/recipes/rift_key.json
git add src/com/explosionwand/Boss.java src/com/explosionwand/RiftMobs.java src/com/explosionwand/RiftLogic.java src/com/explosionwand/ArenaGuard.java src/com/explosionwand/Altar.java src/com/explosionwand/EclipseSealItem.java src/com/explosionwand/WhaleStaff.java src/com/explosionwand/RiftStaff.java src/com/explosionwand/RiftStaffItem.java stubs/net/minecraft/world/entity/Entity.java stubs/net/minecraft/world/damagesource/DamageSources.java stubs/net/minecraft/world/entity/animal/TamableAnimal.java stubs/net/minecraft/world/entity/npc/AbstractVillager.java resources/data/explosionwand/structures/rift_entrance.nbt resources/data/explosionwand/structures/rift_mid.nbt resources/data/explosionwand/structures/rift_deep.nbt resources/data/explosionwand/recipes/arcane_helmet.json resources/data/explosionwand/recipes/arcane_leggings.json resources/assets/explosionwand/models/item/rift_staff.json resources/assets/explosionwand/textures/item/rift_staff.png resources/assets/explosionwand/models/item/sleep_seal_shard.json resources/assets/explosionwand/textures/item/sleep_seal_shard.png tests/BossRiftTest.java tests/RiftMobsTest.java tests/RiftStaffTest.java reports/0051-2026-10-19-mod-engineer-rift-side-rooms-shrink.md reports/0053-2026-10-19-mod-engineer-rift-staff-sleep-shard.md reports/0054-2026-10-19-boss-engineer-rift-heart-lair.md reports/0057-2026-10-19-boss-engineer-review-fixes-4-4.md reports/0058-2026-10-19-mod-engineer-rift-staff-review-fixes.md
git commit -m "feat(boss): Сердце Разлома в логове, Посох Разлома" -m "- зал 7: босс просыпается при входе, зачистка только победой, добыча: посох, ядро, осколок Сна" -m "- Посох Разлома: трещина (indirectMagic, щадит жителей и питомцев) и шаг на 8 блоков" -m "- комнаты 8-16 меньше, раскладка Разлома 4" -m "- убраны Ключ Разлома, алтарь и арена Сердца; JSON измерения arena оставлены" -m "- общая часть (Dungeons, DungeonGuard, Dims, Ticker, lang) - в следующем коммите"

git add tools/gen_city.py tools/gen_dungeons.py resources/data/explosionwand/structures/sleep_north.nbt resources/data/explosionwand/structures/sleep_mid.nbt resources/data/explosionwand/structures/sleep_south.nbt src/com/explosionwand/City.java src/com/explosionwand/Dungeons.java src/com/explosionwand/DungeonGuard.java src/com/explosionwand/Dims.java src/com/explosionwand/Ticker.java src/com/explosionwand/SealShardItem.java src/com/explosionwand/Loot.java src/com/explosionwand/ExplosionWandMod.java src/com/explosionwand/jei/ExplosionWandJei.java stubs/net/minecraft/server/level/ServerPlayer.java stubs/net/minecraft/world/level/Level.java stubs/net/minecraft/world/level/LevelReader.java stubs/net/minecraft/world/level/chunk/ChunkAccess.java stubs/net/minecraft/core/BlockPos.java resources/assets/explosionwand/models/item/temple_seal.json resources/assets/explosionwand/textures/item/temple_seal.png resources/assets/explosionwand/lang/ru_ru.json resources/assets/explosionwand/lang/en_us.json tests/CityTest.java tests/DungeonTest.java reports/0062-2026-10-19-mod-engineer-review-nits-4-5.md reports/0063-2026-10-19-mod-engineer-sleeper-city.md reports/0066-2026-10-19-mod-engineer-review-fixes-4-5.md
git commit -m "feat(dungeon): Город Спящих, логово босса в конвейере данжей" -m "- наземный город 97x97: стена, 20 домов, библиотека-ловушка, склеп, храм-заглушка со шпилем" -m "- ворота - Осколок Сна (обход через стену возвращает к воротам), дверь храма - Печать Храма" -m "- сонный туман на улицах (3 и 6 с), мобы-заглушки, особая добыча сундуков" -m "- Def.ground/avoiding: ровное место (перепад до 8), обход Разломов и обжитых чанков" -m "- конвейер: метка boss, lairWon, Boss.tickAll; защита ядра столба от взрывов" -m "- хвосты 4.4: выход из старого arena, достройка при входе, трещина снаружи"

git add resources/META-INF/mods.toml docs/history.md docs/world.md docs/dungeons.md reports/0055-2026-10-19-asset-engineer-release-2-7-0.md reports/0056-2026-10-19-code-reviewer-phase-4-4.md reports/0060-2026-10-19-code-reviewer-phase-4-4-recheck.md reports/0064-2026-10-19-asset-engineer-release-2-8-0.md reports/0065-2026-10-19-code-reviewer-phase-4-5.md reports/0067-2026-10-19-code-reviewer-phase-4-5-recheck.md
git commit -m "docs(dungeon): релизы 2.7.0 и 2.8.0, ревью фаз 4.4 и 4.5" -m "- версия 2.8.0 в mods.toml; history 2.6.2, 2.7.0, 2.8.0" -m "- world.md и dungeons.md: логово Сердца, Город Спящих, наземные данжи" -m "- отчёты ревью 0056, 0060, 0065, 0067"
```

## Следующий шаг
- **mod-engineer:** п. 1: ленивый `gateOpen` плюс `m_46805_`. П. 2: запасные точки `site()` рядом с `site(…,0)`; незагруженная запасная точка → `continue`; тест в `CityTest`. П. 3: нижняя граница `inWalls`. Затем `build.py` и отчёт.
- **code-reviewer:** повторное ревью только п. 1–3.
- **4.6 / 4.7:** без изменений (0065 minor 4, 6, nit 10, 11).
