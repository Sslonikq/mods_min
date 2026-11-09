# 0158 · Проверка формата configured_carver в check_worldgen.py

- **Дата:** 2026-11-09
- **Агент:** build-engineer
- **Версия мода:** 3.3.0 (без изменений)
- **Статус:** done
- **Связанные отчёты:** 0153 (краш из-за формата карверов)

## Задача
Чтобы `check_worldgen.py` ловил формат карверов 1.20.1, а не только наличие файлов.

## Что сделано
`tools/check_worldgen.py`:
- Ванильного jar в `.cache`/Forge нет, формат описан вручную в коде (по cave.json/canyon.json 1.20.1).
- Float-провайдеры: число | `constant{value}` | `uniform{value:{min_inclusive,max_exclusive}}` | `trapezoid{value:{min,max,plateau}}`. Проверяются точный набор ключей, числа, min<max, plateau<=max-min, лишние поля на уровне провайдера.
- Высоты: якорь = ровно один из `absolute/above_bottom/below_top` (int); height uniform = `min_inclusive`+`max_inclusive` якорями, min<=max.
- Обязательные поля: cave - probability, y, yScale, lava_level, replaceable, horizontal/vertical_radius_multiplier, floor_level; canyon - те же общие + vertical_rotation, shape{distance_factor, thickness, width_smoothness(int), horizontal_radius_factor, vertical_radius_default_factor, vertical_radius_center_factor}. Лишние поля - ошибка (кроме debug_settings). Корень: только type+config.
- Добавлена переменная `EW_WORLDGEN_DATA` (корень данных вместо resources) для самопроверки на копиях в `build/`.

## Проверка
- Реальные `hell_cave.json`/`hell_canyon.json`: ok.
- Плохая копия в `build/carver_bad` (удалена): старая ошибка (`uniform`/`trapezoid` без `value`) + удалённый `lava_level` пойманы, rc=1 (4 сообщения).
- `python tools/build.py`: проходит, jar `dist/explosionwand-1.20.1-3.3.0.jar`. Один ранний прогон упал на DungeonTest (NoClassDefFoundError DungeonData) из-за параллельной чужой пересборки; повтор чистый.

## Проверить в игре
Ничего нового; формат карверов - см. 0153.

## Git (выполняет пользователь)
```
git add tools/check_worldgen.py reports/0158-2026-11-09-build-engineer-carver-format-check.md
git commit -m "build(build): check_worldgen проверяет формат configured_carver" -m "- float uniform/trapezoid с value, якоря высоты, обязательные поля cave/canyon" -m "- EW_WORLDGEN_DATA для самопроверки на плохих копиях" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

## Следующий шаг
Нет.
