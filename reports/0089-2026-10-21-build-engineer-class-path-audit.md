# 0089 — build-engineer: аудит путей классов, TamableAnimal

Агент: build-engineer. Дата: 2026-10-21. Версию не менял (patch 2.15.1 ставит main).

## Причина бага
Заглушка лежала в `net.minecraft.world.entity.animal.TamableAnimal`, реальный класс 1.20.1 — `net.minecraft.world.entity.TamableAnimal`. Заглушка компилировалась, а в игре класса нет: `NoClassDefFoundError` в `Earth.foe`.

Важно: `joined.tsrg` содержит только obf -> `net/minecraft/src/C_xxx` (не Mojang-пути), поэтому как источник путей классов он не годится. Источник истины теперь — официальные Mojang-маппинги 1.20.1 (`client.txt` из version manifest), кеш `.cache/maps/mojang.txt`. Это те же имена, что использует Forge 1.20.1.

## Найдено неверных путей
Аудитом (все 120 заглушек + все 96 уникальных классов `net/minecraft/*` из байткода) найден ровно один: `TamableAnimal` (`entity.animal` -> `entity`).
ArmorStand (`entity.decoration`), ThrownTrident/ThrownEnderpearl/FishingHook (`entity.projectile`), Inventory (`entity.player`), ExperienceOrb, RandomSource — пути верны.
Forge-классы в Mojang-таблице отсутствуют; вручную сверены (EntityLeaveLevelEvent, EntityTeleportEvent, Living{Death,Heal,Hurt}Event, MobSpawnEvent, FillBucketEvent, PlayerEvent, PlayerInteractEvent, BlockEvent, ExplosionEvent, ServerStopp{ed,ing}Event, TickEvent, ITeleporter, FMLJavaModLoadingContext, ForgeRegistries) — пути корректны для Forge 1.20.1.

## Что изменено
- `stubs/net/minecraft/world/entity/TamableAnimal.java` (перенесена из `.../animal/`, пакет исправлен).
- `src/com/explosionwand/Earth.java`: импорт. (`RiftStaff.java` исправил mod-engineer.)
- `tools/classcheck.py` (новый): (а) каждая заглушка `net/minecraft/**` должна быть в Mojang-маппингах; (б) каждая ссылка `net/minecraft/*` в константных пулах скомпилированных классов — тоже. Forge/JEI/библиотеки в EXTERNAL, не проверяются автоматически. Таблица скачивается при первом запуске.
- `tools/build.py`: шаг `[2b] пути классов`, ошибка сборки при несовпадении.

## Результат
`python tools/build.py`: компиляция, SRG, [2b] (96 классов, 0 ошибок), ID, данжи, все тесты OK, jar собран. Проверка поймала бы баг: до переноса выдавала 2 ошибки (заглушка + байткод).

## Проверить в игре
Любая палочка Земли при контакте с мобом (в т.ч. с прирученным волком/котом); посох Разлома рядом с питомцем.
Не проверяется автоматически: сигнатуры методов Forge-классов и пути Forge-классов (только вручную).

## Git
```
git add stubs/net/minecraft/world/entity/TamableAnimal.java stubs/net/minecraft/world/entity/animal/TamableAnimal.java src/com/explosionwand/Earth.java tools/classcheck.py tools/build.py reports/0089-2026-10-21-build-engineer-class-path-audit.md
git commit -m "fix(build): TamableAnimal в верном пакете, проверка путей классов" -m "- заглушка перенесена в net.minecraft.world.entity" -m "- tools/classcheck.py: заглушки и байткод сверяются с Mojang-маппингами" -m "- build.py: шаг [2b]"
```
(Файл `src/com/explosionwand/RiftStaff.java` добавляет mod-engineer отдельно.)
