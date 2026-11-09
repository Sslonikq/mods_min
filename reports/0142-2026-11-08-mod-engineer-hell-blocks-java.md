# 0142 · Блоки Ада: регистрация, поведение, высота площадки (5.1, Java)

- **Дата:** 2026-11-08
- **Агент:** mod-engineer
- **Версия мода:** 3.1.0 (в mods.toml и history.md не трогал: ставит asset-engineer)
- **Статус:** done
- **Связанные отчёты:** 0140 (ресурсы блоков), 0141 (мир Ада, параллельно)

## Задача
Фаза 5.1, Java-часть: зарегистрировать 34 блока из `docs/hell_blocks.md` и их BlockItem, предмет `coal_berries`; поведение особых блоков; точка появления у портала должна искать твёрдый пол в настоящем generated-мире; тесты.

## Что сделано
- **Регистрация** (`HellBlocks`): таблица `SPECS` (id + поставщик блока) и `register(BLOCKS, ITEMS)`, вызывается из конструктора мода до `BLOCKS.register(bus)`. Каждому блоку - `BlockItem`; последним предметом `coal_berries` (еда 3/0.4, `ItemNameBlockItem`: ставится как куст). Во вкладку «Магия» попадает автоматически (она берёт все ITEMS по порядку регистрации; Ада идёт после старых предметов). Свойства строго под blockstates: `axis` (RotatedPillarBlock), `layers` 1-8 (SnowLayerBlock), `age` 0-3 (куст, друза), `facing` (друза).
- **Прочность, свет, звуки** по таблице; камни и руды - `requiresCorrectToolForDrops`; растения - без коллизии, мгновенная ломка, `noOcclusion`.
- **Поведение** (`HellBlock`, `CoalBerryBushBlock`, `EclipseClusterBlock`):
  - Гейзер: цикл запланированными тиками раз в секунду, фаза 0..7 со сдвигом по координатам; фаза 7 - дым и «лавовый» хлопок (предупреждение), фаза 0 - столб `flame`/`lava` до 7 блоков (стоп на полном блоке), поджиг на 6 с и 3 урона всем живым в столбе (огнестойкие пропускаются). Игроков в 48 блоках нет - молчит. Запускается из `onPlace` и из случайного тика (дубль того же тика не ставится).
  - Раскалённый камень: `stepOn` -> урон `hotFloor` 1, кроме присевших и огнестойких.
  - Друза затмения: случайный тик, `eclipse_crystal_block` в кубе 3x3x3 -> age+1 (1/3 шанса), крепится к блоку за спиной, исчезает без опоры, свет 4/6/8/10 по age, форма по facing. Рост пропускает данжи (`Dungeons.protectedAt`).
  - Тлеющая листва: случайный тик -> искры `flame`/`lava` вниз, если снизу воздух; не распадается (обычный Block, не LeavesBlock). Искровик: `lava` сверху.
  - Куст углеягод: рост age случайным тиком (1/5, без света), ПКМ при age >= 2 -> 1-2 / 2-3 ягоды, age -> 1; ломка даёт то же по лут-таблице.
  - Лианы (огненная, призрачная): нужен блок с твёрдой нижней гранью над собой; нет опоры - исчезает с выпадением (через `updateShape` -> воздух).
  - Растения: сухая трава, огнецвет, искровик, куст - на любом блоке с твёрдой верхней гранью; призрачный цветок - только почва/песок душ и призрачный мох; огненный коралл - на твёрдом блоке, если это шлак/магма или лава в радиусе 2.
  - Слой пепла - ванильный `SnowLayerBlock` без случайных тиков (не тает от света), ковры - `CarpetBlock`, брёвна - `RotatedPillarBlock`.
