# 0095 · Повторное ревью правок 0094 (по замечаниям 0093)

- **Дата:** 2026-10-21
- **Агент:** code-reviewer
- **Версия мода:** 2.15.0 (не выпущена)
- **Статус:** done
- **Связанные отчёты:** 0089, 0090, 0091, 0092, 0093, 0094

## Задача
Повторно проверить правки 0094 по замечаниям 0093. Отдельно сверить с настоящим Forge пакеты и сигнатуры Forge-классов в `stubs/` и импортах: неверный пакет даёт `NoClassDefFoundError` при запуске, как уже было с TamableAnimal. Затем проверить major 1–3, регрессии боссов, широту `DungeonGuard.onGrief` и `Boss3.tickRings`. Код не менял.

## Вердикт: **REQUEST CHANGES** (один major, правка небольшая)
Риска при запуске нет: все ссылки байткода мода на Forge и Minecraft проверены по настоящим jar Forge 47.4.10 и клиенту 1.20.1-srg, расхождений 0. Major 1 и 2 из 0093 закрыты. Major 3 закрыт по сути, но новый импульс за один тик сдвигает моба до 1,4 блока и проверяет только конечную клетку, поэтому толчок проносит мобов сквозь стены в 1 блок (см. major 1 ниже). Остальное — minor и nit.

## Сверка Forge-имён с настоящими jar (главное)
Локально нашлись библиотеки MultiMC: `C:\games\MultiMC\libraries\net\minecraftforge\forge\1.20.1-47.4.10\forge-1.20.1-47.4.10-universal.jar` (+ `-client.jar`), `eventbus-6.0.5.jar`, `fmlcore-1.20.1-47.4.10.jar`, `javafmllanguage-1.20.1-47.4.10.jar`, а также `C:\games\MultiMC\libraries\net\minecraft\client\1.20.1-20230612.114412\client-1.20.1-20230612.114412-srg.jar`.

Метод: `javap -v` по всем 155 классам `build/classes`. Собраны все `Class`, `Methodref`, `InterfaceMethodref` и `Fieldref` на `net/minecraft*` и `net/minecraftforge*`. Каждая ссылка искалась (имя + дескриптор) в настоящих jar с подъёмом по суперклассам и интерфейсам (`javap -p -s`, JDK 21). Скрипты лежат вне проекта, в scratchpad (`forgecheck.py`, `mccheck.py`).

- **Forge: 0 ошибок** (все классы и все члены найдены). Уверенность: проверено по jar.
  - `net.minecraftforge.event.level.PistonEvent` / `PistonEvent$Pre` есть. `getFaceOffsetPos()Lnet/minecraft/core/BlockPos;` объявлен в `PistonEvent`, `getLevel()Lnet/minecraft/world/level/LevelAccessor;` и `getPos()` — в `BlockEvent`.
  - `net.minecraftforge.event.entity.EntityMobGriefingEvent` есть (настоящий родитель `EntityEvent`, в заглушке — `Event`, на линковку это не влияет). `getEntity()` берётся из `EntityEvent`, `setResult(Event$Result)V` — из `eventbus.api.Event`.
  - `net.minecraftforge.eventbus.api.Event$Result.DENY` есть.
  - `EntityLeaveLevelEvent` (пакет `event.entity`), `ExplosionEvent$Detonate.getAffectedBlocks/getLevel`, `BlockEvent$BreakEvent/EntityPlaceEvent`, `FillBucketEvent.getTarget`, `MobSpawnEvent$FinalizeSpawn.getLevel()→ServerLevelAccessor/getSpawnType/setSpawnCancelled`, `EntityTeleportEvent$EnderPearl/ChorusFruit`, `LivingHurt/Heal/DeathEvent`, `PlayerEvent$PlayerRespawnEvent`, `PlayerInteractEvent$RightClickBlock/RightClickItem`, `TickEvent$LevelTickEvent.level/phase`, `ServerStopping/StoppedEvent`, `IEventBus.addListener(Consumer)`, `FMLJavaModLoadingContext`, `DeferredRegister.create(IForgeRegistry|ResourceKey,String)`, `ForgeRegistries.*`, `IForgeRegistry.getKey/getValue`, `RegistryObject.get` — всё есть.
