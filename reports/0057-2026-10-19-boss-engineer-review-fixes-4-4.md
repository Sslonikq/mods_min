# 0057 · Исправления по ревью 0056 (логово Сердца Разлома)

- **Дата:** 2026-10-19
- **Агент:** boss-engineer
- **Версия мода:** 2.7.0 (не менялась)
- **Статус:** done
- **Связанные отчёты:** 0056 (ревью), 0054 (бой)

## Как бой выглядел и что меняется
Бой, фазы и цифры (ХП 900, урон 6/8/7/7, кулдауны) не менялись. Менялась только механика зачистки и тексты.
Было: через ~2 с после входа в зал 7 хост ещё не появился (он приходит через 60 тиков), `alive` пуст, зал засчитывался, сундук с Посохом выдавался без боя.

| Что | Было | Стало |
|---|---|---|
| Зачистка логова | по «мобов нет» через 30 тиков (blocker 1) | только победой: `Boss.dying` -> `won` -> `Dungeons.lairWon` |
| Хранение победы | `Boss.DONE` (в памяти) | `Rec.hallsCleared` (сохраняется), `DONE` удалён |
| Полоска после перезапуска | висит у игрока везде | один раз за сессию на запись `bossbar remove` (`Boss.SWEPT`), без условия «игрок рядом» |
| Тексты босса | русские строки в коде | `{"translate":"explosionwand.boss.rift.*"}`, 14 ключей в ru и en |
| Измерение `arena` | удалено | три JSON возвращены как были (из HEAD), в коде ссылок нет |
| Столбы без сущностей (nit 8) | `reset` только при мусоре | `reset` для `spawned && !cleared` |
| Altar kind 0 (nit 11) | конструктор `boolean`, палитра Разлома | убраны, `EclipseSealItem` передаёт `1` |
| Ядро столба (nit 12) | роняет блок аметиста | `DungeonGuard.onBreak` убирает ядро сам, дроп отменён |

## Что сделано
- `Dungeons.clearable(hasBoss, waited, aliveEmpty)` — чистая функция; для зала с боссом всегда false. Перезапуск во время пробуждения: хоста нет, зал не засчитывается, `orphans` сбрасывает отметку «разбужен», бой начнётся заново.
- `Dungeons.lairWon(...)`: `hallsCleared |= bit`, `m_77762_`, `onCleared` (сундук через `Loot.chest`). Вызывается из `Boss.tickAll` после того, как `tick()` вернул done и `won`. Победа после перезапуска не пропадает: зал уже в `hallsCleared`, логово не оживает.
- Тест `BossRiftTest`: логово не засчитывается (в т.ч. «пробуждение без хоста»), обычные залы по-прежнему.
- Nit 9 закрыт этим же: `DONE` нет.
- Nit 10: формулировку «`/kill` ведёт к гибели» считать неточной; `/kill` проходит через `onHurt` (обрезка до порога), гибель наступит только в ярости.
- Общие утилиты не менял. `tools/ids.py` и `RiftStaff*` не трогал. Lang правил дописыванием в конец через Python с сохранением ключей mod-engineer (проверено: `rift_staff.no_*` на месте).

## Изменённые файлы
| Файл | Что |
|---|---|
| `src/com/explosionwand/Boss.java` | победа, `SWEPT`/`barOf`, translate-тексты, `reset` при `spawned && !cleared` |
| `src/com/explosionwand/Dungeons.java` | `clearable`, `lairWon` |
| `src/com/explosionwand/DungeonGuard.java` | ядро столба без дропа |
| `src/com/explosionwand/Altar.java`, `EclipseSealItem.java` | убран kind 0 |
| `resources/data/explosionwand/dimension/arena.json`, `dimension_type/arena.json`, `worldgen/biome/arena.json` | возвращены из HEAD |
| `resources/assets/explosionwand/lang/ru_ru.json`, `en_us.json` | ключи `explosionwand.boss.rift.*` |
| `tests/BossRiftTest.java` | тесты зачистки |

## Проверка
`python tools/build.py`: 124 класса, SRG 220/221, неизвестных 0, ID 0, gen_dungeons 4/0, BossRiftTest/DungeonTest/MazeTest/RiftMobsTest/RiftStaffTest OK, jar 2.7.0. Новых SRG-имён нет.

## Проверить в игре
- Вход в зал 7: сундука нет, пока жив босс; выйти в меню в первые 3 с, загрузиться, постоять у входа: зал не засчитан.
- После победы и перезапуска: логово не оживает, сундук на месте.
- Перезапуск посреди боя, уйти от логова: полоски нет.
- В en_us заголовки и подсказки босса по-английски.
- Ядро столба ломается киркой без выпадения аметиста.
- Старый мир 2.6.x открывается без ошибок датапака.

## Следующий шаг
Повторное ревью (code-reviewer). Незакрытое: minor 7, major 3 — у mod-engineer.

## Git (выполняет пользователь)
В команде из 0056 убрать три пути `dimension/arena.json`, `dimension_type/arena.json`, `worldgen/biome/arena.json` из `git rm`. Добавить к коммиту фичи:
```
git add src/com/explosionwand/Boss.java src/com/explosionwand/Dungeons.java src/com/explosionwand/DungeonGuard.java src/com/explosionwand/Altar.java src/com/explosionwand/EclipseSealItem.java tests/BossRiftTest.java resources/assets/explosionwand/lang/ru_ru.json resources/assets/explosionwand/lang/en_us.json reports/0057-2026-10-19-boss-engineer-review-fixes-4-4.md
```
Файлы `resources/data/explosionwand/dimension/arena.json`, `dimension_type/arena.json`, `worldgen/biome/arena.json` не удалять: после возврата они совпадают с HEAD, `git status` их не покажет.
