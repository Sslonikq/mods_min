# 0148 · Тестовые админ-палочки: Молнии и Смерти

- **Дата:** 2026-11-09
- **Агент:** mod-engineer
- **Версия мода:** 3.2.0
- **Статус:** done
- **Связанные отчёты:** 0146 (Ад, предыдущая версия 3.1.0)

## Задача
Внеплановая просьба пользователя: две некрафтящиеся тестовые палочки для быстрого пропуска боссов и мобов. Палочка Молнии: луч с уроном ~1000 по всем на линии. Палочка Смерти: мгновенное убийство цели в прицеле, боссов с корректным завершением боя.

## Что сделано
- `AdminWands`: чистая логика (`targetable`, `rayBox`, `perp`), луч молнии (`lightning`), убийство (`death`, `kill`, `finishBoss`).
- Палочка Молнии: ПКМ, зигзаг-луч (`end_rod` + `electric_spark`, вспышка в конце) до 64 блоков или до первого блока, пробивает всех живых на линии (раздутый на 0,35 бокс), 1000 урона каждому (`indirectMagic` от игрока, броню игнорирует), перед ударом `f_19802_ = 0`, чтобы кадры неуязвимости не съедали выстрел. Перезарядка 10 тиков. Звук грома и удара молнии.
- Палочка Смерти: ПКМ, зелёный луч (`happy_villager`, `lime_concrete`, `sneeze` на цели), звуки колдовства. Обычные существа: `genericKill` с `Float.MAX_VALUE` (обходит тотем и броню), если обработчик отменил урон - принудительные `die` + `setHealth(0)`.
- Игроки, наблюдатели, мёртвые и стойки (носители block_display) не цели у обеих палочек.
- Предметы: стак 1, EPIC (`props()` с `valueOf`), без прочности, без рецептов, зарегистрированы последними (конец вкладки «Магия»), свечение. Модели `handheld`, текстуры 16x16 (генератор `tools/gen_admin_wands.py`, без PIL: PIL не установлен), lang ru/en (название, описание «Только для тестов», JEI «не крафтится, выдаётся только командой или в креативе»), страницы JEI.
- Тест `AdminWandsTest`: фильтр игроков/стоек/наблюдателей, `rayBox` (попадание, мимо, за дальностью, позади, изнутри, допуск, пробивание нескольких целей с порядком по расстоянию, цель в стороне), ортогональность `perp`.
- Версия 3.2.0, запись в `docs/history.md`.

## Как обрабатываются боссы
**Палочка Смерти** не убивает хост «в лоб», а запускает гибель боя. В каждом классе босса добавлен метод `adminKill()` (2-3 строки, поведение боя не менялось), перед `startDeath()`:
- `Boss` (Сердце Разлома), `Boss3` (Монолит Сна): `lethal = true`. Обычная проверка в тике сама вызывает `startDeath()`, дальше штатная цепочка: гибель, `won`, `tickAll` вызывает `Dungeons.lairWon` (сундук, победа зала), зачистка частей и приспешников.
- `Boss2` (Затмение), `Boss4` (Кит): прямой `startDeath()` (их тик проверяет `stage == 3` раньше проверки хоста), в конце гибели - сундук, опыт, `Arena*.cleared`, уборка по тегу.
- `Boss5` (Феникс): прямой `startDeath()` из любой стадии боя, включая яйцо и возрождение (а не `lethal`, который в фазе 1 вёл бы в падение и яйцо). Победа засчитывается сразу (`won`).
- `AdminWands.finishBoss` находит бой по хосту (`BOSSES` у Boss/Boss3/Boss5, `ACTIVE` у Boss2/Boss4, хитбоксы Кита). Часть босса вне боя и уже умирающий босс не трогаются.
- Приспешники (теги `*_min`) и мобы данжей - обычные существа, убиваются `genericKill`; block_display убирает сама зачистка босса.
- Во время гибели хост неуязвим (`vulnerable()` ложь на stage 3 / `DYING`), повторные выстрелы безвредны.

**Палочка Молнии** идёт штатным путём урона (`LivingHurtEvent` боссов), специального обхода нет: так не ломаются состояния боя. Получается: хост 1024 ХП получает `1000 * множитель` (Разлом: без множителя, с обрезкой до порога фазы; Монолит и Феникс: ×0,5/0,6 к 1024; Затмение ×0,55; Кит ×0,5 или ×1,3 в открытой фазе и ×1024/2300; хитбоксы Кита перенаправляют урон на хост). Пороги фаз и обрезка последнего удара до 1 ХП остаются, поэтому один выстрел не перескакивает фазу, а добивающий запускает штатную гибель. Окна неуязвимости (щит Разлома, ярость Феникса, разлом сегментов Монолита, фазы Затмения и Кита вне окна) выстрел отменяют, как и любой удар. Чтобы пропустить такой бой сразу - Палочка Смерти.

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `src/com/explosionwand/AdminWands.java` | новый: логика обеих палочек |
| `src/com/explosionwand/LightningWandItem.java`, `DeathWandItem.java` | новые: предметы |
| `src/com/explosionwand/Boss.java`, `Boss2.java`, `Boss3.java`, `Boss4.java`, `Boss5.java` | метод `adminKill()` (зона boss-engineer, изменение только добавочное) |
| `src/com/explosionwand/ExplosionWandMod.java` | регистрация `LIGHTNING_WAND`, `DEATH_WAND` в конце ITEMS |
| `src/com/explosionwand/jei/ExplosionWandJei.java` | страницы «как получить» |
| `stubs/.../Entity.java`, `LivingEntity.java`, `DamageSources.java`, `phys/AABB.java` | `f_19802_`, `m_6667_`, `m_287172_`, поля бокса |
| `tests/AdminWandsTest.java` | новый тест |
| `tools/gen_admin_wands.py` | новый: текстуры, модели, lang |
| `resources/assets/explosionwand/textures/item/lightning_wand.png`, `death_wand.png`; `models/item/lightning_wand.json`, `death_wand.json` | новые |
| `resources/assets/explosionwand/lang/ru_ru.json`, `en_us.json` | ключи названий, описаний, JEI |
| `resources/META-INF/mods.toml`, `docs/history.md` | версия 3.2.0, запись |

