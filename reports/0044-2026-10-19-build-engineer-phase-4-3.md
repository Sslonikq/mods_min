# 0044 · Проверка сборки фазы 4.3

- **Дата:** 2026-10-19
- **Агент:** build-engineer
- **Версия мода:** 2.6.0
- **Статус:** done (правок кода не потребовалось)
- **Связанные отчёты:** 0041, 0042, 0043, 0038

## Задача
Независимо проверить сборку, новые SRG, заглушки, ID, тесты фазы 4.3 (мобы Разлома).

## Что сделано
- `python tools/build.py`: 122 класса; SRG исходники 216 / байткод 217, неизвестных 0; ID: проблем 0; `gen_dungeons --check`: ошибок 0; DungeonTest OK (436349 блоков, layout 2, 88 меток), MazeTest OK, RiftMobsTest OK; jar `dist/explosionwand-1.20.1-2.6.0.jar`.
- `tools/ids.py` ok; `gen_dungeons.py --check` ok.
- Новые SRG относительно HEAD (7), все проверены `lookup.py --srg`:
  | SRG | Значение |
  |---|---|
  | `m_20142_` | Entity.isSprinting() |
  | `m_269333_` | DamageSources.mobAttack(LivingEntity) |
  | `m_60812_` | BlockStateBase.getCollisionShape(BlockGetter, BlockPos) |
  | `m_83281_` | VoxelShape.isEmpty() |
  | `m_19098_` | InteractionResultHolder.pass(Object) (4.2) |
  | `m_46672_` | Level.updateNeighborsAt(pos, block) (4.2) |
  | `m_6018_` | ServerLevelAccessor.getLevel() (4.2) |
  Сигнатуры в заглушках совпадают (возврат, параметры, не static).
- Заглушки: новые/изменённые содержат только используемые члены (проверено grep по src). Ванильные id хостов (`spider`, `cave_spider`, `skeleton`, `zombie`, `phantom`, `polar_bear`, `ravager`, `rabbit`) проверены по `reg.json` вручную (`ids.py` их не видит - известное ограничение из 0042).
- Тесты: RiftMobsTest настоящий (319 строк): окно лучника по всем 260 тикам, слух, броня голема, Тень, геометрия, команда summon всех видов (теги, число частей), расстановка по залам Разлома, предел жуков. DungeonTest: `d.layout == 2` и подпись `:v2`, 88 меток, достижимость всех точек мобов, защита меток; новые метки `explosionwand:` проверяются в RiftMobsTest (`kindOfMark`, залы 7 и 11 с големами, зал 9 ванильный).
- Jar: `mods.toml` version 2.6.0, structures `rift_*`/`mech_test`, модели/текстуры/рецепты Разлома на месте; lang `explosionwand.mob.*` (9 ключей) есть в ru и en, ключ `golem_open` использован в коде.

## Изменённые файлы
Только этот отчёт.

## Проверить только в игре
Всё из раздела «Проверить в игре» 0042: ходьба NoAI-хостов через setPos, высота посадки частей, телеграфы, баланс, утечки частей при сбросе зала, TPS в залах 2 и 7. Также `DungeonGuard.onSpawn`/`FillBucketEvent` (события Forge, на заглушках не проверить).

## Замечания (логику не трогал)
- Тень может сработать через стену (радиус 14, dy<=5) - уже в 0042.
- `ids.py` не видит id хостов в `Kind`; низкий приоритет, при желании добавить регулярку.

## Git (выполняет пользователь)
```
git add reports/0044-2026-10-19-build-engineer-phase-4-3.md
git commit -m "docs(build): проверка сборки фазы 4.3" -m "- build.py, SRG (7 новых), ID, тесты: OK" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```
