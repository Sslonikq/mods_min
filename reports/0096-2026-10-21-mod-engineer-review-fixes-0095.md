# 0096 · Правки по ревью 0095

- **Дата:** 2026-10-21
- **Агент:** mod-engineer
- **Версия мода:** 2.15.0 (не выпущена, версию не менял)
- **Статус:** done
- **Связанные отчёты:** 0094, 0095

## Задача
Закрыть major 1, minor 2-4 и nit 6-7 из 0095.

## Что сделано
- **major 1:** `RiftMobs.Mob.applyKnock` двигает моба через новую `RiftLogic.knockMove`: подшаги не длиннее 0,45 блока, на каждом проверка `spaceFor` и поводка, упёршаяся ось обнуляется (скольжение вдоль стены), потолок и пол так же. Стена и дверь-печать в 1 блок не перепрыгиваются. Приземление на целый y сохранено.
- **minor 2:** `DungeonGuard.onPiston` отменяет событие целиком, если в радиусе 13 от поршня есть охраняемый блок (босс жив).
- **minor 3:** `onGrief` работает только для `EnderMan`; остальные мобы сохраняют грифинг. Описано в `docs/dungeons.md`. Заглушка `EnderMan` добавлена.
- **minor 4:** `Dungeons.column`: если `WORLD_SURFACE_WG >= 66` (выше уровня моря 63), воды нет и вторая оценка `OCEAN_FLOOR_WG` не считается.
- **nit 6:** `RiftMobs.knock` не принимает импульс, пока моб под Стоп-Кадром; `step` при заморозке сбрасывает накопленный импульс.
- **nit 7:** `RiftMobs.knock` для любого моба данжа возвращает `true` (статуя просто не двигается), `false` только для не-мобов данжа; комментарий исправлен.

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `src/com/explosionwand/RiftLogic.java` | `Step`, `KNOCK_SUBSTEP`, `knockMove` |
| `src/com/explosionwand/RiftMobs.java` | `applyKnock` на подшагах, `knock` (статуя, Стоп-Кадр), сброс импульса при заморозке |
| `src/com/explosionwand/DungeonGuard.java` | `onPiston` целиком в радиусе 13, `onGrief` только EnderMan |
| `src/com/explosionwand/Dungeons.java` | `column`: вторая оценка шума только у уровня моря |
| `stubs/net/minecraft/world/entity/monster/EnderMan.java` | новая заглушка |
| `tests/RiftMobsTest.java` | `knockTests`: стена 1 блок, печать, потолок, свободный путь |
| `docs/dungeons.md` | поршни и грифинг |

## Проверка
- `python tools/build.py`: 156 классов, SRG 246/247, неизвестных 0, linkcheck 0 ошибок, ID 0, тесты OK (с повтором после конфликта с параллельной сборкой).
- Новых SRG-имён нет. `EnderMan` в реальном Forge лежит в `net.minecraft.world.entity.monster` (linkcheck подтвердил).

## Проверить в игре
- Ветер, Комета, притяжение у стены/перегородки/двери-печати в 1 блок: мобы не проходят насквозь.
- Слизь или мёд с поршнем у стены Разлома/Города до победы: не двигается.
- Зомби у входа в данж подбирают предметы; эндермен блоки не крадёт.
- TPS при подходе к новому Разлому.
- Моб под Стоп-Кадром не вылетает после разморозки; статуя Города не двигается от Кометы.

## Решения и почему
- Порог 66 в `column` взят с запасом над уровнем моря 63, без нового SRG для `getSeaLevel`.
- Подшаги вынесены в `RiftLogic` ради теста без Minecraft.

## Проблемы и открытые вопросы
- Нет.

## Git (выполняет пользователь)
```
git add src/com/explosionwand/RiftLogic.java src/com/explosionwand/RiftMobs.java src/com/explosionwand/DungeonGuard.java src/com/explosionwand/Dungeons.java stubs/net/minecraft/world/entity/monster/EnderMan.java tests/RiftMobsTest.java docs/dungeons.md reports/0096-2026-10-21-mod-engineer-review-fixes-0095.md
git commit -m "fix(wand): импульс подшагами, поршни и грифинг у данжа" -m "- мобы не проходят стену в 1 блок от толчка" -m "- поршень отменяется целиком у охраняемого данжа" -m "- мобгрифинг отключён только у эндерменов" -m "- вторая оценка шума только у уровня моря"
```

## Следующий шаг
- Короткое повторное ревью `applyKnock`.
