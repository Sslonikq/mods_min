# 0131 · Фаза 4.13 (1)-(3): Ключ Ада, алтарь-портал, Ад-заглушка

- **Дата:** 2026-10-22
- **Агент:** mod-engineer
- **Версия мода:** 2.19.2 (не поднималась; 3.0.0 в конце фазы)
- **Статус:** done
- **Связанные отчёты:** 0130

## Задача
4.13 пункты 1-3: Ключ Ада; алтарь-портал из новых блоков с постоянным порталом (ключ остаётся в алтаре); измерение-заглушка `hell` с площадкой, заголовком «Ад ещё строится» и обратным порталом. Сердце Затмения не трогал.

## Что сделано
- **Подход к блокам.** В моде своих блоков не было. Варианты: (А) зарегистрировать настоящие `Block` + `BlockItem` (world.md это допускает: «сложность средняя»); (Б) обойтись ванильными блоками с хитрым опознаванием. Выбрано А: блоки живут в мире сами, портал переживает перезапуск без своих хранилищ, крафт из материалов Земли настоящий. Для этого дописаны заглушки (`Block extends BlockBehaviour`, `Properties`, `BlockItem`, `UseOnContext`, `InteractionResult`).
- **Блоки** (`ExplosionWandMod`, `DeferredRegister<Block> BLOCKS`): `seal_stone` (Печатный камень, рамка), `phoenix_embers` (Угли Феникса, гнездо), `hell_socket` (занятое гнездо: неломаемое, светится, при разрушении дропает Угли), `hell_portal` (проём: без коллизии, неломаемый, светится, без дропа). Предметы блоков `seal_stone`, `phoenix_embers` в `ITEMS` (попадают во вкладку «Магия»).
- **Ключ Ада** `hell_key` (`HellKeyItem`, EPIC, стак 1). Рецепт бесформенный: `rift_core` + `dreaming_eye` + `phoenix_feather` (world.md формы не задаёт). ПКМ по Углям Феникса: `HellGate.activate`.
- **Алтарь** (`HellGate`): рамка 5x5 вертикально вдоль X или Z (15 блоков Печатного камня + гнездо в центре верхней грани), проём 3x3 пуст. Ключ зажигает проём блоками портала, гнездо -> `hell_socket`; ключ тратится (в творческом нет). Отказы действуют сообщением над хотбаром: неполная рамка, защита данжа (`Dungeons.protectedAt` по гнезду и проёму), наши измерения (там зажигать нельзя), «ключ уже в алтаре».
- **Вход**: `HellPortalBlock.m_7892_` (entityInside) -> `HellGate.inside`: игрок стоит в проёме 30 тиков подряд (частицы), затем перенос; перезарядка 100 тиков. Из любого не-нашего измерения переносит в `hell`; место возврата (клетка в 2 блоках от плоскости портала, где есть пол и место) пишется в `getPersistentData` игрока (`ewHellDim/X/Y/Z`). В Аду портал ведёт назад к этой точке; нет записи или измерения - на мировой спавн.
- **Ад-заглушка**: `Dims.HELL` (входит в `isOurs`, значит палочки Земли там не ломают блоки). JSON `dimension/hell.json`, `dimension_type/hell.json` (как eclipse, `ultrawarm`), биом `worldgen/biome/hell.json` (без мобов, красный туман, белый пепел). При первом входе `HellGate.ensureHell` строит круг радиуса 9 (y=62-63: незерак, чёрный камень, кирпичи, магма), ограду по краю (нижний кирпич-забор), лаву 2x2, 4 лампы на столбиках и обратный портал (рамка + зажжённый проём) на z=-6. Игрок появляется на (0.5, 64, -2.5). Заголовок и подзаголовок через `Cmd.run title` (translate-ключи `explosionwand.hell.building` / `.sub`).
- **Крафты**: `seal_stone` (2 каменных кирпича + Пыль Разлома + Пепельный уголь + Сонная нить -> 3 блока, на рамку нужно 5 крафтов), `phoenix_embers` (3 Пепельных угля + Искра Феникса + магмовый блок -> 1).
- **Ассеты**: `tools/gen_hell_assets.py` (PIL в системе нет и не ставился - PNG пишутся через zlib/struct) генерирует текстуры 16x16, blockstates, модели блоков и предметов, лут-таблицы, тег `mineable/pickaxe`, рецепты, JSON измерения/типа/биома. Lang ru+en (имена, подсказка ключа, сообщения, заголовок, JEI-справка), JEI-страницы `ExplosionWandJei`.
- Тест `tests/HellGateTest.java`: раскладка рамки (15 + проём 9 + гнездо = 5x5, без пересечений, обе оси), портал стоит на площадке.

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `src/com/explosionwand/HellGate.java` | новый: раскладка алтаря, активация, вход/возврат, площадка Ада |
| `src/com/explosionwand/HellPortalBlock.java`, `HellKeyItem.java` | новые: блок проёма, Ключ Ада |
| `src/com/explosionwand/ExplosionWandMod.java` | BLOCKS, 4 блока, 2 предмета-блока, Ключ, слушатель выхода игрока |
| `src/com/explosionwand/Dims.java` | `HELL`, входит в `isOurs` |
| `src/com/explosionwand/jei/ExplosionWandJei.java` | 3 справки |
| `stubs/.../Block.java`, `BlockBehaviour.java`, `Item.java`, `ResourceKey.java` | дополнены; новые `BlockItem`, `UseOnContext`, `InteractionResult` |
| `tests/HellGateTest.java` | новый |
| `tools/gen_hell_assets.py` | новый генератор ассетов 4.13 |
| `resources/assets/explosionwand/{textures/block,textures/item,blockstates,models}/...` | 4 блока + Ключ |
| `resources/data/explosionwand/{recipes,loot_tables/blocks,dimension,dimension_type,worldgen/biome}/...`, `data/minecraft/tags/blocks/mineable/pickaxe.json` | рецепты, лут, Ад |
| `resources/assets/explosionwand/lang/ru_ru.json`, `en_us.json` | тексты |
| `docs/world.md` | раздел «Переход в Ад» |

