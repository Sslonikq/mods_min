#!/usr/bin/env python3
"""Проверка строковых ID (блоки, звуки, частицы, эффекты, предметы, сущности) по реестру Minecraft 1.20.1."""
import json, re, sys
from pathlib import Path

CHECKS = [('sound_event', r'Fx\.sound\([^;]*?"([a-z0-9_.]+)"'), ('particle_type', r'Fx\.particle\("([a-z0-9_]+)"\)'),
          ('block', r'(?:Fx\.state|Fx\.block|Disp\.block)\("([a-z0-9_]+)"\)'), ('block', r'BLOCKS\[[^\]]+\]\s*=\s*(?:BLOCKS\[[^\]]+\]\s*=\s*)*"([a-z0-9_]+)"'),
          ('mob_effect', r'Fx\.effect\("([a-z0-9_]+)"\)'), ('entity_type', r'summon minecraft:([a-z_]+)'), ('entity_type', r'Minions\.spawn\(level, "([a-z_]+)"'),
          ('item', r'"(?:\d+ )?minecraft:([a-z_]+)"'), ('item', r'id:\\"minecraft:([a-z_]+)\\"'), ('block', r'setblock [^"]*?minecraft:([a-z_]+)'), ('block', r'Dungeons\.state\("(?:minecraft:)?([a-z0-9_]+)')]
# склейки строк, а не настоящие ID
SKIP = {('Minions.java', 'leather_'), ('Possess.java', 'ravager'), ('Possess.java', 'goat')}


def check(root: Path):
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    import lookup
    lookup.ensure()
    reg = {k: set(v) for k, v in json.load(open(lookup.MAPS / "reg.json", encoding="utf-8")).items() if isinstance(v, list)}
    src = {f: f.read_text(encoding="utf-8") for f in (root / "src").rglob("*.java")}
    mod = next(s for f, s in src.items() if f.name == "ExplosionWandMod.java")
    mod_items = set(re.findall(r'ITEMS\.register\("([a-z_]+)"', mod))
    bad = []
    for f, s in sorted(src.items()):
        for kind, rx in CHECKS:
            for m in re.finditer(rx, s):
                v = m.group(1)
                if (f.name, v) in SKIP: continue
                if kind == 'item' and v in reg['entity_type'] and v not in reg['item']: continue
                if v not in reg[kind]: bad.append(f"{f.name}: {kind} '{v}' нет в реестре 1.20.1")
        for m in re.finditer(r'explosionwand:([a-z_]+)', s):
            ctx = s[max(0, m.start() - 60):m.start()]
            if m.group(1) not in mod_items and not re.search(r'bossbar|BAR', ctx) and m.group(1) not in ('arcane', 'magic'):
                bad.append(f"{f.name}: предмет мода '{m.group(1)}' не зарегистрирован")
    return bad


if __name__ == "__main__":
    b = check(Path(__file__).resolve().parent.parent)
    print("\n".join(b) or "ok"); sys.exit(1 if b else 0)
