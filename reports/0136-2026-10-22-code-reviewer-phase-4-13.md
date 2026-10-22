# 0136 · Ревью фазы 4.13 (2.19.2 → 3.0.0)

- **Дата:** 2026-10-22
- **Агент:** code-reviewer
- **Версия мода:** 3.0.0
- **Статус:** done
- **Связанные отчёты:** 0129, 0130, 0131, 0132, 0133, 0134, 0135

## Задача
Ревью фазы 4.13 перед коммитом: фикс 2.19.2, Ключ Ада и портал-алтарь, Ад-заглушка, Затмение и Кит убраны с Земли, Дневник Архимага, баланс, релиз 3.0.0. Код не менялся.

## Вердикт: **REQUEST CHANGES**
Одно серьёзное замечание: портал, который должен быть «навсегда», смывает водой или лавой. Чинится одной строкой. Остальное minor и nit, их можно отложить.

## Что проверено
- `python tools/build.py`: 194 класса, SRG в исходниках 276 и в байткоде 277, неизвестных 0. Члены net/minecraft: 113, без проблем. linkcheck по настоящим jar Forge (8 jar): 0 расхождений и 0 непроверенных. Проблем с ID 0, gen_dungeons OK. Все 19 тестов OK, включая HellGateTest и JournalTest. Jar `dist/explosionwand-1.20.1-3.0.0.jar`.
- Новые SRG проверены через `lookup.py --srg`, смысл совпадает: `m_284310_` Settings.create (=Properties.of), `m_60913_` strength, `m_60953_` luminance, `m_60955_` nonOpaque, `m_60910_` noCollision, `m_222994_` dropsNothing (=noLootTable), `m_7892_` onEntityCollision (=entityInside), `m_6225_` useOnBlock, `m_43725_/m_8083_/m_43723_/m_43722_` UseOnContext getWorld/getBlockPos/getPlayer/getStack, `m_135782_` RegistryKey.getValue, `m_135785_` RegistryKey.of, `m_36054_` insertStack, `m_19983_` dropStack, `m_7203_` Item.use, `m_220360_` getSpawnPos, `m_6325_` getChunk, `m_49966_` getDefaultState, `m_60734_` getBlock, `m_7500_` isCreative, `m_41774_` decrement, NBT `m_128405_/m_128359_/m_128451_/m_128461_/m_128441_/m_128379_/m_128471_/m_128469_/m_128365_`, `m_6084_` isAlive.
- **(1) Первый старт.** `BLOCKS` создаётся через `DeferredRegister`, в шине регистрируется раньше `ITEMS`. `BlockItem` получает блок внутри поставщика (`.get()` вызывается лениво). Для 4 блоков есть blockstates, модели и текстуры: портал 16x128 с mcmeta, у остальных 16x16. Item-модели `seal_stone`/`phoenix_embers` ссылаются на модели блоков, `hell_key` и `archmage_journal` используют item/generated. Lang `block.*`/`item.*` есть для всех 6 позиций. Лут-таблицы seal_stone/phoenix_embers валидны, тег mineable/pickaxe с `replace:false`. В JEI ссылки только на существующие предметы (у блоков без предмета, hell_socket и hell_portal, страниц нет). Лямбды `s -> 8` реализуют `java.util.function.ToIntFunction`, а не интерфейс MC, поэтому SRG им не нужно. Причин для краша при старте не нашёл.
- **(1) Портал.** Перенос через `changeDimension` с ITeleporter вызывается прямо из `entityInside`, ванильный EndPortalBlock делает так же. Клиент и мёртвые отсекаются. Нужно простоять в портале 30 тиков, после переноса перезарядка 100 тиков. Обратно игрок выходит в 2 блоках от плоскости портала, так что снова сразу не затянет. `STAND`/`LAST` чистятся в `onLogoutEvent`. Точка возврата лежит в корне `getPersistentData` и после смерти теряется. Это нормально: она перезаписывается при каждом входе, а без записи игрок выходит на мировой спавн. Зажигание запрещено в наших измерениях (`HellKeyItem`, `Dims.isOurs`), в данже (`protectedAt` по гнезду и по 9 клеткам проёма) и при занятом гнезде. Портал и гнездо неломаемые (-1/3600000): поршни их не двигают, эффекты палочек их пропускают (везде проверка `m_60800_ < 0`).
- **(2) Dims.HELL** входит в `isOurs`, поэтому палочки Земли и возврат домой там не работают, а портал там не зажечь. JSON измерения, типа и биома валидны: flat с одним слоем air, height 256, min_y 0, ultrawarm, мобов нет, формат как у eclipse.
- **(3) Altar.ENABLED=false.** Печати показывают `seal.later` и выходят до любых проверок. Файлы `eclipse_seal`/`sky_seal`/`eclipse_staff` удалены из `recipes/`. Ссылок на них нет ни в рецептах, ни в advancements (папки advancements нет), ни в JEI: там только info-страницы. Папку `recipes_off/` Forge не читает. Посох Затмения лежит в сундуке Boss2 (изменение 2.19.1).
- **(4) Дневник.** `Dungeons.onCleared` выставляет флаг всем игрокам из `in`, если `h.boss != null`, и делает это до раннего `return` по сундуку. Метки `monolith`/`phoenix` совпадают с `Boss.isMonolith/isPhoenix` и `BOSS_KINDS`. `onLogin` выдаёт дневник один раз по флагу в `PlayerPersisted`, флаг переживает смерть, дублей нет. Если места нет, дневник падает под ноги.
- **(5) EclipseStaff.Spear:** `!Cmd.hasTagPrefix(x, "ew")` стоит в фильтре `m_6249_`, так же как у Лун и Сверхновой.
- **(6) 2.19.2.**
  - `canReach` вне Верхнего мира и вне данжей сразу возвращает true. Проходит, если чист хотя бы один луч из двух.
  - `spawnKind`: огонёк с `ewwh_` не попадает в `summoned`. `adopt` восстанавливает `Wisp.home`, тег `_min` не задевается.
  - WhaleStaff: сообщение берётся из lang, при тихой отмене вызывается `CD.remove`.
  - `ArmorSets.onLogout` чистит MODS/EDGE.
  - `ArcaneMaterial.DUR = {11,16,15,13}` совпадает с порядком `ArmorItem.Type` HELMET..BOOTS и с ванилью.
  - `isFireSource` больше не считает огнём unattributedFireball.
