# 0056 · Ревью фазы 4.4 (2.6.2 + 2.7.0)

- **Дата:** 2026-10-19
- **Агент:** code-reviewer
- **Версия мода:** 2.7.0
- **Статус:** done
- **Связанные отчёты:** 0051, 0052, 0053, 0054, 0055; переносы из 0045, 0048, 0036, 0039

## Задача
Ревью фазы 4.4 перед коммитом. Что входит: уменьшенные комнаты Разлома (2.6.2), правки `ids.py`/`build.py`, Посох Разлома и Осколок Печати: Сон, Сердце Разлома в зале 7, удаление арены, Алтаря и Ключа Разлома, layout 4, релиз 2.7.0. Отдельно: можно ли коммитить раздельно по отчётам.

## Вердикт: **REQUEST CHANGES**
1 blocker, 3 major. Главное: логово засчитывается зачищенным через 2 секунды после входа, ещё до появления босса. Сундук с Посохом выдаётся без боя.

## Что сделано
- Прочитаны отчёты 0051–0055, `git status` и `git diff`. Полностью прочитаны `Boss.java`, `RiftStaff.java`, `RiftStaffItem.java`, а также изменённые места в `Dungeons`, `DungeonGuard`, `Dims`, `Altar`, `ArenaGuard`, `Ticker`, `SealShardItem`, `RiftMobs`, `RiftLogic`, `ids.py`, `build.py` и генератор зала 7.
- `python tools/build.py`: 122 класса, SRG 217/218, в joined.tsrg не найдено 0, проблем ID 0, gen_dungeons 4/0. BossRiftTest, DungeonTest, MazeTest, RiftMobsTest проходят. Собран jar `dist/explosionwand-1.20.1-2.7.0.jar` (787 КБ).
- Новое SRG-имя `m_20049_`: `lookup.py --srg` даёт `Entity.addCommandTag(String)Z`, совпадает с заглушкой и использованием.
- Lang: ключи ru и en совпадают (разница 0). Модели `rift_staff` (handheld) и `sleep_seal_shard` (generated) на месте, PNG 16x16 валидны. Ссылок на `RiftKey`, `rift_key`, `Dims.ARENA`, `Arena.` и `explosionwand:arena` в `src/`, `tests/`, `tools/`, `resources/` не осталось.
- Частицы проверены вручную: регулярка частиц пропала из `ids.py` (major 2). Все `Fx.particle("…")` в `src/` есть в реестре 1.20.1.

## Замечания

### blocker
1. **[blocker] `Dungeons.java:705` + `Boss.java:256`: логово засчитывается до появления босса.** `spawnHall` для зала с боссом только вызывает `Boss.wake`, а хост призывается через 60 тиков (`stage -1`, `t >= 60`). Зачистку `inDungeon` проверяет каждые 10 тиков с условием `tk - SPAWNED_AT > 30 && alive(...).isEmpty()`. Значит, через 40 тиков после входа, пока игрок ещё в зале, `alive` пуст. Зал помечается зачищенным, `onCleared` ставит сундук с Посохом Разлома, Ядром и Осколком Сна, а `Boss.tick` на следующем тике видит `hallsCleared` и всё убирает. Босса нет, добыча бесплатная.
   То же после перезапуска мира, если выйти во время пробуждения (хоста ещё нет). Достаточно стоять в любой части данжа с загруженными чанками зала: `orphans` срабатывает только в 60 блоках от логова.
   → Логово засчитывать только после настоящей победы. В `inDungeon` для `h.boss != null` проверять `Boss.DONE.contains(rec.key)` (его ставит `dying()` при `dyingT == 90`) вместе с `alive(...).isEmpty()`. Лучше вынести решение в чистую функцию (`hasBoss`, `bossDone`, `aliveEmpty`, `waited`) и покрыть её тестом в `BossRiftTest`: «пробуждение без хоста не засчитывает зал».

### major
2. **[major] `tools/ids.py:7`: проверка частиц выпала.** Комментарий `# RiftModels.Kind(id, хост, ...)` стоит перед кортежем `('particle_type', r'Fx\.particle…')`, и Python считает этот кортеж частью комментария. `ids.CHECKS` теперь без `particle_type`: проверено импортом, `any(k == 'particle_type') == False`. Ошибка в ID частицы вернёт `null` из реестра уже в игре. Сейчас все ID верны (проверено вручную).
   → Вернуть кортеж частиц на отдельную строку, комментарий поставить после него. Отчёт 0052 «проверки на прежние регулярки не влияют» неверен.
