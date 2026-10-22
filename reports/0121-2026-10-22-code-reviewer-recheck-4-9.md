# 0121 · Перепроверка фазы 4.9 после правок 0120

- **Дата:** 2026-10-22
- **Агент:** code-reviewer
- **Версия мода:** 2.17.0
- **Статус:** done
- **Связанные отчёты:** 0119, 0120 (а также 0109–0118)

## Задача
Проверить, что замечания 0119 п.1–5 и 8–11 закрыты правильно: тег `ewash_trap_min` и `adopt`, `Loot.wild`, поведение `Loot.chest` без изменений, `waveHits`. Прогнать `build.py`. Дать один общий список git-команд всей фазы, в котором каждый коммит собирается.

## Вердикт: **APPROVE WITH NITS**
Major п.1 закрыт, minor 2–5 и nit 8–11 тоже. Новые замечания мелкие (nit), фазу 4.9 можно закрывать.

## Что проверено
- **п.1 (Искры ловушки):** `AshMobsB.java:558` создаёт Искры с тегом `AshLogic.TRAP_TAG = "ewash_trap_min"` (`AshLogic.java:126`). Тег попадает в Tags хоста через `m.tags` (`RiftMobs.java:139,151`). При `adopt` (`RiftMobs.java:190-191`) суффикс `_min` даёт `summoned = true`, а для тега ловушки срок задаётся заново: `expire = now + TRAP_SPARK_LIFE` (40 с). Срок проверяется в `AMob.think` (`AshMobs.java:88`), то есть только пока рядом игрок. Если игрок не вернулся, хост лежит в выгруженном чанке без ИИ (NoAI), вреда от него нет. После `adopt` Искра живёт не дольше 40 с, `!m.summoned` на `RiftMobs.java:365` отключает выпадение материалов. Тест есть (`AshMobsTest:90-94`).
- **п.2 (`waveHits`):** `dy > WAVE_BELOW (-1,5) && dy < WAVE_JUMP`. Через это условие идут оба места вызова (`AshMobs.java:304` Звонарь, `AshMobsB.java:400` Хранитель). Тест `AshMobsTest:75`: ступень ниже (−1) задета, −4 и −14 нет.
- **п.3 (волны жаровен):** `DungeonMech.java:642` создаёт мобов через `spawnKind(..., tag, "\"ewdw_min\"")`, у них `summoned` и нет материалов. Тег зала `ewdw<hash>` остаётся, поэтому проверка лимита (`Cmd.tagged`) и уборка при решении головоломки работают как раньше.
- **п.4:** в коде Хранитель роняет 2 Тлеющих пера, `docs/dungeons.md:267` теперь говорит то же самое.
- **п.5 (`Loot.wild`):** сундук ставится, только если клетка `m_60795_` (воздух), под ней не воздух и не жидкость. Ничего не заменяется, светокамень не ставится. Имя сундука берётся через `{"translate":...}`, ключ `explosionwand.mob.ash_wisp_chest_name` есть в ru и en. Если поставить нельзя, выпадает `Mats.drop` (3 Пепельного угля).
- **`Loot.chest` ведёт себя как раньше:** порядок шагов прежний (столб → `setblock air` → сундук → `fill` → частицы и звук). Цикл предметов просто вынесен в `fill` без изменений.
- **п.8–11:** комментарии в `Boss5.java:54,860`, Javadoc `fireWave`, шапка `gen_ash.py` и комментарий в `tickWorld` исправлены.
- **SRG:** новых имён нет. `m_60795_`, `m_8055_`, `m_6924_`, `m_60819_` уже были в `src/` на HEAD (`git grep`).
- **lang:** оба файла валидный JSON, новый ключ есть в обоих.

