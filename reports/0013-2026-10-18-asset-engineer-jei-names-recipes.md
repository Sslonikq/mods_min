# 0013 · JEI-зависимость, аудит рецептов, названия «Палочка»

- **Дата:** 2026-10-18
- **Агент:** asset-engineer
- **Версия мода:** 2.2.0 (не менялась)
- **Статус:** done (build.py не проходит из-за чужих правок в src/, см. проблемы)
- **Связанные отчёты:** —

## Задача
Фаза 2: JEI как необязательная зависимость, аудит рецептов, переименование «Палка» в «Палочка», docs/jei.md.

## Что сделано
- mods.toml: зависимость `jei`, mandatory=false, ordering NONE, side BOTH, `[15,)`.
- Найден баг: ванильный загрузчик отвергает рецепты с неравной шириной строк шаблона и с неиспользуемым ключом. Были сломаны 5 рецептов: arcane_helmet, arcane_chestplate, arcane_leggings, arcane_boots (лишний ключ D), portal_gun. Исправлены (те же ингредиенты и количество, строки выровнены пробелами).
- Названия: ни одной «Палки» в ru_ru.json. Портал-ган: «Палочка порталов» / Portal Wand; Кулак Небес: «Палочка кулака небес» / Heavenly Fist Wand. «Посох» остался только у eclipse_staff, dream_staff, whale_staff (сильные, с боссов или из их добычи). Ключи lang в обоих файлах совпадают.
- docs/jei.md создан.

## Аудит: предмет, крафт
| Предмет | Крафт |
|---|---|
| explosion_wand, black_hole_wand, ice_storm_wand, rift_wand, void_wand, haunt_wand, inverted_wand, chronos_wand, mimic_wand, tesseract_wand, possess_wand, timestop_wand, comet_wand | есть |
| rift_key | есть |
| fist_wand | есть (кварцевый блок + золотой блок + палка) |
| portal_gun | есть, но нужен rift_core (трофей) |
| eclipse_seal | есть, нужен rift_core |
| eclipse_staff | есть, нужны eclipse_heart + rift_core (трофеи) + незерит |
| dream_seal | есть, нужен eclipse_heart |
| sky_seal | есть, нужно dreaming_eye |
| arcane_helmet/chestplate/leggings/boots | есть (исправлены) |
| rift_core | НЕТ |
| eclipse_heart | НЕТ |
| dreaming_eye | НЕТ |
| dream_staff | НЕТ |
| whale_staff | НЕТ |

## Предметы без крафта (для JEI-плагина mod-engineer)
| Предмет | Источник |
|---|---|
| explosionwand:rift_core | сундук добычи «Добыча Сердца Разлома» (Boss.java, Loot.chest): с rift_core, 8 алмазов, незеритовый слиток, 32 аметистовых осколка |
| explosionwand:eclipse_heart | сундук «Добыча Короля Затмения» (Boss2.java): + звезда Незера, 16 алмазов, 3 незерита, 2 зачар. золотых яблока |
| explosionwand:dream_staff | сундук «Добыча Монолита Сна» (Boss3.java) |
| explosionwand:dreaming_eye | тот же сундук Монолита Сна |
| explosionwand:whale_staff | сундук «Добыча Небесного Кита» (Boss4.java) |

Печати (eclipse_seal, dream_seal, sky_seal) и посох затмения крафтятся, но цепочка зависит от трофеев выше. Компасов и «Посоха Разлома», упомянутых в docs/world.md, в коде ещё нет.

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `resources/META-INF/mods.toml` | зависимость jei |
| `resources/assets/explosionwand/lang/ru_ru.json`, `en_us.json` | названия |
| `resources/data/explosionwand/recipes/arcane_{helmet,chestplate,leggings,boots}.json`, `portal_gun.json` | исправлены шаблоны |
| `docs/jei.md` | новый |

## Проверка
- Все JSON в resources валидны (json.load), ключи lang совпадают.
- `python tools/build.py`: упал на компиляции в src/ (не мои файлы): "reached end of file while parsing" в Arena3.java:71, Arena4.java:78 и ещё одна ошибка; похоже на незавершённые правки boss-engineer. К ресурсам отношения не имеет.

## Проверить в игре
- Рецепты брони и Портал-гана видны в книге рецептов и крафтятся (раньше не загружались).
- Мод стартует без JEI и с JEI 15.x.

## Решения и почему
- Заглавная буква в именах-собственных (Некто, Хроноса), строчная в нарицательных (разлома, пустоты, порталов).
- Рецепты брони пересобраны по тому же набору ингредиентов; "спорных" добавлений нет.

## Проблемы и открытые вопросы
- Вопрос: нужен ли крафт rift_core/eclipse_heart/dreaming_eye? Оставлены как чистые трофеи.
- Остальные ингредиенты рецептов существуют в 1.20.1; баланс (напр. fist_wand из двух блоков) не оценивался.
- В docs/history.md старое «Палки Некто» не трогал (запрещено).

## Git (выполняет пользователь)
```
git add resources/META-INF/mods.toml resources/assets/explosionwand/lang/ru_ru.json resources/assets/explosionwand/lang/en_us.json resources/data/explosionwand/recipes/arcane_helmet.json resources/data/explosionwand/recipes/arcane_chestplate.json resources/data/explosionwand/recipes/arcane_leggings.json resources/data/explosionwand/recipes/arcane_boots.json resources/data/explosionwand/recipes/portal_gun.json docs/jei.md reports/0013-2026-10-18-asset-engineer-jei-names-recipes.md
git commit -m "assets(items): JEI, названия Палочка, фикс рецептов" -m "- jei необязательная зависимость" -m "- рецепты брони и портал-гана: ровные шаблоны" -m "- Палка -> Палочка в lang"
```

## Следующий шаг
mod-engineer: JEI-плагин для списка трофеев; build-engineer/boss-engineer: исправить ошибки компиляции в Arena*.java.
