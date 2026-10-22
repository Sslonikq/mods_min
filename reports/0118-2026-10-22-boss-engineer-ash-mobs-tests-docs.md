# 0118 · Мобы Святилища: тесты, lang, 2.17.0, документация

- **Дата:** 2026-10-22
- **Агент:** boss-engineer
- **Версия мода:** 2.17.0
- **Статус:** done
- **Связанные отчёты:** 0116, 0117

## Задача
Тест, тексты, версия, docs.

## Что сделано
- `tests/AshMobsTest.java` (новый, подхватывается `build.py`): урон на Сложной, телеграфы, щиты, стая, волна, лечение, червь, Хранитель, огонёк, облик (части, конечность, не под полом), каркас, метки шаблонов, lang ru/en (в т.ч. ключи из кода).
- Правки `DungeonTest` (метки арены, волна, элита) и `BossAshTest` (приспешники Феникса).
- lang ru/en: имена 9 мобов и 10 сообщений; ключи заглушек `explosionwand.ash.{spark,walker,guard}` удалены.
- `mods.toml` 2.17.0 (ниже уже описанной 2.18.0: версия зарезервирована за 4.9 по ROADMAP и просьбе пользователя), `docs/history.md`, `docs/dungeons.md` (раздел мобов), `docs/world.md`, `ROADMAP.md`.

## Проверка
`python tools/build.py`: SRG 249/250, неизвестных 0, проблем ID 0, шаблоны (`gen_dungeons --check`) свежие, все тесты OK (AshMobsTest, BossAshTest, DungeonTest и др.), jar `dist/explosionwand-1.20.1-2.17.0.jar`.

## Проверить в игре
См. 0116 и 0117.

## Git (выполняет пользователь)
```
git add tests/AshMobsTest.java tests/DungeonTest.java tests/BossAshTest.java resources/assets/explosionwand/lang/ru_ru.json resources/assets/explosionwand/lang/en_us.json resources/META-INF/mods.toml docs/history.md docs/dungeons.md docs/world.md ROADMAP.md reports/0118-2026-10-22-boss-engineer-ash-mobs-tests-docs.md
git commit -m "feat(boss): мобы Святилища: тесты, тексты, 2.17.0" -m "- AshMobsTest, lang ru/en" -m "- история и docs"
```

## Следующий шаг
`code-reviewer`: ревью 4.9 (изменения общего `RiftMobs`, поводок приспешников Феникса, червь без лавы); затем 4.12.