- **(7) Боссы не ослаблены.** В diff `Boss*.java` только Boss2 (Посох Затмения в сундуке) и Boss5 (`PERCH_TICKS=60`), оба из 2.19.1 (отчёт 0126, ревью 0129). В шагах 0130–0135 Boss*.java не менялись.
- **(8) Правила.** Enum берутся через `valueOf` (InteractionResult, ChatFormatting, ClipContext, HitResult, Rarity). Блоки, частицы и звуки идут через `Fx`, title через `Cmd`. Массовая постановка блоков есть только в `ensureHell` (измерение hell, данжей там нет) и в `light` (проём проверен по щиту). Lang ru/en: по 388 ключей, наборы совпадают. Все JSON и mcmeta в `resources/` валидны.

## Замечания

1. **[major]** `src/com/explosionwand/ExplosionWandMod.java:131` (`HELL_PORTAL`): у блока с `noCollission` выходит `isSolid()==false`. Тогда `blocksMotion()` тоже false, и `FlowingFluid.canHoldFluid` вместе с `BlockBehaviour.canBeReplaced(state, fluid)` разрешают воде и лаве **заменить блок портала**. Достаточно растёкшейся воды, ведра в проём сбоку или лавы в Незере. `noLootTable` ничего не вернёт, а ключ уже потрачен, так что портал пропадает «навсегда». Хуже на стороне Ада: если смыть обратный портал (вода из ведра), `ensureHell` его не восстановит, потому что маркер — центр пола. Возврат домой в `isOurs` закрыт, кровати там взрываются, и игрок застревает до смерти. → Добавить в Properties `.m_280606_()` (Settings.solid = forceSolidOn; SRG из `lookup.py`, перед правкой проверить `--srg`). Либо реализовать `LiquidBlockContainer` и возвращать false в `canPlaceLiquid`. Дополнительно (см. 2): `ensureHell` должен восстанавливать обратный портал, если его нет.
2. **[minor]** `src/com/explosionwand/HellGate.java:205`: маркер постройки — блок незерака (0,63,0). Если его сломать, при следующем входе площадка строится заново: в цилиндре r=9 по y 64–74 всё затирается воздухом, и рамка снова ставится из 15 Печатных камней. → Хранить флаг «построено» в данных уровня (SavedData или `getPersistentData` уровня). При входе отдельно проверять гнездо обратного портала и перестраивать только портал.
3. **[minor]** `HellGate.buildLit` в Аду: рамка обратного портала из обычного Печатного камня, её ломают киркой (15 штук бесплатно, а с замечанием 2 — бесконечно). Портал при этом продолжает работать. → Ставить в Аду неломаемую рамку (отдельный блок или `strength(-1)`-вариант) либо не давать лут на этой площадке.
4. **[minor]** `resources/data/minecraft/tags/blocks/`: `hell_portal` и `hell_socket` не входят в `wither_immune`/`dragon_immune`, поэтому визер их ломает (у бедрока защита как раз через этот тег). → Добавить теги `wither_immune.json` и `dragon_immune.json` (`replace:false`).
5. **[minor]** `src/com/explosionwand/Chronos.java:126`: откат снимка ставит старое состояние, не глядя на твёрдость. Если зажечь портал внутри активного Хроноса, гнездо вернётся к Углям, а проём к воздуху, и ключ пропадёт. Это старый код, но теперь он задевает «вечный» портал. → В restore пропускать клетки, где текущий блок `m_60800_ < 0`.
6. **[nit]** `resources/data/explosionwand/loot_tables/blocks/hell_socket.json`: таблица не используется, у блока стоят `noLootTable` и `strength(-1)`. В 0131 написано, что гнездо «при разрушении дропает Угли», это неверно. → Удалить таблицу или поправить описание.
7. **[nit]** `src/com/explosionwand/HellPortalBlock.java`: полупрозрачный `cube_all` без `skipRendering` (`m_6104_`) рисует грани между соседними блоками проёма, и портал выглядит слоистым. → Переопределить `skipRendering` для того же блока, как у HalfTransparentBlock (проверить в игре).
8. **[nit]** `HellGate` открытый вопрос 0131: если сломать рамку, проём продолжает висеть. Для «вечного» портала это приемлемо, но стоит записать в docs/world.md.
9. **[nit]** `src/com/explosionwand/HellKeyItem.java:25`: на клиенте возвращается `SUCCESS` по любому блоку, а сервер для блоков не-Углей возвращает `PASS`. Получается лишний взмах руки. → На клиенте тоже отдавать PASS, если блок не Угли и не гнездо.
10. **[nit]** `src/com/explosionwand/Journal.java:126-129`: `pagesOf` вызывается дважды. → Достаточно одного вызова.
11. **[nit]** `en_us.json`: одни и те же вещи названы по-разному: item «Dreaming Eye» и в дневнике «Unsleeping Eye»; «City of Sleepers» и «City of the Sleepers»; «Ash Sanctuary» и «Ashen Sanctuary»; «Paradise» (seal.later, JEI) и «Heaven» (journal.p0). → Свести к одним именам.
12. **[nit]** Отчёты 0133 и 0135 написаны не по шаблону: нет разделов «Задача», «Изменённые файлы», «Решения», «Следующий шаг». → В следующий раз заполнять полностью.
13. **[major, решение пользователя, не код]** Критерий фазы «бои укладываются в цели по времени»: по расчёту 0132 Феникс идёт около 6 мин при цели около 4. Ослаблять боссов пользователь запретил, а вариант «поднять урон игрока» не выбран. → Перед закрытием фазы нужно решение пользователя или замер в игре. Без этого 4.13 закрывается с оговоркой.

