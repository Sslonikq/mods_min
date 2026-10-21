# 0106 · Перепроверка фазы 4.8 после правок 0105

- **Дата:** 2026-10-21
- **Агент:** code-reviewer
- **Версия мода:** 2.16.0
- **Статус:** done
- **Связанные отчёты:** 0104, 0105, 0099–0103

## Задача
Проверить, что правки 0105 закрывают замечания ревью 0104: major 1 (решётки в проёмах башни и тест обхода по воздуху), minor 2–4, nit 5–8. Убедиться, что регрессий нет. Код не менялся.

## Вердикт: **REQUEST CHANGES**
Minor 2–4 и nit 5–8 закрыты, сборка зелёная. **Major 1 закрыт только на 1×3 бойницах.** В широких арках колокольни решётки остаются отдельными столбиками без соединений: между соседними столбиками есть щель, через которую пролезает игрок. Тест этого не видит. Правка маленькая (свойства решёток в `gen_ash.py` и одна проверка в тесте).

## Что сделано (проверено)
- **Major 1, геометрия.** `tower_bars` (`gen_ash.py:142-153`) повторяет `tower_carve` (`:129-139`) клетка в клетку: все 1×3 бойницы на ярусах y 4/18/32 по четырём стенам, арки N/S/W/E y 44–49 и верхний ряд y 50. Других проёмов в рамке башни снаружи нет. Пристройки соединены только с башней (арки `:284`, `:287`). Тайник 14 закрыт кладкой, шахта лежит под нефом. Шпиль и крыша сплошные, костры на крыше стоят на тюках сена.
- **Major 1, тест падает без решёток.** Временный харнесс в `build/` (после проверки удалён) повторял flood из `DungeonTest.ash():621-637` на `Dungeons.assemble(ASH)`. Результаты:
  - с решётками: внутри 0 клеток из 499 230 при проходе высотой 2 и тоже 0 при высоте 1 (поза полёта на элитрах);
  - если заменить все 592 `iron_bars` воздухом: внутри 40 406 клеток (высота 2) и 43 156 (высота 1), первая найденная клетка (-14, 52, -20);
  - шахта (y < 0) снаружи недостижима в обоих случаях.

  Вывод: по клеткам тест обход ловит.
- **Major 1, чего тест не видит (замечание 1 ниже).** Шаблон хранит решётки как голое `minecraft:iron_bars`, то есть со всеми соединениями `false`. `Dungeons` ставит блоки с флагом 18 (`Dungeons.java:384`, `UPDATE_CLIENTS|UPDATE_KNOWN_SHAPE`), поэтому соседи формы не пересчитывают, и решётки так и остаются отдельными столбиками.
  - У одиночной решётки коллизия — только столбик 2/16 в центре клетки (7..9/16).
  - В бойнице шириной 1 до камня с каждой стороны остаётся 7/16 ≈ 0,44 блока. Это меньше ширины игрока 0,6, так что бойницы закрыты.
  - В арке шириной 7 (и 5 в верхнем ряду) между столбиками соседних клеток щель 14/16 ≈ 0,875 блока. Это больше 0,6: в каждую арку проходят игрок на элитрах и даже пешком. Обе клетки в глубину стоят на одних и тех же x/z, так что щели совпадают и пролетаются насквозь.
  - Flood считает `iron_bars` целым твёрдым блоком, поэтому этого не видит.
- **Minor 2.** `DungeonMech.fires:585-588`: при нарушенном наборе зовутся `fireOut` и `fireWave`.
  - Повторной волны каждую секунду не будет: после `fireOut` все костры потушены, а `fireLitAt` считает горящим только костёр, так что набор снова становится верным началом порядка.
  - Внутри `fireWave` учтены `WAVE_CAP` и мирный режим. Тик обёрнут в `try` (`Dungeons.tick`).