3. **[major] `RiftStaff.java:84` + `DungeonGuard.java:94-96`: Shift-шаг заводит в данж снаружи.** `onUseItem` проверяет только позицию игрока, а не точку назначения. `step` переносит на 8 блоков сквозь стены через `m_6021_`, и `EntityTeleportEvent` при этом не срабатывает. Игрок, вплотную подкопавшийся к Разлому снаружи (вне маски данжа землю ломать можно), шагает внутрь: в зал за печатью, в логово, в секретные комнаты 14–16 без рычага. Жемчуг и хорус так не могут: там проверяется `there`. Это противоречит правилу «Shift-шаг не в данжах».
   → В `step` пропускать точки, где `Dungeons.at(l, x, y, z) != null`. Желательно проверить и промежуточные точки линии (`Dungeons.near(..., 1)`), чтобы шаг не прошёл сквозь угол данжа.
4. **[major] Удалено измерение `arena` (`dimension/arena.json`, `dimension_type/arena.json`, `worldgen/biome/arena.json`): риск для старых миров.** Измерения из датапака мода были у каждого мира с 2.6.x и раньше. 1.20.1 хранит набор измерений в `level.dat`, а запись `explosionwand:arena` ссылается на тип и биом, которых больше нет. Не проверено, что такой мир открывается без экрана «ошибки датапака / безопасный режим» и без потери измерений Затмения, Сна и Кита. Утверждение 0054 «игроки внутри перенесутся в обычный мир» тоже не проверено. Из чтения кода это не решить.
   → До релиза открыть в игре мир, созданный на 2.6.x. Самая дешёвая страховка: вернуть три JSON как есть, без кода (`Dims.ARENA` не нужен, измерение просто пустует). Удалить их позже, отдельным решением.

### minor
5. **[minor] `Boss.java:235, 240, 534, 567, 577, 622-623, 686, 726-727, 762`, `RiftStaff.java:93`: тексты на экране зашиты по-русски.** Это название полоски, заголовки фаз «Щит Разлома» и «Ярость Сердца», actionbar-подсказки, «Разлому некуда вести». В `en_us` игрок видит русский, а CLAUDE.md требует оба языка. Раньше в старом Boss было так же, но заголовки фаз добавлены заново.
   → JSON-компоненты `{"translate":"explosionwand.boss.rift_heart.shield"}` и т.п. плюс ключи в обоих lang. Для `EclipseStaff.bar` — вариант с ключом.
6. **[minor] `Boss.java:235` / `orphans`: полоска переживает перезапуск.** Пользовательские bossbar хранятся в `level.dat` вместе со списком игроков. Если сохраниться посреди боя и после перезапуска не подходить к логову ближе чем на 60 блоков, полоска «Сердце Разлома» висит у игрока везде. `orphans` убирает её только рядом с залом.
   → На старте сервера (или в `Boss.clear`/первом тике) снимать все `explosionwand:rift_heart_*`. Либо в `orphans` для записей с `hallsSpawned` без активного боя делать `bossbar remove` без условия «игрок рядом» (команда дешёвая, раз в 20 тиков на запись).
7. **[minor] `RiftStaff.java:68`: трещина бьёт `magic()` без атакующего.** Моб, убитый посохом, не даёт опыта и не засчитывается игроку (`lastHurtByPlayer` пуст). Под удар и подтягивание попадают и прирученные питомцы самого игрока, и жители.
   → `indirectMagic(p, p)` (SRG проверить через lookup). Пропускать `TamableAnimal`, у которых хозяин — `p` (по желанию и жителей).

