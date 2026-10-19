# 0081 · Фаза 4.11, часть B: палочки Земли 1–8

- **Дата:** 2026-10-19
- **Агент:** mod-engineer
- **Версия мода:** 2.9.0 (не повышалась: релиз в конце части C)
- **Статус:** done
- **Связанные отчёты:** 0080 (часть A: материалы)

## Задача
Палочки 1–8 таблицы «ИТОГ» docs/world.md: Огонька, Морозца, Роста, Сбора, Возврата, Сигнальная (стартовые), Копателя, Взрывная (урезанная).

## Что сделано
Один класс предмета `EarthWandItem` (вид = `EarthLogic.Kind`), логика в `Earth`, чистые параметры в `EarthLogic`, временные блоки в `EarthData`.
- **Огонька:** ПКМ вешает огонёк в точке прицела (до 24 блоков, от стены отступает до свободной клетки). Снаружи это блок `minecraft:light` (15) на 3 мин, не больше 20 на измерение (старый гаснет первым). В данже и в наших измерениях блока нет: только частицы. Во всех случаях `CityMobs.registerLight(radius 8, 3600)`; ручка `snuffed` (Ночная моль) убирает блок или частицы. Перезарядка 0,5 с.
- **Морозца:** конус 9 блоков (cos 0,75) без игроков: Замедление III 6 с, +заморозка. Вода-источник у ног и впереди (6 вперёд, 3 в ширину) становится `ice` на 20 с, потом обратно в источник воды. Рядом с данжами и в наших измерениях лёд не ставится. Перезарядка 2 с.
- **Роста:** `randomTick` x10 для растений и саженцев в радиусе 5 (вертикаль -2..+3, до 120 блоков), список в `EarthLogic.GROWABLE` + `*_sapling`. Рядом с данжем (12 блоков) и в наших измерениях молчит. Перезарядка 5 с.
- **Сбора:** 3 секунды (`PULLS`, тик каждые 2) притягивает `ItemEntity` и `ExperienceOrb` в радиусе 10 скоростью к игроку, до 128 сущностей за проход, кольцо частиц сжимается. Перезарядка 5 с.
- **Возврата:** ПКМ запускает каст 5 с (`CASTS`): прерывается сдвигом (0,6 горизонтально, 1,2 вертикально) или падением здоровья; при успехе телепорт к своей кровати/якорю возрождения (в том же или другом измерении, как при смерти; кровать должна стоять на месте, иначе сообщение), перезарядка 10 мин (x `ArcaneSet.cd`), при сбое 2 с. В наших измерениях запрещено, в точку внутри данжа не переносит. В `DungeonGuard.forbidden`.
- **Сигнальная:** столб частиц красный/золотой высотой 110 на 15 с (до 6 меток), вспышка и фейерверк `LifeTime:0` в небе через `Cmd`. Точка прицела (128), без попадания игрок. Блоки не меняет, работает везде. Перезарядка 10 с.
- **Копателя:** ПКМ по блоку (до 6): тоннель 3x3 глубиной 8 вдоль доминирующей оси взгляда (вертикаль, если она больше обеих горизонталей), по срезу каждые 2 тика через `Later` (проверка «игрок жив и в том же измерении»). Пропускает данжи (`Shield`), бедрок (твёрдость < 0), воду/лаву, блоки с `BlockEntity` (сундуки не теряют содержимого). Дроп как алмазной киркой (`Block.m_49881_`), в креативе без дропа. Перезарядка 8 с. В `DungeonGuard.forbidden`; в наших измерениях не работает.
- **Взрывная (переделана):** ПКМ, луч 48 блоков; попадание по сущности даёт 6 урона (`playerAttack`), взрыв силой 1,4 (динамит 4) в режиме `NONE` (урон и отброс, блоки сам не ломает) и вручную ломается 2–4 ближайших блока (твёрдость <= 4, не данж, не контейнер, дроп). В наших измерениях блоки не ломаются. Перезарядка 1,25 с. **Убрано:** Shift+ПКМ «Орбитальный удар» и горящие осколки (`Ember`). Класс `Ember` и его строка в `Ticker` остались (мёртвый код, не трогал: общая утилита, в отчёт).
- Регистрация: `ExplosionWandMod` (блок «часть B», сразу после комментария 4.11 перед материалами; `earthProps`). Копатель RARE, остальные UNCOMMON. Взрывная осталась на старом месте (поле `EXPLOSION_WAND`). Комментарий-место для части C: «палочки 9-16 регистрируются СРАЗУ ПОСЛЕ этой строки».
- Тик: `Earth.tick(sl)` в `Ticker` (после `ArcaneSet.tick`); `Earth.clear()` в `Dungeons.clearState`.
- Ресурсы: модели `handheld`, текстуры 16x16 (`tools/gen_earth_wands.py`, без PIL; у Взрывной старая текстура), 7 рецептов + новый `explosion_wand.json`, lang ru+en (название, desc1, desc2, JEI, 9 сообщений `explosionwand.earth.*`), 8 страниц JEI.
- Рецепты: стартовые `["  X"," C ","R  "]` (R стержень, C Простое ядро, X: светокамень, плотный лёд, костный блок, воронка, любая кровать `#minecraft:beds`, фейерверк). Копатель `["DXD","DRD"]` (4 алмаза, Пыль Разлома, стержень, без ядра по таблице). Взрывная `["TE "," C ","R  "]` (динамит, Кристалл эха, Кристальное ядро, стержень).

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `src/com/explosionwand/Earth.java`, `EarthLogic.java`, `EarthData.java`, `EarthWandItem.java` | новые |
| `src/com/explosionwand/ExplosionWandItem.java` | переделан: тычок, без орбитального удара |
| `src/com/explosionwand/ExplosionWandMod.java` | 7 регистраций, `earthProps` |
| `src/com/explosionwand/Ticker.java`, `Dungeons.java`, `DungeonGuard.java` | `Earth.tick`, `Earth.clear`, `forbidden` |
| `src/com/explosionwand/jei/ExplosionWandJei.java` | 8 страниц |
| `stubs/.../Level.java`, `Block.java`, `BlockBehaviour.java`, `util/RandomSource.java`, `world/entity/ExperienceOrb.java` | заглушки |
| `tests/EarthTest.java`, `tests/MatsTest.java` | новый тест; из MatsTest убрана проверка «рецепта explosion_wand нет» |
| `tools/gen_earth_wands.py` | генератор текстур |
| `resources/...` | 7 моделей, 7 текстур, 8 рецептов, lang x2 |

