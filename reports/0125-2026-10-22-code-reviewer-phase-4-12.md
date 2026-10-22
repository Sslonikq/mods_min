# 0125 · Ревью фазы 4.12 (сеты брони данжей, 2.19.0)

- **Дата:** 2026-10-22
- **Агент:** code-reviewer
- **Версия мода:** 2.19.0
- **Статус:** done
- **Связанные отчёты:** 0122, 0123, 0124

## Задача
Ревью изменений фазы 4.12 перед коммитом. Что смотрел: `ArmorSetLogic`, `ArmorSets`, `SetMaterial`, `SetArmorItem`, правки `RiftMobs` (adopt, onDeath, nearestFrom, слух Пожирателя), `CityMobs.effect`, `City.fog`, `Ticker`, `ExplosionWandMod`, заглушки, JEI, lang, ресурсы, mods.toml, history, `ArmorSetsTest`. Проверял, что всё совпадает с таблицей в `docs/world.md`.

## Вердикт: **APPROVE WITH NITS**
Блокеров нет, коммитить можно. Есть одно замечание major, и это вопрос дизайна. В самих данжах никто не стреляет настоящими снарядами: у мобов лучи, они бьют мгновенно. Поэтому отражение «Грани» работает только против ванильных стрелков и в PvP. Это решает пользователь (см. ниже). Код соответствует тексту таблицы.

## Что сделано (проверено)
- **Цифры против world.md.** Совпадают. Защита 2/6/5/2, 3/6/5/2, 3/7/6/3 (15/16/19). Твёрдость 1/1/2. Отбрасывание 0/0/0,05. Множитель прочности 18/22/26, база {11,16,15,13} по `ArmorItem.Type.ordinal()` (HELMET..BOOTS, порядок в заглушке как в 1.20.1). Зачаровываемость 12/18/14. Ремонт: `rift_dust`/`sleep_thread`/`ash_coal`, все зарегистрированы. Шансы 1,5/2/6/20%, Добыча +1% за уровень, потолок 1. Бонусы 2/4: отбрасывание x0,9; 20% отражения; +2 брони на 60 тиков с перезарядкой 160 от момента срабатывания; x0,75 к дальности замечания (x0,45 с приседом при 4 частях); +8% скорости `MULTIPLY_TOTAL`; горение убывает вдвое быстрее и не дольше 60 тиков; огонь x0,6, лава не смягчается; ответный поджог на 3 с; урон 3,75% за часть. Пропорциональное «+15%» описано в world.md и в подсказке.
- **Модификаторы атрибутов.** Утечек нет. Модификаторы временные (`m_22118_`), в сохранение не пишутся, поэтому перезаход их сбрасывает, а ближайший тик, кратный 10, снова ставит скорость. Повторного добавления нет: `setMod` сначала проверяет `m_22111_`. Снятие частей: скорость снимается в тик, кратный 10, «Грань» сразу при `c < 4`. Смерть: модификаторы уходят вместе со старой сущностью, у новой `tick` сверит состояние и почистит `MODS`. Время берётся от игрового времени Overworld (`EclipseStaff.time`), так что при смене измерения отсчёт не ломается.
- **Отражение снарядов.** `ProjectileImpactEvent` в настоящем Forge 47.4.10 помечен `@Cancelable` (проверил по байткоду jar), `setCanceled` там переводится в `SKIP_ENTITY`. Бесконечного цикла нет: при каждом попадании новый бросок на 20%. PvP-пинг-понг между двумя кристальными игроками затухает сам. Есть проверки `owner == p` и `!owner.m_6084_()`. Скорость синхронизируется через `f_19864_`.
- **Клиент и сервер.** Клиентских классов в общем коде нет. Все обработчики проверяют `instanceof ServerPlayer`, на клиенте игрок `LocalPlayer`/`RemotePlayer`, так что обработчики там сразу выходят. Подсказка использует только `Component`/`ChatFormatting`. Текстура слоя берётся из `m_6082_ = "explosionwand:<set>"`, файл `textures/models/armor/<set>_layer_1/2.png` существует.
- **Ночное зрение и лабиринт.** В лабиринте не выдаётся, как у Архимага.
- **`nearestFrom`.** При множителе 1 условие `d <= r*r && d <= bd` равносильно старому. Множитель трогает только радиусы от 10 (см. nit 4).
- **Дроп.** Только при `killer instanceof ServerPlayer && !m.summoned`. Боссы не входят в `RiftMobs.MOBS`. Приспешники с `_min` и мобы волн жаровен (`ewdw_min`) помечены `summoned`. Мелкие, копии и огонёк имеют `Tier.NONE`. Исключение в `onDeath` перехватывается try/catch, тик не ломается.
- **Рецепты и торговля.** Нет: в `data/explosionwand/recipes` нет `crystal_*|dreamer_*|ash_*` для брони.
- **Ресурсы.** 12 иконок 16x16 RGBA, 6 слоёв 64x32 RGBA, 12 моделей `item/generated`. В lang ru/en одинаковые наборы ключей, всех нужных ключей хватает (12 названий, header2/4, source, `set.<id>.*`, `jei.set_*`). mods.toml 2.19.0, запись в history есть.
- **SRG.** Обратным `lookup.py --srg` проверил все новые имена, все совпадают по смыслу: `m_21051_` getAttributeInstance, `m_22118_` addTemporaryModifier, `m_22120_` removeModifier(UUID), `m_22111_` getModifier(UUID), `m_5602_` setOwner, `m_20094_`/`m_7311_` get/setFireTicks, `m_44930_` getLooting, `m_150109_` getInventory, `m_18947_` Inventory.count, `m_7639_`/`m_7640_` getAttacker/getSource, `m_19385_` getName (msgId), `m_82443_` EntityHitResult.getEntity, методы ArmorMaterial `m_266425_ m_7366_ m_6646_ m_7344_ m_6230_ m_6082_ m_6651_ m_6649_`, `m_43929_` Ingredient.ofItems, `m_7373_` appendTooltip.
- **Правила кода.** Enum берутся через `valueOf`, эффекты, частицы и звуки через `Fx`, прямых ссылок на `Blocks`/`SoundEvents` нет.

