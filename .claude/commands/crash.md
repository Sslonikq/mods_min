---
description: Разобрать падение или ошибку игры по latest.log / crash-report и найти место в коде мода
argument-hint: [путь к логу или описание, что произошло]
---

Игра: `C:\games\MultiMC\instances\1.20.1\.minecraft`. Уточнение пользователя: $ARGUMENTS

Только чтение: в папке игры ничего не менять и не удалять.

1. Найди источник:
   - если в аргументах путь к файлу — он;
   - иначе самый свежий файл в `crash-reports/` (если он новее последнего запуска в `logs/latest.log` — берём его), иначе `logs/latest.log`.
   Скажи, какой файл и за какое время взят: пользователь должен понять, тот ли это запуск.
2. Логи большие — не читай целиком. Grep по `explosionwand`, `Exception`, `Caused by`, `ERROR`, `FATAL`, `NoSuchMethodError`, `NoSuchFieldError`, `ClassNotFoundException`, `Failed to load`, `Couldn't parse` с контекстом 15–30 строк; затем Read нужного куска.
3. Сверь версию мода в логе (`explosionwand-1.20.1-X.Y.Z.jar`) с `resources/META-INF/mods.toml`. Если в игре старый jar — скажи об этом первым делом.
4. Разбери стек:
   - строки `com.explosionwand.*` → файл и строка в `src/` (ссылкой);
   - SRG-имена (`m_12345_`, `f_12345_`) → `python tools/lookup.py --srg <имя>`;
   - `NoSuchMethodError` / `NoSuchFieldError` / `AbstractMethodError` → почти всегда неверная заглушка в `stubs/` (сигнатура, static, тип возврата): сравни заглушку с настоящим членом, запусти `python tools/linkcheck.py`;
   - ошибки датапака (`Couldn't parse`, `Unbound values in registry`, `Failed to load registries`) → JSON в `resources/data/`, проверь `python tools/check_worldgen.py`;
   - ошибка без `explosionwand` в стеке — честно скажи, что это может быть не наш мод, и покажи, на что указывает лог.
5. Выведи коротко: что случилось → причина → где в коде → как чинить и кто (`/fix` или какой агент). Если причина — предположение, так и напиши и скажи, чем её подтвердить.
6. Сам не чини. Если пользователь попросит — дальше по `/fix`. Если падение показало дыру в проверках (то, что `build.py` должен был поймать) — отдельно предложи доработать проверку (`build-engineer`).
