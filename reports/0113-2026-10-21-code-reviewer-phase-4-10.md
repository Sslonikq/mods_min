# 0113 · Ревью фазы 4.10 «Пепельный Феникс» (2.18.0)

- **Дата:** 2026-10-21
- **Агент:** code-reviewer
- **Версия мода:** 2.18.0
- **Статус:** done
- **Связанные отчёты:** 0109, 0110, 0111, 0112

## Задача
Ревью незакоммиченных изменений фазы 4.10 (отчёты 0109–0112): Boss5 (Пепельный Феникс), Посох и Перо Феникса, раскладка 2 Святилища, lang, версия 2.18.0. Код не менялся.

## Что сделано
- Прочитаны CLAUDE.md, отчёты 0109–0112, `git status` / `git diff` по всем изменённым и новым файлам.
- Построчно просмотрены `Boss5.java`, `PhoenixStaff.java`, `PhoenixStaffItem.java`, правки `Boss`, `Ash`, `Dungeons`, `DungeonMech`, `ExplosionWandMod`, `Ticker`, `ExplosionWandJei`, заглушки, `gen_ash.py`, `gen_dungeons.py`, документы.
- Сверены с кодом цифры баланса из 0110/0111, жизненный цикл (wake/reset/orphans/clear), сброс записей раскладки 1 (`DungeonData.checkLayouts` → `reset` обнуляет `hallsCleared` и `bossDead` до `migrateBossDead`).
- `python tools/build.py`.

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `reports/0113-2026-10-21-code-reviewer-phase-4-10.md` | этот отчёт |

## Проверка
- `python tools/build.py`: 166 классов, SRG 249 в исходниках / 250 в байткоде, неизвестных 0; linkcheck: 0 расхождений, 0 непроверенных; ID 0 проблем; `gen_dungeons` без расхождений; тесты OK: BossAshTest, BossCityTest, BossRiftTest, CityMobsTest, CityTest, DungeonTest, EarthTest, MatsTest, MazeTest, PhoenixStaffTest, RiftMobsTest, RiftStaffTest; jar `dist/explosionwand-1.20.1-2.18.0.jar`.
- Новое SRG одно: `m_19879_` → `lookup.py --srg`: `EntityLike.getId ()I`, верно. `getPersistentData`, `LivingDamageEvent.getAmount/setAmount` — имена Forge без SRG, linkcheck сверил с jar Forge.
- lang: ru 304 / en 304 ключа, наборы совпадают, число `%s` в новых ключах одинаковое. Все JSON в `resources/` валидны. Модели `phoenix_staff` (handheld) и `phoenix_feather` (generated), текстуры 16x16 RGBA на месте.
- `mods.toml` `version="2.18.0"`, запись 2.18.0 в `docs/history.md` есть.
- Правила кода: enum через `valueOf` (`Rarity`, `ChatFormatting`); частицы, звуки, блоки, эффекты через `Fx`/строки; summon, bossbar, title через `Cmd`; облик из `block_display` на гиганте; хост 1024 ХП, остальное через `DMG_K`. Блоки не меняют ни Феникс, ни посох, поэтому `Dungeons.shield/protectedAt` не нужны; `m_201971_` не вызывается.
- Что проверено в Boss5 и нарушений не найдено: защита от дублирования (`BOSSES.containsKey`, `orphans` пропускает `Boss5.BOSSES`); окно без хоста (`host == null` → удар по `ewb5_host` отменяется); обрезка порога фазы 2 и `lethal` на 1 ХП; неуязвимость в HATCH/REBORN/RAGE/FALL/DYING; победа однократная (`won/paid`), засчитывается в начале гибели, ниже не сбрасывается (`st != DYING`); уборка через `purge` (ktag + тег зала, приспешники через `RiftMobs.remove`) при сбросе, исчезновении хоста, исключении в тике и в конце гибели; полоска снимается в `cleanup`, в `startDeath` и после перезапуска (`Boss.barFor`); `Boss.clear` → `Boss5.clear` по `ServerStoppedEvent`; исключение тика ловит `Dungeons.tick0`. Огненные шары Искр (blaze) от поджога блоков закрывает уже имеющийся `DungeonGuard.onGrief`.
- PhoenixStaff: отмена `LivingDamageEvent` (событие `@Cancelable`, Forge затем не трогает здоровье), тотем не расходуется; `/kill` и бездна не спасаются; перезарядка по `getGameTime` (одно на все измерения, `/time` его не двигает) в `PlayerPersisted` (Forge копирует подтег при клонировании игрока), ограничена сверху 10 минутами; несколько посохов в инвентаре второго спасения не дают. Рывок — скорость, сквозь блоки не проходит, `DungeonGuard` не обходит.

