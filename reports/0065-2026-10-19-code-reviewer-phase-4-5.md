# 0065 · Ревью фазы 4.5 (Город Спящих, 2.8.0)

- **Дата:** 2026-10-19
- **Агент:** code-reviewer
- **Версия мода:** 2.8.0
- **Статус:** done
- **Связанные отчёты:** 0062 (хвосты 4.4), 0063 (город), 0064 (релиз 2.8.0); 0060 (повторное ревью 4.4, незакоммиченные правки 4.4)

## Задача
Ревью фазы 4.5: генератор `tools/gen_city.py`, `City.java`, `CityTest`, изменения `Dungeons`, `DungeonGuard`, `Dims`, `Ticker`, `RiftStaff`, `SealShardItem`, `Loot`, lang. Отдельно проверить уборку сущностей, производительность постановки, защиту города, Печать Храма, туман, совместимость с Разломом и старыми мирами, сборку lang. Выдать итоговые git-команды фаз 4.4 и 4.5 поверх HEAD `ee28f20`.

## Вердикт: **REQUEST CHANGES**
Код аккуратный, сборка чистая, Разлом не задет. Обе проблемы major — в правилах игры, а не в стабильности:
1. ворота Города обходятся почти даром, поэтому Осколок Сна (критерий фазы) ничего не запирает;
2. в уже исследованном мире город может прорезать постройки игрока.

Исправления небольшие (см. ниже). После них хватит повторного ревью только этих пунктов.

## Что сделано
- Прочитаны 0060–0064, `git status`, `git diff` (40 файлов), новые `City.java`, части `gen_city.py` (основание, стена, ворота). В `Dungeons.java` прочитаны `Def.ground/avoiding`, `site/site0/clashes`, `register/variation`, `schedule`, `Placing`, `inDungeon`, `lootFor`, `spawnHall`, `onRightClick/openDoor`, `clearState`, `tick0`, `removeStrays`, `purgeFalling`. Также `DungeonGuard.java` целиком, `DungeonData.Rec.contains/checkLayouts/reset`, `SealShardItem`, `Loot.chest`, `Dims.evacuateOldArena`, `RiftStaff.crack`.
- Lang: ru 96 и en 96, наборы ключей совпадают. Все ключи из `City.java`/`Dungeons.java` (`city.fog_*`, `dungeon.locked.sleep`, `dungeon.locked.sleep_temple`, `dungeon.locked.rift`, `dungeon.building`, `dungeon.sleep`) есть в обоих файлах. В jar оба lang лежат как валидный UTF-8 JSON (96 ключей).
- Jar `dist/explosionwand-1.20.1-2.8.0.jar`: есть `sleep_{north,mid,south}.nbt`, модели и текстуры `temple_seal`, `sleep_seal_shard`, `rift_staff`, класс `City.class`.
- Новые SRG проверены `lookup.py --srg`: `m_45527_` → `BlockRenderView.isSkyVisible` (Mojang `BlockAndTintGetter.canSeeSky`; заглушка в `LevelReader`, это его подынтерфейс, вызов разрешится), `m_8961_` → `getSpawnPointPosition`, `m_8963_` → `getSpawnPointDimension`, `m_220360_` → `World.getSpawnPos`, `m_7949_` → `BlockPos.toImmutable`. Всё совпадает с заглушками.

## Замечания

