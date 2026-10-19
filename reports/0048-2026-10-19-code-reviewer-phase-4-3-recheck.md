# 0048 · Перепроверка фазы 4.3 после исправлений (0046, 0047)

- **Дата:** 2026-10-19
- **Агент:** code-reviewer
- **Версия мода:** 2.6.0
- **Статус:** done
- **Связанные отчёты:** 0045 (ревью, REQUEST CHANGES), 0046 (boss-engineer), 0047 (mod-engineer), 0041–0044

## Задача
Проверить, что major 1, minor 2–5 и nit 6, 7, 10, 13 из 0045 закрыты и не дали регрессий. Особо: поводок не ломает зачистку зала; у жуков работают предел и уборка; `renderEvery` не оставляет мобов «замершими» в бою. Дать итоговые git-команды фазы с учётом того, что код 4.2 не закоммичен.

## Вердикт: **APPROVE WITH NITS**
Major и все minor закрыты. Регрессий я не нашёл. Новые замечания — три nit, и фазу они не держат.

## Что проверено

### major 1, жуки: закрыт
- **Предел.** `RiftBeetles.tick` считает жуков запросом `m_6249_` по тегу `ewrk_crystal_beetle` в рамке 80 блоков вокруг входа. Найденный жук, которого нет в `MOBS`, убирается через `RiftMobs.remove` вместе с частями.
  - Жуки появляются не дальше ~57 блоков от входа, поводок у них 15. Значит, рамка 80 покрывает всех.
  - Жук, переживший выгрузку, при следующей загрузке днём (игрок в 90 блоках) будет убран или подхвачен `scan`, потом отправлен в `retire`. В обоих случаях предел 6 выдерживается.
- **Уход.** Проверка `beetleGone(isNight, ближайший игрок > 110)` перенесена в `RiftMobs.tick` (раз в 20 тиков) и стоит до фильтра `near` по 56 блокам. Теперь она срабатывает и у жуков, которые сами не тикают.
  - `retire` во время обхода `MOBS.values()` реестр не трогает: `discard` событий не шлёт. `ConcurrentModificationException` нет, запись уберёт основной цикл (`m_213877_`).
  - Ночью незарегистрированных жуков подхватит `scan` (в 64 блоках от игрока), и на следующем проходе они уйдут через `retire`.
- `docs/dungeons.md:110` исправлен. В тесте есть `beetleGone`.

### minor 2, прицел лучника: закрыт
`ARCHER_NO_AIM = ARCHER_CLOSE - 25` = 125. Прицел длится 25 тиков и успевает закончиться к фазе закрытия (150). Тест проверяет `c + 25 <= ARCHER_CLOSE`.

### minor 3, поводок: закрыт, зачистку не ломает
- `leashBlocked` запрещает только шаг, который уходит дальше `leash` (28, у жуков 15) **и** дальше текущего положения. Моб, который уже за пределом (например, после `adopt`), может идти назад. Упёршийся моб просто не двигается:
  - `think()` идёт дальше, замах и удар по дистанции работают;
  - Пожиратель через `stuck > 8` уходит в SEARCH;
  - хост живой, уязвимый, в `alive()` считается.
- Бесконечного цикла нет: `walk`/`fly` перебирают до 3 вариантов шага и возвращают `false`.
- Засада Тени (`setPos` без поводка) по-прежнему ограничена игроком в 14 блоках от дома, так что Тень остаётся в пределах ~18 блоков.
- Оговорка — nit 15 ниже.

### minor 4, нагрузка: закрыт, «замороженных» в бою нет
- `renderEvery`: каждые 3 тика в 16 блоках, каждые 6 до 32, дальше поза не шлётся. Дистанция трёхмерная, до ближайшего игрока.
- Атаки ограничены меньшими радиусами:
  - лучник `nearest(26, 8)`, до 27,2 блока;
  - голем `nearest(30, 6)`, до 30,6;
  - Призма `nearest(24, 9)`, до 25,6;
  - осколочник 24/4, Тень 14/5.
  
  Поэтому моб, который атакует, всегда в зоне рендера.
