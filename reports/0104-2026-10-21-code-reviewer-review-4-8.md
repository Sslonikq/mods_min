# 0104 · Ревью фазы 4.8 «Пепельное святилище: постройка» (2.16.0)

- **Дата:** 2026-10-21
- **Агент:** code-reviewer
- **Версия мода:** 2.16.0
- **Статус:** done
- **Связанные отчёты:** 0099, 0100, 0101, 0102, 0103, 0098, 0087

## Задача
Ревью всей незакоммиченной фазы 4.8: хвосты 4.7 (0099), правки Boss3 (0100), Святилище 2а/2б (0101, 0102), релиз 2.16.0 (0103). Код не менялся.

## Вердикт: **APPROVE WITH NITS**
Блокеров нет. Сборка, проверки и тесты зелёные. Правила CLAUDE.md соблюдены. Есть один major по геймплею (в башню можно залететь, минуя дверь с Осколком). Его можно исправить отдельным патчем 2.16.1 или в 4.9, коммиту он не мешает.

## Что сделано (проверено)
- **Ash.java**: `reissue` безопасен по null (`h`/`h.chest`/`door`/`Container`), клик отменяется только при выдаче. Ключ не выдаётся, если он лежит в сундуке, у игрока или дверь уже открыта. Ключ не тратится и открывает только дверь (`doorOnly`), поэтому дубли безвредны. Строка `gear` даёт 160 ХП (меньше 1024), дроп брони 0, имя через `translate`.
- **DungeonMech**:
  - Тик обёрнут в `try` в `Dungeons.tick:599`. Механики работают только после полной постановки (`partsDone == allParts`), только для живых игроков не в режиме наблюдателя и в радиусе.
  - Парсинг меток (`jetCfg`/`geyserCfg`) возвращает `null` на мусоре. Для `braziers`/`jets`/`geyser`/`crumble` формат строго проверяет `gen_dungeons.py` (стр. 964–1029), так что `parseInt` в `fireSlots`/`onClick` на шаблоне не упадёт.
  - `fill` пропускает `null`-state. `HIT_CD` чистится, `CRUMB`/`HIT_CD` очищаются в `clear()`.
  - Мирный режим соблюдён (струи, гейзеры, волна). Enum только через `Difficulty.valueOf("PEACEFUL")`, звуки и частицы через `Fx`, summon через `Cmd`.
- **Перезапуск мира**:
  - Осыпающиеся платформы: состояние берётся из мира, сломанная платформа вернётся через 7 с.
  - Жаровни: горят они или нет — хранится в самих блоках. «Решено» лежит в `Rec.opened` (NBT). Нарушенный набор гаснет, решённый снова зажигается.
  - Колокол: открытие тоже в `Rec.opened`.
  - Мобы волны без `PersistenceRequired`, с тегом `ewd`. Их убирает `removeStrays`, при решении головоломки они удаляются через `RiftMobs.remove` (тот в конце всегда зовёт `m_146870_`, ванильным мобам это подходит).
- **Нагрузка**: 347 тыс. блоков при `BUDGET = 1500`/тик — около 230 тиков постановки, по частям при подходе. Фреска сверяет 60 блоков раз в 20 тиков и ставит только расходящиеся. Струи и гейзеры раз в 2 тика с `nearAny`. Приемлемо.
- **Защита данжа**: `bossDead` у ASH не ставится до 4.10 (метки `boss` нет), барьер вечный. Это задокументировано (0101/0102). Колодец снизу закрыт дверью `wellgate`, рычаг стоит в Гнезде, тест проверяет, что с поверхности обхода нет. Механики ставят блоки только внутри своего шаблона, `Dungeons.Shield` им не нужен (это движок данжа, не эффект палочки).
- **Dungeons**: `avoiding(Def...)`/`clashes` по списку, `ASH` в `DEFS`, `Ash.gear` в `spawnHall`, отмена клика при повторной выдаче (0099 nit 7) — корректно.
- **Хвосты 4.7 (0099)**: `RiftLogic.gravity`/`landY` эквивалентны прежнему коду плюс предел −3. `EarthLogic.warnDue` верна (1, 10, 100…, для 0 и меньше — false).
- **Boss3 (0100)**: `won` ставится в `startDeath`, `paid` гарантирует один `lairWon`. `stage != 3` не обрывает гибель после засчитанного зала. Отмена урона хоста при `host == null` ограничена тегом `ewb3_host`. Утечек нет: `purge` на 100-м тике остался.
- **Ресурсы**: ru/en симметричны (разница ключей пустая), все 9 новых ключей есть в обоих файлах. Модели и текстуры `ash_key`/`ember_key` на месте, страницы JEI добавлены.
- **SRG**: новые `m_6643_` (Container.getContainerSize) и `m_8020_` (Container.getItem) проверены в 0102. Остальные уже были в коде.
- **Отчёты 0099–0103** заполнены по шаблону, раздел «Проверить в игре» есть везде.

