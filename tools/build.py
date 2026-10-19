#!/usr/bin/env python3
"""
Сборка мода explosionwand без Gradle.

    python tools/build.py              # компиляция + проверки + тесты + jar в dist/
    python tools/build.py --install    # то же + копия jar в ~/Downloads
    python tools/build.py --fast       # без проверок SRG/ID и тестов (только jar)

Шаги:
  1. javac --release 17: stubs/ -> build/stubout, затем src/ -> build/classes (classpath = stubout)
  2. проверка SRG: каждое m_/f_ имя в исходниках И в байткоде есть в MCPConfig joined.tsrg
  3. проверка строковых ID (блоки, звуки, частицы, эффекты, предметы, сущности) по реестру 1.20.1
  3b. шаблоны данжей: tools/gen_dungeons.py --check (файлы .nbt свежие, ID блоков и мобов есть в реестре)
  4. тесты: tests/*.java (main-харнессы на заглушках)
  5. jar: классы + resources/, версия берётся из resources/META-INF/mods.toml
Таблицы маппингов кешируются в .cache/maps (скачиваются при первом запуске, нужен интернет и git).
"""
import argparse, os, re, shutil, subprocess, sys, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BUILD, DIST = ROOT / "build", ROOT / "dist"
sys.path.insert(0, str(Path(__file__).resolve().parent))


_TOOLS = {}


def _jdk_dirs():
    """Каталоги bin кандидатов JDK: JAVA_HOME, затем типовые места установки."""
    jh = os.environ.get("JAVA_HOME")
    if jh: yield Path(jh) / "bin"
    pats = []
    if os.name == "nt":
        for env in ("ProgramFiles", "ProgramFiles(x86)", "LOCALAPPDATA"):
            base = os.environ.get(env)
            if not base: continue
            for vendor in ("Java", "Eclipse Adoptium", "Microsoft", "Zulu", "BellSoft", "Amazon Corretto",
                           "Semeru", "Programs/Eclipse Adoptium", "Programs/Microsoft"):
                pats.append(Path(base) / vendor)
        roots = [p for p in pats if p.is_dir()]
        cands = []
        for r in roots: cands += [d for d in r.glob("*") if (d / "bin").is_dir()]
    else:
        cands = []
        for r in ("/usr/lib/jvm", "/Library/Java/JavaVirtualMachines"):
            if Path(r).is_dir(): cands += list(Path(r).glob("*"))
    # свежие версии первыми
    for d in sorted(cands, key=lambda p: [int(x) if x.isdigit() else 0 for x in re.findall(r"\d+|\D+", p.name)], reverse=True):
        yield d / "bin"
        yield d / "Contents" / "Home" / "bin"


def find_tool(name):
    """Путь к javac/javap/java: PATH -> JAVA_HOME -> типовые каталоги JDK."""
    if name in _TOOLS: return _TOOLS[name]
    exe = shutil.which(name)
    if not exe:
        for d in _jdk_dirs():
            for ext in ("", ".exe"):
                if (d / (name + ext)).is_file(): exe = str(d / (name + ext)); break
            if exe: break
    if not exe:
        sys.exit(f"не найден '{name}' (нужен JDK 17+, а не только JRE).\n"
                 "Искал: PATH, JAVA_HOME, C:\\Program Files\\Java\\jdk-*, Eclipse Adoptium, Microsoft, Zulu и т.п.\n"
                 "Решение: установить JDK 17+ или задать переменную JAVA_HOME (каталог JDK, не bin).")
    _TOOLS[name] = exe
    return exe


def run(cmd, **kw):
    if cmd and cmd[0] in ("javac", "javap", "java"): cmd = [find_tool(cmd[0])] + list(cmd[1:])
    env = dict(os.environ); env.pop("JAVA_TOOL_OPTIONS", None)
    r = subprocess.run(cmd, cwd=ROOT, env=env, capture_output=True, text=True, **kw)
    out = "\n".join(l for l in (r.stdout + r.stderr).splitlines() if not l.startswith("Picked up"))
    return r.returncode, out


def javac(out_dir: Path, sources, classpath=None):
    out_dir.mkdir(parents=True, exist_ok=True)
    argfile = BUILD / (out_dir.name + ".args")
    argfile.write_text("\n".join('"' + str(s).replace("\\", "/") + '"' for s in sources), encoding="utf-8")
    cmd = ["javac", "--release", "17", "-nowarn", "-encoding", "UTF-8", "-d", str(out_dir)]
    if classpath: cmd += ["-cp", classpath]
    code, out = run(cmd + ["@" + str(argfile)])
    if code != 0: sys.exit("javac (" + out_dir.name + ") упал:\n" + out)


