package com.explosionwand;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Часть данжа: шаблон блоков в формате структур Minecraft (.nbt) плюс свои теги ew_origin и ew_marks.
 * Лежит в data/explosionwand/structures/<имя>.nbt, создаётся tools/gen_dungeons.py. Чистая логика, без классов Minecraft.
 */
final class DungeonTemplate {
    /** Метка: start, door, brazier, hall, spawn, chest. Координаты — относительно угла части (после load) или данжа (после shift). */
    static final class Mark {
        final String t, a; final int x, y, z, sx, sy, sz;
        Mark(String t, String a, int x, int y, int z, int sx, int sy, int sz) {
            this.t = t; this.a = a; this.x = x; this.y = y; this.z = z; this.sx = sx; this.sy = sy; this.sz = sz;
        }
        Mark shift(int dx, int dy, int dz) { return new Mark(t, a, x + dx, y + dy, z + dz, sx, sy, sz); }
        boolean inside(int px, int py, int pz) { return px >= x && px < x + sx && py >= y && py < y + sy && pz >= z && pz < z + sz; }
    }

    final String name;
    /** Смещение угла части от начала данжа. */
    final int ox, oy, oz;
    final int sx, sy, sz;
    final String[] palette;
    final int[] bx, by, bz, bs;
    final List<Mark> marks;

    DungeonTemplate(String name, int ox, int oy, int oz, int sx, int sy, int sz, String[] palette, int[] bx, int[] by, int[] bz, int[] bs, List<Mark> marks) {
        this.name = name; this.ox = ox; this.oy = oy; this.oz = oz; this.sx = sx; this.sy = sy; this.sz = sz;
        this.palette = palette; this.bx = bx; this.by = by; this.bz = bz; this.bs = bs; this.marks = marks;
    }

    int count() { return bx.length; }

    /** Метки в координатах данжа. */
    List<Mark> marksInDungeon() {
        List<Mark> out = new ArrayList<>();
        for (Mark m : marks) out.add(m.shift(ox, oy, oz));
        return out;
    }

    static DungeonTemplate parse(String name, Map<String, Object> root) {
        int[] size = Nbt.ints(root.get("size")), org = Nbt.ints(root.get("ew_origin"));
        List<Object> pal = Nbt.list(root.get("palette"));
        String[] palette = new String[pal.size()];
        for (int i = 0; i < palette.length; i++) {
            Map<String, Object> e = Nbt.map(pal.get(i));
            StringBuilder sb = new StringBuilder((String) e.get("Name"));
            if (e.get("Properties") != null) {
                sb.append('[');
                boolean first = true;
                for (Map.Entry<String, Object> p : Nbt.map(e.get("Properties")).entrySet()) {
                    if (!first) sb.append(',');
                    sb.append(p.getKey()).append('=').append(p.getValue());
                    first = false;
                }
                sb.append(']');
            }
            palette[i] = sb.toString();
        }
        List<Object> bl = Nbt.list(root.get("blocks"));
        int n = bl.size();
        int[] bx = new int[n], by = new int[n], bz = new int[n], bs = new int[n];
        for (int i = 0; i < n; i++) {
            Map<String, Object> b = Nbt.map(bl.get(i));
            int[] p = Nbt.ints(b.get("pos"));
            bx[i] = p[0]; by[i] = p[1]; bz[i] = p[2]; bs[i] = ((Number) b.get("state")).intValue();
        }
        List<Mark> marks = new ArrayList<>();
        for (Object o : Nbt.list(root.get("ew_marks"))) {
            Map<String, Object> m = Nbt.map(o);
            int[] p = Nbt.ints(m.get("p")), s = Nbt.ints(m.get("s"));
            marks.add(new Mark((String) m.get("t"), (String) m.get("a"), p[0], p[1], p[2], s[0], s[1], s[2]));
        }
        return new DungeonTemplate(name, org[0], org[1], org[2], size[0], size[1], size[2], palette, bx, by, bz, bs, marks);
    }

    static final String DIR = "data/explosionwand/structures/";

    /** Из jar/classpath; при запуске тестов из папки проекта — из resources/. */
    static InputStream open(String name) throws IOException {
        InputStream in = DungeonTemplate.class.getResourceAsStream("/" + DIR + name + ".nbt");
        if (in != null) return in;
        File f = new File("resources/" + DIR + name + ".nbt");
        if (f.isFile()) return new FileInputStream(f);
        throw new IOException("нет шаблона данжа " + name);
    }

    static DungeonTemplate load(String name) throws IOException {
        try (InputStream in = open(name)) { return parse(name, Nbt.readGz(in)); }
    }
}
