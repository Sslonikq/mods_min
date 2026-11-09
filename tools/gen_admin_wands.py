#!/usr/bin/env python3
"""Ассеты 3.2.0 без PIL: текстуры и модели тестовых админ-палочек (Молнии и Смерти) и тексты lang (ru/en). Рецептов нет. Запуск повторяем."""
import json, sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent))
from gen_hell_assets import png, put, A


def wand(spark, glow, core):
    """Тёмная палочка по диагонали (снизу-слева вверх-вправо) с искрой на конце."""
    px = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
    dark, mid, hi = (28, 24, 38, 255), (54, 46, 70, 255), (92, 80, 118, 255)
    for i in range(11):                      # древко: (2+i, 13-i), толщина 2
        x, y = 2 + i, 13 - i
        px[y][x] = mid
        px[y][x + 1] = dark
        if y + 1 < 16: px[y + 1][x] = dark
        if i % 3 == 1: px[y][x] = hi
    px[14][1] = px[14][2] = px[13][1] = dark  # рукоять
    # искра на конце (13,2)
    cx, cy = 13, 2
    for (dx, dy) in [(-2, 0), (-1, 0), (1, 0), (2, 0), (0, -2), (0, -1), (0, 1), (0, 2)]:
        x, y = cx + dx, cy + dy
        if 0 <= x < 16 and 0 <= y < 16: px[y][x] = spark if abs(dx) + abs(dy) == 2 else glow
    for (dx, dy) in [(-1, -1), (1, -1), (-1, 1), (1, 1)]:
        x, y = cx + dx, cy + dy
        if 0 <= x < 16 and 0 <= y < 16: px[y][x] = spark
    px[cy][cx] = core
    return px


RU = {
    "item.explosionwand.lightning_wand": "Палочка Молнии",
    "item.explosionwand.lightning_wand.desc": "Только для тестов. ПКМ - луч молнии, 1000 урона всем на линии",
    "explosionwand.jei.lightning_wand": "Не крафтится. Выдаётся только командой (/give) или берётся в креативе. Только для тестов.",
    "item.explosionwand.death_wand": "Палочка Смерти",
    "item.explosionwand.death_wand.desc": "Только для тестов. ПКМ - мгновенная смерть цели в прицеле, включая боссов",
    "explosionwand.jei.death_wand": "Не крафтится. Выдаётся только командой (/give) или берётся в креативе. Только для тестов.",
}
EN = {
    "item.explosionwand.lightning_wand": "Lightning Wand",
    "item.explosionwand.lightning_wand.desc": "For testing only. Right-click: lightning beam, 1000 damage to everything in line",
    "explosionwand.jei.lightning_wand": "Cannot be crafted. Given only by command (/give) or taken from creative. For testing only.",
    "item.explosionwand.death_wand": "Death Wand",
    "item.explosionwand.death_wand.desc": "For testing only. Right-click: instantly kills the target in your sights, bosses included",
    "explosionwand.jei.death_wand": "Cannot be crafted. Given only by command (/give) or taken from creative. For testing only.",
}

if __name__ == "__main__":
    png(A / "textures" / "item" / "lightning_wand.png", wand((90, 170, 255, 255), (190, 230, 255, 255), (255, 255, 255, 255)), alpha=True)
    png(A / "textures" / "item" / "death_wand.png", wand((30, 190, 70, 255), (120, 240, 120, 255), (225, 255, 225, 255)), alpha=True)
    for n in ("lightning_wand", "death_wand"):
        put(A / "models" / "item" / (n + ".json"), {"parent": "minecraft:item/handheld", "textures": {"layer0": "explosionwand:item/" + n}})
    for f, d in (("ru_ru.json", RU), ("en_us.json", EN)):
        p = A / "lang" / f
        j = json.loads(p.read_text(encoding="utf-8"))
        j.update(d)
        p.write_text(json.dumps(j, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print("ok")