- **Minecraft: 534 ссылки OK.** 3 «не найдено» оказались ложными (`Entity$RemovalReason.name()`, `MobSpawnType.name()`, `ArmorItem$Type.ordinal()` — методы `java.lang.Enum`, мой скрипт не поднимается в `java/*`). Новые SRG из 0092/0094 сошлись с дескрипторами: `ServerLevel.m_7726_()→ServerChunkCache`, `ServerChunkCache.m_8481_()→ChunkGenerator`, `m_214994_()→RandomState`, `ChunkGenerator.m_223221_(IILHeightmap$Types;LLevelHeightAccessor;LRandomState;)I`, `TamableAnimal` в `net/minecraft/world/entity`.
- Обёртки try/catch через рефлексию вокруг регистрации слушателей **не нужны**: имена подтверждены по jar той версии Forge, с которой играет пользователь (47.4.10).

## Замечания

### major
1. **[major] src/com/explosionwand/RiftMobs.java:522–523 (`Mob.applyKnock`)**: горизонтальный импульс ограничен 1,4 блока за тик (`knock`, :504), а `spaceFor` проверяет только конечную клетку. Если моб стоит на x=0,9 и получает kx=1,4, он попадает на x=2,3, и стена в клетке 1 перепрыгивается. Ветер (`WIND_PUSH 1.5` → 1,4), Комета (`1.2·look`) и тройное притяжение (импульсы складываются до 1,4) так проносят мобов сквозь стены, перегородки и двери-печати в 1 блок: в соседнюю комнату, за печать зала или наружу. У широких хостов (`half`) то же самое, проверяются только углы цели. В итоге моб недоступен, зал не засчитывается до сброса по `hallStale`, плюс дешёвый способ выводить мобов из запечатанного зала. По вертикали прохода нет, там |ky| ≤ 1. → Двигать подшагами: `int n = (int)Math.ceil(Math.max(|kx|,|kz|) / 0.45)`, и на каждом подшаге та же проверка по осям (`spaceFor` + `leashBlocked`), обнуляя компоненту при упоре. Добавить в `RiftMobsTest` случай «стена 1 блок, kx=1,4 из x=0,9 → моб остался перед стеной».

### minor
2. **[minor] src/com/explosionwand/DungeonGuard.java:94–96 (`onPiston`)**: проверяется только линия по направлению поршня. Слизь и мёд, поставленные снаружи вплотную к стене данжа, тянут соседние блоки вбок, и стена уходит вместе с ними. Поршень снаружи, сдвигающий слизь, тоже двигает блоки данжа, хотя ни одна клетка линии в `guarded` может не попасть. → Если в радиусе 13 от поршня есть `guarded` (предфильтр уже есть, `pad 14`), отменять событие целиком. Это проще и закрывает все варианты, а поршни игрока у данжа до победы не нужны.
3. **[minor] src/com/explosionwand/DungeonGuard.java:100–104 (`onGrief`)**: охват приемлемый: только данжи с живым боссом, ±6 блоков, только серверная сторона. Но `EntityMobGriefingEvent` в 1.20.1 решает ещё и подбор предметов мобами (`Mob.aiStep`), фермерство жителей, поедание травы овцами и выпадение кроличьей морковки. У входа в Разлом и в Городе зомби там перестанут поднимать брошенную броню. Не ломает, но это побочный эффект. Огненные шары гаста проверяются по позиции владельца, взрыв всё равно режет `onDetonate`. → Можно сузить до `EnderMan` (а также `Ravager`/`Zombie`, ломающих двери, если такие есть в данже), остальным оставить DEFAULT. Если оставить как есть, написать об этом в docs/dungeons.md.
4. **[minor] src/com/explosionwand/Dungeons.java:806–807 (`column`)**: на каждую незагруженную колонну идут два полных шумовых прохода `m_223221_` (WORLD_SURFACE_WG и OCEAN_FLOOR_WG): ~36 колонн × 2 × 3 точки ≈ 200 вызовов раз в 10 тиков на клетку. Это намного лучше, чем было (до ~2300 за тик), но всё ещё заметные миллисекунды. Если рядом стоят два игрока, `registerCover` зовётся дважды за проход. → Проверка воды нужна только на точке входа и при `floor` близком к уровню моря, вторую оценку можно пропускать, если `floor > seaLevel + 2` (`m_5736_`). Проверить TPS в игре.
5. **[minor] reports/0094 «Git»**: список неполный. В рабочем дереве не закоммичена вся фаза 4.7 (последний коммит — 2.14.0), а в команды 0094 не попали: `CLAUDE.md`, `ROADMAP.md`, `resources/META-INF/mods.toml`, оба lang, удалённые `Arena3.java`, `DreamSealItem.java`, `Ember.java`, `dream_seal.*` (3 файла), новые `ash_seal_shard.*`, `docs/concepts/mobs_ash.png`, `Ticker.java`, `jei/ExplosionWandJei.java`, `tools/gen_dungeons.py`, заглушки `decoration/ArmorStand`, `player/Inventory`, `projectile/FishingHook|ThrownEnderpearl|ThrownTrident`, `event/entity/EntityLeaveLevelEvent`, а также отчёты 0084–0088. Коммит строго по списку 0094 не соберётся: `Earth` ссылается на заглушки `FishingHook` и `ThrownTrident`, которых в коммите нет. → Полный список ниже, в «Git».