- Дальше 32 блоков застывает только анимация: части едут на хосте, и перемещение видно всегда. Например, Пожиратель, бегущий на шум от далёкого стрелка, двигается, но без анимации лап и поворота.
- При возврате в 32 блока кэш `last[]` отправит только изменившиеся части, поэтому застрявшей позы нет.
- Спрятанная Тень (`quiet()`) шлёт позу один раз, `quietSent` сбрасывается при выходе.
- Оговорка про плавность — nit 16.

### minor 5, Портал: закрыт
- `Portal.pullAndSwallow`: в начале цикла стоит `if (!Minions.controllable(e)) continue;`. Хосты (`ewr_h`, `ewd…`) и части (`ewr_p`) не притягиваются и не уносятся.
- Попутно Портал перестал затягивать хосты и хитбоксы боссов (`ewb*`). Это правильно, но поведение посоха изменилось, стоит проверить в игре.
- Игроки и обычные мобы без тегов затягиваются как раньше.

### nit 6, 7, 10, 13: закрыты
- `PREV.keySet().retainAll(...)`.
- `NOISES` подрезается в `onHurt` и ограничен 64 записями.
- `warned = false` в `clear()`.
- `Duration:-1`.
- Фраза про `LinkageError`/`RuntimeException` в `history.md` исправлена.
- Попутно в `docs/dungeons.md` записано про сложность (nit 8 частично: документировано, но не проверено на Hard).

### Сборка и правила
- `python tools/build.py`: 122 класса, SRG 216/217, неизвестных 0, проблем ID 0, gen_dungeons 4/0. DungeonTest, MazeTest, RiftMobsTest OK. Jar `dist/explosionwand-1.20.1-2.6.0.jar` (880 КБ).
- Новых SRG нет. Правила CLAUDE.md не нарушены: новые блоки, частицы и звуки не добавлены, `Cmd`/`Fx` как раньше.

## Замечания

### nit (новые)
15. **[nit] `RiftMobs.java:300` (`leash = 28`) против `Dungeons.ALIVE_PAD = 24`.** Поводок считается от точки появления, а рамка поиска — от коробки зала. Где метка ближе 4 блоков к краю коробки, моб в теории может выйти за «зал + 24». Расчёт по генератору: такие метки только в зале 3 (Зеркальный коридор, ширина 9):
    - Призмы `(2,-32,166)`, `(4,-32,172)` — на 2–3 блока;
    - Тени — на 1–4 блока, но Тень поводком не ходит.
    
    Выход возможен только поперёк коридора сквозь стену, на 25+ блоков в сторону, то есть на практике нет. → При случае `leash = ALIVE_PAD - 2` или проверка по коробке зала + pad (её можно передать из `spawnHall`).
16. **[nit] `RiftMobs.java:389` + `RiftLogic.renderEvery`.** В зоне 16–32 поза шлётся раз в 6 тиков с `interpolation_duration:3`: полцикла движется, полцикла стоит. Лучник часто стреляет как раз с 16–26 блоков. → Передавать в `render` текущий `every` и ставить `interpolation_duration` равным ему.
17. **[nit] Git-история.** Коммит `d230ac9` (выполнен по блоку 0046) содержит `RiftMobs`/`RiftLogic`/`RiftBeetles`/`RiftMobsTest`, но не `RiftModels`, `Dungeons` и заглушки. Сам по себе он не собирается: `RiftMobs` ссылается на ещё не закоммиченные `RiftModels` и `Dungeons.RIFT`/`ALIVE_PAD`. После коммитов ниже `HEAD` снова собирается. Переписывать историю не нужно, это просто для сведения (например, для `git bisect`).

### Перенос в 4.4 (не закрыты, фазу не держат)
- Из 0045:
  - **nit 8** — баланс на Hard: в docs записано, в игре не проверено;
  - **nit 9** — имена над хостами не видны, ожидаемо;
  - **nit 11** — `ids.py`: `sound("…")` и хосты `Kind`;
  - **nit 12** — круг кулака голема 2,4 против удара 2,5, ширина хитбокса в `walk`;
  - **nit 14** — перенос из 0036.
