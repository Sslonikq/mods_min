package com.explosionwand;

import com.explosionwand.DungeonTemplate.Mark;

import java.util.*;

/**
 * Конвейер данжей (фаза 4.1), чистая логика на заглушках:
 *  - шаблоны .nbt читаются, части не пересекаются, метки на месте;
 *  - тестовый разлом «честно проходится»: пешеходный поиск от старта до сундука (дверь открыта), без двери и без ступеней — нет;
 *  - упавший в провал выбирается обратно по пандусу;
 *  - расстановка по сетке детерминирована и укладывается в клетку;
 *  - составной данж в 250 блоков собирается из 4 частей, части ставятся лениво по мере приближения игрока.
 */
public class DungeonTest {
    static int fails = 0;

    static void check(boolean ok, String msg) { if (!ok) { fails++; System.out.println("FAIL: " + msg); } }

    // ---------- пешеходная модель ----------
    static Map<Long, String> world;

    static boolean passable(String s) { return s.startsWith("minecraft:air") || s.contains("lantern") || s.contains("amethyst_cluster") || s.contains("campfire"); }

    /** Не заданные клетки — плоская земля: ниже y=0 твёрдо, выше воздух. */
    static boolean solid(int x, int y, int z) {
        String s = world.get(Dungeons.pack(x, y, z));
        return s == null ? y < 0 : !passable(s);
    }

    static boolean free(int x, int y, int z) { return !solid(x, y, z); }
    /** Поверхность бесконечна, поэтому поиск ограничен окрестностью данжа. */
    static boolean inBox(int x, int y, int z) { return x >= -30 && x <= 30 && z >= -30 && z <= 110 && y >= -45 && y <= 12; }
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