### nit
6. **[nit] RiftMobs.java:422/424**: импульс, полученный во время Стоп-Кадра, копится (`knock` идёт мимо проверки заморозки) и срабатывает сразу после разморозки. Это скорее фича, но в docs об этом стоит написать или не принимать `knock`, пока `frozenOf(host) != null`.
7. **[nit] Earth.java:517–519 / Comet.java:70**: статуя (`knockable()==false`) получает `false` и проваливается в ванильную ветку `setDeltaMovement + hurtMarked`. Для NoAI-хоста это ничего не делает, но комментарий в `knock` («толкать обычным способом») вводит в заблуждение. → Вернуть `true` («обработано, не двигаем») для мобов данжа, которых нельзя толкать.
8. **[nit] tools/classcheck.py**: проверяет только пути классов по `mojang.txt`. Найденные jar MultiMC позволяют проверять члены и дескрипторы, то есть ловить и TamableAnimal-подобные ошибки, и ошибки в методах Forge. → `build-engineer`: необязательный шаг `[2c] linkcheck`: если есть `C:\games\MultiMC\libraries\...` (путь из переменной окружения или конфига), прогнать сверку, как в этом ревью. Без jar шаг пропускается.
9. **[nit] reports/0094**: строка `Co-Authored-By: Claude Sonnet 5.5`. Проверить, что модель указана верно.

## Что проверено и в порядке
- **major 1 (0093), миграция bossDead:** `DungeonData.migrateBossDead()` вызывается в `load` после `checkLayouts`, поэтому сброшенные по layout записи не мигрируют. Бит зала берётся как `1L << h.id`, так же как в `inDungeon`/`lairWon`. Зал с боссом засчитывается только через `lairWon`: `clearable(hasBoss=true)` всегда false (Dungeons.java:998), так что ложной миграции нет. Миграция идемпотентна, есть тест.
- **major 2 (0093), посадка без синхронных чанков:** в `registerCover` кандидаты читаются через `column` (загруженный чанк или генератор, без `m_6325_`), по 3 за проход, состояние лежит в `SITES` и чистится в `clearState`. `m_6325_` вызывается один раз, только у выбранной точки (или у основной в запасном пути). `DungeonSite.choose` теперь реально используется (minor 7 закрыт). Вода: `surf > floor` для незагруженных, `wet` для загруженных, мокрая колонна = `WET` (minor 4 закрыт).
- **major 3 (0093), импульс:** NoAI и NoGravity не трогаются. Двигает `setPos` с обнулением скорости, во время полёта `think()` не вызывается. Поводок работает и в полёте. По вертикали стены и потолок не проходятся. Застревания или бесконечного «airborne» нет: внутри блока все оси обнуляются, и `supported=true`. У летунов гравитации нет, импульс затухает. Дюпов нет: дроп хостов пустой, материалы только за убийство игроком. Лоза (`rootUntil`) блокирует `walk/fly`, стрелять моб может. Статуя не толкается. Остаётся только проход сквозь тонкие стены (major 1 выше).
- **minor 6 (0093):** `envBlockedForMobs("inFire"/"onFire", wandFire)` пропускает только огонь от палочки (`burnUntil`), солнечный огонь гасится в `step`, молнии проходят.
- **minor 9 (0093), Boss3.tickRings:** ≤100 частиц на кольцо, только на чётных тиках, отсечение за стеной зала. Урон 9, откат 10 тиков, скорость 0,55, `RING_MAX` не менялись. Регрессий нет.
- **Боссы:** кроме `tickRings`, правки 0094 боссов не касаются. Хост Монолита 1024 (лимит соблюдён), `DMG_K = 1024/1700`, как в 0093.
- **Регистрация:** `onPiston`/`onGrief` подписаны через `MinecraftForge.EVENT_BUS.addListener(DungeonGuard::...)`, как и остальные слушатели, тип события выводится из ссылки на метод. `PistonEvent` в Forge `@Cancelable`, `setCanceled` есть. Проверка `f_46443_` (isClientSide) стоит в обоих обработчиках.
- **nit 11–13 (0093):** classcheck строг к вложенным классам, без сети выдаёт понятную ошибку. `javac.*.args` из корня удалён, однократный лог в `column` есть.