def version():
    m = re.search(r'^version="([^"]+)"', (ROOT / "resources/META-INF/mods.toml").read_text(encoding="utf-8"), re.M)
    return m.group(1) if m else "0.0.0"


def compile_all():
    if BUILD.exists(): shutil.rmtree(BUILD)
    BUILD.mkdir()
    javac(BUILD / "stubout", sorted((ROOT / "stubs").rglob("*.java")))
    javac(BUILD / "classes", sorted((ROOT / "src").rglob("*.java")), str(BUILD / "stubout"))
    n = len(list((BUILD / "classes").rglob("*.class")))
    print(f"[1] компиляция: {n} классов")


def check_srg():
    import lookup
    _, allsrg = lookup.load()
    rx = re.compile(r"\b[mf]_[0-9]+_\b")
    src = set()
    for f in (ROOT / "src").rglob("*.java"): src |= set(rx.findall(f.read_text(encoding="utf-8")))
    bc = set()
    classes = [str(c) for c in (BUILD / "classes").rglob("*.class")]
    for i in range(0, len(classes), 40):
        _, out = run(["javap", "-c", "-p"] + classes[i:i + 40]); bc |= set(rx.findall(out))
    miss = sorted((src | bc) - allsrg)
    print(f"[2] SRG: исходники {len(src)}, байткод {len(bc)}, нет в joined.tsrg: {len(miss)}")
    if miss: sys.exit("неизвестные SRG-имена: " + ", ".join(miss))


def check_ids():
    import ids
    bad = ids.check(ROOT)
    print(f"[3] строковые ID: проблем {len(bad)}")
    if bad: sys.exit("\n".join(bad))


def check_dungeons():
    code, out = run([sys.executable, str(ROOT / "tools" / "gen_dungeons.py"), "--check"])
    print("[3b] " + (out.strip().splitlines()[0] if out.strip() else "gen_dungeons"))
    if code != 0: sys.exit(out)


def tests():
    tests = sorted((ROOT / "tests").glob("*.java"))
    if not tests: print("[4] тестов нет"); return
    out_dir = BUILD / "tests"
    # resources/ в classpath тестов: туда же попадают шаблоны, которых нет в jar (JAR_EXCLUDE)
    cp = os.pathsep.join([str(BUILD / "classes"), str(BUILD / "stubout"), str(ROOT / "resources")])
    javac(out_dir, tests, cp)
    for t in tests:
        pkg = re.search(r"^package\s+([\w.]+);", t.read_text(encoding="utf-8"), re.M)
        cls = (pkg.group(1) + "." if pkg else "") + t.stem
        code, out = run(["java", "-cp", os.pathsep.join([str(out_dir), cp]), cls])
        print(f"[4] {t.stem}: {'OK' if code == 0 else 'FAIL'}" + ("" if code == 0 else "\n" + out))
        if code != 0: sys.exit(1)
        last = [l for l in out.splitlines() if l.strip()]
        if last: print("    " + last[-1])


# тестовый стенд механик (93 КБ) нужен только тестам, в игре его никто не грузит
JAR_EXCLUDE = {"data/explosionwand/structures/mech_test.nbt"}


def package():
    v = version()
    DIST.mkdir(exist_ok=True)
    jar = DIST / f"explosionwand-1.20.1-{v}.jar"
    manifest = ("Manifest-Version: 1.0\nSpecification-Title: explosionwand\nSpecification-Version: 1\n"
                f"Implementation-Title: Magic Wands\nImplementation-Version: {v}\n\n")
    with zipfile.ZipFile(jar, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("META-INF/MANIFEST.MF", manifest)
        for f in sorted((BUILD / "classes").rglob("*.class")): z.write(f, f.relative_to(BUILD / "classes").as_posix())
        for f in sorted((ROOT / "resources").rglob("*")):
            rel = f.relative_to(ROOT / "resources").as_posix()
            if f.is_file() and rel not in JAR_EXCLUDE: z.write(f, rel)
    print(f"[5] jar: {jar.relative_to(ROOT)} ({jar.stat().st_size // 1024} КБ)")
    return jar


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--install", action="store_true", help="скопировать jar в ~/Downloads")
    ap.add_argument("--fast", action="store_true", help="без проверок и тестов")
    a = ap.parse_args()
    compile_all()
    if not a.fast:
        check_srg(); check_ids(); check_dungeons(); tests()
    jar = package()
    if a.install:
        dst = Path.home() / "Downloads" / jar.name
        shutil.copy2(jar, dst); print(f"    скопирован в {dst}")