### nit
8. **[nit] `Boss.java:183-184`: столбы убираются только вместе с остатками сущностей.** `orphans` вызывает `reset`, а значит и `clearPylons`, только при `junk`. Если сущностей не осталось, а блоки столбов остались (например, хост убит `/kill` в обход onHurt, или босс выгрузился, а столбы стоят), обсидиан с аметистом остаются. → Вызывать `reset` для `spawned && !cleared` без условия `junk`: `cleanup` безопасен и на пустом зале.
9. **[nit] `Boss.DONE` не сохраняется.** Если перезапуск придётся ровно между `purge` и отметкой зачистки (≤10 тиков) или все игроки покинут данж в эти тики, зал сбросится и бой начнётся снова. Добыча не теряется, просто придётся драться ещё раз. → Допустимо. После исправления blocker 1 можно отмечать `hallsCleared` прямо из `dying()`.
10. **[nit] Отчёт 0054: «`/kill` хоста тоже ведёт к гибели».** `LivingEntity.kill` идёт через `hurt(genericKill)`, а `onHurt` либо отменяет урон (щит, пробуждение), либо обрезает его до порога фазы. Гибель от `/kill` наступит только в ярости. → Проверить в игре или поправить формулировку.
11. **[nit] `Altar.java:22`: конструктор с `boolean eclipse` по-прежнему даёт `kind 0`.** Тогда пурпурная палитра «Разлома» в `tick`, а портал ведёт в Затмение (ветка `default`). Вызовов с `false` нет. → Убрать конструктор с `boolean` (`EclipseSealItem` передаёт `1`) и ветку палитры `kind 0`.
12. **[nit] Ядра столбов дают блок аметиста.** Если сбросить бой и разбудить зал заново, 4 блока за раз. Мелочь, но это фарм. → При желании ставить ядро, которое ничего не роняет, или убирать дроп в `BreakEvent` (`setExpToDrop`/замена на воздух через `m_7731_` с отменой).
13. **[nit] `docs/history.md`: раздел 2.6.2 описывает версию, которая отдельным jar не выходила** (шаблоны 2.6.2 перегенерированы в 0054). → Пометить «(в составе 2.7.0)» или оставить как есть.
14. **[nit] Отчёт 0052, «Проблемы»:** регулярка хостов `Kind` проверяет любой enum вида `ИМЯ("a", "b"…` как entity_type. Пока ложных срабатываний нет. → Ограничить файлом `RiftModels.java` (как `SKIP`, но по имени файла).

### Что проверено и в порядке
- **Уборка сущностей босса.** `purge` убирает хост и части по `ktag` в `hallBox`, приспешников — через `RiftMobs.remove`. Исключение в `Boss.tick` убирает бой, `tickAll` пробрасывает его в `try` из `Dungeons.tick0`, и до тика мира оно не доходит. Мирный режим: `Dungeons:687` (`reset`), ванильный деспавн гиганта даёт `host.m_213877_()` → `cleanup`. Игроки умерли или вышли: `hallStale` по коробке без запаса, через 30 с `reset` (minor 1 из 0039 закрыт). Выход в меню: `Boss.clear` в `clearState`, после загрузки `orphans` убирает остатки и снимает «разбужен». Смена раскладки: `Boss.tick` сверяет биты зала, остатки убирают `orphans`/`purgeUnbuilt`.
- **Гонка `DONE` / `orphans`.** `DONE` ставится до `return true`, `orphans` пропускает запись, пока зал не засчитан или не сброшен. После `wake` `DONE` снимается. Конфликтов итераторов нет: `wake`/`reset` вызываются из `inDungeon`, не во время обхода `BOSSES`.
- **`clampHit`.** Пороги 540/270/1 не перескакиваются, щит и ярость включаются в следующем тике. Индексы `volleyTargets` согласованы с `volleyCount`, `ps` не пуст. ХП хоста 900 ≤ 1024.
- **DungeonGuard.** Ломать и взрывать можно только ядро активного столба (`isPylonCore`: `shield`, `pylonAlive`, `floor+2`, x/z столба). Ставить блоки в данже нельзя. `RiftStaffItem` есть в `forbidden`.
- **Посох Разлома.** Трещина пропускает игроков и всё с тегами `ew*` (`Minions.controllable`; приспешники `ewb_min` считаются обычными мобами, так и задумано). Шаг проверяет загруженность чанка, высоту мира, ноги и голову в воздухе. Кулдаун умножается на `ArcaneSet.cd`. Тексты и описания в обоих lang.
- **Осколок Сна.** `locked()` по `Dungeons.def("sleep") == null`, без указателя и без NPE. Описание берётся из `key`.
- **Удаление Алтаря Сердца, Ключа и Arena.** Висячих ссылок в коде и ресурсах нет. `Dims.ARENA_FLOOR = 64` совпадает с прежним `Arena.FLOOR`. `ArenaGuard` вернулся к `clear()` для своих измерений. `WhaleStaff.NOT_STAFF` без `rift_key`. Ключ в инвентарях старых миров пропадёт как неизвестный предмет, это ожидаемо (записано в history).
- **Layout 4.** Записи v3 сбрасываются, тесты на 77 мобов и метку `boss` проходят. Пол бездны (`low=5`) входит в коробку зала, поэтому сброс по коробке без запаса не срабатывает на игроке, упавшем в бездну.
- **Правила кода.** Новые enum через `valueOf` (`Difficulty.valueOf("HARD")`, `ChatFormatting.valueOf`). Частицы, звуки и блоки идут через `Fx`. Summon, bossbar, title, data merge — через `Cmd`. Облик из `block_display` на гиганте. Столбы ставятся `m_7731_` (простые блоки без свойств, только в пустые клетки шаблона, это проверяет `BossRiftTest`).
- **Перенос nit.** 8, 12, 15, 16 (0045/0048) и 11 (0045), 13 (0036) закрыты. Поводок 22 < ALIVE_PAD, `interpolation_duration = every`, `GOLEM_FIST_R` — единый радиус, `capHit` ≤ 14 на Hard.

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `reports/0056-2026-10-19-code-reviewer-phase-4-4.md` | этот отчёт (код не менялся) |

