# 0094 · Правки по ревью 0093

- **Дата:** 2026-10-21
- **Агент:** mod-engineer
- **Версия мода:** 2.15.0 (не менялась)
- **Статус:** done
- **Связанные отчёты:** 0089, 0090, 0091, 0092, 0093

## Задача
Закрыть замечания ревью 0093 (REQUEST CHANGES): major 1-3, minor 4-9, nit 10-13 (одним агентом, включая `Boss3.java`).

## Что сделано
- **major 1.** `DungeonData.migrateBossDead` (вызывается в `load` после `checkLayouts`): флаг `bossDead` ставится записи, у которой зал с меткой `boss` уже в `hallsCleared`. Тест в `DungeonTest.siteTests` (зачищен зал босса / обычный зал / ничего; идемпотентность).
- **major 2.** Посадка подземного данжа (`Dungeons.registerCover`) больше не вызывает `m_6325_` для кандидатов: колонны читаются по загруженным чанкам или оцениваются генератором (`column`), точки считаются по 3 за проход `discover` (состояние в `Dungeons.SITES`, чистится в `clearState`), в `exposure` колонны берутся через одну (шаг 32 блока). Чанк грузится один раз, у выбранной точки, и там повторно проверяются вода и выставленность. Если оценка генератором недоступна, берётся основная точка по-старому.
- **major 3.** Реальный импульс: `RiftMobs.knock` -> `Mob.knock/applyKnock` (смещение хоста по осям, стены, пол и потолок, поводок `leash`, гравитация для наземных, затухание для летунов; пока моб летит, `think` не вызывается). `Earth.shove` шлёт мобам данжа импульс, Комета тоже. Лоза: `RiftMobs.root` блокирует `walk/fly` на 3 с. Статуя-страж не толкается (`knockable`). Таблица в docs поправлена.
- **minor 4.** Вода: `Dungeons.column` возвращает признак воды (загруженные: блок под поверхностью; незагруженные: `WORLD_SURFACE_WG` против `OCEAN_FLOOR_WG`), мокрая колонна = выставленность 99 (`DungeonSite.WET`). Фраза в docs исправлена.
- **minor 5.** `docs/history.md` 2.15.0: барьер после босса, посадка Разлома, палочки на мобах данжей, формулировка ХП Монолита (nit 10).
- **minor 6.** `DungeonSite.envBlockedForMobs(id, wandFire)`: `inFire/onFire` гасятся, если `now >= burnUntil` (жаровня и солнце), молнии проходят.
- **minor 7.** Дубль убран: подземные данжи выбирают точку только через `DungeonSite.choose` (в `registerCover`); старый `register` остался для наземных (Город, своя логика ровности).
- **minor 8.** `DungeonGuard.onPiston` (`PistonEvent.Pre` есть в Forge 1.20.1: линия 13 клеток от поршня) и `DungeonGuard.onGrief` (`EntityMobGriefingEvent`, результат DENY в 6 блоках от данжа с живым боссом, защищает от эндерменов). Огонь: события, отменяющего распространение или сгорание блока, в Forge 1.20.1 нет, не перехвачено (в «Следующий шаг»).
- **minor 9.** `Boss3.tickRings`: не больше 100 частиц на кольцо и только на чётных тиках (было до ~230 каждый тик).
- **nit 11.** `tools/classcheck.py`: вложенные классы проверяются строго, офлайн даёт понятную ошибку со ссылкой на `.cache/maps/mojang.txt`. **nit 12.** Удалён `javac.20261021_143753.args` из корня. **nit 13.** Однократный лог в `Dungeons.column`.
- **nit 14.** Не менял: ядро столба после победы ломается киркой с дропом, бой не повторяется, это разовая награда.
- **nit 15.** В отчёте 0089 написано «Версию не менял (patch 2.15.1 ставит main)», хотя правка вошла в 2.15.0: старые отчёты не редактируются, упоминание здесь. В 0092 git-команда была без `docs/history.md` и без Co-Authored-By; файлы `Dungeons.java`, `DungeonTest.java`, `docs/dungeons.md` общие у 0091, 0092 и 0094, поэтому в списке ниже они одним коммитом.

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `src/com/explosionwand/Dungeons.java` | `registerCover`, `SiteState`, `column`, `exposure` (шаг, вода) |
| `src/com/explosionwand/DungeonSite.java` | `WET`, `envBlockedForMobs(id, wandFire)` |
| `src/com/explosionwand/DungeonData.java` | `migrateBossDead` |
| `src/com/explosionwand/DungeonGuard.java` | `onPiston`, `onGrief` |
| `src/com/explosionwand/ExplosionWandMod.java` | регистрация двух слушателей |
| `src/com/explosionwand/RiftMobs.java` | `knock/root/applyKnock`, фильтр огня по `burnUntil` |
| `src/com/explosionwand/Earth.java`, `Comet.java`, `CityMobsB.java` | импульс мобам данжей, лоза, статуя не толкается |
| `src/com/explosionwand/Boss3.java` | частицы кольца волны |
| `stubs/...` | `LevelAccessor`, `Event.Result/setResult`, `BlockEvent.getLevel`, `PistonEvent`, `EntityMobGriefingEvent`, `Level implements LevelAccessor` |
| `tools/classcheck.py` | строгие вложенные классы, офлайн-сообщение |
| `tests/DungeonTest.java` | миграция, вода, политика огня |
| `docs/dungeons.md`, `docs/history.md` | исправления по ревью |

