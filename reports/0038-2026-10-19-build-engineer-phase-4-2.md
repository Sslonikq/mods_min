# 0038 · Фаза 4.2: сборка и сверка заглушек Forge/Minecraft

- **Дата:** 2026-10-19
- **Агент:** build-engineer
- **Версия мода:** 2.5.0
- **Статус:** done
- **Связанные отчёты:** 0032, 0033, 0034, 0036, 0037

## Задача
Полная сборка, сверка новых заглушек шагов 0032–0037 с настоящим API Forge 47.x / Minecraft 1.20.1, проверка, что `ids.py` и `gen_dungeons --check` ловят новые метки, оценка времени сборки (п. 9 ревью 0036).

## Что сделано
- `python tools/build.py` без правок: зелёная, 14,7 с (приемлемо).
- Сверены заглушки (см. таблицу ниже). Исправлений в заглушках не потребовалось.
- `tools/ids.py`: регулярки `Fx.particle/Fx.effect/Fx.state|block|Disp.block` принимали только `[a-z_]`, и ID с цифрой пропускались молча. Теперь `[a-z0-9_]`.
- Негативный тест на копии `src/` в temp: подмена звука и частицы в `DungeonMech.java` на несуществующие ловится (`block.lever.clack`, `critx`); для ID с цифрой (`crit2`) до правки не ловилось.

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `tools/ids.py` | цифры в ID частиц, эффектов и блоков |

## Проверка
- `python tools/build.py`: 103 класса, SRG исходники/байткод 212/213, неизвестных 0, проблем ID 0, `[3b]` gen_dungeons 4 файла / 0 проблем, DungeonTest OK, MazeTest OK, jar `dist/explosionwand-1.20.1-2.5.0.jar` (830 КБ). Повторно после правки `ids.py`: то же.
- Новые SRG (`lookup.py --srg`, Mojang-смысл сверен знанием; yarn-название в таблице другое, но intermediary-ID тот же метод):

| SRG / член | Таблица | Настоящий API 1.20.1 | Вердикт |
|---|---|---|---|
| `Level.m_46672_(BlockPos, Block)` | World.updateNeighborsAlways | `Level.updateNeighborsAt(BlockPos, Block)` | верно (void, instance) |
| `InteractionResultHolder.m_19098_(T)` | TypedActionResult.pass | `InteractionResultHolder.pass(T)`, static | верно |
| `InteractionResultHolder.m_19092_(T, boolean)` | success | `sidedSuccess(T, boolean)`, static (старая) | верно |
| `ServerLevelAccessor.m_6018_()` | ServerWorldAccess.toServerWorld | `getLevel()` -> `ServerLevel` | верно; интерфейс (в заглушке `interface`) |
| `MobSpawnType` | — | enum, 16 констант NATURAL…PATROL, порядок совпадает; в коде только `.name()` строкой, прямых ссылок нет | верно |
| `MobSpawnEvent` (abstract, extends `Event`) | — | в 47.x тоже `getEntity(): Mob`, `getLevel(): ServerLevelAccessor`, `getX/Y/Z(): double` | верно |
| `MobSpawnEvent.FinalizeSpawn#getSpawnType()` | — | возвращает `MobSpawnType` | верно |
| `FinalizeSpawn#setSpawnCancelled(boolean)` | — | есть в Forge 47.1+ (рядом `isSpawnCancelled`) | уверен на ~90%, не по jar |
| `FillBucketEvent extends PlayerEvent`, `getTarget(): HitResult` | — | так и есть, событие `@Cancelable` + `@HasResult` | верно |

- Регистрация: `MinecraftForge.EVENT_BUS.addListener(DungeonGuard::onSpawn/onBucket)` с типами вложенных событий без дженериков - соответствует API `addListener(Consumer<T extends Event>)`.
- `ids.py`/`gen_dungeons --check`: звуки и частицы из `DungeonMech` проверяются `ids.py` (негативный тест выше); блоки меток (`steps`, `platform`, `seal`, палитра, мобы `spawn`) - `validate_mech` и проверка реестра в `gen_dungeons.py`; шаблоны сравниваются побайтно (свежесть).

## Проверить в игре
- `setSpawnCancelled` существует и действует: естественный спавн в Разломе блокируется (лог без строки `защита от спавна ... отключена`).
- Ведро воды/лавы рядом с Разломом не срабатывает (`FillBucketEvent` приходит и отменяется).

## Решения и почему
- Код мода не менял: заглушки совпадают с API.
- Цифры в регулярках ids.py: ложное «ок» хуже ложной тревоги.

## Проблемы и открытые вопросы
- Нет jar Forge, поэтому всё про Forge-события - по знанию, не по таблицам (в `.cache/maps` только Mojang/SRG-методы, классов Forge там нет). Сверка по jar при первой Gradle-сборке.
- Запасной путь `ev.setCanceled(true)` в `onSpawn`: если `FinalizeSpawn` не `@Cancelable` в этой сборке, `setCanceled` бросит `UnsupportedOperationException` (не `LinkageError`) и он не перехватится. Достижим только при отсутствии `setSpawnCancelled`, на практике маловероятно; если захочется закрыть - `catch (RuntimeException)` рядом (mod-engineer, по желанию).
- `ids.py` не проверяет динамические ID (`Dungeons.state(m.a)`, `Dungeons.state(a[1])`): их закрывает `gen_dungeons --check` по меткам.
- `arcane_helmet.json`/`arcane_leggings.json` помечены `M` из-за окончаний строк (из 0037).

## Git (выполняет пользователь)
```
git add tools/ids.py reports/0038-2026-10-19-build-engineer-phase-4-2.md
git commit -m "build(build): ids.py принимает цифры в ID частиц и блоков" -m "- сверка заглушек Forge фазы 4.2, отчёт 0038" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

## Следующий шаг
Повторное ревью `code-reviewer`, затем закрытие фазы 4.2.
