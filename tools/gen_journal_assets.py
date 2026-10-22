#!/usr/bin/env python3
"""Ассеты Дневника Архимага (4.13) без PIL: текстура 16x16, модель предмета, рецепт (книга + аметистовый осколок)
и тексты lang (ru/en). Запуск повторяем: lang обновляется ключами journal."""
import json, sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent))
from gen_hell_assets import png, put, A, D


def book():
    px = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
    outl, cover, hi, lo = (38, 20, 60, 255), (96, 52, 140, 255), (132, 84, 178, 255), (66, 34, 100, 255)
    page, page_lo, gold, gem = (238, 226, 190, 255), (200, 186, 146, 255), (240, 196, 72, 255), (150, 240, 255, 255)
    for y in range(1, 15):
        for x in range(2, 14):
            px[y][x] = outl if y in (1, 14) or x in (2, 13) else cover
    for y in range(2, 14):
        px[y][3] = lo
        for x in range(4, 13):
            px[y][x] = hi if y == 2 else cover
    for y in range(2, 14):       # обрез страниц справа
        px[y][12] = page
        px[y][11] = page_lo if y % 2 else page
    for x in range(3, 13):
        px[13][x] = lo if x < 11 else page_lo
    # золотая звезда-печать на обложке
    cx, cy = 7, 7
    for (dx, dy) in [(0, -3), (0, -2), (0, 2), (0, 3), (-3, 0), (-2, 0), (2, 0), (3, 0), (-1, -1), (1, -1), (-1, 1), (1, 1)]:
        px[cy + dy][cx + dx] = gold
    for (dx, dy) in [(0, 0), (0, -1), (0, 1), (-1, 0), (1, 0)]:
        px[cy + dy][cx + dx] = gem
    return px


