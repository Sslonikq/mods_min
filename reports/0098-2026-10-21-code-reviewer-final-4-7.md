# 0098 · Финальное ревью фазы 4.7 и правок после игры (0096, 0097)

- **Дата:** 2026-10-21
- **Агент:** code-reviewer
- **Версия мода:** 2.15.0 (не выпущена)
- **Статус:** done
- **Связанные отчёты:** 0093, 0094, 0095, 0096, 0097

## Задача
Короткое итоговое ревью правок 0096 по замечаниям 0095: `RiftLogic.knockMove` и `RiftMobs.applyKnock` с подшагами, `DungeonGuard.onPiston`/`onGrief` (только EnderMan), `Dungeons.column`, Стоп-Кадр. Плюс linkcheck из 0097. Затем один итоговый набор git-команд для всей фазы 4.7 (0084–0098). Код не менял.

## Вердикт: **APPROVE WITH NITS**
Major 1 из 0095 закрыт. Blocker и major нет. Minor 2–4 и nit 6–8 из 0095 закрыты. Регрессий не нашёл.

## Что сделано (проверено)
- **knockMove (RiftLogic.java:186–206):** `n = ceil(max|k| / 0,45)`, n ≥ 1 и конечно, поэтому бесконечного цикла нет (NaN даёт n=1). Оси двигаются по отдельности, x → z → y. Если ось упёрлась, её шаг и скорость обнуляются, а остальные оси продолжают движение (скольжение).
  - **Стены:** подшаг ≤ 0,45 < 1. Если центр стартует перед клеткой стены, он обязательно попадает в эту клетку на каком-то подшаге, и `spaceFor` его отсекает. У широких хостов углы `±half` сдвигаются на тот же подшаг, вывод тот же. Стены, перегородки и двери-печати в 1 блок не проходятся.
  - **Потолок и пол:** вертикаль режется по подшагам так же, при `|ky|` до 1 это 3 подшага.
  - **Поводок:** в лямбде старая и новая дистанции до дома. По y дистанция не меняется, поэтому поводок не блокирует падение. Если моб уже за поводком, отойти от дома дальше он не может, а вернуться может.
  - **Приземление (RiftMobs.java:530):** снап на `floor(cy)` только когда упёрлась нисходящая y. Моб висит над полом не выше 0,45, после снапа `supported=true`, и `ky=0`. Если снап не прошёл (y с дробью ≥ 0,99), моб остаётся на 0,005 выше пола и всё равно считается опорным. Застревания нет.
  - **Внутри блока** все оси упираются, `kx=kz=0`. Если под мобом есть воздух, он падает, иначе становится опорным. Вечного `airborne` нет. Летуны затухают через ×0,82.
- **Стоп-Кадр:** `knock` не принимает импульс, пока `frozenOf != null` (RiftMobs.java:308), а `step` при заморозке сбрасывает `k*` и `airborne` (:425). Статуя (`knockable()==false`) возвращает `true` и в ванильную ветку не уходит.
- **onPiston:** отменяет событие целиком в радиусе 13 от охраняемого блока. Проверки null и клиента на месте, слушатель зарегистрирован (ExplosionWandMod.java:139).
- **onGrief:** срабатывает только для `instanceof EnderMan` (null безопасен), ±6, только сервер. linkcheck подтвердил пакет `net.minecraft.world.entity.monster`. Описание есть в docs/dungeons.md.
- **column:** при `WORLD_SURFACE_WG ≥ 66` возвращает `{surf, 0}`. По шуму генератора деревьев нет, поэтому на суше surf совпадает с floor. try/catch и однократный лог сохранены.
- **linkcheck (0097):** шаг `[2c]` работает, без jar не роняет сборку. Nit 8 из 0095 закрыт.
- **Тесты:** `knockTests` покрывают стену 1 блок из x=0,9, обратный ход, печать со скольжением, потолок и свободный путь.