    public static void main(String[] a) throws Exception {
        Dungeons.Def d = Dungeons.RIFT;
        List<DungeonTemplate> tp = d.templates();
        check(tp.size() == 2, "две части у тестового разлома");

        // ---- шаблоны и метки
        Set<Long> seen0 = new HashSet<>();
        int total = 0;
        for (DungeonTemplate t : tp) {
            total += t.count();
            check(t.count() > 500, t.name + ": блоков " + t.count());
            for (int i = 0; i < t.count(); i++) {
                check(t.bx[i] >= 0 && t.bx[i] < t.sx && t.by[i] >= 0 && t.by[i] < t.sy && t.bz[i] >= 0 && t.bz[i] < t.sz, t.name + ": блок вне size");
                check(t.bs[i] >= 0 && t.bs[i] < t.palette.length, t.name + ": индекс палитры");
                check(seen0.add(Dungeons.pack(t.ox + t.bx[i], t.oy + t.by[i], t.oz + t.bz[i])), t.name + ": пересечение частей");
            }
            for (String p : t.palette) check(p.startsWith("minecraft:"), "палитра: " + p);
        }
        int[] b = d.bounds();
        System.out.println("разлом: " + total + " блоков в 2 частях, габариты " + (b[3] - b[0]) + "x" + (b[4] - b[1]) + "x" + (b[5] - b[2]));
        check(d.halls().size() == 3, "три зала");
        check(d.doors().size() == 1 && d.braziers().size() == 2 && d.start() != null, "дверь, две жаровни, старт");
        int chests = 0;
        for (Dungeons.Hall h : d.halls()) { check(h.spawns.size() >= 4, "в зале " + h.id + " мобов: " + h.spawns.size()); if (h.chest != null) chests++; }
        check(chests == 1, "один сундук награды");
        check(d.halls().get(2).chest != null, "сундук в логове");

        // ---- прохождение: данж собран в мир со смещением, чтобы заодно проверить сдвиг координат
        int ox = 1234, oy = 70, oz = -987;
        world = Dungeons.assemble(d, 0, 0, 0);     // локальные координаты — земля плоская на y<0
        Mark door = d.doors().get(0), st = d.start();
        Map<Long, String> full = new HashMap<>(world);
        Map<Long, String> shifted = Dungeons.assemble(d, ox, oy, oz);
        check(shifted.size() == full.size(), "сдвиг не меняет число блоков");
        check(shifted.containsKey(Dungeons.pack(ox + door.x, oy + door.y, oz + door.z)), "дверь на месте после сдвига");

        Mark chest = d.halls().get(2).chest;
        // дверь закрыта: к сундуку не попасть
        Set<Long> r = reach(st.x, st.y, st.z);
        check(r.contains(Dungeons.pack(st.x, st.y, st.z)), "старт — место, где можно стоять");
        check(!reachNear(r, chest.x, chest.y, chest.z), "при закрытой двери сундук недостижим");
        // дверь открыта
        for (int x = 0; x < door.sx; x++) for (int y = 0; y < door.sy; y++) for (int z = 0; z < door.sz; z++) world.put(Dungeons.pack(door.x + x, door.y + y, door.z + z), "minecraft:air");
        r = reach(st.x, st.y, st.z);
        check(reachNear(r, chest.x, chest.y, chest.z), "с открытой дверью до сундука можно дойти (лестница, провал по ступеням, залы)");
        for (Dungeons.Hall h : d.halls()) for (Mark m : h.spawns) check(r.contains(Dungeons.pack(m.x, m.y, m.z)), "точка моба недостижима: " + m.t + " " + m.x + "," + m.y + "," + m.z);
        for (Mark br : d.braziers()) {
            String[] o = br.a.split(",");
            int rx = br.x + Integer.parseInt(o[0]), ry = br.y + Integer.parseInt(o[1]), rz = br.z + Integer.parseInt(o[2]);
            check(r.contains(Dungeons.pack(rx, ry, rz)), "точка возрождения жаровни недостижима: " + rx + "," + ry + "," + rz);
            check(world.get(Dungeons.pack(br.x, br.y, br.z)).contains("campfire"), "на метке жаровни стоит костёр");
        }
        // без кристальных ступеней провал не пройти
        Map<Long, String> noSteps = new HashMap<>(world);
        for (Map.Entry<Long, String> e : world.entrySet()) {
            if (e.getValue().equals("minecraft:amethyst_block")) {
                long k = e.getKey();
                int y = (int) (k & 0xFFF) - 2048;
                if (y == -21 && ((k >> 12) & 0x3FFFFFF) >= 27 && ((k >> 12) & 0x3FFFFFF) <= 33) noSteps.put(k, "minecraft:air");
            }
        }
        Map<Long, String> keep = world; world = noSteps;
        r = reach(st.x, st.y, st.z);
        check(!reachNear(r, chest.x, chest.y, chest.z), "без ступеней провал непроходим (иначе это не ловушка)");
        // упавший в провал возвращается по пандусу
        check(r.contains(Dungeons.pack(-9, -20, 25)), "северный край провала достижим");
        Set<Long> rp = reach(0, -26, 29);
        check(rp.contains(Dungeons.pack(0, 0, -8)), "упавший в провал выбирается наверх по пандусу");
        world = keep;

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
        Dungeons.Def big = new Dungeons.Def("big", 1024, 200, "p0", "p1", "p2", "p3");
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
        Dungeons.Shield real = new Dungeons.Shield(List.of(new int[]{ox + bd[0], oy + bd[1], oz + bd[2], ox + bd[3], oy + bd[4], oz + bd[5]}));
        Mark ch = d.halls().get(2).chest;
        check(real.at(ox + ch.x, oy + ch.y, oz + ch.z) && real.at(ox + door.x, oy + door.y, oz + door.z), "щит настоящего разлома закрывает сундук и дверь");
        check(!real.at(ox + bd[0] - 1, oy, oz) && !real.at(ox + bd[3], oy, oz), "щит настоящего разлома не шире границ");
        // океан: запись «мёртвой» клетки пропускается ближайшим данжем
        Dungeons.Hit near0 = Dungeons.nearest(seed, d, 100, 100, null);
        Dungeons.Hit skip = Dungeons.nearest(seed, d, 100, 100, null, k -> k.equals(d.key(Math.floorDiv(near0.x, d.cell), Math.floorDiv(near0.z, d.cell))));
        check(skip.x != near0.x || skip.z != near0.z, "клетка в океане пропускается указателем");

        if (fails > 0) { System.out.println(fails + " проверок не прошли"); System.exit(1); }
        System.out.println("dungeon ok: " + total + " блоков, прохождение (дверь, провал, пандус, жаровни), сетка, составной данж 250");
    }
}
