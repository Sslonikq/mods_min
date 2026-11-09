#!/usr/bin/env python3
"""Проверка JSON датапака Мира Ада: валидность, типы (реестры 1.20.1), ссылки на блоки/файлы/шумы/частицы/звуки.
Блоки explosionwand: берутся из таблицы docs/hell_blocks.md и ExplosionWandMod.java (не из реестров игры).
Запуск: python tools/check_worldgen.py"""
import json, re, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(Path(__file__).resolve().parent))
import lookup
lookup.ensure()
reg = {k: set(v) for k, v in json.load(open(lookup.MAPS / "reg.json", encoding="utf-8")).items() if isinstance(v, list)}
DATA = ROOT / "resources" / "data" / "explosionwand"

mod_blocks = set(re.findall(r"^\| `([a-z0-9_]+)` \|", (ROOT / "docs" / "hell_blocks.md").read_text(encoding="utf-8"), re.M))
mod_blocks |= {"seal_stone", "phoenix_embers"}
TYPE_REGS = ["worldgen/density_function_type", "worldgen/feature", "worldgen/placement_modifier_type", "worldgen/trunk_placer_type", "worldgen/foliage_placer_type",
             "worldgen/tree_decorator_type", "worldgen/block_state_provider_type", "worldgen/feature_size_type", "block_predicate_type", "int_provider_type",
             "height_provider_type", "worldgen/material_condition", "worldgen/material_rule", "worldgen/biome_source", "worldgen/chunk_generator", "rule_test", "worldgen/carver"]
types = set().union(*[reg[r] for r in TYPE_REGS]) | {"minecraft:" + x for x in ()}

files = {}
for sub in ("dimension", "dimension_type", "worldgen/biome", "worldgen/configured_carver", "worldgen/configured_feature", "worldgen/placed_feature", "worldgen/noise_settings", "worldgen/density_function"):
    for f in (DATA / sub).rglob("*.json"):
        files.setdefault(sub, set()).add(str(f.relative_to(DATA / sub).with_suffix("")).replace("\\", "/"))

bad = []
def err(f, m): bad.append(f"{f.relative_to(ROOT)}: {m}")

def block_ok(v):
    ns, _, n = v.partition(":")
    return n in mod_blocks if ns == "explosionwand" else n in reg["block"]

def ref_ok(v, sub):
    ns, _, n = v.partition(":")
    return n in files.get(sub, set()) if ns == "explosionwand" else True

def walk(f, o, key=""):
    if isinstance(o, dict):
        for k, v in o.items():
            if k == "options": continue  # частица: проверяется отдельно
            if k == "type" and isinstance(v, str):
                ns, _, n = v.partition(":")
                if ns == "minecraft" and n not in types: err(f, f"тип '{v}' неизвестен")
            elif k == "Name" and isinstance(v, str):
                if not (block_ok(v) or v.partition(":")[2] in reg["fluid"]): err(f, f"блок '{v}' не найден")
            elif k in ("block", "blocks", "valid_blocks") and not isinstance(v, dict):
                for b in ([v] if isinstance(v, str) else v):
                    if isinstance(b, str) and not b.startswith("#") and not block_ok(b): err(f, f"блок '{b}' не найден")
            elif k == "noise" and isinstance(v, str):
                if v.partition(":")[2] not in reg["worldgen/noise"]: err(f, f"шум '{v}' неизвестен")
            elif k == "sound" and isinstance(v, str):
                if v.partition(":")[2] not in reg["sound_event"]: err(f, f"звук '{v}' неизвестен")
            elif k == "feature" and isinstance(v, str):
                if not ref_ok(v, "worldgen/configured_feature") and not ref_ok(v, "worldgen/placed_feature"): err(f, f"фича '{v}' нет файла")
            elif k == "settings" and isinstance(v, str):
                if not ref_ok(v, "worldgen/noise_settings"): err(f, f"noise_settings '{v}' нет файла")
            elif k == "biome" and isinstance(v, str):
                if not ref_ok(v, "worldgen/biome"): err(f, f"биом '{v}' нет файла")
            elif k == "biome_is":
                for b in v:
                    if not ref_ok(b, "worldgen/biome"): err(f, f"биом '{b}' нет файла")
            walk(f, v, k)
    elif isinstance(o, list):
        for x in o: walk(f, x, key)
    elif isinstance(o, str) and key in ("vegetation", "continents", "erosion", "ridges", "final_density", "initial_density_without_jaggedness", "argument", "argument1", "argument2", "input"):
        if o.startswith("explosionwand:") and not ref_ok(o, "worldgen/density_function"): err(f, f"density function '{o}' нет файла")