- **Minor 3.** `crumbles:487-490`: перед возвратом платформы ищутся сущности через `m_6249_` по AABB платформы с предикатом `m_6084_ && !m_5833_`. AABB расширен на 0,1 по горизонтали и сужен на 0,01 по вертикали. Возврат откладывается, пока в AABB кто-то есть. SRG проверены: `m_6249_` = `getOtherEntities(Entity, Box, Predicate)`, `m_6084_` = `isAlive`, `m_5833_` = `isSpectator`. Все три уже используются в проекте.
- **Minor 4.** Оговорка про ключ, переложенный в другой или эндер-сундук, есть в `docs/dungeons.md:225`. Проверка «с воздуха» упомянута в `:250`.
- **Nit 5, 6.** `ExplosionWandMod`: у `ASH_KEY`, `EMBER_KEY` и `TEMPLE_SEAL` теперь по своему javadoc. Javadoc `ASH_SEAL_SHARD` обновлён.
- **Nit 7.** `EarthLogic`: `warnDue` со своим javadoc стоит перед javadoc `moved`.
- **Nit 8.** `trim()` в `fireSlots:519` и `onClick:699`.
- **Регрессии.** Сборка, проверки и все 10 тестов зелёные. В `Ash`, `Dungeons`, `Boss3` правки 0105 ничего не меняли.

## Замечания
1. **[major] tools/gen_ash.py:149-152.** Решётки в арках колокольни стоят без свойств соединения, а постановка с флагом 18 их не соединяет. Между столбиками щель 0,875 блока, игрок проходит, так что обход двери `ash` через колокольню остаётся.
   → В `tower_bars` задать свойства явно:
   - арки N/S (тянутся по x): `iron_bars[east=true,west=true]`;
   - арки W/E (тянутся по z): `iron_bars[north=true,south=true]`;
   - бойницы, ради вида: N/S стены — `east/west=true`, W/E — `north/south=true`. Решётки соединяются и с полным камнем, поэтому крайним клеткам `true` тоже подходит.

   Затем пересоздать `ash_*.nbt` и прогнать `tools/ids.py`: он проверит свойства.
2. **[major] tests/DungeonTest.java:638.** Проверка `equals("minecraft:iron_bars")` закрепляет как раз сломанное состояние без соединений, а flood на `:631` не моделирует коллизию столбика.
   → Проверять, что решётка в арке соединена вдоль проёма: например, `(0,46,-50)` содержит `east=true` и `west=true`, `(-16,46,-34)` — `north=true` и `south=true`. Ещё лучше — общая проверка: у каждой `iron_bars` в шаблоне есть хотя бы одно `=true` вдоль проёма.
3. **[nit] DungeonMech.java:489.** Предикат пропускает любые сущности: предмет, стрелу в стене рядом с краем (из-за расширения на 0,1), сферу опыта. Застрявшая стрела задержит возврат платформы до 60 с (пока не исчезнет). → `e instanceof LivingEntity && e.m_6084_() && !e.m_5833_()`.
4. **[nit] DungeonMech.java:587.** Волна от поджога без клика приходит без сообщения `fires_wrong` (при клике оно есть). Игрок может не понять, за что ему волна. → По желанию слать actionbar игрокам из `in`.

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `reports/0106-2026-10-21-code-reviewer-recheck-4-8.md` | этот отчёт |

## Проверка
- `python tools/build.py` (один прогон):
  - 159 классов;
  - SRG исходники/байткод 248/249, неизвестных 0;
  - пути классов: ошибок 0;
  - linkcheck: расхождений 0;
  - проблем ID 0;
  - `gen_dungeons`: 10 файлов, проблем 0;
  - тесты: все 10 OK;
  - jar `dist/explosionwand-1.20.1-2.16.0.jar` (1672 КБ).
- Новых SRG в 0105 нет. Использованные `m_6249_`, `m_6084_`, `m_5833_` проверены через `lookup.py --srg`.
- Временный харнесс лежал в `build/rv0106/`, удалён. Исходники не менялись.

## Проверить в игре
- После правки замечания 1: подлететь на элитрах к арке колокольни (N/S/W/E) и к бойнице. Решётки должны быть сплошным полотном, а не рядом столбиков, и пролететь сквозь них нельзя.
- Остальное — как в 0105: поджог стрелой вызывает волну, моб на месте платформы не даёт ей вернуться.

## Решения и почему
- REQUEST CHANGES, хотя в 0104 major можно было отложить. В 0105 он заявлен исправленным, и тест зелёный при живом обходе: ложная уверенность хуже открытого замечания. Правка в две строки, её стоит сделать до коммита Святилища.

## Проблемы и открытые вопросы
- Политика барьера Святилища до 4.10 — по-прежнему на решении пользователя (0101/0102).
- Строку `Co-Authored-By` ниже указал вызывающий агент (Sonnet 5.5). При желании пользователь может её заменить.