## Замечания
- **[nit] src/com/explosionwand/RiftMobs.java:523:** у `ky -= 0.08` нет предела скорости падения. В длинной шахте или в пустоте `|ky|` растёт без конца, а с ним и число подшагов (по `cellFree` на каждый). Ваниль сама уберёт моба ниже мира, так что вреда нет. → `ky = Math.max(ky, -3.0)`, как терминальная скорость в ванили.
- **[nit] src/com/explosionwand/Dungeons.java:807:** порог 66 жёстко завязан на уровень моря 63. При другом `sea_level` (датапак) или у воды аквиферов выше 66 колонна будет считаться сухой. Посадка Разлома в воду — косметика. → Оставить, но при случае взять `m_5736_` с проверкой SRG.
- **[nit] tests/RiftMobsTest.java:47–65:** нет случаев на поводок (с дистанции за `leash` моб не удаляется, но возвращается) и на снап приземления. Снап живёт в `applyKnock`, вне `RiftLogic`. → Добавить тест на поводок через лямбду `Step`, как в `applyKnock`.
- **[nit] reports/0096 «Git»:** в коммите нет `Co-Authored-By`, а в список не попал `tools/linkcheck.py`, хотя `build.py` его вызывает. Итоговый список ниже это закрывает.

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `reports/0098-2026-10-21-code-reviewer-final-4-7.md` | этот отчёт |

## Проверка
- `python tools/build.py` (один прогон): 156 классов; SRG исходники/байткод 246/247, неизвестных 0; пути классов 102 / 0 ошибок; linkcheck: jar 8, классов 139, членов 414, не проверено 0, расхождений 0; ID 0; gen_dungeons 7/0; тесты OK (BossCityTest, BossRiftTest, CityMobsTest, CityTest, DungeonTest, EarthTest, MatsTest, MazeTest, RiftMobsTest, RiftStaffTest); jar `dist/explosionwand-1.20.1-2.15.0.jar`.
- Новых SRG-имён в 0096 и 0097 нет.

## Проверить в игре
- Ветер, Комета и тройное притяжение у стены, перегородки или двери-печати в 1 блок: мобы не проходят насквозь, вдоль стены скользят.
- Толчок с уступа: моб падает и встаёт на пол, не висит и не дёргается. Летуны затухают.
- Поводок: моба, отброшенного к пределу, дальше не уносит, и он возвращается.
- Поршень или слизь у стены Разлома/Города до победы ничего не двигают. После победы работают.
- Эндермен у данжа с живым боссом не крадёт блоки, зомби подбирают предметы.
- Стоп-Кадр: моб после разморозки не вылетает. Статуя Города стоит на месте.
- TPS при подходе к новому Разлому.
- Остальное — по «Проверить в игре» из 0094 и 0095.

## Решения и почему
- Nit не блокируют коммит: это края (бездонная шахта, нестандартный уровень моря) или тесты.
- Коммитов два. Первый — игровая часть фазы 4.7 и правки после игры. Второй — сборочные инструменты (classcheck из 0089, linkcheck из 0097), `build.py` и `CLAUDE.md`, потому что `build.py` вызывает оба скрипта.
- Удаления идут через `git rm --ignore-unmatch`. Переименование `animal/TamableAnimal.java` уже в индексе (staged R100), поэтому `git rm` по старому пути с `--ignore-unmatch` не падает.

## Проблемы и открытые вопросы
- Огонь, расползающийся по дереву Города, не перехвачен (перенесено в 4.8, см. 0094).
- `build/` общий: параллельные `build.py` ломают друг друга (0097).

## Git (выполняет пользователь)
Сверено с `git status` на 2026-10-21. Команды для PowerShell.