- **Площадка у портала** (`HellGate`, `HellData`): высота пола больше не константа. При первом входе `pickFloor` идёт снизу вверх с y=41 до 200 по колонне (0,0) и берёт первый твёрдый блок с двумя свободными над ним (лава/вода не считаются; лава моря на y=40 отсекается началом с 41); нет поверхности - площадка в воздухе на y=70. Высота хранится в `HellData` (`data/explosionwand_hell.dat` обычного мира). Гнездо - на floor+5, игрок - floor+1, `inReturnGate/stripReturnGate` принимают floor. Площадка: пол на f, 3 слоя камня под ним (закрывают лаву), 11 клеток воздуха над; материалы теперь блоки Ада (затменный камень/булыжник/кирпичи, шлак вместо магмы), лава-яма, ограда, фонари прежние. Миры 3.0.x: если на (0,68,-6) стоит занятое гнездо - пол = 63 (прежняя площадка). Бедрок-маркер `MARKER` убран (его заменили данные мира).
- **Тесты**: `HellBlocksTest` (набор ID из таблицы в docs = регистрации блоков и предметов, порядок, blockstate/лут/lang/модель предмета у каждого, урожай куста, свет друзы, фаза гейзера); `HellGateTest` обновлён (раскладка относительно floor, 41/63/90/119, `pickFloor` на синтетических колоннах: море, остров, лавовое озеро, всё лава, нет поверхности).

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `src/com/explosionwand/HellBlocks.java`, `HellBlock.java`, `CoalBerryBushBlock.java`, `EclipseClusterBlock.java`, `HellData.java` | новые |
| `src/com/explosionwand/ExplosionWandMod.java` | вызов `HellBlocks.register(BLOCKS, ITEMS)` |
| `src/com/explosionwand/HellGate.java` | высота пола: `socketAt/spawnY/pickFloor/classify/ensureHell/buildPlatform(f)`, `inReturnGate(floor, pos)`, без `HELL_SOCKET/HELL_Y/MARKER` |
| `tests/HellBlocksTest.java` (новый), `tests/HellGateTest.java` | тесты |
| `stubs/...` | Direction (6 сторон, getOpposite), BlockPos (up/down/relative), RandomSource.nextInt, Block (+createBlockStateDefinition, registerDefaultState, stepOn, box, popResource, getStateForPlacement), BlockBehaviour (+randomTick, tick, onPlace, use, canSurvive, updateShape, getShape, Properties, BlockStateBase), StateDefinition(+Builder, any), StateHolder.getValue, IntegerProperty, DirectionProperty, BlockStateProperties, RotatedPillarBlock, SnowLayerBlock, CarpetBlock, SoundType, FoodProperties, ItemNameBlockItem, BlockPlaceContext, CollisionContext, Level.getEntitiesOfClass, LevelAccessor.scheduleTick, DamageSources (inFire, hotFloor), Entity.fireImmune, Item.Properties.food |

## Проверка
- `python tools/build.py`: 201 класс, SRG неизвестных 0 (исходники и байткод), classcheck 0 ошибок, **linkcheck против настоящих jar Forge: проверено 172, отсутствующих 0**, ID 0 проблем, тесты все OK (включая HellBlocksTest, HellGateTest), jar `dist/explosionwand-1.20.1-3.1.0.jar`.
- Новые SRG проверены `lookup.py --srg`: `m_213898_` randomTick, `m_213897_` tick, `m_6807_` onPlace, `m_141947_` stepOn, `m_6227_` use, `m_7898_` canSurvive, `m_7417_` updateShape, `m_5940_` getShape, `m_7926_` createBlockStateDefinition, `m_5573_` getStateForPlacement, `m_49959_` registerDefaultState, `m_61090_` any, `m_61104_` add, `m_61631_` IntegerProperty.create, `m_61143_` getValue, `m_49796_` box, `m_49840_` popResource, `m_60977_` randomTicks, `m_60999_` requiresCorrectTool, `m_60966_` instabreak, `m_60918_` sound, `m_121945_` relative, `m_7495_` below, `m_7494_` above, `m_6630_` above(n), `m_122424_` getOpposite, `m_186460_` scheduleTick, `m_6443_` getEntitiesOfClass, `m_269387_` inFire, `m_269047_` hotFloor, `m_5825_` fireImmune, `m_20275_` distanceToSqr, `m_41489_` food, `m_38760_/m_38758_/m_38767_` FoodProperties.Builder, `m_60783_` isFaceSturdy, `m_60838_` isCollisionShapeFullBlock, `m_60812_` getCollisionShape, `m_76178_` FluidState.isEmpty, `m_188503_` nextInt, поля `f_61372_` FACING, звуки `f_56740_` GRASS, `f_56736_` WOOD, `f_56746_` SAND, `f_56714_` WEEPING_VINES, `f_154655_` AMETHYST_CLUSTER, `f_56724_` BONE, `f_56757_` SWEET_BERRY_BUSH, `f_56759_` STEM.