## Замечания
1. **[major] tools/gen_ash.py:130-139.** Бойницы 1×3 (y 4/18/32) и арки колокольни 7×6 (y 44) — открытые проёмы в 2 блока толщиной. Игрок с элитрами (или другим способом подняться снаружи, если поставить блоки за маской) влетает в башню, звонит в колокол и спускается в шахту, минуя дверь `door a=ash`. Так обходится трофей Монолита (Осколок Пепел), то есть прогрессия. `DungeonTest` проверяет только ходьбу, поэтому это не ловит. → Закрыть бойницы и арки `iron_bars` (или стеклянной панелью / решёткой) в `tower_carve`/`deco` и добавить в тест «из воздуха снаружи нет прохода в рамку башни». Альтернатива: колокол срабатывает, только если открыта дверь `ash` (проверить её блок в `onClick` для `bell`).
2. **[minor] DungeonMech.java:fires/fireClick.** Жаровню можно зажечь, не кликая по ней: горящей стрелой, огненным шаром, палочкой огня. Неверную такую жаровню `fires()` просто погасит, без волны. Значит, порядок можно подобрать перебором без штрафа. → В `fires()` при `!firePrefix` (головоломка не решена) звать `fireWave` так же, как при неверном клике (с тем же `WAVE_CAP`).
3. **[minor] DungeonMech.java:crumbles (возврат платформы).** `blocked` учитывает только игроков. Блейз, хаск или магмовый куб, стоящий в клетке платформы, будет замурован, когда она вернётся. → Перед `fill(..., state(m.a))` проверить `m_6249_` по AABB платформы на живые сущности (или откладывать возврат, как для игрока).
4. **[minor] Ash.java:76-80.** `inChest` проверяет только сундук, по которому кликнули. Ключ, переложенный в другой сундук или эндер-сундук, выдаётся заново. Это безвредно: ключ открывает одну дверь и не тратится. → Оставить, но упомянуть в `docs/dungeons.md`.
5. **[nit] ExplosionWandMod.java:59-61.** `EMBER_KEY` вставлен между javadoc «Печать Храма» и `TEMPLE_SEAL`: комментарий теперь относится к Угольному ключу, а у `TEMPLE_SEAL` его нет. → Перенести `EMBER_KEY` выше, со своим javadoc.
6. **[nit] ExplosionWandMod.java:53.** Javadoc `ASH_SEAL_SHARD` («данжа пока нет, предмет говорит „путь не открыт“») устарел. → Обновить.
7. **[nit] EarthLogic.java:65-67.** `warnDue` вставлен между javadoc `moved` и самим `moved`: два javadoc подряд, у `moved` описания нет. → Переставить.
8. **[nit] DungeonMech.java onClick (braziers).** `Integer.parseInt(a[2])` без `trim()`, а в `fireSlots` с `trim()`. Формат проверен генератором, так что это только про единообразие.
9. **[nit] отчёт 0100, git-команда** без строки `Co-Authored-By`, в отличие от остальных. Ниже дан общий набор команд, он это перекрывает.

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `reports/0104-2026-10-21-code-reviewer-review-4-8.md` | этот отчёт |

## Проверка
- `python tools/build.py`: 159 классов, SRG исходники/байткод 248/249, неизвестных 0. Пути классов: ошибок 0. linkcheck: расхождений 0. Проблем ID 0. `gen_dungeons`: 10 файлов, проблем 0. Тесты BossCity, BossRift, CityMobs, City, Dungeon, Earth, Mats, Maze, RiftMobs, RiftStaff — OK. Jar `dist/explosionwand-1.20.1-2.16.0.jar` (1672 КБ).
- Новые SRG: `m_6643_` → `Container.getContainerSize`, `m_8020_` → `Container.getItem` (по отчёту 0102). Других новых нет.
- Баланс сверен с кодом:
  - струи: 4 урона + 3 с огня, кулдаун 12 тиков, вдох и выдох по 25 тиков;
  - гейзеры: 2 урона + 2 с огня, бульканье 30 тиков, выброс 14 тиков;
  - осыпание через 30 тиков, возврат через 140 тиков;
  - элита: 160 ХП, 6 урона, скорость 0,27, стойкость к отбрасыванию 0,8;
  - волна: 6 точек, новая не зовётся, пока стоят 4 и больше мобов.

  Всё совпадает с 0101/0102.

