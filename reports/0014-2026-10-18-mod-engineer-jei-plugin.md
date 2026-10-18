# 0014 · JEI-плагин (страницы информации о трофеях)

- **Дата:** 2026-10-18
- **Агент:** mod-engineer
- **Версия мода:** 2.2.0 (не менялась)
- **Статус:** done
- **Связанные отчёты:** 0012, 0013

## Задача
Фаза 2, пункт 3: JEI-плагин, описывающий предметы без крафта.

## Что сделано
- `src/com/explosionwand/jei/ExplosionWandJei.java`: `@JeiPlugin`, `IModPlugin`, uid `explosionwand:jei`; в `registerRecipes` для rift_core, eclipse_heart, dream_staff, dreaming_eye, whale_staff вызывается `addItemStackInfo(ItemStack, Component)` с ключом `explosionwand.jei.<item>`.
- Плагин ссылается на `ExplosionWandMod` (в одну сторону); основной код на плагин и JEI не ссылается. Класс грузит только JEI по аннотации, без JEI он не загружается. Jar JEI в себя не включает, зависимость в mods.toml необязательная (отчёт 0013).
- Заглушки (только используемое): `stubs/mezz/jei/api/JeiPlugin.java`, `IModPlugin.java` (getPluginUid, registerRecipes), `registration/IRecipeRegistration.java` (две перегрузки addItemStackInfo). В jar заглушки не попадают.
- Lang: 5 ключей `explosionwand.jei.*` в ru_ru.json и en_us.json (источник и путь до босса). Тексты построены по цепочке 2.2.0 (ключ/печати), не по черновику docs/world.md.
- Loot.chest (0012): при повторном убийстве второй сундук ставится рядом, поэтому в текстах написано «в сундуке с добычей», без привязки к позиции.

## Изменённые файлы
`src/com/explosionwand/jei/ExplosionWandJei.java`, `stubs/mezz/jei/api/{JeiPlugin,IModPlugin}.java`, `stubs/mezz/jei/api/registration/IRecipeRegistration.java`, `resources/assets/explosionwand/lang/ru_ru.json`, `en_us.json`.

## Проверка
`python tools/build.py`: 84 класса, SRG 0 неизвестных, ID 0 проблем, MazeTest OK, jar собран. Новое SRG-имя `m_237115_` (Component.translatable) проверено через `--srg`. JSON валидны (перезаписаны через json.dump).

## Проверить в игре
Без JEI:
- Мод стартует, в логе нет ошибок про mezz.jei, предметы и боссы работают как раньше.
С JEI 15.x (Forge 1.20.1):
- Мод стартует; в логе JEI виден плагин `explosionwand:jei`.
- Для 5 предметов (Ядро Разлома, Сердце Затмения, Посох Сна, Недремлющее Око, Посох Кита) в JEI на вкладке «Информация» есть страница с текстом на языке клиента.
- Сигнатуры JEI API сверены по памяти (сеть не проверялась): `IModPlugin.getPluginUid()` возвращает ResourceLocation, `IRecipeRegistration.addItemStackInfo(ItemStack, Component...)`. Если JEI пожалуется, сверить в игре.

## Решения и почему
- Описания для сундуковых предметов, а не для самих боссов: так просят отчёты 0013.
- Крафтовые печати и компасов не описывал (компасов в коде нет).

## Проблемы и открытые вопросы
- При переходе на порядок из docs/world.md (фаза 4) тексты придётся обновить.

## Git (выполняет пользователь)
```
git add src/com/explosionwand/jei/ExplosionWandJei.java stubs/mezz/jei/api/JeiPlugin.java stubs/mezz/jei/api/IModPlugin.java stubs/mezz/jei/api/registration/IRecipeRegistration.java resources/assets/explosionwand/lang/ru_ru.json resources/assets/explosionwand/lang/en_us.json reports/0014-2026-10-18-mod-engineer-jei-plugin.md
git commit -m "feat(items): JEI-плагин с описанием трофеев" -m "- страницы информации для 5 предметов без крафта" -m "- плагин в отдельном классе, без JEI не грузится" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```
Замечание: lang-файлы уже содержат правки отчёта 0013; при раздельных коммитах учесть.

## Следующий шаг
Фаза 2: оставшиеся пункты ROADMAP; обновить тексты JEI в фазе 4.