### major
1. **[major] `tools/gen_city.py:47,67,77-80` + `DungeonGuard.onPlace:37-40`: ворота обходятся без Осколка Сна.** Маска города — это `±48` (ров) по горизонтали и воздух до `y=20`. Стена по `|x|,|z| = 43..45`, высота 10 с зубцами. За `|x| > 48` ставить можно, поэтому хватает столба из ~11 блоков у внешнего края рва и прыжка через 3 блока рва на стену. Затем спрыгнуть на улицу (~7 урона). Склеп с Печатью Храма тоже внутри стены, значит без Осколка и без победы над Сердцем доступен весь город, а в 4.7 и Монолит. Элитры над `y>20` гаснут только у маски, после чего игрок просто падает внутрь. В 0063 это записано как «открытый вопрос», но на деле обход дешёвый и ломает прогресс. → Самое надёжное и дешёвое: в `City.tick` (и так раз в 10 тиков считает `inTown`) не творческого игрока внутри стены при закрытых воротах (блок в метке `door a=sleep` ещё не воздух, или флаг в `Rec.opened`) переносить к метке `start` с сообщением в actionbar (ru/en). Расширять маску не стоит: дорого и всё равно обходится.
2. **[major] `Dungeons.register:659-679` / `discover`: в старом или уже обжитом мире город встаёт поверх построек игрока.** `discover` регистрирует клетку, когда игрок в 80 блоках от точки, в том числе в давно исследованной местности. `base()` заливает воздухом коробку 97×21×97 и ставит фундамент. Дом игрока в этой рамке пропадает без следа и без отката. У Разлома так же, но там над землёй только маленький вход, а здесь ~200 тыс. клеток на поверхности. Большинство миров пользователя созданы до 2.8.0. → В `register` (для `d.flat > 0`) пропускать клетку (`data.dead`), если чанк в 9 точках `variation` обжит: `ChunkAccess.getInhabitedTime()` больше порога, например 20·60·5 тиков. SRG найти через `lookup.py`, заглушку дописать. Либо хотя бы явно предупредить в `docs/history.md` 2.8.0 и в «Проверить в игре».

### minor
3. **[minor] `Dungeons.java:226-231` (`SLEEP.ground(5, 12)`) vs `gen_city.py:47` (`FOUND = -10`): предел перепада 12 больше глубины фундамента 10.** Перепад меряется по 9 точкам на `±42`, а ров (`±48`) не проверяется. На склоне край стены и ров могут висеть над землёй на 2–8 блоков, а порог ворот оказаться высоко над внешней землёй (подъёма нет). → `maxVar` ≤ 8, или фундамент колоннами до земли (в шаблоне нельзя: глубина неизвестна), или точки замера на `±48`.
4. **[minor] `City.java` / `Dungeons.java:930` + `extra(21)`: Печать Храма одна на город, повторной выдачи нет.** Дубликаты не страшны: дверь не расходует Печать, а одна Печать открывает храмы всех городов. Открытая дверь — это воздух в мире, после перезапуска она остаётся открытой. Но если Печать потеряна до открытия двери (лава, кактус, исчезновение через 5 минут после смерти), храм города закрыт навсегда, а в 4.7 там босс. Уязвимое окно короткое (склеп → храм внутри одного города), поэтому minor. → В 4.7 (или сейчас): ПКМ по саркофагу в зачищенном склепе выдаёт Печать, если у игрока её нет и дверь храма ещё закрыта.
5. **[minor] `Dungeons.variation:682-695`: синхронная генерация чанков.** `m_6325_` вызывается для 9 точек × до 8 запасных мест. Точки бывают в 80 + 48 + 42 ≈ 170 блоках от игрока, то есть дальше дистанции прорисовки 10 чанков. Такие чанки генерируются в главном потоке, один раз на город получается рывок в сотни мс. → Перед `m_6325_` проверять `m_46805_` (`hasChunkAt`, уже есть в заглушках). Если чанк не загружен, отложить регистрацию клетки до следующего прохода `discover`.
6. **[minor] `Dungeons.java:810-812` + `City.stub` (вексы `dreamer`/`bookguard`): уход за рамку зала засчитывает зал.** Вексы летают сквозь стены. Когда они выходят за зал + 24, зал засчитывается: библиотека (`seal`) открывается, сундук появляется, а вексы с тегом зала остаются жить в городе (`removeStrays` убирает только дальше 24 от рамки данжа). Записано в 0063, это заглушки до 4.6. → Пока поставить `NoGravity`/поводок не нужно. В 4.6 настоящие мобы на хостах с поводком (как `RiftMobs`) закроют пункт. Записать в задачи 4.6.