## Проверка
- `python tools/build.py`: 122 класса, SRG 217/218, неизвестных 0, проблем ID 0 (без проверки частиц, см. major 2; частицы проверены вручную), тесты OK, jar 2.7.0.
- Новое SRG-имя: `m_20049_` → `Entity.addCommandTag(String)` (`lookup.py --srg`).
- Баланс: ХП 900, осколки 6/8, шипы 7, луч 6, волна 7, кулдауны залпа 70/55/38 — в коде так же, как в 0054. Посох: 6 урона, ширина 2,6, дальность 14, кулдауны 100/80 — так же, как в 0053.

## Проверить в игре
К списку 0054 и 0053 после исправлений:
- Вход в зал 7: сундук **не** появляется, пока жив босс (blocker 1). Выйти в меню во время сердцебиения (первые 3 с), загрузиться, постоять у входа в Разлом: зал не засчитан, босс просыпается при новом входе.
- Мир, созданный на 2.6.x (с измерением `arena`), открывается без экрана ошибок датапака. Измерения Затмения, Сна и Кита на месте (major 4).
- Shift+ПКМ Посохом Разлома снаружи у стены Разлома (подкоп) не переносит внутрь (major 3).
- После перезапуска посреди боя и ухода от логова полоски «Сердце Разлома» нет (minor 6).
- В `en_us` заголовки фаз и подсказки босса на английском (если сделан minor 5).

## Решения и почему
- Blocker 1 — неверная логика с прямым эксплойтом добычи, который сработает у каждого игрока при первом входе.
- Major 4 — не доказанная ошибка, а непроверенный риск сломать все старые миры. Страховка (три JSON) стоит ноль строк кода.

## Проблемы и открытые вопросы

### Git: можно ли коммитить раздельно по отчётам
**Нельзя.** Почему:
- Шаблоны `rift_*.nbt` перегенерированы дважды (0051 → layout 3, 0054 → layout 4). Состояния 2.6.2 в рабочей копии больше нет, и `git add -p` бинарные файлы не делит.
- `Dungeons.java`, `gen_dungeons.py`, `tests/DungeonTest.java`, `tests/RiftMobsTest.java`, `DungeonGuard.java`, `ExplosionWandMod.java`, lang, `mods.toml`, `docs/history.md`, `docs/dungeons.md` содержат правки сразу нескольких отчётов.
- Коммит «только 0053» не соберётся: `jei/ExplosionWandJei.java` и `DungeonGuard` ссылаются на `RIFT_STAFF`/`RiftStaffItem` в `ExplosionWandMod`, где заодно удалён `RIFT_KEY` (0054). Коммит «только 0054» без `RiftStaffItem` тоже не соберётся.

Независим только 0052 (`tools/ids.py`, `tools/build.py`). Его коммитить **после** исправления major 2.

Ниже объединённый набор поверх текущего HEAD (`d406854`, 4.2–4.3 уже в истории). Выполнять **после** исправлений по этому ревью. Отчёты шагов-исправлений (0057+) и все файлы, которые они тронут, добавить в соответствующий коммит. `ROADMAP.md` меняет main, `arcane_helmet.json`/`arcane_leggings.json` (только CRLF) не включать.

## Git (выполняет пользователь)
```
git add tools/ids.py tools/build.py reports/0052-2026-10-19-build-engineer-ids-jar-cleanup.md
git commit -m "build(build): ids.py видит sound() и хосты Kind, mech_test вне jar" -m "- проверка голых sound(`"…`") и хостов RiftModels.Kind по реестру" -m "- mech_test.nbt исключён из jar, тесты берут его из resources/"

