# 0058 · Посох Разлома: исправления по ревью 0056

- **Дата:** 2026-10-19
- **Агент:** mod-engineer
- **Версия мода:** 2.7.0
- **Статус:** done
- **Связанные отчёты:** 0053, 0056

## Задача
major 3, minor 7, minor 5 (только строка посоха) из 0056.

## Что сделано
- major 3: `RiftStaff.step` выбирает дальность через чистую `pick(...)` с интерфейсом `Probe`. Точка назначения и промежуточные точки каждый ~1 блок проверяются `Dungeons.near(l, x, y, z, 1)`. Точки с данжем пропускаются, берётся самая дальняя безопасная, ближе данжа. Если место есть только через данж, шаг отказывает с сообщением.
- minor 7: трещина бьёт `indirectMagic(p, p)` (`m_269104_`, проверено `lookup.py --srg`), так что опыт и убийства засчитываются игроку. Пропускаются `AbstractVillager` (жители и торговцы), `TamableAnimal` со своим хозяином (`m_21830_`) и любые ручные (`m_21824_`). Оба SRG-имени проверены `--srg`. `m_21824_` в yarn называется `isTamed`, а не `isOwnedBy`. Это приемлемо для игры: чужих ручных тоже не трогаем.
- minor 5 (посох): actionbar через `{"translate":...}`. Ключи `explosionwand.rift_staff.no_room` и `.no_dungeon` добавлены в конец ru_ru и en_us точечно. Тексты Boss.java не трогал.
- Заглушки: `TamableAnimal`, `AbstractVillager` (новые), `DamageSources.m_269104_`.
- Тест `tests/RiftStaffTest.java`: открытое поле, угол данжа на пути, цель в данже, данж вплотную, нет места.

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| src/com/explosionwand/RiftStaff.java | pick/Probe, проверка данжа, indirectMagic, фильтр питомцев и жителей, lang-ключи |
| stubs/net/minecraft/world/entity/animal/TamableAnimal.java | новая заглушка |
| stubs/net/minecraft/world/entity/npc/AbstractVillager.java | новая заглушка |
| stubs/net/minecraft/world/damagesource/DamageSources.java | m_269104_ |
| tests/RiftStaffTest.java | новый тест |
| resources/assets/explosionwand/lang/ru_ru.json, en_us.json | 2 ключа |

## Проверка
`python tools/build.py`: SRG 220/221, неизвестных 0, ID 0, все тесты OK, jar собран.

## Проверить в игре
- Shift+ПКМ у подкопанной стены Разлома снаружи: не переносит внутрь, сообщение; в чистом поле шаг на 8 блоков.
- Трещина не бьёт жителей, своих и чужих приручённых волков и кошек; убитые мобы дают опыт и дроп игрока.
- Сообщения шага на английском в en_us.

## Решения и почему
- `Dungeons.near` с pad 1 вместо `at`: данж проверяется с запасом в блок, шаг не ныряет через угол.
- Сообщение отказа различает «нет места» и «данж», чтобы игрок понимал причину.

## Следующий шаг
nit 11 (Altar) не делался, не в задаче.

## Git (выполняет пользователь)
```
git add src/com/explosionwand/RiftStaff.java stubs/net/minecraft/world/entity/animal/TamableAnimal.java stubs/net/minecraft/world/entity/npc/AbstractVillager.java stubs/net/minecraft/world/damagesource/DamageSources.java tests/RiftStaffTest.java resources/assets/explosionwand/lang/ru_ru.json resources/assets/explosionwand/lang/en_us.json reports/0058-2026-10-19-mod-engineer-rift-staff-review-fixes.md
git commit -m "fix(staff): шаг Посоха Разлома не входит в данж, трещина щадит питомцев" -m "- проверка точки назначения и пути через Dungeons.near, тест RiftStaffTest" -m "- indirectMagic(p, p), пропуск жителей и ручных" -m "- сообщения шага через lang"
```