### nit
7. **[nit] `City.stage:33` + `SAMPLE=10`:** пороги сравниваются через `>`, а счётчик растёт шагом 10. Поэтому замедление наступает на 3,5 с, слепота на 6,5 с, предупреждение на 2,5 с, а в отчёте и lang заявлено «3 с / 6 с». → `>=` или отметить в docs.
8. **[nit] `RiftStaff.java:63`:** в одной строке слиплись два комментария. К тому же ветка `inDungeon == true` недостижима: `RiftStaffItem` есть в `DungeonGuard.forbidden`, и в маске данжа посох не срабатывает. → Убрать лишний комментарий. Условие оставить как страховку или упростить до `if (e.m_19880_().contains("ewd")) continue;`.
9. **[nit] `docs/history.md:13`:** фраза «ядро столба не ломается взрывом без дропа и ставится воздухом» читается наоборот. → «ядро столба от взрыва исчезает без дропа».
10. **[nit] `Dims.evacuateOldArena:82-92`:** `safeY` по координатам кровати выводит игрока на крышу дома, а не к кровати. Для разового спасения из старого `arena` это допустимо.
11. **[nit] `DungeonData` `int hallsSpawned/hallsCleared`:** битовые маски `1 << id` держат максимум 31 зал. В Городе 25 из «30» номеров. → Если в 4.6–4.8 залов станет больше 31, перейти на `long` (как `partsDone`).

### Проверено и в порядке
- **Правила кода:** enum через `valueOf` (`MOTION_BLOCKING_NO_LEAVES`, `PEACEFUL`, `MAIN_HAND`). Частицы, звуки и блоки через `Fx` (`cloud`, `soul`, `poof`, `air`). Summon, effect и `item replace` через `Cmd`. Массовая постановка через `Dungeons.state` + `m_7731_`, ID проверяет `ids.py` (0 проблем). ХП заглушек ≤ 30, лимит 1024 не задет. Прямых ссылок на `Blocks`/`SoundEvents` нет.
- **Исключения в тике:** `City.tick` вызывается внутри `Dungeons.tick0`, его ловит общий `try` в `Dungeons.tick` (`warned`). NPE нет: `rec.def()` проверяется на `null` (`d != SLEEP` при `null` даёт `continue`), `Dims.get(...)` в `evacuateOldArena` проверяется на `null`.
- **Туман:** только сервер. Частицы `sendTo` идут только этому игроку, эффекты `effect give <uuid> … true` длятся 2 с и сами спадают за пределами тумана. `TRACK` чистится `retainAll(seen)` на каждом проходе и в `clearState` (выход в меню). Утечки нет. Творческий режим, наблюдатель и мёртвые пропускаются. Туман включается только после полной достройки.
- **Уборка заглушек:** обычный путь залов `alive` → `RiftMobs.remove`. Учтены мирный режим, `hallStale`, `purgeUnbuilt` при смене раскладки и `removeStrays`. Summon с NBT не вызывает `finalizeSpawn`, поэтому без детёнышей, жокеев и случайного снаряжения. `DeathLootTable:"minecraft:empty"`, шансы выпадения 0. `PersistenceRequired`. У векса из `/summon` нет `LifeTicks`, сам не исчезает, но уборка его покрывает (кроме п. 6).
- **Производительность:** 281 тыс. блоков при `BUDGET 1500` займут ~190 тиков (~10 с) плюс `settle` по оболочке. Флаг 18 ставит блоки без обновления соседей, `purgeFalling` убирает песок, осыпавшийся в ров. `site` с обходом даёт ≤ 32 × 9 вызовов `site0`, это чистая арифметика. `City.tick` раз в 10 тиков: один `canSeeSky` на игрока в городе.
- **Защита:** весь путь `DungeonGuard` работает по маске, а в маске город с рвом и воздухом до +20. Ломание, постановка, вёдра (запас 1), взрывы, жемчуг и хорус (по точке «здесь»/«там»), палочки из `forbidden`, элитры, естественный спавн — всё действует. Обход см. п. 1.
- **Совместимость с Разломом:** у `site0` при `salt = 0` хеш тот же, что раньше, поэтому места Разломов не сдвинулись. Шаблоны Разлома байт в байт (`gen_dungeons --check`). Подпись раскладки Разлома не изменилась, перестройки в мирах 2.7.0 не будет. Город обходит Разломы по `site(…, 0)` с запасом 52 ≥ ±48 запасных точек Разлома (тест `cityPlacement`, 40 сидов).
- **Старые миры (кроме п. 2):** записей `sleep` нет, они появятся при исследовании. Игроков из `arena` переносит `evacuateOldArena`, JSON измерения на месте.
- **Двери:** сообщение «заперто» по `m.a`, у Разлома `door a=rift`, ключ `locked.rift` прежний. `holdsShard` сравнивает `dungeon` предмета с `m.a`. Печать Храма (`doorOnly`) без указателя, `m_7203_` не машет рукой, двери отпирает серверный `RightClickBlock`. `Loot.chest` с NBT: число впереди отделяется только если это цифры, пробелы внутри NBT допустимы. В текстах книг нет `'` и `"` (проверяет `CityTest`).
- **Баланс по заявленному:** туман 3/6 с (см. nit 7), замедление II (`amplifier 1`), слепота I. Страж Шва 30 ХП и железный меч. 78 мобов и 14 сундуков, по 0063 и тестам.

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `reports/0065-2026-10-19-code-reviewer-phase-4-5.md` | этот отчёт (код не менялся) |