## Проверка
- `python tools/build.py`: 154 класса, SRG 231/232, неизвестных 0, проблем ID 0, тесты OK (новый EarthTest), jar `dist/explosionwand-1.20.1-2.9.0.jar`.
- Новые SRG (проверены `lookup.py --srg`): `m_222972_` BlockState.randomTick(ServerLevel,BlockPos,RandomSource); `f_46441_` Level.random; `m_49881_` Block.dropResources(state,level,pos,be,entity,tool); `m_76170_` FluidState.isSource (yarn isStill); `m_269075_` playerAttack; `m_8961_`/`m_8963_`/`m_8999_` respawn pos/dim/teleportTo. Enum `Level.ExplosionInteraction.NONE` через `valueOf`.
- Баланс (первая прикидка, фаза 8): перезарядки в `EarthLogic.Kind` и константах.

## Проверить в игре
- Огонёк: свет в воздухе 3 мин, гаснет и блок исчезает; после выхода из игры и возврата просроченный свет убирается; в данже (Город) блока нет, моль гасит частицы.
- Морозец: мобы в конусе замедлены; на реке дорожка льда, через 20 с снова вода; рядом с данжем льда нет.
- Рост: пшеница/саженец ускоряются (саженец обычно за 1–2 нажатия); рядом с данжем сообщение.
- Сбор: предметы и опыт летят к игроку, не застревают; предметы, только что выброшенные игроком, тоже возвращаются (так задумано).
- Возврат: полоска каста нет (только частицы и сообщение); шаг или удар прерывает; успех при кровати в том же и другом измерении; без кровати или с разрушенной кроватью — сообщение; перезарядка 10 мин (в иконке); в данже молчит.
- Сигнальная: столб виден за сотни блоков, фейерверк в небе.
- Копатель: тоннель по срезам, вертикально при взгляде вниз/вверх; сундук/бедрок/данж остаются; дроп руды корректный.
- Взрывная: урон по мобу, слабый взрыв, 2–4 сломанных блока, блоки данжа целы; Shift ничего особого не делает.
- Креатив-вкладка «Магия»: порядок и текстуры; JEI: страницы и рецепты.