## Замечания
1. **[nit]** `RiftMobs.java:192`: тег `ewdw_min` начинается с `ewd`. Поэтому `adopt` может записать его в `m.hallTag` вместо `ewdw<hash>`: порядок обхода `m_19880_()` (HashSet) не определён. Сейчас вреда нет, потому что `Mob.hallTag` у мобов Святилища нигде не читается (используется только при делении осколков, `RiftMobs.java:709`). → Сделать `t.startsWith("ewd") && t.length() > 3 && !t.endsWith("_min")`, либо дать тегу имя не на `ewd` (например `ewwave_min`).
2. **[nit]** `docs/dungeons.md:227`: в описании Зала Жаровен ещё написано «ванильные заглушки (хаски, скелеты, блейз; `waveMob`)». Это противоречит строке 267. → Заменить на «мобы Святилища (Пеплоходы, Искра, гончая, страж; без материалов)».
3. **[nit]** отчёт 0120, «Проверить в игре»: пункт «на крыше постройки сундука нет» не соответствует коду. `groundY` берёт карту высот `MOTION_BLOCKING_NO_LEAVES`, поэтому над домом это крыша, и `Loot.wild` поставит сундук на неё (ничего не ломая). Проверять в игре нужно другое: на крыше ничего не заменено и светокамня нет. Если сундуков на постройках быть не должно, нужна отдельная проверка натурального грунта (на усмотрение пользователя).
4. Открыто с прошлого ревью, решает пользователь: п.6 (ферма огонька), п.7 (2.17.0 < 2.18.0, jar 2.18.0 убрать из mods).

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `reports/0121-2026-10-22-code-reviewer-recheck-4-9.md` | этот отчёт (код не менялся) |

## Проверка
- `python tools/build.py` в рабочей копии: компиляция 182, неизвестных SRG 0, linkcheck 0 неразрешённых, ID 0, gen_dungeons 0 устаревших, все 13 тестов OK, jar `dist/explosionwand-1.20.1-2.17.0.jar`.
- Отдельно проверен коммит 1 из списка ниже. В `build/recheck` выгружено `git archive HEAD`, сверху положены все файлы коммита 1 (всё, кроме `docs/`, `reports/`, `ROADMAP.md`). `build.py` прошёл полностью: SRG 0, ID 0, 13/13 тестов, jar. Временная папка удалена. Тесты не читают `docs/` и `reports/`, так что коммиты 2–5 на сборку не влияют.
- Почему коммит кода один: рабочие файлы (`Ash.java`, `Boss5.java`, `RiftMobs.java`, lang, `DungeonMech.java`) содержат правки 4.10, 4.9 и 0120 одновременно. Без `git add -p` разбить их на коммиты, каждый из которых собирается, нельзя: `Ash.minion` тянет за собой `RiftMobs.spawnKind`/`Kind.ASH_*`, а AshMobsTest проверяет lang. HEAD сейчас не собирается (см. 0119), коммит 1 это исправляет.

## Проверить в игре
- Всё из 0119 и 0120.
- Огонёк: при сундуке на крыше постройки блок крыши не заменён и светокамня нет (см. замечание 3).
- Перед запуском удалить из mods `explosionwand-1.20.1-2.18.0.jar`.

## Решения и почему
- APPROVE WITH NITS: все блокирующие и существенные замечания закрыты, новые находки не влияют на игру.
- ROADMAP идёт отдельным последним коммитом. Строку «ждёт ревью code-reviewer» основная сессия заменит на «ревью 0119, перепроверка 0121 APPROVE WITH NITS» до коммита.

