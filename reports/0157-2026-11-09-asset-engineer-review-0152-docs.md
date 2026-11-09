# 0157 · Остатки ревью 0152: history 3.3.0, лишний ключ JEI

- **Дата:** 2026-11-09
- **Агент:** asset-engineer
- **Версия мода:** 3.3.0
- **Статус:** done
- **Связанные отчёты:** 0152, 0149, 0150, 0153, 0154, 0155

## Задача
Закрыть minor 2 (history 3.3.0) и nit 4 (ключ `explosionwand.jei.eclipse_crystal_block`).

## Что сделано
- `docs/history.md`, запись 3.3.0: убрана пометка «ресурсная часть»; добавлены Копатель (добыча в инвентарь), площадка портала, лог сбоев `Later.tick`, числа инструментов (1800, 8.5, уровень 3, чар 12, +25% на блоках Ада, меч 7.5, топор 9.5), итог по пещерам (cave y54..160, canyon y64..110).
- JEI: в `ExplosionWandJei.java` для `ECLIPSE_CRYSTAL_BLOCK` вызова `info(...)` нет, страница требует кода плагина (зона mod-engineer). Ключ удалён из обоих lang (ru и en), по 456 ключей, JSON валиден.

## Изменённые файлы
| Файл | Что изменилось |
|---|---|
| `docs/history.md` | запись 3.3.0 дополнена |
| `resources/assets/explosionwand/lang/ru_ru.json`, `en_us.json` | удалён `explosionwand.jei.eclipse_crystal_block` |

## Проверка
Lang: оба JSON парсятся, 456/456. `build.py` не запускал (Java другие агенты правят параллельно, моя правка только docs и lang).

## Проверить в игре
Ничего нового.

## Решения и почему
Страницу JEI для блока кристалла не добавлял: нужен код плагина. Если нужна, mod-engineer добавляет `info(reg, new ItemStack(<блок>), "eclipse_crystal_block")` и возвращает ключ в оба lang (текст: «Крафтится из 4 Осколков затмения (2x2). Светится.» / «Crafted from 4 Eclipse Shards (2x2). Glows.»).

## Git (выполняет пользователь)
```
git add docs/history.md resources/assets/explosionwand/lang/ru_ru.json resources/assets/explosionwand/lang/en_us.json reports/0157-2026-11-09-asset-engineer-review-0152-docs.md
git commit -m "docs(assets): история 3.3.0 дополнена, убран лишний ключ JEI" -m "- Копатель, площадка портала, лог Later, числа инструментов, пещеры" -m "- удалён неиспользуемый jei.eclipse_crystal_block из ru и en"
```

## Следующий шаг
mod-engineer по желанию: JEI-страница блока кристалла (текст выше).
