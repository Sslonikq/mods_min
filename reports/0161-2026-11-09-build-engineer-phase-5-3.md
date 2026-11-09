# 0161 · Сборка фазы 5.3: gen_citadel --check в build.py, проверка SRG и заглушек, устойчивость к кодировке

- **Дата:** 2026-11-09
- **Агент:** build-engineer
- **Версия мода:** 3.4.0
- **Статус:** done
- **Связанные отчёты:** 0159, 0160

## Что сделано
- `tools/build.py`: новый шаг `[3c] check_citadel` (запуск `tools/gen_citadel.py --check`, после ID и `gen_dungeons --check`; код возврата ≠ 0 роняет сборку, в лог идёт последняя строка вывода).
- Устойчивость к кодировке Windows: `run()` читает вывод дочерних процессов как UTF-8 с `errors="replace"` и задаёт детям `PYTHONIOENCODING=utf-8`; в `__main__` stdout/stderr переключены на `errors="replace"`. До этого `python -X utf8 tools/build.py` падал на декодировании javap, а после первой правки - на печати строки теста в cp1251; теперь обе формы запуска работают (с `-X utf8` и без).

## Проверка
- `python tools/build.py` (без `-X utf8`): exit 0. 214 классов; SRG: нет в joined.tsrg 0; пути классов 0 ошибок; `[2c] linkcheck`: найдено 8 jar Forge, проверено 185 членов, 563 класса, не найдено 0, отсутствуют 0 (jar на этой машине есть, проверка реальная); ID 0; `[3b]` gen_dungeons 10 частей 0 проблем; `[3c]` gen_citadel 6 частей, 1 484 772 блока, 0 проблем; все тесты OK (28 OK, 0 FAIL, включая CitadelTest). Вывод консоли в моей оболочке показывается кракозябрами (кодировка терминала), на содержимое это не влияет.
- `python -X utf8 tools/build.py`: после правки тоже exit 0.
- `python tools/linkcheck.py`: расхождений 0 (см. выше).

### Новые SRG из 0160 (`lookup.py --srg`)
| SRG | Что это | Заглушка |
|---|---|---|
| `m_62218_` | `ChunkGenerator.getBiomeSource()` -> `BiomeSource` | `ChunkGenerator.m_62218_()` верно |
| `m_203407_` | `BiomeResolver.getNoiseBiome(int,int,int,Climate.Sampler)` -> `Holder<Biome>` | в интерфейсе `BiomeResolver`; `BiomeSource implements BiomeResolver` (как в настоящем) |
| `m_203565_` | `Holder.is(ResourceKey<T>)` -> boolean | интерфейс `Holder<T>` верно |
| `m_224579_` | `RandomState.sampler()` -> `Climate.Sampler` | `RandomState.m_224579_()` верно |
| `f_256952_` | `Registries.BIOME` (`ResourceKey<Registry<Biome>>`) | static final поле верно |

Смысл совпал везде (yarn-имена дают те же методы). Заглушки минимальны: `Biome`, `Climate`/`Sampler`, `BiomeSource` пустые, в остальных только по одному члену; типы, static/interface совпадают, `linkcheck` против настоящих jar подтвердил отсутствие `NoSuchMethodError`.

### Jar и build/
- `build/` пересоздаётся целиком в `compile_all` (rmtree): там только артефакты (классы, args, тесты и превью `gen_citadel --preview`, воспроизводимые командой). Тесты берут шаблоны из `resources/`, не из `build/`, так что ничего нужного не теряется.
- В `dist/explosionwand-1.20.1-3.4.0.jar` - 3 063 772 байт (~2,92 МБ; 3.3.0 был 3 043 391, прирост мал за счёт сжатия) попали `citadel_p1..p6.nbt` (0,64-0,69 МБ каждый, сжатые 147-178 КБ), `eclipse_seal_shard` и `throne_seal` (модель и текстура), рецепт осколка, `mods.toml` с `version="3.4.0"`. `mech_test.nbt` исключён, как и раньше. Размер приемлем.
- Старый `dist/explosionwand-1.20.1-3.3.0.jar` лежит рядом (не трогал; не в git).

## Что покрыто тестами / только в игре
- Тестами: потоковый разбор .nbt, метки и блоки под ними, часы, поиск места, регистрация/lang/версия (`CitadelTest`); проходимость и печати - `gen_citadel --check`.
- Только в игре: время/память вставки 1,5 млн блоков и расчистки, лаги на тик, Базальтовые пики на практике, поведение механик (список в 0160).

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `tools/build.py` | шаг `[3c]`, UTF-8 с replace для дочерних процессов, безопасный stdout |

## Следующий шаг
1. code-reviewer: ревью 0159-0161.
2. Пользователь: проверка в игре по списку 0160.
3. boss-engineer (5.4): мобы Цитадели.

## Git (выполняет пользователь)
```
git add tools/build.py reports/0161-2026-11-09-build-engineer-phase-5-3.md
git commit -m "build(build): gen_citadel --check в build.py, устойчивая кодировка" -m "- шаг [3c]: проверка шаблонов Цитадели" -m "- вывод javap/тестов читается как UTF-8 с заменой, stdout не падает на cp1251"
```
