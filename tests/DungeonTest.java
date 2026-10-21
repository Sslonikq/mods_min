package com.explosionwand;

import com.explosionwand.DungeonTemplate.Mark;

import java.util.*;

/**
 * Конвейер данжей (фаза 4.1), чистая логика на заглушках:
 *  - шаблоны .nbt читаются, части не пересекаются, метки на месте;
 *  - Разлом «честно проходится»: пешеходный поиск от старта по этапам (дверь, ступени, зеркала, платформы, рычаги) до всех залов и логова, без этапа — нет;
 *  - упавший в провал выбирается обратно по пандусу;
 *  - расстановка по сетке детерминирована и укладывается в клетку;
 *  - составной данж в 250 блоков собирается из 4 частей, части ставятся лениво по мере приближения игрока.
 */
public class DungeonTest {
    static int fails = 0;

    static void check(boolean ok, String msg) { if (!ok) { fails++; System.out.println("FAIL: " + msg); } }

    // ---------- пешеходная модель ----------
    static Map<Long, String> world;

    static boolean passable(String s) { return s.startsWith("minecraft:air") || (s.contains("lantern") && !s.contains("sea_lantern")) || s.contains("amethyst_cluster") || s.contains("campfire") || s.equals("minecraft:water") || s.startsWith("minecraft:lever"); }

    /** Не заданные клетки — плоская земля: ниже y=0 твёрдо, выше воздух. */
    static boolean solid(int x, int y, int z) {
        String s = world.get(Dungeons.pack(x, y, z));
        return s == null ? y < 0 : !passable(s);
    }

    static boolean free(int x, int y, int z) { return !solid(x, y, z); }
    /** Для полёта: решётка без соединений — лишь столбик 2/16, между столбиками проходит игрок. */
    static boolean open(int x, int y, int z) { String s = world.get(Dungeons.pack(x, y, z)); return free(x, y, z) || (s != null && s.startsWith("minecraft:iron_bars") && !s.contains("=true")); }
    /** Поверхность бесконечна, поэтому поиск ограничен окрестностью данжа. */
    static int[] BOX = {-30, 30, -30, 110, -45, 12};   // x0, x1, z0, z1, y0, y1
    static boolean inBox(int x, int y, int z) { return x >= BOX[0] && x <= BOX[1] && z >= BOX[2] && z <= BOX[3] && y >= BOX[4] && y <= BOX[5]; }
    static boolean standable(int x, int y, int z) { return inBox(x, y, z) && free(x, y, z) && free(x, y + 1, z) && solid(x, y - 1, z); }

