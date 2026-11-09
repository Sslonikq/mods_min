#!/usr/bin/env python3
"""Проверка JSON датапака Мира Ада: валидность, типы (реестры 1.20.1), ссылки на блоки/файлы/шумы/частицы/звуки.
Блоки explosionwand: берутся из таблицы docs/hell_blocks.md и ExplosionWandMod.java (не из реестров игры).
Запуск: python tools/check_worldgen.py"""
import json, os, re, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(Path(__file__).resolve().parent))
import lookup
lookup.ensure()
reg = {k: set(v) for k, v in json.load(open(lookup.MAPS / "reg.json", encoding="utf-8")).items() if isinstance(v, list)}
DATA = Path(os.environ.get("EW_WORLDGEN_DATA") or ROOT / "resources" / "data" / "explosionwand")  # переопределение - для самопроверки на плохих копиях в build/

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

# --- формат configured_carver 1.20.1 (ванильного jar в кэше нет, формат описан вручную по cave.json/canyon.json) ---
def num(x): return isinstance(x, (int, float)) and not isinstance(x, bool)

def chk_float(f, v, path):
    """FloatProvider: число | constant{value} | uniform{value:{min_inclusive,max_exclusive}} | trapezoid{value:{min,max,plateau}}."""
    if num(v): return
    if not isinstance(v, dict) or "type" not in v: err(f, f"{path}: не float-провайдер"); return
    t = v["type"].partition(":")[2]
    extra = lambda ok: [k for k in v if k not in ok]
    if t == "constant":
        if not num(v.get("value")): err(f, f"{path}: constant без числового value")
    elif t == "uniform":
        w = v.get("value")
        if not isinstance(w, dict): err(f, f"{path}: uniform без объекта value (формат 1.20.1: value{{min_inclusive,max_exclusive}})"); return
        if set(w) != {"min_inclusive", "max_exclusive"} or not all(num(x) for x in w.values()): err(f, f"{path}: uniform.value должен быть {{min_inclusive,max_exclusive}} числами")
        elif w["min_inclusive"] >= w["max_exclusive"]: err(f, f"{path}: uniform min_inclusive >= max_exclusive")
    elif t == "trapezoid":
        w = v.get("value")
        if not isinstance(w, dict): err(f, f"{path}: trapezoid без объекта value (формат 1.20.1: value{{min,max,plateau}})"); return
        if set(w) != {"min", "max", "plateau"} or not all(num(x) for x in w.values()): err(f, f"{path}: trapezoid.value должен быть {{min,max,plateau}} числами")
        elif w["min"] > w["max"] or w["plateau"] < 0 or w["plateau"] > w["max"] - w["min"]: err(f, f"{path}: trapezoid min/max/plateau несовместимы")
    else: err(f, f"{path}: неизвестный float-провайдер '{v['type']}'"); return
    if extra({"type", "value"}): err(f, f"{path}: лишние поля {extra({'type', 'value'})} на уровне провайдера (min_inclusive и т.п. должны быть внутри value)")

def chk_anchor(f, v, path):
    if not isinstance(v, dict) or len(v) != 1 or next(iter(v)) not in ("absolute", "above_bottom", "below_top") or not isinstance(next(iter(v.values())), int):
        err(f, f"{path}: якорь высоты = ровно одно из absolute/above_bottom/below_top (целое)")

def chk_height(f, v, path):
    if isinstance(v, dict) and "type" in v:
        t = v["type"].partition(":")[2]
        if t == "uniform":
            if set(v) != {"type", "min_inclusive", "max_inclusive"}: err(f, f"{path}: height uniform = type+min_inclusive+max_inclusive"); return
            chk_anchor(f, v["min_inclusive"], path + ".min_inclusive"); chk_anchor(f, v["max_inclusive"], path + ".max_inclusive")
            a, b = v["min_inclusive"], v["max_inclusive"]
            if "absolute" in a and "absolute" in b and a["absolute"] > b["absolute"]: err(f, f"{path}: min > max")
        elif t == "constant":
            if "value" not in v: err(f, f"{path}: constant без value")
            else: chk_anchor(f, v["value"], path + ".value")
        elif t in ("biased_to_bottom", "very_biased_to_bottom", "trapezoid"):
            for k in ("min_inclusive", "max_inclusive"):
                if k in v: chk_anchor(f, v[k], f"{path}.{k}")
        else: err(f, f"{path}: неизвестный height provider '{v['type']}'")
    else: chk_anchor(f, v, path)

CARVER_COMMON = {"probability": "prob", "y": "height", "yScale": "float", "lava_level": "anchor", "replaceable": "tag"}
CARVER_REQ = {
    "minecraft:cave": {"horizontal_radius_multiplier": "float", "vertical_radius_multiplier": "float", "floor_level": "float"},
    "minecraft:canyon": {"vertical_rotation": "float", "shape": "shape"},
}
SHAPE_REQ = {"distance_factor": "float", "thickness": "float", "width_smoothness": "int",
             "horizontal_radius_factor": "float", "vertical_radius_default_factor": "float", "vertical_radius_center_factor": "float"}

def chk_field(f, kind, v, path):
    if kind == "float": chk_float(f, v, path)
    elif kind == "height": chk_height(f, v, path)
    elif kind == "anchor": chk_anchor(f, v, path)
    elif kind == "prob":
        if not num(v) or not 0 <= v <= 1: err(f, f"{path}: вероятность 0..1")
    elif kind == "int":
        if not isinstance(v, int) or isinstance(v, bool): err(f, f"{path}: нужно целое")
    elif kind == "tag":
        if not isinstance(v, str) or not v.startswith("#"): err(f, f"{path}: нужен тег '#ns:name'")

def chk_carver(f, o):
    if set(o) != {"type", "config"}: err(f, f"корень карвера: ожидается только type+config, есть {sorted(o)}")
    t = o.get("type"); c = o.get("config")
    if t not in CARVER_REQ: err(f, f"формат для карвера '{t}' не описан в check_worldgen"); return
    if not isinstance(c, dict): err(f, "config не объект"); return
    spec = {**CARVER_COMMON, **CARVER_REQ[t]}
    for k, kind in spec.items():
        if k not in c: err(f, f"config без обязательного поля {k}")
        else: chk_field(f, kind, c[k], "config." + k) if kind != "shape" else None
    for k in c:
        if k not in spec and k != "debug_settings": err(f, f"config: лишнее/неизвестное поле '{k}'")
    if t == "minecraft:canyon" and isinstance(c.get("shape"), dict):
        sh = c["shape"]
        for k, kind in SHAPE_REQ.items():
            if k not in sh: err(f, f"shape без {k}")
            else: chk_field(f, kind, sh[k], "config.shape." + k)
        for k in sh:
            if k not in SHAPE_REQ: err(f, f"shape: лишнее поле '{k}'")
    elif t == "minecraft:canyon" and "shape" in c: err(f, "shape не объект")

n = 0
for f in sorted(DATA.rglob("*.json")):
    rel = f.relative_to(DATA).as_posix()
    if not (rel.startswith(("dimension/hell", "dimension_type/hell", "tags/blocks/hell")) or "hell" in rel and rel.startswith("worldgen/")): continue
    n += 1
    try: o = json.loads(f.read_text(encoding="utf-8"))
    except Exception as e: err(f, f"невалидный JSON: {e}"); continue
    walk(f, o)
    if rel.startswith("worldgen/configured_carver/"): chk_carver(f, o)
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
