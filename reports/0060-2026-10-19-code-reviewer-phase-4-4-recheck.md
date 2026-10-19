# 0060 · Повторное ревью фазы 4.4 после исправлений

- **Дата:** 2026-10-19
- **Агент:** code-reviewer
- **Версия мода:** 2.7.0
- **Статус:** done
- **Связанные отчёты:** 0056 (ревью), 0057, 0058, 0059 (исправления); 0051–0055 (шаги фазы)

## Задача
Повторная проверка фазы 4.4: закрыты ли blocker 1, major 2–4, minor 5–7 и взятые nit из 0056, нет ли регрессий. Итоговые git-команды всей фазы поверх HEAD `d406854`.

## Вердикт: **APPROVE WITH NITS**
Все blocker, major и minor из 0056 закрыты, регрессий в коде не нашёл. Осталось два minor: документация про измерение `arena` устарела, и неясно, как игрок выбирается из старого `arena`. Ещё два nit. Коммитить можно. Документацию лучше поправить до docs-коммита (одна строка в трёх файлах), остальное перенести в 4.5.

## Что сделано
- Прочитаны 0056–0059, `git status`, `git diff`. Полностью прочитаны `Boss.java`, `RiftStaff.java`, `RiftStaffItem.java`, `RiftStaffTest.java`. В `Dungeons.java` прочитаны `inDungeon`, `clearable`, `lairWon`, `onCleared`, `near`/`at`, `clearState`, `tick0`. Просмотрены диффы `DungeonGuard`, `ArenaGuard`, `Dims`, `Ticker`, `Altar`, `EclipseSealItem`, `ids.py` и заглушек.
- Lang: ключи ru и en совпадают. 14 ключей `explosionwand.boss.rift.*`, которые использует `Boss.java`, есть в обоих файлах. `rift_staff.no_room`/`no_dungeon` на месте. В строках `Boss.java` кириллицы нет (только комментарии).
- Три JSON `arena` валидны и совпадают с HEAD (`git diff HEAD` пуст).

### Пункты 0056
| № | Статус | Чем закрыто |
|---|---|---|
| blocker 1 | закрыт | `Dungeons.clearable(hasBoss, …)` для логова всегда `false` (`Dungeons.java:775`). Логово засчитывает только `Boss.tickAll` → `lairWon` при `b.won`, а `won` ставит `dying()` на `dyingT == 90` после `purge`. Пробуждение без хоста, `stage 0` без хоста (`t > 40`), удалённый хост и сброс возвращают `done` с `won == false`, так что зал не засчитан, и `orphans` его сбрасывает. Тест `BossRiftTest:86-89`. |
| — сохранение | ок | `lairWon` ставит `hallsCleared` и `m_77762_` (бит сохраняется в `DungeonData`, `cleared`). После перезапуска `inDungeon` пропускает зал (`if (cleared) continue`), `orphans` не будит его (`spawned && cleared`, `reset` только при мусоре), печать открывается (`DungeonMech.sealWanted(…, hallsCleared, …)`). `DONE` удалён, гонки с `orphans` больше нет. |
| — перезапуск в гибели | ок (как nit 9) | Если выйти в первые 90 тиков гибели, после загрузки `orphans` сбросит зал и бой начнётся заново с полным ХП. Добыча не теряется. |
| major 2 | закрыт | `ids.py`: `particle_type` снова в `CHECKS`. Регулярка `Kind` работает только в `RiftModels.java`. `build.py`: проблем ID 0. |
| major 3 | закрыт | `RiftStaff.pick` проверяет точку назначения и путь через каждый блок, начиная с `t = 1`, функцией `Dungeons.near(…, 1)`. Точку с данжем берёт ближе данжа, если пути нет — отказ. Подкоп вплотную к стене даёт отказ уже на `t = 1`. `RiftStaffTest` покрывает 5 случаев. |
| major 4 | закрыт | Три JSON `arena` возвращены как в HEAD, ссылок в коде нет. Старые миры не потеряют запись измерения. |
| minor 5 | закрыт | Заголовки, подписи, actionbar и название полоски Сердца идут через `translate`. Сообщения шага Посоха тоже. |
| minor 6 | закрыт | `Boss.orphans:184`: один раз за сессию на запись `bossbar remove` (`SWEPT`), без условия «игрок рядом». Активный бой не задет: запись с боссом в `BOSSES` пропускается, а `spawnHost` сам делает `remove`+`add` того же id. `SWEPT` очищается в `Boss.clear()` → `clearState`. |
| minor 7 | закрыт | `m_269104_(p, p)`, это `indirectMagic`. Пропускаются `AbstractVillager` и `TamableAnimal` со своим хозяином (`m_21830_` `isOwner`) или прирученные (`m_21824_` `isTamed`). |
| nit 8 | закрыт | `orphans:188`: `reset` и при `spawned && !cleared` без мусора. |
| nit 9 | закрыт | Сделано вместе с blocker 1. |
| nit 10 | закрыт | Формулировка поправлена в 0057. |
| nit 11 | закрыт | Конструкторы `Altar(…)` и `Altar(…, boolean)` убраны, палитра и портал `kind 0` тоже. `EclipseSealItem` передаёт `1`. |
| nit 12 | закрыт частично | `DungeonGuard.onBreak:29-34`: ядро снимается через `m_7731_` в воздух, событие отменено, дропа нет, `tickShield` видит не-аметист. Проверка «inside» осталась прежней, для остальных блоков данжа поведение не изменилось. Взрыв ядро по-прежнему роняет (см. nit 3 ниже). |
| nit 13 | не взят | Перенос в 4.5. |
| nit 14 | закрыт | В 0059. |

