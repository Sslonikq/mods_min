# 0153 · Исправление формата карверов Ада (краш при создании мира)

- **Дата:** 2026-11-09
- **Агент:** main
- **Версия мода:** 3.3.0 (jar пересобран, версия та же: релиз не выходил)
- **Статус:** done
- **Связанные отчёты:** 0150 (карверы), 0152 (ревью пропустило)

## Задача
Игра падала при создании мира: `Failed to load registries`, `Not a JSON object: null` в `hell_cave.json` и `hell_canyon.json`.

## Причина
Карверы были написаны в формате, не совпадающем с 1.20.1. Там `uniform` для float и `trapezoid` хранят числа внутри `value`:
- `{"type":"minecraft:uniform","value":{"min_inclusive":a,"max_exclusive":b}}`
- `{"type":"minecraft:trapezoid","value":{"min":..,"max":..,"plateau":..}}`

Формат сверен с `data/minecraft/worldgen/configured_carver/cave.json` и `canyon.json` из ванильного jar.

## Что сделано
- `hell_cave.json`, `hell_canyon.json`: float `uniform` и `trapezoid` приведены к формату 1.20.1. Высотный `uniform` (`y`) остаётся с `min_inclusive`/`max_inclusive` у якорей, как в ваниле.
- `python tools/build.py`: проходит, jar собран. `check_worldgen.py`: ok.

## Проблемы и открытые вопросы
- `check_worldgen.py` проверяет только наличие карверов, ссылок и тега, но не форму полей, поэтому ошибку не поймал. Нужна проверка формата карверов против ванильных файлов (build-engineer, следующая фаза).
- Другие worldgen JSON 5.1 в игре уже загружались; карверы были единственным новым типом.

## Проверить в игре
- Создание нового мира без краша; в `latest.log` нет `Registry loading errors`.
- Пещеры и каньоны под каждым биомом Ада, лава ниже y=40.

## Git (выполняет пользователь)
```
git add resources/data/explosionwand/worldgen/configured_carver/hell_cave.json resources/data/explosionwand/worldgen/configured_carver/hell_canyon.json reports/0153-2026-11-09-main-carver-format-fix.md
git commit -m "fix(dims): формат карверов Ада для 1.20.1, краш при создании мира" -m "- uniform и trapezoid с value, как в ванильных карверах" -m "- причина: check_worldgen не проверяет форму полей"
```

## Следующий шаг
build-engineer: расширить `check_worldgen.py` проверкой формы карверов (в 5.3 первым шагом).
