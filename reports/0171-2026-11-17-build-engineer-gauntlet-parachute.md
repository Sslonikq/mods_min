# 0171 · Проверка и выпуск 3.6.0: Перчатка Бесконечности и Парашют

- **Дата:** 2026-11-17
- **Агент:** build-engineer
- **Версия мода:** 3.5.0 -> 3.6.0
- **Статус:** done
- **Связанные отчёты:** 0169, 0170

## Задача
Вне фазы: полная сборка, независимая проверка SRG и заглушек, оценка теста, выпуск 3.6.0.

## Что сделано
- Полный `build.py` (до и после bump версии), цифры ниже.
- Каждое SRG из 0169 проверено `lookup.py --srg`; заглушки сверены с `git diff stubs/`.
- `mods.toml` 3.6.0; запись `## 3.6.0` в начале `docs/history.md`.

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `resources/META-INF/mods.toml` | version 3.6.0 |
| `docs/history.md` | запись 3.6.0 |

Код, заглушки и тесты не менялись: расхождений не найдено.

## Проверка
`python tools/build.py` (3.6.0), код 0:
- [1] компиляция: 225 классов
- [2] SRG: исходники 358, байткод 359, нет в joined.tsrg: 0
- [2b] пути классов: 140, ошибок 0
- [2c] linkcheck: jar 8, классов 185, членов 576, не удалось проверить 0, расхождений 0
- [3] ID: проблем 0; gen_dungeons: файлов 10, проблем 0; gen_citadel: частей 6, блоков 1485126, проблем 0
- [4] тесты OK: AdminWands, ArmorSets, AshMobs, BloodIron, BossAsh, BossCity, BossRift, Citadel (проверка history для версии прошла), CityMobs, City, Dungeon, Earth, Gauntlet (камней 7, частей купола 21), HellBlocks, HellGate, Journal, Mats, Maze, PhoenixStaff, Quake, RiftMobs, RiftStaff
- [5] jar: `dist/explosionwand-1.20.1-3.6.0.jar` (3028 КБ)

SRG (yarn из lookup -> смысл в Mojang, совпадает с использованием и заглушкой):
- m_82557_ Vec3.distanceToSqr(Vec3)D; m_20077_ Entity.isInLava()Z; m_6074_ Entity.kill()V
- m_41622_ ItemStack.hurtAndBreak(int,T extends LivingEntity,Consumer<T>)V; m_21190_ LivingEntity.broadcastBreakEvent(InteractionHand)V; m_41503_ Item.Properties.durability(int)
- m_8583_ ServerLevel.getAllEntities(); m_8999_ ServerPlayer.teleportTo(ServerLevel,x,y,z,yRot,xRot)
- m_255391_ Level.explode(Entity,x,y,z,float,boolean,ExplosionInteraction) - заглушка совпадает
- m_82425_ BlockHitResult.getBlockPos; m_60838_ BlockState.isCollisionShapeFullBlock(BlockGetter,BlockPos); m_46468_ Level.getDayTime; m_46472_ Level.dimension
- m_5661_ Player.displayClientMessage(Component,boolean); m_237110_ Component.translatable(String,Object...); m_41784_ ItemStack.getOrCreateTag
Все подтверждены, исправлений нет. Новые заглушки (6 штук) с типами/static/возвратом соответствуют 1.20.1; linkcheck расхождений 0.

GauntletTest: проверяет реальную логику (порядок и замыкание круга камней, clamp мусора NBT, перезарядки, half(n) по таблице, pickHalf на n=0..39: размер, отсутствие повторов, неизменность исходника, форма купола без NaN), плюс согласованность регистрации/ресурсов/lang. Усиление не потребовалось.

## Проверить в игре
Как в 0169 (камни, Щелчок, купол, закрытие парашюта, прочность) и 0170 (иконки, рецепт, JEI). Не проверяемо без игры: ощущение полёта, облик купола, реальное поведение teleportTo/kill/time set.

## Решения и почему
Версия минорная (новая функциональность). Перезарядка на весь предмет оставлена как в 0169.

## Проблемы и открытые вопросы
Нет.

## Git (выполняет пользователь)
```
git add src/com/explosionwand/Gauntlet.java src/com/explosionwand/GauntletLogic.java src/com/explosionwand/InfinityGauntletItem.java src/com/explosionwand/Parachute.java src/com/explosionwand/ParachuteItem.java src/com/explosionwand/ExplosionWandMod.java src/com/explosionwand/Ticker.java src/com/explosionwand/WhaleStaff.java src/com/explosionwand/jei/ExplosionWandJei.java stubs/net/minecraft/world/item/Item.java stubs/net/minecraft/world/item/ItemStack.java stubs/net/minecraft/world/entity/LivingEntity.java stubs/net/minecraft/world/entity/Entity.java stubs/net/minecraft/world/phys/Vec3.java tests/GauntletTest.java tools/gen_gauntlet_parachute.py resources/assets/explosionwand/textures/item/infinity_gauntlet.png resources/assets/explosionwand/textures/item/parachute.png resources/assets/explosionwand/models/item/infinity_gauntlet.json resources/assets/explosionwand/models/item/parachute.json resources/data/explosionwand/recipes/parachute.json resources/assets/explosionwand/lang/ru_ru.json resources/assets/explosionwand/lang/en_us.json resources/META-INF/mods.toml docs/history.md reports/0169-2026-11-17-mod-engineer-gauntlet-parachute.md reports/0170-2026-11-17-asset-engineer-gauntlet-parachute.md reports/0171-2026-11-17-build-engineer-gauntlet-parachute.md
git commit -m "feat(items): Перчатка Бесконечности и Парашют, 3.6.0" -m "- 6 камней перчатки и Щелчок: прыжок, сон, луч, день/ночь, взлёт, превращение блоков" -m "- парашют с куполом из block_display, прочность 64, рецепт, JEI, эхо Кита" -m "- текстуры, модели, lang ru/en, тест GauntletTest, версия 3.6.0" -m "" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

## Следующий шаг
code-reviewer: ревью перед коммитом.
