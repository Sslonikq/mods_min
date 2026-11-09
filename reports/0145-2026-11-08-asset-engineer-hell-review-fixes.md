# 0145 · Правки ресурсов по ревью 0144 (замечания 2, 3, 6, 7, 8, 10, 11)

- **Дата:** 2026-11-08
- **Агент:** asset-engineer
- **Версия мода:** 3.1.0
- **Статус:** done
- **Связанные отчёты:** 0144, 0141, 0143

## Сделано
- 2: `resources/data/minecraft/tags/blocks/mushroom_grow_block.json` (replace:false, `explosionwand:ash_soil`); запись добавлена и в `tools/gen_hell_blocks.py`. Остальные фичи не имеют такого дефекта: деревья (`tree`) сами не проверяют почву, подмену делает `dirt_provider`, а опору проверяет наш `on(...)`; `random_patch`+`simple_block`/`block_column` проверяют `canSurvive` блока (0143); `spring_feature` и `ore` берут свои `valid_blocks`/цели. Единственная фича с тегом почвы - huge mushroom.
- 3: `worldgen/biome/hell.json` вернул, генератор `tools/assets/gen_hell_world.py` пишет его копией `hell_ash_wastes` (в `multi_noise` не входит).
- 6: лут `ash_layer` - `set_count` 2..8 по `block_state_property layers`; в `gen_hell_blocks.py` вид лута `layers`, чтобы генератор не затёр.
- 7: заголовок Ада: ru «Ад» / «Мир затмения, пепла и лавы», en «Hell» / «A world of eclipse, ash and lava».
- 8: `docs/world.md` - описание Ада обновлено (бесконечный мир 5.1, площадка на твёрдом полу, HellData, старые миры 63, биом-копия).
- 10: `tools/gen_hell_assets.py` дописывает в `mineable/pickaxe.json`, а не перезаписывает.
- 11: `density_function/hell/final.json`: добавлен терм `0.25*(y - surface_height + 8)` в cave: пещеры не подходят к поверхности ближе 8 блоков; `hell_lavafall` count 16 -> 5.

## Проверки
- `python tools/build.py` - ок (первый прогон упал на ArmorSetsTest из-за чужой параллельной правки, повтор чистый); `tools/ids.py` ок; `tools/check_worldgen.py` ок; все JSON в resources валидны.

## Проверить в игре
- Угольные грибы растут в Тлеющем лесу; поверхность без «дырявости»; число лавопадов; слой пепла x8 даёт 8 предметов; в мире 3.0.x нет «Unknown registry key».

## Проблемы и открытые вопросы
- `explosionwand.journal.p5` (оба lang) всё ещё говорит «Ад не достроен» - вне списка, нужен отдельный текст.
- Не запускал `gen_hell_blocks.py` целиком (лут правил руками + в генераторе, результат эквивалентен).

## Git (выполняет пользователь)
```
git add resources/data/minecraft/tags/blocks/mushroom_grow_block.json resources/data/explosionwand/loot_tables/blocks/ash_layer.json resources/data/explosionwand/worldgen/biome/hell.json resources/data/explosionwand/worldgen/density_function/hell/final.json resources/data/explosionwand/worldgen/placed_feature/hell_lavafall.json resources/assets/explosionwand/lang/ru_ru.json resources/assets/explosionwand/lang/en_us.json docs/world.md tools/assets/gen_hell_world.py tools/gen_hell_blocks.py tools/gen_hell_assets.py reports/0145-2026-11-08-asset-engineer-hell-review-fixes.md
git commit -m "fix(dims): правки ресурсов Ада по ревью 5.1" -m "- ash_soil в mushroom_grow_block, лут слоя пепла по layers" -m "- биом hell оставлен копией для миров 3.0.x" -m "- пещеры не у поверхности, лавопадов 5 на чанк, заголовок Ада"
```