## Замечания

### minor
1. **[minor] `docs/history.md:9`, `docs/world.md:56`, `docs/dungeons.md:51`: в документации написано, что измерение `arena` убрано, а в history ещё и «игроки в `arena` вернутся в обычный мир».** После major 4 JSON вернулись, а код, который переносит игроков, никто не писал. → Переписать строки: «арена Сердца убрана, измерение `arena` пока оставлено пустым для совместимости старых миров». Это одна строка в каждом из трёх файлов, лучше до docs-коммита.
2. **[minor] `Dims.java` / `Ticker.java`: у игрока, вышедшего из игры в `arena` на 2.6.x, нет выхода.** Измерение пустое (диск арены сохранён в регионах), `ArenaGuard` его больше не охраняет, портала домой нет, `ORIGIN` в памяти после перезапуска пуст. Выбраться можно только смертью, с потерей вещей, или другими палочками. Случай редкий. → В 4.5 при входе или в тике игрока в `explosionwand:arena` отправлять через `Dims.send` в обычный мир, на точку возрождения.

### nit
3. **[nit] `DungeonGuard.java:73` и `onDetonate`:** ядро столба остаётся в списке блоков взрыва, а ванильный взрыв роняет блок с вероятностью. Фарм аметиста взрывами возможен, хотя и мизерный. → Перенос в 4.5: убирать ядро из `getAffectedBlocks` и ставить воздух вручную, как в `onBreak`.
4. **[nit] `RiftStaff.crack`:** трещина, пущенная снаружи, затягивает мобов зала с края данжа наружу. Через 24 блока их уберёт `removeStrays`, зал засчитается легче. Мелочь. → Перенос в 4.5: пропускать сущности с тегом `ewd`, если игрок вне данжа.

