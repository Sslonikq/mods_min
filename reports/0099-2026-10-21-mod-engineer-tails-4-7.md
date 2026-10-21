# 0099 · Хвосты фазы 4.7 (фаза 4.8, шаг 1)

- **Дата:** 2026-10-21
- **Агент:** mod-engineer
- **Версия мода:** 2.15.0 (не менялась; релиз 2.16.0 за asset-engineer)
- **Статус:** done
- **Связанные отчёты:** 0087, 0094, 0098

## Задача
Закрыть nit 1 и 3 из 0098, nit 5-8 из 0087, немой `Earth.guard`.

## Что сделано
- **0098 nit 1:** предел падения -3 блока/тик. `RiftLogic.gravity(ky) = max(ky-0.08, FALL_MAX)`, `RiftMobs.applyKnock` использует её.
- **0098 nit 3:** `RiftLogic.landY` (снап приземления) вынесен из `applyKnock`; в `tests/RiftMobsTest.knockLeashLandTests` проверены поводок (наружу за `leash` не уйти, к дому летит целиком, из-за предела возвращается, падение по y не блокируется; через `RiftLogic.Step`, как в `applyKnock`), предел падения и снап (падение, занятая клетка, не при падении).
- **0087 nit 7:** `Dungeons` (реиздание Печати): при выдаче теперь `ev.setCanceled(true)`, Печать не кладётся в сундук тем же кликом.
- **0087 nit 9 / задача 4:** `Earth.guard` считает сбои (`tickFails`) и пишет в лог №1, 10, 100, 1000... (`EarthLogic.warnDue`), без спама и без немой потери. Тест в `EarthTest`.
- **0087 nit 5, 6, 8:** все в `Boss3.java` (зона boss-engineer, не трогал): 5 - отмена урона по `ewb3_host` пока `host == null`; 6 - `bossbar`/title привязать к `players()`; 8 - ставить `lairWon` в `startDeath`. Нужен boss-engineer.
- **0087 nit 10:** CLAUDE.md и `docs/world.md` - зона main, не трогал.
- Огонь по дереву Города: не трогал, в `docs/dungeons.md` уже стоит как открытое.

## Изменённые файлы
`src/com/explosionwand/RiftLogic.java`, `RiftMobs.java`, `Earth.java`, `EarthLogic.java`, `Dungeons.java`, `tests/RiftMobsTest.java`, `tests/EarthTest.java`.

## Проверка
`python tools/build.py`: SRG неизвестных 0, ID 0 проблем, linkcheck 0, все тесты OK, jar собран. Новых SRG-имён нет (только свои методы).

## Проверить в игре
- Моба данжа сбросить с большой высоты (Ветер/Комета): падает не быстрее 3 блоков/тик, встаёт на землю.
- ПКМ по саркофагу при потерянной Печати с открытым сундуком: Печать приходит в инвентарь, в сундук не кладётся.

## Решения и почему
Логи сбоев по степеням десяти: одна строка на порядок, счётчик виден в тексте.

## Проблемы и открытые вопросы
Boss3 nit 5, 6, 8 остались.

## Следующий шаг
- boss-engineer: Boss3 nit 5, 6, 8 из 0087.
- main: CLAUDE.md:47 и `docs/world.md` (nit 10).

## Git (выполняет пользователь)
```
git add src/com/explosionwand/RiftLogic.java src/com/explosionwand/RiftMobs.java src/com/explosionwand/Earth.java src/com/explosionwand/EarthLogic.java src/com/explosionwand/Dungeons.java tests/RiftMobsTest.java tests/EarthTest.java reports/0099-2026-10-21-mod-engineer-tails-4-7.md
git commit -m "fix(dungeon): хвосты 4.7: предел падения, тесты толчка, лог сбоев" -m "- предел падения -3 и снап приземления в RiftLogic, тесты поводка" -m "- Earth.guard логирует повторные сбои (1, 10, 100...)" -m "- повторная выдача Печати отменяет клик" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```