Коммит 1: фаза 4.7 и правки после игры (0084–0096, 0098):
```
git rm --ignore-unmatch src/com/explosionwand/Arena3.java src/com/explosionwand/DreamSealItem.java src/com/explosionwand/Ember.java resources/assets/explosionwand/models/item/dream_seal.json resources/assets/explosionwand/textures/item/dream_seal.png resources/data/explosionwand/recipes/dream_seal.json stubs/net/minecraft/world/entity/animal/TamableAnimal.java
git add ROADMAP.md docs/dungeons.md docs/history.md docs/world.md docs/concepts/mobs_ash.png resources/META-INF/mods.toml resources/assets/explosionwand/lang/en_us.json resources/assets/explosionwand/lang/ru_ru.json resources/assets/explosionwand/models/item/ash_seal_shard.json resources/assets/explosionwand/textures/item/ash_seal_shard.png
git add resources/data/explosionwand/structures/sleep_mid.nbt resources/data/explosionwand/structures/sleep_north.nbt resources/data/explosionwand/structures/sleep_south.nbt
git add src/com/explosionwand/Altar.java src/com/explosionwand/ArenaGuard.java src/com/explosionwand/Boss.java src/com/explosionwand/Boss3.java src/com/explosionwand/CityMobsB.java src/com/explosionwand/Comet.java src/com/explosionwand/Dims.java src/com/explosionwand/DungeonData.java src/com/explosionwand/DungeonGuard.java src/com/explosionwand/DungeonSite.java src/com/explosionwand/Dungeons.java src/com/explosionwand/Earth.java src/com/explosionwand/ExplosionWandMod.java src/com/explosionwand/Minions.java src/com/explosionwand/RiftLogic.java src/com/explosionwand/RiftMobs.java src/com/explosionwand/RiftStaff.java src/com/explosionwand/Ticker.java src/com/explosionwand/TimeStop.java src/com/explosionwand/WhaleStaff.java src/com/explosionwand/jei/ExplosionWandJei.java
git add stubs/net/minecraft/server/level/ServerLevel.java stubs/net/minecraft/server/level/ServerChunkCache.java stubs/net/minecraft/world/entity/Entity.java stubs/net/minecraft/world/entity/TamableAnimal.java stubs/net/minecraft/world/entity/decoration/ArmorStand.java stubs/net/minecraft/world/entity/monster/EnderMan.java stubs/net/minecraft/world/entity/player/Player.java stubs/net/minecraft/world/entity/player/Inventory.java stubs/net/minecraft/world/entity/projectile/Projectile.java stubs/net/minecraft/world/entity/projectile/FishingHook.java stubs/net/minecraft/world/entity/projectile/ThrownEnderpearl.java stubs/net/minecraft/world/entity/projectile/ThrownTrident.java
git add stubs/net/minecraft/world/level/BlockGetter.java stubs/net/minecraft/world/level/Level.java stubs/net/minecraft/world/level/LevelAccessor.java stubs/net/minecraft/world/level/LevelHeightAccessor.java stubs/net/minecraft/world/level/chunk/ChunkGenerator.java stubs/net/minecraft/world/level/levelgen/RandomState.java stubs/net/minecraftforge/event/level/BlockEvent.java stubs/net/minecraftforge/event/level/PistonEvent.java stubs/net/minecraftforge/event/entity/EntityMobGriefingEvent.java stubs/net/minecraftforge/event/entity/EntityLeaveLevelEvent.java stubs/net/minecraftforge/eventbus/api/Event.java
git add tests/DungeonTest.java tests/EarthTest.java tests/RiftMobsTest.java tests/BossCityTest.java tools/gen_city.py tools/gen_dungeons.py
git add reports/0084-2026-10-19-mod-engineer-review-tails-4-11.md reports/0085-2026-10-21-boss-engineer-monolith-temple.md reports/0086-2026-10-21-asset-engineer-release-2-15-0.md reports/0087-2026-10-21-code-reviewer-phase-4-7.md reports/0088-2026-10-21-main-phase-4-7-closed.md reports/0090-2026-10-21-mod-engineer-rift-staff-revert.md reports/0091-2026-10-21-boss-engineer-monolith-arena-hp.md reports/0092-2026-10-21-mod-engineer-rift-spawn-wands-dungeons.md reports/0093-2026-10-21-code-reviewer-post-playtest-2-15-0.md reports/0094-2026-10-21-mod-engineer-review-fixes-0093.md reports/0095-2026-10-21-code-reviewer-recheck-0094.md reports/0096-2026-10-21-mod-engineer-review-fixes-0095.md reports/0098-2026-10-21-code-reviewer-final-4-7.md
git commit -m "feat(dungeon): Город v3, Монолит в храме, барьер до победы (2.15.0)" -m "- миграция bossDead, посадка Разлома без синхронных чанков" -m "- импульс палочек мобам данжа подшагами, сквозь стены не проносит" -m "- лоза, Стоп-Кадр гасит импульс, поршни и эндермены у данжа" -m "- ревью 0087/0093/0095/0098" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
Коммит 2: сборочные проверки (0089, 0097):
```
git add tools/build.py tools/classcheck.py tools/linkcheck.py CLAUDE.md reports/0089-2026-10-21-build-engineer-class-path-audit.md reports/0097-2026-10-21-build-engineer-linkcheck.md
git commit -m "build(build): classcheck и linkcheck по настоящим jar Forge" -m "- [2b] пути классов net/minecraft по mojang.txt" -m "- [2c] члены и дескрипторы против jar Forge 47.4.10, без jar пропуск" -m "- CLAUDE.md: команда linkcheck, структура без Arena3" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
После коммитов `git status` должен быть чистым, кроме `dist/`, `build/`, `.cache/` (они в .gitignore).

## Следующий шаг
- Пользователь: коммиты выше, затем проверка в игре по списку.
- Необязательно, в 4.8 (`mod-engineer`): nit про предел `ky`, тест поводка, огонь по дереву Города.