## Проверить в игре
- Всё из 0101 и 0102.
- Дополнительно по замечанию 1: долететь на элитрах до арки колокольни или бойницы яруса и проверить, проходим ли проём.
- По замечанию 2: зажечь неверную жаровню горящей стрелой.

## Решения и почему
- Major не повышен до blocker: проблема в геймплее, а не в падении или утечке. Тот же класс обхода (полёт) у наземных данжей уже был. Фиксить в 2.16.1 или 4.9.

## Проблемы и открытые вопросы
- Политика барьера Святилища до 4.10 (из 0101/0102) — на решение пользователя.

## Git (выполняет пользователь)
`Dungeons.java` содержит правки 0099, 0101 и 0102, поэтому он уходит в коммит Святилища (3). Порядок важен.
```
git add src/com/explosionwand/RiftLogic.java src/com/explosionwand/RiftMobs.java src/com/explosionwand/Earth.java src/com/explosionwand/EarthLogic.java tests/RiftMobsTest.java tests/EarthTest.java reports/0099-2026-10-21-mod-engineer-tails-4-7.md
git commit -m "fix(dungeon): хвосты 4.7: предел падения, тесты толчка, лог сбоев" -m "- предел падения -3 и снап приземления в RiftLogic, тесты поводка" -m "- Earth.guard логирует повторные сбои (1, 10, 100...)" -m "- отмена клика при повторной выдаче Печати - в коммите Святилища (Dungeons.java)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"

git add src/com/explosionwand/Boss3.java reports/0100-2026-10-21-boss-engineer-boss3-nits.md
git commit -m "fix(boss): хвосты ревью Монолита Сна (nit 5, 6, 8)" -m "- урон по хосту отменяется, пока host == null" -m "- полоска и титры не показываются наблюдателям" -m "- зал засчитывается в начале гибели" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"

git add tools/gen_ash.py tools/gen_dungeons.py src/com/explosionwand/Ash.java src/com/explosionwand/DungeonMech.java src/com/explosionwand/Dungeons.java src/com/explosionwand/Mats.java src/com/explosionwand/ExplosionWandMod.java src/com/explosionwand/jei/ExplosionWandJei.java stubs/net/minecraft/world/Container.java tests/DungeonTest.java resources/data/explosionwand/structures/ash_north.nbt resources/data/explosionwand/structures/ash_mid.nbt resources/data/explosionwand/structures/ash_south.nbt resources/assets/explosionwand/models/item/ash_key.json resources/assets/explosionwand/models/item/ember_key.json resources/assets/explosionwand/textures/item/ash_key.png resources/assets/explosionwand/textures/item/ember_key.png resources/assets/explosionwand/lang/ru_ru.json resources/assets/explosionwand/lang/en_us.json docs/world.md docs/dungeons.md reports/0101-2026-10-21-mod-engineer-ash-sanctuary-2a.md reports/0102-2026-10-21-mod-engineer-ash-sanctuary-2b.md
git commit -m "feat(dungeon): Пепельное святилище: башня, 5 уровней, колодец" -m "- gen_ash.py: башня с колоколом, шахта, Зал Пепла, Огненная переправа, Четыре жаровни, арена, Гнездо, колодец-выход" -m "- метки bell, jets, crumble, geyser, braziers в DungeonMech" -m "- Пепельный и Угольный ключи, повторная выдача ключей и Печати Храма" -m "- Dungeons.ASH обходит Разломы и Города, Осколок Пепел указывает на святилище" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"

git add resources/META-INF/mods.toml docs/history.md reports/0103-2026-10-21-asset-engineer-release-2-16-0.md
git commit -m "docs(assets): релиз 2.16.0, Пепельное святилище" -m "- версия 2.16.0" -m "- запись в history.md" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"

git add reports/0104-2026-10-21-code-reviewer-review-4-8.md
git commit -m "docs(agents): ревью фазы 4.8" -m "- APPROVE WITH NITS, major: проёмы башни обходят дверь Осколка" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

## Следующий шаг
- `mod-engineer`: замечания 1–3 (решётки в проёмах башни и тест «с воздуха», волна при поджоге не кликом, проверка сущностей при возврате платформы) — патч 2.16.1 или начало 4.9. Замечания 5–8 — попутно.
- main: закрыть фазу 4.8 (ROADMAP, CLAUDE.md «Структура»: `Ash.java`, `gen_ash.py`).
