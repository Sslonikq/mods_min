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

    static boolean passable(String s) { return s.startsWith("minecraft:air") || s.contains("lantern") || s.contains("amethyst_cluster") || s.contains("campfire") || s.equals("minecraft:water") || s.startsWith("minecraft:lever"); }

    /** Не заданные клетки — плоская земля: ниже y=0 твёрдо, выше воздух. */
    static boolean solid(int x, int y, int z) {
        String s = world.get(Dungeons.pack(x, y, z));
        return s == null ? y < 0 : !passable(s);
    }

    static boolean free(int x, int y, int z) { return !solid(x, y, z); }
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
    static final Set<String> LIGHT = Set.of("shroomlight", "sea_lantern", "lantern", "crying_obsidian", "glowstone", "soul_campfire", "soul_lantern");

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
            if (h.id <= 13) check(h.spawns.size() >= 4, "в зале " + h.id + " мобов: " + h.spawns.size());
            if (h.chest != null) {
                chests++;
                String[] sp = Dungeons.chestSpec(h.chest.a);
                check(!sp[0].isEmpty() && chestNames.add(sp[0]) && Integer.parseInt(sp[1]) >= 1 && Integer.parseInt(sp[1]) <= 3, "название сундука: " + h.chest.a);
            }
        }
        check(ids.equals(new TreeSet<>(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16))), "залы 1..16: " + ids);
        check(chests == 12 && spawns == 88, "сундуков " + chests + ", мобов " + spawns);
        for (Dungeons.Hall h : d.halls()) if (h.id == 7) check(h.chest != null && h.spawns.size() >= 8, "логово: сундук и мобы");
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

    public static void main(String[] a) throws Exception {
        Dungeons.Def d = Dungeons.RIFT;
        rift();
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
        check(d.layout == 2 && sig.endsWith(":v" + d.layout) && v2.signature().endsWith(":v" + next) && !v2.signature().equals(sig), "повышение номера раскладки меняет подпись (Разлом 4.3: раскладка 2)");
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
            int bit = 1 << hid;
            int spawned = 0, cleared = 0;
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
        for (String t : DungeonMech.TYPES) check(!mt.marks(t).isEmpty(), "на стенде есть метка " + t);
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
        check(!DungeonMech.sealWanted(0, 0, 7, true) && DungeonMech.sealWanted(1 << 7, 0, 7, true) && !DungeonMech.sealWanted(1 << 7, 1 << 7, 7, true) && !DungeonMech.sealWanted(1 << 6, 0, 7, true), "дверь закрыта только пока зал разбужен и не зачищен");
        check(!DungeonMech.sealWanted(1 << 7, 0, 7, false), "в зале никого — дверь открыта");
        check(Integer.parseInt(seal.a.split(",")[0]) == lair.id, "печать относится к залу 7");
    }
}
