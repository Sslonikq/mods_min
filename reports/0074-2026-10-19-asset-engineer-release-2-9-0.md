# 0074 · Релиз 2.9.0: lang мобов Города, история, версия

- **Дата:** 2026-10-19
- **Агент:** asset-engineer
- **Версия мода:** 2.9.0
- **Статус:** done
- **Связанные отчёты:** 0072, 0073

## Задача
Фаза 4.6, выпуск 2.9.0: lang-ключи мобов и сообщений Города (ru и en), версия, запись в историю, пометка в docs/world.md.

## Что сделано
- В оба lang добавлено по 17 ключей `explosionwand.mob.*`: имена lunatic, drowsy, stitch_guard, night_moth, dreamer, dream_clone, book_guard, statue_guard, cradler, fog_walker и сообщения dreamer_cast, cradler_chime, cradler_lantern(+.sub), fog_steal, fog_escaped, fog_returned. Тексты из отчёта 0073.
- Все `explosionwand.mob.*` из кода (City*.java, RiftMobs.java) присутствуют в обоих файлах; наборы ключей идентичны (114), JSON валиден.
- `mods.toml`: version 2.9.0.
- `docs/history.md`: запись 2.9.0.
- `docs/world.md`: помечено, что мобы Города реализованы. Упоминаний заглушек Города в JEI-плагине и lang нет (в коде остался только актуальный комментарий City.java).

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `resources/assets/explosionwand/lang/ru_ru.json`, `en_us.json` | +17 ключей |
| `resources/META-INF/mods.toml` | 2.9.0 |
| `docs/history.md` | запись 2.9.0 |
| `docs/world.md` | пометка о мобах Города |

## Проверка
- `python tools/build.py`: компиляция, SRG, ID, тесты (BossRiftTest, CityMobsTest, CityTest, DungeonTest, MazeTest, RiftMobsTest, RiftStaffTest) OK, jar `dist/explosionwand-1.20.1-2.9.0.jar`.

## Проверить в игре
- Имена мобов над хостами и сообщения (actionbar, заголовок Колыбельщика) на русском и английском.

## Решения и почему
- Ключи вставлены после `mob.golem_open`, рядом с мобами Разлома.

## Проблемы и открытые вопросы
- Тексты не менял относительно отчёта 0073; `cradler_lantern` без точки в конце, как заголовок.

## Git (выполняет пользователь)
```
git add resources/assets/explosionwand/lang/ru_ru.json resources/assets/explosionwand/lang/en_us.json resources/META-INF/mods.toml docs/history.md docs/world.md reports/0074-2026-10-19-asset-engineer-release-2-9-0.md
git commit -m "feat(items): релиз 2.9.0, lang мобов Города" -m "- имена и сообщения мобов Города (ru, en)" -m "- версия 2.9.0, запись в history.md, пометка в world.md" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

## Следующий шаг
`code-reviewer`: ревью частей A и B (0072, 0073); затем фаза 4.7.
