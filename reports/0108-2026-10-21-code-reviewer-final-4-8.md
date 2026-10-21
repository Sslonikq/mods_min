# 0108 · Финальная перепроверка фазы 4.8 после правок 0107

- **Дата:** 2026-10-21
- **Агент:** code-reviewer
- **Версия мода:** 2.16.0
- **Статус:** done
- **Связанные отчёты:** 0106, 0107, 0099–0105

## Задача
Убедиться, что 0107 закрывает замечания 1–4 из 0106:
- решётки в `ash_*.nbt` соединены, щелей по реальной коллизии нет;
- `DungeonTest` падает, если соединений нет;
- nit-ы `DungeonMech` исправлены;
- `gen_dungeons.py` не изменил лишних файлов.

Код не менялся.

## Вердикт: **APPROVE WITH NITS**
Major 1–2 и nit 3–4 из 0106 закрыты. Сборка зелёная. Ниже два nit-а, коммит Святилища они не блокируют.

## Что сделано (проверено)
- **Решётки в шаблонах.** Проверял временным харнессом в `build/rv0108/` (после проверки удалён) на `Dungeons.assemble(ASH)`, то есть на реальных `ash_*.nbt`.
  - Всего 592 `iron_bars`, все стоят в стенах башни. На стенах N/S их 332, у всех `[east=true,west=true]`. На стенах W/E — 260, у всех `[north=true,south=true]`. Решёток с неверной ориентацией 0, голых 0.
  - Непрерывность полотна. Каждая сторона решётки с `=true` упирается либо в соседнюю решётку с ответным `=true`, либо в полный блок: открытых концов 0. Сверху и снизу каждой решётки стоит решётка или полный блок: вертикальных щелей 0. Значит, по коллизии арки (7×6 и 5×1) и бойницы 1×3 закрыты сплошным полотном толщиной 2/16. Игрок шириной 0,6 не помещается ни в одну из половин клетки (по 7/16).
  - `gen_ash.py:142-155` (`tower_bars`) повторяет `tower_carve` клетка в клетку, свойства соединений заданы явно.
- **Негативная проверка теста.** Тот же flood, что в `DungeonTest.ash()`, через `DungeonTest.open`:
  - шаблон как есть — внутри 0 клеток;
  - все решётки заменены на голые `minecraft:iron_bars` — внутри 40 406 клеток, flood падает;
  - проверка `bare == 0` тоже падает: 592 голых решётки.

  Тест ловит обе поломки.
- **Nit 3 (0106).** `DungeonMech.java:294`: предикат стал `e instanceof LivingEntity && e.m_6084_() && !e.m_5833_()`, импорт добавлен.
- **Nit 4 (0106).** `DungeonMech.java:392`: при поджоге без клика приходит actionbar `fires_wrong`, затем `fireWave`. Ключ есть в обоих lang (`:117`). Сообщение не повторяется каждую секунду: после `fireOut` проверка порядка снова проходит.
- **`gen_dungeons.py` и лишние файлы.** Сравнил `git status` до и после сборки:
  - отслеживаемые `seal_shard_rift.png`, `temple_seal.png` и все `.nbt` Разлома и Города с HEAD совпадают, в `M` их нет;
  - `ash_key.png` и `ember_key.png` — новые файлы фазы (untracked), так и должно быть;
  - `--check` сверяет байты с генератором (`gen_dungeons.py:1047-1053`), проблем 0.

  Лишних изменений нет.
- **Новых SRG** в 0107 нет. `m_6249_`, `m_6084_`, `m_5833_`, `m_5661_`, `m_237115_` в проекте уже использовались и проверены.

## Замечания
1. **[nit] tests/DungeonTest.java:33.** `open()` считает твёрдой любую решётку, у которой есть хоть одно `=true`. Если решётку поставить поперёк проёма (`north/south` в стене N), тест это пропустит. Сейчас такую поломку ловят только точечные проверки `:217-219`. → По желанию в общем цикле `:221` сверять ориентацию со стеной (`z∈{-50,-49,-19,-18}` → `east&west`, `x∈{±15,±16}` → `north&south`).
2. **[nit] DungeonMech.java:392.** `fires_wrong` получают все игроки из `in`, то есть все в данже, а не только те, кто у жаровен. → По желанию фильтровать по `nearAny`-радиусу.

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `reports/0108-2026-10-21-code-reviewer-final-4-8.md` | этот отчёт |

## Проверка
- `python tools/build.py`, один прогон:
  - 159 классов;
  - SRG 248/249, неизвестных 0;
  - пути классов: ошибок 0;
  - linkcheck: расхождений 0;
  - ID: проблем 0;
  - `gen_dungeons`: 10 файлов, проблем 0;
  - тесты: все 10 OK;
  - jar: `dist/explosionwand-1.20.1-2.16.0.jar` (1672 КБ).
- Временный харнесс `build/rv0108/` удалён. Исходники не менялись.

