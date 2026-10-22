# 0133 · Текстуры Ада: Печатный камень, Угли, гнездо, проём, Ключ

- **Дата:** 2026-10-22
- **Агент:** asset-engineer
- **Версия мода:** 2.19.2 (не поднималась)
- **Статус:** done
- **Связанные отчёты:** 0131

## Что сделано
Расширен `tools/gen_hell_assets.py` (чистый zlib, PIL не ставился), заготовки заменены:
- `seal_stone` - старые каменные кирпичи с бликами/тенью граней, в центре круглая печать с рунным крестом.
- `phoenix_embers` - тёмная зола с раскалёнными трещинами, серые крапинки.
- `hell_socket` - те же угли + круглая золотая печать-замок с замочной скважиной.
- `hell_portal` - полоса 16x128 (8 кадров, бесшовный цикл), красно-оранжевые завихрения, альфа 150-230; `hell_portal.png.mcmeta`: frametime 3, interpolate.
- `hell_key` - ключ по диагонали: круглая печать-головка сверху-справа (золото, красный камень), бородка снизу-слева, тёмный контур.
Превью: `build/preview/hell_textures.png` (камень, угли, гнездо, кадры 1 и 4 проёма, ключ; x10).

## Проверка путей
- blockstates/модели блоков (`cube_all`, портал `render_type translucent`) ссылаются на `explosionwand:block/<имя>`, файлы есть; предметные модели `seal_stone`, `phoenix_embers` наследуют блок, `hell_key` - `item/generated` с `item/hell_key`. Для `hell_socket`/`hell_portal` предмета нет - модель не нужна.
- Все JSON/mcmeta в assets валидны; lang-ключи block/item для 5 позиций есть в en (lang и Java не менял).
- `python tools/build.py`: OK, все тесты OK, jar 2.19.2.
- Побочно: запуск генератора перезаписывает блок-JSON, рецепты, лут, измерение/биом `hell` теми же значениями (по генератору).

## Проверить в игре
- Рамка из Печатного камня и гнездо без фиолетово-чёрных текстур, в инвентаре предметы-блоки целы.
- Проём портала переливается (анимация плавная, не слишком ярка), полупрозрачный.
- Занятое гнездо отличается от пустых Углей (золотая печать).
- Иконка Ключа читается в хотбаре; оценить стыки кирпичей камня на стене 5x5 (бесшовность).

## Файлы
tools/gen_hell_assets.py; resources/assets/explosionwand/textures/block/{seal_stone,phoenix_embers,hell_socket,hell_portal}.png, hell_portal.png.mcmeta; textures/item/hell_key.png; reports/0133-...md

## Git (не выполнялось)
```
git add tools/gen_hell_assets.py resources/assets/explosionwand/textures/block resources/assets/explosionwand/textures/item/hell_key.png reports/0133-2026-10-22-asset-engineer-hell-textures.md
git commit -m "assets(assets): текстуры Печатного камня, Углей, гнезда, портала, Ключа Ада" -m "- анимированный проём портала (8 кадров, mcmeta)" -m "- генератор на zlib расширен, превью в build/preview"
```
Если эти текстуры ещё не закоммичены вместе с 0131, пути blockstates/models подтянет коммит 0131.
