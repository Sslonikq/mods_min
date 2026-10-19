# 0075 · Проверка сборки фазы 4.6 (мобы Города, 2.9.0)

- **Дата:** 2026-10-19
- **Агент:** build-engineer
- **Версия мода:** 2.9.0
- **Статус:** done
- **Связанные отчёты:** 0071, 0072, 0073, 0074

## Задача
Независимо прогнать сборку и проверить новые SRG-имена, заглушки, lang и jar для фазы 4.6.

## Что сделано
- Полный `python tools/build.py`; сверка SRG-имён со списком из git diff (src/stubs/tests) против HEAD.
- Обратный поиск `lookup.py --srg` для новых и перечисленных имён; сверка lang ru/en; проверка jar.

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `reports/0075-2026-10-19-build-engineer-phase-4-6.md` | этот отчёт (код не менялся) |

## Проверка
- `python tools/build.py`: 142 класса, SRG уникальных 229 (в байткоде 230), нет в joined.tsrg 0, проблем ID 0, gen_dungeons --check 7 файлов, расхождений 0; тесты BossRiftTest, CityMobsTest, CityTest, DungeonTest, MazeTest, RiftMobsTest, RiftStaffTest: OK; jar `dist/explosionwand-1.20.1-2.9.0.jar` (275 файлов), mods.toml version="2.9.0".
- Единственное SRG-имя, которого нет в HEAD: `m_46471_` -> Level.isRaining()Z (заглушка Level.java совпадает: boolean, без параметров).
- Остальные перечисленные (уже были в HEAD, проверены по смыслу): m_21205_ getMainHandStack, m_21206_ getOffHandStack, m_41619_ ItemStack.isEmpty, m_41720_ ItemStack.getItem, m_6144_ isSneaking (Entity), m_20142_ isSprinting, m_128356_ CompoundTag.putLong, m_128454_ getLong, m_8961_ ServerPlayer.getRespawnPosition, m_123342_ Vec3i.getY; также m_46462_ Level.isNight, m_46467_ getGameTime, m_7967_ addFreshEntity.
- Заглушки: diff в stubs — одна строка в Level.java (добавлен только используемый m_46471_).
- lang ru_ru/en_us: валидный JSON, по 114 ключей, расхождений 0.

## Проверить в игре
- Мобы Города (CityMobs/CityMobsB): облик из block_display, поведение днём/ночью/в дождь (отлов m_46462_/m_46471_), удаление при дожде, урон и приспешники.
- Новые структуры sleep_*.nbt в игре (расстановка, нет ли пропусков по высоте).

## Решения и почему
Исправлений в tools/stubs/tests не потребовалось.

## Проблемы и открытые вопросы
- Замечаний по игровой логике нет. Вывод build.py в консоли Windows идёт в cp1251/OEM (кракозябры при чтении), на результат не влияет.

## Git (выполняет пользователь)
```
git add reports/0075-2026-10-19-build-engineer-phase-4-6.md
git commit -m "docs(build): проверка сборки фазы 4.6" -m "- build.py, SRG, ID, тесты и jar 2.9.0 проверены"
```

## Следующий шаг
code-reviewer: ревью фазы 4.6 перед коммитом.