## Git (выполняет пользователь)
Единый список (PowerShell), по порядку:
```
git add src/com/explosionwand/Ash.java src/com/explosionwand/AshLogic.java src/com/explosionwand/AshMobs.java src/com/explosionwand/AshMobsB.java src/com/explosionwand/AshModels.java src/com/explosionwand/Boss.java src/com/explosionwand/Boss5.java src/com/explosionwand/DungeonMech.java src/com/explosionwand/Dungeons.java src/com/explosionwand/ExplosionWandMod.java src/com/explosionwand/Loot.java src/com/explosionwand/Mats.java src/com/explosionwand/PhoenixStaffItem.java src/com/explosionwand/RiftMobs.java src/com/explosionwand/RiftModels.java src/com/explosionwand/Ticker.java src/com/explosionwand/jei/ExplosionWandJei.java stubs/net/minecraft/world/entity/Entity.java stubs/net/minecraftforge/event/entity/living/LivingDamageEvent.java tools/gen_ash.py tools/gen_dungeons.py tools/gen_materials.py resources/data/explosionwand/structures/ash_north.nbt resources/data/explosionwand/structures/ash_mid.nbt resources/data/explosionwand/structures/ash_south.nbt resources/assets/explosionwand/models/item/phoenix_feather.json resources/assets/explosionwand/models/item/phoenix_staff.json resources/assets/explosionwand/textures/item/phoenix_feather.png resources/assets/explosionwand/textures/item/phoenix_staff.png resources/assets/explosionwand/lang/ru_ru.json resources/assets/explosionwand/lang/en_us.json resources/META-INF/mods.toml tests/AshMobsTest.java tests/BossAshTest.java tests/DungeonTest.java
git commit -m "feat(boss): Феникс подключён, мобы Святилища, релиз 2.17.0" -m "- Boss делегирует Boss5, Посох и Перо Феникса, JEI, заглушки LivingDamageEvent/Entity" -m "- 8 мобов Святилища, Хранитель Углей, Блуждающий огонёк, Ash.minion для Феникса" -m "- волны жаровен из мобов Святилища без материалов, Loot.wild для сундука огонька" -m "- метки шаблонов ash_*, lang ru/en, тесты AshMobs/BossAsh/Dungeon, версия 2.17.0" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"

git add docs/history.md docs/dungeons.md docs/world.md reports/0109-2026-10-21-mod-engineer-nits-0108.md reports/0110-2026-10-21-boss-engineer-phoenix.md reports/0111-2026-10-21-mod-engineer-phoenix-staff.md reports/0112-2026-10-21-asset-engineer-release-2-18-0.md
git commit -m "docs(boss): Пепельный Феникс 2.18.0: история и отчёты 4.10" -m "- history 2.18.0 и 2.17.0 (включает 2.18.0), dungeons.md, world.md" -m "- отчёты 0109-0112" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"

git add reports/0116-2026-10-22-boss-engineer-ash-mobs-a.md reports/0117-2026-10-22-boss-engineer-ash-mobs-b.md reports/0118-2026-10-22-boss-engineer-ash-mobs-tests-docs.md reports/0120-2026-10-22-boss-engineer-ash-mobs-review-fixes.md
git commit -m "docs(boss): отчёты фазы 4.9 и правок ревью" -m "- 0116-0118 мобы Святилища, 0120 правки по ревью 0119" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"

git add reports/0113-2026-10-21-code-reviewer-phase-4-10.md reports/0119-2026-10-22-code-reviewer-phase-4-9.md reports/0121-2026-10-22-code-reviewer-recheck-4-9.md
git commit -m "docs(agents): ревью фаз 4.10 и 4.9, перепроверка" -m "- 0113 ревью 4.10, 0119 ревью 4.9 (REQUEST CHANGES)" -m "- 0121 перепроверка после 0120: APPROVE WITH NITS" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"

git add ROADMAP.md
git commit -m "docs(agents): фаза 4.9 закрыта, текущая 4.12" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
Отчёты 0114 и 0115 уже в HEAD (коммиты `56ee7f0`, `68bbc5f`), добавлять их не нужно.

## Следующий шаг
Основная сессия: поправить строку в ROADMAP («ждёт ревью» → «ревью 0119/0121»), закрыть 4.9 и вывести блок закрытия фазы. Nit 1–2 можно отдать `boss-engineer` в начале 4.12 или следующим патчем. Пп.6–7 из 0119 решает пользователь.
