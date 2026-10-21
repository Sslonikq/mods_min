# 0103 · Релиз 2.16.0

- **Дата:** 2026-10-21
- **Агент:** asset-engineer
- **Версия мода:** 2.16.0
- **Статус:** done
- **Связанные отчёты:** 0099, 0100, 0101, 0102, 0086

## Задача
Выпустить 2.16.0 (фаза 4.8, Пепельное святилище): версия, запись в history.md, проверка ресурсов, сборка. Код механик не трогать.

## Что сделано
- `mods.toml`: version="2.16.0".
- `docs/history.md`: запись 2.16.0 (башня, колокол, 5 уровней, ключи Пепельный/Угольный, механики jets/crumble/geyser/braziers/bell, повторная выдача ключей, колодец-выход, барьер, хвосты 4.7, Boss3 nit-ы).
- Проверка ресурсов: ru_ru и en_us симметричны (различий ключей нет); все строковые ключи `explosionwand.*`/`item.explosionwand.*` из src есть в lang; у ash_key/ember_key есть item, .desc, jei, locked.ash_gate/ember_gate; есть bell, fires_wrong, fires_solved, key_reissued, ash.guard, dungeon.ash; все JSON в `resources/` парсятся (0 ошибок); модели ash_key/ember_key `item/generated` со своими текстурами; PNG ash_key/ember_key валидны, 16x16 RGBA; в `ExplosionWandJei` есть страницы ash_key и ember_key. Правок ресурсов не потребовалось.

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `resources/META-INF/mods.toml` | версия 2.16.0 |
| `docs/history.md` | запись 2.16.0 |

## Проверка
- `python tools/build.py`: 159 классов, SRG исходники 248 / байткод 249, неизвестных 0, пути классов ошибок 0, linkcheck расхождений 0, проблем ID 0, gen_dungeons 10 файлов проблем 0, тесты (BossCity, BossRift, CityMobs, City, Dungeon, Earth, Mats, Maze, RiftMobs, RiftStaff) OK; jar `dist/explosionwand-1.20.1-2.16.0.jar` (1672 КБ).
- PIL в окружении не установлен (ничего не ставил): PNG проверены по заголовку (сигнатура, 16x16, цвет-тип 6), визуального превью нет.

## Проверить в игре
- Списки из 0101 и 0102 (башня, колокол, шахта, переправа, жаровни, арена, ключи, Гнездо, колодец), вид текстур обоих ключей.

## Решения и почему
- Формулировку про барьер и заглушку Гнезда внёс в запись, чтобы не обещать босса до 4.10.

## Проблемы и открытые вопросы
- Из 0101/0102: политика барьера и выход из святилища после босса (4.10).

## Git (выполняет пользователь)
```
git add resources/META-INF/mods.toml docs/history.md reports/0103-2026-10-21-asset-engineer-release-2-16-0.md
git commit -m "docs(assets): релиз 2.16.0, Пепельное святилище" -m "- версия 2.16.0" -m "- запись в history.md: святилище, ключи, механики, хвосты 4.7, nit-ы Boss3" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```
Остальные файлы фазы 4.8 (src, resources, tools, tests, docs, отчёты 0099-0102) коммитятся отдельными коммитами по их отчётам.

## Следующий шаг
main: закрытие фазы 4.8, `CLAUDE.md`/ROADMAP; code-reviewer: ревью 4.8.
