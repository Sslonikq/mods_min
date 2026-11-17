# 0167 · Уборка: старые исходники Gradle и неиспользуемые заглушки

- **Дата:** 2026-11-17
- **Агент:** main
- **Версия мода:** 3.4.1 (не менялась: содержимое jar то же, заглушки в jar не попадают)
- **Статус:** done
- **Связанные отчёты:** 0166

## Задача
Пользователь: «Лишнее убирай. Тут только ты главный» — после осмотра проекта убрать мёртвые файлы.

## Что сделано
- Удалена папка `legacy/gradle-src/` (`Config.java`, `ExplosionWandItem.java`, `ExplosionWandMod.java`): не собиралась, ни на что не влияла. История остаётся в git.
- Удалены заглушки `stubs/net/minecraft/world/item/CreativeModeTabs.java` и `stubs/net/minecraftforge/event/BuildCreativeModeTabContentsEvent.java`: их использовал только `legacy/`.
- Из `CLAUDE.md` (раздел «Структура») убрана строка про `legacy/`.
- Вне git: из `dist/` удалены старые jar 3.3.0 и 3.4.0, удалена папка `tools/__pycache__`.

Проверено и оставлено, потому что используется:
- `Haunt.java`: фигуру и `groundY` вызывают `VoidWorld` и `Mimic`.
- `dimension/haunt.json`: старые измерения сохраняются, чтобы открывались старые сохранения (так же, как `arena` и `dream`).
- Все картинки `docs/concepts/` упоминаются в документах.
- Все генераторы `tools/gen_*.py` нужны: они источник ассетов.
- Все модели предметов зарегистрированы.
- В `ru_ru` и `en_us` одинаково по 487 строк.
- Единственный класс без ссылок, `jei/ExplosionWandJei`, грузится через аннотацию JEI.

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `legacy/gradle-src/*.java` (3) | удалены |
| `stubs/net/minecraft/world/item/CreativeModeTabs.java` | удалён |
| `stubs/net/minecraftforge/event/BuildCreativeModeTabContentsEvent.java` | удалён |
| `CLAUDE.md` | убрана строка про `legacy/` |

## Проверка
- `python tools/build.py`: 215 классов, SRG 350/351, неизвестных 0, проблем ID 0, тесты OK, jar `dist/explosionwand-1.20.1-3.4.1.jar`.
- Новых SRG-имён нет.

## Проверить в игре
- Ничего: код мода не менялся.

## Решения и почему
- Версия не поднята: в jar ничего не изменилось.
- `haunt.json` не удалён: без него мир, в котором кто-то стоял в этом измерении, откроется с ошибкой.

## Проблемы и открытые вопросы
- В игре (`mods/`) лежит `explosionwand-1.20.1-3.5.0.jar` от 2026-11-16, собранный не из этого репозитория (здесь 3.4.1, коммитов после 2026-11-09 нет). Папку игры не трогали: правило проекта разрешает её только читать.

## Git (выполняет пользователь)
```
git rm -r legacy stubs/net/minecraft/world/item/CreativeModeTabs.java stubs/net/minecraftforge/event/BuildCreativeModeTabContentsEvent.java
git add CLAUDE.md reports/0167-2026-11-17-main-cleanup-legacy.md
git commit -m "chore(build): убраны старые исходники Gradle и лишние заглушки" -m "- legacy/gradle-src не собирался, история есть в git" -m "- заглушки CreativeModeTabs и BuildCreativeModeTabContentsEvent нужны были только ему"
```

## Следующий шаг
Фаза 5.4 «Цитадель: мобы»: `/clear`, затем `/next-phase`. Выяснить происхождение jar 3.5.0 в игре.
