# 0141 · Датапак Мира Ада: бесконечное измерение, 5 биомов, фичи

- **Дата:** 2026-11-08
- **Агент:** asset-engineer
- **Версия мода:** 3.1.0
- **Статус:** done
- **Связанные отчёты:** 0140 (блоки Ада), 0139 (остатки ревью 0138)

## Задача
Фаза 5.1, часть датапака: заменить Ад-заглушку (`minecraft:flat`) бесконечным измерением без потолка (noise + multi_noise, свои noise_settings), 5 биомов, фичи, тип измерения, версия 3.1.0.

## Что сделано
- Генератор `tools/assets/gen_hell_world.py` пишет весь датапак (JSON правятся там и перегенерируются).
- **Рельеф** (собственные density functions `explosionwand:hell/*`): `continents`, `erosion`, `humidity`, `ridges` (flat_cache, растянутые ванильные шумы), `land` (0 в море, 1 на суше, берег около continents -0.35), `peaks`, `hilliness`, `surface_height`, `slope`, `final`. Высота поверхности: море ~20 (лава на 40), суша ~56, холмы до +-18, горы до +105 (ridge-гребни), острова в море до ~50, в долине (высокая эрозия) плоско. Пещеры: `cave_cheese`, окно y>=28..40, ниже 40 затопляются лавой (aquifers выключены, default_fluid = лава). Над y=200 плотность гасится. Потолка и верхнего бедрока нет.
- **noise_settings/hell.json**: min_y 0, height 256, sea_level 40, default_block `eclipse_stone`, default_fluid лава, бедрок только на 0-4. Поверхность по биомам: пустоши и лес - `ash_soil` (слой по глубине поверхности); пики - камень с пятнами `blazing_stone` и `eclipse_cobblestone`; море - `slag` с пятнами `pumice` и `magma_block`; долина - `soul_sand` пятнами по `soul_sand_layer`, иначе `soul_soil`.
- **Биомы** по (continentalness, erosion, humidity), без пересечений: море C<-0.35; на суше пики E<-0.3; долина E>0.35; между ними пустоши (humidity<0.05) и лес (>0.05). Параметры подобраны так, что доля каждого биома 15-30%, пятна около 700-1500 блоков.
- **dimension/hell.json**: `minecraft:noise`, `multi_noise` с 5 биомами. **dimension_type/hell.json**: has_ceiling=false, has_skylight=true, ultrawarm=true, `fixed_time` 13000, ambient_light 0.1, min_y 0 / height 256 / logical_height 256, natural=false, bed_works=false, infiniburn nether, effects `minecraft:overworld`.
  - Почему `overworld`, а не `the_nether`: у `the_nether` тип неба NONE (неба нет, один туман). У `overworld` небо рисуется из `sky_color` биома. При 13000 солнце на 3.5 градуса ниже горизонта: небо = `sky_color` * ~0.38 (тёмно-красное) плюс закатная полоса на горизонте; постоянно, без смены дня и ночи.
- **Биомы** `hell_ash_wastes`, `hell_smoldering_forest`, `hell_basalt_peaks`, `hell_lava_sea`, `hell_soul_valley`: fog (красно-бурый 8A4A2E / янтарный A3501F / красно-фиолетовый 6A3050 / оранжевый D5601A / бирюзовый 3FB0A8), sky_color, water, частицы (ash, flame, white_ash, lava, soul), музыка и настроение Незера, без осадков, без спавнеров. Старый биом `hell` удалён (в `src/` и `tests/` на `explosionwand:hell` как биом никто не ссылается; `Dims.HELL` - ключ измерения).
- **Фичи (placed + configured, по 23)**: деревья `minecraft:tree` (обугленное с forking-стволом, тлеющее с огненными лианами через `attached_to_leaves`, костяное с призрачными лозами), угольный гриб (`huge_brown_mushroom`), слой пепла, мох углей, сухая трава, огнецвет, искровик, углеягоды, призрачные мох и цветок, огонь душ, гейзеры и огненные кораллы на шлаке/пемзе, кости великанов и колонны из камня Архимагов, друзы затмения (поверхностные кластеры + `geode` внутри гор), шпили (пучок колонн тёмного обсидиана биазированной высоты, сужается кверху, жилы резных кирпичей, кристалл на макушке), руды (кровавое железо везде, самоцвет короны редко), лавопады (`spring_feature`). Порядок фич единый для всех биомов (без циклов).
- Острова шлака/пемзы в море даёт сам рельеф + поверхностное правило, отдельной фичи нет.
- `tools/gen_hell_assets.py`: убрана запись dimension/dimension_type/biome Ада (иначе затёрла бы новые файлы).
- Версия 3.1.0 в `mods.toml`, запись в `docs/history.md`.

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `resources/data/explosionwand/dimension/hell.json` | noise + multi_noise |
| `resources/data/explosionwand/dimension_type/hell.json` | открытое небо, overworld-effects, fixed_time 13000 |
| `resources/data/explosionwand/worldgen/noise_settings/hell.json` | новое |
| `resources/data/explosionwand/worldgen/density_function/hell/*.json` | 10 новых |
| `resources/data/explosionwand/worldgen/biome/hell_*.json` | 5 новых; старый `hell.json` удалён |
| `resources/data/explosionwand/worldgen/configured_feature/hell_*.json`, `placed_feature/hell_*.json` | по 23 новых |
| `tools/assets/gen_hell_world.py`, `tools/check_worldgen.py` | генератор и проверка |
| `tools/gen_hell_assets.py` | убран блок измерения Ада |
| `resources/META-INF/mods.toml`, `docs/history.md` | 3.1.0 |

