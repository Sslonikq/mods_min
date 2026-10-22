# 0119 · Ревью фазы 4.9 «Мобы Святилища» (2.17.0)

- **Дата:** 2026-10-22
- **Агент:** code-reviewer
- **Версия мода:** 2.17.0
- **Статус:** done
- **Связанные отчёты:** 0113, 0114, 0115, 0116, 0117, 0118

## Задача
Ревью 4.9 (0116–0118) и хвостов 0113 (0114, 0115): AshLogic, AshModels, AshMobs, AshMobsB, общий код RiftMobs/RiftModels/Mats, Ash.minion, Boss5, DungeonMech.waveMob, gen_ash.py и шаблоны, lang, тесты. Особо: жизненный цикл и уборка, Хранитель Углей, приспешники Феникса, Блуждающий огонёк, Dungeons.shield, лимит ХП, lang, не сломаны ли Разлом и Город.

## Вердикт: **REQUEST CHANGES** (одно major, п.1; правка маленькая)
Коммитить можно уже сейчас: HEAD сейчас **не собирается** (см. «Проблемы»), а команды ниже это чинят. Правка п.1 пойдёт отдельным коммитом (патч), после неё фазу можно закрывать.

## Что сделано
- `python tools/build.py`: зелёный. Неизвестных SRG 0, linkcheck 0 неразрешённых, проблем ID 0, gen_dungeons 0 устаревших, все 13 тестов OK (AshMobsTest, BossAshTest, DungeonTest, RiftMobsTest, CityMobsTest и др.), jar `dist/explosionwand-1.20.1-2.17.0.jar`.
- Новых SRG-имён нет: все `m_*/f_*` из Ash*.java уже есть в `src/` на HEAD (проверено `git grep` по каждому). Новые методы заглушки `Entity` (`m_19879_`, `getPersistentData`) относятся к 4.10 (0111).
- lang: ru/en по 320 ключей, наборы совпадают; все ключи `explosionwand.mob.ash_*` из кода есть в lang; старые ключи `explosionwand.ash.{spark,walker,guard}` в коде больше не используются.
- ХП хостов: 6–360, лимит 1024 соблюдён.
- Блоки: мобы данжа блоков не меняют; сундук огонька ставится вне данжей (`Dungeons.near(..., 8)`), `Dungeons.shield` не нужен.
- Цифры баланса из 0116/0117/history сверены с `AshLogic` и `RiftModels.Kind`: совпадают, кроме п.4.
- Уборка: смерть → `onDeath` (die + части); сброс зала / Boss5.purge / minionsOff / решённая головоломка → `RiftMobs.remove` с частями; осиротевшие части убирает `sweep`; статика — `AshMobs.clear()` из `RiftMobs.clear()`. Горящий пепел только от `die()` (смерть), не от сброса; у приспешников (`summoned`) пепла нет.
- Хранитель: `clampPhase` останавливает удар на пороге 50% − 0,01, ярость включается на следующем тике; множители щит/спина/оглушение применяются до среза по порогу; ключ — через зачистку зала 20 (`Ash.GUARD_HALL`, тест в AshMobsTest).
- Общий код: `spawnKind(..., null)` для старых вызовов даёт `tags = ""`, `summoned = false` — Разлом и Город не затронуты (RiftMobsTest и CityMobsTest OK).