## Проверить в игре
- Старт клиента и сервера без крашей. Во вкладке «Магия» есть Печатный камень, Угли Феникса, Ключ Ада, Дневник. Текстуры не фиолетовые, проём анимирован и полупрозрачный.
- Рамка вдоль X и вдоль Z, ключ зажигает портал. Неполная рамка или рамка в данже: сообщение, ключ цел. В Аду, Перевёртыше и Лабиринте ключ не работает.
- Портал: стоя неподвижно, через 1,5 с переносит в Ад. Появляется заголовок, обратный портал работает, возврат в 2 блоках перед алтарём. Туда и обратно много раз, перезаход в мир, рестарт сервера: портал на месте.
- **Вода и лава у проёма (major 1):** вылить ведро рядом с порталом, в Аду и на Земле. Сейчас портал, скорее всего, смоет. После правки не должен.
- Смерть в Аду: возрождение у кровати или на спавне. Выход из игры в Аду и повторный вход.
- Дневник: выдаётся при первом входе один раз, после перезахода и смерти не выдаётся. Страницы 2/3/4/5 открываются по победам, ключу и порталу. Длинные строки читаются в чате.
- Печати Затмения и Неба: сообщение, алтаря нет, рецептов нет. Посох Затмения: Столп не бьёт Монолита и Феникса.
- Посохи в данжах и вне их (2.19.2): шаг и нырок сквозь стену вне данжа работают, в данже нет. Низкий забор не мешает.
- Замер боёв с Монолитом (цель 2–3 мин) и Фениксом (цель около 4).

