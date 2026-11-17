# 0169 · Перчатка Бесконечности и Парашют (код)

- **Дата:** 2026-11-17
- **Агент:** mod-engineer
- **Версия мода:** 3.5.0 (версию и history.md ведёт build-engineer)
- **Статус:** done
- **Связанные отчёты:** 0168, 0170 (ресурсы, asset-engineer)

## Задача
Вне фазы, по просьбе пользователя: код двух предметов, `infinity_gauntlet` (7 камней из миров мода) и `parachute` (купол из block_display).

## Что сделано
- `InfinityGauntletItem` + `Gauntlet` + `GauntletLogic`: Shift+ПКМ меняет камень (NBT int `ewStone`, actionbar, частицы, chime), ПКМ запускает способность. Камни: rift (прыжок до 64, защита от падения `Gauntlet.Safe`), sleep (DreamStaff.sleep, радиус 16, 8 с), ash (луч 40 блоков, урон 20, поджог, взрывы NONE каждые 4 блока с 5-го), eclipse (`time set midnight/day` только в Верхнем мире, волна частиц), sky (взлёт/knock, через 2 с удар вниз, урон 10), deep (шар R=4, золото/аметист/стекло/медь/кварц, Shield, без BlockEntity/жидкостей/твёрдости <0 или >=50/неполных блоков), snap (половина врагов `Enemy` и мобов данжей в радиусе 64, пепел снизу вверх 2 с, затем `kill`). Перезарядки 20/60/40/200/80/100/1200 тиков на весь предмет.
- `ParachuteItem` + `Parachute`: купол 21 block_display (красно-белая шерсть, 4 стропы из white_concrete), телепортируются за игроком каждый тик, теги `ew_chute` + `ewchuteN`. Падение не быстрее 0.12, планирование 0.15, fallDistance=0, slow_falling без частиц. Закрытие: земля, вода, лава, элитры, Shift (после 8 тиков), смерть, выход, смена измерения. `Parachute.sweep` раз в 100 тиков убирает осиротевшие `ew_chute`. Прочность 64, минус 1 за раскрытие.
- Регистрация после `earthquake_wand`, тик в `Ticker`, JEI info-страницы, эхо Посоха Кита (`case "infinity_gauntlet"`: вспышка камней, урон 10, отброс; парашют идёт в default).
- Тест `tests/GauntletTest.java` (логика, форма купола, регистрация, ресурсы, lang).
- Заглушки: `Item.Properties.m_41503_`, `ItemStack.m_41622_`, `LivingEntity.m_21190_`, `Entity.m_20077_`, `Entity.m_6074_`, `Vec3.m_82557_`.

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `src/com/explosionwand/Gauntlet.java`, `GauntletLogic.java`, `InfinityGauntletItem.java` | новые |
| `src/com/explosionwand/Parachute.java`, `ParachuteItem.java` | новые |
| `src/com/explosionwand/ExplosionWandMod.java` | регистрация двух предметов |
| `src/com/explosionwand/Ticker.java` | тик Parachute, sweep, Gauntlet.tick |
| `src/com/explosionwand/WhaleStaff.java` | case "infinity_gauntlet" |
| `src/com/explosionwand/jei/ExplosionWandJei.java` | две info-страницы |
| `stubs/.../Item.java, ItemStack.java, LivingEntity.java, Entity.java, Vec3.java` | новые члены |
| `tests/GauntletTest.java` | новый тест |