## Проверка
- `python tools/build.py`: SRG исходники 274 / байткод 275, неизвестных 0, linkcheck с настоящими jar Forge: расхождений 0, проблем ID 0, все тесты OK (в т.ч. HellGateTest), jar 2.19.2.
- Новые SRG (проверены `lookup.py --srg`): `m_284310_` = Properties.of; `m_60913_` strength(f,f); `m_60953_` lightLevel; `m_60955_` noOcclusion; `m_60910_` noCollission; `m_222994_` noLootTable; `m_7892_` entityInside; `m_6225_` Item.useOn; `m_43725_/m_8083_/m_43723_/m_43722_` UseOnContext.getLevel/getClickedPos/getPlayer/getItemInHand; `m_135782_` ResourceKey.location. Остальные уже были в проекте.
- JSON: все новые валидны (генератор пишет через `json`, lang перечитан).

## Проверить в игре
- Мод стартует (регистрация блоков: нет крашей), блоки и Ключ есть во вкладке «Магия» (ключ - через крафт), текстуры не «фиолетовые».
- Крафты: 5x Печатный камень (15 блоков), Угли Феникса, Ключ Ада из трёх трофеев.
- Построить рамку 5x5 (вертикально, оба направления X и Z), Угли в центре верхней грани, внутри пусто. ПКМ ключом: проём загорается, гнездо меняет вид, ключ уходит. С неполной рамкой - сообщение, ключ цел.
- Войти в проём: через ~1,5 с в Аду на площадке, заголовок «Ад ещё строится»; обратный портал за спиной (z=-6) возвращает точно к алтарю (в 2 блоках перед ним), потом снова туда-обратно; перезапустить мир - портал на месте и работает; смерть в Аду - возрождение у кровати.
- Проём правда виден (прозрачная текстура, translucent) и не блокирует ход; в нём можно стоять, не двигаясь.
- Площадка Ада: свет достаточный, ограда на краю, лава не растекается, мобов нет.
- Поломка рамки: рамка ломается киркой и дропает себя; проём остаётся (см. вопросы).
- Тебя не должно пускать зажигать портал внутри данжей и наших измерений.