## Проверка
- `python tools/build.py`: 155 классов, SRG неизвестных 0, пути классов 101 / 0 ошибок, ID 0 проблем, тесты OK, jar собран.
- Новых SRG нет (`valueOf("OCEAN_FLOOR_WG")` и Forge-имена `PistonEvent.Pre`, `EntityMobGriefingEvent`, `Event.Result`). `getFaceOffsetPos` и `setResult` — API Forge без SRG, сверить с исходниками Forge 1.20.1 не мог (сети для Forge нет).

## Проверить в игре
- Старый мир с убитым Сердцем Разлома: после загрузки блоки ломаются киркой.
- Подход к новому Разлому в холмах и у озера: нет фриза, крыша под землёй, под озером Разлом не строится (в клетке может не быть Разлома); в логе нет строки про оценку генератором.
- Шипы, ветер, притяжение, Комета на мобе Разлома и Города: моб реально отлетает/подлетает, не проходит сквозь стены и пол, упирается в потолок, не уходит дальше поводка, после падения снова ходит; летуны (призма, моль) отлетают и затухают; лоза останавливает ходьбу на 3 с; статуя стоит.
- Моб данжа у зажжённой жаровни и под солнцем не получает урона от огня; поджог Искрами/Кометой жжёт.
- Поршень (в т.ч. липкий) рядом с данжем не двигает его блоки до победы; эндермен рядом с Городом не уносит блоки; после победы оба разрешены.
- Мод запускается (проверка совпадения `PistonEvent.Pre`, `EntityMobGriefingEvent`, `Event.Result` с Forge).
- Монолит: двойная волна в фазе 3-4 без просадки TPS.

## Решения и почему
- Колонны через одну (32 блока) вместо всех: допуск `COVER_OK = 2` и повторная проверка выбранной точки на настоящей высоте это покрывают, а стоимость падает вчетверо.
- Огонь по блокам и пожары Города не перехвачены: события для этого в Forge 1.20.1 нет.

## Проблемы и открытые вопросы
- Если `getFaceOffsetPos`/`Event.Result` в Forge отличаются от заглушек, мод упадёт при запуске: проверить запуск игры.
- `EntityMobGriefingEvent` общий для всех мобов, поэтому мобгрифинг возле данжа с живым боссом выключен целиком, не только для блоков данжа.

## Git (выполняет пользователь)
Набор 0089-0094 одним списком:
```
git add src/com/explosionwand/Altar.java src/com/explosionwand/ArenaGuard.java src/com/explosionwand/Boss.java src/com/explosionwand/Boss3.java src/com/explosionwand/CityMobsB.java src/com/explosionwand/Comet.java src/com/explosionwand/Dims.java src/com/explosionwand/DungeonData.java src/com/explosionwand/DungeonGuard.java src/com/explosionwand/DungeonSite.java src/com/explosionwand/Dungeons.java src/com/explosionwand/Earth.java src/com/explosionwand/ExplosionWandMod.java src/com/explosionwand/Minions.java src/com/explosionwand/RiftMobs.java src/com/explosionwand/RiftStaff.java src/com/explosionwand/TimeStop.java src/com/explosionwand/WhaleStaff.java
git add stubs/net/minecraft/world/entity/Entity.java stubs/net/minecraft/world/entity/animal/TamableAnimal.java stubs/net/minecraft/world/entity/TamableAnimal.java stubs/net/minecraft/world/entity/player/Player.java stubs/net/minecraft/world/entity/projectile/Projectile.java stubs/net/minecraft/world/level/Level.java stubs/net/minecraft/world/level/LevelAccessor.java stubs/net/minecraft/world/level/LevelHeightAccessor.java stubs/net/minecraft/world/level/BlockGetter.java stubs/net/minecraft/world/level/chunk/ChunkGenerator.java stubs/net/minecraft/world/level/levelgen/RandomState.java stubs/net/minecraft/server/level/ServerChunkCache.java stubs/net/minecraft/server/level/ServerLevel.java stubs/net/minecraftforge/eventbus/api/Event.java stubs/net/minecraftforge/event/level/BlockEvent.java stubs/net/minecraftforge/event/level/PistonEvent.java stubs/net/minecraftforge/event/entity/EntityMobGriefingEvent.java
git add tools/build.py tools/classcheck.py tools/gen_city.py resources/data/explosionwand/structures/sleep_north.nbt resources/data/explosionwand/structures/sleep_mid.nbt resources/data/explosionwand/structures/sleep_south.nbt
git add tests/DungeonTest.java tests/EarthTest.java tests/BossCityTest.java docs/dungeons.md docs/history.md docs/world.md
git add reports/0089-2026-10-21-build-engineer-class-path-audit.md reports/0090-2026-10-21-mod-engineer-rift-staff-revert.md reports/0091-2026-10-21-boss-engineer-monolith-arena-hp.md reports/0092-2026-10-21-mod-engineer-rift-spawn-wands-dungeons.md reports/0093-2026-10-21-code-reviewer-post-playtest-2-15-0.md reports/0094-2026-10-21-mod-engineer-review-fixes-0093.md
git commit -m "fix(dungeon): правки по ревью: барьер, посадка Разлома, толчки" -m "- миграция bossDead, поршни и мобгрифинг, посадка без загрузки чанков" -m "- реальный импульс и лоза для мобов данжей" -m "- классы и тесты по ревью 0093" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```
Часть файлов (`Altar.java`, `Dims.java`, `WhaleStaff.java`, `Boss.java`, `gen_city.py`, `.nbt`) относится к фазе 4.7 и 0091; если они уже в коммите, `git add` их пропустит.

## Следующий шаг
- Огонь по блокам Города (нет события Forge): решить в 4.8 (правило огня или несгораемые материалы).
- Повторное ревью (`code-reviewer`).