### Проверено и в порядке
- Исключения: `Boss.tickAll` убирает бой и пробрасывает исключение, его ловит `Dungeons.tick0` (`warnedBoss`), до тика мира оно не доходит. Итерация по `BOSSES` во время `lairWon`/`onCleared` карту не меняет.
- Уборка: победа (`purge` на 90-м тике, полоска снята в `startDeath`), смерть или уход игроков (`hallStale` → `Boss.reset`), выход в меню (`clearState` → `Boss.clear`, затем `orphans` и `SWEPT`), мирный режим, смена раскладки. Утечек не вижу.
- `RiftStaff.pick`: не больше 7×9 вызовов `near` за одно применение, а не каждый тик. NPE нет: `Dungeons.near` вне обычного мира возвращает `false`.
- Правила кода: новые SRG-имена (`m_269104_`, `m_21830_`, `m_21824_`, `m_20049_`, `m_36316_`) проверены `lookup.py --srg` и совпадают с заглушками. Enum через `valueOf`, частицы, звуки и блоки через `Fx`, bossbar, title и summon через `Cmd`. ХП хоста 900 ≤ 1024.
- Баланс не менялся: ХП 900, урон 6/8/7/6/7, Посох 6 урона, кулдауны 100/80.

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `reports/0060-2026-10-19-code-reviewer-phase-4-4-recheck.md` | этот отчёт (код не менялся) |

## Проверка
- `python tools/build.py`: 124 класса, SRG 220/221, в joined.tsrg не найдено 0, проблем ID 0, gen_dungeons 4/0. BossRiftTest, DungeonTest, MazeTest, RiftMobsTest и RiftStaffTest OK. Jar `dist/explosionwand-1.20.1-2.7.0.jar` (790 КБ).
- Новые SRG-имена: `m_269104_` → `DamageSources.indirectMagic(Entity, Entity)`, `m_21830_` → `TameableEntity.isOwner(LivingEntity)`, `m_21824_` → `TameableEntity.isTamed()`.

## Проверить в игре
- Вход в зал 7: сундука нет, пока жив босс. Выйти в меню в первые 3 с, загрузиться: зал не засчитан, при входе босс просыпается снова.
- Победа, затем перезапуск: логово не оживает, сундук и открытая печать на месте.
- Перезапуск посреди боя, игрок далеко от логова: полоски «Сердце Разлома» нет.
- Ядро столба ломается киркой, аметист не выпадает, щит снимается.
- Посох: Shift+ПКМ из подкопа у стены Разлома даёт отказ с сообщением, в поле шаг на 8 блоков. Трещина не бьёт жителей и питомцев, мобы, убитые ею, дают опыт.
- en_us: заголовки и подсказки босса и сообщения посоха на английском.
- Мир, созданный на 2.6.x, открывается без экрана ошибок датапака.

## Решения и почему
- Minor 1–2 фазу не держат: код корректен, неверен только текст документации, а случай «застрял в `arena`» редкий и не ломает мир.

## Проблемы и открытые вопросы
- `arcane_helmet.json`/`arcane_leggings.json` отличаются от HEAD только концами строк (`git diff --ignore-cr-at-eol` пуст). По просьбе main они включены в коммит фичи. Если после `git add` git не увидит изменений, они просто не попадут в коммит.