## Проверка
- `python tools/build.py`: проходит полностью (SRG неизвестных 0, linkcheck без нарушений, проблем ID 0, все тесты OK, jar `dist/explosionwand-1.20.1-3.2.0.jar`).
- Новые SRG (`lookup.py --srg`): `m_287172_` DamageSources.genericKill, `m_269104_` indirectMagic, `m_6667_` LivingEntity.die (onDeath), `f_19802_` Entity.invulnerableTime (timeUntilRegen), `f_82288_..f_82293_` AABB min/max X/Y/Z, `m_82400_` AABB.inflate, `m_6443_` getEntitiesOfClass, `m_20206_` getBbHeight.

## Проверить в игре
- `/give @s explosionwand:lightning_wand` и `death_wand`: названия, описания, свечение, предметы в конце вкладки «Магия», нет рецептов, страницы JEI.
- Молния: вид зигзага (яркость, толщина), пробивает ряд мобов за стеной? (должна остановиться на блоке), игроков рядом не бьёт, звук.
- Молния по каждому боссу: проходит ли урон, видно ли пороги фаз и окна неуязвимости, добивание запускает гибель.
- Смерть по каждому боссу (Сердце Разлома, Монолит Сна, Феникс в бою, в яйце и после возрождения, Затмение и Кит на аренах, если они доступны): гибель, сундук, победа зала, закрытие арены, исчезновение приспешников и block_display.
- Смерть по обычному мобу с тотемом/в броне, по мобу данжа, по приспешнику. По игроку - ничего не происходит.
- Смерть по Киту: цель может быть хитбоксом, а не хостом.

## Решения и почему
- Урон Молнии не обходит защиту боссов: специальный путь рисковал рассинхронизацией конечных автоматов боя (пороги фаз читаются из ХП хоста).
- `lethal` для Boss/Boss3, прямой `startDeath()` для остальных: так каждый вызывается в безопасной для него точке.
- Стойки исключены из целей: на них едут block_display приспешников и частей данжей.

## Проблемы и открытые вопросы
- Молнии надо несколько выстрелов на босса с фазами, это сознательно. Если нужен «сквозной» урон - отдельная доработка в зоне boss-engineer (флаг обхода `vulnerable`).
- Игра на сервере не запускалась: поведение боссов под `adminKill()` проверено только чтением кода.

## Git (выполняет пользователь)
```
git add src/com/explosionwand/AdminWands.java src/com/explosionwand/LightningWandItem.java src/com/explosionwand/DeathWandItem.java src/com/explosionwand/Boss.java src/com/explosionwand/Boss2.java src/com/explosionwand/Boss3.java src/com/explosionwand/Boss4.java src/com/explosionwand/Boss5.java src/com/explosionwand/ExplosionWandMod.java src/com/explosionwand/jei/ExplosionWandJei.java stubs/net/minecraft/world/entity/Entity.java stubs/net/minecraft/world/entity/LivingEntity.java stubs/net/minecraft/world/damagesource/DamageSources.java stubs/net/minecraft/world/phys/AABB.java tests/AdminWandsTest.java tools/gen_admin_wands.py resources/assets/explosionwand/textures/item/lightning_wand.png resources/assets/explosionwand/textures/item/death_wand.png resources/assets/explosionwand/models/item/lightning_wand.json resources/assets/explosionwand/models/item/death_wand.json resources/assets/explosionwand/lang/ru_ru.json resources/assets/explosionwand/lang/en_us.json resources/META-INF/mods.toml docs/history.md reports/0148-2026-11-09-mod-engineer-admin-wands.md
git commit -m "feat(items): тестовые палочки Молнии и Смерти, 3.2.0" -m "- Молния: луч 64 блока, 1000 урона всем на линии" -m "- Смерть: гибель цели, боссы через свою цепочку (adminKill)" -m "- без рецептов, JEI, lang ru/en, тест AdminWandsTest"
```
Внимание: в рабочем дереве уже есть чужие несохранённые правки в `Entity.java`, `AABB`-соседях, `docs/history.md`, `mods.toml`, lang (фаза 5.1). Они попадут в этот коммит; при желании разнесите через `git add -p`.

## Следующий шаг
code-reviewer: проверить `adminKill()` во всех пяти боссах. Дальше - фаза 5.x по ROADMAP.