n = 0
for f in sorted(DATA.rglob("*.json")):
    rel = f.relative_to(DATA).as_posix()
    if not (rel.startswith(("dimension/hell", "dimension_type/hell", "tags/blocks/hell")) or "hell" in rel and rel.startswith("worldgen/")): continue
    n += 1
    try: o = json.loads(f.read_text(encoding="utf-8"))
    except Exception as e: err(f, f"невалидный JSON: {e}"); continue
    walk(f, o)
    if rel.startswith("worldgen/biome/"):
        fe = o["features"]
        if len(fe) != 11: err(f, "features: нужно 11 шагов")
        for step in fe:
            for p in step:
                if not ref_ok(p, "worldgen/placed_feature"): err(f, f"placed_feature '{p}' нет файла")
        for st, lst in o["carvers"].items():
            if st not in ("air", "liquid"): err(f, f"carvers: шаг '{st}'")
            for c in lst:
                if not ref_ok(c, "worldgen/configured_carver"): err(f, f"configured_carver '{c}' нет файла")
        if not o["carvers"].get("air"): err(f, "в биоме Ада нет карверов (air)")
        for k in ("effects", "temperature", "downfall", "has_precipitation", "spawners", "spawn_costs"):
            if k not in o: err(f, f"нет поля {k}")
        pt = o["effects"]["particle"]["options"]["type"].partition(":")[2]
        if pt not in reg["particle_type"]: err(f, f"частица {pt}")
    if rel.startswith("worldgen/configured_carver/"):
        c = o["config"]
        if o["type"] not in ("minecraft:cave", "minecraft:canyon", "minecraft:nether_cave"): err(f, f"тип карвера {o['type']}")
        for k in ("probability", "y", "lava_level", "replaceable"):
            if k not in c: err(f, f"config без {k}")
        t = c["replaceable"]
        if not t.startswith("#explosionwand:") or not (DATA / "tags" / "blocks" / (t.partition(":")[2] + ".json")).exists(): err(f, f"тег замещаемых блоков '{t}' не найден")
    if rel == "tags/blocks/hell_carver_replaceables.json":
        for b in o["values"]:
            if not block_ok(b): err(f, f"блок '{b}' не найден")
    if rel.startswith("worldgen/placed_feature/"):
        if not ref_ok(o["feature"], "worldgen/configured_feature"): err(f, "нет configured_feature")
    if rel.startswith("worldgen/noise_settings/"):
        nz = o["noise"]
        if nz["height"] % 16 or nz["min_y"] % 16: err(f, "noise.min_y/height не кратны 16")
        for k in ("barrier", "continents", "depth", "erosion", "final_density", "fluid_level_floodedness", "fluid_level_spread", "initial_density_without_jaggedness",
                  "lava", "ridges", "temperature", "vegetation", "vein_gap", "vein_ridged", "vein_toggle"):
            if k not in o["noise_router"]: err(f, f"noise_router без {k}")
    if rel == "dimension/hell.json":
        for b in o["generator"]["biome_source"]["biomes"]:
            if not ref_ok(b["biome"], "worldgen/biome"): err(f, f"нет биома {b['biome']}")
print(f"проверено файлов: {n}")
print("\n".join(bad) or "ok")
sys.exit(1 if bad else 0)
