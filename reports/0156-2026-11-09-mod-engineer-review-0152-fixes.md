# 0156 mod-engineer: остатки ревью 0152

Agent: mod-engineer. Дата: 2026-11-09. Основание: reports/0152-2026-11-09-code-reviewer-phase-5-2.md (пункты 1, 3, 5).

## Сделано
1. **Опыт руд Ада.** `blood_iron_ore` и `crown_gem_ore` теперь `DropExperienceBlock` (опыт 1-3 и 3-7, как у железа/алмаза по духу). В `Earth.giveDrops` отдельное начисление не понадобилось: там уже вызывается `st.m_222967_(sl, pos, tool(), true)` (spawnAfterBreak с xp=true), а `DropExperienceBlock` по нему сам выпускает шары опыта. Раньше обычный `Block` опыта не давал. В Earth добавлен только поясняющий комментарий.
   - Новые заглушки: `stubs/net/minecraft/util/valueproviders/IntProvider.java`, `UniformInt.java` (`m_146622_` = UniformIntProvider.create, проверено `--srg`), `stubs/.../block/DropExperienceBlock.java`.
2. **BloodIron.** Поиск `blood_iron_ingot` в реестре вынесен в `repair()` с кэшем в поле (раньше искался на каждый вызов `m_6282_`).
3. **HellGate FLOOR_FALLBACK=42 - не баг.** Диапазон поиска пола `FLOOR_MIN=41..FLOOR_MAX=200`; лава Лавового моря на y=40, поэтому поверхность лавы - клетка 40, ниже FLOOR_MIN. Если в столбце нет ни твёрдого рельефа, ни жидкости выше 41 (над морем или в каньоне), площадка встаёт на 42: это на 2 блока выше лавы, три слоя камня площадки сами образуют твёрдый пол, в лаву/пустоту игрок не попадает. Если лава/рельеф есть выше 41, `pickFloor` берёт их высоту. Изменений нет.

## Проверка
`python tools/build.py`: SRG неизвестных 0, проблем ID 0, все тесты OK, jar `dist/explosionwand-1.20.1-3.3.0.jar`.

## Проверить в игре
- Ломать руду Ада киркой (в выживании) и Копателем: падают шары опыта; шёлковое касание опыта не даёт.
- Ремонт инструментов из кровавого железа на наковальне слитком работает.

## Следующий шаг
Нет. Поведение предметов не менялось, кроме опыта руд.

## Git
```
git add src/com/explosionwand/HellBlocks.java src/com/explosionwand/BloodIron.java src/com/explosionwand/Earth.java stubs/net/minecraft/util/valueproviders/IntProvider.java stubs/net/minecraft/util/valueproviders/UniformInt.java stubs/net/minecraft/world/level/block/DropExperienceBlock.java reports/0156-2026-11-09-mod-engineer-review-0152-fixes.md
git commit -m "fix(items): опыт руд Ада, кэш ингредиента кровавого железа" -m "- blood_iron_ore и crown_gem_ore: DropExperienceBlock" -m "- BloodIron: ингредиент ремонта кэшируется"
```
