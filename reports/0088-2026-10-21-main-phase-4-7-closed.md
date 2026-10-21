# 0088 · Фаза 4.7 закрыта: Монолит Сна в храме, 2.15.0

- **Дата:** 2026-10-21
- **Агент:** main
- **Версия мода:** 2.15.0
- **Статус:** done
- **Связанные отчёты:** 0084–0087

## Задача
Фаза 4.7: Монолит Сна переезжает в храм Города. Первым шагом — хвосты ревью 4.11.

## Что сделано
- 0084: хвосты 4.11 (Ветер и снаряды, Ветер в данжах, guard в `Earth.tick`, nit 6–9, 11, 12, странник в Мирном режиме, повторная Печать Храма).
- 0085: Монолит в логове храма (зал 20), Печать Сна, алтарь Сна и `Arena3` убраны, добыча: Посох Сна, Недремлющее Око, Осколок Печати: Пепел.
- 0086: версия 2.15.0, `history.md`.
- 0087: ревью, APPROVE WITH NITS (0 blocker, 0 major, 4 minor, 6 nit).
- main: ROADMAP (текущая фаза 4.8, перенос замечаний, версии), `CLAUDE.md` (структура: арены).

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `ROADMAP.md` | текущая фаза 4.8, перенос замечаний 0087 |
| `CLAUDE.md` | строка про Boss/Arena в структуре |
| этот отчёт | создан |

## Проверка
`python tools/build.py` (в 0087): SRG 239/240, неизвестных 0, проблем ID 0, 10 тестов OK, jar 2.15.0.

## Проверить в игре
Полные списки — в 0084 и 0085. Главное: бой с Монолитом в зале 21×11 (масштаб 0,75, теснота при вращении и волне), старый мир с Городом (сброс раскладки), Посох Разлома без вылета.

## Решения и почему
Версии 4.8+ сдвинуты после 2.14.0 (4.11 вышла вне очереди).

## Проблемы и открытые вопросы
Нужен ответ пользователя: ХП Монолита реально ≈2833, заявлено 1700 — оставить или вернуть 1700. Для вылета Посоха Разлома нужны логи.

## Git (выполняет пользователь)
Правки 0084 и 0085 пересекаются по файлам (`Boss.java`, `Dungeons.java`, `Earth.java` и др.), раздельные коммиты не соберутся — один коммит на фазу:
```
git rm src/com/explosionwand/Arena3.java src/com/explosionwand/DreamSealItem.java src/com/explosionwand/Ember.java resources/assets/explosionwand/models/item/dream_seal.json resources/assets/explosionwand/textures/item/dream_seal.png resources/data/explosionwand/recipes/dream_seal.json
git add src/com/explosionwand/Altar.java src/com/explosionwand/ArenaGuard.java src/com/explosionwand/Boss.java src/com/explosionwand/Boss3.java src/com/explosionwand/CityMobsB.java src/com/explosionwand/Dims.java src/com/explosionwand/DungeonGuard.java src/com/explosionwand/Dungeons.java src/com/explosionwand/Earth.java src/com/explosionwand/ExplosionWandMod.java src/com/explosionwand/Ticker.java src/com/explosionwand/WhaleStaff.java src/com/explosionwand/jei/ExplosionWandJei.java
git add stubs/net/minecraft/world/entity/Entity.java stubs/net/minecraft/world/entity/player/Player.java stubs/net/minecraft/world/entity/projectile/Projectile.java stubs/net/minecraft/world/entity/decoration stubs/net/minecraft/world/entity/player/Inventory.java stubs/net/minecraft/world/entity/projectile/FishingHook.java stubs/net/minecraft/world/entity/projectile/ThrownEnderpearl.java stubs/net/minecraft/world/entity/projectile/ThrownTrident.java stubs/net/minecraftforge/event/entity/EntityLeaveLevelEvent.java
git add tests/BossCityTest.java tests/DungeonTest.java tools/gen_city.py tools/gen_dungeons.py
git add resources/META-INF/mods.toml resources/assets/explosionwand/lang/en_us.json resources/assets/explosionwand/lang/ru_ru.json resources/assets/explosionwand/models/item/ash_seal_shard.json resources/assets/explosionwand/textures/item/ash_seal_shard.png resources/data/explosionwand/structures/sleep_mid.nbt resources/data/explosionwand/structures/sleep_north.nbt
git add docs/dungeons.md docs/history.md ROADMAP.md CLAUDE.md reports/0084-2026-10-19-mod-engineer-review-tails-4-11.md reports/0085-2026-10-21-boss-engineer-monolith-temple.md reports/0086-2026-10-21-asset-engineer-release-2-15-0.md reports/0087-2026-10-21-code-reviewer-phase-4-7.md reports/0088-2026-10-21-main-phase-4-7-closed.md
git commit -m "feat(boss): Монолит Сна в храме Города (2.15.0)" -m "- Монолит в логове храма: фазы 75/50/25, приспешники, добыча: Посох Сна, Око, Осколок Пепла" -m "- убраны Печать Сна, алтарь Сна, арена Монолита" -m "- хвосты ревью 4.11: Ветер, guard в Earth.tick, повторная Печать Храма, странник" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

## Следующий шаг
`/clear`, затем `/next-phase` → 4.8 «Пепельное святилище: постройка».