## Git (выполняет пользователь)
Коммит Святилища (3) делать **после** правки замечаний 1–2 (новый отчёт `mod-engineer`, его добавить в тот же `git add`). Правки 0105 влиты в этот коммит. Исключение — `EarthLogic.java`: файл целиком уходит в коммит хвостов (1), перестановка javadoc из 0105 (nit 7) едет вместе с ним.
```
git add src/com/explosionwand/RiftLogic.java src/com/explosionwand/RiftMobs.java src/com/explosionwand/Earth.java src/com/explosionwand/EarthLogic.java tests/RiftMobsTest.java tests/EarthTest.java reports/0099-2026-10-21-mod-engineer-tails-4-7.md
git commit -m "fix(dungeon): хвосты 4.7: предел падения, тесты толчка, лог сбоев" -m "- предел падения -3 и снап приземления в RiftLogic, тесты поводка" -m "- Earth.guard логирует повторные сбои (1, 10, 100...)" -m "- отмена клика при повторной выдаче Печати - в коммите Святилища (Dungeons.java)" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"

git add src/com/explosionwand/Boss3.java reports/0100-2026-10-21-boss-engineer-boss3-nits.md
git commit -m "fix(boss): хвосты ревью Монолита Сна (nit 5, 6, 8)" -m "- урон по хосту отменяется, пока host == null" -m "- полоска и титры не показываются наблюдателям" -m "- зал засчитывается в начале гибели" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"

git add tools/gen_ash.py tools/gen_dungeons.py src/com/explosionwand/Ash.java src/com/explosionwand/DungeonMech.java src/com/explosionwand/Dungeons.java src/com/explosionwand/Mats.java src/com/explosionwand/ExplosionWandMod.java src/com/explosionwand/jei/ExplosionWandJei.java stubs/net/minecraft/world/Container.java tests/DungeonTest.java resources/data/explosionwand/structures/ash_north.nbt resources/data/explosionwand/structures/ash_mid.nbt resources/data/explosionwand/structures/ash_south.nbt resources/assets/explosionwand/models/item/ash_key.json resources/assets/explosionwand/models/item/ember_key.json resources/assets/explosionwand/textures/item/ash_key.png resources/assets/explosionwand/textures/item/ember_key.png resources/assets/explosionwand/lang/ru_ru.json resources/assets/explosionwand/lang/en_us.json docs/world.md docs/dungeons.md reports/0101-2026-10-21-mod-engineer-ash-sanctuary-2a.md reports/0102-2026-10-21-mod-engineer-ash-sanctuary-2b.md reports/0105-2026-10-21-mod-engineer-review-fixes-4-8.md
git commit -m "feat(dungeon): Пепельное святилище: башня, 5 уровней, колодец" -m "- gen_ash.py: башня с колоколом, шахта, Зал Пепла, Огненная переправа, Четыре жаровни, арена, Гнездо, колодец-выход" -m "- метки bell, jets, crumble, geyser, braziers в DungeonMech" -m "- Пепельный и Угольный ключи, повторная выдача ключей и Печати Храма" -m "- Dungeons.ASH обходит Разломы и Города, Осколок Пепел указывает на святилище" -m "- решётки в проёмах башни, тест обхода по воздуху; волна при поджоге без клика; платформа не возвращается на мобов" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"

git add resources/META-INF/mods.toml docs/history.md reports/0103-2026-10-21-asset-engineer-release-2-16-0.md
git commit -m "docs(assets): релиз 2.16.0, Пепельное святилище" -m "- версия 2.16.0" -m "- запись в history.md" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"

git add reports/0104-2026-10-21-code-reviewer-review-4-8.md reports/0106-2026-10-21-code-reviewer-recheck-4-8.md
git commit -m "docs(agents): ревью и перепроверка фазы 4.8" -m "- 0104: APPROVE WITH NITS, major: проёмы башни обходят дверь Осколка" -m "- 0106: правки 0105 приняты, кроме соединений решёток в арках колокольни" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

## Следующий шаг
- `mod-engineer`: замечания 1–2 (свойства соединения `iron_bars` в `tower_bars`, пересоздать `ash_*.nbt`, проверка соединений в `DungeonTest.ash()`), попутно nit 3–4. Новый отчёт добавить в `git add` коммита Святилища.
- Затем короткая перепроверка `code-reviewer` (только `gen_ash.py:142-153` и тест) и закрытие фазы 4.8 у main.
