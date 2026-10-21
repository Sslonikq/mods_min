#!/usr/bin/env python3
"""
Проверка линковки: каждая ссылка скомпилированных классов мода (build/classes) на классы, методы и поля
net/minecraft* и net/minecraftforge* ищется в НАСТОЯЩИХ jar Forge и клиента 1.20.1 (имя + дескриптор,
с подъёмом по суперклассам и интерфейсам). Ловит ошибки вида «заглушка есть, а в игре NoSuchMethodError /
NoClassDefFoundError», которых не видят SRG-проверка и classcheck.

    python tools/linkcheck.py

Где искать jar: EW_FORGE_DIR (список через os.pathsep), C:\\games\\MultiMC\\libraries, ~/.minecraft/libraries,
~/.gradle, .cache проекта. Нужны: forge-*-client.jar / client-*-srg.jar (классы Minecraft с SRG-именами),
forge-*-universal.jar, eventbus, fmlcore, javafmllanguage, lowcodelanguage, mclanguage.
Нет jar -> «не проверено», код возврата 0. Есть jar и расхождения -> код возврата 1.
Классы java/* сверяются через javap (фильтр ложных срабатываний: Enum/Object/Record и т.п.).
"""
import os, re, struct, sys, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PREFIXES = ("net/minecraft/", "net/minecraftforge/")
WANT = re.compile(r"^(forge-.*-(client|universal)|client-.*-srg|eventbus-.*|fmlcore-.*|javafmllanguage-.*|"
                  r"lowcodelanguage-.*|mclanguage-.*)\.jar$")
SKIP = re.compile(r"-(sources|javadoc|slim|extra)\.jar$")


def roots():
    env = os.environ.get("EW_FORGE_DIR")
    r = [Path(p) for p in env.split(os.pathsep) if p] if env else []
    r += [Path(r"C:\games\MultiMC\libraries"), Path.home() / ".minecraft" / "libraries",
          Path.home() / ".gradle", ROOT / ".cache"]
    return [p for p in r if p.is_dir()]


def vkey(p):
    return [int(x) if x.isdigit() else 0 for x in re.findall(r"\d+|\D+", p.name)]


def find_jars():
    found = []
    for r in roots():
        for dp, dn, fn in os.walk(r):
            for f in fn:
                if WANT.match(f) and not SKIP.search(f): found.append(Path(dp) / f)

    def prio(p):  # пропатченный forge-client первым, затем srg-клиент, затем остальное
        n = p.name
        return 0 if n.startswith("forge-") and n.endswith("-client.jar") else 1 if n.startswith("client-") else 2
    seen, res = set(), []
    for p in sorted(found, key=lambda p: (prio(p), [-x for x in vkey(p)])):
        if p.name not in seen: seen.add(p.name); res.append(p)
    return res


class CF:
    """Минимальный разбор class-файла: пул констант, суперкласс, интерфейсы, члены."""
    def __init__(self, data):
        p = 8
        n = struct.unpack_from(">H", data, p)[0]; p += 2
        cp = [None] * n
        i = 1
        while i < n:
            t = data[p]
            if t == 1:
                ln = struct.unpack_from(">H", data, p + 1)[0]
                cp[i] = ("u", data[p + 3:p + 3 + ln].decode("utf-8", "replace")); p += 3 + ln
            elif t in (3, 4): p += 5
            elif t in (5, 6): p += 9; i += 1
            elif t in (7, 8, 16, 19, 20): cp[i] = ("c" if t == 7 else "o", struct.unpack_from(">H", data, p + 1)[0]); p += 3
            elif t in (9, 10, 11, 12, 17, 18):
                a, b = struct.unpack_from(">HH", data, p + 1); cp[i] = (t, a, b); p += 5
            elif t == 15: p += 4
            else: raise ValueError("tag %d" % t)
            i += 1
        self.cp = cp
        _, this, sup, ni = struct.unpack_from(">HHHH", data, p); p += 8
        self.sup = self.cls(sup) if sup else None
        self.ifaces = []
        for _ in range(ni):
            self.ifaces.append(self.cls(struct.unpack_from(">H", data, p)[0])); p += 2
        self.members = set()
        for _ in range(2):  # поля, затем методы
            cnt = struct.unpack_from(">H", data, p)[0]; p += 2
            for _ in range(cnt):
                _, nm, ds, na = struct.unpack_from(">HHHH", data, p); p += 8
                self.members.add((cp[nm][1], cp[ds][1]))
                for _ in range(na):
                    ln = struct.unpack_from(">I", data, p + 2)[0]; p += 6 + ln

    def cls(self, idx): return self.cp[self.cp[idx][1]][1]

    def refs(self):
        """(вид, владелец, имя, дескриптор) для членов и множество имён классов."""
        mem, cls = set(), set()
        for e in self.cp:
            if not e: continue
            if e[0] in (9, 10, 11):
                nat = self.cp[e[2]]
                mem.add((e[0], self.cls(e[1]), self.cp[nat[1]][1], self.cp[nat[2]][1]))
            elif e[0] == 12:
                cls.update(re.findall(r"L([\w/$]+);", self.cp[e[2]][1]))
            elif e[0] == "c":
                n = self.cp[e[1]][1]
                if n.startswith("["): cls.update(re.findall(r"L([\w/$]+);", n))
                else: cls.add(n)
        return mem, cls


