# 0150 · Руды Ада и пещеры (ресурсы)

- **Дата:** 2026-11-09
- **Агент:** asset-engineer
- **Версия мода:** 3.3.0
- **Статус:** done (ресурсная часть; предметы в коде регистрирует mod-engineer)
- **Связанные отчёты:** 0147 (ревью, nit 3 и 4), 0140, 0141

## Задача
Фаза 5.2: закрыть nit-ы 0147; сделать настоящие пещеры Ада (свои карверы); руды Ада дают собственные предметы, инструменты из кровавого железа, рецепты, lang, версия 3.3.0.

## Что сделано
- Nit 3: `gen_hell_blocks.py` в ветке `bush` ставит `item_model = None` и не пишет `models/item/coal_berry_bush.json` (модели в `resources` нет).
- Nit 4: удалены `loot_tables/blocks/hell_socket.json` и строки в `gen_hell_assets.py`.
- Карверы `hell_cave` (вероятность 0.2, y 8..160) и `hell_canyon` (0.02, y 20..100) по формату ванильных 1.20.1; `lava_level` absolute 40 (уровень лавы Ада). Тег `explosionwand:hell_carver_replaceables`. Во всех 5 биомах и в копии `biome/hell.json` `carvers.air` = оба карвера; `gen_hell_world.py` тоже обновлён, чтобы перегенерация не вернула `{}`.
- Лут: blood_iron_ore -> `raw_blood_iron` 1-2 (удача), crown_gem_ore -> `crown_gem` (удача), eclipse_cluster: шёлк -> блок, age=3 -> `eclipse_shard` 2-4 (удача), молодая -> сама друза. Блок crown_gem_ore в lang переименован в «Руда самоцвета короны» (ID не менялся).
- Предметы `raw_blood_iron`, `blood_iron_ingot`, `crown_gem`, `eclipse_shard` и инструменты `blood_iron_pickaxe/axe/shovel/hoe/sword`: текстуры 16x16 RGBA, модели (инструменты handheld, остальное generated).
- Рецепты: smelting и blasting сырого железа, 5 инструментов, `eclipse_crystal_block` из 4 осколков (2x2). ID блока подтверждён: `eclipse_crystal_block`.
- Теги mineable/needs_*_tool для блоков Ада уже были в 5.1 (blood_iron_ore - камень, crown_gem_ore - железо), менять не потребовалось.
- `check_worldgen.py`: проверяет configured_carver, ссылки биомов на них, тег и тип карвера.
- Версия 3.3.0 в `mods.toml`, запись в `docs/history.md`.

## Ключи lang для mod-engineer (JEI и др.)
Для каждого из 9 предметов (`raw_blood_iron`, `blood_iron_ingot`, `crown_gem`, `eclipse_shard`, `blood_iron_pickaxe|axe|shovel|hoe|sword`) добавлены `item.explosionwand.<id>`, `.desc` и `explosionwand.jei.<id>` (ru и en). Дополнительно `explosionwand.jei.eclipse_crystal_block` (если JEI-страницы для блоков не предусмотрены, ключ можно игнорировать).

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `tools/gen_hell_ores.py` (новый) | текстуры, модели, рецепты, карверы, биомы, lang; превью `build/ore_preview.png` |
| `tools/gen_hell_blocks.py` | нет item-модели у куста; новый лут руд и друзы; имя руды самоцвета |
| `tools/gen_hell_assets.py`, `tools/assets/gen_hell_world.py`, `tools/check_worldgen.py` | см. выше |
| `resources/data/explosionwand/worldgen/configured_carver/*.json`, `tags/blocks/hell_carver_replaceables.json`, `worldgen/biome/hell*.json` | пещеры |
| `resources/data/explosionwand/loot_tables/blocks/{blood_iron_ore,crown_gem_ore,eclipse_cluster}.json` | новая добыча |
| `resources/data/explosionwand/recipes/*` (8 файлов), `models/item/*` (9), `textures/item/*` (9), `lang/*.json` | предметы |
| `resources/META-INF/mods.toml`, `docs/history.md` | 3.3.0 |

## Проверка
- `python tools/build.py`: SRG неизвестных 0, проблем ID 0, тесты OK, jar `explosionwand-1.20.1-3.3.0.jar`. `check_worldgen.py`: ok (68 файлов). Все JSON валидны.
- Превью текстур: `build/ore_preview.png` (по порядку: сырое железо, слиток, самоцвет, осколок, кирка, топор, лопата, мотыга, меч).
- Предметы в Java ещё не зарегистрированы (зона mod-engineer), поэтому проверки «модель/текстура/lang для зарегистрированных» для них пока не выполнялись: файлы готовы.

