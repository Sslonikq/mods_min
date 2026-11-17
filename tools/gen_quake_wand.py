#!/usr/bin/env python3
"""Ассеты Палочки Землетрясения (3.5.0) без PIL: текстура 16x16, модель, тексты lang (ru/en). Рецепта нет. Запуск повторяем."""
import json, sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent))
from gen_hell_assets import png, put, A

T = (0, 0, 0, 0)


def texture():
    """Древко из тёмного дерева с каменной оплёткой по диагонали, на конце расколотый валун, по расколу светится магма."""
    px = [[T] * 16 for _ in range(16)]
    wood_d, wood_m, wood_h = (52, 34, 22, 255), (86, 58, 36, 255), (120, 86, 54, 255)
    band = (112, 108, 100, 255)
    for i in range(9):                       # древко: (2+i, 13-i)
        x, y = 2 + i, 13 - i
        px[y][x] = wood_m
        px[y][x + 1] = wood_d
        if y + 1 < 16: px[y + 1][x] = wood_d
        if i % 4 == 1: px[y][x] = wood_h
        if i in (2, 6): px[y][x] = px[y][x + 1] = band   # каменные кольца
    px[14][1] = px[14][2] = px[13][1] = wood_d           # пятка
    px[15][0] = band
    # валун на конце: неровный круг с центром (12, 4), радиус ~3.3
    s_d, s_m, s_h = (70, 66, 62, 255), (104, 98, 90, 255), (142, 136, 124, 255)
    cx, cy = 12, 4
    for y in range(16):
        for x in range(16):
            dx, dy = x - cx, y - cy
            d2 = dx * dx + dy * dy
            if d2 <= 11 or (d2 <= 13 and (x + y) % 3):
                px[y][x] = s_h if dx + dy <= -2 else (s_d if dx + dy >= 2 else s_m)
    # раскол с магмой: зигзаг с верхнего правого края к низу-слева валуна
    glow, hot, core = (210, 70, 20, 255), (255, 140, 40, 255), (255, 226, 120, 255)
    crack = [(14, 1), (13, 2), (13, 3), (12, 4), (11, 5), (11, 6), (10, 7)]
    for i, (x, y) in enumerate(crack):
        px[y][x] = core if i in (2, 3) else hot
    for (x, y) in [(15, 1), (14, 2), (12, 3), (13, 4), (12, 5), (10, 6), (9, 7)]:
        if px[y][x] != T: px[y][x] = glow
    # искры, отлетающие от раскола
    for (x, y, c) in [(15, 0, hot), (8, 2, glow), (15, 6, glow)]:
        px[y][x] = c
    return px


RU = {
    "item.explosionwand.earthquake_wand": "Палочка Землетрясения",
    "item.explosionwand.earthquake_wand.desc1": "ПКМ: землетрясение в точке прицела. Земля идёт волнами, трещины с магмой, гейзеры",
    "item.explosionwand.earthquake_wand.desc2": "Shift+ПКМ: вокруг себя. Через 7 с трещины захлопываются. Перезарядка 15 с",
}
EN = {
    "item.explosionwand.earthquake_wand": "Earthquake Wand",
    "item.explosionwand.earthquake_wand.desc1": "Right-click: earthquake where you aim. The ground ripples, cracks open with magma, geysers erupt",
    "item.explosionwand.earthquake_wand.desc2": "Shift + right-click: around yourself. After 7 s the cracks slam shut. 15 s cooldown",
}

if __name__ == "__main__":
    png(A / "textures" / "item" / "earthquake_wand.png", texture(), alpha=True)
    put(A / "models" / "item" / "earthquake_wand.json", {"parent": "minecraft:item/handheld", "textures": {"layer0": "explosionwand:item/earthquake_wand"}})
    for f, d in (("ru_ru.json", RU), ("en_us.json", EN)):
        p = A / "lang" / f
        j = json.loads(p.read_text(encoding="utf-8"))
        j.update(d)
        p.write_text(json.dumps(j, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print("ok")