- Из 0036: **nit 13** (`mech_test.nbt` в jar) и **nit 14** (залы спят до полной достройки).
- Из этого ревью: nit 15, 16.

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `reports/0048-2026-10-19-code-reviewer-phase-4-3-recheck.md` | этот отчёт (код не менялся) |

## Проверка
- `python tools/build.py`: см. выше, всё OK.
- Расчёт поводка против рамки `alive` — скриптом по `gen_dungeons.build_rift()` (временный, в проект не сохранялся).
- `git log`: `d230ac9 fix(boss): замечания ревью 4.3…` поверх `e8c4e3d`. Код 4.2 и остальной код 4.3 не закоммичены.

## Проверить в игре
1. **Жуки:** днём у входа ≤ 6. Уйти на 200+ блоков и вернуться с другой стороны: всё ещё ≤ 6. Ночью и после перезахода старые жуки исчезают (облачко `poof`), висящих деталей нет.
2. **Поводок:** вывести осколочника из зала 1 по коридору — дальше ~28 блоков от точки он не идёт, стоит у границы, бьёт, если подойти, убиваем. Зал без убийства не засчитывается. Голем и Пожиратель у предела тоже не застывают в атаке.
3. **Рендер:** в 16–32 блоках облик обновляется чуть дёргано (nit 16), дальше 32 замирает анимация, перемещение видно. В бою (лучник с 20–26 блоков) телеграфы и выстрел видны полностью.
4. **TPS** в залах 2 и 7 с полным набором мобов — заметно ниже нагрузка, чем в 0045 (оценка около 30 команд за тик → меньше вдвое и больше на средних дистанциях).
5. **Портал** у моба Разлома и у босса: не затягивает; игрок и обычные мобы затягиваются.
6. Остальное — по спискам 0041–0047 (старый мир 2.5.0 и сброс layout 2, лучник, Тень, Призма, Пожиратель, голем, мирный режим, перезапуск).

## Решения и почему
- APPROVE WITH NITS. Major закрыт полностью: подсчёт по сущностям и уход вне фильтра 56. Поводок сделан как ограничение шага, а не возврат домой: это не ломает ни атаки, ни счёт живых. Новые nit касаются плавности и крайнего случая в одном зале, на поведение в бою не влияют.

## Проблемы и открытые вопросы
- Код 4.2 по-прежнему не закоммичен. Правки 4.2 и 4.3 лежат в одних файлах, разделить их можно только `git add -p`. Ниже они идут вместе. Команды из 0036–0047 **не выполнять**: блок 0046 уже выполнен (`d230ac9`), остальное заменено набором ниже.
- `arcane_helmet.json`/`arcane_leggings.json`: в diff только окончания строк, в коммиты не включаются.

## Git (выполняет пользователь)
Итоговые команды фаз 4.2 и 4.3 поверх `d230ac9`. Они заменяют git-блоки отчётов 0036–0047. Последний коммит закрывает фазу: в нём версия, lang, docs и отчёты.

