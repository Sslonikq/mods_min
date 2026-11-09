# 0140 · Ресурсы блоков Ада (5.1)

- **Дата:** 2026-11-08
- **Агент:** asset-engineer
- **Версия мода:** 3.0.1 (не менялась)
- **Статус:** done
- **Связанные отчёты:** 0139

## Задача
Фаза 5.1, только ресурсы: таблица блоков Ада, текстуры, blockstates/модели/лут/рецепты/теги, lang ru+en.

## Что сделано
- `docs/hell_blocks.md`: таблица 34 блоков (ID, RU/EN, тип, свет, твёрдость, лут, поведение), рецепты, теги, нужные свойства блоков.
- `tools/gen_hell_blocks.py`: текстуры 16x16 RGBA (PIL в среде нет, как и в gen_hell_assets.py — чистый zlib), blockstates, модели, item-модели, лут, рецепты, теги, lang. Идемпотентен (теги и lang дополняются).
- Доски: 1 бревно -> 4 доски для обугленных и тлеющих; обе в `minecraft:planks`, палки обычные.
- Дополнительно: предмет `coal_berries` (текстура, модель, lang) — для лута куста.
- Превью текстур: `build/hell_blocks_preview.png`.

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `tools/gen_hell_blocks.py`, `docs/hell_blocks.md` | новые |
| `resources/assets/explosionwand/textures/block/*.png` (+ `item/coal_berries.png`) | текстуры блоков (в т.ч. `_top`, `eclipse_cluster_0..3`, `coal_berry_bush_0..3`, `geyser_top/side`) |
| `resources/assets/explosionwand/blockstates, models/block, models/item` | по каждому блоку |
| `resources/data/explosionwand/loot_tables/blocks/*.json`, `recipes/{charred,smoldering}_planks.json` | лут и рецепты |
| `resources/data/minecraft/tags/{blocks,items}/...` | дополнены/созданы теги |
| `resources/assets/explosionwand/lang/ru_ru.json, en_us.json` | 34 блока + `coal_berries`, только добавления |

## Проверка
- `python tools/build.py`: проходит, jar собран, тесты OK.
- `python tools/ids.py`: ok.
- Все JSON в resources парсятся; у каждого блока есть blockstate, item-модель, лут, строки в обоих lang; все ссылки моделей на текстуры и модели существуют.

## Проверить в игре
- Вид текстур и моделей (ковры, кресты, друза в 6 ориентациях, слои пепла), свечение, лут.

## Решения и почему
- Без Java: лут ссылается на `explosionwand:coal_berries`; пока предмет не зарегистрирован, лут-таблица куста не загрузится (регистрирует mod-engineer).
- Самоцвет короны падает сам (отдельного предмета-самоцвета нет). Лиана и гейзер упрощены: лианы — кресты (1 блок), не мультигрань.
- `gen_hell_assets.py` перезаписывает `pickaxe.json` без новых блоков; после его запуска перезапустить `gen_hell_blocks.py`.

## Проблемы и открытые вопросы
- В world.md было 32 блока, фактически 34.

## Git (выполняет пользователь)
```
git add tools/gen_hell_blocks.py docs/hell_blocks.md resources reports/0140-2026-11-08-asset-engineer-hell-blocks.md
git commit -m "assets(assets): блоки Ада - текстуры, модели, лут, теги, lang" -m "- 34 блока, доски 1 бревно -> 4" -m "- предмет углеягоды для куста"
```

## Следующий шаг
mod-engineer: регистрация блоков и `coal_berries`, поведение (см. docs/hell_blocks.md), затем измерение и деревья.