## Проверка
- `python tools/build.py`: 155 классов, SRG исходники/байткод 246/247, неизвестных 0; пути классов 101 / 0 ошибок; ID 0 проблем; gen_dungeons 7/0; тесты OK (BossCityTest, BossRiftTest, CityMobsTest, CityTest, DungeonTest, EarthTest, MatsTest, MazeTest, RiftMobsTest, RiftStaffTest); jar `dist/explosionwand-1.20.1-2.15.0.jar`.
- Сверка байткода с jar Forge 47.4.10 и клиентом 1.20.1-srg: Forge 0 ошибок, Minecraft 534 OK (3 ложных — методы `Enum`).
- Новых SRG в 0094 нет.

## Проверить в игре
- Ветер, Комета и притяжение у мобов данжа рядом со стеной или перегородкой в 1 блок и у двери-печати: не проходят ли насквозь (major 1).
- Слизь или мёд с поршнем снаружи вплотную к стене Разлома или Города до победы (minor 2).
- Зомби у входа в данж с живым боссом не подбирают предметы. Терпимо ли это (minor 3)?
- TPS при подходе к новому Разлому (оценка точек, minor 4).
- Остальное — как в «Проверить в игре» отчёта 0094 (старый мир с убитым Сердцем, лоза, огонь, кольца Монолита).

## Решения и почему
- Обёртки через рефлексию не предлагаю: ссылки проверены по jar именно той сборки Forge, что стоит у пользователя. Риск `NoClassDefFoundError`/`NoSuchMethodError` от Forge-имён закрыт фактом, а не оценкой.
- REQUEST CHANGES из-за прохода сквозь стены: это заметный эксплойт (мобы выносятся из запечатанных залов), а правка подшагами занимает несколько строк. Всё остальное можно коммитить.

## Проблемы и открытые вопросы
- Огонь, распространяющийся по деревянным блокам Города, по-прежнему не перехвачен (в 0094 перенесён в 4.8).

