# 0134 · Фаза 4.13 (5): Дневник Архимага

- **Дата:** 2026-10-22
- **Агент:** mod-engineer
- **Версия мода:** 2.19.2 (не поднималась)
- **Статус:** done
- **Связанные отчёты:** 0131, 0132

## Задача
Предмет «Дневник Архимага»: завязка и подсказки, страницы открываются по победам над боссами Земли и по прогрессу (Ключ Ада, портал). Выдача при первом входе в мир (один раз), крафт из книги и аметиста.

## Что сделано
- **Способ показа:** ПКМ выводит в чат открытые страницы (простейший и надёжный вариант: без written_book/NBT и GUI). `JournalItem` -> `Journal.show`.
- **Прогресс** хранится в `getPersistentData().PlayerPersisted` (как у Посоха Феникса, переживает смерть): `ewJRift`, `ewJMonolith`, `ewJPhoenix`, `ewJHell`, `ewJournalGiven`. Флаги побед ставит `Dungeons.onCleared` всем игрокам в зале при победе над боссом логова (по метке `h.boss.a`: monolith / phoenix / иначе Разлом); `ewJHell` - в конце успешной `HellGate.activate`. Boss*.java не тронуты.
- **Запасной путь:** страница открыта и по трофею в инвентаре (Посох Разлома / Сна / Феникса, Ключ Ада) - если игрока не было в зале при победе.
- **Страницы** (чистая функция `Journal.pages`): 0 завязка и 1 первый осколок - всегда; 2 (Сон, город) - после Сердца Разлома; 3 (Пепел, святилище) - после Монолита; 4 (Ключ Ада, алтарь) - после Феникса или с ключом; 5 (портал горит, Ад строится) - после зажигания портала. Пока есть закрытые страницы, внизу строка «остальные страницы пусты».
- **Выдача:** `Journal.onLogin` (`PlayerLoggedInEvent`, зарегистрирован в `ExplosionWandMod`): один раз, флаг `ewJournalGiven`; нет места - падает под ноги.
- **Крафт:** `archmage_journal.json` бесформенный, книга + аметистовый осколок. JEI: страница «как получить» (первый вход / крафт).
- **Ассеты:** `tools/gen_journal_assets.py` (zlib, без PIL; переиспользует `png`/`put` из `gen_hell_assets.py`): текстура 16x16 (фиолетовая книга с золотой звездой), модель, рецепт, lang ru+en (имя, подсказка, JEI, сообщение выдачи, заголовок, 6 страниц).
- Тон записей Архимага по `docs/world.md`, ничего сверх открытого: стр. 1 не упоминает Город и Феникса и т.д.
- Заглушки: `Inventory.m_36054_`, `Entity.m_19983_`, `PlayerEvent.PlayerLoggedInEvent`.
- Тест `tests/JournalTest.java`: страницы по прогрессу, страница 5 только после портала, ключ открывает страницу 4.

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `src/com/explosionwand/Journal.java`, `JournalItem.java` | новые |
| `src/com/explosionwand/ExplosionWandMod.java` | `ARCHMAGE_JOURNAL`, слушатель входа |
| `src/com/explosionwand/Dungeons.java` | 1 строка в `onCleared`: флаги победы |
| `src/com/explosionwand/HellGate.java` | флаг портала в `activate` |
| `src/com/explosionwand/jei/ExplosionWandJei.java` | страница Дневника |
| `stubs/.../Inventory.java`, `Entity.java`, `PlayerEvent.java` | заглушки |
| `tests/JournalTest.java`, `tools/gen_journal_assets.py` | новые |
| `resources/assets/explosionwand/{textures/item,models/item}/archmage_journal*`, `lang/ru_ru.json`, `lang/en_us.json` | ассеты и тексты |
| `resources/data/explosionwand/recipes/archmage_journal.json` | рецепт |