## Замечания
1. **[minor]** `src/com/explosionwand/PhoenixStaff.java:80,104`: `DASHES` хранит `ServerLevel` и чистится только в тике того же уровня (`if (d.level != l) continue`). Если выйти из мира в одиночной игре в течение ~3,4 с после рывка, запись со старым `ServerLevel` (а через него и весь старый мир) остаётся в памяти до выхода из игры: в новом мире уровни другие, запись никто не удалит. → добавить `PhoenixStaff.clear()` (`DASHES.clear(); WAITING.clear();`) и вызывать его из `Dungeons.clearState()`, как `Earth.clear()`, либо удалять записи, у которых `d.level` больше не в `server.getAllLevels()`.
2. **[minor]** `src/com/explosionwand/Boss5.java:817` (и комментарий :797, отчёт 0110 «прыжок выше блока спасает», `docs/dungeons.md`): жар бьёт, если `y < floor + 1.0`, а `floor` — уровень метки, то есть верх помоста, на блок выше пола пещеры. Игрок на полу стоит на `floor - 1`, прыжок поднимает его максимум до `floor + 0,25`, и на блоке он стоит на `floor`. Значит, вне помоста ни прыжок, ни блок не спасают, а «безопасна» только высота от двух блоков над полом. → решить, как задумано. Если прыжок должен спасать, порог `v.f_82480_ < floor - 0.5`, тогда игрок на полу горит, а в прыжке и на блоке нет. Если не должен, исправить описание в отчёте и `docs/dungeons.md`.
3. **[minor]** `reports/0111-…-phoenix-staff.md:193` «Проверить в игре», п. 6: «Через 10 минут (можно `/time add 12000`) приходит „снова готов“». `/time add` меняет только время суток, а перезарядка считается по `getGameTime`, поэтому так её не ускорить: проверяющий будет ждать впустую. → в следующем отчёте указать, что ждать надо 10 реальных минут (или временно уменьшить `ASH_CD` в отладочной сборке). Сама логика верная, и из этого же следует, что `/time` пассивку не обходит.
4. **[minor]** `src/com/explosionwand/PhoenixStaff.java:53-57`: заявленная дальность рывка «~6–7 блоков» (0111) верна только на земле. Если смотреть вверх, `vy` = 0,55, игрок летит ~18 тиков с торможением в воздухе 0,91/тик и пролетает около 13 блоков по горизонтали, а со спуском ещё больше; раз в 6 с (4,2 с в Облачении). Это заметная мобильность в данжах: прыжковые пролёты Святилища, «обрушенные пролёты» башни, лавовые лужки. → проверить в игре дальность прыжка рывком, при необходимости ограничить `vy` (например, до 0,3) или горизонталь в воздухе; добавить строку о посохе в таблицу палочек в данжах (`docs/dungeons.md`, это уже отмечено в 0111/0112).
5. **[minor]** `src/com/explosionwand/Boss5.java:247`: `rate()` нигде не используется, а в отчёте 0110 (таблица, фаза 4) написано «кулдауны x0,65». В коде так только у пике (170→110) и почти так у залпа (120→85, x0,71); жар 230→190 (x0,83), смерчи 270→220 (x0,81), Искры 330→280 (x0,85). Посадка в фазе 4 — 45 тиков против 70 (в таблице не указано). → удалить `rate()` или применить его к кулдаунам; при балансе фазы 8 писать фактические цифры.
6. **[minor]** `src/com/explosionwand/Dungeons.java:245` (раскладка 2): записи Святилища раскладки 1 сбрасываются целиком (`opened`, `hallsCleared`, `respawn`). Святилище из 2.16.0, которое игрок уже прошёл, перестроится: сундуки залов (материалы, Угольный и прочие ключи) наполнятся снова, двери закроются, а ключи, оставшиеся у игрока с прошлого прохождения, могут открыть их без головоломки. Механизм общий и в 0110 частично описан. → указать в `docs/history.md`/«Проверить в игре», что старое Святилище даст добычу повторно, и проверить, привязаны ли ключи `Ash` к конкретному данжу; если нет, принять это осознанно.
7. **[nit]** `src/com/explosionwand/Boss5.java:335`: в фазе яйца хост-гигант (3,6 x 12) стоит ногами на `floor + 3,2 − 6`, и его хитбокс поднимается до ~`floor + 9`, а яйцо заканчивается около `floor + 6`. Удары «по воздуху» над яйцом засчитываются. → по желанию в EGG опустить хост на ~3 блока (тест свода не затронут), иначе вписать это в «Проверить в игре».
8. **[nit]** `src/com/explosionwand/Boss5.java:342`: `barMax()` в фазе 4 всегда считает от `REBORN_FULL`, поэтому ослабленный Феникс (700) появляется с полоской ~47%. Если так задумано (видно, что ослаблен), хорошо; иначе шкалу брать от фактического запаса.
9. **[nit]** `src/com/explosionwand/Boss5.java:807-808`: телеграф одного сектора жара — около 50 частиц раз в 2 тика (16 точек на две границы и по ~9 на две дуги), в фазе 4 три сектора дают ~150. В 0110 написано «до ~20 точек за 2 тика». На TPS это не опасно, но цифру стоит исправить, а при просадках шаг по радиусу сделать 2,5.
10. **[nit]** `src/com/explosionwand/PhoenixStaff.java:127-129`: след поджигает и бьёт любых «контролируемых» мобов, в том числе мирных животных (не прирученных); рывок через загон жарит коров. Если нежелательно, добавить в фильтр `e instanceof Enemy` для не-данжевых мобов.
11. **[nit]** `src/com/explosionwand/Boss5.java:187` (тот же шаблон в `Boss3`/`Boss`): после `cleanup` исключение пробрасывается дальше, и в этом тике не тикают остальные боссы (`Boss.tickAll` прерывается до Сердца Разлома). Это существующий шаблон, менять вместе для всех.
12. **[nit]** Отчёты 0109 и 0110 оба добавляют `tests/DungeonTest.java`, а 0110, 0111 и 0112 делят `ExplosionWandMod.java` и `tools/gen_materials.py`. Покоммитные команды из отчётов в указанном порядке захватят чужие правки. Ниже единый список, где каждый общий файл стоит в одном коммите и каждый коммит компилируется.