## Проверка
- `python tools/build.py` (запущен сам): 125 классов, SRG исходники 225 / байткод 226, в joined.tsrg не найдено 0, проблем ID 0, `gen_dungeons` 7 файлов / 0 проблем. Тесты BossRiftTest, CityTest, DungeonTest (312 871 блок, прохождение по этапам), MazeTest, RiftMobsTest, RiftStaffTest — OK. Jar `dist/explosionwand-1.20.1-2.8.0.jar` (1058 КБ).
- Новые SRG-имена: `m_45527_`, `m_8961_`, `m_8963_`, `m_220360_`, `m_7949_`, все проверены (см. выше).

## Проверить в игре
- **Обход ворот (п. 1):** без Осколка поставить столб у края рва и прыгнуть на стену. Сейчас это получится, после правки игрока должно вернуть к воротам.
- **Старый мир (п. 2):** открыть мир 2.7.0 с постройками и подойти к месту города (Shift+ПКМ Осколком Сна в творческом показывает координаты). Посмотреть, что стало с постройками.
- **Склон:** город на неровном месте (перепад 8–12). Висит ли фундамент у стены и рва, можно ли дойти до порога ворот.
- **Река или озеро у рва:** заливает ли вода ров и улицы через открытые ворота после `settle` (вёдра в маске запрещены, убрать воду игрок не сможет).
- **Рывок при регистрации:** подойти к далёкому неисследованному месту, нет ли подвисания на 0,5–1 с при появлении записи.
- **Туман:** 3–3,5 с до замедления, 6–6,5 с до слепоты. Эффекты сходят за 2 с после захода в дом. На крышах домов туман есть, в склепе и храме нет.
- **Вексы-заглушки:** выходят ли из библиотеки сквозь стены и открывается ли её печать раньше времени.
- **Печать Храма:** ПКМ по двери храма открывает её. После перезапуска дверь открыта. Вторая Печать (из другого города) тоже открывает.
- Всё из «Проверить в игре» отчётов 0062 и 0063.