## Решения и почему
- Вердикт REQUEST CHANGES только из-за major 1. Это единственный найденный путь безвозвратно потерять «вечный» портал и застрять в Аду, а правка занимает одну строку. После неё фазу можно коммитить как APPROVE WITH NITS.
- Пункты 4.12 и 2.19.1 не закоммичены (0129), и общие файлы (`ExplosionWandMod`, `Dungeons`, `ExplosionWandJei`, lang, `Entity`/`PlayerEvent`, `docs/world.md`, `mods.toml`, `history.md`) содержат правки нескольких шагов. Поэтому 4.12, 2.19.1 и 2.19.2 объединены в один коммит, а общие файлы идут в последний шаг, который их трогает. Промежуточные коммиты могут не собираться по отдельности. Для чистой истории нужен `git add -p`.

## Проблемы и открытые вопросы
- Феникс дольше цели (замечание 13): решение за пользователем.

## Git (выполняет пользователь)
Выполнять **после** правки major 1, отчёт фикса добавить в коммит 2. Команды под PowerShell, пробелов в путях нет.
```
git add src/com/explosionwand/ArmorSetLogic.java src/com/explosionwand/ArmorSets.java src/com/explosionwand/SetMaterial.java src/com/explosionwand/SetArmorItem.java src/com/explosionwand/Ticker.java src/com/explosionwand/RiftMobs.java src/com/explosionwand/CityMobs.java src/com/explosionwand/CityMobsB.java src/com/explosionwand/City.java src/com/explosionwand/AshMobs.java src/com/explosionwand/AshMobsB.java src/com/explosionwand/AshLogic.java src/com/explosionwand/Boss2.java src/com/explosionwand/Boss5.java src/com/explosionwand/DungeonGuard.java src/com/explosionwand/RiftStaff.java src/com/explosionwand/WhaleStaff.java src/com/explosionwand/Earth.java src/com/explosionwand/EarthLogic.java src/com/explosionwand/ExplosionWandItem.java src/com/explosionwand/ArcaneMaterial.java
git add stubs/net/minecraft/world/entity/LivingEntity.java stubs/net/minecraft/world/entity/projectile/Projectile.java stubs/net/minecraft/world/entity/projectile/AbstractHurtingProjectile.java stubs/net/minecraft/world/level/Level.java stubs/net/minecraftforge/event/entity/living/LivingEvent.java stubs/net/minecraftforge/registries/ForgeRegistries.java stubs/net/minecraft/world/entity/ai/attributes stubs/net/minecraft/world/item/enchantment stubs/net/minecraftforge/event/entity/ProjectileImpactEvent.java stubs/net/minecraftforge/event/entity/living/LivingKnockBackEvent.java
git add tests/ArmorSetsTest.java tests/AshMobsTest.java tests/BossAshTest.java tests/RiftStaffTest.java resources/data/explosionwand/recipes/eclipse_staff.json docs/dungeons.md
git add "resources/assets/explosionwand/textures/item/crystal_*.png" "resources/assets/explosionwand/textures/item/dreamer_*.png" "resources/assets/explosionwand/textures/item/ash_*.png" resources/assets/explosionwand/textures/models/armor "resources/assets/explosionwand/models/item/crystal_*.json" "resources/assets/explosionwand/models/item/dreamer_*.json" "resources/assets/explosionwand/models/item/ash_*.json"
git add reports/0122-2026-10-22-boss-engineer-nits-0121.md reports/0123-2026-10-22-mod-engineer-armor-sets.md reports/0124-2026-10-22-asset-engineer-release-2-19-0.md reports/0126-2026-10-22-boss-engineer-fix-2-19-1.md reports/0127-2026-10-22-mod-engineer-fix-2-19-1.md reports/0130-2026-10-22-mod-engineer-fix-2-19-2.md
git commit -m "feat(items): сеты брони данжей Земли, фиксы 2.19.1 и 2.19.2" -m "- три сета по 4 части с бонусами, Грань отражает лучи мобов" -m "- посохи в данжах только в видимую точку своего зала, вне данжей как раньше" -m "- огонёк: ключ Святилища в теге хоста, 1 сундук за ночь" -m "- нырок Кита: lang и возврат перезарядки; прочность Облачения Архимага" -m "- рецепт Посоха Затмения убран, посох в сундуке Короля"

git add src/com/explosionwand/HellGate.java src/com/explosionwand/HellPortalBlock.java src/com/explosionwand/HellKeyItem.java src/com/explosionwand/Dims.java stubs/net/minecraft/world/level/block/Block.java stubs/net/minecraft/world/level/block/state/BlockBehaviour.java stubs/net/minecraft/world/item/Item.java stubs/net/minecraft/resources/ResourceKey.java stubs/net/minecraft/world/item/BlockItem.java stubs/net/minecraft/world/item/context/UseOnContext.java stubs/net/minecraft/world/InteractionResult.java tests/HellGateTest.java tools/gen_hell_assets.py
git add resources/assets/explosionwand/textures/block resources/assets/explosionwand/textures/item/hell_key.png resources/assets/explosionwand/blockstates resources/assets/explosionwand/models/block resources/assets/explosionwand/models/item/seal_stone.json resources/assets/explosionwand/models/item/phoenix_embers.json resources/assets/explosionwand/models/item/hell_key.json
git add resources/data/explosionwand/recipes/hell_key.json resources/data/explosionwand/recipes/seal_stone.json resources/data/explosionwand/recipes/phoenix_embers.json resources/data/explosionwand/loot_tables resources/data/explosionwand/dimension/hell.json resources/data/explosionwand/dimension_type/hell.json resources/data/explosionwand/worldgen/biome/hell.json resources/data/minecraft reports/0131-2026-10-22-mod-engineer-hell-portal.md reports/0133-2026-10-22-asset-engineer-hell-textures.md
git commit -m "feat(dims): Ключ Ада, постоянный портал-алтарь и Ад-заглушка" -m "- блоки Печатный камень, Угли Феникса, гнездо, проём портала" -m "- портал двусторонний, возврат к своему алтарю" -m "- измерение hell: площадка, обратный портал, заголовок" -m "- текстуры (анимированный проём), рецепты, тест раскладки"

git add src/com/explosionwand/Altar.java src/com/explosionwand/EclipseSealItem.java src/com/explosionwand/SkySealItem.java src/com/explosionwand/EclipseStaff.java resources/data/explosionwand/recipes/eclipse_seal.json resources/data/explosionwand/recipes/sky_seal.json resources/data/explosionwand/recipes_off docs/balance_earth.md reports/0132-2026-10-22-boss-engineer-earth-cleanup-balance.md
git commit -m "feat(boss): Затмение и Кит убраны с Земли, расчёт баланса" -m "- печати не ставят алтарь, рецепты перенесены в recipes_off" -m "- Столп солнца не бьёт ew*-сущностей" -m "- docs/balance_earth.md: время боёв с Монолитом и Фениксом"

git add src/com/explosionwand/Journal.java src/com/explosionwand/JournalItem.java src/com/explosionwand/ExplosionWandMod.java src/com/explosionwand/Dungeons.java src/com/explosionwand/jei/ExplosionWandJei.java stubs/net/minecraft/world/entity/player/Inventory.java stubs/net/minecraft/world/entity/Entity.java stubs/net/minecraftforge/event/entity/player/PlayerEvent.java tests/JournalTest.java tools/gen_journal_assets.py
git add resources/assets/explosionwand/textures/item/archmage_journal.png resources/assets/explosionwand/models/item/archmage_journal.json resources/data/explosionwand/recipes/archmage_journal.json resources/assets/explosionwand/lang/ru_ru.json resources/assets/explosionwand/lang/en_us.json docs/world.md reports/0134-2026-10-22-mod-engineer-archmage-journal.md
git commit -m "feat(items): Дневник Архимага; регистрация и тексты 4.12-4.13" -m "- выдаётся один раз при первом входе, крафт книга + аметист" -m "- страницы по победам над боссами Земли, Ключу и порталу" -m "- регистрация блоков и предметов, JEI, lang ru/en, флаги в onCleared"

git add resources/META-INF/mods.toml docs/history.md reports/0128-2026-10-22-asset-engineer-release-2-19-1.md reports/0135-2026-10-22-asset-engineer-release-3-0-0.md
git commit -m "build(build): релиз 3.0.0" -m "- версия 3.0.0 в mods.toml" -m "- записи 2.19.0-2.19.2 и 3.0.0 в docs/history.md"

git add reports/0125-2026-10-22-code-reviewer-phase-4-12.md reports/0129-2026-10-22-code-reviewer-fix-2-19-1.md reports/0136-2026-10-22-code-reviewer-phase-4-13.md
git commit -m "docs(agents): ревью 4.12, 2.19.1 и фазы 4.13" -m "- 0136: REQUEST CHANGES, портал смывает водой и лавой (major)" -m "- 4 minor, 7 nit; Феникс дольше цели ждёт решения пользователя"

git add ROADMAP.md
git commit -m "docs(agents): фаза 4.13 закрыта, 3.0.0"
```
ROADMAP коммитить последним, после того как main его обновит.

## Следующий шаг
- **mod-engineer** (фикс 3.0.1): major 1 (`.m_280606_()` у HELL_PORTAL, при желании и у HELL_SOCKET) и восстановление обратного портала в `ensureHell`; minor 2–5; nit 6, 7, 9, 10.
- **asset-engineer**: nit 11 (имена в en), теги wither_immune/dragon_immune (minor 4).
- **Пользователь**: Феникс около 6 мин против цели около 4: поднять урон игрока или принять как есть.
- **code-reviewer**: перепроверить фикс 3.0.1.
