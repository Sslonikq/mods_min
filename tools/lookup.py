#!/usr/bin/env python3
"""
Поиск SRG-имени по понятному (yarn) имени.

    python tools/lookup.py LivingEntity "^getHealth$"    # класс yarn + regex имени члена
    python tools/lookup.py "~item/ArmorItem" "^getType$"  # ~ = часть пути класса
    python tools/lookup.py --srg m_21223_                 # обратно: SRG -> yarn

Цепочка: yarn (named) -> intermediary -> obf -> SRG (MCPConfig joined.tsrg).
Таблицы лежат в .cache/maps и скачиваются при первом запуске (нужны интернет и git).
"""
import os, re, sys, pickle, subprocess, urllib.request
from pathlib import Path

MAPS = Path(__file__).resolve().parent.parent / ".cache" / "maps"
CACHE = MAPS / "index.pkl"
URLS = {
    "joined.tsrg": "https://raw.githubusercontent.com/MinecraftForge/MCPConfig/master/versions/release/1.20.1/joined.tsrg",
    "inter.tiny": "https://raw.githubusercontent.com/FabricMC/intermediary/master/mappings/1.20.1.tiny",
    "reg.json": "https://raw.githubusercontent.com/misode/mcmeta/1.20.1-summary/registries/data.json",
}


def ensure():
    MAPS.mkdir(parents=True, exist_ok=True)
    for name, url in URLS.items():
        p = MAPS / name
        if not p.exists():
            print(f"  скачиваю {name}...", file=sys.stderr); urllib.request.urlretrieve(url, p)
    if not (MAPS / "yarn" / "mappings").exists():
        print("  клонирую yarn 1.20.1...", file=sys.stderr)
        subprocess.run(["git", "clone", "-q", "--depth", "1", "-b", "1.20.1", "https://github.com/FabricMC/yarn", str(MAPS / "yarn")], check=True)


def build():
    ensure()
    i2o_m, i2o_f = {}, {}
    for line in open(MAPS / "inter.tiny", encoding="utf-8"):
        p = line.rstrip("\n").split("\t")
        if p[0] == "METHOD": i2o_m[p[4]] = (p[1], p[3], p[2])
        elif p[0] == "FIELD": i2o_f[p[4]] = (p[1], p[3], p[2])
    srg_m, srg_f, cur = {}, {}, None
    for line in open(MAPS / "joined.tsrg", encoding="utf-8"):
        if line.startswith("tsrg2"): continue
        if not line.startswith("\t"): cur = line.split()[0]; continue
        if line.startswith("\t\t"): continue
        p = line.strip().split()
        if len(p) == 4 and p[1].startswith("("): srg_m[(cur, p[0], p[1])] = p[2]
        elif len(p) >= 2 and not p[1].startswith("("): srg_f[(cur, p[0])] = p[1]
    rows = []
    for root, _, files in os.walk(MAPS / "yarn" / "mappings"):
        for fn in files:
            if not fn.endswith(".mapping"): continue
            stack = []
            for line in open(os.path.join(root, fn), encoding="utf-8"):
                ind = len(line) - len(line.lstrip("\t")); p = line.strip().split()
                if not p: continue
                if p[0] == "CLASS":
                    stack = stack[:ind]
                    if ind == 0: inter, named = p[1], (p[2] if len(p) > 2 else p[1])
                    else: inter, named = stack[-1][0] + "$" + p[1], stack[-1][1] + "$" + (p[2] if len(p) > 2 else p[1])
                    stack.append((inter, named))
                elif p[0] in ("METHOD", "FIELD") and stack:
                    owner = stack[min(ind, len(stack)) - 1]
                    named = p[2] if len(p) == 4 else p[1]
                    if p[0] == "METHOD": o = i2o_m.get(p[1]); srg = srg_m.get(o) if o else None
                    else: o = i2o_f.get(p[1]); srg = srg_f.get((o[0], o[1])) if o else None
                    if srg and srg[:2] in ("m_", "f_"): rows.append((owner[1], p[0][0], named, p[-1], srg))
    data = (rows, set(srg_m.values()) | set(srg_f.values()))
    pickle.dump(data, open(CACHE, "wb"))
    return data


def load():
    return pickle.load(open(CACHE, "rb")) if CACHE.exists() else build()


if __name__ == "__main__":
    if len(sys.argv) < 2: sys.exit(__doc__)
    rows, allsrg = load()
    if sys.argv[1] == "--srg":
        for r in rows:
            if r[4] == sys.argv[2]: print(r)
        sys.exit()
    cs, mr = sys.argv[1], (re.compile(sys.argv[2]) if len(sys.argv) > 2 else None)
    for r in rows:
        if r[0].split("/")[-1] == cs or (cs.startswith("~") and cs[1:] in r[0]):
            if mr is None or mr.search(r[2]): print(f"{r[0].split('/')[-1]}\t{r[1]}\t{r[2]}\t{r[3]}\t{r[4]}")