## Замечания
1. **[major]** `ArmorSets.java:223-241` (+ `RiftMobs.java:772` `Archer.fire`): мобы всех трёх данжей стреляют лучами-частицами, сущностей `Projectile` в `src/` нет вообще. Поэтому «20% снарядов отражается в стрелка» не работает против Лучника, Призмы, Искры, Жреца и других, то есть в собственном данже сета это мёртвый бонус. → Пользователь решает одно из двух. (а) Принять как есть и уточнить подсказку и world.md («вражеские стрелы и огненные шары»). (б) Добавить в выстрелы мобов данжей проверку `ArmorSets.reflects(...)`: луч гасится и бьёт стрелка.
2. **[minor]** `ArmorSets.java:238`: `m_5602_(p)` вызывается для любого снаряда. У `AbstractArrow.setOwner` есть побочный эффект: если владелец игрок, `pickup` становится ALLOWED, и отражённые стрелы скелетов (в том числе зелья стреев) можно подобрать. В PvP отражённая жемчужина Эндера телепортирует носителя брони, а у `FishingHook` подменяется `player.fishing`. → Отражать только при `!(owner instanceof Player)` или исключить `FishingHook`/`ThrownEnderpearl`, а `pickup` у стрел оставлять прежним.
3. **[minor]** `ArmorSets.java:234-236`: у `AbstractHurtingProjectile` (огненные шары блейза и гаста, черепа визера) остаются поля ускорения `xPower/yPower/zPower`. Развёрнутый шар тормозит, летит обратно в игрока и попадает в него, уже с владельцем-игроком. Так уже сказано в «Проверить в игре» отчёта 0123. → Либо развернуть и ускорение (заглушка полей `f_36813_/f_36814_/f_36815_`, проверить через lookup), либо не отражать такие снаряды.
4. **[minor]** `CityMobs.java:104`: иммунитет «сонный туман и усыпление» отключает и слепоту от укуса Ночной моли (`CityMobs.java:455`), а это не усыпление. В отчёте 0123 этот случай не упомянут. → Либо пропускать проверку для `blindness` моли, либо дописать в подсказку и world.md «и слепота моли».
5. **[nit]** `RiftMobs.java:641`: порог `range >= 10` захватывает не только «заметить». Под него попадают атака Тени `nearest(10, 4)` (`RiftMobs.java:847`: поворот и второй замах при 7,5–10 блоках пропадают), направление бегства (`CityMobsB.java:651,660`) и вождение огонька (`AshMobsB.java:509`, 30 → 22,5). → Порог `> 10` или отдельный `nearestNotice(...)` для дальности обнаружения.
6. **[nit]** `ArmorSets.java:191`: бонус урона применяется после `Golem.hurt` (`RiftMobs.java:1105`), где урон по кристаллам уже посчитан. По кристаллам Голема бонуса нет, только по ХП. Если так задумано, можно оставить.
7. **[nit]** `ArmorSetLogic.java:114`: такого msgId, как `unattributedFireball`, в 1.20.1 нет: у `unattributed_fireball` он `fireball`. Вреда нет, это мёртвая строка.
8. **[nit]** `ArmorSets.java:71-73`: `MODS`/`EDGE` чистятся только на остановке сервера, при выходе игрока нет. Рост ограничен числом игроков. → При желании чистить на `PlayerLoggedOutEvent`.
9. **[nit]** `ArmorSets.java:163-166`: при входе в лабиринт уже выданное ночное зрение держится до 15 с. То же у Архимага, так что поведение одинаковое.
10. **[nit]** `ArcaneMaterial.java:13` (старый код, не эта фаза): `DUR = {13,15,16,11}` по ordinal перевёрнут (шлем 13×37, сапоги 11×37). Нашёл mod-engineer. → Отдельный fix в следующей фазе, это поменяет прочность у уже выданных предметов.
11. **[nit]** Отчёты: в 0124 нет разделов «Задача», «Изменённые файлы», «Решения и почему», «Проблемы и открытые вопросы». Отдельного отчёта build-engineer нет: заглушки и SRG сделал и проверил mod-engineer (0123), я перепроверил всё через `--srg`. Старые отчёты не правятся, отмечаю здесь.

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `reports/0125-2026-10-22-code-reviewer-phase-4-12.md` | этот отчёт (код не менялся) |