git rm src/com/explosionwand/Arena.java src/com/explosionwand/RiftKeyItem.java resources/assets/explosionwand/models/item/rift_key.json resources/assets/explosionwand/textures/item/rift_key.png resources/data/explosionwand/recipes/rift_key.json resources/data/explosionwand/dimension/arena.json resources/data/explosionwand/dimension_type/arena.json resources/data/explosionwand/worldgen/biome/arena.json
git add src/com/explosionwand/Boss.java src/com/explosionwand/Dungeons.java src/com/explosionwand/DungeonGuard.java src/com/explosionwand/RiftMobs.java src/com/explosionwand/RiftLogic.java src/com/explosionwand/Dims.java src/com/explosionwand/Ticker.java src/com/explosionwand/ArenaGuard.java src/com/explosionwand/Altar.java src/com/explosionwand/ExplosionWandMod.java src/com/explosionwand/WhaleStaff.java src/com/explosionwand/RiftStaff.java src/com/explosionwand/RiftStaffItem.java src/com/explosionwand/SealShardItem.java src/com/explosionwand/jei/ExplosionWandJei.java stubs/net/minecraft/world/entity/Entity.java tools/gen_dungeons.py resources/data/explosionwand/structures/rift_entrance.nbt resources/data/explosionwand/structures/rift_mid.nbt resources/data/explosionwand/structures/rift_deep.nbt resources/assets/explosionwand/lang/ru_ru.json resources/assets/explosionwand/lang/en_us.json resources/assets/explosionwand/models/item/rift_staff.json resources/assets/explosionwand/models/item/sleep_seal_shard.json resources/assets/explosionwand/textures/item/rift_staff.png resources/assets/explosionwand/textures/item/sleep_seal_shard.png resources/META-INF/mods.toml tests/BossRiftTest.java tests/DungeonTest.java tests/RiftMobsTest.java reports/0051-2026-10-19-mod-engineer-rift-side-rooms-shrink.md reports/0053-2026-10-19-mod-engineer-rift-staff-sleep-shard.md reports/0054-2026-10-19-boss-engineer-rift-heart-lair.md
git commit -m "feat(dungeon): Сердце Разлома в логове, Посох Разлома, версия 2.7.0" -m "- зал 7: босс просыпается при входе, зачистка = победа, добыча: посох, ядро, осколок Сна" -m "- Посох Разлома: трещина и шаг на 8 блоков, в данжах молчит; Осколок Печати: Сон" -m "- комнаты 8-16 меньше, раскладка Разлома 4" -m "- убраны Ключ Разлома, алтарь Сердца, арена Сердца" -m "- nit 8, 12, 15, 16: потолок урона на Сложной, кулак голема, поводок, плавность облика"

git add docs/history.md docs/world.md docs/dungeons.md reports/0055-2026-10-19-asset-engineer-release-2-7-0.md reports/0056-2026-10-19-code-reviewer-phase-4-4.md
git commit -m "docs(dungeon): история 2.6.2 и 2.7.0, world.md и dungeons.md" -m "- логово Сердца, метка boss, раскладка 4" -m "- ревью фазы 4.4"
```
Примечания:
- Если по major 4 решат вернуть JSON измерения `arena`, убрать три пути `dimension/…`, `dimension_type/…`, `worldgen/biome/arena.json` из `git rm`.
- В PowerShell кавычки внутри `-m` экранируются обратной кавычкой, как в первой команде. Можно и просто убрать их из текста.

## Следующий шаг
- **boss-engineer:** blocker 1 (зачистка логова только после `DONE`, тест), minor 6 (полоска после перезапуска), nit 8–10.
- **mod-engineer:** major 3 (шаг Посоха в данж снаружи), minor 7 (`indirectMagic`, питомцы), minor 5 (lang для текстов босса и посоха, вместе с boss-engineer; ключи — asset-engineer), nit 11.
- **build-engineer:** major 2 (вернуть проверку частиц), nit 14; затем `build.py`.
- **main / пользователь:** major 4 — решить, вернуть ли JSON `arena`, или проверить старый мир в игре.
- Потом повторное ревью (code-reviewer).
- **Перенос в 4.5** (фазу не держат, если останутся после повторного ревью): minor 5–7, nit 8–14 из этого отчёта; из 0036 nit 14 («залы спят до полной достройки данжа»). nit 9 из 0045 (имена над хостами не видны) закрыт как «так и задумано».