## Git (выполняет пользователь)
Поверх HEAD `d406854`, по порядку. `ROADMAP.md` добавляет main отдельно. JSON измерения `arena` не удаляются.
```
git add tools/ids.py tools/build.py reports/0052-2026-10-19-build-engineer-ids-jar-cleanup.md reports/0059-2026-10-19-build-engineer-ids-particles-fix.md
git commit -m "build(build): ids.py видит sound(), частицы и хосты Kind, mech_test вне jar" -m "- проверка голых sound() и хостов RiftModels.Kind (только в RiftModels.java)" -m "- вернута проверка частиц (кортеж выпадал в комментарий)" -m "- mech_test.nbt исключён из jar, тесты берут его из resources/"

git rm src/com/explosionwand/Arena.java src/com/explosionwand/RiftKeyItem.java resources/assets/explosionwand/models/item/rift_key.json resources/assets/explosionwand/textures/item/rift_key.png resources/data/explosionwand/recipes/rift_key.json
git add src/com/explosionwand/Boss.java src/com/explosionwand/Dungeons.java src/com/explosionwand/DungeonGuard.java src/com/explosionwand/RiftMobs.java src/com/explosionwand/RiftLogic.java src/com/explosionwand/Dims.java src/com/explosionwand/Ticker.java src/com/explosionwand/ArenaGuard.java src/com/explosionwand/Altar.java src/com/explosionwand/EclipseSealItem.java src/com/explosionwand/ExplosionWandMod.java src/com/explosionwand/WhaleStaff.java src/com/explosionwand/RiftStaff.java src/com/explosionwand/RiftStaffItem.java src/com/explosionwand/SealShardItem.java src/com/explosionwand/jei/ExplosionWandJei.java stubs/net/minecraft/world/entity/Entity.java stubs/net/minecraft/world/damagesource/DamageSources.java stubs/net/minecraft/world/entity/animal/TamableAnimal.java stubs/net/minecraft/world/entity/npc/AbstractVillager.java tools/gen_dungeons.py resources/data/explosionwand/structures/rift_entrance.nbt resources/data/explosionwand/structures/rift_mid.nbt resources/data/explosionwand/structures/rift_deep.nbt resources/data/explosionwand/recipes/arcane_helmet.json resources/data/explosionwand/recipes/arcane_leggings.json resources/assets/explosionwand/lang/ru_ru.json resources/assets/explosionwand/lang/en_us.json resources/assets/explosionwand/models/item/rift_staff.json resources/assets/explosionwand/models/item/sleep_seal_shard.json resources/assets/explosionwand/textures/item/rift_staff.png resources/assets/explosionwand/textures/item/sleep_seal_shard.png resources/META-INF/mods.toml tests/BossRiftTest.java tests/DungeonTest.java tests/RiftMobsTest.java tests/RiftStaffTest.java reports/0051-2026-10-19-mod-engineer-rift-side-rooms-shrink.md reports/0053-2026-10-19-mod-engineer-rift-staff-sleep-shard.md reports/0054-2026-10-19-boss-engineer-rift-heart-lair.md reports/0057-2026-10-19-boss-engineer-review-fixes-4-4.md reports/0058-2026-10-19-mod-engineer-rift-staff-review-fixes.md
git commit -m "feat(dungeon): Сердце Разлома в логове, Посох Разлома, версия 2.7.0" -m "- зал 7: босс просыпается при входе, зачистка только победой (hallsCleared), добыча: посох, ядро, осколок Сна" -m "- Посох Разлома: трещина (indirectMagic, щадит жителей и питомцев) и шаг на 8 блоков, в данж не входит" -m "- Осколок Печати: Сон; тексты босса и посоха через lang" -m "- комнаты 8-16 меньше, раскладка Разлома 4" -m "- убраны Ключ Разлома, алтарь Сердца, арена Сердца; JSON измерения arena оставлены для старых миров" -m "- полоска после перезапуска снимается, ядро столба без дропа, nit 8, 12, 15, 16"

git add docs/history.md docs/world.md docs/dungeons.md reports/0055-2026-10-19-asset-engineer-release-2-7-0.md reports/0056-2026-10-19-code-reviewer-phase-4-4.md reports/0060-2026-10-19-code-reviewer-phase-4-4-recheck.md
git commit -m "docs(dungeon): история 2.6.2 и 2.7.0, world.md и dungeons.md" -m "- логово Сердца, метка boss, раскладка 4" -m "- ревью фазы 4.4 и повторное ревью"
```
Если до docs-коммита правят minor 1 (строки про `arena`), отчёт этой правки добавить в тот же `git add`.

## Следующий шаг
- **main / asset-engineer:** minor 1, поправить три строки docs про `arena` (до docs-коммита или первым шагом 4.5).
- **Перенос в 4.5:** minor 2 (выход из старого `arena`, mod-engineer), nit 3 (ядро столба и взрывы, boss-engineer), nit 4 (трещина и мобы данжа снаружи, mod-engineer), nit 13 из 0056 (пометка 2.6.2 в history), nit 14 из 0036 (залы спят до полной достройки).
- Фазу 4.4 можно закрывать после коммитов.