## Проверить в игре
- Мод грузится, все 34 блока и «Углеягоды» во вкладке «Магия», у блоков нет фиолетовых текстур; бревно/слой/друза ставятся с верными свойствами (ось, слои стопкой, друза на стене/потолке).
- Гейзер: поставить в Аду/на Земле - через ~секунду начинает цикл; раз в 8 с дым за секунду до выброса, затем столб огня до 7 блоков, урон 3 и поджиг; под потолком столб короче. Гейзеры, созданные генерацией мира, оживают после случайного тика (в среднем около минуты после загрузки чанка) - это ожидаемо.
- Раскалённый камень жжёт при ходьбе, не жжёт на приседе; огнестойкие (ифрит и пр.) нет.
- Друза: рядом с кристаллом затмения растёт от age 0 до 3 (смена модели и света), без кристалла не растёт; без опоры - выпадает.
- Куст углеягод: растёт, ПКМ при age >= 2 даёт ягоды и откатывает в age 1; ягоды едятся, ставятся как куст; ломка даёт то же.
- Лианы: висят только под блоком; ломаем блок над ними - лиана выпадает. Призрачный цветок не ставится на обычную землю. Огненный коралл ставится на шлак/магму или рядом с лавой.
- Первый вход в Ад по новому миру: площадка стоит на твёрдой поверхности (y>=41), не в лаве, портал назад работает и не ломается; вход из мира, где Ад уже был (3.0.x) - прежняя площадка на y=63, ничего не перестраивается.
- Если поверхность в (0,0) - гора, площадка вырезается в склоне; если в (0,0) воздух до лавы - площадка висит на y=70 над лавой.

## Решения и почему
- Блоки с поведением собраны в один класс с `Kind`, а не по классу на блок: меньше заглушек и мест для SRG; отдельные классы только там, где свои свойства (куст, друза).
- Листва - обычный Block, а не LeavesBlock: не нужно ни `distance`/`persistent`, ни распада (blockstate всё равно один вариант `""`).
- Рост и эффекты на блоках идут через `Dungeons.protectedAt` (в Аду всегда пусто, дёшево, но правило проекта соблюдено); гейзер блоки не меняет.
- Высота пола в `SavedData`, а не бедрок-маркер: маркер нельзя надёжно искать в мире с горами и рельефом, а в старых мирах он и так не нужен (узнаём по гнезду).
- Растения на «любой твёрдый блок»: спецификация «растёт на ash_soil» ломала бы постановку игроком и фичи worldgen; призрачный цветок и коралл строгие по таблице.

## Проблемы и открытые вопросы
- **Для asset-engineer:** в `data/minecraft/tags/blocks/` нет `needs_diamond_tool.json` с `explosionwand:dark_obsidian` - без него тёмный обсидиан добывается любой киркой (твёрдость 50 и `requiresCorrectTool` уже стоят). Ресурсы блоков я не трогал.
- **Для asset-engineer (worldgen):** фичи, ставящие `ghost_flower`, `fire_coral`, лианы, должны ставить их на допустимую опору (иначе `canSurvive` вернёт false и фича ничего не поставит): призрачный цветок - на `soul_soil/soul_sand/ghost_moss`; коралл - на `slag/magma_block` или в 2 блоках от лавы; лианы - под сплошной блок или листву. Куст углеягод/сухая трава/огнецвет/искровик - на любой твёрдый верх.
- Ковры (мох) и слой пепла не «растут» сами (в таблице стояло «растёт»): ванильный CarpetBlock/SnowLayerBlock; распространение - через worldgen/позже, если понадобится.
- Лианы - один блок, не мультигрань (как договорено в 0140). Огнецвет/мох без визуального смещения.
- Платформа теперь из блоков Ада (раньше ванильный незер-набор, магма заменена шлаком): внешний вид новой площадки отличается; поведение портала - то же.
- Углеягоды: эффектов еды нет (3 голода, 0.4 насыщения).

## Git (выполняет пользователь)
```
git add src/com/explosionwand/HellBlocks.java src/com/explosionwand/HellBlock.java src/com/explosionwand/CoalBerryBushBlock.java src/com/explosionwand/EclipseClusterBlock.java src/com/explosionwand/HellData.java src/com/explosionwand/ExplosionWandMod.java src/com/explosionwand/HellGate.java tests/HellBlocksTest.java tests/HellGateTest.java stubs reports/0142-2026-11-08-mod-engineer-hell-blocks-java.md
git commit -m "feat(items): блоки Ада - регистрация, гейзер, друза, куст; пол площадки по рельефу" -m "- 34 блока и углеягоды, поведение гейзера, раскалённого камня, друзы, куста, лиан" -m "- высота площадки у портала Ада считается по рельефу и хранится в HellData" -m "- тесты HellBlocksTest и HellGateTest"
```
(В `git add stubs` попадут и правки заглушек других агентов, если были: перед коммитом посмотреть `git status`.)

## Следующий шаг
asset-engineer: `needs_diamond_tool` для тёмного обсидиана, опоры в фичах worldgen, версия 3.1.0 и запись в history.md. Затем code-reviewer; дальше деревья/данжи Ада по ROADMAP 5.
