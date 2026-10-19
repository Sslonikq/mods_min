# 0080 · Фаза 4.11, часть A: материалы Земли, баг Посоха Разлома, Вселение убрано

- **Дата:** 2026-10-19
- **Агент:** mod-engineer
- **Версия мода:** 2.9.0 (не повышалась: релиз 2.14.0 в конце части C)
- **Статус:** done
- **Связанные отчёты:** 0079 (фаза 4.6 закрыта); пользователь просил 4.11 вне очереди, переносы 4.7 не тронуты

## Задача
Часть A фазы 4.11: (1) баг «игра вылетает при ПКМ Посохом Разлома по мобам»; (2) 14 новых предметов (Магический стержень, Простое ядро, 3 ядра стихий, 9 материалов трёх данжей); (3) их добыча; (4) рецепты, старые рецепты палочек удалены; (5) Вселение убрано из мода; (6) страницы JEI. Сами 16 палочек делают части B и C.

## Что сделано
**1. Баг Посоха Разлома (RiftStaff.crack).** Причину надёжно найти не удалось, крашлога нет.
- Все SRG в `crack()` проверены `lookup.py --srg` по смыслу: `m_269111_` getDamageSources, `m_269104_` indirectMagic, `m_269075_` playerAttack, `m_19880_` getTags, `m_6249_` getEntities(excl, box, pred), `m_20256_` setDeltaMovement, `m_20206_` getBbHeight, `m_21830_`/`m_21824_` isOwnedBy/isTame, `f_19864_` hurtMarked, `m_6469_` hurt, `m_146892_`, `m_20252_`, `m_82549_`, `m_82490_`, `m_82554_`, `m_6087_`, `m_37304_` (ProjectileUtil.getEntityHitResult без margin), `m_82443_`, `m_82450_` - все соответствуют задуманному. Вызов `indirectMagic` (`m_269104_`) был единственным непроверенным (нигде больше в проекте не используется).
- Источник урона заменён на проверенный `m_269075_(p)` (playerAttack).
- Обработка каждого моба вынесена в `pull(...)` и обёрнута в `try/catch (RuntimeException | LinkageError)` с однократным логом в stderr (`[explosionwand] Посох Разлома: сбой на мобе <теги>: ...` + стек). Один сбойный моб больше не обрывает остальных.
- Просмотрен остальной путь: `Aim.of` (при попадании по сущности: `m_82450_()`/`m_82443_()`, null-проверки есть; тот же код у 12 других палочек), `Minions.controllable`/`Cmd.hasTagPrefix` (только теги, NPE нет), хуки `LivingHurtEvent` при уроне игрока по обычному мобу: `RiftMobs.onHurt` (ранний выход при пустом реестре; `getSource()` с null-проверкой), `ArcaneSet.onHurt` (только цель в сете), `Inverted.onHurt`/`TimeStop.onHurt`/`Comet.onHurt`/`Boss*.onHurt` (сравнение с известными сущностями, обычного моба не касаются). NPE/ClassCast не найдено. Теперь урон идёт тем же путём, что обычный удар игрока.
- **Если краш повторится:** игра, вылетающая без `crash-reports`, чаще падает на клиенте или в нативной части. Прислать: `crash-reports/*.txt` (если есть), `logs/latest.log` и `logs/debug.log` (последние ~100 строк до вылета), `hs_err_pid*.log` из папки игры (краш JVM), а также какой моб (ваниль/наш), в каком измерении, стоит ли JEI и другие моды. В логе искать строку `[explosionwand] Посох Разлома: сбой` - её пишет новый try/catch.

**2. Предметы** (регистрация в `ExplosionWandMod`, класс `MaterialItem` с тултипом `.desc`): `magic_rod`, `simple_core`, `crystal_core`, `wind_core`, `fire_core`, `rift_dust`/`echo_crystal`/`rift_marrow`, `sleep_thread`/`teal_stitch`/`sleeper_tear`, `ash_coal`/`smoldering_feather`/`phoenix_spark` (редкость: обычные COMMON, редкие UNCOMMON, очень редкие и ядра стихий RARE; стак 64). Модели `item/generated`, текстуры 16x16 (генератор `tools/gen_materials.py`; PIL не установлен, использован чистый zlib/struct, ничего не устанавливалось). Lang ru+en: названия, `.desc`, страницы JEI. Во вкладке «Магия» порядок = порядок регистрации: в `ExplosionWandMod` оставлен комментарий-место **до материалов** для 16 палочек (B и C).