## Проверка
- `python tools/build.py`: компиляция OK, код выхода 0, все тесты OK (включая GauntletTest; ресурсы asset-engineer уже были на месте).
- Новые SRG (проверены `lookup.py --srg`): `m_82557_` Vec3.distanceToSqr; `m_20077_` Entity.isInLava; `m_6074_` Entity.kill; `m_41622_` ItemStack.hurtAndBreak; `m_21190_` LivingEntity.broadcastBreakEvent; `m_41503_` Item.Properties.durability; `m_8583_` ServerLevel.getAllEntities; `m_8999_` ServerPlayer.teleportTo(level,...); `m_255391_` Level.explode; `m_82425_` BlockHitResult.getBlockPos; `m_60838_` isCollisionShapeFullBlock; `m_46468_` getDayTime; `m_46472_` dimension; `m_5661_` displayClientMessage; `m_237110_` translatable с аргументами; `m_41784_` getOrCreateTag.
- ID: блоки gold_block, amethyst_block, glass, copper_block, quartz_block, red_wool, white_wool, white_concrete; частицы reverse_portal, end_rod, squid_ink, flame, ash, cloud, poof, explosion, flash, dust; звуки block.amethyst_block.chime/resonate, entity.enderman.teleport, entity.elder_guardian.curse, entity.blaze.shoot, item.firecharge.use, block.beacon.activate/power_select, entity.illusioner.cast_spell, entity.firework_rocket.launch, entity.generic.explode, entity.player.attack.crit, block.note_block.hat, entity.wither.spawn, item.armor.equip_leather, entity.phantom.flap; эффекты levitation, slow_falling.
- Lang-ключи (новых нет): все из списка задачи.

## Проверить в игре
- Shift+ПКМ: имя камня в actionbar, подсказка показывает «текущий камень».
- rift: прыжок к прицелу, падение с высоты после прыжка без урона; в потолок/стену не телепортирует внутрь блока.
- sleep, ash (луч, поджог, взрывы не ломают блоки, 20 урона), sky (мобы взлетают и бьются), deep (блоки в шаре меняются; блоки данжей и сундуки не трогает), eclipse (день<->ночь в Верхнем мире).
- snap: ровно половина врагов в радиусе 64 рассыпается, боссы и игроки целы; перезарядка 60 с.
- Парашют: купол виден над головой и не отстаёт/не дрожит; падает медленно, планирует по взгляду; закрывается на земле, в воде, по Shift; после смерти/выхода купол не остаётся; прочность убывает.
- Дополнительно: красно-белая окраска и наклон краёв купола, стропы доходят до груди.

## Решения и почему
- Перезарядка на весь предмет, как просили: после Щелчка 60 с нельзя ни применять, ни менять камень (ПКМ блокируется игрой). Если мешает, нужна отдельная перезарядка камней в NBT.
- Парашют: телепорт частей каждый тик (как кольцо DreamStaff), а не `ride`: смещения пассажира на игроке непредсказуемы. Скорость игрока берём по сдвигу позиции, т.к. серверная скорость игрока ненадёжна.
- Фильтр целей: как `Quake.affects` + приспешники боссов; части боссов не трогаем (кроме урона от эха Кита).
- Щелчок убивает `kill` (с лутом); мобы данжа без интерфейса Enemy тоже учитываются (`Minions.dungeonMob`).
- eclipse в других измерениях: только эффекты, время не меняется.

## Проблемы и открытые вопросы
- Облик купола и ощущение полёта нельзя проверить без игры.
- Падение игрока с серверной стороны подавляется slow_falling (8 тиков после закрытия на высоте): теоретически безопасно, возможна «короткая» защита после Shift.

## Git (выполняет пользователь)
```
git add src/com/explosionwand/Gauntlet.java src/com/explosionwand/GauntletLogic.java src/com/explosionwand/InfinityGauntletItem.java src/com/explosionwand/Parachute.java src/com/explosionwand/ParachuteItem.java src/com/explosionwand/ExplosionWandMod.java src/com/explosionwand/Ticker.java src/com/explosionwand/WhaleStaff.java src/com/explosionwand/jei/ExplosionWandJei.java stubs/net/minecraft/world/item/Item.java stubs/net/minecraft/world/item/ItemStack.java stubs/net/minecraft/world/entity/LivingEntity.java stubs/net/minecraft/world/entity/Entity.java stubs/net/minecraft/world/phys/Vec3.java tests/GauntletTest.java reports/0169-2026-11-17-mod-engineer-gauntlet-parachute.md
git commit -m "feat(items): Перчатка Бесконечности и Парашют" -m "- 7 камней перчатки: прыжок, сон, луч, день/ночь, взлёт, превращение блоков, Щелчок" -m "- парашют с куполом из block_display, JEI и эхо Кита"
```

## Следующий шаг
build-engineer: версия и docs/history.md; code-reviewer: ревью.
