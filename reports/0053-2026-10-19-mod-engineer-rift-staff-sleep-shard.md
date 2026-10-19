# 0053 · Посох Разлома и Осколок Печати: Сон (предметы фазы 4.4)

- **Дата:** 2026-10-19
- **Агент:** mod-engineer
- **Версия мода:** 2.6.2 (не менялась; 2.7.0 ставит asset-engineer)
- **Статус:** done
- **Связанные отчёты:** 0051, 0052

## Задача
Часть 4.4 (предметы): новый Посох Разлома, Осколок Печати: Сон (заглушка до Города Спящих), убрать рецепт Ключа Разлома. Босса и логово делает boss-engineer.

## Что сделано
- `rift_staff` (посох 1-го уровня, EPIC): ПКМ — трещина по линии прицела до 14 блоков (Aim.of, режет по блоку/мобу); живые не-игроки в 2.6 блока от линии, `Minions.controllable`, затягиваются к ней и получают 6 урона (магия); кулдаун 100 тиков. Shift+ПКМ — шаг на 8 блоков вперёд (по горизонту с наклоном взгляда): ищется самая дальняя точка 8..2 блока, где ноги и голова в воздухе и чанк загружен; нет места — сообщение в экшнбар, кулдауна нет; успех — кулдаун 80, fallDistance сбрасывается. Кулдаун — встроенный `m_41524_`, умножается на `ArcaneSet.cd` (сет брони). Эффекты через Fx.
- `RiftStaffItem` добавлен в `DungeonGuard.forbidden` — в данжах посох молчит (то же сообщение `no_wand`).
- `sleep_seal_shard`: `SealShardItem` получил второй конструктор (тип данжа + id для описания). Тип «sleep» не найден в `Dungeons.def` -> `locked()`: в руке раз в 2 сек экшнбар `explosionwand.dungeon.locked.sleep`, ПКМ — то же; указателя/частиц нет. Когда появится `Def` «sleep», осколок заработает сам, без правок. Не крафтится (рецепта нет).
- Рецепт `rift_key.json` удалён. `RiftKeyItem`, его модель/текстура/lang, алтарь Сердца, `RiftCoreItem` — не тронуты.
- JEI: страницы `rift_staff` и `sleep_seal_shard` (оба с Сердца Разлома).
- Ресурсы: текстуры 16x16 (PIL в среде не установлен, не ставил — PNG записаны чистым Python zlib), модели item, ru/en lang (названия, описания, `dungeon.sleep`, `dungeon.locked.sleep`, JEI).

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `src/com/explosionwand/RiftStaff.java`, `RiftStaffItem.java` | новые: логика и предмет посоха |
| `src/com/explosionwand/SealShardItem.java` | режим «дорога не открыта», конструктор с ключом описания |
| `src/com/explosionwand/ExplosionWandMod.java` | регистрация `RIFT_STAFF`, `SLEEP_SEAL_SHARD` |
| `src/com/explosionwand/DungeonGuard.java` | `RiftStaffItem` в forbidden |
| `src/com/explosionwand/jei/ExplosionWandJei.java` | две страницы |
| `resources/assets/explosionwand/textures/item/rift_staff.png`, `sleep_seal_shard.png` | новые текстуры |
| `resources/assets/explosionwand/models/item/rift_staff.json`, `sleep_seal_shard.json` | новые модели |
| `resources/assets/explosionwand/lang/ru_ru.json`, `en_us.json` | новые ключи |
| `resources/data/explosionwand/recipes/rift_key.json` | удалён |

## Проверка
- `python tools/build.py`: 124 класса, SRG 216/217 (новых неизвестных 0), проблем ID 0, DungeonTest/MazeTest/RiftMobsTest OK, jar 2.6.2.
- Новые SRG-имена не потребовались (все уже были в заглушках); перепроверены `m_141937_` = getBottomY, `m_151558_` = getTopY, `m_46805_` = isChunkLoaded, `m_6021_` = requestTeleport, `m_269425_` = magic.

## Проверить в игре
- ПКМ посохом: фиолетовая линия до цели, мобы на ней подлетают к ней и получают ~3 сердца; игроки, хосты боссов и существа ew* не затрагиваются.
- Shift+ПКМ: шаг на 8 блоков вперёд; у стены — на меньшее расстояние; в упор в стену — красное сообщение; шаг через тонкую стену допустим (намеренно). Падение после шага не бьёт.
- В данже оба режима блокируются сообщением «В данже эта палочка молчит».
- Осколок Печати: Сон: в руке экшнбар «дорога ещё не открыта», без падений; в творческом Shift+ПКМ тоже без ошибок.
- Вкладка «Магия»: посох и осколок есть; иконки читаемы; рецепта Ключа Разлома нет в рецептах.
- Кулдаун виден на предмете (серая полоска).

## Решения и почему
- id осколка взят как в задании (`sleep_seal_shard`), хотя у Разлома `seal_shard_rift`; описание ищется по id через новый параметр.
- Игроков трещина не затягивает (против гриферства в PvP).
- Шаг допускает проход сквозь блоки (имя «сквозь разлом»), но финальные клетки обязаны быть свободны.

## Проблемы и открытые вопросы
- JEI-текст `rift_core` по-прежнему упоминает Ключ Разлома — переписать при переносе босса.
- Посох в добычу Сердца ещё не положен (это Boss/Loot, зона boss-engineer): сейчас получить только командой/творческим.
- Названия режимов в экшнбаре `RiftStaff.step` — захардкожены по-русски (как в EclipseStaff); при желании перенести в lang.

## Git (выполняет пользователь)
```
git add src/com/explosionwand/RiftStaff.java src/com/explosionwand/RiftStaffItem.java src/com/explosionwand/SealShardItem.java src/com/explosionwand/ExplosionWandMod.java src/com/explosionwand/DungeonGuard.java src/com/explosionwand/jei/ExplosionWandJei.java resources/assets/explosionwand/textures/item/rift_staff.png resources/assets/explosionwand/textures/item/sleep_seal_shard.png resources/assets/explosionwand/models/item/rift_staff.json resources/assets/explosionwand/models/item/sleep_seal_shard.json resources/assets/explosionwand/lang/ru_ru.json resources/assets/explosionwand/lang/en_us.json reports/0053-2026-10-19-mod-engineer-rift-staff-sleep-shard.md
git rm resources/data/explosionwand/recipes/rift_key.json
git commit -m "feat(items): Посох Разлома и Осколок Печати: Сон" -m "- ПКМ трещина затягивает мобов, Shift+ПКМ шаг на 8 блоков, в данжах молчит" -m "- осколок Сна сообщает, что дорога не открыта" -m "- убран рецепт Ключа Разлома"
```

## Следующий шаг
boss-engineer: перенос Сердца в логово, добыча (посох, Ядро, осколок Сна), удаление Ключа/алтаря. asset-engineer: версия 2.7.0.
