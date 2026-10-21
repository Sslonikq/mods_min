# 0084 · Хвосты ревью 0083, 0078, 0077, 0065

- **Дата:** 2026-10-19
- **Агент:** mod-engineer
- **Версия мода:** 2.14.0 (без смены версии)
- **Статус:** done
- **Связанные отчёты:** 0083, 0078, 0077, 0065

## Задача
Закрыть перенесённые замечания ревью перед фазой 4.7 (сама фаза 4.7 и Монолит не тронуты).

## Что сделано
- 0083 minor 1: `docs/history.md` 2.14.0 - «защита от сбоя, причина вылета не установлена». Код не менялся.
- 0083 minor 2: Ветер (`Earth.wind`) пропускает снаряды с владельцем-игроком, `ThrownTrident`, `ThrownEnderpearl`, `FishingHook`.
- 0083 minor 3: Ветер с Shift в данже (`Dungeons.at`) - обычный порыв и сообщение `explosionwand.earth.wind_dungeon`, без подброса и Медленного падения.
- 0083 minor 4: `Earth.tick` - каждый `tick*` через `guard(...)`, однократный лог в `System.err` со стеком (`tickWarned`).
- 0083 nit 6: вытесненный огонёк (и в блоке, и в `WISPS`) гасит ручку `snuff()`.
- nit 7: `tickSkin` убирает записи по сроку независимо от того, есть ли игрок.
- nit 8: тег двойника `ewdecoy<время мира>x<номер>e` (завершающая `e` исключает префиксные совпадения).
- nit 9: Морозец использует `foe(...)`; `foe` исключает `ArmorStand`.
- nit 11 + 0077 nit 10: `Dims.nearY` заменён на `Dims.nearSpot(level, bed)` (возвращает {x,y,z}): соседняя клетка (радиус до 2), не кровать и не якорь, иначе поверхность. Используется в `Earth.finishReturn` и `Dims.evacuateOldArena`. Заряд якоря по-прежнему не тратится (не делалось).
- nit 12: удалён `Ember.java` и его тик в `Ticker`.
- 0078 minor 1: событие `EntityLeaveLevelEvent` доступно в Forge 1.20.1. `CityMobsB.onLeave` (регистрация в `ExplosionWandMod`): для хоста с тегом `ewrk_fog_walker`, причина удаления `DISCARDED` (ванильный despawn; выгрузка чанка и смерть не затрагиваются), вещь из основной руки выпадает через `Later.run(..., 1, ...)` (не внутри обратного вызова удаления). `docs/dungeons.md` обновлён. Nit 2: в `history.md` 2.9.0 добавлена строка про правки 0077.
- 0065 minor 4: `Dungeons.reissueSeal`: ПКМ (основная рука) по саркофагу склепа (зал 21, координаты саркофагов из `tools/gen_city.py`) выдаёт `temple_seal`, если зал зачищен, дверь храма (`door a=sleep_temple`, угол метки) ещё закрыта и Печати нет в инвентаре. Сообщение `explosionwand.dungeon.seal_reissued` (ru+en). Генератор данжа не менялся.
- Не делалось: nit 10 (шёлковое касание льда), 13, 14 из 0083; minor 5 (версии в ROADMAP) - зона main.

## Изменённые файлы
- src/com/explosionwand/Earth.java, Dims.java, Dungeons.java, CityMobsB.java, ExplosionWandMod.java, Ticker.java; удалён Ember.java
- stubs: Projectile (m_19749_), новые ThrownTrident, ThrownEnderpearl, FishingHook, decoration/ArmorStand, player/Inventory (+ Player.m_150109_), event/entity/EntityLeaveLevelEvent, Entity (m_146911_ + RemovalReason)
- resources/assets/explosionwand/lang/ru_ru.json, en_us.json
- docs/history.md, docs/dungeons.md

## Проверка
`python tools/build.py`: 155 классов, SRG 239/240, неизвестных 0, проблем ID 0, все тесты OK. SRG по смыслу: `m_19749_` Ownable.getOwner (Projectile), `m_146911_` getRemovalReason, `m_150109_` getInventory, `m_18947_` Inventory.count(Item), `m_150171_` isOwner - сверены `lookup.py --srg`.

## Проверить в игре
- Ветер: стрела скелета сбивается, свой/чужой трезубец и эндер-жемчуг не пропадают. В данже Shift+ПКМ - сообщение и обычный порыв, подброса нет; вне данжа подброс работает.
- Возврат к кровати: игрок оказывается рядом с кроватью/якорем, а не на ней.
- Странник с вещью в Мирном режиме (сменить сложность, пока он убегает): вещь лежит на земле. Это единственная непроверяемая без игры часть: приходит ли у ванильного despawn причина именно `DISCARDED`.
- Печать Храма: в зачищенном склепе выбросить Печать, ПКМ по саркофагу (верх блока) - выдаётся новая; при открытой двери храма или с Печатью в инвентаре - нет.
- Морозец не замораживает жителей, питомцев и стойки для брони.

## Решения и почему
- Событие поймано по `DISCARDED`: так не трогаются выгрузка чанка (вещь сохраняется с мобом) и убийство (вещь уже роняет `die()`).
- Печать выдаётся по саркофагу в коде, а не новой меткой: не нужна перегенерация данжа и совместимо со старыми мирами.

## Проблемы и открытые вопросы
Нет. Причина вылета Посоха Разлома по-прежнему неизвестна.

## Git (выполняет пользователь)
```
git add src/com/explosionwand/Earth.java src/com/explosionwand/Dims.java src/com/explosionwand/Dungeons.java src/com/explosionwand/CityMobsB.java src/com/explosionwand/ExplosionWandMod.java src/com/explosionwand/Ticker.java src/com/explosionwand/Ember.java stubs resources/assets/explosionwand/lang/ru_ru.json resources/assets/explosionwand/lang/en_us.json docs/history.md docs/dungeons.md reports/0084-2026-10-19-mod-engineer-review-tails-4-11.md
git commit -m "fix(wand): хвосты ревью 4.11 и 4.6, повторная Печать Храма" -m "- Ветер: не сбивает снаряды игроков, в данже без подброса" -m "- Earth.tick с try/catch, snuff огоньков, Морозец через foe" -m "- Dims.nearSpot: возврат рядом с кроватью, не на ней" -m "- странник: вещь выпадает при ванильном despawn (EntityLeaveLevelEvent)" -m "- саркофаг склепа выдаёт утерянную Печать Храма" -m "- удалён холостой Ember"
```
(`git add stubs` захватывает все новые заглушки; Ember.java удалён - `git add` на удалённый путь в PowerShell работает как фиксация удаления.)

## Следующий шаг
Фаза 4.7 (Монолит Сна). Main: перенумеровать версии в ROADMAP (0083 minor 5).