## Решения и почему
- Один класс предмета вместо семи: общая логика тултипа и кулдауна; запреты в данжах читаются из `Kind.noDungeon`.
- Свет через блок `light` + `EarthData` в данных измерения: игровое время мира сохраняется между запусками, поэтому остаточные невидимые блоки не копятся. Альтернатива «сущность со светом» не существует.
- Ломать блоки в данжах Огоньку нельзя (правило CLAUDE.md), поэтому там виртуальный огонёк: хук для мобов работает.
- Взрывная: `ExplosionInteraction.NONE` + ручное разрушение, чтобы число блоков было точно 2–4 и проверка данжей была явной.
- Возврат без `Dims.teleport` в том же измерении: `ServerPlayer.teleportTo`; между измерениями `Dims.teleport`.

## Хуки и паттерны для части C (палочки 9–16)
- Новая палочка: значение в `EarthLogic.Kind(id, cd, noDungeon)`, ветка в `switch` метода `Earth.use`, метод в `Earth`, регистрация в `ExplosionWandMod` после комментария части C, lang (`item.explosionwand.<id>`, `.desc1`, `.desc2`, `explosionwand.jei.<id>`), модель/текстура (`tools/gen_earth_wands.py`: добавить функцию в `WANDS`, запустить; для данжей нужны другие цвета), рецепт `["  X"," C ","R  "]` с нужным ядром (`wind_core`, `fire_core`, `crystal_core`) и редкими материалами, страница JEI (список в `ExplosionWandJei` и проверка в `EarthTest`).
- Перезарядка: `Earth.use` возвращает базовые тики, предмет умножает на `ArcaneSet.cd` (0 тиков = перезарядки нет).
- Блоки: `canEdit(sl)` (не наши измерения), `Dungeons.shield(sl)` один раз на проход, `breakable(...)` для ломки, `Earth.addTemp`/`EarthData` для блоков, которые должны исчезнуть по сроку.
- Фон: `Earth.tick` вызывает tick-методы списков (`PULLS`, `BEACONS`, `CASTS`); добавить свой список и очистку в `Earth.clear`.
- Ветер (11): `CityMobs.gust(...)` по-прежнему ждёт вызова. Притяжение (13) можно строить на `tickPulls`, Двойника (14) на `Cmd`/`Minions`.
- Тесты чистой логики: `EarthLogic` + `tests/EarthTest.java`.

## Проблемы и открытые вопросы
- `Ember` теперь не используется (`Ticker` его тикает вхолостую): удалить при ближайшей чистке.
- Версия `explosion_wand`-рецепта: старый был удалён в части A, теперь создан новый; `MatsTest` подправлен соответственно.
- Не проверено в игре: все эффекты.

## Git (выполняет пользователь)
```
git add src/com/explosionwand/Earth.java src/com/explosionwand/EarthLogic.java src/com/explosionwand/EarthData.java src/com/explosionwand/EarthWandItem.java src/com/explosionwand/ExplosionWandItem.java src/com/explosionwand/ExplosionWandMod.java src/com/explosionwand/Ticker.java src/com/explosionwand/Dungeons.java src/com/explosionwand/DungeonGuard.java src/com/explosionwand/jei/ExplosionWandJei.java stubs/net/minecraft/world/level/Level.java stubs/net/minecraft/world/level/block/Block.java stubs/net/minecraft/world/level/block/state/BlockBehaviour.java stubs/net/minecraft/util/RandomSource.java stubs/net/minecraft/world/entity/ExperienceOrb.java tests/EarthTest.java tests/MatsTest.java tools/gen_earth_wands.py resources/assets/explosionwand/lang/ru_ru.json resources/assets/explosionwand/lang/en_us.json resources/assets/explosionwand/models/item resources/assets/explosionwand/textures/item resources/data/explosionwand/recipes reports/0081-2026-10-19-mod-engineer-earth-wands-4-11b.md
git commit -m "feat(wand): палочки Земли 1-8 (стартовые, Копатель, Взрывная)" -m "- Огонька, Морозца, Роста, Сбора, Возврата, Сигнальная, Копателя" -m "- Взрывная урезана: тычок и слабый взрыв, орбитальный удар убран" -m "- рецепты, текстуры, lang, JEI, тест EarthTest"
```
(Часть файлов — Dungeons.java, Ticker.java, DungeonGuard.java, ExplosionWandMod.java, lang, jei — уже содержат изменения части A; при раздельных коммитах использовать `git add -p`.)

## Следующий шаг
Часть C: палочки 9–16, релиз 2.14.0 и `docs/history.md`.