## Проверить в игре
- Арки колокольни N/S/W/E и бойницы на ярусах: решётки сплошным полотном, на элитрах сквозь них не пролететь.
- Поджечь жаровни стрелой в неверном порядке: жаровни гаснут, приходит волна стражей и сообщение `fires_wrong`.
- Стрела в стене у крошащейся платформы не мешает ей вернуться. Моб, стоящий на месте платформы, мешает.
- Остальное — по разделам «Проверить в игре» в 0101–0105.

## Решения и почему
- 0107 предлагал отдельный коммит `fix(dungeon)`, но это невозможно: `gen_ash.py` и `ash_*.nbt` новые (untracked), `DungeonMech.java` уже входит в коммит Святилища. Поэтому правки 0107 влиты в коммит Святилища, и ниже единый список.
- `EarthLogic.java` целиком в коммите хвостов (1), как и в 0106.

## Проблемы и открытые вопросы
- Политика барьера Святилища до 4.10 — на решении пользователя (0101/0102).
- Строку `Co-Authored-By` задал вызывающий агент (Sonnet 5.5).

## Git (выполняет пользователь)
Единый список фазы 4.8 (0099–0108), ROADMAP не включён.
```
git add src/com/explosionwand/RiftLogic.java src/com/explosionwand/RiftMobs.java src/com/explosionwand/Earth.java src/com/explosionwand/EarthLogic.java tests/RiftMobsTest.java tests/EarthTest.java reports/0099-2026-10-21-mod-engineer-tails-4-7.md
git commit -m "fix(dungeon): хвосты 4.7: предел падения, тесты толчка, лог сбоев" -m "- предел падения -3 и снап приземления в RiftLogic, тесты поводка" -m "- Earth.guard логирует повторные сбои (1, 10, 100...)" -m "- отмена клика при повторной выдаче Печати - в коммите Святилища (Dungeons.java)" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"

git add src/com/explosionwand/Boss3.java reports/0100-2026-10-21-boss-engineer-boss3-nits.md
git commit -m "fix(boss): хвосты ревью Монолита Сна (nit 5, 6, 8)" -m "- урон по хосту отменяется, пока host == null" -m "- полоска и титры не показываются наблюдателям" -m "- зал засчитывается в начале гибели" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"

git add tools/gen_ash.py tools/gen_dungeons.py src/com/explosionwand/Ash.java src/com/explosionwand/DungeonMech.java src/com/explosionwand/Dungeons.java src/com/explosionwand/Mats.java src/com/explosionwand/ExplosionWandMod.java src/com/explosionwand/jei/ExplosionWandJei.java stubs/net/minecraft/world/Container.java tests/DungeonTest.java resources/data/explosionwand/structures/ash_north.nbt resources/data/explosionwand/structures/ash_mid.nbt resources/data/explosionwand/structures/ash_south.nbt resources/assets/explosionwand/models/item/ash_key.json resources/assets/explosionwand/models/item/ember_key.json resources/assets/explosionwand/textures/item/ash_key.png resources/assets/explosionwand/textures/item/ember_key.png resources/assets/explosionwand/lang/ru_ru.json resources/assets/explosionwand/lang/en_us.json docs/world.md docs/dungeons.md reports/0101-2026-10-21-mod-engineer-ash-sanctuary-2a.md reports/0102-2026-10-21-mod-engineer-ash-sanctuary-2b.md reports/0105-2026-10-21-mod-engineer-review-fixes-4-8.md reports/0107-2026-10-21-mod-engineer-bars-connections-4-8.md
git commit -m "feat(dungeon): Пепельное святилище: башня, 5 уровней, колодец" -m "- gen_ash.py: башня с колоколом, шахта, Зал Пепла, Огненная переправа, Четыре жаровни, арена, Гнездо, колодец-выход" -m "- метки bell, jets, crumble, geyser, braziers в DungeonMech" -m "- Пепельный и Угольный ключи, повторная выдача ключей и Печати Храма" -m "- Dungeons.ASH обходит Разломы и Города, Осколок Пепел указывает на святилище" -m "- решётки с явными соединениями в проёмах башни, тест обхода по воздуху и связей" -m "- волна и сообщение при поджоге без клика; платформа ждёт только живых" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"

git add resources/META-INF/mods.toml docs/history.md reports/0103-2026-10-21-asset-engineer-release-2-16-0.md
git commit -m "docs(assets): релиз 2.16.0, Пепельное святилище" -m "- версия 2.16.0" -m "- запись в history.md" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"

git add reports/0104-2026-10-21-code-reviewer-review-4-8.md reports/0106-2026-10-21-code-reviewer-recheck-4-8.md reports/0108-2026-10-21-code-reviewer-final-4-8.md
git commit -m "docs(agents): ревью фазы 4.8" -m "- 0104: проёмы башни обходят дверь Осколка" -m "- 0106: решётки в арках без соединений" -m "- 0108: APPROVE WITH NITS, соединения и тест проверены" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

## Следующий шаг
- Закрыть фазу 4.8 у main (ROADMAP — отдельно, в этот список не включён).
- Nit 1–2 — по желанию, в следующей фазе.