## Проверка
- `python tools/build.py`: проходит, jar `dist/explosionwand-1.20.1-3.1.0.jar`, тесты OK.
- `python tools/ids.py`: ok.
- `python tools/check_worldgen.py`: 64 файла Ада, JSON валидны; типы density/feature/placement/trunk/foliage/decorator/provider/predicate/rule_test, шумы, звуки, частицы, ванильные блоки сверены с реестром 1.20.1; блоки `explosionwand:` - с таблицей `docs/hell_blocks.md`; ссылки на файлы фич, биомов, density functions и noise_settings существуют; у noise_router все 15 полей.
- Новых SRG-имён нет.

## Проверить в игре
- Датапак загружается без ошибок в логе (особенно: `geode`, `huge_brown_mushroom`, `attached_to_leaves`, поля `crack`/`min_gen_offset` геода, `forking_trunk_placer`). Это зависит от того, что mod-engineer зарегистрировал все 34 блока с теми же ID; иначе измерение не загрузится.
- Свойства блоков в фичах: `age`/`facing` у `eclipse_cluster`, `layers` у `ash_layer`, `age` у `coal_berry_bush` - должны совпадать с Java.
- Шум нельзя проверить без игры: реально ли в 2000 блоках от портала встречаются все 5 биомов (по оценке - да, доли 15-30% при пятнах 700-1500, но точные границы по `continentalness`/`erosion`/`humidity` зависят от шума; при перекосе правятся числа в `BIOMES` и `build_density`), высоты и шкала гор (до y~170), доля лавовых озёр на суше, частота пещер и их затопление ниже y=40, вид неба при 13000 (темнота, закатная полоса), читаемость тумана.
- Шпили (форма и частота), гейзеры/кораллы на островах, плотность деревьев и растений, отсутствие плавающих блоков из 3D-шума на пиках.
- Тлеющая листва: в фиче без свойств (дефолтное состояние); если листва распадается, поставить `persistent` в Java.

## Решения и почему
- Биомы управляются router-значениями (continents/erosion/humidity), которые же формируют рельеф: границы биомов совпадают с формой земли (море = впадина, пики = горы, долина = плоско).
- Шпиль - пучок колонн разной высоты, а не точная башня со ступенями: в 1.20.1 нет способа связать несколько фич в одной точке.
- `ultrawarm` и лава: вода не ставится; жидкости в пещерах ниже y=40 - лава.

## Проблемы и открытые вопросы
- Не проверено запуском игры (Gradle и Forge недоступны). Параметры шума подобраны расчётом, а не наблюдением.
- Если фича с геодом отвергнется загрузчиком, удалить `hell_eclipse_geode` из `hell_basalt_peaks.json` (кластеры на поверхности остаются).

## Git (выполняет пользователь)
```
git add resources/data/explosionwand/dimension/hell.json resources/data/explosionwand/dimension_type/hell.json resources/data/explosionwand/worldgen resources/META-INF/mods.toml docs/history.md tools/assets/gen_hell_world.py tools/check_worldgen.py tools/gen_hell_assets.py reports/0141-2026-11-08-asset-engineer-hell-world.md
git commit -m "feat(dims): бесконечный Ад без потолка, 5 биомов, 3.1.0" -m "- noise_settings, density functions, multi_noise" -m "- биомы, деревья, растения, руды, друзы, шпили" -m "- версия 3.1.0, history"
```
(удаление старого биома: `git add -A resources/data/explosionwand/worldgen/biome` захватит и его)

## Следующий шаг
code-reviewer: сверить ID и свойства блоков в фичах с Java mod-engineer. Пользователь запускает игру и проверяет карту биомов.