## Проверка
- `python tools/build.py`: 189 классов, SRG в исходниках 257, в байткоде 258, неизвестных 0. Байткод `net/minecraft`: 107, ненайденных 0. Linkcheck: 149 членов, ненайденных 0. ID: 0 проблем. gen_dungeons 10/0. Все 15 тестов OK, включая `ArmorSetsTest` и `AshMobsTest`. Jar: `dist/explosionwand-1.20.1-2.19.0.jar`.
- `ProjectileImpactEvent` и `LivingKnockBackEvent` в `forge-1.20.1-47.4.10-universal.jar` помечены `@Cancelable` (по байткоду). Распаковка лежит в `build/rv/`.
- lang: наборы ключей ru и en совпадают, нужные ключи все на месте. PNG: размеры и RGBA правильные.

## Проверить в игре
- **«Грань»:** скелеты и стреи. Часть стрел возвращается, урон засчитан игроку. Проверить, подбираются ли отражённые стрелы (замечание 2). Блейз и гаст: куда летит отражённый шар (замечание 3). В Разломе Лучник и Призма не отражаются (замечание 1).
- **Атрибуты:** надеть и снять 4 части Сновидца. Скорость появляется и пропадает, после перезахода и смерти с keepInventory не удваивается (F3 или `/attribute @s minecraft:generic.movement_speed get`).
- **«+2 брони»:** полоска брони +1 значок на 3 с, повтор не раньше чем через 8 с. После снятия любой части бонус сразу пропадает.
- **Моль:** в полном сете Сновидца укус моли не ослепляет (замечание 4, проверить, нравится ли так).
- **Тень:** в сете Сновидца её второй замах срабатывает на дистанции 8–10 (замечание 5).
- Дальше по спискам «Проверить в игре» отчётов 0123 и 0124: дроп, Пепельный сет, иконки, слои, подсказки, JEI, наковальня.

## Решения и почему
- Major 1 не блокирует коммит: реализация совпадает с формулировкой таблицы, вопрос в замысле. Код и подсказка не врут друг другу.
- RiftMobs.java содержит правки 0122 и 0123, поэтому они идут одним коммитом. Без `git add -p`: тест `AshMobsTest` проверяет условие 0122 в том же файле.

## Проблемы и открытые вопросы
- Решение пользователя: отражение лучей мобов данжей (major 1) и слепота моли (minor 4).
- Остаются открытыми решения из ROADMAP: Облачение Архимага против сетов Земли, ферма огонька, удаление jar 2.18.0.