## Проверить в игре
- Жар фазы 2/4: спасает ли прыжок или блок (замечание 2).
- Дальность рывка Посоха вверх и вперёд и его применение на прыжковых пролётах Святилища (замечание 4).
- «Из пепла»: перезарядка 10 реальных минут, `/time add` её не ускоряет (замечание 3).
- Старый мир 2.16.0 с пройденным Святилищем: перестройка, повторная добыча, старые ключи (замечание 6).
- Удары по пустому месту над яйцом засчитываются (замечание 7).
- Остальное — по спискам «Проверить в игре» в 0110, 0111 и 0112.

## Решения и почему
- Вердикт **APPROVE WITH NITS**: блокеров и мажоров нет. Сборка, тесты, SRG, ID, lang и JSON в порядке, жизненный цикл босса и уборка сущностей полные, дюпа и обхода пассивки не нашёл. Замечания 1–6 можно закрыть отдельным шагом (патч 2.18.1) или в фазе баланса.
- В единый список git ROADMAP не включён (по заданию). Общие файлы поделены так, чтобы каждый коммит собирался: `ExplosionWandMod.java` (ссылается на `PhoenixStaffItem`) идёт с посохом, `DungeonTest.java` — с Фениксом (правки раскладки 2 в нём основные), `gen_materials.py` — с релизом ассетов.