**3. Добыча** (`Mats.java`):
- обычные: с мобов своего данжа, только за убийство игроком (`RiftMobs.onDeath` -> `Mats.onMobDeath`, `ItemEntity`). Обычный моб: 0/1/2 шт. = 40/50/10%; сильный (голем, Пожиратель эха, Страж Шва, Статуя-страж, Колыбельщик): 2-3; Кристальный жук: 50% одна. Не дают: мини-осколочники, копии Сновидца. Туманный странник и жук считаются мобами своих данжей.
- редкие: сундуки (`Dungeons.lootFor` -> `Mats.chestLoot`) по тиру метки: тир 1 (побочные комнаты и залы) 0/1/2 редких = 35/50/15%; тир 2 (логова, склады, склеп) 2-3 редких; тир 3 (секретные) 2 редких + 1 очень редкий (25%: 2). Во все сундуки ещё 1-3 обычных. Только Разлом и Город Спящих; у стенда механик материалов нет.
- Ванильная часть добычи сундуков (аметист, золото, алмазы) оставлена - она нужна для крафта стержня и ядер; временная добыча в части материалов заменена настоящей.
- Святилище: мобов и данжа ещё нет, материалы Святилища **нигде не добываются** (запланировано 4.8/4.9). Хуки описаны ниже.

**4. Рецепты** (shapeless, `resources/data/explosionwand/recipes/`): стержень = палка + аметистовый осколок + золотой самородок; Простое ядро = аметистовый осколок + редстоун + лазурит; Кристальное ядро = Пыль Разлома + аметистовый блок; Ядро ветра = Сонная нить + 2 пера + железный слиток; Огненное ядро = Пепельный уголь + угольный блок + огниво. Удалены рецепты: explosion_wand, black_hole_wand, ice_storm_wand, rift_wand, chronos_wand, mimic_wand, tesseract_wand, fist_wand, timestop_wand, comet_wand, void_wand, haunt_wand, inverted_wand, portal_gun, possess_wand. Предметы старых палочек остаются зарегистрированными (только креатив/команда). Не тронуты: seal_shard_rift, dream_seal, eclipse_seal, sky_seal, eclipse_staff, 4 рецепта брони Архимага.

**5. Вселение убрано:** удалены `Possess.java`, `PossessWandItem.java`, регистрация, lang (ru+en), модель, текстура, рецепт, вызов `Possess.tick` в `Ticker`, ветка в `DungeonGuard.forbidden`, `case "possess_wand"` в `WhaleStaff`, исключения в `tools/ids.py`. JEI-страницы Вселения не было. Мировой сейв: неизвестный ID предмета Forge просто удаляет, данных мира у Вселения не было.

**6. JEI:** `ExplosionWandJei` - 14 новых страниц (ru+en).

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `src/com/explosionwand/RiftStaff.java` | `pull()` с try/catch, урон `m_269075_(p)` |
| `src/com/explosionwand/Mats.java` | новый: материалы, дроп мобов, состав сундуков, хуки |
| `src/com/explosionwand/MaterialItem.java` | новый: предмет с тултипом |
| `src/com/explosionwand/ExplosionWandMod.java` | 14 регистраций, место для 16 палочек, убрано Вселение |
| `src/com/explosionwand/RiftMobs.java` | `onDeath`: дроп материала за убийство игроком |
| `src/com/explosionwand/Dungeons.java` | `lootFor` добавляет `Mats.chestLoot` |
| `src/com/explosionwand/Ticker.java`, `DungeonGuard.java`, `WhaleStaff.java` | убрано Вселение |
| `src/com/explosionwand/jei/ExplosionWandJei.java` | 14 страниц |
| `src/com/explosionwand/Possess.java`, `PossessWandItem.java` | удалены |
| `stubs/net/minecraftforge/event/entity/living/LivingDeathEvent.java`, `stubs/net/minecraft/world/item/ItemStack.java` | `getSource()`, конструктор `ItemStack(ItemLike,int)` |
| `tests/MatsTest.java` | новый тест: шансы, состав сундуков, регистрация/модели/текстуры/lang, нет старых рецептов |
| `tools/gen_materials.py`, `tools/ids.py` | генератор текстур и моделей; убраны исключения Possess |
| `resources/assets/explosionwand/models/item/*.json` (14), `textures/item/*.png` (14) | новые; `possess_wand` удалены |
| `resources/data/explosionwand/recipes/` | 5 новых, 15 удалённых |
| `resources/assets/explosionwand/lang/ru_ru.json`, `en_us.json` | +14x3 ключей, -possess_wand (файлы переписаны через json.dump, отступ 2) |

## Проверка
- `python tools/build.py`: 143 класса, SRG исходники 226 / байткод 227, неизвестных 0, проблем ID 0, все тесты OK (новый MatsTest), jar `dist/explosionwand-1.20.1-2.9.0.jar`.
- Новых SRG-имён нет (`m_269075_` = DamageSources.playerAttack проверен `--srg`; остальные уже использовались в проекте).

