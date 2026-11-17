#!/usr/bin/env python3
"""Ассеты Перчатки Бесконечности и Парашюта без PIL: текстуры 16x16, модели (generated), рецепт парашюта, lang ru/en. Запуск повторяем."""
import json, sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent))
from gen_hell_assets import png, put, A, D

T = (0, 0, 0, 0)


def gauntlet():
    """Латная перчатка тыльной стороной, пальцы вверх: тёмная сталь, золото, шесть самоцветов."""
    pal = {'.': T, 'o': (14, 14, 20, 255), 'd': (36, 38, 46, 255), 'm': (60, 63, 74, 255), 'h': (96, 100, 114, 255),
           'G': (255, 226, 112, 255), 'g': (224, 170, 40, 255), 'k': (146, 98, 20, 255)}
    rows = [
        "...oo.oo.oo.oo..",
        "..ohdohdohdohdo.",
        "..omdomdomdomdo.",
        "..omdomdomdomdo.",
        "..oGgggggggggko.",
        "..oggggggggggko.",
        "..ohmmmmmmmmmdo.",
        ".oommmmmmmmmmdo.",
        "ommhmmmmmmmmmdo.",
        "ohmmmmmmmmmmmdo.",
        ".ommmmmmmmmmmdo.",
        "..oGgggggggggko.",
        "..ohmmmmmmmmdo..",
        "..ommmmmmmmmdo..",
        "..oddddddddddo..",
        "..okggggggggko..",
    ]
    px = [[pal[c] for c in r] for r in rows]
    # золотая накладка на тыльной стороне
    for y in (7, 8, 9):
        for x in range(5, 12):
            px[y][x] = pal['G'] if y == 7 else (pal['k'] if y == 9 else pal['g'])
    gem = {
        'rift': (165, 60, 235, 255), 'sleep': (40, 225, 205, 255), 'ash': (255, 130, 20, 255),
        'eclipse': (34, 44, 150, 255), 'sky': (130, 205, 255, 255), 'deep': (225, 30, 45, 255),
    }
    px[4][4], px[4][8], px[4][12] = gem['rift'], gem['sleep'], gem['ash']      # костяшки
    px[8][6], px[8][8], px[8][10] = gem['eclipse'], gem['sky'], gem['deep']    # тыльная сторона
    px[7][6] = px[9][6] = px[8][5] = px[8][7] = pal['G']                        # золотая искра вокруг Затмения
    return px