## Проблемы и открытые вопросы
- Привязку ключей Святилища к конкретному данжу (замечание 6) не проверял глубоко.

## Git (выполняет пользователь)
Единый список фазы 4.10 (0109–0113), PowerShell:
```
git add src/com/explosionwand/DungeonMech.java reports/0109-2026-10-21-mod-engineer-nits-0108.md
git commit -m "fix(dungeon): хвосты ревью 4.8 (nit 1, 2)" -m "- fires_wrong только игрокам у жаровен" -m "- тест решёток по стене башни (в коммите Феникса вместе с DungeonTest)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"

git add src/com/explosionwand/Boss5.java src/com/explosionwand/Boss.java src/com/explosionwand/Ash.java src/com/explosionwand/Dungeons.java tools/gen_ash.py tools/gen_dungeons.py resources/data/explosionwand/structures/ash_south.nbt resources/assets/explosionwand/models/item/phoenix_feather.json resources/assets/explosionwand/textures/item/phoenix_feather.png tests/BossAshTest.java tests/DungeonTest.java docs/dungeons.md reports/0110-2026-10-21-boss-engineer-phoenix.md
git commit -m "feat(boss): Пепельный Феникс в Гнезде Святилища" -m "- облик из 58 block_display, 4 фазы: полёт, жар, яйцо из пепла, возрождённый" -m "- метка boss и печать в Гнезде (раскладка 2), барьер Святилища снимает победа" -m "- добыча: Посох и Перо Феникса, заглушки Искр и Пеплоходов" -m "- тест BossAshTest, DungeonTest под раскладку 2 и решётки (0109)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"

git add src/com/explosionwand/PhoenixStaff.java src/com/explosionwand/PhoenixStaffItem.java src/com/explosionwand/ExplosionWandMod.java src/com/explosionwand/Ticker.java src/com/explosionwand/jei/ExplosionWandJei.java stubs/net/minecraftforge/event/entity/living/LivingDamageEvent.java stubs/net/minecraft/world/entity/Entity.java resources/assets/explosionwand/models/item/phoenix_staff.json tests/PhoenixStaffTest.java reports/0111-2026-10-21-mod-engineer-phoenix-staff.md
git commit -m "feat(staff): Посох Феникса: рывок и пассивка Из пепла" -m "- ПКМ: огненный рывок скоростью, след из частиц поджигает мобов" -m "- пассивка: раз в 10 минут смертельный удар не убивает, кулдаун в PlayerPersisted" -m "- регистрация посоха и Пера Феникса, Boss5::onHurt, JEI, тест PhoenixStaffTest" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"

git add tools/gen_materials.py resources/assets/explosionwand/textures/item/phoenix_staff.png resources/assets/explosionwand/lang/ru_ru.json resources/assets/explosionwand/lang/en_us.json resources/META-INF/mods.toml docs/history.md docs/world.md reports/0112-2026-10-21-asset-engineer-release-2-18-0.md
git commit -m "assets(assets): релиз 2.18.0, Пепельный Феникс" -m "- текстуры пера и посоха (tools/gen_materials.py)" -m "- lang ru/en: босс, Искра, Пеплоход, Перо и Посох Феникса, JEI" -m "- версия 2.18.0, history.md и world.md" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"

git add reports/0113-2026-10-21-code-reviewer-phase-4-10.md
git commit -m "docs(agents): ревью фазы 4.10" -m "- APPROVE WITH NITS: 6 minor, 6 nit" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

## Следующий шаг
- mod-engineer: замечания 1, 3, 4, 10 (PhoenixStaff).
- boss-engineer: замечания 2, 5, 7, 8, 9 (Boss5).
- main: ROADMAP (закрыть 4.10), строка о Посохе Феникса в таблице палочек `docs/dungeons.md`, замечание 6 (решение о повторной добыче).
