# 0043 · Lang мобов Разлома, версия 2.6.0

- **Дата:** 2026-10-19
- **Агент:** asset-engineer
- **Версия мода:** 2.6.0
- **Статус:** done
- **Связанные отчёты:** 0042, 0041, 0039

## Задача
Фаза 4.3, шаг 3: ключи `explosionwand.mob.*` в оба lang, версия 2.6.0, раздел в `docs/history.md`, сверка `docs/dungeons.md` и отметка в `docs/world.md`.

## Что сделано
- В `ru_ru.json` и `en_us.json` добавлено по 9 ключей (shardling, shardling_mini, crystal_archer, rift_shade, prism, echo_eater, amethyst_golem, crystal_beetle, golem_open); наборы ключей совпадают (63 и 63), JSON валиден.
- Сверка с кодом: `RiftMobs.java:125` строит имя хоста как `explosionwand.mob.<k.id>`, id видов в `RiftModels.Kind` ровно те восемь; `golem_open` используется в `RiftMobs.java:919`. Других ключей `explosionwand.mob.*` в `src/` нет.
- `mods.toml`: было 2.5.0, стало 2.6.0.
- `docs/history.md`: раздел 2.6.0 (мобы и их поведение, Кристальный жук, замечания ревью 0039, сброс миров с Разломом из-за layout 2); в 2.5.0 строка «мобы Разлома - в 2.6.0» уточнена.
- `docs/dungeons.md`: раздел о подписи уже описывает номер версии `layout` (ручное повышение, `части:vN`), с кодом (`Dungeons.java:160`) согласован, правок не нужно.
- `docs/world.md`: у заголовка «Разлом» в бестиарии отметка «мобы построены в 4.3 (2.6.0)».
- `arcane_helmet.json` и `arcane_leggings.json` не тронуты.

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `resources/assets/explosionwand/lang/ru_ru.json` | +9 ключей `explosionwand.mob.*` |
| `resources/assets/explosionwand/lang/en_us.json` | +9 ключей |
| `resources/META-INF/mods.toml` | version 2.6.0 |
| `docs/history.md` | раздел 2.6.0 |
| `docs/world.md` | отметка у бестиария Разлома |
| `reports/0043-...md` | этот отчёт |

## Проверка
- `python tools/build.py`: 122 класса, SRG 216/217, неизвестных 0, проблем ID 0, gen_dungeons --check 0, DungeonTest/MazeTest/RiftMobsTest OK, jar `dist/explosionwand-1.20.1-2.6.0.jar`.
- Новых SRG и текстур нет (превью не требуется).

## Проверить в игре
- Над хостами мобов Разлома видны русские/английские имена (не ключи); actionbar «Голем раскрыт - бей!» при раскрытии голема.
- Старый мир 2.5.0: Разлом один раз сбрасывается и достраивается.

## Решения и почему
- В тексте `golem_open` оставлено длинное тире, как в задании для ru; в en - дефис.

## Проблемы и открытые вопросы
- Файлы lang в рабочей копии с LF (git предупреждает о CRLF) - как и раньше.

## Git (выполняет пользователь)
```
git add resources/assets/explosionwand/lang/ru_ru.json resources/assets/explosionwand/lang/en_us.json resources/META-INF/mods.toml docs/history.md docs/world.md reports/0043-2026-10-19-asset-engineer-rift-mobs-lang-version.md
git commit -m "feat(items): мобы Разлома: lang и версия 2.6.0" -m "- имена шести мобов, осколочка, жука и сообщение голема в ru/en" -m "- версия 2.6.0, раздел в history.md" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

## Следующий шаг
`code-reviewer`: ревью фазы 4.3; затем пробный проход в игре и баланс (`boss-engineer`).