## Проверить в игре
- Пещеры под каждым из 5 биомов (пустоши, лес, пики, лавовое море, долина душ): есть ли проходимые пещеры и каньоны, не вскрывают ли они поверхность слишком сильно, ниже y=40 в них лава.
- Карвер не должен пробивать данжи (карверы идут до структур; уточнить по Цитадели) и бедрок.
- Руда кровавого железа: сырое железо 1-2, удача, шёлк; плавка в печи и плавильне.
- Руда самоцвета: нужна железная кирка, выпадает crown_gem; друза: молодая даёт себя, взрослая 2-4 осколка.
- Инструменты: вид в руке (handheld), крафт; прочность и скорость зависят от кода (не мои).

## Решения и почему
- Карверы с `lava_level` 40, а не `above_bottom 8`, потому что море лавы Ада стоит на y=40 (`sea_level`, aquifers выключены).
- В тег замещаемых добавлены ванильные базальт, чёрный камень, незерак, гравий: блоки рельефа могут встретиться там через фичи.
- Ключ ядра Ада/стержней (рецепты палочек Ада) не трогал: вне задачи этой фазы по ресурсам.

## Следующий шаг
mod-engineer: зарегистрировать 9 предметов, тир инструментов, ремонт слитком, вкладку «Магия», JEI-страницы.

## Git
```
git add tools/gen_hell_ores.py tools/gen_hell_blocks.py tools/gen_hell_assets.py tools/assets/gen_hell_world.py tools/check_worldgen.py resources/data/explosionwand/worldgen/configured_carver/hell_cave.json resources/data/explosionwand/worldgen/configured_carver/hell_canyon.json resources/data/explosionwand/tags/blocks/hell_carver_replaceables.json resources/data/explosionwand/worldgen/biome/hell.json resources/data/explosionwand/worldgen/biome/hell_ash_wastes.json resources/data/explosionwand/worldgen/biome/hell_basalt_peaks.json resources/data/explosionwand/worldgen/biome/hell_lava_sea.json resources/data/explosionwand/worldgen/biome/hell_smoldering_forest.json resources/data/explosionwand/worldgen/biome/hell_soul_valley.json resources/data/explosionwand/loot_tables/blocks/blood_iron_ore.json resources/data/explosionwand/loot_tables/blocks/crown_gem_ore.json resources/data/explosionwand/loot_tables/blocks/eclipse_cluster.json resources/assets/explosionwand/lang/ru_ru.json resources/assets/explosionwand/lang/en_us.json resources/META-INF/mods.toml docs/history.md reports/0150-2026-11-09-asset-engineer-hell-ores-caves.md
git add resources/assets/explosionwand/models/item/raw_blood_iron.json resources/assets/explosionwand/models/item/blood_iron_ingot.json resources/assets/explosionwand/models/item/crown_gem.json resources/assets/explosionwand/models/item/eclipse_shard.json resources/assets/explosionwand/models/item/blood_iron_pickaxe.json resources/assets/explosionwand/models/item/blood_iron_axe.json resources/assets/explosionwand/models/item/blood_iron_shovel.json resources/assets/explosionwand/models/item/blood_iron_hoe.json resources/assets/explosionwand/models/item/blood_iron_sword.json resources/assets/explosionwand/textures/item/raw_blood_iron.png resources/assets/explosionwand/textures/item/blood_iron_ingot.png resources/assets/explosionwand/textures/item/crown_gem.png resources/assets/explosionwand/textures/item/eclipse_shard.png resources/assets/explosionwand/textures/item/blood_iron_pickaxe.png resources/assets/explosionwand/textures/item/blood_iron_axe.png resources/assets/explosionwand/textures/item/blood_iron_shovel.png resources/assets/explosionwand/textures/item/blood_iron_hoe.png resources/assets/explosionwand/textures/item/blood_iron_sword.png resources/data/explosionwand/recipes/blood_iron_ingot_smelting.json resources/data/explosionwand/recipes/blood_iron_ingot_blasting.json resources/data/explosionwand/recipes/blood_iron_pickaxe.json resources/data/explosionwand/recipes/blood_iron_axe.json resources/data/explosionwand/recipes/blood_iron_shovel.json resources/data/explosionwand/recipes/blood_iron_hoe.json resources/data/explosionwand/recipes/blood_iron_sword.json resources/data/explosionwand/recipes/eclipse_crystal_block.json
git commit -m "feat(dims): пещеры Ада и руды с предметами, 3.3.0" -m "- карверы hell_cave и hell_canyon во всех биомах, лава ниже y=40" -m "- сырое кровавое железо, слиток, самоцвет короны, осколок затмения" -m "- инструменты из кровавого железа, рецепты и lang" -m "- ревью 0147: без модели предмета куста, без лишней таблицы гнезда"
```
(Удалённый untracked `hell_socket.json` в git добавлять не нужно.)