    static Set<Long> reach(int sx, int sy, int sz) {
        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(new int[]{sx, sy, sz}); seen.add(Dungeons.pack(sx, sy, sz));
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] c = q.poll();
            for (int[] d : dirs) {
                int nx = c[0] + d[0], nz = c[2] + d[1];
                for (int dy = 1; dy >= -3; dy--) {          // шаг вверх на 1, спуск до 3
                    int ny = c[1] + dy;
                    if (!standable(nx, ny, nz)) continue;
                    if (dy == 1 && !free(c[0], c[1] + 2, c[2])) break;
                    if (dy < 1 && !(free(nx, c[1], nz) && free(nx, c[1] + 1, nz))) break;
                    if (seen.add(Dungeons.pack(nx, ny, nz))) q.add(new int[]{nx, ny, nz});
                    break;
                }
                // прыжок через одну яму: клетка рядом — пустая колонна, приземление на расстоянии 2 не выше
                int mx = c[0] + d[0], mz = c[2] + d[1], jx = c[0] + 2 * d[0], jz = c[2] + 2 * d[1];
                if (free(mx, c[1], mz) && free(mx, c[1] + 1, mz) && free(mx, c[1] + 2, mz) && free(c[0], c[1] + 2, c[2]) && !standable(mx, c[1], mz)) {
                    for (int dy = 0; dy >= -2; dy--) {
                        int ny = c[1] + dy;
                        if (standable(jx, ny, jz) && free(jx, c[1], jz) && free(jx, c[1] + 1, jz) && free(jx, c[1] + 2, jz)) {
                            if (seen.add(Dungeons.pack(jx, ny, jz))) q.add(new int[]{jx, ny, jz});
                            break;
                        }
                    }
                }
            }
        }
        return seen;
    }

    static boolean reachNear(Set<Long> seen, int gx, int gy, int gz) {
        for (int x = gx - 2; x <= gx + 2; x++) for (int z = gz - 2; z <= gz + 2; z++) for (int y = gy - 2; y <= gy + 1; y++)
            if (seen.contains(Dungeons.pack(x, y, z))) return true;
        return false;
    }

    // ================================================================ Разлом (настоящий данж)
    static final Set<String> LIGHT = Set.of("shroomlight", "sea_lantern", "lantern", "crying_obsidian", "glowstone", "soul_campfire", "soul_lantern", "campfire", "lava");

    static String name(String st) { int i = st.indexOf('['); return (i < 0 ? st : st.substring(0, i)).replace("minecraft:", ""); }

    static void fill(Mark m, String blk) {
        for (int x = 0; x < m.sx; x++) for (int y = 0; y < m.sy; y++) for (int z = 0; z < m.sz; z++) world.put(Dungeons.pack(m.x + x, m.y + y, m.z + z), blk);
    }

    static Mark spawn(Dungeons.Def d, int hall, int i) {
        for (Dungeons.Hall h : d.halls()) if (h.id == hall) return h.spawns.get(i);
        throw new IllegalStateException("нет зала " + hall);
    }

    static boolean at(Set<Long> r, Mark m) { return r.contains(Dungeons.pack(m.x, m.y, m.z)); }

    static boolean hallReached(Dungeons.Def d, Set<Long> r, int hall) {
        for (Dungeons.Hall h : d.halls()) {
            if (h.id != hall) continue;
            for (Mark m : h.spawns) if (at(r, m)) return true;
            if (h.chest != null && reachNear(r, h.chest.x, h.chest.y, h.chest.z)) return true;
        }
        return false;
    }

    static void rift() throws Exception {
        Dungeons.Def d = Dungeons.RIFT;
        List<DungeonTemplate> tp = d.templates();
        check(tp.size() == 3, "три части Разлома");
        // ---- шаблоны, габариты, пересечения
        Set<Long> seen0 = new HashSet<>();
        int total = 0;
        for (DungeonTemplate t : tp) {
            total += t.count();
            check(t.name.startsWith("rift_") && t.count() > 10000, t.name + ": блоков " + t.count());
            for (int i = 0; i < t.count(); i++) {
                if (t.bx[i] < 0 || t.bx[i] >= t.sx || t.by[i] < 0 || t.by[i] >= t.sy || t.bz[i] < 0 || t.bz[i] >= t.sz) { check(false, t.name + ": блок вне size"); break; }
                if (t.bs[i] < 0 || t.bs[i] >= t.palette.length) { check(false, t.name + ": индекс палитры"); break; }
                if (!seen0.add(Dungeons.pack(t.ox + t.bx[i], t.oy + t.by[i], t.oz + t.bz[i]))) { check(false, t.name + ": пересечение частей"); break; }
            }
            for (String p : t.palette) check(p.startsWith("minecraft:"), "палитра: " + p);
        }
        int[] b = d.bounds();
        int w = b[3] - b[0], dep = b[5] - b[2], hgt = b[4] - b[1];
        System.out.println("разлом: " + total + " блоков в 3 частях, габариты " + w + "x" + hgt + "x" + dep);
        check(w >= 150 && w <= 200 && dep >= 150 && dep <= 205 && hgt >= 55, "габариты Разлома ~200x200, 3 уровня: " + w + "x" + hgt + "x" + dep);
        check(Dungeons.RIFT.margin * 2 - 96 >= Math.max(w, dep), "соседние Разломы (с запасными точками ±48) не пересекаются");

        // ---- метки
        check(d.doors().size() == 1 && d.doors().get(0).a.equals("rift") && d.start() != null, "дверь rift и старт");
        check(d.braziers().size() == 4, "четыре жаровни-якоря: " + d.braziers().size());
        check(d.marks("steps").size() == 3 && d.marks("platform").size() == 2 && d.marks("seal").size() == 2, "ступени, платформы, печати");
        check(d.marks("beams").size() >= 30 && d.marks("lever").size() == 3 && d.marks("secret").size() == 4, "лучи, рычаги, секреты: " + d.marks("beams").size());
        Set<Integer> ids = new TreeSet<>();
        int chests = 0, spawns = 0;
        Set<String> chestNames = new HashSet<>();
        for (Dungeons.Hall h : d.halls()) {
            check(ids.add(h.id) && h.id >= 1 && h.id <= 30, "номер зала " + h.id);
            spawns += h.spawns.size();
            if (h.id <= 13 && h.boss == null) check(h.spawns.size() >= 4, "в зале " + h.id + " мобов: " + h.spawns.size());
            if (h.chest != null) {
                chests++;
                String[] sp = Dungeons.chestSpec(h.chest.a);
                check(!sp[0].isEmpty() && chestNames.add(sp[0]) && Integer.parseInt(sp[1]) >= 1 && Integer.parseInt(sp[1]) <= 3, "название сундука: " + h.chest.a);
            }
        }
        check(ids.equals(new TreeSet<>(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16))), "залы 1..16: " + ids);
        check(chests == 12 && spawns == 77, "сундуков " + chests + ", мобов " + spawns);
        for (Dungeons.Hall h : d.halls()) {
            if (h.id == 7) check(h.chest != null && h.boss != null && h.spawns.isEmpty() && h.boss.a.equals("rift_heart"), "логово: сундук и босс, обычных мобов нет");
            else check(h.boss == null, "босс только в логове: зал " + h.id);
        }
        // залы не пересекаются: метка spawn/chest относится ровно к одному залу
        for (Dungeons.Hall x : d.halls()) for (Dungeons.Hall y : d.halls()) {
            if (x.id >= y.id) continue;
            boolean ov = x.x < y.x + y.sx && y.x < x.x + x.sx && x.y < y.y + y.sy + 1 && y.y < x.y + x.sy + 1 && x.z < y.z + y.sz && y.z < x.z + x.sz;
            check(!ov, "залы " + x.id + " и " + y.id + " пересекаются");
        }
        // свет в каждом зале
        world = Dungeons.assemble(d, 0, 0, 0);
        Map<Integer, Integer> lights = new HashMap<>();
        for (Map.Entry<Long, String> e : world.entrySet()) {
            if (!LIGHT.contains(name(e.getValue()))) continue;
            long k = e.getKey();
            int x = (int) (k >> 38), y = (int) (k & 0xFFF) - 2048, z = (int) ((k >> 12) & 0x3FFFFFF);
            if (x >= 1 << 25) x -= 1 << 26;
            if (z >= 1 << 25) z -= 1 << 26;
            for (Dungeons.Hall h : d.halls()) if (x >= h.x - 1 && x < h.x + h.sx + 1 && y >= h.y && y <= h.y + h.sy + 1 && z >= h.z - 1 && z < h.z + h.sz + 1) lights.merge(h.id, 1, Integer::sum);
        }
        for (Dungeons.Hall h : d.halls()) check(lights.getOrDefault(h.id, 0) > 0, "в зале " + h.id + " нет света");

        // ---- прохождение: данж собран в мир (земля плоская, ниже y=0 порода); состояния открываются по этапам
        int[] saveBox = BOX;
        BOX = new int[]{-100, 100, -40, 190, -70, 12};
        Mark door = d.doors().get(0), st = d.start();
        check(standable(st.x, st.y, st.z), "старт — место, где можно стоять");
        Set<Long> r = reach(st.x, st.y, st.z);
        check(!hallReached(d, r, 1), "при закрытой двери зал 1 недостижим");
        fill(door, "minecraft:air");
        r = reach(st.x, st.y, st.z);
        check(hallReached(d, r, 1) && at(r, spawn(d, 1, 0)) && at(r, spawn(d, 1, 1)), "с открытой дверью достижим северный берег зала 1");
        check(!at(r, spawn(d, 1, 2)) && !hallReached(d, r, 2), "без ступеней провал зала 1 непроходим (иначе это не ловушка)");
        // упавший в провал возвращается по пандусу на северный берег
        Mark bank = spawn(d, 1, 0);
        Set<Long> rp = reach(0, bank.y - 6, 45);
        check(rp.contains(Dungeons.pack(bank.x, bank.y, bank.z)), "упавший в провал зала 1 выбирается по пандусу");
        for (Mark m : d.marks("steps")) fill(m, m.a);
        r = reach(st.x, st.y, st.z);
        for (int h : new int[]{1, 2, 3, 4, 8, 9, 10, 11, 12}) check(hallReached(d, r, h), "со ступенями достижим зал " + h);
        check(at(r, spawn(d, 1, 2)) && at(r, spawn(d, 1, 3)), "южный берег зала 1 достижим по ступеням");
        for (int h : new int[]{5, 6, 7, 13, 14, 15, 16}) check(!hallReached(d, r, h), "до зеркал/платформ/рычагов зал " + h + " недостижим");
        // лучи: плитки обходятся (в зале 3 всегда есть свободная дорожка)
        Map<Long, String> keep = new HashMap<>(world);
        for (Mark m : d.marks("beams")) world.put(Dungeons.pack(m.x, m.y + 1, m.z), "minecraft:stone");
        int stand3 = d.marks("beams").get(0).y + 1;
        Set<Long> rb = reach(st.x, st.y, st.z);
        check(rb.contains(Dungeons.pack(-30, stand3, 167)), "обход плиток: из зала 3 на запад (к залу 4) пройти можно, не наступая на плитки");
        check(rb.contains(Dungeons.pack(30, stand3, 167)), "обход плиток: из зала 3 на восток (к складу) пройти можно, не наступая на плитки");
        world = keep;
        // зеркала: головоломка refl открывает дверь на мост
        DungeonMech.Puzzle pz = DungeonMech.puzzles(d).get("refl");
        check(DungeonMech.puzzles(d).size() == 1 && pz != null && pz.srcs.size() == 1 && pz.mirs.size() == 3 && pz.rcvs.size() == 1, "головоломка refl: излучатель, 3 зеркала, приёмник");
        Map<Long, String> pw = Dungeons.assemble(d, 0, 0, 0);
        DungeonMech.Solid wall = (x, y, z) -> { String v = pw.get(Dungeons.pack(x, y, z)); return v == null ? y < 0 : !v.equals("minecraft:air"); };
        DungeonData.Rec rec = new DungeonData.Rec("rift_0_0", "rift", 0, 0, 0, 0, 0);
        check(!DungeonMech.solve(rec, pz, wall, new DungeonMech.Trace()), "в начале зеркала не решены");
        int solutions = 0;
        for (int c = 0; c < 64; c++) {
            rec.mirrors.put(DungeonMech.mirKey(pz.mirs.get(0)), c & 3);
            rec.mirrors.put(DungeonMech.mirKey(pz.mirs.get(1)), (c >> 2) & 3);
            rec.mirrors.put(DungeonMech.mirKey(pz.mirs.get(2)), (c >> 4) & 3);
            if (DungeonMech.solve(rec, pz, wall, new DungeonMech.Trace())) solutions++;
        }
        check(solutions == 1, "у головоломки ровно одно решение (" + solutions + ")");
        rec.mirrors.clear();
        for (Mark m : d.marks("secret")) if (m.a.equals("refl")) fill(m, "minecraft:air");
        r = reach(st.x, st.y, st.z);
        check(at(r, spawn(d, 5, 0)) && at(r, spawn(d, 5, 1)), "после зеркал достижим западный берег зала 5");
        check(!at(r, spawn(d, 5, 2)) && !at(r, spawn(d, 5, 4)) && !hallReached(d, r, 6) && !hallReached(d, r, 7), "без платформ остров, восточный берег, зал 6 и логово недостижимы");
        // платформы: стыкуются с берегами в крайних положениях и не соединяют их
        for (Mark pl : d.marks("platform")) {
            int[] cfg = DungeonMech.platCfg(pl.a);
            check(cfg != null && DungeonMech.platBlock(pl.a).equals("crying_obsidian"), "разбор платформы");
            for (int o = 0; o <= cfg[2]; o++) for (int x = 0; x < pl.sx; x++) for (int z = 0; z < pl.sz; z++) check(!solid(pl.x + cfg[0] * o + x, pl.y, pl.z + cfg[1] * o + z), "путь платформы свободен на шаге " + o);
            int sy = pl.y + 1;
            int farX = pl.x + cfg[0] * (cfg[2] + pl.sx), nearX = pl.x - 1;
            check(standable(nearX, sy, pl.z) && standable(farX, sy, pl.z), "берега платформы: " + pl.x);
            for (int off : new int[]{0, cfg[2]}) {
                Map<Long, String> save = new HashMap<>(world);
                for (int x = 0; x < pl.sx; x++) for (int z = 0; z < pl.sz; z++) world.put(Dungeons.pack(pl.x + cfg[0] * off + x, pl.y, pl.z + z), "minecraft:crying_obsidian");
                Set<Long> rr = reach(off == 0 ? nearX : farX, sy, pl.z);
                check(rr.contains(Dungeons.pack(pl.x + cfg[0] * off, sy, pl.z)), "платформа " + pl.x + " в положении " + off + " стыкуется с берегом");
                check(!rr.contains(Dungeons.pack(off == 0 ? farX : nearX, sy, pl.z)), "платформа " + pl.x + " в положении " + off + " не соединяет берега");
                world = save;
            }
        }
        for (Mark pl : d.marks("platform")) {          // дальше считаем платформы мостом
            int[] cfg = DungeonMech.platCfg(pl.a);
            for (int o = 0; o <= cfg[2]; o++) for (int x = 0; x < pl.sx; x++) for (int z = 0; z < pl.sz; z++) world.put(Dungeons.pack(pl.x + cfg[0] * o + x, pl.y, pl.z + z), "minecraft:crying_obsidian");
        }
        r = reach(st.x, st.y, st.z);
        for (int h : new int[]{5, 6, 7, 13}) check(hallReached(d, r, h), "с платформами достижим зал " + h);
        for (int h : new int[]{14, 15, 16}) check(!hallReached(d, r, h), "секретный зал " + h + " закрыт до рычага");
        for (Mark m : d.marks("secret")) if (!m.a.equals("refl")) fill(m, "minecraft:air");
        r = reach(st.x, st.y, st.z);
        for (Dungeons.Hall h : d.halls()) {
            check(hallReached(d, r, h.id), "зал " + h.id + " достижим");
            for (Mark m : h.spawns) check(at(r, m), "точка моба недостижима: зал " + h.id + " " + m.a + " " + m.x + "," + m.y + "," + m.z);
            if (h.chest != null) check(reachNear(r, h.chest.x, h.chest.y, h.chest.z), "сундук недостижим: зал " + h.id);
            if (h.boss != null) check(at(r, h.boss), "площадка босса достижима: зал " + h.id);
        }
        for (Mark br : d.braziers()) {
            String[] o = br.a.split(",");
            check(r.contains(Dungeons.pack(br.x + Integer.parseInt(o[0]), br.y + Integer.parseInt(o[1]), br.z + Integer.parseInt(o[2]))), "точка возрождения жаровни недостижима: " + br.x + "," + br.y + "," + br.z);
            check(world.get(Dungeons.pack(br.x, br.y, br.z)).contains("campfire"), "на метке жаровни костёр");
        }
        // рычаги и секреты: id согласованы, рычаги на метках
        Set<String> lev = new HashSet<>();
        for (Mark m : d.marks("lever")) { lev.add(m.a); check(world.get(Dungeons.pack(m.x, m.y, m.z)).startsWith("minecraft:lever"), "рычаг " + m.a); }
        for (Mark m : d.marks("secret")) check(lev.contains(m.a) || m.a.equals("refl"), "у секрета " + m.a + " есть рычаг или головоломка");
        // печати: проём открыт в шаблоне, зал есть, дверь вне своего зала; закрытая печать отрезает зал
        Set<Integer> sealed = new HashSet<>();
        for (Mark m : d.marks("seal")) {
            int hid = Integer.parseInt(m.a.split(",")[0]);
            sealed.add(hid);
            Dungeons.Hall hh = null;
            for (Dungeons.Hall h : d.halls()) if (h.id == hid) hh = h;
            check(hh != null && !hh.contains(m.x, m.y, m.z) && !hh.contains(m.x + m.sx - 1, m.y + m.sy - 1, m.z + m.sz - 1), "дверь печати вне зала " + hid);
            for (int x = 0; x < m.sx; x++) for (int y = 0; y < m.sy; y++) for (int z = 0; z < m.sz; z++) check(pw.get(Dungeons.pack(m.x + x, m.y + y, m.z + z)).equals("minecraft:air"), "проём печати открыт в шаблоне");
            Map<Long, String> save = new HashMap<>(world);
            fill(m, "minecraft:polished_blackstone_bricks");
            check(!hallReached(d, reach(st.x, st.y, st.z), hid), "закрытая печать отрезает зал " + hid);
            world = save;
        }
        check(sealed.equals(Set.of(6, 7)), "печати у залов 6 и 7: " + sealed);
        // лучи: свободны и стыкуются (общая проверка для стенда и Разлома)
        mw = pw;
        beams(d);
        // сдвиг и границы
        int ox = 1234, oy = 70, oz = -987;
        Map<Long, String> shifted = Dungeons.assemble(d, ox, oy, oz);
        check(shifted.size() == pw.size(), "сдвиг не меняет число блоков");
        check(shifted.containsKey(Dungeons.pack(ox + door.x, oy + door.y, oz + door.z)), "дверь на месте после сдвига");
        BOX = saveBox;
        System.out.println("разлом ok: " + total + " блоков, залов " + ids.size() + ", мобов " + spawns + ", сундуков " + chests);
    }

    // ================================================================ Город Спящих (4.5)
        /** Зал достижим: хотя бы одна точка моба или клетка сундука достижима точно (в городе стены тонкие: «рядом» не годится). */
    static boolean hallExact(Dungeons.Def d, Set<Long> r, int hall) {
        Dungeons.Hall h = d.hall(hall);
        for (Mark m : h.spawns) if (at(r, m)) return true;
        return h.chest != null && r.contains(Dungeons.pack(h.chest.x, h.chest.y, h.chest.z));
    }

    static void city() throws Exception {
        Dungeons.Def d = Dungeons.SLEEP;
        List<DungeonTemplate> tp = d.templates();
        check(tp.size() == 3 && d.layout == 3 && d.signature().equals("sleep_north+sleep_mid+sleep_south:v3"), "три части города, раскладка 3 (подземная арена Монолита): " + d.signature());
        Set<Long> seen0 = new HashSet<>();
        int total = 0;
        for (DungeonTemplate t : tp) {
            total += t.count();
            check(t.name.startsWith("sleep_") && t.count() > 10000, t.name + ": блоков " + t.count());
            for (int i = 0; i < t.count(); i++) {
                if (t.bx[i] < 0 || t.bx[i] >= t.sx || t.by[i] < 0 || t.by[i] >= t.sy || t.bz[i] < 0 || t.bz[i] >= t.sz) { check(false, t.name + ": блок вне size"); break; }
                if (!seen0.add(Dungeons.pack(t.ox + t.bx[i], t.oy + t.by[i], t.oz + t.bz[i]))) { check(false, t.name + ": пересечение частей"); break; }
            }
            for (String p : t.palette) check(p.startsWith("minecraft:"), "палитра: " + p);
        }
        int[] b = d.bounds();
        int w = b[3] - b[0], dep = b[5] - b[2];
        System.out.println("город: " + total + " блоков в 3 частях, габариты " + w + "x" + (b[4] - b[1]) + "x" + dep);
        check(w >= 90 && w <= 100 && dep >= 90 && dep <= 100 && b[4] >= 60, "габариты города ~97x97, шпиль >= 60: " + w + "x" + dep + ", верх " + b[4]);
        check(Dungeons.SLEEP.margin * 2 - 96 >= Math.max(w, dep), "соседние города (с запасными точками ±48) не пересекаются");
        check(d.avoid == Dungeons.RIFT && d.flat > 0 && d.maxVar >= d.flat, "наземный данж обходит Разломы и проверяет ровность");

        // ---- метки
        Mark gate = null, temple = null;
        for (Mark m : d.doors()) { if (m.a.equals("sleep")) gate = m; else if (m.a.equals("sleep_temple")) temple = m; }
        check(d.doors().size() == 2 && gate != null && temple != null && d.start() != null, "ворота (sleep), дверь храма (sleep_temple) и старт");
        check(d.braziers().size() == 5, "пять жаровен (четыре в городе и одна перед ареной): " + d.braziers().size());
        check(d.marks("seal").size() == 2 && d.marks("lever").size() == 3 && d.marks("secret").size() == 3 && d.marks("platform").isEmpty()
                && d.marks("steps").isEmpty() && d.marks("beams").isEmpty() && d.marks("mirror").isEmpty(), "печати, рычаги, секреты");
        Set<Integer> ids = new TreeSet<>();
        int chests = 0, spawns = 0, secretChests = 0;
        Set<String> chestNames = new HashSet<>();
        for (Dungeons.Hall h : d.halls()) {
            check(ids.add(h.id) && h.id >= 1 && h.id <= 30, "номер зала " + h.id);
            spawns += h.spawns.size();
            check(h.boss == null || (h.id == 20 && h.boss.a.equals("monolith")), "босс в городе только Монолит в логове храма (зал 20): зал " + h.id);
            for (Mark m : h.spawns) check(RiftMobs.kindOfMark(m.a) != null, "моб Города известен (настоящий, не заглушка): " + m.a);
            if (h.chest != null) {
                chests++;
                String[] sp = Dungeons.chestSpec(h.chest.a);
                check(!sp[0].isEmpty() && chestNames.add(sp[0]), "название сундука: " + h.chest.a);
                if (h.id >= 23) { secretChests++; check(sp[1].equals("3") && h.spawns.isEmpty(), "тайник: тир 3, без мобов: зал " + h.id); }
            }
            if (h.id <= 22 && h.boss == null) check(h.spawns.size() >= 3, "в зале " + h.id + " мобов: " + h.spawns.size());
        }
        Set<Integer> want = new TreeSet<>();
        for (int i = 1; i <= 25; i++) want.add(i);
        check(ids.equals(want), "залы 1..25: " + ids);
        check(secretChests == 3 && chests >= 14 && spawns >= 70 && spawns <= 130, "сундуков " + chests + ", мобов " + spawns);
        for (Dungeons.Hall x : d.halls()) for (Dungeons.Hall y : d.halls()) {
            if (x.id >= y.id) continue;
            boolean ov = x.x < y.x + y.sx && y.x < x.x + x.sx && x.y < y.y + y.sy + 1 && y.y < x.y + x.sy + 1 && x.z < y.z + y.sz && y.z < x.z + x.sz;
            check(!ov, "залы " + x.id + " и " + y.id + " пересекаются");
        }
        check(d.sealed(19) && d.sealed(20) && !d.sealed(1) && !d.sealed(21), "печати у библиотеки (19) и арены Монолита (20)");
        check(d.hall(20) != null && d.hall(20).chest != null && d.hall(20).spawns.isEmpty() && d.hall(20).boss != null, "логово храма: босс Монолит и сундук, обычных мобов нет (4.7)");
        check(Dungeons.chestSpec(d.hall(21).chest.a)[0].equals("Склеп Спящих") && City.extra(21).equals(List.of("explosionwand:temple_seal")), "Печать Храма в сундуке склепа");
        check(!City.extra(19).isEmpty() && !City.extra(25).isEmpty() && City.extra(7).isEmpty() && Dungeons.lootFor(d, d.hall(21), 2).contains("explosionwand:temple_seal")
                && !Dungeons.lootFor(Dungeons.RIFT, Dungeons.RIFT.hall(7), 2).contains("explosionwand:temple_seal"), "особая добыча только у сундуков города");

        // ---- мир: стена сплошная, шпиль, свет в домах
        world = Dungeons.assemble(d, 0, 0, 0);
        int maxY = Integer.MIN_VALUE;
        for (long k : world.keySet()) maxY = Math.max(maxY, (int) (k & 0xFFF) - 2048);
        check(maxY >= 60 && world.get(Dungeons.pack(0, 60, -15)).equals("minecraft:sea_lantern"), "шпиль Монолита над храмом: верх " + maxY);
        int holes = 0;
        for (int x = -45; x <= 45; x++) for (int z = -45; z <= 45; z++) {
            if (Math.max(Math.abs(x), Math.abs(z)) < 43) continue;
            for (int y = 0; y <= 6; y++) { String v = world.get(Dungeons.pack(x, y, z)); if (v == null || v.equals("minecraft:air")) holes++; }
        }
        check(holes == 0, "стена без дыр (ворота закрыты): клеток " + holes);
        Map<Integer, Integer> lights = new HashMap<>();
        for (Map.Entry<Long, String> e : world.entrySet()) {
            if (!LIGHT.contains(name(e.getValue()))) continue;
            long k = e.getKey();
            int x = (int) (k >> 38), y = (int) (k & 0xFFF) - 2048, z = (int) ((k >> 12) & 0x3FFFFFF);
            if (x >= 1 << 25) x -= 1 << 26;
            if (z >= 1 << 25) z -= 1 << 26;
            for (Dungeons.Hall h : d.halls()) if (x >= h.x - 1 && x < h.x + h.sx + 1 && y >= h.y && y <= h.y + h.sy + 1 && z >= h.z - 1 && z < h.z + h.sz + 1) lights.merge(h.id, 1, Integer::sum);
        }
        for (Dungeons.Hall h : d.halls()) if (h.id >= 7) check(lights.getOrDefault(h.id, 0) > 0, "в зале " + h.id + " нет света");
        // эмблемы: лунный знак (светящиеся блоки на мостовой) только у двери дома со склепом (W3: улица x -29..-25, z 4..8)
        int moon = 0, other = 0;
        for (int x = -29; x <= -25; x++) for (int z = 4; z <= 8; z++) if (world.get(Dungeons.pack(x, -1, z)).equals("minecraft:sea_lantern")) moon++;
        for (int x = -20; x <= -16; x++) for (int z = -29; z <= -25; z++) if (world.get(Dungeons.pack(x, -1, z)).equals("minecraft:sea_lantern")) other++;
        check(moon >= 5 && other == 0, "лунный знак у дома со склепом: " + moon + " светящихся, у соседа " + other);

        // ---- прохождение по этапам
        int[] saveBox = BOX;
        BOX = new int[]{-60, 60, -60, 62, -40, 14};
        Mark st = d.start();
        check(standable(st.x, st.y, st.z), "старт у ворот — место, где можно стоять");
        Set<Long> r = reach(st.x, st.y, st.z);
        for (Dungeons.Hall h : d.halls()) check(!hallExact(d, r, h.id), "до открытия ворот зал " + h.id + " недостижим");
        fill(gate, "minecraft:air");
        r = reach(st.x, st.y, st.z);
        for (int h = 1; h <= 19; h++) check(hallExact(d, r, h), "после ворот достижим зал " + h);
        check(hallExact(d, r, 21) && hallExact(d, r, 22), "склеп (спуск из дома) и обсерватория достижимы без секретов");
        for (int h : new int[]{20, 23, 24, 25}) check(!hallExact(d, r, h), "зал " + h + " закрыт: дверь храма / тайники");
        for (Dungeons.Hall h : d.halls()) {
            if (h.id == 20 || h.id >= 23) continue;
            for (Mark m : h.spawns) check(at(r, m), "точка моба недостижима: зал " + h.id + " " + m.a + " " + m.x + "," + m.y + "," + m.z);
            if (h.chest != null) check(r.contains(Dungeons.pack(h.chest.x, h.chest.y, h.chest.z)), "сундук недостижим: зал " + h.id);
        }
        // печать библиотеки отрезает зал; рычаги в домах и обсерватории достижимы до тайников
        Map<Long, String> pw = Dungeons.assemble(d, 0, 0, 0);
        for (Mark m : d.marks("seal")) {
            int hid = Integer.parseInt(m.a.split(",")[0]);
            Dungeons.Hall hh = d.hall(hid);
            check(hh != null && !hh.contains(m.x, m.y, m.z) && !hh.contains(m.x + m.sx - 1, m.y + m.sy - 1, m.z + m.sz - 1), "дверь печати вне зала " + hid);
            for (int x = 0; x < m.sx; x++) for (int y = 0; y < m.sy; y++) for (int z = 0; z < m.sz; z++) check(pw.get(Dungeons.pack(m.x + x, m.y + y, m.z + z)).equals("minecraft:air"), "проём печати открыт в шаблоне");
            if (hid == 19) {
                Map<Long, String> save = new HashMap<>(world);
                fill(m, "minecraft:polished_blackstone_bricks");
                check(!hallExact(d, reach(st.x, st.y, st.z), hid), "закрытая печать отрезает библиотеку");
                world = save;
            }
        }
        for (Mark m : d.marks("lever")) check(reachNear(r, m.x, m.y, m.z), "рычаг достижим: " + m.a);
        // дверь храма: до Печати логово недостижимо, после — достижимо; печать логова отрезает его
        fill(temple, "minecraft:air");
        r = reach(st.x, st.y, st.z);
        check(hallExact(d, r, 20), "с открытой дверью храма достижимо логово");
        for (Mark br : d.braziers()) {
            String[] o = br.a.split(",");
            check(r.contains(Dungeons.pack(br.x + Integer.parseInt(o[0]), br.y + Integer.parseInt(o[1]), br.z + Integer.parseInt(o[2]))), "точка возрождения жаровни (после двери храма) недостижима: " + br.x + "," + br.y + "," + br.z);
            check(world.get(Dungeons.pack(br.x, br.y, br.z)).contains("campfire"), "на метке жаровни костёр");
        }
        Mark lb = d.hall(20).boss;
        check(lb != null && at(r, lb), "площадка босса логова достижима: " + lb);
        check(r.contains(Dungeons.pack(d.hall(20).chest.x, d.hall(20).chest.y, d.hall(20).chest.z)), "сундук логова достижим");
        for (Mark m : d.marks("seal")) if (m.a.startsWith("20,")) {
            Map<Long, String> save = new HashMap<>(world);
            fill(m, "minecraft:polished_blackstone_bricks");
            check(!hallExact(d, reach(st.x, st.y, st.z), 20), "закрытая печать отрезает логово храма");
            world = save;
        }
        // тайники
        for (Mark m : d.marks("secret")) fill(m, "minecraft:air");
        r = reach(st.x, st.y, st.z);
        for (int h : new int[]{23, 24, 25}) check(hallExact(d, r, h), "после рычага достижим тайник " + h);
        Set<String> lev = new HashSet<>();
        for (Mark m : d.marks("lever")) { lev.add(m.a); check(world.get(Dungeons.pack(m.x, m.y, m.z)).startsWith("minecraft:lever"), "рычаг " + m.a); }
        for (Mark m : d.marks("secret")) check(lev.contains(m.a), "у секрета " + m.a + " есть рычаг");
        // всё проходимое внутри стены (и ров) — внутри данжа: защита и счёт игрока
        Dungeons.Mask mk = d.mask();
        int walkOut = 0;
        for (long k : r) {
            int x = (int) (k >> 38), y = (int) (k & 0xFFF) - 2048, z = (int) ((k >> 12) & 0x3FFFFFF);
            if (x >= 1 << 25) x -= 1 << 26;
            if (z >= 1 << 25) z -= 1 << 26;
            if (Math.abs(x) <= 48 && Math.abs(z) <= 48 && y >= -40 && (!mk.has(x, y, z) || !mk.has(x, y + 1, z))) walkOut++;
        }
        check(walkOut == 0, "проходимые клетки города все в маске: вне " + walkOut);
        BOX = saveBox;
        System.out.println("город ok: " + total + " блоков, залов " + ids.size() + ", мобов " + spawns + ", сундуков " + chests + ", маска " + mk.count());
    }


    // ================================================================ Пепельное святилище (4.8, шаг 2а)
    /** Как reach, но прыжок через яму до maxGap клеток (сначала земля, потом платформы над лавой). */
    static Set<Long> reachG(int sx, int sy, int sz, int maxGap) {
        Set<Long> seen = reach(sx, sy, sz);
        ArrayDeque<int[]> q = new ArrayDeque<>();
        for (long k : seen) q.add(unpack(k));
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] c = q.poll();
            for (int[] d : dirs) {
                for (int gap = 1; gap <= maxGap; gap++) {
                    boolean clear = free(c[0], c[1] + 2, c[2]);
                    for (int i = 1; i <= gap && clear; i++) {
                        int mx = c[0] + d[0] * i, mz = c[2] + d[1] * i;
                        clear = free(mx, c[1], mz) && free(mx, c[1] + 1, mz) && free(mx, c[1] + 2, mz);
                    }
                    if (!clear) break;
                    int jx = c[0] + d[0] * (gap + 1), jz = c[2] + d[1] * (gap + 1);
                    for (int dy = 0; dy >= -2; dy--) {
                        int ny = c[1] + dy;
                        if (standable(jx, ny, jz) && free(jx, c[1], jz) && free(jx, c[1] + 1, jz) && free(jx, c[1] + 2, jz)) {
                            if (seen.add(Dungeons.pack(jx, ny, jz))) {
                                int[] n = {jx, ny, jz};
                                q.add(n);
                                for (long k : reach(jx, ny, jz)) if (seen.add(k)) q.add(unpack(k));   // дальше снова пешком
                            }
                            break;
                        }
                    }
                }
            }
        }
        return seen;
    }

    static int[] unpack(long k) {
        int x = (int) (k >> 38), y = (int) (k & 0xFFF) - 2048, z = (int) ((k >> 12) & 0x3FFFFFF);
        if (x >= 1 << 25) x -= 1 << 26;
        if (z >= 1 << 25) z -= 1 << 26;
        return new int[]{x, y, z};
    }

    static Mark markAt(Dungeons.Def d, String type, String a) {
        for (Mark m : d.marks(type)) if (m.a.equals(a)) return m;
        throw new IllegalStateException("нет метки " + type + " " + a);
    }

    static void ash() throws Exception {
        Dungeons.Def d = Dungeons.ASH;
        List<DungeonTemplate> tp = d.templates();
        check(tp.size() == 3 && d.layout == 1 && d.signature().equals("ash_north+ash_mid+ash_south:v1"), "три части Святилища, раскладка 1: " + d.signature());
        Set<Long> seen0 = new HashSet<>();
        int total = 0;
        for (DungeonTemplate t : tp) {
            total += t.count();
            check(t.name.startsWith("ash_") && t.count() > 10000, t.name + ": блоков " + t.count());
            for (int i = 0; i < t.count(); i++) {
                if (t.bx[i] < 0 || t.bx[i] >= t.sx || t.by[i] < 0 || t.by[i] >= t.sy || t.bz[i] < 0 || t.bz[i] >= t.sz) { check(false, t.name + ": блок вне size"); break; }
                if (!seen0.add(Dungeons.pack(t.ox + t.bx[i], t.oy + t.by[i], t.oz + t.bz[i]))) { check(false, t.name + ": пересечение частей"); break; }
            }
            check(t.sx <= 250 && t.sz <= 250, t.name + ": часть не больше 250 блоков в поперечнике");
            for (String p : t.palette) check(p.startsWith("minecraft:"), "палитра: " + p);
        }
        int[] b = d.bounds();
        int w = b[3] - b[0], dep = b[5] - b[2], hgt = b[4] - b[1];
        System.out.println("святилище: " + total + " блоков в 3 частях, габариты " + w + "x" + hgt + "x" + dep + ", y " + b[1] + ".." + (b[4] - 1));
        check(w >= 165 && dep >= 135 && b[4] >= 62 && b[1] <= -75 && hgt >= 135, "габариты ~173x142 после 2б, башня >= 62 вверх, Гнездо на y -75: " + w + "x" + hgt + "x" + dep);
        check(w <= 190 && dep <= 190, "рамка укладывается в запас клетки (margin*2-96 = 192): " + w + "x" + dep);
        check(Dungeons.ASH.margin * 2 - 96 >= Math.max(w, dep), "соседние святилища (с запасными точками ±48) не пересекаются");
        check(d.roof < 0 && d.maxVar == DungeonSite.COVER_MAX && d.avoidAll.contains(Dungeons.RIFT) && d.avoidAll.contains(Dungeons.SLEEP) && Dungeons.def("ash") == d, "подземно-вертикальный, обходит Разломы и Города, найден по id ash");

        // ---- метки
        Mark door = null, gate = null, ember = null;
        for (Mark m : d.doors()) { if (m.a.equals("ash")) door = m; else if (m.a.equals("ash_gate")) gate = m; else if (m.a.equals("ember_gate")) ember = m; }
        check(d.doors().size() == 3 && door != null && gate != null && ember != null && d.start() != null, "дверь башни (ash), дверь вниз (ash_gate), дверь Гнезда (ember_gate) и старт");
        check(d.braziers().size() == 6, "шесть жаровен-якорей: двор, Зал Пепла, посадка уровня 2, зал 17, тоннель, Гнездо: " + d.braziers().size());
        check(d.marks("bell").size() == 1 && d.marks("bell").get(0).a.equals("shaft") && d.marks("secret").size() == 7 && d.marks("lever").size() == 5, "колокол, секреты (7), рычаги (5)");
        check(d.marks("braziers").size() == 14 && DungeonMech.fireIds(d).equals(Set.of("fires")), "головоломка жаровен: 4 костра, 4 панели, 6 точек волны: " + d.marks("braziers").size());
        check(d.marks("jets").size() >= 15 && d.marks("crumble").size() >= 10 && d.marks("geyser").size() >= 6, "струи " + d.marks("jets").size() + ", платформы " + d.marks("crumble").size() + ", гейзеры " + d.marks("geyser").size());
        check(d.marks("steps").isEmpty() && d.marks("beams").isEmpty() && d.marks("boss").isEmpty(), "ступеней и лучей нет, босса нет (босс — 4.10, метка boss зарезервирована)");
        check(d.marks("seal").size() == 1 && d.marks("seal").get(0).a.startsWith("20,") && d.sealed(20) && !d.sealed(23), "печать только на арене Хранителя (зал 20)");
        for (Mark m : d.marks("jets")) check(DungeonMech.jetCfg(m.a) != null, "струя: разбор a " + m.a);
        for (Mark m : d.marks("geyser")) check(DungeonMech.geyserCfg(m.a) != null, "гейзер: разбор a " + m.a);
        Set<String> mobs = Set.of("husk", "skeleton", "blaze", "magma_cube", "zombie", "wither_skeleton");
        Set<Integer> ids = new TreeSet<>();
        int chests = 0, spawns = 0, secretChests = 0;
        Set<String> chestNames = new HashSet<>();
        for (Dungeons.Hall h : d.halls()) {
            check(ids.add(h.id) && h.id >= 1 && h.id <= 30, "номер зала " + h.id);
            spawns += h.spawns.size();
            check(h.boss == null, "босса в Святилище 4.8 нет: зал " + h.id);
            for (Mark m : h.spawns) check(mobs.contains(m.a), "моб-заглушка ванильный: " + m.a);
            if (h.chest != null) {
                chests++;
                String[] sp = Dungeons.chestSpec(h.chest.a);
                check(!sp[0].isEmpty() && chestNames.add(sp[0]), "название сундука: " + h.chest.a);
                if (h.id == 14 || h.id == 15 || h.id == 16 || h.id == 21) { secretChests++; check(sp[1].equals("3") && h.spawns.isEmpty(), "тайник: тир 3, без мобов: зал " + h.id); }
                else check(sp[1].equals("1") || sp[1].equals("2"), "сундук тира 1-2: зал " + h.id);
            }
            if (h.id <= 13 || h.id == 18 || h.id == 19 || h.id == 20) check(h.spawns.size() >= 2, "в зале " + h.id + " мобов: " + h.spawns.size());
            if (h.id == 17 || h.id == 23) check(h.spawns.isEmpty(), "зал " + h.id + ": мобы приходят волной (17) или не нужны (Гнездо до 4.10)");
        }
        check(ids.equals(new TreeSet<>(List.of(1, 2, 3, 4, 5, 6, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 23))), "залы 1-6, 8-21, 23 (7, 22, 24-30 свободны): " + ids);
        check(secretChests == 4 && chests == 16 && spawns >= 62, "сундуков " + chests + ", мобов " + spawns);
        Dungeons.Hall nest = d.hall(23);
        check(nest != null && nest.sx == 61 && nest.sz == 61 && nest.chest != null && nest.boss == null && Ash.GUARD_HALL == 20, "Гнездо: зал 61x61 с сундуком-заглушкой, босса нет");
        for (Dungeons.Hall x : d.halls()) for (Dungeons.Hall y : d.halls()) {
            if (x.id >= y.id) continue;
            boolean ov = x.x < y.x + y.sx && y.x < x.x + x.sx && x.y < y.y + y.sy + 1 && y.y < x.y + x.sy + 1 && x.z < y.z + y.sz && y.z < x.z + x.sz;
            check(!ov, "залы " + x.id + " и " + y.id + " пересекаются");
        }
        check(Ash.extra(9).equals(List.of("explosionwand:ash_key")) && Ash.extra(8).isEmpty() && Ash.extra(12).isEmpty()
                && Dungeons.lootFor(d, d.hall(9), 2).contains("explosionwand:ash_key") && !Dungeons.lootFor(d, d.hall(12), 2).contains("explosionwand:ash_key")
                && !Dungeons.lootFor(Dungeons.RIFT, Dungeons.RIFT.hall(2), 1).contains("explosionwand:ash_key"), "Пепельный ключ только в сундуке Кладовой (зал 9)");
        check(Ash.extra(20).equals(List.of("explosionwand:ember_key")) && Dungeons.lootFor(d, d.hall(20), 2).contains("explosionwand:ember_key") && !Dungeons.lootFor(d, d.hall(20), 2).contains("explosionwand:ash_key")
                && !Dungeons.lootFor(d, d.hall(9), 2).contains("explosionwand:ember_key") && !Dungeons.lootFor(d, d.hall(23), 2).contains("explosionwand:ember_key"), "Угольный ключ только в сундуке арены (зал 20)");
        // повторная выдача ключа: зал зачищен, дверь закрыта, ключа нет ни у игрока, ни в сундуке
        check(Ash.reissueWanted(true, true, false, false) && !Ash.reissueWanted(false, true, false, false) && !Ash.reissueWanted(true, false, false, false)
                && !Ash.reissueWanted(true, true, true, false) && !Ash.reissueWanted(true, true, false, true), "ключ выдаётся заново только когда потерян (иначе сундук открывается как обычно)");
        // элита арены: усиление только у wither_skeleton зала 20
        String eg = Ash.gear(20, "wither_skeleton");
        check(eg.startsWith(",") && eg.contains("generic.max_health") && eg.contains("explosionwand.ash.guard") && Ash.gear(20, "husk").isEmpty() && Ash.gear(9, "wither_skeleton").isEmpty() && Ash.gear(13, "blaze").isEmpty(), "заглушка-элита арены усилена, остальные мобы без добавки");
        long mobsE = d.hall(20).spawns.stream().filter(m -> m.a.equals("wither_skeleton")).count();
        check(mobsE == 1 && d.hall(20).spawns.size() == 5, "в арене одна элита и четыре хаски");
        check(Mats.dungeonOf(d) == Mats.Dungeon.ASH && Dungeons.lootFor(d, d.hall(10), 1).stream().anyMatch(x -> x.contains("explosionwand:ash_coal") || x.contains("explosionwand:smoldering_feather")), "сундуки дают материалы Святилища");
        // дверь и ключ, тексты (файлы, не игра)
        String mod = new String(java.nio.file.Files.readAllBytes(java.nio.file.Path.of("src/com/explosionwand/ExplosionWandMod.java")), java.nio.charset.StandardCharsets.UTF_8);
        check(mod.contains("\"ash_key\", () -> new SealShardItem(") && mod.contains("\"ash_gate\", \"ash_key\", true)") && mod.contains("\"ash\", \"ash_seal_shard\")"), "Осколок Пепел (ash) и Пепельный ключ (ash_gate) связаны с дверями");
        check(mod.contains("\"ember_key\", () -> new SealShardItem(") && mod.contains("\"ember_gate\", \"ember_key\", true)"), "Угольный ключ (ember_gate) связан с дверью Гнезда");
        for (String lang : new String[]{"ru_ru", "en_us"}) {
            String js = new String(java.nio.file.Files.readAllBytes(java.nio.file.Path.of("resources/assets/explosionwand/lang/" + lang + ".json")), java.nio.charset.StandardCharsets.UTF_8);
            for (String k : new String[]{"explosionwand.dungeon.ash", "explosionwand.dungeon.locked.ash", "explosionwand.dungeon.locked.ash_gate", "explosionwand.dungeon.bell", "item.explosionwand.ash_key", "item.explosionwand.ash_key.desc", "explosionwand.jei.ash_key",
                    "explosionwand.dungeon.locked.ember_gate", "item.explosionwand.ember_key", "item.explosionwand.ember_key.desc", "explosionwand.jei.ember_key", "explosionwand.dungeon.fires_wrong", "explosionwand.dungeon.fires_solved", "explosionwand.dungeon.key_reissued", "explosionwand.ash.guard"})
                check(js.contains("\"" + k + "\""), lang + ": нет ключа " + k);
        }

        // ---- мир, свет, высота
        world = Dungeons.assemble(d, 0, 0, 0);
        int maxY = Integer.MIN_VALUE;
        for (long k : world.keySet()) maxY = Math.max(maxY, (int) (k & 0xFFF) - 2048);
        check(maxY >= 62 && world.get(Dungeons.pack(0, 64, -34)).contains("campfire[") && world.get(Dungeons.pack(0, 64, -34)).contains("signal_fire=true") && world.get(Dungeons.pack(0, 63, -34)).equals("minecraft:hay_block"), "на острие шпиля дымовой костёр на тюке сена: верх " + maxY);
        int smoke = 0;
        for (String v : world.values()) if (v.startsWith("minecraft:campfire[") && v.contains("lit=true") && v.contains("signal_fire=true")) smoke++;
        check(smoke >= 9, "дымовых костров (башня, двор): " + smoke);
        Map<Integer, Integer> lights = new HashMap<>();
        for (Map.Entry<Long, String> e : world.entrySet()) {
            if (!LIGHT.contains(name(e.getValue()))) continue;
            int[] c = unpack(e.getKey());
            for (Dungeons.Hall h : d.halls()) if (c[0] >= h.x - 1 && c[0] < h.x + h.sx + 1 && c[1] >= h.y && c[1] <= h.y + h.sy + 1 && c[2] >= h.z - 1 && c[2] < h.z + h.sz + 1) lights.merge(h.id, 1, Integer::sum);
        }
        for (Dungeons.Hall h : d.halls()) check(lights.getOrDefault(h.id, 0) > 0, "в зале " + h.id + " нет света");
        // лава не вытекает: бока и низ каждой клетки лавы — лава или твёрдое
        int leaks = 0;
        for (Map.Entry<Long, String> e : world.entrySet()) {
            if (!e.getValue().equals("minecraft:lava")) continue;
            int[] c = unpack(e.getKey());
            for (int[] n : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}}) {
                String v = world.get(Dungeons.pack(c[0] + n[0], c[1] + n[1], c[2] + n[2]));
                if (v == null || v.equals("minecraft:air") || v.equals("minecraft:water")) { leaks++; break; }
            }
        }
        check(leaks == 0, "лава заперта (бока и низ — твёрдое или лава): открытых клеток " + leaks);

        // ---- прохождение по этапам
        int[] saveBox = BOX;
        BOX = new int[]{-105, 85, -115, 70, -85, 75};
        Mark st = d.start();
        check(standable(st.x, st.y, st.z), "старт во дворе — место, где можно стоять");
        Set<Long> r = reach(st.x, st.y, st.z);
        for (Dungeons.Hall h : d.halls()) check(!hallExact(d, r, h.id), "при закрытой двери башни зал " + h.id + " недостижим");
        // полёт (элитры): из воздуха снаружи в рамку башни не попасть, пока дверь закрыта (бойницы и арки закрыты решётками)
        {
            ArrayDeque<int[]> fq = new ArrayDeque<>();
            Set<Long> air = new HashSet<>();
            fq.add(new int[]{0, 70, 8}); air.add(Dungeons.pack(0, 70, 8));
            int[][] d6 = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
            while (!fq.isEmpty()) {
                int[] c = fq.poll();
                for (int[] dd : d6) {
                    int nx = c[0] + dd[0], ny = c[1] + dd[1], nz = c[2] + dd[2];
                    if (nx < -40 || nx > 40 || nz < -70 || nz > 16 || ny < -3 || ny > 80) continue;
                    if (!open(nx, ny, nz) || !open(nx, ny + 1, nz)) continue;
                    if (air.add(Dungeons.pack(nx, ny, nz))) fq.add(new int[]{nx, ny, nz});
                }
            }
            int inside = 0;
            for (long k : air) { int[] c = unpack(k); if (c[0] >= -14 && c[0] <= 14 && c[2] >= -48 && c[2] <= -20 && c[1] >= 0 && c[1] <= 52) inside++; }
            check(air.size() > 5000 && inside == 0, "с воздуха снаружи в башню не попасть при закрытой двери: клеток внутри " + inside + " из " + air.size());
            String bn = world.get(Dungeons.pack(0, 46, -50)), bw = world.get(Dungeons.pack(-16, 46, -34)), bs = world.get(Dungeons.pack(-3, 5, -50)), bk = world.get(Dungeons.pack(-16, 5, -46));
            check(bn != null && bn.startsWith("minecraft:iron_bars") && bn.contains("east=true") && bn.contains("west=true"), "арка N соединена решёткой вдоль x: " + bn);
            check(bw != null && bw.startsWith("minecraft:iron_bars") && bw.contains("north=true") && bw.contains("south=true"), "арка W соединена решёткой вдоль z: " + bw);
            check(bs != null && bs.contains("east=true") && bs.contains("west=true") && bk != null && bk.contains("north=true") && bk.contains("south=true"), "бойницы соединены по виду стены");
            int bare = 0;
            for (Map.Entry<Long, String> en : world.entrySet()) if (en.getValue().startsWith("minecraft:iron_bars") && !en.getValue().contains("=true")) bare++;
            check(bare == 0, "решётки без соединений (столбики со щелью): " + bare);
        }
        fill(door, "minecraft:air");
        r = reach(st.x, st.y, st.z);
        for (int h : new int[]{1, 2, 3, 4, 5, 6}) check(hallExact(d, r, h), "дверь открыта: достижим зал башни " + h);
        for (Dungeons.Hall h : d.halls()) {
            if (h.id > 6) continue;
            for (Mark m : h.spawns) check(at(r, m), "точка моба недостижима: зал " + h.id + " " + m.a + " " + m.x + "," + m.y + "," + m.z);
            if (h.chest != null) check(r.contains(Dungeons.pack(h.chest.x, h.chest.y, h.chest.z)), "сундук недостижим: зал " + h.id);
        }
        for (int h : new int[]{8, 9, 10, 11, 12, 13, 14, 15, 16}) check(!hallExact(d, r, h), "до колокола зал " + h + " недостижим");
        check(!hallExact(d, r, 23) && r.contains(Dungeons.pack(22, -74, 0)) && !r.contains(Dungeons.pack(24, -74, 0)), "с поверхности колодец ведёт вниз до глухой двери, а Гнездо в обход ключей недоступно");
        Mark bell = d.marks("bell").get(0);
        check(reachNear(r, bell.x, bell.y, bell.z) && r.contains(Dungeons.pack(bell.x, bell.y - 2, bell.z)), "к колоколу можно подойти и достать");
        Mark lever = markAt(d, "lever", "tower");
        check(reachNear(r, lever.x, lever.y, lever.z), "рычаг тайника башни (в Караулке) достижим");
        // обрушенные пролёты: площадка с провалом в 1 блок, без прыжка не пройти
        int stand0 = 7;
        Map<Long, String> keep = new HashMap<>(world);
        for (int x = -13; x <= -11; x++) world.put(Dungeons.pack(x, stand0 - 1, -30), "minecraft:polished_blackstone_bricks");
        Set<Long> rf = reach(st.x, st.y, st.z);
        check(rf.contains(Dungeons.pack(-12, stand0, -30)), "залатанная площадка проходится (провал — настоящая яма, а не стена)");
        world = keep;
        check(!r.contains(Dungeons.pack(-12, stand0, -30)) && r.contains(Dungeons.pack(-12, stand0, -29)) && r.contains(Dungeons.pack(-12, stand0, -31)), "провал площадки перепрыгивается (клетки по краям достижимы, сам провал нет)");
        // колокол открывает шахту
        Mark shaft = markAt(d, "secret", "shaft");
        check(shaft.sx == 9 && shaft.sz == 9 && shaft.y == -1, "крышка шахты 9x9 в полу башни");
        fill(shaft, "minecraft:air");
        r = reach(st.x, st.y, st.z);
        check(hallExact(d, r, 8) && hallExact(d, r, 9) && hallExact(d, r, 10) && hallExact(d, r, 11), "после колокола винтовая лестница ведёт в Зал Пепла (залы 8-11)");
        check(r.contains(Dungeons.pack(-4, -1, -34)) && r.contains(Dungeons.pack(3, -22, -30)), "ступени шахты проходимы вниз до пола Зала Пепла");
        check(!hallExact(d, r, 12) && !hallExact(d, r, 13), "до ключа вниз на уровень 2 не пройти");
        for (Mark br : d.braziers()) { String[] o = br.a.split(","); check(r.contains(Dungeons.pack(br.x + Integer.parseInt(o[0]), br.y + Integer.parseInt(o[1]), br.z + Integer.parseInt(o[2]))) || br.y < -30, "точка возрождения жаровни достижима: " + br.x + "," + br.y + "," + br.z); }
        for (Dungeons.Hall h : d.halls()) {
            if (h.id < 8 || h.id > 11) continue;
            for (Mark m : h.spawns) check(at(r, m), "точка моба недостижима: зал " + h.id + " " + m.a + " " + m.x + "," + m.y + "," + m.z);
            if (h.chest != null) check(r.contains(Dungeons.pack(h.chest.x, h.chest.y, h.chest.z)), "сундук недостижим: зал " + h.id);
        }
        // струи по коридорам: сопла в стенах, линия свободна, не накрывает метки мобов и сундуки
        for (Mark m : d.marks("jets")) {
            int[] c = DungeonMech.jetCfg(m.a);
            check(world.get(Dungeons.pack(m.x, m.y, m.z)).startsWith("minecraft:observer"), "сопло в стене: " + m.x + "," + m.y + "," + m.z);
            int free = 0;
            for (int i = 1; i <= c[2]; i++) {
                int jx = m.x + c[0] * i, jz = m.z + c[1] * i;
                if (!mSolidW(jx, m.y, jz)) free++;
                for (Dungeons.Hall h : d.halls()) {
                    for (Mark sp : h.spawns) check(!(sp.x == jx && sp.z == jz && Math.abs(sp.y + 1 - m.y) <= 1), "моб стоит на линии струи " + m.x + "," + m.z + " -> " + sp.x + "," + sp.z);
                    if (h.chest != null) check(!(h.chest.x == jx && h.chest.z == jz), "сундук на линии струи");
                }
            }
            check(free >= c[2] / 2, "струя бьёт в проход, а не в стену: " + m.x + "," + m.z + " " + m.a);
        }
        // ключ: дверь вниз
        check(!hallExact(d, r, 12), "дверь ash_gate закрыта");
        fill(gate, "minecraft:air");
        r = reach(st.x, st.y, st.z);
        check(hallExact(d, r, 13), "с ключом достижим Грот гейзеров (зал 13) по лестнице и коридору");
        boolean landBrazier = false;
        for (Mark br : d.braziers()) if (br.y == -40 && br.x == 73) landBrazier = true;
        check(landBrazier && r.contains(Dungeons.pack(72, -40, -34)), "жаровня посадки уровня 2 достижима по лестнице");
        // переправа: платформы с прыжками до 2 клеток
        Set<Long> rg = reachG(st.x, st.y, st.z, 2);
        Dungeons.Hall h12 = d.hall(12);
        for (Mark m : h12.spawns) check(at(rg, m), "переправа: точка моба достижима прыжками: " + m.a + " " + m.x + "," + m.z);
        check(h12.chest != null && rg.contains(Dungeons.pack(h12.chest.x, h12.chest.y, h12.chest.z)), "переправа проходится до выступа с сундуком (прыжки до 2 клеток)");
        Mark chest12 = h12.chest;
        check(!reach(st.x, st.y, st.z).contains(Dungeons.pack(chest12.x, chest12.y, chest12.z)), "без прыжков до выступа с сундуком не дойти");
        // осыпающиеся платформы обязательны: без них пути нет
        Map<Long, String> keep2 = new HashMap<>(world);
        for (Mark m : d.marks("crumble")) {
            check(m.sx == 3 && m.sy == 1 && m.sz == 3 && world.get(Dungeons.pack(m.x, m.y, m.z)).equals("minecraft:" + m.a), "осыпающаяся платформа 3x3 из " + m.a);
            fill(m, "minecraft:air");
        }
        check(!reachG(st.x, st.y, st.z, 2).contains(Dungeons.pack(chest12.x, chest12.y, chest12.z)), "когда платформы осыпались, дальше пути нет (они на главном пути)");
        world = keep2;
        // тайники 14-16 закрыты до рычага и достижимы после
        for (int hh : new int[]{14, 15, 16}) check(!hallExact(d, rg, hh), "секретный зал " + hh + " закрыт до рычага");
        Mark sLava = markAt(d, "secret", "ashlava"), sRoom = markAt(d, "secret", "ashroom"), sTower = markAt(d, "secret", "tower");
        check(reachNear(rg, markAt(d, "lever", "ashlava").x, markAt(d, "lever", "ashlava").y, markAt(d, "lever", "ashlava").z) && reachNear(rg, markAt(d, "lever", "ashroom").x, markAt(d, "lever", "ashroom").y, markAt(d, "lever", "ashroom").z), "рычаги тайников 15 и 16 достижимы");
        for (Mark m : new Mark[]{sLava, sRoom, sTower}) {
            check(world.get(Dungeons.pack(m.x, m.y, m.z)).contains("cracked"), "секрет " + m.a + ": трещина в кладке — единственный знак");
            fill(m, "minecraft:air");
        }
        rg = reachG(st.x, st.y, st.z, 2);
        for (int hh : new int[]{14, 15, 16}) {
            check(hallExact(d, rg, hh), "после рычага достижим тайник " + hh);
            check(rg.contains(Dungeons.pack(d.hall(hh).chest.x, d.hall(hh).chest.y, d.hall(hh).chest.z)), "сундук тайника " + hh + " достижим");
        }

        // ---- уровень 3 (шаг 2б): выход с уступа переправы -> лестница -> зал 17; головоломка закрывает спуск
        Dungeons.Hall h17 = d.hall(17);
        Mark[] fb = DungeonMech.fireSlots(d, "fires", "b", 4), ff = DungeonMech.fireSlots(d, "fires", "f", 4), fw = DungeonMech.fireSlots(d, "fires", "w", 16);
        check(h17 != null && fb[0] != null && fb[3] != null && ff[0] != null && ff[3] != null && fw[2] != null, "зал 17: четыре жаровни, четыре панели, точки волны");
        boolean in17 = false;
        for (Mark m : fb) in17 |= reachNear(rg, m.x, m.y - 2, m.z);
        check(in17 && rg.contains(Dungeons.pack(-55, -56, -40)) && rg.contains(Dungeons.pack(-53, -56, -40)), "с уступа переправы по лестнице достижим зал 17");
        for (Mark m : fb) {
            check(reachNear(rg, m.x, m.y - 2, m.z) && !standable(m.x, m.y - 2, m.z), "к жаровне можно подойти, а залезть на постамент нельзя: " + m.x + "," + m.z);
            check(world.get(Dungeons.pack(m.x, m.y, m.z)).startsWith("minecraft:campfire[") && world.get(Dungeons.pack(m.x, m.y, m.z)).contains("lit=false"), "жаровня стоит незажжённая");
            int bi = Integer.parseInt(m.a.split(",")[2]);
            check(world.get(Dungeons.pack(m.x, m.y - 3, m.z)).equals("minecraft:" + DungeonMech.FIRE_COLORS[bi]) || world.get(Dungeons.pack(m.x + 1, m.y - 3, m.z)).equals("minecraft:" + DungeonMech.FIRE_COLORS[bi]), "площадка жаровни " + bi + " цвета " + DungeonMech.FIRE_COLORS[bi]);
        }
        for (Mark m : fw) if (m != null) check(rg.contains(Dungeons.pack(m.x, m.y, m.z)), "точка волны достижима: " + m.x + "," + m.z);
        for (Mark m : ff) {
            check(m.sx == 3 && m.sy == 5 && m.sz == 1, "панель фрески 3x5");
            boolean solidPanel = true, airFront = true;
            for (int ix = 0; ix < 3; ix++) for (int iy = 0; iy < 5; iy++) {
                solidPanel &= mSolidW(m.x + ix, m.y + iy, m.z);
                airFront &= !mSolidW(m.x + ix, m.y + iy, m.z + 1);
            }
            check(solidPanel && airFront, "панель фрески — часть стены, перед ней свободно");
            check(rg.contains(Dungeons.pack(m.x + 1, m.y - 2, m.z + 3)) || reachNear(rg, m.x + 1, m.y - 2, m.z + 3), "перед фреской можно стоять");
        }
        for (int hh : new int[]{18, 19}) check(hallExact(d, rg, hh), "боковой зал " + hh + " достижим без секретов");
        check(!hallExact(d, rg, 21) && !hallExact(d, rg, 20) && !hallExact(d, rg, 23), "до решения головоломки спуск закрыт: зал 21 (рычаг), арена и Гнездо недостижимы");
        Mark lvE = markAt(d, "lever", "emberroom"), sE = markAt(d, "secret", "emberroom");
        check(reachNear(rg, lvE.x, lvE.y, lvE.z) && world.get(Dungeons.pack(sE.x, sE.y, sE.z)).contains("cracked") && sE.sx == 3 && sE.sy == 3 && sE.sz == 3, "рычаг тайника 21 в зале 18 достижим, трещина в зале 19");
        fill(sE, "minecraft:air");
        Set<Long> rg2 = reachG(st.x, st.y, st.z, 2);
        check(hallExact(d, rg2, 21) && rg2.contains(Dungeons.pack(d.hall(21).chest.x, d.hall(21).chest.y, d.hall(21).chest.z)) && !hallExact(d, rg2, 20), "после рычага достижим тайник 21, но не арена");
        for (Dungeons.Hall hh : d.halls()) {
            if (hh.id < 17 || hh.id > 19) continue;
            for (Mark m : hh.spawns) check(at(rg2, m), "точка моба достижима: зал " + hh.id);
            if (hh.chest != null) check(rg2.contains(Dungeons.pack(hh.chest.x, hh.chest.y, hh.chest.z)), "сундук достижим: зал " + hh.id);
        }
        Mark sF = markAt(d, "secret", "fires");
        check(sF.sx == 3 && sF.sy == 4 && sF.sz == 1 && world.get(Dungeons.pack(sF.x, sF.y, sF.z)).equals("minecraft:obsidian"), "заслонка спуска 3x4 из обсидиана в южной стене зала 17");

        // ---- головоломка «Четыре жаровни»: порядок из позиции данжа, фреска, шаги
        Set<String> perms = new HashSet<>();
        boolean allPerm = true;
        for (int sx = -20; sx < 20; sx++) for (int sz = -20; sz < 20; sz += 3) {
            int[] o = DungeonMech.fireOrder(DungeonMech.fireSeed(sx * 896 + 17, sz * 896 - 5));
            int[] sorted = o.clone();
            Arrays.sort(sorted);
            allPerm &= Arrays.equals(sorted, new int[]{0, 1, 2, 3});
            perms.add(Arrays.toString(o));
            check(Arrays.equals(o, DungeonMech.fireOrder(DungeonMech.fireSeed(sx * 896 + 17, sz * 896 - 5))), "порядок жаровен детерминирован");
        }
        check(allPerm && perms.size() >= 20, "порядок — перестановка 0..3, у разных данжей разный: различных " + perms.size() + " из 24");
        Set<String> glyphs = new HashSet<>();
        for (int bi = 0; bi < 4; bi++) {
            StringBuilder sb = new StringBuilder();
            int ink = 0;
            for (int row = 0; row < 5; row++) for (int col = 0; col < 3; col++) { boolean k = DungeonMech.fireInk(bi, col, row); sb.append(k ? '#' : '.'); if (k) ink++; }
            glyphs.add(sb.toString());
            check(ink >= 5 && ink <= 12 && DungeonMech.fireBlock(bi, 0, 0).equals(DungeonMech.fireInk(bi, 0, 0) ? DungeonMech.FIRE_COLORS[bi] : DungeonMech.FIRE_BG), "узор жаровни " + bi + ": " + ink + " клеток краски");
        }
        check(glyphs.size() == 4 && new HashSet<>(Arrays.asList(DungeonMech.FIRE_COLORS)).size() == 4 && !DungeonMech.fireInk(0, 3, 0) && !DungeonMech.fireInk(9, 0, 0), "четыре разных узора и цвета; вне панели краски нет");
        for (int[] o : new int[][]{{0, 1, 2, 3}, {2, 0, 3, 1}, {3, 2, 1, 0}}) {
            boolean[] lit = new boolean[4];
            int[] want = {DungeonMech.FIRE_OK, DungeonMech.FIRE_OK, DungeonMech.FIRE_OK, DungeonMech.FIRE_DONE};
            for (int k = 0; k < 4; k++) {
                check(DungeonMech.firePrefix(o, lit), "состояние до шага " + k + " согласовано");
                int wrong = o[(k + 1) % 4] == o[k] ? -1 : o[(k + 1) % 4];
                if (k < 3) check(DungeonMech.fireStep(o, lit, wrong) == DungeonMech.FIRE_WRONG, "не та жаровня на шаге " + k + " - ошибка");
                if (k > 0) check(DungeonMech.fireStep(o, lit, o[0]) == DungeonMech.FIRE_NONE, "уже горящая жаровня ничего не меняет");
                check(DungeonMech.fireStep(o, lit, o[k]) == want[k], "верная жаровня на шаге " + k);
                lit[o[k]] = true;
            }
        }
        boolean[] bad = new boolean[4];
        bad[2] = true;
        check(!DungeonMech.firePrefix(new int[]{0, 1, 2, 3}, bad) && DungeonMech.firePrefix(new int[]{2, 0, 1, 3}, bad) && DungeonMech.firePrefix(new int[]{0, 1, 2, 3}, new boolean[4]), "нарушенное состояние (горит не первая по порядку) распознаётся");
        // фреска показывает порядок: панель k - узор жаровни order[k]; по нему игрок восстанавливает порядок
        int[] ord = DungeonMech.fireOrder(DungeonMech.fireSeed(100, 200));
        int[] seen = new int[4];
        for (int k = 0; k < 4; k++) for (int bi = 0; bi < 4; bi++) {
            boolean same = true;
            for (int row = 0; row < 5; row++) for (int col = 0; col < 3; col++) same &= DungeonMech.fireInk(bi, col, row) == DungeonMech.fireInk(ord[k], col, row);
            if (same) seen[k] = bi;
        }
        check(Arrays.equals(seen, ord), "по узорам панелей слева направо читается порядок зажигания");
        check(!DungeonMech.waveMob(0).isEmpty() && Set.of("husk", "skeleton", "blaze").containsAll(List.of(DungeonMech.waveMob(0), DungeonMech.waveMob(2), DungeonMech.waveMob(5), DungeonMech.waveMob(11))) && DungeonMech.WAVE_CAP >= 2, "волна из ванильных заглушек");

        // ---- уровень 4: спуск, арена (печать), элита, сундук с Угольным ключом, дверь Гнезда
        fill(sF, "minecraft:air");
        Set<Long> rg3 = reachG(st.x, st.y, st.z, 2);
        Dungeons.Hall h20 = d.hall(20);
        for (Mark m : h20.spawns) check(at(rg3, m), "арена: точка моба достижима " + m.a + " " + m.x + "," + m.z);
        check(h20.chest != null && rg3.contains(Dungeons.pack(h20.chest.x, h20.chest.y, h20.chest.z)), "арена: сундук с ключом достижим");
        Mark seal = d.marks("seal").get(0);
        boolean sealOpen = true;
        for (int ix = 0; ix < seal.sx; ix++) for (int iy = 0; iy < seal.sy; iy++) sealOpen &= !mSolidW(seal.x + ix, seal.y + iy, seal.z);
        check(sealOpen && seal.sx == 3 && seal.sy == 4 && rg3.contains(Dungeons.pack(seal.x + 1, seal.y, seal.z)), "проём печати арены свободен в шаблоне и проходим; закрывает его движок");
        check(DungeonMech.sealWanted(1L << 20, 0, 20, true) && !DungeonMech.sealWanted(1L << 20, 1L << 20, 20, true) && !DungeonMech.sealWanted(0, 0, 20, true), "печать арены: закрыта, пока зал разбужен и не зачищен");
        check(!hallExact(d, rg3, 23) && !rg3.contains(Dungeons.pack(3, -64, 4)), "до Угольного ключа в Гнездо не пройти");
        check(world.get(Dungeons.pack(ember.x, ember.y, ember.z)) != null && ember.sx == 2 && ember.sy == 4 && ember.sz == 3 && mSolidW(ember.x, ember.y, ember.z + 1), "дверь Гнезда на месте");
        Mark aElite = null;
        for (Mark m : h20.spawns) if (m.a.equals("wither_skeleton")) aElite = m;
        check(aElite != null && !mSolidW(aElite.x, aElite.y, aElite.z) && !mSolidW(aElite.x, aElite.y + 2, aElite.z) && mSolidW(aElite.x, aElite.y - 1, aElite.z), "элита стоит на помосте со свободной головой");

        // ---- уровень 5: Гнездо и колодец-выход
        fill(ember, "minecraft:air");
        Set<Long> rg4 = reachG(st.x, st.y, st.z, 2);
        check(hallExact(d, rg4, 23) && rg4.contains(Dungeons.pack(nest.chest.x, nest.chest.y, nest.chest.z)), "с Угольным ключом достижимо Гнездо и его сундук");
        boolean landNest = false;
        for (Mark br : d.braziers()) if (br.y == -74) landNest = rg4.contains(Dungeons.pack(br.x + 1, br.y, br.z));
        check(landNest, "жаровня-якорь у входа в Гнездо достижима");
        boolean eggBlocked = !standable(34, -74, 4) && !standable(34, -70, 4) && mSolidW(34, -70, 4);
        check(eggBlocked, "яйцо из пепла в центре — декор, на него не встать");
        int lava = 0;
        for (Map.Entry<Long, String> e : world.entrySet()) if (e.getValue().equals("minecraft:lava") && unpack(e.getKey())[1] == -75) lava++;
        check(lava >= 30, "лавовые лужки в Гнезде есть: " + lava);
        // колодец: с поверхности вниз по лестнице до глухой двери (в Гнездо не попасть), из Гнезда рычаг открывает дверь и ведёт наверх
        Mark wl = markAt(d, "lever", "wellgate"), wsec = markAt(d, "secret", "wellgate");
        check(wsec.sx == 1 && wsec.sy == 3 && wsec.sz == 3 && world.get(Dungeons.pack(wsec.x, wsec.y, wsec.z)).equals("minecraft:obsidian"), "дверь колодца 1x3x3 из обсидиана");
        check(mSolidW(wsec.x, wsec.y, wsec.z) && mSolidW(wsec.x, wsec.y + 2, wsec.z + 1) && rg4.contains(Dungeons.pack(22, -74, 0)), "внизу колодца глухая дверь: с поверхности лестница доходит до неё");
        check(reachNear(rg4, wl.x, wl.y - 1, wl.z) && rg4.contains(Dungeons.pack(wl.x, wl.y - 1, wl.z)), "рычаг двери колодца стоит в Гнезде и достижим из пещеры");
        Set<Long> rClosed = reachG(3, -74, 4, 0);
        check(!rClosed.contains(Dungeons.pack(22, -74, 0)) && !rClosed.contains(Dungeons.pack(18, 0, 2)), "пока дверь колодца закрыта, из Гнезда в него не войти");
        fill(wsec, "minecraft:air");
        Set<Long> rn = reachG(3, -74, 4, 0);
        check(rn.contains(Dungeons.pack(22, -74, 0)) && rn.contains(Dungeons.pack(18, 0, 2)), "после рычага из Гнезда по колодцу можно выйти на поверхность (клетка (18,0,2) вровень со двором)");
        int stepsOk = 0;
        int[][] ring = {{2, 0}, {2, -1}, {2, -2}, {1, -2}, {0, -2}, {-1, -2}, {-2, -2}, {-2, -1}, {-2, 0}, {-2, 1}, {-2, 2}, {-1, 2}, {0, 2}, {1, 2}, {2, 2}, {2, 1}};
        for (int j = 0; j <= 74; j++) if (rn.contains(Dungeons.pack(20 + ring[j % 16][0], -74 + j, ring[j % 16][1]))) stepsOk++;
        check(stepsOk == 75, "все 75 ступеней колодца достижимы по порядку: " + stepsOk);
        int into = 0, onRing = 0;
        for (long k : rn) {
            int[] c = unpack(k);
            if (c[0] >= 19 && c[0] <= 21 && c[2] >= -1 && c[2] <= 1 && c[1] < -1 && c[1] > -74) into++;
            if (c[0] >= 17 && c[0] <= 23 && c[2] >= -3 && c[2] <= 3 && c[1] > -74 && c[1] < 0) { boolean rr = false; for (int[] q : ring) rr |= c[0] == 20 + q[0] && c[2] == q[1]; if (rr) onRing++; }
        }
        check(into == 0 && onRing >= 70 && onRing <= 80, "в ядро шахты (столб 3x3) не попасть, на шахте только ступени кольца: внутри " + into + ", на кольце " + onRing);
        boolean capped = true;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            boolean open = (dx == -2 && dz >= -1);
            if (!open && !mSolidW(20 + dx, -1, dz)) capped = false;
        }
        check(capped, "устье колодца закрыто вровень со двором, кроме четырёх клеток последних ступеней");
        // ---- всё проходимое внутри данжа: под землёй и на дворе (иначе игрок остался бы вне защиты и счёта)
        Dungeons.Mask mk = d.mask();
        int walkOut = 0;
        for (long k : rg4) {
            int[] c = unpack(k);
            boolean yard = Math.abs(c[0]) <= 26 && c[2] >= -52 && c[2] <= 4;
            if ((c[1] < -3 || (yard && c[1] <= 64)) && (!mk.has(c[0], c[1], c[2]) || !mk.has(c[0], c[1] + 1, c[2]))) walkOut++;
        }
        check(walkOut == 0, "проходимые клетки Святилища все в маске: вне " + walkOut);
        long vol = (long) w * hgt * dep;
        check(mk.count() < vol * 0.40, "маска уже рамки: " + mk.count() + " из " + vol);
        for (Dungeons.Hall h : d.halls()) check(mk.has(h.x, h.y, h.z) && mk.has(h.x + h.sx - 1, h.y + h.sy, h.z + h.sz - 1), "зал " + h.id + " целиком в маске");
        for (Dungeons.Hall x : d.halls()) {
            int cellsIn = 0;
            for (int px = x.x; px < x.x + x.sx; px += 7) for (int pz = x.z; pz < x.z + x.sz; pz += 7) if (mk.has(px, x.y + 1, pz)) cellsIn++;
            check(cellsIn > 0, "в маске есть клетки зала " + x.id);
        }
        // барьер: нет босса — флаг bossDead не ставится сам, блоки защищены
        DungeonData data = new DungeonData();
        DungeonData.Rec rec = new DungeonData.Rec("ash_0_0", "ash", 0, 0, 100, 64, 100);
        rec.layout = d.signature(); rec.partsDone = d.allParts();
        data.recs.put(rec.key, rec);
        check(rec.contains(100.5, 64.5, 100.5 - 8, 0) && Dungeons.guardedAt(data, 100.5, 64.5, 91.5, 0) == rec && data.migrateBossDead() == 0 && !rec.bossDead, "без босса барьер Святилища стоит (босс — фаза 4.10, зал Гнезда получит метку boss)");
        BOX = saveBox;
        System.out.println("святилище ok: " + total + " блоков, залов " + ids.size() + ", мобов " + spawns + ", сундуков " + chests + ", маска " + mk.count());
    }

    static boolean mSolidW(int x, int y, int z) { String v = world.get(Dungeons.pack(x, y, z)); return v != null && !v.equals("minecraft:air"); }

    /** Святилище обходит и Разломы, и Города: ни при каком сиде и запасной точке рамки не пересекаются. */
    static void ashPlacement() {
        Dungeons.Def a = Dungeons.ASH;
        int[] ab = a.bounds();
        int clashes0 = 0, cells = 0;
        for (Dungeons.Def o : new Dungeons.Def[]{Dungeons.RIFT, Dungeons.SLEEP}) {
            int[] ob = o.bounds();
            for (long seed = 1; seed <= 25; seed++) for (int cx = -3; cx <= 3; cx++) for (int cz = -3; cz <= 3; cz++) {
                cells++;
                long sd = seed * 7919;
                int[] s0 = Dungeons.site(sd, a, cx, cz, 0);
                check(s0[0] >= cx * a.cell + a.margin && s0[0] < (cx + 1) * a.cell - a.margin && s0[1] >= cz * a.cell + a.margin && s0[1] < (cz + 1) * a.cell - a.margin, "святилище в своей клетке");
                if (Dungeons.clashes(sd, a, Dungeons.site0Raw(sd, a, cx, cz))) clashes0++;
                for (int at = 0; at < 8; at++) {
                    int[] s = Dungeons.site(sd, a, cx, cz, at);
                    if (at > 0 && Dungeons.clashes(sd, a, s)) continue;
                    for (int ax = Math.floorDiv(s[0], o.cell) - 1; ax <= Math.floorDiv(s[0], o.cell) + 1; ax++) for (int az = Math.floorDiv(s[1], o.cell) - 1; az <= Math.floorDiv(s[1], o.cell) + 1; az++)
                        for (int ra = 0; ra < 8; ra++) {
                            int[] q = Dungeons.site(sd, o, ax, az, ra);
                            boolean ov = s[0] + ab[0] < q[0] + ob[3] && q[0] + ob[0] < s[0] + ab[3] && s[1] + ab[2] < q[1] + ob[5] && q[1] + ob[2] < s[1] + ab[5];
                            check(!ov, "святилище " + Arrays.toString(s) + " налезает на " + o.id + " " + Arrays.toString(q));
                        }
                }
            }
        }
        System.out.println("святилище обходит Разломы и Города: проверок " + cells + ", с первой попытки мешало в " + clashes0);
        check(clashes0 < cells / 2, "обход не должен быть правилом: " + clashes0 + " из " + cells);
    }

    /** Механики Святилища: цикл струй и гейзеров, попадание, осыпающиеся платформы. */
    static void ashMech() {
        // цикл: покой, предупреждение, выдох; сдвиг фазы
        int idle = 0, warn = 0, burst = 0;
        for (int t = 0; t < 100; t++) {
            int ph = DungeonMech.cycle(t, 100, DungeonMech.JET_WARN, DungeonMech.JET_BURST, 0);
            if (ph == 0) idle++; else if (ph == 1) warn++; else burst++;
        }
        check(idle == 50 && warn == DungeonMech.JET_WARN && burst == DungeonMech.JET_BURST, "цикл струи: покой " + idle + ", вдох " + warn + ", выдох " + burst);
        check(DungeonMech.cycle(99, 100, 25, 25, 0) == 2 && DungeonMech.cycle(49, 100, 25, 25, 0) == 0 && DungeonMech.cycle(50, 100, 25, 25, 0) == 1 && DungeonMech.cycle(75, 100, 25, 25, 0) == 2, "границы цикла");
        check(DungeonMech.cycle(0, 100, 25, 25, 50) == 1 && DungeonMech.cycle(25, 100, 25, 25, 50) == 2 && DungeonMech.cycle(-1, 100, 25, 25, 0) == 2, "сдвиг фазы и отрицательное время");
        // у каждой струи уровня есть окно, когда её линия пуста, и струи соседних сопел не бьют вместе без перерыва
        Dungeons.Def d = Dungeons.ASH;
        for (Mark m : d.marks("jets")) {
            int[] c = DungeonMech.jetCfg(m.a);
            int quiet = 0;
            for (int t = 0; t < c[3]; t++) if (DungeonMech.cycle(t, c[3], DungeonMech.JET_WARN, DungeonMech.JET_BURST, c[4]) != 2) quiet++;
            check(quiet >= c[3] / 2, "струя оставляет окно для прохода: " + m.a);
        }
        // попадание: линия +z от сопла (0,5,0), игрок у соседней клетки на уровне груди; в стороне, сзади и дальше длины — мимо
        check(DungeonMech.jetHit(0.0, -1.5, 2.5, 0, 1, 5) && DungeonMech.jetHit(0.5, -1.4, 5.5, 0, 1, 5), "игрок на линии струи горит");
        check(!DungeonMech.jetHit(2.0, -1.5, 2.5, 0, 1, 5) && !DungeonMech.jetHit(0.0, -1.5, -1.0, 0, 1, 5) && !DungeonMech.jetHit(0.0, -1.5, 6.8, 0, 1, 5), "в стороне, позади сопла и дальше длины - мимо");
        check(!DungeonMech.jetHit(0.0, 1.5, 2.5, 0, 1, 5) && !DungeonMech.jetHit(0.0, -4.0, 2.5, 0, 1, 5) && DungeonMech.jetHit(-3.0, -1.5, 0.0, -1, 0, 5) && !DungeonMech.jetHit(3.0, -1.5, 0.0, -1, 0, 5), "прыжок выше струи и яма ниже её - мимо; направление -x");
        // гейзер: столб над жерлом
        check(DungeonMech.inGeyser(0.2, 0.0, -0.3) && DungeonMech.inGeyser(0.0, 2.0, 0.0) && !DungeonMech.inGeyser(1.2, 0.0, 0.0) && !DungeonMech.inGeyser(0.0, 3.2, 0.0) && !DungeonMech.inGeyser(0.0, -1.0, 0.0), "столб гейзера");
        check(Arrays.equals(DungeonMech.geyserCfg("-z,150"), new int[]{0, -1, 150}) && Arrays.equals(DungeonMech.geyserCfg("up,160"), new int[]{0, 0, 160}) && DungeonMech.geyserCfg("up") == null && DungeonMech.geyserCfg("++,1") == null, "разбор гейзера");
        check(Arrays.equals(DungeonMech.jetCfg("+z,5,100,37"), new int[]{0, 1, 5, 100, 37}) && DungeonMech.jetCfg("+z,5,100") == null && DungeonMech.jetCfg("up,5,100,0") == null, "разбор струи");
        // осыпающаяся платформа: цела -> шаг -> осыпается 30 тиков -> нет 140 тиков -> цела; на месте стоит игрок — не возвращается
        long now = 1000;
        int st = DungeonMech.crumbNext(0, now, now, false, false);
        check(st == 0, "без шага платформа цела");
        st = DungeonMech.crumbNext(0, now, now, true, false);
        check(st == 1, "шаг запускает осыпание");
        long since = now;
        check(DungeonMech.crumbNext(1, since, since + DungeonMech.CRUMBLE_AFTER - 1, true, false) == 1 && DungeonMech.crumbNext(1, since, since + DungeonMech.CRUMBLE_AFTER, false, false) == 2, "осыпается через CRUMBLE_AFTER тиков, даже если игрок ушёл");
        check(DungeonMech.crumbNext(2, since, since + DungeonMech.CRUMBLE_BACK - 1, false, false) == 2 && DungeonMech.crumbNext(2, since, since + DungeonMech.CRUMBLE_BACK, false, false) == 0, "возвращается через CRUMBLE_BACK тиков");
        check(DungeonMech.crumbNext(2, since, since + 5000, false, true) == 2, "пока в платформе кто-то стоит, блоки не ставятся");
        check(DungeonMech.CRUMBLE_AFTER >= 20 && DungeonMech.CRUMBLE_AFTER <= 40 && DungeonMech.CRUMBLE_BACK >= 100, "время на прыжок есть: " + DungeonMech.CRUMBLE_AFTER + " тиков");
        Mark pad = new Mark("crumble", "basalt", 10, 5, 20, 3, 1, 3);
        check(DungeonMech.onPad(pad, 11.5, 6.0, 21.5) && DungeonMech.onPad(pad, 9.8, 6.0, 20.0) && !DungeonMech.onPad(pad, 11.5, 7.5, 21.5) && !DungeonMech.onPad(pad, 14.0, 6.0, 21.5) && !DungeonMech.onPad(pad, 11.5, 4.0, 21.5), "стоит на платформе: сверху, у края, не в прыжке и не сбоку");
        check(DungeonMech.inPad(pad, 11.5, 4.5, 21.5) && DungeonMech.inPad(pad, 11.5, 5.5, 21.5) && !DungeonMech.inPad(pad, 11.5, 6.3, 21.5) && !DungeonMech.inPad(pad, 11.5, 2.0, 21.5), "телом внутри места платформы (ставить блоки нельзя)");
    }

    /** Город обходит Разломы: ни при каком сиде и запасной точке рамки не пересекаются; город остаётся в своей клетке. */
    static void cityPlacement() {
        Dungeons.Def c = Dungeons.SLEEP, r = Dungeons.RIFT;
        int[] cb = c.bounds(), rb = r.bounds();
        int clashes0 = 0, cells = 0;
        for (long seed = 1; seed <= 40; seed++) for (int cx = -3; cx <= 3; cx++) for (int cz = -3; cz <= 3; cz++) {
            cells++;
            int[] s0 = Dungeons.site(seed * 7919, c, cx, cz, 0);
            check(s0[0] >= cx * c.cell + c.margin && s0[0] < (cx + 1) * c.cell - c.margin && s0[1] >= cz * c.cell + c.margin && s0[1] < (cz + 1) * c.cell - c.margin, "город в своей клетке");
            if (Dungeons.clashes(seed * 7919, c, Dungeons.site0Raw(seed * 7919, c, cx, cz))) clashes0++;
            for (int at = 0; at < 8; at++) {
                int[] s = Dungeons.site(seed * 7919, c, cx, cz, at);
                if (at > 0 && Dungeons.clashes(seed * 7919, c, s)) continue;   // register такую запасную точку пропускает
                for (int ax = Math.floorDiv(s[0], r.cell) - 1; ax <= Math.floorDiv(s[0], r.cell) + 1; ax++) for (int az = Math.floorDiv(s[1], r.cell) - 1; az <= Math.floorDiv(s[1], r.cell) + 1; az++)
                    for (int ra = 0; ra < 8; ra++) {
                        int[] q = Dungeons.site(seed * 7919, r, ax, az, ra);
                        boolean ov = s[0] + cb[0] < q[0] + rb[3] && q[0] + rb[0] < s[0] + cb[3] && s[1] + cb[2] < q[1] + rb[5] && q[1] + rb[2] < s[1] + cb[5];
                        check(!ov, "город " + Arrays.toString(s) + " налезает на Разлом " + Arrays.toString(q));
                    }
            }
        }
        System.out.println("город обходит Разломы: клеток " + cells + ", с первой попытки мешало в " + clashes0);
        check(clashes0 < cells / 2, "обход не должен быть правилом: " + clashes0 + " из " + cells);
    }

    public static void main(String[] a) throws Exception {
        Dungeons.Def d = Dungeons.RIFT;
        rift();
        city();
        cityPlacement();
        ash();
        ashPlacement();
        int total = 0;
        for (DungeonTemplate t : d.templates()) total += t.count();
        int ox = 1234, oy = 70, oz = -987;
        Mark door = d.doors().get(0);

        // ---- расстановка по сетке
        long seed = 123456789L;
        for (int cx = -5; cx <= 5; cx++) for (int cz = -5; cz <= 5; cz++) {
            int[] s = Dungeons.site(seed, d, cx, cz, 0), s2 = Dungeons.site(seed, d, cx, cz, 0);
            check(Arrays.equals(s, s2), "site детерминирован");
            check(s[0] >= cx * d.cell + d.margin && s[0] < (cx + 1) * d.cell - d.margin && s[1] >= cz * d.cell + d.margin && s[1] < (cz + 1) * d.cell - d.margin, "site в клетке " + cx + "," + cz);
            for (int at = 1; at < 8; at++) {
                int[] s3 = Dungeons.site(seed, d, cx, cz, at);
                check(Math.abs(s3[0] - s[0]) <= 48 && Math.abs(s3[1] - s[1]) <= 48, "запасная точка близко");
            }
        }
        check(!Arrays.equals(Dungeons.site(seed, d, 0, 0, 0), Dungeons.site(seed + 1, d, 0, 0, 0)), "другой сид — другое место");
        // ближайший данж: не дальше диагонали соседних клеток; зарегистрированный приоритетнее расчётного
        Random rnd = new Random(7);
        for (int i = 0; i < 500; i++) {
            double px = rnd.nextInt(20000) - 10000, pz = rnd.nextInt(20000) - 10000;
            Dungeons.Hit h = Dungeons.nearest(seed, d, px, pz, null);
            check(h.dist < d.cell * 1.5, "ближайший данж в пределах " + (int) (d.cell * 1.5) + " блоков: " + (int) h.dist);
        }
        Dungeons.Hit h1 = Dungeons.nearest(seed, d, 100, 100, null);
        Dungeons.Hit h2 = Dungeons.nearest(seed, d, 100, 100, key -> key.equals(d.key(0, 0)) ? new int[]{101, 101} : null);
        check(h2.registered && h2.x == 101 && h2.z == 101 || h1.dist < h2.dist, "зарегистрированный данж берётся из реестра");

        // ---- составной данж ~250 блоков: 4 части по 40 с шагом 70 (0..250)
        Dungeons.Def big = new Dungeons.Def("big", 1024, 200, 1, "p0", "p1", "p2", "p3");
        List<DungeonTemplate> parts = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            int n = 40 * 40, k = 0;
            int[] bx = new int[n], by = new int[n], bz = new int[n], bs = new int[n];
            for (int x = 0; x < 40; x++) for (int z = 0; z < 40; z++) { bx[k] = x; by[k] = 0; bz[k] = z; bs[k] = 0; k++; }
            parts.add(new DungeonTemplate("p" + i, i * 70 + (i == 3 ? 0 : 0), 0, 0, 40, 1, 40, new String[]{"minecraft:stone"}, bx, by, bz, bs, new ArrayList<>()));
        }
        big.setTemplates(parts);
        int[] bb = big.bounds();
        check(bb[3] - bb[0] == 250, "габарит составного данжа 250 блоков, а не " + (bb[3] - bb[0]));
        Map<Long, String> bw = Dungeons.assemble(big, -100, 64, 300);
        check(bw.size() == 4 * 1600, "все блоки 4 частей собраны: " + bw.size());
        check(bw.containsKey(Dungeons.pack(-100 + 249, 64, 300 + 39)) && !bw.containsKey(Dungeons.pack(-100 + 250, 64, 300)) && !bw.containsKey(Dungeons.pack(-100 + 195, 64, 300)), "края частей и зазоры на своих местах");
        // ленивая постановка: игрок идёт вдоль данжа, части запрашиваются по мере приближения, одновременно не все
        Set<Integer> asked = new HashSet<>();
        int[] atStart = {0}, nearStart = {0};
        for (double px = -150; px <= 400; px += 10) {
            int now = 0;
            for (int i = 0; i < 4; i++) {
                DungeonTemplate t = parts.get(i);
                if (Dungeons.distToRect(px, 20, t.ox, 0, t.sx, t.sz) <= Dungeons.PART_RANGE) { asked.add(i); now++; }
            }
            if (px == -150) atStart[0] = now;
            if (px == -60) nearStart[0] = now;
        }
        check(asked.size() == 4, "по пути запрошены все части");
        check(atStart[0] == 0, "издалека не ставится ни одна часть (" + atStart[0] + ")");
        check(nearStart[0] == 1, "у края данжа ставится только ближняя часть (" + nearStart[0] + ")");

        // ---- Shield / protectedAt: граница данжа (min включён, max нет), несколько данжей, пустой щит
        Dungeons.Shield sh = new Dungeons.Shield(List.of(new int[]{100, 10, 200, 121, 47, 286}, new int[]{-50, 0, -50, -40, 5, -40}));
        check(sh.at(100, 10, 200) && sh.at(120, 46, 285), "щит: углы внутри");
        check(!sh.at(99, 10, 200) && !sh.at(121, 10, 200) && !sh.at(100, 9, 200) && !sh.at(100, 47, 200) && !sh.at(100, 10, 199) && !sh.at(100, 10, 286), "щит: сразу за границей — снаружи");
        check(sh.at(-45, 2, -45) && !sh.at(-39, 2, -45), "щит: второй данж");
        check(!Dungeons.Shield.NONE.at(0, 0, 0), "пустой щит ничего не защищает");
        int[] bd = d.bounds();
        Dungeons.Shield real = new Dungeons.Shield(List.of(new int[]{ox + bd[0], oy + bd[1], oz + bd[2], ox + bd[3], oy + bd[4], oz + bd[5], ox, oy, oz}), List.of(d.mask()));
        Mark ch = null;
        for (Dungeons.Hall h : d.halls()) if (h.id == 7) ch = h.chest;
        check(real.at(ox + ch.x, oy + ch.y, oz + ch.z) && real.at(ox + door.x, oy + door.y, oz + door.z), "щит настоящего разлома закрывает сундук и дверь");
        check(!real.at(ox + bd[0] - 1, oy, oz) && !real.at(ox + bd[3], oy, oz), "щит настоящего разлома не шире границ");
        maskTests(d, ox, oy, oz, real);
        layoutTests(d);
        siteTests(d);
        sealRespawnTests(d);
        // океан: запись «мёртвой» клетки пропускается ближайшим данжем
        Dungeons.Hit near0 = Dungeons.nearest(seed, d, 100, 100, null);
        Dungeons.Hit skip = Dungeons.nearest(seed, d, 100, 100, null, k -> k.equals(d.key(Math.floorDiv(near0.x, d.cell), Math.floorDiv(near0.z, d.cell))));
        check(skip.x != near0.x || skip.z != near0.z, "клетка в океане пропускается указателем");

        mech();
        if (fails > 0) { System.out.println(fails + " проверок не прошли"); System.exit(1); }
        System.out.println("dungeon ok: " + total + " блоков, прохождение (дверь, провал, пандус, жаровни), сетка, составной данж 250, механики (ступени, лучи, зеркала, платформа, рычаг, печать)");
    }


    // ================================================================ маска клеток (MAJOR 3)
    static void maskTests(Dungeons.Def d, int ox, int oy, int oz, Dungeons.Shield real) {
        Dungeons.Mask mk = d.mask();
        int[] bd = d.bounds();
        long vol = (long) (bd[3] - bd[0]) * (bd[4] - bd[1]) * (bd[5] - bd[2]);
        System.out.println("маска Разлома: " + mk.count() + " клеток из " + vol + " в рамке (" + (100 * mk.count() / vol) + "%)");
        check(mk.count() < vol * 0.40, "маска намного уже рамки: " + mk.count() + " из " + vol);
        // каждый блок каждой части и каждый зал (с воздухом над полом) в маске
        int miss = 0;
        for (DungeonTemplate t : d.templates()) for (int i = 0; i < t.count(); i++) if (!mk.has(t.ox + t.bx[i], t.oy + t.by[i], t.oz + t.bz[i])) miss++;
        check(miss == 0, "все блоки шаблонов в маске: нет " + miss);
        for (Dungeons.Hall h : d.halls()) {
            check(mk.has(h.x, h.y, h.z) && mk.has(h.x + h.sx - 1, h.y + h.sy, h.z + h.sz - 1) && mk.has(h.x + h.sx / 2, h.y + 2, h.z + h.sz / 2), "зал " + h.id + " целиком в маске");
            for (Mark m : h.spawns) check(real.at(ox + m.x, oy + m.y, oz + m.z), "точка моба зала " + h.id + " под защитой");
        }
        // поверхность над данжем и вне его: в рамке, но вне маски — не защищена
        int outside = 0, inBoxTotal = 0;
        for (int x = bd[0]; x < bd[3]; x += 3) for (int z = bd[2]; z < bd[5]; z += 3) {
            inBoxTotal++;
            if (!real.at(ox + x, oy + bd[4] - 1, oz + z)) outside++;
        }
        check(outside > inBoxTotal * 0.9, "верх рамки почти везде не защищён: " + outside + " из " + inBoxTotal + " колонн");
        check(!real.at(ox + bd[0] + 2, oy + bd[4] - 2, oz + bd[2] + 2) && !real.at(ox + bd[3] - 3, oy + bd[1] + 1, oz + bd[5] - 3), "углы рамки вне маски — не защищены");
        // Rec.contains: рамка как предфильтр, маска для малых запасов, грубая сетка для больших
        DungeonData.Rec rec = new DungeonData.Rec("rift_t", "rift", 0, 0, ox, oy, oz);
        Mark chest = null;
        for (Dungeons.Hall h : d.halls()) if (h.id == 7) chest = h.chest;
        check(rec.contains(ox + chest.x + 0.5, oy + chest.y + 0.5, oz + chest.z + 0.5, 0), "contains: сундук логова внутри");
        double fx = ox + bd[0] + 2.5, fy = oy + bd[4] - 2.5, fz = oz + bd[2] + 2.5;
        check(!rec.contains(fx, fy, fz, 0) && !rec.contains(fx, fy, fz, 1), "contains: пустой угол рамки вне данжа");
        check(rec.contains(ox + chest.x + 0.5, oy + chest.y + 0.5, oz + chest.z + 0.5, 24), "contains: большой запас, внутри");
        check(!rec.contains(ox + bd[0] - 40, oy, oz, 24) && !rec.contains(ox, oy + bd[4] + 50, oz, 24), "contains: далеко за рамкой — вне, даже с запасом 24");
        // грубая маска: рядом с настоящей клеткой — да, в пустом углу рамки с запасом 24 — нет
        check(mk.near(chest.x, chest.y, chest.z, 24), "грубая маска: рядом с сундуком");
        // пешеходные клетки под землёй — внутри данжа (иначе игрок в проходе остался бы вне защиты и счёта)
        int walkOut = 0;
        world = Dungeons.assemble(d, 0, 0, 0);   // свой мир Разлома (после city() там мир города), двери открыты
        for (Mark dm : d.doors()) fill(dm, "minecraft:air");
        Set<Long> rr = reach(d.start().x, d.start().y, d.start().z);
        for (long k : rr) {
            int x = (int) (k >> 38), y = (int) (k & 0xFFF) - 2048, z = (int) ((k >> 12) & 0x3FFFFFF);
            if (x >= 1 << 25) x -= 1 << 26;
            if (z >= 1 << 25) z -= 1 << 26;
            if (y < -3 && !mk.has(x, y, z)) walkOut++;
        }
        check(walkOut == 0, "проходимые клетки под землёй все в маске: вне " + walkOut);
    }

    // ================================================================ подпись раскладки (MAJOR 2)
    /** Выбор высоты Разлома (крыша под землёй по всей рамке) и снятие барьера после победы над боссом. */
    static void siteTests(Dungeons.Def d) {
        check(d.roof < 0 && d.tries > 8 && d.maxVar == DungeonSite.COVER_MAX, "Разлом проверяет крышу по рамке и пробует больше точек");
        check(Dungeons.SLEEP.roof == 0, "наземный город крышу не проверяет");
        int U = DungeonSite.UNKNOWN;
        // ровная земля на уровне входа: крыша (-4) глубоко под землёй
        check(DungeonSite.exposure(64, -4, new int[]{64, 64, 65, 63}) == 0, "ровно: не торчит");
        // вход на холме, вокруг на 10 ниже: крыша торчит на 10-4... = 64-4-54=6
        check(DungeonSite.exposure(64, -4, new int[]{64, 54, 60}) == 6, "холм: крыша торчит на 6");
        check(DungeonSite.exposure(64, -4, new int[]{U, 70, 80}) == 0 && DungeonSite.exposure(64, -4, new int[0]) == 0, "неизвестные колонны и склон вверх не мешают");
        check(DungeonSite.choose(new int[]{9, 3, 1, 0}) == 2 && DungeonSite.choose(new int[]{9, 4, 6}) == 1, "берётся первая годная, иначе наименее торчащая");
        check(DungeonSite.choose(new int[]{9, 6, 12}) == -1 && DungeonSite.choose(new int[0]) == -1, "все точки хуже предела: данж не строим криво");
        // реальная рамка Разлома: колонны с подземными блоками покрывают почти всю площадь
        Dungeons.Mask mk = d.mask();
        int under = 0, all = mk.gx * mk.gz;
        for (int i = 0; i < mk.gx; i++) for (int k = 0; k < mk.gz; k++) if (mk.colMin(i, k) < d.roof) under++;
        check(under > all / 3 && under <= all, "в рамке Разлома много подземных колонн: " + under + "/" + all);
        // барьер: пока босс жив, блоки данжа под защитой, после победы нет
        DungeonData data = new DungeonData();
        DungeonData.Rec r = new DungeonData.Rec("rift_9_9", "rift", 9, 9, 1000, 64, 1000);
        r.layout = d.signature(); r.partsDone = d.allParts();
        data.recs.put(r.key, r);
        DungeonTemplate.Mark start = d.start();
        double sx = 1000 + start.x + 0.5, sy = 64 + start.y + 0.5, sz = 1000 + start.z + 0.5;
        check(r.contains(sx, sy, sz, 0) && Dungeons.guardedAt(data, sx, sy, sz, 0) == r, "босс жив: клетка данжа под защитой");
        Dungeons.markCleared(data, r);
        check(r.bossDead && Dungeons.guardedAt(data, sx, sy, sz, 0) == null && Dungeons.at(data, sx, sy, sz, 0) == r, "босс убит: барьер снят, данж по-прежнему данж (телепорты, спавн)");
        data.reset(r);
        check(!r.bossDead && Dungeons.guardedAt(data, sx, sy, sz, 0) == r, "сброс раскладки возвращает защиту");
        // политика урона по мобам данжа: огонь только от палочки, молнии проходят, стены и падения нет
        check(!DungeonSite.envBlockedForMobs("onFire", true) && !DungeonSite.envBlockedForMobs("inFire", true) && !DungeonSite.envBlockedForMobs("lightningBolt", false)
                && DungeonSite.envBlockedForMobs("onFire", false) && DungeonSite.envBlockedForMobs("inFire", false)
                && DungeonSite.envBlockedForMobs("inWall", true) && DungeonSite.envBlockedForMobs("fall", true) && DungeonSite.envBlockedForMobs("drown", false) && !DungeonSite.envBlockedForMobs(null, false),
                "огонь по мобам данжа проходит только от палочки, молнии проходят, стены и падения нет");
        // вода над кровлей: колонна считается выставленной
        check(DungeonSite.exposure(64, -4, new int[]{64, DungeonSite.WET}) == DungeonSite.WET_EXPOSURE && DungeonSite.choose(new int[]{DungeonSite.WET_EXPOSURE}) == -1, "озеро над данжем отвергает точку");
        // миграция: старая запись с зачищенным залом босса получает bossDead, без зачистки - нет, зачищенный зал без босса - нет
        DungeonTemplate.Mark none = null;
        Dungeons.Hall bossHall = null, plain = null;
        for (Dungeons.Hall h : d.halls()) { if (h.boss != null) bossHall = h; else if (plain == null) plain = h; }
        check(bossHall != null && plain != null, "в Разломе есть зал с боссом и обычный зал");
        DungeonData old = new DungeonData();
        DungeonData.Rec a = new DungeonData.Rec("rift_1_1", "rift", 1, 1, 0, 64, 0), b = new DungeonData.Rec("rift_2_2", "rift", 2, 2, 0, 64, 0), c = new DungeonData.Rec("rift_3_3", "rift", 3, 3, 0, 64, 0);
        a.hallsCleared = 1L << bossHall.id; b.hallsCleared = 1L << plain.id; c.hallsCleared = 0;
        old.recs.put(a.key, a); old.recs.put(b.key, b); old.recs.put(c.key, c);
        check(old.migrateBossDead() == 1 && a.bossDead && !b.bossDead && !c.bossDead, "миграция bossDead: только зал босса зачищен");
        check(old.migrateBossDead() == 0, "миграция идемпотентна");
    }

    static void layoutTests(Dungeons.Def d) {
        String sig = d.signature();
        check(sig.startsWith("rift_entrance+rift_mid+rift_deep:") && sig.equals(d.signature()), "подпись раскладки стабильна: " + sig);
        DungeonData data = new DungeonData();
        // запись из 2.4.0: подписи нет, 2 части, залы 1-3 отмечены, точки возрождения старые
        DungeonData.Rec old = new DungeonData.Rec("rift_0_0", "rift", 0, 0, 100, 64, 200);
        old.partsDone = 0b11; old.hallsSpawned = 0b1110; old.hallsCleared = 0b1010; old.opened.add("refl"); old.mirrors.put("1,2,3", 2);
        DungeonData.Rec cur = new DungeonData.Rec("rift_1_0", "rift", 1, 0, 900, 64, 200);
        cur.layout = sig; cur.partsDone = d.allParts(); cur.hallsCleared = 0b100; cur.opened.add("lv1");
        data.recs.put(old.key, old); data.recs.put(cur.key, cur);
        data.respawn.put("u1|rift_0_0", "1|2|3"); data.respawn.put("u2|rift_0_0", "4|5|6"); data.respawn.put("u1|rift_1_0", "7|8|9");
        int n = data.checkLayouts();
        check(n == 1, "сброшена одна запись из старого мира: " + n);
        check(old.partsDone == 0 && old.hallsSpawned == 0 && old.hallsCleared == 0 && old.opened.isEmpty() && old.mirrors.isEmpty(), "старая запись сброшена: части, залы, секреты, зеркала");
        check(!data.respawn.containsKey("u1|rift_0_0") && !data.respawn.containsKey("u2|rift_0_0") && data.respawn.containsKey("u1|rift_1_0"), "точки возрождения сброшенного данжа удалены, чужие целы");
        check(old.layout.equals(sig) && old.partsDone != d.allParts(), "после сброса подпись новая, части достраиваются заново");
        check(cur.partsDone == d.allParts() && cur.hallsCleared == 0b100 && cur.opened.contains("lv1"), "запись с совпавшей подписью не тронута");
        check(data.checkLayouts() == 0, "повторная проверка ничего не сбрасывает");
        // подпись другой раскладки (например, 2 части вместо 3) не совпадает
        Dungeons.Def two = new Dungeons.Def("rift", 768, 192, 1, "rift_entrance", "rift_mid");
        check(!two.signature().equals(sig), "другой набор частей — другая подпись");
        // номер версии раскладки: подпись зависит только от него и набора частей, а не от содержимого
        int next = d.layout + 1;
        Dungeons.Def v2 = new Dungeons.Def("rift", 768, 192, next, "rift_entrance", "rift_mid", "rift_deep");
        check(d.layout == 4 && sig.endsWith(":v" + d.layout) && v2.signature().endsWith(":v" + next) && !v2.signature().equals(sig), "повышение номера раскладки меняет подпись (Разлом 4.4: раскладка 4)");
        v2.setTemplates(d.templates());
        check(v2.signature().equals("rift_entrance+rift_mid+rift_deep:v" + next), "подпись не зависит от содержимого шаблонов");
    }

    // ================================================================ смерть у печати (MAJOR 1)
    static void sealRespawnTests(Dungeons.Def d) {
        check(d.sealed(6) && d.sealed(7) && !d.sealed(1) && !d.sealed(13), "печати есть только у залов 6 и 7");
        for (int hid : new int[]{6, 7}) {
            Dungeons.Hall hall = d.hall(hid);
            // жаровня-якорь перед залом: точка возрождения вне коробки зала, но внутри данжа и достижима (проверено в rift())
            int anchors = 0;
            for (Mark br : d.braziers()) {
                String[] o = br.a.split(",");
                int px = br.x + Integer.parseInt(o[0]), py = br.y + Integer.parseInt(o[1]), pz = br.z + Integer.parseInt(o[2]);
                if (!hall.contains(px + 0.5, py + 0.5, pz + 0.5) && Math.abs(pz - hall.z) < 80 && Math.abs(px - hall.x) < 120) anchors++;
            }
            check(anchors >= 1, "для зала " + hid + " есть якорь вне коробки зала рядом: " + anchors);
            // сброс по залу с запасом: комната у якоря (в радиусе поиска мобов) ещё считается «в зале», сама печать - нет
            double ex = hall.x + hall.sx + 10, ey = hall.y + 1, ez = hall.z + hall.sz / 2.0;
            check(!hall.contains(ex, ey, ez) && hall.containsPad(ex, ey, ez, Dungeons.ALIVE_PAD) && !hall.containsPad(ex + Dungeons.ALIVE_PAD, ey, ez, Dungeons.ALIVE_PAD),
                    "зал " + hid + ": запас ALIVE_PAD для сброса, коробка без запаса для печати");
            long bit = 1L << hid;
            long spawned = 0, cleared = 0;
            long lastHall = 0, now = 1000;
            // игрок вошёл: зал разбужен, дверь за ним закрыта
            spawned |= bit; lastHall = now;
            check(DungeonMech.sealWanted(spawned, cleared, hid, true), "зал " + hid + ": игрок внутри — печать закрыта");
            // погиб (внутри живых нет): печать открыта сразу, мобы остались
            now += 20;
            check(!DungeonMech.sealWanted(spawned, cleared, hid, false), "зал " + hid + ": после смерти печать открыта");
            // возродился у якоря (вне зала): печать открыта, пройти можно; зал ещё не сброшен
            now += 100;
            check(!DungeonMech.sealWanted(spawned, cleared, hid, false) && !Dungeons.hallStale(now, lastHall), "зал " + hid + ": после возрождения дверь открыта");
            // вернулся — снова закрылась, зал считается занятым
            lastHall = now;
            check(DungeonMech.sealWanted(spawned, cleared, hid, true), "зал " + hid + ": вошёл снова — печать закрыта");
            // не вернулся: через EMPTY_TICKS зал по залу (не по данжу) сбрасывается
            now += Dungeons.EMPTY_TICKS;
            check(!Dungeons.hallStale(now, lastHall), "зал " + hid + ": ровно EMPTY_TICKS — ещё не сброс");
            now += 1;
            check(Dungeons.hallStale(now, lastHall), "зал " + hid + ": пуст дольше EMPTY_TICKS — зал сбрасывается, хотя игрок жив в данже");
            spawned &= ~bit;
            check(!DungeonMech.sealWanted(spawned, cleared, hid, true), "зал " + hid + ": после сброса печать открыта, мобы уйдут");
            // зачистка открывает печать при любом положении игрока
            spawned |= bit; cleared |= bit;
            check(!DungeonMech.sealWanted(spawned, cleared, hid, true), "зал " + hid + ": зачищен — печать открыта");
        }
    }

    // ================================================================ механики (стенд mech_test)
    static Map<Long, String> mw;

    static boolean mSolid(int x, int y, int z) { String v = mw.get(Dungeons.pack(x, y, z)); return v == null ? y < 0 : !v.equals("minecraft:air"); }

    /** Плитки-ловушки: кристалл виден плитке, луч не упирается в стену, наступить можно, мимо — нет (по миру mw). */
    static void beams(Dungeons.Def def) {
        for (Mark m : def.marks("beams")) {
            String[] o = m.a.split(",");
            int cx = m.x + Integer.parseInt(o[0]), cy = m.y + Integer.parseInt(o[1]), cz = m.z + Integer.parseInt(o[2]);
            check(mSolid(m.x, m.y, m.z) && mSolid(cx, cy, cz), "плитка и кристалл на месте: " + m.x + "," + m.z);
            double ax = cx + 0.5, ay = cy + 0.5, az = cz + 0.5, tx = m.x + 0.5, ty = m.y + 1.2, tz = m.z + 0.5, len = Math.sqrt((tx - ax) * (tx - ax) + (ty - ay) * (ty - ay) + (tz - az) * (tz - az));
            for (double t = 0.9 / len; t <= 1.0; t += 0.1 / len)
                check(!mSolid((int) Math.floor(ax + (tx - ax) * t), (int) Math.floor(ay + (ty - ay) * t), (int) Math.floor(az + (tz - az) * t)), "луч свободен у плитки " + m.x + "," + m.z + " при t=" + t);
            check(DungeonMech.onPlate(m, m.x + 0.5, m.y + 1.0, m.z + 0.5) && DungeonMech.onPlate(m, m.x + 0.1, m.y + 1.0, m.z + 0.9), "стоящий на плитке задевает её");
            check(!DungeonMech.onPlate(m, m.x + 1.2, m.y + 1.0, m.z + 0.5) && !DungeonMech.onPlate(m, m.x + 0.5, m.y + 2.6, m.z + 0.5) && !DungeonMech.onPlate(m, m.x - 0.1, m.y + 1.0, m.z + 0.5), "сбоку и в прыжке плитку не задевают");
            check(DungeonMech.segDist(tx, ty - 0.3, tz, ax, ay, az, tx, ty, tz) <= DungeonMech.BEAM_HIT, "игрок на плитке попадает под луч");
            double hx = tx - ax, hz = tz - az, hl = Math.max(1e-6, Math.hypot(hx, hz));   // в двух блоках в стороне от луча (перпендикуляр по горизонтали)
            check(DungeonMech.segDist(tx - hz / hl * 2.0, ty - 0.3, tz + hx / hl * 2.0, ax, ay, az, tx, ty, tz) > DungeonMech.BEAM_HIT, "игрок в двух блоках сбоку мимо луча");
        }
    }

    static void mech() throws Exception {
        Dungeons.Def mt = Dungeons.MECH_TEST;
        check(mt.templates().size() == 1, "стенд механик: одна часть");
        mw = Dungeons.assemble(mt, 0, 0, 0);
        for (String t : new String[]{"steps", "beams", "mirror", "platform", "lever", "secret", "seal"}) check(!mt.marks(t).isEmpty(), "на стенде есть метка " + t);
        check(DungeonMech.TYPES.length == 12 && Dungeons.ASH.hasMech(), "метки механик: 7 прежних + колокол, струи, платформы, гейзеры, жаровни (Святилище)");
        ashMech();
        check(mt.hasMech() && Dungeons.RIFT.hasMech(), "hasMech: стенд и Разлом");
        check(mt.marks("steps").size() == 3 && mt.marks("beams").size() == 3 && mt.marks("platform").size() == 1 && mt.marks("secret").size() == 2, "число меток стенда");

        // ---- ступени: на месте воздух, провал на всю ширину зала, без ступеней не пройти, со ступенями проходится
        for (Mark m : mt.marks("steps"))
            for (int x = 0; x < m.sx; x++) for (int z = 0; z < m.sz; z++) {
                check(!mSolid(m.x + x, m.y, m.z + z), "ступень " + m.x + "," + m.z + ": в шаблоне воздух");
                check(!mSolid(m.x + x, m.y - 2, m.z + z) && mSolid(m.x + x, -6, m.z + z), "под ступенью провал с дном");
            }
        int[] saveBox = BOX;
        BOX = new int[]{-8, 44, -2, 54, -9, 8};
        Map<Long, String> saveW = world;
        world = new HashMap<>(mw);
        Mark start = mt.start();
        check(start != null && standable(start.x, start.y, start.z), "старт стенда");
        Set<Long> r = reach(start.x, start.y, start.z);
        check(r.contains(Dungeons.pack(0, 0, 1)) && !r.contains(Dungeons.pack(0, 0, 12)), "без ступеней с начала зала до выхода не дойти (провал во всю ширину)");
        for (Mark m : mt.marks("steps"))
            for (int x = 0; x < m.sx; x++) for (int z = 0; z < m.sz; z++) world.put(Dungeons.pack(m.x + x, m.y, m.z + z), "minecraft:amethyst_block");
        r = reach(start.x, start.y, start.z);
        check(r.contains(Dungeons.pack(0, 0, 12)), "когда ступени появились, выход из зала достижим");

        // ---- платформа: путь свободен, в крайних положениях стыкуется с берегами, ходит туда-сюда
        Mark pl = mt.marks("platform").get(0);
        int[] cfg = DungeonMech.platCfg(pl.a);
        check(cfg != null && cfg[0] == 1 && cfg[1] == 0 && cfg[2] == 8 && DungeonMech.platBlock(pl.a).equals("crying_obsidian"), "разбор a платформы");
        for (int o = 0; o <= cfg[2]; o++)
            for (int x = 0; x < pl.sx; x++) for (int z = 0; z < pl.sz; z++) check(!mSolid(pl.x + cfg[0] * o + x, pl.y, pl.z + cfg[1] * o + z), "путь платформы свободен на шаге " + o);
        for (int[] off : new int[][]{{0, 29, 31}, {8, 40, 39}}) {   // смещение, откуда идём, куда должны дойти
            world = new HashMap<>(mw);
            for (int x = 0; x < pl.sx; x++) for (int z = 0; z < pl.sz; z++) world.put(Dungeons.pack(pl.x + cfg[0] * off[0] + x, pl.y, pl.z + z), "minecraft:crying_obsidian");
            Set<Long> rp = reach(off[1], 0, pl.z);
            check(rp.contains(Dungeons.pack(off[2], 0, pl.z)), "платформа в положении " + off[0] + " стыкуется с берегом " + off[1]);
            check(!rp.contains(Dungeons.pack(off[0] == 0 ? 40 : 29, 0, pl.z)), "платформа в положении " + off[0] + " не соединяет берега");
        }
        DungeonMech.Plat st = new DungeonMech.Plat();
        int last = 0, moves = 0, stills = 0, maxOff = 0; boolean wentBack = false, pausedAtEnd = false;
        for (int i = 0; i < 400; i++) {
            int was = st.off, to = DungeonMech.advance(st, cfg[2]);
            if (to < 0) { stills++; continue; }
            check(Math.abs(to - was) == 1 && to >= 0 && to <= cfg[2], "шаг платформы на 1 блок внутри пути: " + was + "->" + to);
            if (to < last) wentBack = true;
            if (stills == DungeonMech.PLAT_PAUSE && (to == cfg[2] - 1 || to == 1)) pausedAtEnd = true;
            stills = 0;
            last = to; moves++; maxOff = Math.max(maxOff, to);
        }
        check(moves > 100 && wentBack && maxOff == cfg[2] && pausedAtEnd, "платформа ходит туда-сюда с паузой на краях (шагов " + moves + ")");
        world = saveW; BOX = saveBox;

        beams(mt);
        check(DungeonMech.segDist(5, 5, 5, 0, 0, 0, 0, 0, 0) > 8.6 && Math.abs(DungeonMech.segDist(1, 1, 0, 0, 0, 0, 4, 0, 0) - 1) < 1e-9, "segDist: вырожденный и обычный отрезок");

        // ---- зеркала: одна головоломка, в начале не решена, решение единственное, секрет за дверью
        Map<String, DungeonMech.Puzzle> pzs = DungeonMech.puzzles(mt);
        check(pzs.size() == 1 && pzs.containsKey("m1"), "одна головоломка m1");
        DungeonMech.Puzzle pz = pzs.get("m1");
        check(pz.srcs.size() == 1 && pz.mirs.size() == 2 && pz.rcvs.size() == 1, "излучатель, два зеркала, приёмник");
        DungeonData.Rec rec = new DungeonData.Rec("mech_0_0", "mechtest", 0, 0, 0, 0, 0);
        DungeonMech.Solid wall = (x, y, z) -> mSolid(x, y, z);
        DungeonMech.Trace t0 = new DungeonMech.Trace();
        check(!DungeonMech.solve(rec, pz, wall, t0), "в начале головоломка не решена");
        check(!t0.cells.isEmpty() && t0.hit.isEmpty(), "луч идёт (видно куда), но приёмника не достигает");
        int solutions = 0, expect = -1;
        for (int d1 = 0; d1 < 4; d1++) for (int d2 = 0; d2 < 4; d2++) {
            rec.mirrors.put(DungeonMech.mirKey(pz.mirs.get(0)), d1);
            rec.mirrors.put(DungeonMech.mirKey(pz.mirs.get(1)), d2);
            if (DungeonMech.solve(rec, pz, wall, new DungeonMech.Trace())) { solutions++; expect = d1 * 4 + d2; }
        }
        check(solutions == 1 && expect == 2 * 4 + 1, "единственное решение: первое зеркало на юг, второе на восток (" + solutions + ", " + expect + ")");
        rec.mirrors.clear();
        int turns1 = 0, turns2 = 0;
        for (int d1 = DungeonMech.dirNow(rec, pz.mirs.get(0)); d1 != 2; d1 = DungeonMech.turn(d1)) turns1++;
        for (int d2 = DungeonMech.dirNow(rec, pz.mirs.get(1)); d2 != 1; d2 = DungeonMech.turn(d2)) turns2++;
        check(turns1 == 2 && turns2 == 2, "решение достижимо поворотами на 90 градусов (" + turns1 + "+" + turns2 + ")");
        for (int i = 0; i < turns1; i++) rec.mirrors.put(DungeonMech.mirKey(pz.mirs.get(0)), DungeonMech.turn(DungeonMech.dirNow(rec, pz.mirs.get(0))));
        for (int i = 0; i < turns2; i++) rec.mirrors.put(DungeonMech.mirKey(pz.mirs.get(1)), DungeonMech.turn(DungeonMech.dirNow(rec, pz.mirs.get(1))));
        DungeonMech.Trace t1 = new DungeonMech.Trace();
        check(DungeonMech.solve(rec, pz, wall, t1) && t1.cells.size() > 15, "после поворотов луч достигает приёмника");
        check(rec.opened.add("m1") && !rec.opened.add("m1"), "решение запоминается в DungeonData (повторно не открывает)");
        DungeonMech.Trace t2 = new DungeonMech.Trace();
        check(!DungeonMech.solve(rec, pz, (x, y, z) -> wall.at(x, y, z) || (x == 21 && y == 1 && z == 5), t2), "блок на пути гасит луч");
        // секреты: рычаг s1 и головоломка m1; стена сплошная, за фальшивой стеной тайник
        for (Mark m : mt.marks("secret")) {
            for (int x = 0; x < m.sx; x++) for (int y = 0; y < m.sy; y++) for (int z = 0; z < m.sz; z++) check(mSolid(m.x + x, m.y + y, m.z + z), "секрет " + m.a + ": стена сплошная");
            check(m.a.equals("m1") || m.a.equals("s1"), "id секрета известен: " + m.a);
        }
        Mark lever = mt.marks("lever").get(0);
        check(mw.get(Dungeons.pack(lever.x, lever.y, lever.z)).startsWith("minecraft:lever") && lever.a.equals("s1"), "рычаг s1 на месте");
        check(!mSolid(4, 0, 33) && !mSolid(5, 1, 36), "за фальшивой стеной тайник");

        // ---- дверь логова: проём открыт в шаблоне, закрывается по состоянию зала
        Mark seal = mt.marks("seal").get(0);
        Dungeons.Hall lair = mt.halls().get(0);
        check(mt.halls().size() == 1 && lair.id == 7 && lair.spawns.size() == 4 && lair.chest != null, "логово: зал 7, 4 моба, сундук");
        check(seal.z + seal.sz <= lair.z && !lair.contains(seal.x, seal.y, seal.z), "дверь стоит перед залом, а не в нём");
        for (int x = 0; x < seal.sx; x++) for (int y = 0; y < seal.sy; y++) check(!mSolid(seal.x + x, seal.y + y, seal.z), "проём двери открыт в шаблоне");
        check(!DungeonMech.sealWanted(0, 0, 7, true) && DungeonMech.sealWanted(1L << 7, 0, 7, true) && !DungeonMech.sealWanted(1L << 7, 1L << 7, 7, true) && !DungeonMech.sealWanted(1L << 6, 0, 7, true) && DungeonMech.sealWanted(1L << 40, 0, 40, true) && !DungeonMech.sealWanted(1L << 40, 1L << 40, 40, true), "дверь закрыта только пока зал разбужен и не зачищен");
        check(!DungeonMech.sealWanted(1L << 7, 0, 7, false), "в зале никого — дверь открыта");
        check(!Dungeons.keepCell(null, Integer.MAX_VALUE, 12) && !Dungeons.keepCell(new int[]{0, 0, 0}, 13, 12) && Dungeons.keepCell(new int[]{0, 0, 0}, 12, 12),
                "клетка годна, только если есть точка с перепадом не больше maxVar (иначе dead при полной оценке, ожидание при пропуске)");
        check(Integer.parseInt(seal.a.split(",")[0]) == lair.id, "печать относится к залу 7");
    }
}