## Проверка
- `python tools/build.py`: SRG неизвестных 0 (исходники 276 / байткод 277), linkcheck с настоящими jar расхождений 0, ID проблем 0, все тесты OK (в т.ч. JournalTest), jar 2.19.2.
- Новые SRG по `lookup.py --srg`: `m_36054_` = PlayerInventory.insertStack (Inventory.add(ItemStack)); `m_19983_` = Entity.dropStack(ItemStack); `m_7203_` = Item.use; `m_150109_`, `m_18947_` уже были. Forge-методы (`getPersistentData`, `getEntity`) без SRG.
- JSON lang пересобраны через `json`, валидны.

## Проверить в игре
- Новый мир / первый вход: в инвентаре Дневник и сообщение; перезайти - второй раз не выдаётся; после смерти не выдаётся снова.
- ПКМ в начале: заголовок + страницы 0 и 1 + «остальные страницы пусты». Тексты читаются в чате (длинные строки переносятся), цвета нормальные.
- После победы над Сердцем Разлома появляется стр. 2, после Монолита - 3, после Феникса - 4 (в мире с уже убитым боссом - появятся по посоху в инвентаре). Ключ Ада в руках открывает стр. 4; после зажигания портала - 5.
- Крафт: книга + аметистовый осколок; страница в JEI «как получить».
- Текстура книги не фиолетово-чёрная, видна в хотбаре и вкладке «Магия».
- Мультиплеер: флаги получают все игроки, бывшие в зале в момент победы.

## Решения и почему
- Чат вместо written_book: команда `give` не откроет книгу, а свой GUI без Gradle дорог; чат прост и не ломается. Если захочется настоящую книгу - можно заменить `Journal.show` на выдачу `written_book` с NBT страниц.
- Победу ставим в `Dungeons.onCleared` (общая зачистка), а не в Boss*.java: зона boss-engineer не тронута.
- Игрок, отсутствовавший в зале при победе, получает страницу по трофею в инвентаре; без трофея (выкинул, спрятал в сундук) - не получит.

## Проблемы и открытые вопросы
- `docs/world.md` и `docs/history.md` не правил (запись в history - в конце фазы, версия 3.0.0).
- Тексты страниц 2-3 раскрывают общий путь (город, святилище), но только после соответствующей победы; при желании смягчить - правка в `gen_journal_assets.py` и перезапуск.

## Git (выполняет пользователь)
```
git add src/com/explosionwand/Journal.java src/com/explosionwand/JournalItem.java src/com/explosionwand/ExplosionWandMod.java src/com/explosionwand/Dungeons.java src/com/explosionwand/HellGate.java src/com/explosionwand/jei/ExplosionWandJei.java stubs/net/minecraft/world/entity/player/Inventory.java stubs/net/minecraft/world/entity/Entity.java stubs/net/minecraftforge/event/entity/player/PlayerEvent.java tests/JournalTest.java tools/gen_journal_assets.py resources/assets/explosionwand/textures/item/archmage_journal.png resources/assets/explosionwand/models/item/archmage_journal.json resources/data/explosionwand/recipes/archmage_journal.json resources/assets/explosionwand/lang/ru_ru.json resources/assets/explosionwand/lang/en_us.json reports/0134-2026-10-22-mod-engineer-archmage-journal.md
git commit -m "feat(items): Дневник Архимага" -m "- выдаётся при первом входе, крафт: книга + аметист" -m "- страницы по победам над боссами Земли и прогрессу (Ключ Ада, портал)" -m "- ru/en тексты, JEI, тест страниц"
```
Примечание: `ExplosionWandMod.java`, `Dungeons.java`, `HellGate.java`, `Entity.java`, `ExplosionWandJei.java`, оба lang и `PlayerEvent.java` уже содержали чужие незакоммиченные правки - попадут целиком; разнести можно через `git add -p`.

## Следующий шаг
- Остаток 4.13: баланс Феникса (решение пользователя), `docs/history.md`, версия 3.0.0, ревью.