## Git (выполняет пользователь)
Вся фаза: сначала код (0122+0123 вместе, RiftMobs общий), потом ресурсы (0124), потом ревью, ROADMAP последним (его обновит main).
```
git add src/com/explosionwand/ArmorSetLogic.java src/com/explosionwand/ArmorSets.java src/com/explosionwand/SetMaterial.java src/com/explosionwand/SetArmorItem.java src/com/explosionwand/ExplosionWandMod.java src/com/explosionwand/Ticker.java src/com/explosionwand/RiftMobs.java src/com/explosionwand/CityMobs.java src/com/explosionwand/City.java tests/ArmorSetsTest.java tests/AshMobsTest.java docs/dungeons.md stubs/net/minecraft/world/entity/Entity.java stubs/net/minecraft/world/entity/LivingEntity.java stubs/net/minecraft/world/entity/projectile/Projectile.java stubs/net/minecraftforge/event/entity/living/LivingEvent.java stubs/net/minecraftforge/registries/ForgeRegistries.java stubs/net/minecraft/world/entity/ai/attributes/Attribute.java stubs/net/minecraft/world/entity/ai/attributes/AttributeInstance.java stubs/net/minecraft/world/entity/ai/attributes/AttributeModifier.java stubs/net/minecraft/world/item/enchantment/EnchantmentHelper.java stubs/net/minecraftforge/event/entity/ProjectileImpactEvent.java stubs/net/minecraftforge/event/entity/living/LivingKnockBackEvent.java reports/0122-2026-10-22-boss-engineer-nits-0121.md reports/0123-2026-10-22-mod-engineer-armor-sets.md
git commit -m "feat(items): сеты брони данжей Земли (код)" -m "- три материала, 12 предметов, ремонт материалом данжа" -m "- части падают с мобов своего данжа: 1,5/2/6/20% и Добыча" -m "- бонусы 2 и 4 частей: Грань, иммунитет ко сну, горение; +3,75% урона мобам данжа за часть" -m "- RiftMobs.adopt: теги *_min не становятся тегом зала (nits 0121)"

git add resources/assets/explosionwand/textures/item/crystal_helmet.png resources/assets/explosionwand/textures/item/crystal_chestplate.png resources/assets/explosionwand/textures/item/crystal_leggings.png resources/assets/explosionwand/textures/item/crystal_boots.png resources/assets/explosionwand/textures/item/dreamer_helmet.png resources/assets/explosionwand/textures/item/dreamer_chestplate.png resources/assets/explosionwand/textures/item/dreamer_leggings.png resources/assets/explosionwand/textures/item/dreamer_boots.png resources/assets/explosionwand/textures/item/ash_helmet.png resources/assets/explosionwand/textures/item/ash_chestplate.png resources/assets/explosionwand/textures/item/ash_leggings.png resources/assets/explosionwand/textures/item/ash_boots.png
git add resources/assets/explosionwand/textures/models/armor/crystal_layer_1.png resources/assets/explosionwand/textures/models/armor/crystal_layer_2.png resources/assets/explosionwand/textures/models/armor/dreamer_layer_1.png resources/assets/explosionwand/textures/models/armor/dreamer_layer_2.png resources/assets/explosionwand/textures/models/armor/ash_layer_1.png resources/assets/explosionwand/textures/models/armor/ash_layer_2.png
git add resources/assets/explosionwand/models/item/crystal_helmet.json resources/assets/explosionwand/models/item/crystal_chestplate.json resources/assets/explosionwand/models/item/crystal_leggings.json resources/assets/explosionwand/models/item/crystal_boots.json resources/assets/explosionwand/models/item/dreamer_helmet.json resources/assets/explosionwand/models/item/dreamer_chestplate.json resources/assets/explosionwand/models/item/dreamer_leggings.json resources/assets/explosionwand/models/item/dreamer_boots.json resources/assets/explosionwand/models/item/ash_helmet.json resources/assets/explosionwand/models/item/ash_chestplate.json resources/assets/explosionwand/models/item/ash_leggings.json resources/assets/explosionwand/models/item/ash_boots.json
git add resources/assets/explosionwand/lang/ru_ru.json resources/assets/explosionwand/lang/en_us.json resources/META-INF/mods.toml docs/history.md docs/world.md src/com/explosionwand/jei/ExplosionWandJei.java reports/0124-2026-10-22-asset-engineer-release-2-19-0.md
git commit -m "assets(items): ресурсы сетов брони, версия 2.19.0" -m "- иконки, слои, модели, lang ru/en, страницы JEI" -m "- history 2.19.0, world.md: 3,75% за часть"

git add reports/0125-2026-10-22-code-reviewer-phase-4-12.md
git commit -m "docs(agents): ревью фазы 4.12" -m "- APPROVE WITH NITS: 1 major (лучи мобов не отражаются), 3 minor, 7 nit"

git add ROADMAP.md
git commit -m "docs(agents): фаза 4.12 закрыта, 2.19.0"
```

## Следующий шаг
- Пользователь: решить по major 1 (лучи мобов данжей и «Грань») и minor 4 (слепота моли).
- mod-engineer, отдельным фикс-шагом (2.19.1): minor 2–3 (типы отражаемых снарядов, ускорение огненных шаров), nit 5 (порог `nearestFrom`), по желанию nit 7–8. Фикс `ArcaneMaterial.DUR` (nit 10) отдельным коммитом.
- main: обновить ROADMAP (4.12 ✅ 2.19.0, ревью 0125) и закрыть фазу.
