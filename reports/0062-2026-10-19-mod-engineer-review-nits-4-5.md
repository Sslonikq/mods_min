# 0062 · Хвосты ревью 0060 (первый шаг фазы 4.5)

- **Дата:** 2026-10-19
- **Агент:** mod-engineer
- **Версия мода:** 2.7.0 (не менялась; релиз 2.8.0 в конце фазы)
- **Статус:** done
- **Связанные отчёты:** 0060, 0056 (nit 13), 0036 (nit 14)

## Задача
Закрыть перенесённые замечания: minor 2, nit 3, nit 4, nit 13 (0056), nit 14 (0036).

## Что сделано
- **minor 2:** `Dims.OLD_ARENA` (`explosionwand:arena`) и `Dims.evacuateOldArena`: раз в 20 тиков (`Ticker`) каждый игрок в этом измерении телепортируется в обычный мир на точку возрождения (если она в обычном мире), иначе на мировой спавн; высота через `safeY`. Перенос через `Dims.teleport`, ORIGIN не трогается.
- **nit 3:** `DungeonGuard.onDetonate`: ядро столба убирается из списка блоков взрыва, затем ставится воздух вручную, со звуком, без дропа (как в `onBreak`).
- **nit 4:** `RiftStaff.crack`: сущности с тегом `ewd` пропускаются, если игрок не в данже.
- **nit 13:** `docs/history.md`: раздел 2.6.2 помечен «отдельным jar не выходила; вошла в 2.7.0».
- **nit 14:** залы и раньше спят до полной достройки (`inDungeon`, `DungeonMech`, `Boss`), это оставлено. Исправлено, что мешало: `Dungeons.schedule` теперь ставит все недостроенные части, если игрок уже внутри данжа (`rec.contains`), не дожидаясь 96 блоков до части. Плюс подсказка в actionbar `explosionwand.dungeon.building` раз в 5 с, пока идёт достройка (ru/en).
- Заглушки: `ServerPlayer.m_8961_`, `m_8963_`, `Level.m_220360_`, `BlockPos.m_7949_`.

## SRG (проверены `lookup.py --srg`)
m_8961_ getSpawnPointPosition, m_8963_ getSpawnPointDimension, m_220360_ getSpawnPos, m_7949_ toImmutable.

## Проверка
`python tools/build.py`: SRG 224, неизвестных 0, проблем ID 0, gen_dungeons 0, все тесты OK, jar 2.7.0.

## Изменённое поведение
Только описанное выше; существующие предметы не менялись.

## Проверить в игре
- Старый мир 2.6.x с игроком в измерении `arena`: при входе его выбрасывает в обычный мир на кровать или спавн.
- Взорвать (TNT, палочки) столб логова Сердца: аметистовое ядро исчезает без дропа, остальные блоки данжа целы.
- Посох Разлома, трещина по мобам данжа с поверхности: мобы с тегом `ewd` не вытягиваются; внутри данжа действует как раньше.
- Подойти к недостроенному Разлому вплотную: части достраиваются без ожидания, при входе внутрь виден текст про достройку; ступени зала 1 появляются после достройки.

## Следующий шаг
Основная часть фазы 4.5.

## Git
git add src/com/explosionwand/Dims.java src/com/explosionwand/Ticker.java src/com/explosionwand/DungeonGuard.java src/com/explosionwand/RiftStaff.java src/com/explosionwand/Dungeons.java stubs/net/minecraft/server/level/ServerPlayer.java stubs/net/minecraft/world/level/Level.java stubs/net/minecraft/core/BlockPos.java resources/assets/explosionwand/lang/ru_ru.json resources/assets/explosionwand/lang/en_us.json docs/history.md reports/0062-2026-10-19-mod-engineer-review-nits-4-5.md
git commit -m "fix(dungeon): хвосты ревью 4.4" -m "- игроки из старого arena уходят в обычный мир" -m "- ядро столба при взрыве без дропа" -m "- трещина не вытягивает мобов данжа снаружи" -m "- достройка данжа по входу игрока и подсказка" -m "- пометка 2.6.2 в history"
