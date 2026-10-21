# 0090 · Откат правок Посоха Разлома из 2.14.0

- **Дата:** 2026-10-21
- **Агент:** mod-engineer
- **Версия мода:** без изменений (2.15.0 в сборке)
- **Статус:** done
- **Связанные отчёты:** 0080 (правка), 0083 замечание 1 (ревью)

## Задача
Пользователь: «Разлом ты ухудшил будто, верни». Вернуть поведение Посоха Разлома к состоянию до 2.14.0.

## Что сделано
Сравнение `git diff 1b7d7b9 HEAD -- RiftStaff/RiftLogic/RiftStaffItem`: в 2.14.0 изменён только `RiftStaff.java` (RiftLogic и RiftStaffItem не менялись; `RiftMobs` — только дроп материалов, к посоху не относится).
- **Откачено:** источник урона `m_269075_(p)` (playerAttack, резался бронёй) обратно на `m_269104_(p, p)` (indirectMagic, броню игнорирует). Урон 6, дальность, ширина, перезарядка, притяжение, эффекты и ограничения (тег `ewd`, жители, ручные питомцы, `Minions.controllable`) в 0080 не менялись и остаются как были. Комментарий у `pull` поправлен.
- **Оставлено:** `pull()` с `try/catch (RuntimeException | LinkageError)` и однократным логом: безвреден.
- **Найдено попутно (вероятная настоящая причина вылета):** в `RiftStaff.java` был `import net.minecraft.world.entity.animal.TamableAnimal`, а в Minecraft класс лежит в `net.minecraft.world.entity.TamableAnimal`. В игре это `NoClassDefFoundError` при проверке `instanceof` на первом же мобе (LinkageError, из-за которого игру роняло). Исправил импорт (как в `Earth.java`). Заглушку build-engineer уже перенёс в правильный пакет.
- `docs/history.md` (запись 2.14.0): честно написано, что смена источника урона откачена, а причина вылета не установлена; детали про импорт — здесь, в отчёте.

## Изменённые файлы
- `src/com/explosionwand/RiftStaff.java`
- `docs/history.md`
- `reports/0090-2026-10-21-mod-engineer-rift-staff-revert.md`

## Проверка
`python tools/build.py`: компиляция OK, все тесты OK (RiftStaffTest, RiftMobsTest, MatsTest, EarthTest, MazeTest, dungeon), jar `dist/explosionwand-1.20.1-2.15.0.jar`. Новых SRG нет (`m_269104_` проверен ревью 0083).

## Проверить в игре
- ПКМ Посохом Разлома по группе мобов: вылета нет, мобов затягивает, урон 6 проходит сквозь броню (проверить на мобе в броне); ручной волк и жители не затрагиваются.
- В `logs/latest.log` нет строки `[explosionwand] Посох Разлома: сбой`.

## Следующий шаг
Нет.

## Git
```
git add src/com/explosionwand/RiftStaff.java docs/history.md reports/0090-2026-10-21-mod-engineer-rift-staff-revert.md
git commit -m "fix(staff): Посох Разлома: вернуть indirect_magic и импорт TamableAnimal" -m "- урон снова игнорирует броню, как до 2.14.0" -m "- исправлен неверный пакет TamableAnimal (NoClassDefFoundError)" -m "- history.md: честная запись о защите от сбоя" -m "" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```