```
git add stubs/net/minecraft/world/InteractionResultHolder.java stubs/net/minecraft/world/damagesource/DamageSources.java stubs/net/minecraft/world/entity/Entity.java stubs/net/minecraft/world/entity/MobSpawnType.java stubs/net/minecraft/world/level/Level.java stubs/net/minecraft/world/level/ServerLevelAccessor.java stubs/net/minecraft/world/level/block/state/BlockBehaviour.java stubs/net/minecraft/world/phys/shapes/VoxelShape.java stubs/net/minecraftforge/event/entity/living/MobSpawnEvent.java stubs/net/minecraftforge/event/entity/player/FillBucketEvent.java tools/ids.py reports/0038-2026-10-19-build-engineer-phase-4-2.md reports/0044-2026-10-19-build-engineer-phase-4-3.md
git commit -m "build(build): заглушки и проверка ID для Разлома и его мобов" -m "- заглушки Forge/Minecraft фаз 4.2-4.3 (спавн, ведро, коллизии, mobAttack)" -m "- ids.py принимает цифры в ID, отчёты сборки 0038, 0044" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"

git rm resources/data/explosionwand/structures/rift_test_entrance.nbt resources/data/explosionwand/structures/rift_test_halls.nbt
git add src/com/explosionwand/Dungeons.java src/com/explosionwand/DungeonData.java src/com/explosionwand/DungeonGuard.java src/com/explosionwand/DungeonMech.java src/com/explosionwand/SealShardItem.java src/com/explosionwand/ExplosionWandMod.java src/com/explosionwand/RiftModels.java tools/gen_dungeons.py tests/DungeonTest.java resources/data/explosionwand/structures/mech_test.nbt resources/data/explosionwand/structures/rift_entrance.nbt resources/data/explosionwand/structures/rift_mid.nbt resources/data/explosionwand/structures/rift_deep.nbt reports/0032-2026-10-19-mod-engineer-dungeon-guard-carryover.md reports/0033-2026-10-19-mod-engineer-dungeon-mechanics-engine.md reports/0034-2026-10-19-mod-engineer-rift-building.md reports/0037-2026-10-19-mod-engineer-review-fixes-4-2.md reports/0041-2026-10-19-mod-engineer-review-fixes-4-3.md reports/0042-2026-10-19-boss-engineer-rift-mobs.md
git commit -m "feat(dungeon): Разлом из 16 залов и шесть мобов Разлома" -m "- DungeonMech: ступени, лучи, зеркала, платформы, рычаги, секреты, печати" -m "- защита по маске шаблона, подпись раскладки по номеру (layout 2)" -m "- облик мобов (RiftModels), спавн по меткам explosionwand:<вид>, уборка с хостом" -m "- генератор: расстановка мобов, колонны лучников; DungeonTest" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"

git add src/com/explosionwand/Portal.java reports/0047-2026-10-19-mod-engineer-portal-ew-filter.md
git commit -m "fix(wand): портал не затягивает хосты мобов Разлома и боссов" -m "- Portal.pullAndSwallow пропускает сущности ew* (кроме приспешников)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"

git add resources/META-INF/mods.toml resources/assets/explosionwand/lang/ru_ru.json resources/assets/explosionwand/lang/en_us.json docs/world.md reports/0035-2026-10-19-asset-engineer-rift-version-docs.md reports/0036-2026-10-19-code-reviewer-phase-4-2.md reports/0039-2026-10-19-code-reviewer-phase-4-2-recheck.md reports/0043-2026-10-19-asset-engineer-rift-mobs-lang-version.md reports/0045-2026-10-19-code-reviewer-phase-4-3.md reports/0048-2026-10-19-code-reviewer-phase-4-3-recheck.md
git commit -m "docs(dungeon): версия 2.6.0, lang мобов Разлома, ревью 4.2-4.3" -m "- mods.toml 2.6.0, имена мобов и сообщение голема в ru/en" -m "- world.md: размер Разлома и отметка о мобах" -m "- ревью 0036, 0039, 0045 и перепроверка 0048: APPROVE WITH NITS" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

После этих четырёх коммитов `git status` должен показывать только `arcane_helmet.json` и `arcane_leggings.json` (окончания строк).

## Следующий шаг
- **main:** закрыть фазу 4.3.
  - `ROADMAP.md`: «4.3 ✅ закрыта, 2.6.0, отчёты 0041–0049; перенос в 4.4 — nit 8, 9, 11, 12, 15, 16 из 0045/0048 и nit 13–14 из 0036»;
  - закрывающий отчёт `reports/0049-2026-10-19-main-phase-4-3-closed.md`;
  - коммит `docs(agents): фаза 4.3 закрыта, текущая 4.4`;
  - вывести блок «Фаза 4.3 закрыта… /clear и /next-phase».
- **В 4.4, первым шагом:**
  - `build-engineer`: nit 11;
  - `boss-engineer`: nit 12, 15, 16 и проверка баланса на Hard (nit 8), вместе с логовом босса в зале 7 (там станет важен minor 1 из 0039: сброс зала с печатью);
  - nit 13–14 из 0036 — `mod-engineer`/`build-engineer`.
