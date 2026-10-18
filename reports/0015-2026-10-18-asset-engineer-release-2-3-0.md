# 0015 · Релиз 2.3.0

- **Дата:** 2026-10-18
- **Агент:** asset-engineer
- **Версия мода:** 2.3.0
- **Статус:** done
- **Связанные отчёты:** 0006, 0007, 0008, 0009, 0010, 0011, 0012, 0013, 0014

## Задача
Финал фазы 2: поднять версию до 2.3.0, записать в историю, сверить docs/jei.md с итоговым состоянием.

## Что сделано
- mods.toml: `version="2.3.0"`.
- docs/history.md: запись 2.3.0 (ArenaGuard, сундуки с добычей, JEI и плагин, «Палочка», 5 рецептов, docs/jei.md).
- docs/jei.md сверен: плагин `ExplosionWandJei` регистрирует ровно 5 предметов (rift_core, eclipse_heart, dream_staff, dreaming_eye, whale_staff); в lang по 5 ключей `explosionwand.jei.*` в обоих файлах. Строку про плагин уточнил (имя класса, список, ключи).

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `resources/META-INF/mods.toml` | версия 2.3.0 |
| `docs/history.md` | запись 2.3.0 |
| `docs/jei.md` | уточнено описание плагина |

## Проверка
- `python tools/build.py`: 84 класса, SRG 185/186, неизвестных 0, проблем ID 0, MazeTest OK, jar `dist/explosionwand-1.20.1-2.3.0.jar`.
- Примечание: в моей оболочке `javap` не был в PATH; запускал с `jdk-21/bin` в PATH.

## Проверить в игре
- Мод стартует с JEI 15.x и без него; в JEI у пяти трофеев есть страница описания.

## Решения и почему
- В истории использовано название «Недремлющее Око» (как в ru_ru.json).

## Проблемы и открытые вопросы
- В `dist/` остался старый jar 2.2.0.

## Git (выполняет пользователь)
```
git add resources/META-INF/mods.toml docs/history.md docs/jei.md reports/0015-2026-10-18-asset-engineer-release-2-3-0.md
git commit -m "chore(build): версия 2.3.0" -m "- запись 2.3.0 в docs/history.md" -m "- docs/jei.md сверен с плагином"
```

## Следующий шаг
Фаза 3 по ROADMAP.md.
