# 0143 · Фичи Ада под canSurvive блоков, needs_diamond_tool

- **Дата:** 2026-11-08
- **Агент:** asset-engineer
- **Версия мода:** 3.1.0
- **Статус:** done
- **Связанные отчёты:** 0141, 0142

## Задача
Тег алмазной кирки для тёмного обсидиана; сверить фичи worldgen с `canSurvive` из `HellBlock.java`, `CoalBerryBushBlock`, `EclipseClusterBlock`.

## Что сделано
- `resources/data/minecraft/tags/blocks/needs_diamond_tool.json`: `explosionwand:dark_obsidian`.
- Сверка фич с Java: призрачный цветок (soul_soil, soul_sand, ghost_moss) совпадает; растения (dry_grass, ember_bloom, spark_fern, куст углеягод, слой пепла) стоят на `ash_soil` - полный куб, верхняя грань твёрдая; друзы - на `eclipse_stone`/`eclipse_cobblestone` facing=up; лианы ставит `attached_to_leaves` под листву (`smoldering_leaves` - обычный Block с полной коллизией, `bone_block` - куб), оба имеют твёрдую нижнюю грань.
- Расхождение найдено одно: огненный коралл ставился ещё и на пемзу, а Java допускает только `slag`, `magma_block` или лаву в радиусе 2. Пемза убрана из `hell_fire_coral` (в `tools/assets/gen_hell_world.py`, файлы перегенерированы).

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `resources/data/minecraft/tags/blocks/needs_diamond_tool.json` | новый |
| `tools/assets/gen_hell_world.py`, `worldgen/configured_feature/hell_fire_coral.json`, `worldgen/placed_feature/hell_fire_coral.json` | коралл без пемзы |

## Проверка
- `python tools/build.py`: проходит, jar 3.1.0, linkcheck 0 неизвестных.
- `python tools/check_worldgen.py`: 64 файла, ok.

## Проверить в игре
- Что лианы под тлеющей листвой и костяной кроной не исчезают после роста дерева (зависит от `m_60783_` вниз для этих блоков).
- Что кораллы появляются на островах (слой шлака/магмы на верхней грани).

## Git (выполняет пользователь)
```
git add resources/data/minecraft/tags/blocks/needs_diamond_tool.json tools/assets/gen_hell_world.py resources/data/explosionwand/worldgen/configured_feature/hell_fire_coral.json resources/data/explosionwand/worldgen/placed_feature/hell_fire_coral.json reports/0143-2026-11-08-asset-engineer-hell-survive.md
git commit -m "fix(dims): коралл Ада без пемзы, needs_diamond_tool" -m "- тег алмазной кирки для тёмного обсидиана" -m "- опора фич сверена с canSurvive"
```

## Следующий шаг
Пользователь проверяет в игре.
