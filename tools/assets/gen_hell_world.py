#!/usr/bin/env python3
"""Генератор датапака Мира Ада (фаза 5.1): измерение, тип, noise_settings, density functions, биомы, фичи.
Пишет в resources/data/explosionwand/. Запуск: python tools/assets/gen_hell_world.py"""
import json
from pathlib import Path

D = Path(__file__).resolve().parents[2] / "resources" / "data" / "explosionwand"
NS = "explosionwand:"


def put(rel, obj):
    p = D / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(obj, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


# ---------- density functions ----------
def noise(n, xz=1.0, y=0.0): return {"type": "minecraft:noise", "noise": "minecraft:" + n, "xz_scale": xz, "y_scale": y}
def add(a, b): return {"type": "minecraft:add", "argument1": a, "argument2": b}
def mul(a, b): return {"type": "minecraft:mul", "argument1": a, "argument2": b}
def clamp(a, lo, hi): return {"type": "minecraft:clamp", "input": a, "min": lo, "max": hi}
def absf(a): return {"type": "minecraft:abs", "argument": a}
def mn(a, b): return {"type": "minecraft:min", "argument1": a, "argument2": b}
def mx(a, b): return {"type": "minecraft:max", "argument1": a, "argument2": b}
def grad(y0, y1, v0, v1): return {"type": "minecraft:y_clamped_gradient", "from_y": y0, "to_y": y1, "from_value": v0, "to_value": v1}
def ref(n): return NS + "hell/" + n
def flat(a): return {"type": "minecraft:flat_cache", "argument": a}
def c2d(a): return {"type": "minecraft:cache_2d", "argument": a}
def neg(a): return mul(-1.0, a)


def build_density():
    put("worldgen/density_function/hell/continents.json", flat(clamp(mul(1.7, noise("continentalness", 0.5)), -1.0, 1.0)))
    put("worldgen/density_function/hell/erosion.json", flat(clamp(mul(1.7, noise("erosion", 0.7)), -1.0, 1.0)))
    put("worldgen/density_function/hell/humidity.json", flat(clamp(mul(1.7, noise("vegetation", 0.35)), -1.0, 1.0)))
    put("worldgen/density_function/hell/ridges.json", flat(clamp(mul(2.0, noise("ridge", 0.8)), -1.0, 1.0)))
    # land: 0 в море, 1 на суше (берег около continents = -0.35)
    put("worldgen/density_function/hell/land.json", c2d(clamp(mul(4.0, add(0.5, ref("continents"))), 0.0, 1.0)))
    # peaks: 0..1, доля гор по эрозии (пики при erosion < -0.3)
    put("worldgen/density_function/hell/peaks.json", c2d(clamp(add(-0.5, mul(-2.5, ref("erosion"))), 0.0, 1.0)))
    # hilliness: холмистость, в долине плоско
    put("worldgen/density_function/hell/hilliness.json", c2d(clamp(add(1.2, mul(-1.3, ref("erosion"))), 0.1, 1.0)))
    land, peaks, hf = ref("land"), ref("peaks"), ref("hilliness")
    island = mul(30.0, clamp(mul(4.0, add(-0.15, noise("surface", 1.0))), 0.0, 1.0))
    hills = mul(mul(land, hf), mul(18.0, noise("ridge", 0.5)))
    mountain = clamp(mul(2.2, absf(noise("ridge", 1.5))), 0.0, 1.0)
    peak = mul(mul(peaks, land), add(20.0, mul(85.0, mountain)))
    h = add(add(20.0, mul(36.0, land)), add(add(mul(add(1.0, neg(land)), island), hills), peak))
    put("worldgen/density_function/hell/surface_height.json", c2d(h))
    y = grad(0, 256, 0.0, 256.0)
    put("worldgen/density_function/hell/slope.json", mul(0.08, add(ref("surface_height"), neg(y))))
    rough = mul(add(0.35, mul(0.9, peaks)), noise("ridge", 1.5, 1.2))
    d = add(add(ref("slope"), rough), grad(200, 250, 0.0, -3.0))
    cave = mul(-3.0, add(noise("cave_cheese", 1.0, 0.7), -0.3))
    cave = mx(cave, grad(28, 40, 1.0, -2.0))
    # у поверхности пещеры глушим: выше (поверхность - 8) терм растёт и делает блок твёрдым
    cave = mx(cave, mul(0.25, add(add(y, neg(ref("surface_height"))), 8.0)))
    put("worldgen/density_function/hell/final.json", {"type": "minecraft:interpolated", "argument": mn(d, cave)})


# ---------- noise settings ----------
def blk(name, **props):
    s = {"Name": name}
    if props: s["Properties"] = {k: str(v) for k, v in props.items()}
    return s


def rule_block(name): return {"type": "minecraft:block", "result_state": blk(name)}
def cond(c, then): return {"type": "minecraft:condition", "if_true": c, "then_run": then}
def seq(*r): return {"type": "minecraft:sequence", "sequence": list(r)}
def biome_is(b): return {"type": "minecraft:biome", "biome_is": [NS + b]}
def top(): return {"type": "minecraft:stone_depth", "add_surface_depth": False, "offset": 0, "secondary_depth_range": 0, "surface_type": "floor"}
def layer(): return {"type": "minecraft:stone_depth", "add_surface_depth": True, "offset": 0, "secondary_depth_range": 0, "surface_type": "floor"}
def nthr(n, lo, hi=10.0): return {"type": "minecraft:noise_threshold", "noise": "minecraft:" + n, "min_threshold": lo, "max_threshold": hi}


def build_noise_settings():
    surface = seq(
        cond(biome_is("hell_ash_wastes"), cond(layer(), rule_block(NS + "ash_soil"))),
        cond(biome_is("hell_smoldering_forest"), cond(layer(), rule_block(NS + "ash_soil"))),
        cond(biome_is("hell_basalt_peaks"), cond(top(), seq(
            cond(nthr("surface", 0.55), rule_block(NS + "blazing_stone")),
            cond(nthr("surface_secondary", 0.45), rule_block(NS + "eclipse_cobblestone"))))),
        cond(biome_is("hell_lava_sea"), cond(layer(), seq(
            cond(nthr("surface_secondary", 0.3), rule_block(NS + "pumice")),
            cond(nthr("netherrack", 0.3), rule_block("minecraft:magma_block")),
            rule_block(NS + "slag")))),
        cond(biome_is("hell_soul_valley"), cond(layer(), seq(
            cond(nthr("soul_sand_layer", -0.012), rule_block("minecraft:soul_sand")),
            rule_block("minecraft:soul_soil")))),
    )
    bedrock = cond({"type": "minecraft:vertical_gradient", "random_name": NS + "bedrock_floor",
                    "true_at_and_below": {"above_bottom": 0}, "false_at_and_above": {"above_bottom": 5}}, rule_block("minecraft:bedrock"))
    s = {
        "aquifers_enabled": False,
        "default_block": blk(NS + "eclipse_stone"),
        "default_fluid": blk("minecraft:lava", level=0),
        "disable_mob_generation": True,
        "legacy_random_source": False,
        "noise": {"min_y": 0, "height": 256, "size_horizontal": 1, "size_vertical": 2},
        "noise_router": {
            "barrier": 0.0, "fluid_level_floodedness": 0.0, "fluid_level_spread": 0.0, "lava": 0.0,
            "temperature": 0.0, "vegetation": ref("humidity"), "continents": ref("continents"), "erosion": ref("erosion"),
            "depth": 0.0, "ridges": ref("ridges"),
            "initial_density_without_jaggedness": ref("slope"),
            "final_density": ref("final"),
            "vein_toggle": 0.0, "vein_ridged": 0.0, "vein_gap": 0.0},
        "ore_veins_enabled": False,
        "sea_level": 40,
        "spawn_target": [],
        "surface_rule": seq(bedrock, cond({"type": "minecraft:above_preliminary_surface"}, surface)),
    }
    put("worldgen/noise_settings/hell.json", s)


# ---------- dimension & type ----------
BIOMES = {  # имя: (continentalness, erosion, humidity)
    "hell_lava_sea": ([-2.0, -0.35], [-2.0, 2.0], [-2.0, 2.0]),
    "hell_basalt_peaks": ([-0.35, 2.0], [-2.0, -0.3], [-2.0, 2.0]),
    "hell_soul_valley": ([-0.35, 2.0], [0.35, 2.0], [-2.0, 2.0]),
    "hell_ash_wastes": ([-0.35, 2.0], [-0.3, 0.35], [-2.0, 0.05]),
    "hell_smoldering_forest": ([-0.35, 2.0], [-0.3, 0.35], [0.05, 2.0]),
}


def build_dimension():
    biomes = [{"biome": NS + b, "parameters": {"temperature": 0.0, "humidity": h, "continentalness": c, "erosion": e,
                                               "depth": 0.0, "weirdness": 0.0, "offset": 0.0}} for b, (c, e, h) in BIOMES.items()]
    put("dimension/hell.json", {"type": NS + "hell", "generator": {
        "type": "minecraft:noise", "settings": NS + "hell", "biome_source": {"type": "minecraft:multi_noise", "biomes": biomes}}})
    put("dimension_type/hell.json", {
        "ambient_light": 0.1, "bed_works": False, "coordinate_scale": 1.0, "effects": "minecraft:overworld", "fixed_time": 13000,
        "has_ceiling": False, "has_raids": False, "has_skylight": True, "height": 256, "infiniburn": "#minecraft:infiniburn_nether",
        "logical_height": 256, "min_y": 0, "monster_spawn_block_light_limit": 0, "monster_spawn_light_level": 0,
        "natural": False, "piglin_safe": False, "respawn_anchor_works": False, "ultrawarm": True})


# ---------- features ----------
def simple(name, **p): return {"type": "minecraft:simple_state_provider", "state": blk(name, **p)}
def weighted(*entries): return {"type": "minecraft:weighted_state_provider", "entries": [{"data": blk(n, **p), "weight": w} for n, w, p in entries]}
def uni(a, b): return {"type": "minecraft:uniform", "value": {"min_inclusive": a, "max_inclusive": b}}
def below(*blocks): return {"type": "minecraft:matching_blocks", "offset": [0, -1, 0], "blocks": list(blocks)}
AIR = {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"}
def on(*blocks): return {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:all_of", "predicates": [AIR, below(*blocks)]}}
def hm(t="MOTION_BLOCKING"): return {"type": "minecraft:heightmap", "heightmap": t}
BIOME = {"type": "minecraft:biome"}
SQ = {"type": "minecraft:in_square"}
def rare(n): return {"type": "minecraft:rarity_filter", "chance": n}
def count(n): return {"type": "minecraft:count", "count": n}
def yrange(a, b): return {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": a}, "max_inclusive": {"absolute": b}}}

CONF, PLACED = {}, {}
ORDER = []  # (шаг, имя): единый порядок для всех биомов (без циклов порядка фич)


def feat(step, name, ftype, config, placement):
    CONF[name] = {"type": "minecraft:" + ftype, "config": config}
    PLACED[name] = {"feature": NS + name, "placement": placement}
    ORDER.append((step, name))


def patch(step, name, state, tries, spread, soils, chance, ys=2):
    inner = {"feature": {"type": "minecraft:simple_block", "config": {"to_place": state}}, "placement": [on(*soils)]}
    feat(step, name, "random_patch", {"tries": tries, "xz_spread": spread, "y_spread": ys, "feature": inner}, [rare(chance), SQ, hm(), BIOME])


def tree(step, name, trunk, trunk_placer, foliage, foliage_placer, soil, soils_on, decorators, n):
    cfg = {"trunk_provider": simple(trunk), "trunk_placer": trunk_placer, "foliage_provider": foliage, "foliage_placer": foliage_placer,
           "dirt_provider": simple(soil), "minimum_size": {"type": "minecraft:two_layers_feature_size", "limit": 1, "lower_size": 0, "upper_size": 1},
           "decorators": decorators, "ignore_vines": True}
    feat(step, name, "tree", cfg, [count(n), SQ, hm(), on(*soils_on), BIOME])


def build_features():
    ash, soul, soulsand = NS + "ash_soil", "minecraft:soul_soil", "minecraft:soul_sand"
    GEODE, ORE, SPRING, V = 3, 6, 8, 9
    layers = {"filling": 1.7, "inner_layer": 2.2, "middle_layer": 3.2, "outer_layer": 4.2}
    feat(GEODE, "hell_eclipse_geode", "geode", {
        "blocks": {"filling_provider": simple("minecraft:air"), "inner_layer_provider": simple(NS + "eclipse_crystal_block"),
                   "alternate_inner_layer_provider": simple(NS + "eclipse_crystal_block"), "middle_layer_provider": simple(NS + "dark_obsidian"),
                   "outer_layer_provider": simple(NS + "eclipse_stone"),
                   "inner_placements": [blk(NS + "eclipse_cluster", age=3, facing="up")],
                   "cannot_replace": "#minecraft:features_cannot_replace", "invalid_blocks": "#minecraft:geode_invalid_blocks"},
        "layers": layers, "crack": {"generate_crack_chance": 0.5, "base_crack_size": 2.0, "crack_point_offset": 2},
        "noise_multiplier": 0.05, "use_potential_placements_chance": 0.35, "use_alternate_layer0_chance": 0.0,
        "placements_require_layer0_alternate": False, "outer_wall_distance": uni(4, 6), "distribution_points": uni(3, 4),
        "point_offset": uni(1, 2), "invalid_blocks_threshold": 1, "min_gen_offset": -16, "max_gen_offset": 16},
        [rare(10), SQ, yrange(50, 150), BIOME])

    def ore(name, block, size, cnt, y0, y1, chance=None):
        pl = ([rare(chance)] if chance else []) + [count(cnt), SQ, yrange(y0, y1), BIOME]
        feat(ORE, name, "ore", {"size": size, "discard_chance_on_air_exposure": 0.0,
             "targets": [{"target": {"predicate_type": "minecraft:block_match", "block": NS + "eclipse_stone"}, "state": blk(block)}]}, pl)
    ore("hell_blood_iron_ore", NS + "blood_iron_ore", 9, 12, 6, 160)
    ore("hell_crown_gem_ore", NS + "crown_gem_ore", 3, 1, 6, 80, 4)
    feat(SPRING, "hell_lavafall", "spring_feature", {
        "state": {"Name": "minecraft:lava", "Properties": {"falling": "true"}}, "requires_block_below": True, "rock_count": 4, "hole_count": 1,
        "valid_blocks": [NS + "eclipse_stone", NS + "slag", NS + "pumice", NS + "eclipse_cobblestone"]},
        [count(5), SQ, yrange(36, 130), BIOME])
    # деревья
    forking = {"type": "minecraft:forking_trunk_placer", "base_height": 4, "height_rand_a": 2, "height_rand_b": 2}
    nofol = {"type": "minecraft:blob_foliage_placer", "radius": 0, "offset": 0, "height": 0}
    tree(V, "hell_charred_tree", NS + "charred_log", forking, simple("minecraft:air"), nofol, ash, [ash], [], 2)
    tree(V, "hell_smoldering_tree", NS + "smoldering_log", {"type": "minecraft:straight_trunk_placer", "base_height": 5, "height_rand_a": 2, "height_rand_b": 2},
         simple(NS + "smoldering_leaves"), {"type": "minecraft:blob_foliage_placer", "radius": 2, "offset": 0, "height": 3}, ash, [ash],
         [{"type": "minecraft:attached_to_leaves", "probability": 0.25, "exclusion_radius_xz": 1, "exclusion_radius_y": 0, "required_empty_blocks": 1,
           "block_provider": simple(NS + "fire_vine"), "directions": ["down"]}], 9)
    tree(V, "hell_bone_tree", "minecraft:bone_block", {"type": "minecraft:straight_trunk_placer", "base_height": 4, "height_rand_a": 3, "height_rand_b": 1},
         simple("minecraft:bone_block"), {"type": "minecraft:random_spread_foliage_placer", "radius": 2, "offset": 0, "foliage_height": 2, "leaf_placement_attempts": 20},
         soul, [soul, soulsand], [{"type": "minecraft:attached_to_leaves", "probability": 0.3, "exclusion_radius_xz": 1, "exclusion_radius_y": 0,
                                   "required_empty_blocks": 1, "block_provider": simple(NS + "ghost_vine"), "directions": ["down"]}], 3)
    feat(V, "hell_coal_mushroom", "huge_brown_mushroom", {"cap_provider": simple(NS + "coal_mushroom_cap"), "stem_provider": simple(NS + "coal_mushroom_stem"),
         "foliage_radius": 2}, [count(2), SQ, hm(), on(ash), BIOME])
    # покров и растения
    patch(V, "hell_ash_layer", weighted((NS + "ash_layer", 4, {"layers": 1}), (NS + "ash_layer", 2, {"layers": 2}), (NS + "ash_layer", 1, {"layers": 3})), 48, 7, [ash], 1)
    patch(V, "hell_ember_moss", simple(NS + "ember_moss"), 40, 6, [ash], 2)
    patch(V, "hell_dry_grass", simple(NS + "dry_grass"), 40, 7, [ash], 1)
    patch(V, "hell_ember_bloom", simple(NS + "ember_bloom"), 14, 5, [ash], 3)
    patch(V, "hell_spark_fern", simple(NS + "spark_fern"), 16, 5, [ash], 2)
    patch(V, "hell_coal_berry", weighted((NS + "coal_berry_bush", 2, {"age": 3}), (NS + "coal_berry_bush", 1, {"age": 2})), 6, 4, [ash], 8)
    patch(V, "hell_ghost_moss", simple(NS + "ghost_moss"), 48, 7, [soul, soulsand], 1)
    patch(V, "hell_ghost_flower", simple(NS + "ghost_flower"), 12, 5, [soul, soulsand, NS + "ghost_moss"], 2)
    patch(V, "hell_soul_fire", simple("minecraft:soul_fire"), 8, 5, [soul], 3)
    patch(V, "hell_geyser", simple(NS + "geyser"), 3, 3, [NS + "slag", NS + "pumice"], 4)
    patch(V, "hell_fire_coral", simple(NS + "fire_coral"), 20, 5, [NS + "slag", "minecraft:magma_block"], 2)

    def column_patch(name, block, soils, tries, a, c, chance):
        col = {"type": "minecraft:block_column", "config": {"direction": "up", "allowed_placement": AIR, "prioritize_tip": False,
               "layers": [{"height": uni(a, c), "provider": block}]}}
        feat(V, name, "random_patch", {"tries": tries, "xz_spread": 6, "y_spread": 2, "feature": {"feature": col, "placement": [on(*soils)]}},
             [rare(chance), SQ, hm(), BIOME])
    column_patch("hell_petrified_bones", simple(NS + "petrified_bones"), [ash], 8, 1, 3, 3)
    column_patch("hell_archmage_pillars", simple(NS + "archmage_stone"), [soul, soulsand], 8, 2, 5, 3)
    # друзы на поверхности гор
    clusters = weighted(*[(NS + "eclipse_cluster", w, {"age": a, "facing": "up"}) for a, w in ((0, 2), (1, 2), (2, 2), (3, 1))])
    patch(V, "hell_eclipse_clusters", clusters, 20, 6, [NS + "eclipse_stone", NS + "eclipse_cobblestone"], 2)
    # шпили из тёмного обсидиана: пучок колонн разной высоты (сужается кверху), на макушках кристаллы
    spire_col = {"type": "minecraft:block_column", "config": {"direction": "up", "allowed_placement": AIR, "prioritize_tip": True, "layers": [
        {"height": {"type": "minecraft:biased_to_bottom", "value": {"min_inclusive": 8, "max_inclusive": 32}},
         "provider": weighted((NS + "dark_obsidian", 8, {}), (NS + "carved_eclipse_bricks", 1, {}))},
        {"height": 1, "provider": simple(NS + "eclipse_crystal_block")}]}}
    inner = {"feature": spire_col, "placement": [hm("WORLD_SURFACE_WG")]}
    feat(V, "hell_spire", "random_patch", {"tries": 22, "xz_spread": 3, "y_spread": 3, "feature": inner}, [rare(14), SQ, hm("WORLD_SURFACE_WG"), BIOME])

    for n, c in CONF.items(): put(f"worldgen/configured_feature/{n}.json", c)
    for n, p in PLACED.items(): put(f"worldgen/placed_feature/{n}.json", p)


# ---------- biomes ----------
BIO = {
    "hell_ash_wastes": dict(fog=0x8A4A2E, sky=0x9C1D12, water=0x4A1A14, wfog=0x1A0A08, particle=("ash", 0.02), mood="nether_wastes", music="nether_wastes",
        feats=["hell_blood_iron_ore", "hell_lavafall", "hell_charred_tree", "hell_ash_layer", "hell_dry_grass", "hell_ember_bloom", "hell_coal_berry", "hell_petrified_bones"]),
    "hell_smoldering_forest": dict(fog=0xA3501F, sky=0xA02312, water=0x4A1A14, wfog=0x1A0A08, particle=("flame", 0.004), mood="crimson_forest", music="crimson_forest",
        feats=["hell_blood_iron_ore", "hell_smoldering_tree", "hell_coal_mushroom", "hell_ash_layer", "hell_ember_moss", "hell_ember_bloom", "hell_spark_fern"]),
    "hell_basalt_peaks": dict(fog=0x6A3050, sky=0x8C1A22, water=0x3A1A24, wfog=0x1A0A10, particle=("white_ash", 0.04), mood="basalt_deltas", music="basalt_deltas",
        feats=["hell_eclipse_geode", "hell_blood_iron_ore", "hell_crown_gem_ore", "hell_lavafall", "hell_eclipse_clusters", "hell_spire"]),
    "hell_lava_sea": dict(fog=0xD5601A, sky=0xA82A10, water=0x4A1A14, wfog=0x1A0A08, particle=("lava", 0.002), mood="nether_wastes", music="nether_wastes",
        feats=["hell_blood_iron_ore", "hell_crown_gem_ore", "hell_lavafall", "hell_geyser", "hell_fire_coral"]),
    "hell_soul_valley": dict(fog=0x3FB0A8, sky=0x7A1E34, water=0x1A4A4A, wfog=0x082020, particle=("soul", 0.012), mood="soul_sand_valley", music="soul_sand_valley",
        feats=["hell_blood_iron_ore", "hell_bone_tree", "hell_ghost_moss", "hell_ghost_flower", "hell_soul_fire", "hell_archmage_pillars"]),
}


def build_biomes():
    step_of = {n: s for s, n in ORDER}
    pos = {n: i for i, (s, n) in enumerate(ORDER)}
    for bid, b in BIO.items():
        feats = [[] for _ in range(11)]
        for n in sorted(b["feats"], key=lambda n: pos[n]):
            feats[step_of[n]].append(NS + n)
        put(f"worldgen/biome/{bid}.json", {
            "carvers": {}, "downfall": 0.0, "has_precipitation": False, "temperature": 2.0,
            "effects": {"fog_color": b["fog"], "sky_color": b["sky"], "water_color": b["water"], "water_fog_color": b["wfog"],
                        "particle": {"options": {"type": "minecraft:" + b["particle"][0]}, "probability": b["particle"][1]},
                        "mood_sound": {"block_search_extent": 8, "offset": 2.0, "sound": f"minecraft:ambient.{b['mood']}.mood", "tick_delay": 6000},
                        "music": {"max_delay": 24000, "min_delay": 12000, "replace_current_music": False, "sound": f"minecraft:music.nether.{b['music']}"}},
            "features": feats, "spawn_costs": {},
            "spawners": {k: [] for k in ("ambient", "axolotls", "creature", "misc", "monster", "underground_water_creature", "water_ambient", "water_creature")}})
        if bid == "hell_ash_wastes":
            # совместимость с мирами 3.0.x: в чанках сохранена палитра explosionwand:hell; в multi_noise этого биома нет
            put("worldgen/biome/hell.json", json.loads((D / "worldgen/biome/hell_ash_wastes.json").read_text(encoding="utf-8")))


if __name__ == "__main__":
    build_density(); build_noise_settings(); build_dimension(); build_features(); build_biomes()
    print("ok")
