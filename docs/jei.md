# JEI (Just Enough Items)

JEI нужен только для просмотра рецептов; мод работает и без него (зависимость необязательная, `mandatory=false`, `versionRange="[15,)"`).

- Версия: **JEI 15.x для Forge 1.20.1** (например, `jei-1.20.1-forge-15.x.y.z.jar`). Бери именно сборку `forge`, не `fabric` и не `common`.
- Скачать: страница JEI на CurseForge или Modrinth, фильтр «Minecraft 1.20.1», загрузчик Forge.
- Куда положить: в папку `mods` рядом с `explosionwand-1.20.1-X.Y.Z.jar` (`%APPDATA%\.minecraft\mods`).
- Предметы без обычного крафта (трофеи боссов) описывает JEI-плагин мода `ExplosionWandJei` (`src/com/explosionwand/jei/`): страница «как получить» для пяти предметов — rift_core, eclipse_heart, dream_staff, dreaming_eye, whale_staff. Кроме них, JEI показывает страницы «как получить» для 14 материалов и всех 16 палочек Земли (4.11): рецепты видны обычные, страница добавляет описание и источник материалов. Тексты — ключи `explosionwand.jei.<имя>` в обоих lang. Источники трофеев — в отчёте 0013.