def parachute():
    """Купол в красно-белую полоску, стропы и рюкзачок."""
    px = [[T] * 16 for _ in range(16)]
    red, red_d, wht, wht_d = (214, 36, 40, 255), (150, 20, 28, 255), (244, 240, 232, 255), (186, 180, 176, 255)
    out = (60, 20, 24, 255)
    spans = {1: (5, 10), 2: (3, 12), 3: (2, 13), 4: (1, 14), 5: (1, 14)}
    for y, (a, b) in spans.items():
        for x in range(a, b + 1):
            r = ((x - 1) // 2) % 2 == 0
            if y == 5:
                c = red_d if r else wht_d
            else:
                c = red if r else wht
            px[y][x] = c
    for x in range(5, 11):
        px[0][x] = out
    for y, (a, b) in spans.items():
        if a > 0: px[y][a - 1] = out
        if b + 1 < 16: px[y][b + 1] = out
    px[0][4] = px[0][11] = px[1][4] = px[1][11] = out
    # скаллопы по нижней кромке
    for x in (2, 3, 6, 7, 10, 11):
        px[6][x] = T
    for x in (1, 4, 5, 8, 9, 12, 13, 14):
        px[6][x] = out
    rope = (226, 222, 214, 255)

    def line(x0, y0, x1, y1):
        n = max(abs(x1 - x0), abs(y1 - y0))
        for i in range(n + 1):
            px[y0 + (y1 - y0) * i // n][x0 + (x1 - x0) * i // n] = rope
    line(1, 7, 7, 11); line(14, 7, 8, 11); line(5, 7, 7, 11); line(10, 7, 8, 11)
    pack, pack_d, strap = (122, 78, 40, 255), (78, 48, 24, 255), (190, 150, 60, 255)
    for y in range(11, 15):
        for x in range(6, 10):
            px[y][x] = pack_d if (y == 14 or x == 9) else pack
    for x in range(6, 10):
        px[12][x] = strap
    px[10][7] = px[10][8] = rope
    return px


RU = {
    "item.explosionwand.infinity_gauntlet": "Перчатка Бесконечности",
    "item.explosionwand.infinity_gauntlet.desc1": "Shift+ПКМ: сменить камень. ПКМ: применить его силу",
    "item.explosionwand.infinity_gauntlet.desc2": "Шесть камней миров и Щелчок: половина врагов рядом рассыпается в пепел (60 с)",
    "explosionwand.gauntlet.current": "Камень: %s",
    "explosionwand.gauntlet.stone.rift": "Камень Разлома",
    "explosionwand.gauntlet.stone.sleep": "Камень Сна",
    "explosionwand.gauntlet.stone.ash": "Камень Пепла",
    "explosionwand.gauntlet.stone.eclipse": "Камень Затмения",
    "explosionwand.gauntlet.stone.sky": "Камень Неба",
    "explosionwand.gauntlet.stone.deep": "Камень Глубин",
    "explosionwand.gauntlet.stone.snap": "Щелчок",
    "explosionwand.jei.infinity_gauntlet": "Shift+ПКМ меняет камень, ПКМ применяет. Камень Разлома: телепорт в точку прицела до 64 блоков (1 с). Камень Сна: усыпляет мобов в радиусе 16 на 8 с (3 с). Камень Пепла: огненный луч на 40 блоков, 20 урона (2 с). Камень Затмения: день сменяется ночью и наоборот (10 с). Камень Неба: мобы в радиусе 16 взмывают и падают (4 с). Камень Глубин: блоки в радиусе 4 у точки прицела превращаются в золото, аметист, стекло и прочее (5 с). Щелчок: половина враждебных мобов в радиусе 64 рассыпается в пепел (60 с). Не крафтится, только креатив или /give.",
    "item.explosionwand.parachute": "Парашют",
    "item.explosionwand.parachute.desc1": "ПКМ в падении: раскрыть купол. Падаешь медленно и планируешь, куда смотришь, урона нет",
    "item.explosionwand.parachute.desc2": "На земле или в воде складывается. Shift: обрезать стропы. Прочность: 64 раскрытия",
    "explosionwand.parachute.air": "Раскрывать в воздухе",
    "explosionwand.jei.parachute": "Крафт: три шерсти (любых) сверху, нити по бокам во втором ряду и кожа внизу по центру. ПКМ в падении раскрывает купол: медленное падение и планирование туда, куда смотришь, урона от падения нет. На земле или в воде купол складывается, Shift обрезает стропы. Хватает на 64 раскрытия.",
}
EN = {
    "item.explosionwand.infinity_gauntlet": "Infinity Gauntlet",
    "item.explosionwand.infinity_gauntlet.desc1": "Shift + right-click: switch stone. Right-click: use its power",
    "item.explosionwand.infinity_gauntlet.desc2": "Six world stones and the Snap: half the hostile mobs nearby turn to ash (60 s)",
    "explosionwand.gauntlet.current": "Stone: %s",
    "explosionwand.gauntlet.stone.rift": "Rift Stone",
    "explosionwand.gauntlet.stone.sleep": "Sleep Stone",
    "explosionwand.gauntlet.stone.ash": "Ash Stone",
    "explosionwand.gauntlet.stone.eclipse": "Eclipse Stone",
    "explosionwand.gauntlet.stone.sky": "Sky Stone",
    "explosionwand.gauntlet.stone.deep": "Deep Stone",
    "explosionwand.gauntlet.stone.snap": "The Snap",
    "explosionwand.jei.infinity_gauntlet": "Shift + right-click switches the stone, right-click uses it. Rift Stone: teleport to the aimed point up to 64 blocks (1 s). Sleep Stone: puts mobs within 16 blocks to sleep for 8 s (3 s). Ash Stone: fire beam 40 blocks long, 20 damage (2 s). Eclipse Stone: day turns to night and back (10 s). Sky Stone: mobs within 16 blocks fly up and fall (4 s). Deep Stone: blocks within 4 of the aimed point turn into gold, amethyst, glass and more (5 s). The Snap: half of the hostile mobs within 64 blocks turn to ash (60 s). Not craftable: creative or /give only.",
    "item.explosionwand.parachute": "Parachute",
    "item.explosionwand.parachute.desc1": "Right-click while falling: open the canopy. Fall slowly and glide where you look, no fall damage",
    "item.explosionwand.parachute.desc2": "Folds on land or in water. Shift: cut the lines. Durability: 64 openings",
    "explosionwand.parachute.air": "Open it while falling",
    "explosionwand.jei.parachute": "Craft: three wool of any colour on top, string on both sides in the middle row, leather at the bottom centre. Right-click while falling to open the canopy: slow fall and gliding where you look, no fall damage. It folds on land or in water, Shift cuts the lines. Lasts 64 openings.",
}

RECIPE = {
    "type": "minecraft:crafting_shaped",
    "pattern": ["WWW", "S S", " L "],
    "key": {"W": {"tag": "minecraft:wool"}, "S": {"item": "minecraft:string"}, "L": {"item": "minecraft:leather"}},
    "result": {"item": "explosionwand:parachute", "count": 1},
}

if __name__ == "__main__":
    for name, px in (("infinity_gauntlet", gauntlet()), ("parachute", parachute())):
        png(A / "textures" / "item" / f"{name}.png", px, alpha=True)
        put(A / "models" / "item" / f"{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"explosionwand:item/{name}"}})
    put(D / "explosionwand" / "recipes" / "parachute.json", RECIPE)
    for f, d in (("ru_ru.json", RU), ("en_us.json", EN)):
        p = A / "lang" / f
        j = json.loads(p.read_text(encoding="utf-8"))
        j.update(d)
        p.write_text(json.dumps(j, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print("ok")
