# 0173 · Исправления по ревью 0172: Перчатка Бесконечности и Парашют (3.6.0)

- **Дата:** 2026-11-17
- **Агент:** mod-engineer
- **Версия мода:** 3.6.0 (не выпущена, версию не меняли)
- **Статус:** done
- **Связанные отчёты:** 0169, 0172

## Задача
Исправить находки ревью 0172 (REQUEST CHANGES): пункты 1-8.

## Что сделано
1. **major, sweep:** `Parachute.sweep` использует `l.m_46467_() % 100 != 7` (время уровня). `Ticker.java:48` не тронут.
2. **Перезарядки по камням:** в NBT стака `ewCd<N>` = время игры + перезарядка. Время берётся у Верхнего мира через сервер (`Gauntlet.now`), не зависит от измерения. Общая пауза предмета теперь 10 тиков (`ITEM_COOLDOWN`). Смена камня (Shift) работает всегда. На перезарядке: actionbar `explosionwand.gauntlet.cooldown` («%s: ещё %s с» / «%s: %s s left»), в подсказке остаток у текущего камня (красная строка). Расчёт (`readyAt`, `left`, `secs`, `cdKey`) в `GauntletLogic`, тест есть. Если время «откатилось» (остаток больше самой перезарядки), камень считается готовым.
3. **Щелчок:** исключены ванильные боссы (wither, ender_dragon, warden, elder_guardian, по id типа через `ForgeRegistries.ENTITY_TYPES`) и все мобы данжей (`Minions.dungeonMob`): штатной безопасной смерти у них нет (Хранитель Пепла режется `clampPhase`, копия Города лопается без добычи). Приспешники боссов мода остаются целями. Тексты desc2/jei в ru и en обновлены. Фильтр id в `GauntletLogic.snapSpared`, протестирован.
4. **Луч Пепла:** убраны взрывы `m_255391_`, вместо них частицы `explosion` и звук `entity.generic.explode`; урон только целям по `affects`.
5. **Прыжок Разлома:** `LivingFallEvent` (`Gauntlet.onFall`, подписка в ExplosionWandMod) гасит урон, пока игрок в `SAFE`; защита до приземления или 200 тиков (10 с). Место прыжка: отступ 0.7 назад от точки попадания, проверка хитбокса 0.6x1.8 через `Level.m_45756_`.
6. **Тег купола:** `ewchute` + 8 символов UUID игрока + `x` + время мира (base36) + seq. Время мира переживает перезапуск, поэтому старый купол не прилипнет (чистый UUID не годился бы: сирота того же игрока совпал бы).
7. **Парашют:** не раскрывается в творческом полёте (`abilities.flying`) и верхом; полёт во время купола тоже складывает его.
8. **Камень Неба:** во второй отложенной задаче добавлена проверка `alive(l, p)`; жив ли каждый целевой моб и тот же ли уровень, проверялось и раньше (оставлено).

Приняты без изменений (nits): анимация перевзятия при смене NBT, баланс золота Глубин, лаг-скорость парашюта (`vy` по сдвигу позиции).

## Новые SRG (проверены `lookup.py --srg`)
| SRG | Смысл |
|---|---|
| `m_150110_` | `Player.getAbilities` |
| `f_35935_` | `Abilities.flying` |
| `m_20159_` | `Entity.hasVehicle` (isPassenger) |
| `m_129783_` | `MinecraftServer.getOverworld` |
| `m_45756_` | `Level.isSpaceEmpty(Entity, AABB)` (noCollision) |
| `m_128441_`, `m_128454_`, `m_128356_` | `CompoundTag.contains / getLong / putLong` |
| `m_7654_`, `m_6095_`, `m_135815_`, `m_237110_` | getServer, getType, ResourceLocation.getPath, translatable с аргументами (уже были в проекте, перепроверены) |

Заглушки: `Abilities`, `LivingFallEvent` (Forge, имена без обфускации), правки `Player`, `Entity`, `MinecraftServer`, `Level`.

## Изменённые файлы
- `src/com/explosionwand/Gauntlet.java`, `GauntletLogic.java`, `InfinityGauntletItem.java`, `Parachute.java`, `ExplosionWandMod.java`
- `stubs/net/minecraft/world/entity/player/Player.java`, `.../player/Abilities.java` (новый), `stubs/net/minecraft/world/entity/Entity.java`, `stubs/net/minecraft/server/MinecraftServer.java`, `stubs/net/minecraft/world/level/Level.java`, `stubs/net/minecraftforge/event/entity/living/LivingFallEvent.java` (новый)
- `resources/assets/explosionwand/lang/ru_ru.json`, `en_us.json`
- `tests/GauntletTest.java`, `docs/history.md`
- `reports/0173-2026-11-17-mod-engineer-gauntlet-review-fixes.md`

## Проверка
`PYTHONIOENCODING=utf-8 python tools/build.py`: 225 классов; SRG исходники 364, байткод 365, неизвестных 0; пути классов 0 ошибок; linkcheck: членов 585, расхождений 0; ID 0 проблем; все тесты OK (Gauntlet: камней 7, частей купола 21); jar `dist/explosionwand-1.20.1-3.6.0.jar`.

## Проверить в игре
- Перезарядки: применить Щелчок, сразу сменить камень и применить другой (должно работать); вернуться на Щелчок: actionbar «ещё N с», подсказка показывает остаток. Сменить измерение и проверить, что остаток не сбрасывается.
- Щелчок: рядом Хранитель Пепла, копии Города, Иссушитель/дракон: не трогаются; зомби и т.п. рассыпаются.
- Луч Пепла рядом с рамками, предметами на земле, другим игроком: не ломаются.
- Прыжок Разлома: с высоты >100 блоков, над лавой/пропастью дольше 4 с, вплотную к стене и углу: урона и удушья нет.
- Парашют: выйти из мира с открытым куполом и зайти (купол пропадает за 5 с, в обоих измерениях); не раскрывается в творческом полёте и верхом на лошади.

## Решения и почему
- Часть общей перезарядки предмета оставлена 10 тиков (против спама), как рекомендовано.
- Мобы данжей полностью исключены из Щелчка: штатной смерти нет.

## Проблемы и открытые вопросы
- Нет.

## Git (выполняет пользователь)
```
git add src/com/explosionwand/Gauntlet.java src/com/explosionwand/GauntletLogic.java src/com/explosionwand/InfinityGauntletItem.java src/com/explosionwand/Parachute.java src/com/explosionwand/ExplosionWandMod.java stubs/net/minecraft/world/entity/player/Player.java stubs/net/minecraft/world/entity/player/Abilities.java stubs/net/minecraft/world/entity/Entity.java stubs/net/minecraft/server/MinecraftServer.java stubs/net/minecraft/world/level/Level.java stubs/net/minecraftforge/event/entity/living/LivingFallEvent.java resources/assets/explosionwand/lang/ru_ru.json resources/assets/explosionwand/lang/en_us.json tests/GauntletTest.java docs/history.md reports/0173-2026-11-17-mod-engineer-gauntlet-review-fixes.md
git commit -m "fix(items): правки Перчатки и Парашюта по ревью" -m "- перезарядки по камням, Щелчок щадит боссов и мобов данжей" -m "- sweep куполов по времени уровня, защита от падения через событие"
```
(остальные файлы фичи 3.6.0 — по отчётам 0169-0172)

## Следующий шаг
- Отдельный `/fix`: `Ticker.java:48` (`ticks % 20` для `Dims.evacuateOldArena`) — та же ошибка счётчика, что была в sweep.
- Повторное ревью (code-reviewer).
