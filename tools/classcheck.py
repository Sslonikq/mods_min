#!/usr/bin/env python3
"""
Проверка путей классов: каждый net.minecraft.* класс в stubs/ и в байткоде/импортах мода
должен существовать в официальных Mojang-маппингах 1.20.1 (.cache/maps/mojang.txt) под тем же полным именем.
Иначе в игре NoClassDefFoundError. Forge (net.minecraftforge) и сторонние библиотеки в Mojang-таблице
отсутствуют: они перечисляются отдельно (EXTERNAL) и проверяются вручную.
"""
import re, sys, urllib.request, json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MAPS = ROOT / ".cache" / "maps"
MOJ = MAPS / "mojang.txt"
# префиксы, которых нет в Mojang-маппингах (Forge, JEI, библиотеки) — не проверяются автоматически
EXTERNAL = ("net/minecraftforge/", "mezz/jei/", "com/mojang/", "org/joml/", "org/apache/", "org/slf4j/", "com/google/", "it/unimi/", "io/netty/")


def ensure():
    if MOJ.exists(): return
    MAPS.mkdir(parents=True, exist_ok=True)
    try:
        m = json.load(urllib.request.urlopen("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json", timeout=30))
        u = [v["url"] for v in m["versions"] if v["id"] == "1.20.1"][0]
        url = json.load(urllib.request.urlopen(u, timeout=30))["downloads"]["client_mappings"]["url"]
        urllib.request.urlretrieve(url, MOJ)
    except OSError as e:   # URLError/таймаут: нет сети
        MOJ.unlink(missing_ok=True)
        sys.exit(f"classcheck: нет файла {MOJ} и не удалось скачать маппинги Mojang ({e}). "
                 "Подключитесь к сети и повторите или положите mojang.txt (client_mappings 1.20.1) в .cache/maps/.")


def load():
    ensure()
    names = set()
    for line in open(MOJ, encoding="utf-8"):
        if line.startswith("#") or line.startswith(" "): continue
        names.add(line.split(" -> ")[0].strip().replace(".", "/"))
    return names


def is_mc(n): return n.startswith("net/minecraft/") and not n.startswith("net/minecraftforge/")


def check(build_dir):
    names = load(); bad = []
    # 1. заглушки: путь файла = полное имя класса (вложенные классы проверяются по внешнему)
    for f in sorted((ROOT / "stubs").rglob("*.java")):
        rel = f.relative_to(ROOT / "stubs").with_suffix("").as_posix()
        if is_mc(rel) and rel not in names: bad.append(f"заглушка: {rel} нет в Mojang-маппингах 1.20.1")
    # 2. байткод: все ссылки на net/minecraft/* из скомпилированного мода (константный пул)
    seen = set()
    for c in (build_dir / "classes").rglob("*.class"):
        data = c.read_bytes()
        for m in re.finditer(rb"net/minecraft/[A-Za-z0-9_/$]+", data):
            seen.add(m.group().decode())
    for n in sorted(seen):
        # вложенные классы (Outer$Inner) в mojang.txt перечислены, проверяем строго; дескрипторы (Lnet/...;) regex уже отрезал по ';'
        if n not in names: bad.append(f"байткод: {n} нет в Mojang-маппингах 1.20.1")
    return bad, len(seen)


if __name__ == "__main__":
    bad, n = check(ROOT / "build")
    print(f"классов net/minecraft в байткоде: {n}, ошибок: {len(bad)}")
    for b in bad: print("  " + b)
    sys.exit(1 if bad else 0)