## Решения и почему
- Обход ворот — major, а не открытый вопрос: он стоит ~11 блоков и одного прыжка и снимает требование Осколка Сна, которое входит в критерий фазы («ворота открываются Осколком Сна»).
- Утрата построек в старых мирах — major: потеря данных игрока без отката, а большинство миров пользователя созданы до 2.8.0.
- Вексы-заглушки (п. 6) — minor: это временные мобы до 4.6.

## Проблемы и открытые вопросы
- Общие файлы (`Dungeons.java`, `DungeonGuard.java`, `Dims.java`, `Ticker.java`, `SealShardItem.java`, `ExplosionWandMod.java`, JEI, lang, `gen_dungeons.py`, `DungeonTest.java`, docs) несут правки и 4.4, и 4.5. Без `git add -p` (интерактив, не используем) их не разделить. Поэтому коммит 2 ниже по отдельности **не собирается**: `Boss.java` ссылается на `Dungeons.lairWon/clearable`, которые придут коммитом 3. Вся цепочка собирается после коммита 3. Если важна сборка каждого коммита, объединить 2 и 3 в один.
- `arcane_helmet.json`/`arcane_leggings.json` отличаются от HEAD только концами строк (см. 0060). Если git не увидит изменений, они просто не войдут в коммит.

## Git (выполняет пользователь)
Поверх HEAD `ee28f20`, по порядку, в PowerShell. `ROADMAP.md` не включён. Если major 1–2 будут исправляться, правки и их отчёт добавить к коммиту 3 (или отдельным `fix(dungeon)` после него), а этот отчёт оставить в коммите 4.
```
git add tools/ids.py tools/build.py reports/0052-2026-10-19-build-engineer-ids-jar-cleanup.md reports/0059-2026-10-19-build-engineer-ids-particles-fix.md
git commit -m "build(build): ids.py видит sound(), частицы и Kind; mech_test вне jar" -m "- проверка голых sound() и хостов RiftModels.Kind (только в RiftModels.java)" -m "- вернута проверка частиц (кортеж выпадал в комментарий)" -m "- mech_test.nbt исключён из jar, тесты берут его из resources/"

git rm src/com/explosionwand/Arena.java src/com/explosionwand/RiftKeyItem.java resources/assets/explosionwand/models/item/rift_key.json resources/assets/explosionwand/textures/item/rift_key.png resources/data/explosionwand/recipes/rift_key.json
git add src/com/explosionwand/Boss.java src/com/explosionwand/RiftMobs.java src/com/explosionwand/RiftLogic.java src/com/explosionwand/ArenaGuard.java src/com/explosionwand/Altar.java src/com/explosionwand/EclipseSealItem.java src/com/explosionwand/WhaleStaff.java src/com/explosionwand/RiftStaff.java src/com/explosionwand/RiftStaffItem.java stubs/net/minecraft/world/entity/Entity.java stubs/net/minecraft/world/damagesource/DamageSources.java stubs/net/minecraft/world/entity/animal/TamableAnimal.java stubs/net/minecraft/world/entity/npc/AbstractVillager.java resources/data/explosionwand/structures/rift_entrance.nbt resources/data/explosionwand/structures/rift_mid.nbt resources/data/explosionwand/structures/rift_deep.nbt resources/data/explosionwand/recipes/arcane_helmet.json resources/data/explosionwand/recipes/arcane_leggings.json resources/assets/explosionwand/models/item/rift_staff.json resources/assets/explosionwand/textures/item/rift_staff.png resources/assets/explosionwand/models/item/sleep_seal_shard.json resources/assets/explosionwand/textures/item/sleep_seal_shard.png tests/BossRiftTest.java tests/RiftMobsTest.java tests/RiftStaffTest.java reports/0051-2026-10-19-mod-engineer-rift-side-rooms-shrink.md reports/0053-2026-10-19-mod-engineer-rift-staff-sleep-shard.md reports/0054-2026-10-19-boss-engineer-rift-heart-lair.md reports/0057-2026-10-19-boss-engineer-review-fixes-4-4.md reports/0058-2026-10-19-mod-engineer-rift-staff-review-fixes.md
git commit -m "feat(boss): Сердце Разлома в логове, Посох Разлома" -m "- зал 7: босс просыпается при входе, зачистка только победой, добыча: посох, ядро, осколок Сна" -m "- Посох Разлома: трещина (indirectMagic, щадит жителей и питомцев) и шаг на 8 блоков" -m "- комнаты 8-16 меньше, раскладка Разлома 4" -m "- убраны Ключ Разлома, алтарь и арена Сердца; JSON измерения arena оставлены" -m "- общая часть (Dungeons, DungeonGuard, Dims, Ticker, lang) - в следующем коммите"

git add tools/gen_city.py tools/gen_dungeons.py resources/data/explosionwand/structures/sleep_north.nbt resources/data/explosionwand/structures/sleep_mid.nbt resources/data/explosionwand/structures/sleep_south.nbt src/com/explosionwand/City.java src/com/explosionwand/Dungeons.java src/com/explosionwand/DungeonGuard.java src/com/explosionwand/Dims.java src/com/explosionwand/Ticker.java src/com/explosionwand/SealShardItem.java src/com/explosionwand/Loot.java src/com/explosionwand/ExplosionWandMod.java src/com/explosionwand/jei/ExplosionWandJei.java stubs/net/minecraft/server/level/ServerPlayer.java stubs/net/minecraft/world/level/Level.java stubs/net/minecraft/world/level/LevelReader.java stubs/net/minecraft/core/BlockPos.java resources/assets/explosionwand/models/item/temple_seal.json resources/assets/explosionwand/textures/item/temple_seal.png resources/assets/explosionwand/lang/ru_ru.json resources/assets/explosionwand/lang/en_us.json tests/CityTest.java tests/DungeonTest.java reports/0062-2026-10-19-mod-engineer-review-nits-4-5.md reports/0063-2026-10-19-mod-engineer-sleeper-city.md
git commit -m "feat(dungeon): Город Спящих, логово босса в конвейере данжей" -m "- наземный город 97x97: стена, 20 домов, библиотека-ловушка, склеп, храм-заглушка со шпилем" -m "- ворота - Осколок Сна, дверь храма - Печать Храма из склепа; подсказки и тайники" -m "- сонный туман на улицах, мобы-заглушки, особая добыча сундуков" -m "- Def.ground/avoiding: ровное место и обход Разломов" -m "- конвейер: метка boss, lairWon, Boss.tickAll; защита ядра столба от взрывов" -m "- хвосты 4.4: выход из старого arena, достройка при входе, трещина снаружи"

git add resources/META-INF/mods.toml docs/history.md docs/world.md docs/dungeons.md reports/0055-2026-10-19-asset-engineer-release-2-7-0.md reports/0056-2026-10-19-code-reviewer-phase-4-4.md reports/0060-2026-10-19-code-reviewer-phase-4-4-recheck.md reports/0064-2026-10-19-asset-engineer-release-2-8-0.md reports/0065-2026-10-19-code-reviewer-phase-4-5.md
git commit -m "docs(dungeon): релизы 2.7.0 и 2.8.0, ревью фаз 4.4 и 4.5" -m "- версия 2.8.0 в mods.toml; history 2.6.2, 2.7.0, 2.8.0" -m "- world.md и dungeons.md: логово Сердца, Город Спящих, наземные данжи" -m "- отчёты ревью 0056, 0060, 0065"
```

## Следующий шаг
- **mod-engineer:** major 1 (возврат к воротам из `City.tick`, lang ru/en, тест в `CityTest`), major 2 (`getInhabitedTime` в `register` или предупреждение в history), minor 3 и 5. Nit 7–9 заодно.
- **code-reviewer:** повторное ревью только этих пунктов.
- **В 4.6:** minor 6 (настоящие мобы с поводком вместо вексов). **В 4.7:** minor 4 (повторная выдача Печати Храма). Nit 11 — когда залов станет больше 31.