## Замечания
1. **[major]** `AshMobsB.java:555-560` (+ `RiftMobs.java:176-196`): Искры ловушки огонька получают `expire` и `summoned` только в памяти. Хост с `PersistenceRequired`, без тега зала. Если чанк выгрузится (игрок ушёл) или сервер перезапустится, `RiftMobs.scan` подхватит их заново через `adopt` уже с `expire = 0` и `summoned = false`. Получается вечная враждебная Искра в обычном мире, которая к тому же даёт материалы Святилища (`Mats.dungeonOf(ASH_SPARK)`). Пока рядом нет игрока, `think()` не идёт, и срок жизни не проверяется. → Звать `spawnKind(lv, Kind.ASH_SPARK, ..., null, "\"ewash_trap_min\"")`: тег на `_min` сам ставит `summoned` при `adopt`. Хост с этим тегом без живой записи в реестре убирать: в `adopt` (`expire = now + TRAP_SPARK_LIFE` или сразу `retire`) либо в цикле `AshMobsB.tickWorld` рядом с огоньками (`RiftMobs.remove`). Добавить проверку в AshMobsTest (тег ловушки → summoned и срок жизни).
2. **[minor]** `AshMobs.java:304`, `AshMobsB.java:400` (`AshLogic.waveHits`): условие только `dy < WAVE_JUMP`, снизу ограничения нет. Волна Звонаря и удар Хранителя о землю бьют и отбрасывают игроков на этажах ниже, сквозь перекрытия: у башни этажи через 14 блоков, Звонари на ярусах 2–4. Срабатывает, когда в мультиплеере кто-то стоит ниже или игрок спрыгнул во время телеграфа. → `dist <= radius && dy > -1.5 && dy < WAVE_JUMP`, плюс проверка в тесте.
3. **[minor]** `DungeonMech.java:642`: мобы волны жаровен идут через `spawnKind(..., tag)` без `summoned` и дают материалы Святилища, в каждой волне есть Угольный страж (`strong`, щедрый дроп). Ошибся в порядке, перезажёг — и так раз за разом: ферма материалов на головоломке (раньше в волнах были ванильные мобы без материалов). → Передавать extraTags (например `"\"ewdw_min\""`, чтобы `summoned` пережил перезапуск) или осознанно оставить, но тогда записать в docs.
4. **[minor]** `AshMobsB.java:479` против 0117, `docs/dungeons.md` и history: заявлено «+2 редких материала», а в коде `Mats.drop(level, ASH, 1, 2, ...)`, и rarity 1 — это `smoldering_feather` (UNCOMMON). Редкий — индекс 2 (`phoenix_spark`). → Либо `rarity 2`, либо поправить тексты на «2 Тлеющих пера».
5. **[minor]** `AshMobsB.java:571` + `Loot.java:18-21,50`: сундук «Дар Огонька» в обычном мире. `Loot.chest` ставит `setblock air` в клетку сундука и `glowstone` под ним. Если цель огонька попала на постройку игрока (крыша по heightmap), блок крыши заменится светокамнем. К тому же это бесплатный светокамень каждую ночь. Имя сундука только на русском (как у Boss2/Boss4, но здесь видно в обычном мире). → Для огонька не трогать блок под сундуком (отдельный вариант `Loot.chest` без `pillar`) и ставить сундук только в воздух (`m_60795_` клетки) на натуральном грунте; имя через `{"translate":...}` или английский вариант.
6. **[minor]** баланс огонька: до 2 огоньков, 20% за проверку раз в 10 с всю ночь, 45% — сундук с материалами Святилища тира 1. Если ходить за огоньками у башни, за ночь выходит несколько сундуков: материалы Святилища без похода в данж. → Решение пользователя: оставить или ограничить (например, не больше N сундуков за ночь на Святилище, или кулдаун после сундука).
7. **[minor]** версия: в `dist/` уже лежит `explosionwand-1.20.1-2.18.0.jar` (4.10, отчёт 0112), а теперь `mods.toml` = 2.17.0 с тем же Фениксом плюс мобами. «Новая» сборка получила номер меньше, в history 2.17.0 стоит выше 2.18.0. Так решил пользователь (0118). → В «Проверить в игре» добавлено: удалить jar 2.18.0 из папки mods, иначе будет два jar с одним modId. В history 2.17.0 можно дописать строку «включает 2.18.0».
8. **[nit]** `Boss5.java:54`: в комментарии осталось «имена мобов-заглушек - explosionwand.ash.spark / explosionwand.ash.walker». → Убрать.
9. **[nit]** `DungeonMech.java:631`: Javadoc `fireWave` говорит «ванильные мобы (хаски, скелеты, блейз)». → «мобы Святилища (waveMob)».
10. **[nit]** `tools/gen_ash.py:23` (шапка): «ваниль-элита (wither_skeleton) и хаски» на арене. → «Хранитель Углей и 4 Пеплохода».
11. **[nit]** `AshMobsB.java:598-602`: ветка `else RiftMobs.remove(e)` почти не срабатывает. `RiftMobs.scan` (раз в 20 тиков, 64 блока) подхватывает огоньков прошлой сессии раньше, чем `tickWorld` (раз в 200 тиков), и они живут заново: `born` сбрасывается, ещё 5 минут. Не утечка: днём они гаснут. → Либо пометить огонька тегом и не усыновлять (как в п.1), либо поправить комментарий.
12. **[nit]** цикл: отчёта `build-engineer` нет (сборку прогонял boss-engineer в 0118). Новых SRG-имён нет, так что по существу не страшно; в следующий раз шаг build-engineer лучше не пропускать.

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `reports/0119-2026-10-22-code-reviewer-phase-4-9.md` | этот отчёт (код не менялся) |

