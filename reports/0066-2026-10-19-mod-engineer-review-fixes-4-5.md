# 0066 · Исправления по ревью 4.5

- **Дата:** 2026-10-19
- **Агент:** mod-engineer
- **Версия мода:** 2.8.0
- **Статус:** done
- **Связанные отчёты:** 0065 (ревью 4.5), 0063 (город)

## Задача
Закрыть major 1-2, minor 3 и 5, nit 7-9 из отчёта 0065. Minor 4, 6, nit 10, 11 не трогать.

## Что сделано
- **Major 1:** `City.tick` для не творческого и не наблюдателя игрока в стене, на ней (до y+22) и под городом (`City.inWalls`, |x|,|z| ≤ 45,5), пока центр метки `door a=sleep` не воздух (`gateOpen`), переносит его к метке `start` (`m_8999_`, падение обнуляется) с подсказкой `explosionwand.city.gate_closed` (ru/en).
- **Major 2:** `Dungeons.variation` для наземных данжей читает `ChunkAccess.getInhabitedTime` (`m_6319_`) в 9 точках; больше `SETTLED_TICKS` (5 минут) - точка пропускается; если обжиты все 8 запасных мест, клетка уходит в `dead`. Строка в `docs/history.md` 2.8.0.
- **Minor 5:** для наземных данжей перед `m_6325_` проверка `m_46805_`; чанк не загружен - регистрация клетки откладывается до следующего прохода `discover` (запись `dead` не создаётся). У Разлома (`flat = 0`) поведение прежнее.
- **Minor 3:** `SLEEP.ground(5, 8)` (было 12); `docs/dungeons.md` обновлён. Фундамент 10 покрывает перепад 8.
- **Nit 7:** пороги `>=`: предупреждение ровно на 2 с (WARN=40), замедление на 3 с, слепота на 6 с; тест `CityTest` подтверждает 60 и 120 тиков.
- **Nit 8:** `RiftStaff`: убран слипшийся комментарий и недостижимая ветка `inDungeon`, условие - `if (e.m_19880_().contains("ewd")) continue;`.
- **Nit 9:** фраза про ядро столба в `docs/history.md` исправлена.

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `src/com/explosionwand/City.java` | возврат от ворот, пороги `>=`, `inWalls`, `gateOpen` |
| `src/com/explosionwand/Dungeons.java` | обжитость, отложенная регистрация, `ground(5, 8)` |
| `src/com/explosionwand/RiftStaff.java` | nit 8 |
| `stubs/net/minecraft/world/level/chunk/ChunkAccess.java` | `m_6319_` |
| `resources/assets/explosionwand/lang/ru_ru.json`, `en_us.json` | `city.gate_closed` |
| `tests/CityTest.java` | пороги 60/120, `inWalls` |
| `docs/history.md`, `docs/dungeons.md` | 2.8.0: ворота, обжитые места, фраза про столб; `ground(5, 8)` |

## Проверка
- `python tools/build.py`: 125 классов, SRG исходники 226 / байткод 227, неизвестных 0, проблем ID 0, gen_dungeons 7 / 0, тесты BossRiftTest, CityTest, DungeonTest, MazeTest, RiftMobsTest, RiftStaffTest OK, jar 2.8.0.
- Новое SRG: `m_6319_` → `Chunk.getInhabitedTime` (`lookup.py --srg`, Mojang `ChunkAccess.getInhabitedTime`).
- Баланс: туман 3 и 6 с точно (было 3,5 и 6,5); перепад под городом ≤ 8 (было 12).

## Проверить в игре
- Столб у рва и прыжок на стену без Осколка: возврат к старту с подсказкой; с Осколком (ворота открыты) вход свободен, возврата нет.
- Старый мир 2.7.0 с домом: город не должен вставать на обжитые места (после 5 минут игры в чанках).
- Нет рывка при подходе к далёкому месту города; город всё же регистрируется при приближении.
- Туман: замедление через 3 с, слепота через 6 с.

## Решения и почему
- Ворота «открыты» определяются по блоку в проёме, не по `Rec.opened`: состояние хранится в мире, ничего нового в NBT.
- Для `variation` выбран «возврат результата» (константы `UNLOADED`/`SETTLED`), чтобы не менять сигнатуры.
- Порог обжитости 5 минут: в обычной игре чанки новой местности набирают его только при реальном присутствии игрока.

## Проблемы и открытые вопросы
- Если запасная точка (до 170 блоков от игрока) так и не загрузится, клетка ждёт, пока игрок не подойдёт ближе: это ожидаемо.
- Не тронуты по заданию: minor 4, 6, nit 10, 11.

## Git (выполняет пользователь)
```
git add src/com/explosionwand/City.java src/com/explosionwand/Dungeons.java src/com/explosionwand/RiftStaff.java stubs/net/minecraft/world/level/chunk/ChunkAccess.java resources/assets/explosionwand/lang/ru_ru.json resources/assets/explosionwand/lang/en_us.json tests/CityTest.java docs/history.md docs/dungeons.md reports/0066-2026-10-19-mod-engineer-review-fixes-4-5.md
git commit -m "fix(dungeon): ворота Города, обжитые места, ровность, пороги тумана" -m "- игрока внутри стены при закрытых воротах возвращает к старту" -m "- город не строится на обжитых чанках, незагруженные не генерируются синхронно" -m "- ground(5, 8), туман ровно 3 и 6 с, чистка RiftStaff"
```
(Если коммит 3 из 0065 ещё не сделан, добавить эти файлы к нему.)

## Следующий шаг
- **4.6:** minor 6 (настоящие мобы на хостах с поводком вместо вексов-заглушек).
- **4.7:** minor 4 (повторная выдача Печати Храма из саркофага); nit 11 (`hallsSpawned/hallsCleared` на `long`, когда залов станет больше 31).
- Nit 10 (`Dims.evacuateOldArena`, `safeY`) - по желанию.
- **code-reviewer:** повторное ревью major 1-2, minor 3, 5.