RU = {
    "item.explosionwand.archmage_journal": "Дневник Архимага",
    "item.explosionwand.archmage_journal.desc": "ПКМ - читать записи. Новые страницы открываются по мере побед и находок",
    "explosionwand.jei.archmage_journal": "Выдаётся один раз при первом входе в мир. Если потерян - крафт: книга + аметистовый осколок. Страницы открываются по мере побед над стражами Земли и по прогрессу (Ключ Ада, портал).",
    "explosionwand.journal.given": "В вещах нашлась старая книга: Дневник Архимага. ПКМ - читать.",
    "explosionwand.journal.title": "=== Дневник Архимага ===",
    "explosionwand.journal.more": "Остальные страницы пока пусты или слиплись. Они откроются позже.",
    "explosionwand.journal.p0": "Запись последняя. Лестница миров сломана: Земля, Ад, Энд, Рай, и мы сами обрезали ступени. Печать держится на стражах, но стражи одичали, и Печать трескается. Я не успею. Кто бы ты ни был, ты должен подняться раньше, чем рухнет Печать, и раньше того, что ждёт наверху.",
    "explosionwand.journal.p1": "Страж первый: Сердце Разлома, он спит в Разломе под землёй. Найти его легко, если знать как: сделай Осколок Печати: Разлом (аметист, око Эндера, золото). В руке он тянется к ближайшему Разлому, рядом дрожит и звенит, а дверь отпирает сам. Разломы далеко друг от друга, так что готовься к дороге. Стен данжей не ломай: они нам не подвластны.",
    "explosionwand.journal.p2": "Сердце Разлома пало. Из его сундука возьми Ядро Разлома, его посох и новый осколок: Печать: Сон. Он тянется к Городу Спящих и открывает ворота города. Город укутан сонным туманом, а храм заперт Печатью Храма, которую хранит склеп под одним из домов. Читай фрески на площадях: лунный знак подскажет дом.",
    "explosionwand.journal.p3": "Монолит Сна молчит. Он оставил Недремлющее Око и Осколок Печати: Пепел. Тот ведёт к Пепельному святилищу: башне, над которой вечно дым. Сперва вверх, к колоколу, потом вниз; четыре жаровни, огненная переправа, Хранитель Углей. Внизу спит пламя, которому не следовало просыпаться.",
    "explosionwand.journal.p4": "Три трофея стражей: Ядро Разлома, Недремлющее Око и Перо Феникса. Слей их в Ключ Ада (бесформенный крафт). Алтарь строй из Печатного камня: рамка 5x5 стоймя, проём 3x3 пустой; в центр верхней грани поставь Угли Феникса. ПКМ ключом по Углям, и проём загорится.",
    "explosionwand.journal.p5": "Портал горит, и теперь он горит всегда: ключ остался в алтаре, ходи туда и обратно сколько хочешь. Но Ад ещё не достроен: там пока только площадка и надпись. Лестница идёт дальше вверх, и я не знаю, что ты найдёшь на следующей ступени. Береги Печать. Береги себя.",
}
EN = {
    "item.explosionwand.archmage_journal": "Archmage's Journal",
    "item.explosionwand.archmage_journal.desc": "Right-click to read. New pages open as you win and find things",
    "explosionwand.jei.archmage_journal": "Given once when you first enter a world. If lost, craft it: book + amethyst shard. Pages open as you defeat the guardians of the Overworld and progress (Hell Key, portal).",
    "explosionwand.journal.given": "You find an old book among your things: the Archmage's Journal. Right-click to read.",
    "explosionwand.journal.title": "=== Archmage's Journal ===",
    "explosionwand.journal.more": "The remaining pages are blank or stuck together for now. They will open later.",
    "explosionwand.journal.p0": "My last entry. The stair of worlds is broken: Overworld, Hell, End, Heaven, and we cut the steps ourselves. The Seal rests on the guardians, but the guardians have run wild and the Seal is cracking. I will not make it. Whoever you are, you must climb before the Seal falls, and before whatever waits above.",
    "explosionwand.journal.p1": "First guardian: the Rift Heart, asleep in a Rift underground. Finding it is easy if you know how: make a Seal Shard: Rift (amethyst, ender eye, gold). In hand it pulls toward the nearest Rift, trembles and rings close by, and opens the door itself. Rifts lie far apart, so prepare for a journey. Do not try to break dungeon walls: they will not yield to us.",
    "explosionwand.journal.p2": "The Rift Heart has fallen. From its chest take the Rift Core, its staff and a new shard: Seal: Sleep. It pulls toward the City of the Sleepers and opens the city gates. The city is wrapped in a drowsy fog, and the temple is locked by the Temple Seal kept in a crypt beneath one of the houses. Read the frescoes in the squares: the moon sign tells which house.",
    "explosionwand.journal.p3": "The Dream Monolith is silent. It left the Unsleeping Eye and the Seal Shard: Ash. That leads to the Ashen Sanctuary: a tower under never-ending smoke. Up to the bell first, then down; four braziers, a fiery crossing, the Keeper of Embers. Below sleeps a flame that should never have woken.",
    "explosionwand.journal.p4": "Three guardian trophies: the Rift Core, the Unsleeping Eye and the Phoenix Feather. Fuse them into the Hell Key (shapeless craft). Build the altar from Seal Stone: a 5x5 frame standing upright with an empty 3x3 opening; put Phoenix Embers in the centre of the top edge. Right-click the Embers with the key and the opening ignites.",
    "explosionwand.journal.p5": "The portal burns, and now it burns forever: the key stays in the altar, go there and back as you please. But Hell is not finished yet: only a platform and a sign so far. The stair climbs on, and I do not know what you will find on the next step. Guard the Seal. Guard yourself.",
}


if __name__ == "__main__":
    png(A / "textures" / "item" / "archmage_journal.png", book(), alpha=True)
    put(A / "models" / "item" / "archmage_journal.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "explosionwand:item/archmage_journal"}})
    put(D / "explosionwand" / "recipes" / "archmage_journal.json", {"type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [{"item": "minecraft:book"}, {"item": "minecraft:amethyst_shard"}], "result": {"item": "explosionwand:archmage_journal", "count": 1}})
    for f, d in (("ru_ru.json", RU), ("en_us.json", EN)):
        p = A / "lang" / f
        j = json.loads(p.read_text(encoding="utf-8"))
        j.update(d)
        p.write_text(json.dumps(j, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print("ok")
