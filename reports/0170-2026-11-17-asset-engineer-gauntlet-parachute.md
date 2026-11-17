# 0170 · Ресурсы: Перчатка Бесконечности и Парашют

- **Дата:** 2026-11-17
- **Агент:** asset-engineer
- **Версия мода:** не менялась
- **Статус:** done
- **Связанные отчёты:** 0169 (код, mod-engineer)

## Задача
Задача вне фазы: ресурсы предметов `infinity_gauntlet` и `parachute` (текстуры, модели, рецепт парашюта, lang ru/en).

## Что сделано
- Скрипт `tools/gen_gauntlet_parachute.py` (повторяемый, PNG без PIL через `png, put, A, D` из `gen_hell_assets.py`).
- Перчатка: тёмная сталь, золотые накладки, 3 самоцвета на костяшках (Разлом, Сон, Пепел) и 3 на тыльной стороне (Затмение, Небо, Глубины), вокруг тёмно-синего Затмения золотые искры.
- Парашют: красно-белый купол, белые стропы, коричневый рюкзачок с ремнём.
- Модели обоих: `minecraft:item/generated`.
- Рецепт `parachute.json`: WWW / S S / " L ", тег `minecraft:wool`, нить, кожа. У перчатки рецепта нет.
- Lang: все запрошенные ключи в ru_ru и en_us (по 17 штук).
- Превью x16: `build/infinity_gauntlet_x16.png`, `build/parachute_x16.png` (вспомогательный `build/zoom.py`).

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `tools/gen_gauntlet_parachute.py` | новый генератор |
| `resources/assets/explosionwand/textures/item/infinity_gauntlet.png`, `parachute.png` | новые 16x16 RGBA |
| `resources/assets/explosionwand/models/item/infinity_gauntlet.json`, `parachute.json` | новые |
| `resources/data/explosionwand/recipes/parachute.json` | новый рецепт |
| `resources/assets/explosionwand/lang/ru_ru.json`, `en_us.json` | новые ключи |

## Проверка
- Скрипт отработал, lang парсятся и перезаписаны с тем же форматом (indent 2, ensure_ascii=False).
- `python tools/ids.py`: без ошибок.
- Полный `python tools/build.py` не запускался (код mod-engineer ещё пишется).
- Текстуры просмотрены в x16: читаются.

## Проверить в игре
- Читаемость иконок в инвентаре, вид перчатки и парашюта в руке (generated, плоские).
- Рецепт парашюта в верстаке и JEI-страницы (ключи `explosionwand.jei.*` должен подхватить код).

## Решения и почему
- В рецепте парашюта `S S` / ` L ` — строго по заданию; в JEI-тексте рецепт описан словами.
- Текст JEI перчатки содержит все перезарядки списком в один абзац.

## Проблемы и открытые вопросы
- Регистрация предметов и использование ключей lang — на стороне mod-engineer (0169).

## Git (выполняет пользователь)
```
git add tools/gen_gauntlet_parachute.py resources/assets/explosionwand/textures/item/infinity_gauntlet.png resources/assets/explosionwand/textures/item/parachute.png resources/assets/explosionwand/models/item/infinity_gauntlet.json resources/assets/explosionwand/models/item/parachute.json resources/data/explosionwand/recipes/parachute.json resources/assets/explosionwand/lang/ru_ru.json resources/assets/explosionwand/lang/en_us.json reports/0170-2026-11-17-asset-engineer-gauntlet-parachute.md
git commit -m "assets(items): Перчатка Бесконечности и Парашют" -m "- текстуры, модели, рецепт парашюта" -m "- lang ru/en, генератор tools/gen_gauntlet_parachute.py"
```

## Следующий шаг
build-engineer: полная сборка после завершения кода 0169.