def java_info(c):
    """Супертипы и члены класса JDK через javap (фильтр ложных срабатываний: Enum, Object, Record...)."""
    import build
    code, out = build.run(["javap", "-p", "-s", c.replace("/", ".")])
    if code != 0: return None
    lines = out.splitlines()
    head = next((l for l in lines if "{" in l and ("class " in l or "interface " in l)), "")
    for _ in range(4): head = re.sub(r"<[^<>]*>", "", head)
    sup = []
    m = re.search(r" extends ([\w.$, ]+?)(?: implements |\s*\{)", head)
    if m: sup += [x.strip().replace(".", "/") for x in m.group(1).split(",")]
    m = re.search(r" implements ([\w.$, ]+?)\s*\{", head)
    if m: sup += [x.strip().replace(".", "/") for x in m.group(1).split(",")]
    if c != "java/lang/Object": sup.append("java/lang/Object")
    mem = set()
    simple = re.escape(c.split("/")[-1].split("$")[-1])
    for i, l in enumerate(lines):
        if "descriptor:" in l and i:
            d = l.split("descriptor:")[1].strip()
            decl = lines[i - 1]
            nm = re.search(r"([\w$<>]+)\(", decl) or re.search(r"([\w$]+);\s*$", decl)
            if nm: mem.add((nm.group(1), d))
            if re.search(r"\b" + simple + r"\(", decl): mem.add(("<init>", d))
    return sup, mem


class World:
    def __init__(self, jars):
        self.index = {}
        for j in jars:
            z = zipfile.ZipFile(j)
            for nm in z.namelist():
                if nm.endswith(".class"): self.index.setdefault(nm[:-6], z)
        self.cache = {}

    def info(self, c):
        if c in self.cache: return self.cache[c]
        r = None
        if c in self.index:
            cf = CF(self.index[c].read(c + ".class"))
            r = ([s for s in [cf.sup] + cf.ifaces if s], cf.members)
        elif c.startswith("java/"): r = java_info(c)
        self.cache[c] = r
        return r

    def find(self, c, name, desc, seen=None):
        """True найден; False точно нет; None — цепочка ушла в класс вне известных jar (проверить нельзя)."""
        seen = seen if seen is not None else set()
        if c in seen: return False
        seen.add(c)
        i = self.info(c)
        if i is None: return None
        sup, mem = i
        if (name, desc) in mem: return True
        unknown = False
        for s in sup:
            r = self.find(s, name, desc, seen)
            if r: return True
            if r is None: unknown = True
        return None if unknown else False


def check(build_dir=None):
    """-> (статус 'skip'|'ok', список расхождений, строка статистики)."""
    build_dir = Path(build_dir or ROOT / "build")
    jars = find_jars()
    if not any(p.name.startswith(("forge-", "client-")) for p in jars):
        return "skip", [], "jar Forge/клиента не найдены"
    w = World(jars)
    if "net/minecraft/world/entity/Entity" not in w.index:
        return "skip", [], "в найденных jar нет классов Minecraft с Mojang-путями"
    mem, cls = set(), set()
    for f in (build_dir / "classes").rglob("*.class"):
        m, c = CF(f.read_bytes()).refs(); mem |= m; cls |= c
    cls = {c for c in cls if c.startswith(PREFIXES)}
    mem = {m for m in mem if m[1].startswith(PREFIXES)}
    bad, unverified = [], 0
    for c in sorted(cls):
        if c not in w.index: bad.append("нет класса " + c.replace("/", "."))
    kinds = {9: "поля", 10: "метода", 11: "метода интерфейса"}
    for k, c, n, d in sorted(mem, key=lambda x: (x[1], x[2], x[3])):
        if c not in w.index: continue  # класс уже в списке выше
        r = w.find(c, n, d)
        if r is None: unverified += 1
        elif not r: bad.append(f"нет {kinds[k]} {c.replace('/', '.')}.{n}{d}")
    return "ok", bad, (f"jar {len(jars)}, классов {len(cls)}, членов {len(mem)}, "
                       f"не удалось проверить {unverified}")


def main():
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    st, bad, stat = check()
    if st == "skip":
        print("[2c] linkcheck: НЕ ПРОВЕРЕНО (" + stat + "); задайте EW_FORGE_DIR"); return 0
    print(f"[2c] linkcheck: {stat}, расхождений {len(bad)}")
    if bad:
        print("нет в настоящих Forge/Minecraft 1.20.1 (в игре будет NoSuchMethodError/NoClassDefFoundError):")
        print("\n".join("  " + b for b in bad)); return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