## Git (выполняет пользователь)
Вся незакоммиченная работа (фаза 4.7 + 0089–0095) одним набором, `git add` по явному списку:
```
git add CLAUDE.md ROADMAP.md docs/dungeons.md docs/history.md docs/world.md docs/concepts/mobs_ash.png resources/META-INF/mods.toml resources/assets/explosionwand/lang/en_us.json resources/assets/explosionwand/lang/ru_ru.json
git add resources/assets/explosionwand/models/item/dream_seal.json resources/assets/explosionwand/textures/item/dream_seal.png resources/data/explosionwand/recipes/dream_seal.json resources/assets/explosionwand/models/item/ash_seal_shard.json resources/assets/explosionwand/textures/item/ash_seal_shard.png
git add resources/data/explosionwand/structures/sleep_mid.nbt resources/data/explosionwand/structures/sleep_north.nbt resources/data/explosionwand/structures/sleep_south.nbt
git add src/com/explosionwand/Altar.java src/com/explosionwand/Arena3.java src/com/explosionwand/ArenaGuard.java src/com/explosionwand/Boss.java src/com/explosionwand/Boss3.java src/com/explosionwand/CityMobsB.java src/com/explosionwand/Comet.java src/com/explosionwand/Dims.java src/com/explosionwand/DreamSealItem.java src/com/explosionwand/DungeonData.java src/com/explosionwand/DungeonGuard.java src/com/explosionwand/DungeonSite.java src/com/explosionwand/Dungeons.java src/com/explosionwand/Earth.java src/com/explosionwand/Ember.java src/com/explosionwand/ExplosionWandMod.java src/com/explosionwand/Minions.java src/com/explosionwand/RiftMobs.java src/com/explosionwand/RiftStaff.java src/com/explosionwand/Ticker.java src/com/explosionwand/TimeStop.java src/com/explosionwand/WhaleStaff.java src/com/explosionwand/jei/ExplosionWandJei.java
git add stubs/net/minecraft/server/level/ServerLevel.java stubs/net/minecraft/server/level/ServerChunkCache.java stubs/net/minecraft/world/entity/Entity.java stubs/net/minecraft/world/entity/animal/TamableAnimal.java stubs/net/minecraft/world/entity/TamableAnimal.java stubs/net/minecraft/world/entity/decoration/ArmorStand.java stubs/net/minecraft/world/entity/player/Player.java stubs/net/minecraft/world/entity/player/Inventory.java stubs/net/minecraft/world/entity/projectile/Projectile.java stubs/net/minecraft/world/entity/projectile/FishingHook.java stubs/net/minecraft/world/entity/projectile/ThrownEnderpearl.java stubs/net/minecraft/world/entity/projectile/ThrownTrident.java
git add stubs/net/minecraft/world/level/BlockGetter.java stubs/net/minecraft/world/level/Level.java stubs/net/minecraft/world/level/LevelAccessor.java stubs/net/minecraft/world/level/LevelHeightAccessor.java stubs/net/minecraft/world/level/chunk/ChunkGenerator.java stubs/net/minecraft/world/level/levelgen/RandomState.java stubs/net/minecraftforge/event/level/BlockEvent.java stubs/net/minecraftforge/event/level/PistonEvent.java stubs/net/minecraftforge/event/entity/EntityMobGriefingEvent.java stubs/net/minecraftforge/event/entity/EntityLeaveLevelEvent.java stubs/net/minecraftforge/eventbus/api/Event.java
git add tests/DungeonTest.java tests/EarthTest.java tests/BossCityTest.java tools/build.py tools/classcheck.py tools/gen_city.py tools/gen_dungeons.py
git add reports/0084-2026-10-19-mod-engineer-review-tails-4-11.md reports/0085-2026-10-21-boss-engineer-monolith-temple.md reports/0086-2026-10-21-asset-engineer-release-2-15-0.md reports/0087-2026-10-21-code-reviewer-phase-4-7.md reports/0088-2026-10-21-main-phase-4-7-closed.md reports/0089-2026-10-21-build-engineer-class-path-audit.md reports/0090-2026-10-21-mod-engineer-rift-staff-revert.md reports/0091-2026-10-21-boss-engineer-monolith-arena-hp.md reports/0092-2026-10-21-mod-engineer-rift-spawn-wands-dungeons.md reports/0093-2026-10-21-code-reviewer-post-playtest-2-15-0.md reports/0094-2026-10-21-mod-engineer-review-fixes-0093.md reports/0095-2026-10-21-code-reviewer-recheck-0094.md
git commit -m "feat(dungeon): Город v3, Монолит в храме, барьер до победы (2.15.0)" -m "- миграция bossDead, посадка Разлома без синхронных чанков" -m "- импульс и лоза для мобов данжей, поршни и мобгрифинг у данжа" -m "- ревью 0093/0095: Forge-имена сверены с jar 47.4.10" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
Если фаза 4.7 уже закоммичена по 0088, `git add` пропустит неизменённые файлы. Лучше сначала закрыть major 1 и потом коммитить вместе с его отчётом.

## Следующий шаг
- `mod-engineer`: major 1 (подшаги в `applyKnock` + тест), minor 2 (поршень: отменять целиком у охраняемого данжа), minor 3 (сузить `onGrief` или описать в docs), minor 4 (вторая оценка `m_223221_` только у уровня моря), nit 6–7.
- `build-engineer`: nit 8 (необязательный linkcheck по jar MultiMC).
- Затем короткое повторное ревью только `applyKnock`.