## Проверить в игре
- Посох Разлома: ПКМ по группе мобов (зомби, скелеты, коровы, ручной волк - его не должно тянуть); вылета нет. Если вылет остался - материалы из раздела «Если краш повторится».
- Креатив-вкладка «Магия»: 14 новых предметов в конце, текстуры различимы, тултипы на русском/английском.
- Верстак: 5 новых рецептов; старые палочки не крафтятся, в креативе есть.
- Моб Разлома, убитый игроком, роняет Пыль Разлома (не всегда); убитый лавой/падением - нет; мини-осколочники ничего не дают; Кристальный жук иногда роняет пыль.
- Мобы Города: Сонная нить; Колыбельщик и стражи - 2-3 штуки.
- Сундуки: тайник Разлома (тир 3) - Сердцевина + Кристаллы эха; «Ложе Архимага» в Городе - Слеза Спящего; побочные сундуки - иногда Кристалл эха/Бирюзовый шов.
- Старый сейв с Палочкой Вселения в инвентаре: игра не падает при загрузке.

## Решения и почему
- Дроп мобов централизован в `RiftMobs.onDeath`, а не в каждом `die()`: у Города `die()` не вызывает `super`, переопределений больше 10; единая точка надёжнее. Условие «убил игрок» защищает от случайных смертей.
- Рецепты shapeless: форма в ТЗ не задана.
- Материалы данжей не крафтятся из ванильных ресурсов и не падают с ванильных мобов.

## Хуки для частей B и C и следующих фаз
- Регистрация палочек: `ExplosionWandMod`, комментарий «4.11: палочки Земли ... регистрируются ЗДЕСЬ» (перед материалами). Поля материалов: `MAGIC_ROD, SIMPLE_CORE, CRYSTAL_CORE, WIND_CORE, FIRE_CORE, RIFT_DUST, ECHO_CRYSTAL, RIFT_MARROW, SLEEP_THREAD, TEAL_STITCH, SLEEPER_TEAR, ASH_COAL, SMOLDERING_FEATHER, PHOENIX_SPARK`; в рецептах id `explosionwand:<имя>`.
- Запрет палочек в данжах: `DungeonGuard.forbidden` (список классов) - Копателя и Возврата добавить туда.
- `Mats.drop(level, Dungeon.ASH, rarity, count, x, y, z)` - общий хук дропа; `Mats.dungeonOf(RiftModels.Kind)` и `strong(Kind)` - вписать виды мобов Святилища (4.9); `Mats.dungeonOf(Dungeons.Def)` - вписать Def Святилища (4.8): сундуки и мобы заработают без других правок. `Mats.id(dungeon, rarity)` - ID для рецептов и сундуков.
- Хуки `CityMobs.registerLight/gust` ждут палочки Огонька и Ветра - не трогались.

## Проблемы и открытые вопросы
- Причина вылета Посоха Разлома не установлена: исправление защитное (проверенный источник урона + перехват). Нужна проверка пользователем.
- Количество и шансы материалов - первая прикидка, баланс в фазе 8.
- Туманный странник вне данжа тоже дропает Сонную нить (мобы Города по ТЗ).

## Git (выполняет пользователь)
```
git add src/com/explosionwand/RiftStaff.java
git commit -m "fix(staff): Посох Разлома - проверенный источник урона и защита от сбоя" -m "- урон через playerAttack вместо indirectMagic" -m "- try/catch на каждого моба с однократным логом"
git add src/com/explosionwand/Possess.java src/com/explosionwand/PossessWandItem.java src/com/explosionwand/Ticker.java src/com/explosionwand/DungeonGuard.java src/com/explosionwand/WhaleStaff.java tools/ids.py
git commit -m "chore(wand): Палочка Вселения убрана из мода" -m "- класс, регистрация, lang, модель, текстура, рецепт"
git add src/com/explosionwand/Mats.java src/com/explosionwand/MaterialItem.java src/com/explosionwand/ExplosionWandMod.java src/com/explosionwand/RiftMobs.java src/com/explosionwand/Dungeons.java src/com/explosionwand/jei/ExplosionWandJei.java stubs/net/minecraftforge/event/entity/living/LivingDeathEvent.java stubs/net/minecraft/world/item/ItemStack.java tests/MatsTest.java tools/gen_materials.py resources/assets/explosionwand/lang/ru_ru.json resources/assets/explosionwand/lang/en_us.json resources/assets/explosionwand/models/item resources/assets/explosionwand/textures/item resources/data/explosionwand/recipes reports/0080-2026-10-19-mod-engineer-materials-4-11a.md
git commit -m "feat(items): материалы данжей, ядра, Магический стержень" -m "- 14 предметов, текстуры, lang, страницы JEI" -m "- дроп с мобов Разлома и Города, материалы в сундуках" -m "- рецепты стержня и ядер, старые рецепты палочек удалены"
```
(Удалённые файлы попадают в `git add` через указанные каталоги и путь `src/.../Possess*.java`; если git не видит удаление, использовать `git add -A <путь>`.)

## Следующий шаг
Часть B (`mod-engineer`): палочки Земли по таблице «ИТОГ» (стартовые, Копатель, Взрывная урезанная); часть C: оставшиеся палочки, рецепты по схеме, релиз 2.14.0 и `docs/history.md`.
