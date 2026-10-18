# 0007 · Защита арен боссов от разрушения и застройки

- **Дата:** 2026-10-18
- **Агент:** mod-engineer
- **Версия мода:** см. mods.toml (версию не менял)
- **Статус:** done (build.py не запускался — его запустит build-engineer)
- **Связанные отчёты:** —

## Задача
Фаза 1 (а): арены боссов (ARENA, ECLIPSE, DREAM, SKY) нельзя ломать и застраивать; взрывы не должны разрушать блоки арен.

## Что сделано
- Новый класс `ArenaGuard` (образец — `Maze.onBreak`):
  - `onBreak(BlockEvent.BreakEvent)` — отмена для игроков не в творческом режиме;
  - `onPlace(BlockEvent.EntityPlaceEvent)` — то же для установки блоков;
  - `onDetonate(ExplosionEvent.Detonate)` — `getAffectedBlocks().clear()` в этих измерениях (крипер, TNT, взрывные палочки, Comet/Ember/Fist/Rift/Possess с `TNT`-взаимодействием).
- Слушатели зарегистрированы в `ExplosionWandMod` после `Maze::onBreak`.
- Проверка эффектов боссов: события их не затрагивают. Эффекты арен (обрушение Короля Затмения, провалы пола Монолита) идут через `Cmd`/setBlock/fill, а они `BreakEvent`/`EntityPlaceEvent`/`Detonate` не вызывают. Реальные взрывы `m_255391_` в Boss*/Arena* встречаются один раз: `Boss2:419` с `ExplosionInteraction.NONE` (блоки не ломает, очистка списка ему не вредит). `BlackHole` (NONE) ломает блоки своим кодом, не событием — вне задачи.

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `src/com/explosionwand/ArenaGuard.java` | новый |
| `src/com/explosionwand/ExplosionWandMod.java` | 3 `addListener` |
| `stubs/net/minecraftforge/event/level/BlockEvent.java` | + `EntityPlaceEvent.getEntity()` |
| `stubs/net/minecraftforge/event/level/ExplosionEvent.java` | новая заглушка: `getLevel`, `getExplosion`, `Detonate.getAffectedBlocks` |

## Проверка
- `python tools/build.py`: не запускался (за build-engineer).
- Новые SRG-имена: нет (использованы уже имеющиеся `m_7500_`, `m_9236_`; методы Forge-событий обычные).

## Проверить в игре
- Выживание в каждой из 4 арен: блоки не ломаются и не ставятся; в творческом — можно.
- Крипер/TNT/взрывная палочка в арене не оставляют воронок, урон существам идёт.
- Обрушение арены Затмения и провалы пола Монолита работают как раньше.

## Решения и почему
- Ставят/ломают только `ServerPlayer`; не-игроки (эндермены и т.п.) в `EntityPlaceEvent` не отменяются — по заданию.
- Взрыв: чистим список блоков, а не отменяем событие, чтобы урон и эффекты остались.

## Проблемы и открытые вопросы
- Ведро/огниво/жидкости могут менять блоки не через эти события (ведро шлёт `EntityPlaceEvent` в Forge — отменится; растекание жидкости и огонь — нет).

## Git (выполняет пользователь)
```
git add src/com/explosionwand/ArenaGuard.java src/com/explosionwand/ExplosionWandMod.java stubs/net/minecraftforge/event/level/BlockEvent.java stubs/net/minecraftforge/event/level/ExplosionEvent.java reports/0007-2026-10-18-mod-engineer-arena-protection.md
git commit -m "feat(arena): защита арен боссов от разрушения и застройки" -m "- отмена BreakEvent/EntityPlaceEvent для игроков не в творческом" -m "- взрывы в аренах не разрушают блоки"
```

## Следующий шаг
build-engineer: прогнать `python tools/build.py` (проверить компиляцию заглушек).