## Проверка
- `python tools/build.py`: SRG 0 неизвестных, linkcheck 0, ID 0, тесты 13/13 OK, jar 2.17.0.
- Новые SRG-имена: нет.
- Баланс: сверен, расхождение только в п.4.

## Проверить в игре
- Всё из 0116 и 0117 (облик, стая гончих, волна Звонаря и прыжок, червь на переправе, жрец, страж, Хранитель: щит, спина, окно после удара, ярость, сундук с ключом после зачистки зала 20).
- Пеплоходы Феникса появляются на `floor - 1` (пол у помоста) и не застревают в полу; Искры не вылетают за свод.
- Огонёк: ушли от ловушки за 100+ блоков, вернулись или перезашли — лишних Искр в обычном мире нет (после правки п.1).
- Волна жаровен: мобы убираются при решении; со Стражем в каждой волне не слишком жёстко.
- Перед запуском удалить из mods старый `explosionwand-1.20.1-2.18.0.jar` (п.7).

## Решения и почему
- Вердикт REQUEST CHANGES только из-за п.1: в обычном мире копятся вечные враждебные мобы, которые дают материалы. Правка точечная. Остальное — minor и nit, их можно закрыть тем же патчем или отложить.
- Коммитить, не дожидаясь правки, разумно: в HEAD уже лежат `Boss5.java` и `PhoenixStaff.java` (из коммитов 0114/0115), но без `Ash.stub` / `Ash.minion`, `LivingDamageEvent`, `PhoenixStaffItem` и регистрации. HEAD сейчас не компилируется.

## Проблемы и открытые вопросы
- **Состояние git:** 4.10 (отчёты 0109–0113) закоммичена лишь частично: `Boss5.java`, `PhoenixStaff.java` и `PhoenixStaffTest` попали в коммиты хвостов, а всё остальное по 4.10 до сих пор в рабочей копии и перемешано с 4.9 в одних файлах (lang, mods.toml, history, gen_ash.py, шаблоны, DungeonTest, BossAshTest, DungeonMech). Ниже один общий список. Коммит 1 содержит только файлы 4.10. Промежуточные коммиты 1–2 сами по себе могут не собраться, сборка зелёная после коммита 3.
- П.6 (ферма огонька) и п.3 (ферма на волнах) решает пользователь.