## Решения и почему
- Проём - постоянный блок, а не сущность/таймер: переживает рестарты, не требует уборки, соответствует «навсегда». Ключ расходуется и «остаётся» как смена блока гнезда (без отдельной сущности-предмета).
- Один Ад на мир, у всех порталов одна площадка и один обратный портал; возврат к конкретному порталу - по данным игрока.
- Рамка 5x5 с проёмом 3x3 (не как у Незера 4x5) - «своя форма».
- Лёгкий выбор рецептов блоков: цифры требуют решения экономики; поправимо.

## Проблемы и открытые вопросы
- Проём не гаснет, если сломать рамку (блоки портала неломаемые, в выживании остаются висеть). Если нужно - слушатель `BlockEvent.BreakEvent` по рамке с очисткой проёма (небольшая доработка).
- Если игрок пришёл в Ад, а точка возврата позже уничтожена (мир другого игрока) - он выйдет в воздух над пустотой или на землю по `exitSpot`; запасной вариант - мировой спавн только когда записи нет.
- Площадка Ада в подвалах/подводных мирах не ломает чужое: строится только в `hell`.
- Хвост ночи: `Fx.state(...)`-блоки площадки ставятся в одном тике (~700 блоков) - разово, на ходу не заметно; при проблемах - раздробить по тикам.

## Git (выполняет пользователь)
```
git add src/com/explosionwand/HellGate.java src/com/explosionwand/HellPortalBlock.java src/com/explosionwand/HellKeyItem.java src/com/explosionwand/ExplosionWandMod.java src/com/explosionwand/Dims.java src/com/explosionwand/jei/ExplosionWandJei.java stubs/net/minecraft/world/level/block/Block.java stubs/net/minecraft/world/level/block/state/BlockBehaviour.java stubs/net/minecraft/world/item/Item.java stubs/net/minecraft/resources/ResourceKey.java stubs/net/minecraft/world/item/BlockItem.java stubs/net/minecraft/world/item/context/UseOnContext.java stubs/net/minecraft/world/InteractionResult.java tests/HellGateTest.java tools/gen_hell_assets.py resources/assets/explosionwand/textures/block resources/assets/explosionwand/textures/item/hell_key.png resources/assets/explosionwand/blockstates resources/assets/explosionwand/models resources/data/explosionwand/recipes/hell_key.json resources/data/explosionwand/recipes/seal_stone.json resources/data/explosionwand/recipes/phoenix_embers.json resources/data/explosionwand/loot_tables resources/data/explosionwand/dimension/hell.json resources/data/explosionwand/dimension_type/hell.json resources/data/explosionwand/worldgen/biome/hell.json resources/data/minecraft/tags/blocks/mineable/pickaxe.json resources/assets/explosionwand/lang/ru_ru.json resources/assets/explosionwand/lang/en_us.json docs/world.md reports/0131-2026-10-22-mod-engineer-hell-portal.md
git commit -m "feat(dims): Ключ Ада, портал-алтарь и Ад-заглушка" -m "- блоки Печатный камень, Угли Феникса, проём портала; Ключ Ада" -m "- постоянный двусторонний портал, измерение hell с площадкой и заголовком" -m "- ассеты, рецепты, lang ru/en, тест раскладки"
```
Примечание: `ExplosionWandMod.java`, `Dims.java`, `ru_ru.json`, `en_us.json`, `docs/world.md`, `ExplosionWandJei.java` уже содержали чужие незакоммиченные правки до этого шага - они попадут в коммит целиком; при желании разнести - `git add -p`.

## Следующий шаг
- **asset-engineer**: заменить заготовки 16x16 (генератор `tools/gen_hell_assets.py`, простой zlib) на нормальные текстуры: Печатный камень, Угли Феникса (анимация mcmeta по желанию), занятое гнездо, проём портала (анимированный), иконка Ключа Ада; проверить вид блоков при размещении рамки; перечитать тексты lang.
- **code-reviewer**: ревью 0131 (особенно проём как неломаемый блок, перенос и запись возврата).
- **mod-engineer**, остаток 4.13: пункты 4-5 (убрать Короля/Кита из Земли, рецепт Посоха Затмения, JEI; Дневник Архимага); при желании - гашение проёма при поломке рамки.
- boss-engineer: пункт 6 (баланс).
