# 0151 · Фаза 5.2: проверка сборки и ресурсов, лог в Later.tick

- **Дата:** 2026-11-09
- **Агент:** build-engineer
- **Версия мода:** 3.3.0
- **Статус:** done
- **Связанные отчёты:** 0149, 0150

## Задача
Полная сборка 5.2, linkcheck, check_worldgen, обратная проверка новых SRG, лог в `Later.tick`, сверка ресурсов и кода для 9 новых предметов.

## Что сделано
- `python tools/build.py` (до и после правки): 211 классов, SRG 343/344, неизвестных 0, пути классов 0, linkcheck 178 классов / 556 членов, расхождений 0, проблем ID 0, все тесты OK (в т.ч. BloodIronTest, HellGateTest, HellBlocksTest), jar `dist/explosionwand-1.20.1-3.3.0.jar`.
- `tools/linkcheck.py`: jar 8, расхождений 0, не проверено 0. `tools/check_worldgen.py`: 68 файлов, ok.
- `Later.tick`: `catch (Exception ignored)` заменён на `fail(e)`. Поведение прежнее (задача пропускается, остальные идут), но в stderr пишется `[explosionwand] Later: сбой отложенной задачи №N: ...` (N = 1, 10, 100, 1000), на первом сбое ещё и стек. Механизм тот же, что в `DungeonGuard`/`Dungeons` (`System.err.println`), новых заглушек не нужно.
- Обратный поиск `lookup.py --srg` для всех новых имён из 0149 (lookup даёт yarn-имена):
  - `m_49874_` Block.getDroppedStacks(state, level, pos, be, entity, tool) -> List
  - `m_222967_` BlockState.onStacksDropped(level, pos, tool, boolean)
  - `m_49840_` Block.dropStack(level, pos, stack)
  - `m_36054_` PlayerInventory.insertStack(stack) -> boolean
  - `m_150109_` Player.getInventory()
  - `m_46805_` isChunkLoaded(BlockPos) (hasChunkAt)
  - `m_135827_` / `m_135815_` ResourceLocation getNamespace / getPath
  - `m_8102_` Item.getMiningSpeedMultiplier(stack, state) -> float
  - `m_6609_` getDurability, `m_6624_` getMiningSpeedMultiplier, `m_6631_` getAttackDamage, `m_6604_` getMiningLevel, `m_6601_` getEnchantability, `m_6282_` getRepairIngredient (все ToolMaterial = Tier)
  Все совпадают по смыслу и сигнатурам с описанием 0149.
- Ресурсы (скриптом): для всех 9 предметов есть модель (generated для 4 материалов, handheld для 5 инструментов), текстура, `item.explosionwand.<id>`, `.desc` и `explosionwand.jei.<id>` в ru и en; `explosionwand.jei.eclipse_crystal_block` тоже есть в обоих. Все JSON в `resources/` валидны (0 ошибок). Рецепты: плавка и плавильня сырого железа, 5 инструментов (слиток + палка, шаблоны ванильные), `eclipse_crystal_block` 2x2 из осколков. Лут: blood_iron_ore -> raw_blood_iron 1-2 + удача + шёлк, crown_gem_ore -> crown_gem + удача + шёлк, eclipse_cluster -> шёлк / age=3 -> eclipse_shard 2-4; соответствует проверкам BloodIronTest. В jar попали ресурсы (32 файла по новым ID), `BloodIron.class`, mods.toml 3.3.0.

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `src/com/explosionwand/Later.java` | лог сбоев отложенных задач вместо молчаливого catch |

## Проверка
Сборка и тесты проходят (см. выше). Расхождений ресурсов и кода не найдено, правок вне Later не потребовалось.

## Проверить в игре
- Если отложенная задача где-то падает, в `latest.log` появится «Later: сбой отложенной задачи». Первые запуски после обновления посмотреть на эту строку: ранее скрытые ошибки теперь видны (в том числе возможные причины жалоб на Копателя).
- Всё остальное - списки из 0149 и 0150 (пещеры, Копатель, инструменты, JEI).

## Решения и почему
- Лог ограничен по частоте (1/10/100/1000), чтобы повторяющийся сбой не засорял лог каждый тик.
- Не добавлена JEI-страница для `eclipse_crystal_block` (ключ lang есть и не используется) - решение mod-engineer, не ошибка.

## Проблемы и открытые вопросы
- Крупных несоответствий нет.
- Карверы (пробой данжей/Цитадели), корень проблемы Копателя, нужна игра.

## Git (выполняет пользователь)
```
git add src/com/explosionwand/Later.java reports/0151-2026-11-09-build-engineer-phase-5-2-check.md
git commit -m "fix(build): лог сбоев отложенных задач в Later.tick" -m "- вместо молчаливого catch: строка в stderr по 1/10/100/1000 сбою, стек на первом" -m "- поведение не изменено"
```

## Следующий шаг
code-reviewer: ревью 5.2.