## Git (выполняет пользователь)
Единый список (PowerShell), по порядку:
```
git add src/com/explosionwand/Boss.java src/com/explosionwand/ExplosionWandMod.java src/com/explosionwand/Ticker.java src/com/explosionwand/jei/ExplosionWandJei.java src/com/explosionwand/PhoenixStaffItem.java stubs/net/minecraft/world/entity/Entity.java stubs/net/minecraftforge/event/entity/living/LivingDamageEvent.java resources/assets/explosionwand/models/item/phoenix_feather.json resources/assets/explosionwand/models/item/phoenix_staff.json resources/assets/explosionwand/textures/item/phoenix_feather.png resources/assets/explosionwand/textures/item/phoenix_staff.png tools/gen_materials.py reports/0109-2026-10-21-mod-engineer-nits-0108.md reports/0110-2026-10-21-boss-engineer-phoenix.md reports/0111-2026-10-21-mod-engineer-phoenix-staff.md reports/0112-2026-10-21-asset-engineer-release-2-18-0.md reports/0113-2026-10-21-code-reviewer-phase-4-10.md
git commit -m "feat(boss): Пепельный Феникс и Посох Феникса: подключение" -m "- Boss.wake/reset/orphans делегируют Boss5, Boss5::onHurt, PhoenixStaff tick/onDamage" -m "- регистрация Посоха и Пера Феникса, JEI, заглушки LivingDamageEvent и Entity" -m "- модели и текстуры пера и посоха (gen_materials.py); отчёты 0109-0113" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"

git add src/com/explosionwand/AshLogic.java src/com/explosionwand/AshModels.java src/com/explosionwand/AshMobs.java src/com/explosionwand/AshMobsB.java src/com/explosionwand/RiftMobs.java src/com/explosionwand/RiftModels.java src/com/explosionwand/Mats.java src/com/explosionwand/Ash.java src/com/explosionwand/Boss5.java src/com/explosionwand/DungeonMech.java src/com/explosionwand/Dungeons.java tools/gen_ash.py tools/gen_dungeons.py resources/data/explosionwand/structures/ash_north.nbt resources/data/explosionwand/structures/ash_mid.nbt resources/data/explosionwand/structures/ash_south.nbt reports/0116-2026-10-22-boss-engineer-ash-mobs-a.md reports/0117-2026-10-22-boss-engineer-ash-mobs-b.md
git commit -m "feat(boss): мобы Святилища и Хранитель Углей" -m "- Пеплоход, Искра, гончая, Звонарь, червь, жрец, Угольный страж" -m "- Хранитель Углей на арене, Блуждающий огонёк в обычном мире" -m "- Ash.minion для Феникса, волны жаровен, метки шаблонов explosionwand:ash_*" -m "- в шаблонах и gen_ash также метка boss phoenix и печать Гнезда (4.10)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"

git add tests/AshMobsTest.java tests/BossAshTest.java tests/DungeonTest.java resources/assets/explosionwand/lang/ru_ru.json resources/assets/explosionwand/lang/en_us.json resources/META-INF/mods.toml docs/history.md docs/dungeons.md docs/world.md reports/0118-2026-10-22-boss-engineer-ash-mobs-tests-docs.md
git commit -m "feat(boss): тесты, тексты и релиз 2.17.0 (Святилище с Фениксом)" -m "- AshMobsTest, BossAshTest, DungeonTest (раскладка 2, арена, волна, решётки 0109)" -m "- lang ru/en: мобы Святилища, Феникс, Посох и Перо" -m "- версия 2.17.0, history 2.17.0 и 2.18.0, dungeons.md, world.md" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"

git add reports/0119-2026-10-22-code-reviewer-phase-4-9.md
git commit -m "docs(agents): ревью фазы 4.9" -m "- REQUEST CHANGES: 1 major (Искры ловушки огонька), 6 minor, 5 nit" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"

git add ROADMAP.md
git commit -m "docs(agents): фаза 4.9 закрыта, текущая 4.12" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
Коммит ROADMAP лучше делать после патча по п.1. Строку «ждёт ревью code-reviewer» в ROADMAP поправит основная сессия.

## Следующий шаг
`boss-engineer`: п.1 (обязательно), по желанию п.2, 4, 8–10, патч 2.17.1 и отчёт. Пп.3, 5, 6 — сначала решение пользователя. Затем короткая повторная проверка `code-reviewer`, после неё закрыть 4.9 в ROADMAP и переходить к 4.12.
