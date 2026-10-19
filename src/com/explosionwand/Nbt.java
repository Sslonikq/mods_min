package com.explosionwand;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Минимальный читатель NBT (gzip), без классов Minecraft: читает шаблоны данжей и годится для тестов. */
final class Nbt {
    private Nbt() {}

    /** Читает сжатый файл; корень — compound. Значения: Byte, Short, Integer, Long, Float, Double, String, byte[]/int[]/long[], List, Map. */
    static Map<String, Object> readGz(InputStream raw) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(raw)))) {
            if (in.readByte() != 10) throw new IOException("корень NBT не compound");
            in.readUTF();
            return compound(in);
        }
    }

    private static Map<String, Object> compound(DataInputStream in) throws IOException {
        Map<String, Object> m = new LinkedHashMap<>();
        while (true) {
            int t = in.readByte();
            if (t == 0) return m;
            String name = in.readUTF();
            m.put(name, value(in, t));
        }
    }

    private static Object value(DataInputStream in, int type) throws IOException {
        switch (type) {
            case 1: return in.readByte();
            case 2: return in.readShort();
            case 3: return in.readInt();
            case 4: return in.readLong();
            case 5: return in.readFloat();
            case 6: return in.readDouble();
            case 7: { byte[] a = new byte[in.readInt()]; in.readFully(a); return a; }
            case 8: return in.readUTF();
            case 9: {
                int et = in.readByte(), n = in.readInt();
                List<Object> l = new ArrayList<>(Math.max(0, n));
                for (int i = 0; i < n; i++) l.add(value(in, et));
                return l;
            }
            case 10: return compound(in);
            case 11: { int[] a = new int[in.readInt()]; for (int i = 0; i < a.length; i++) a[i] = in.readInt(); return a; }
            case 12: { long[] a = new long[in.readInt()]; for (int i = 0; i < a.length; i++) a[i] = in.readLong(); return a; }
            default: throw new IOException("неизвестный тип NBT " + type);
        }
    }

    // ---- удобные геттеры ----
    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object o) { return (Map<String, Object>) o; }
    @SuppressWarnings("unchecked")
    static List<Object> list(Object o) { return o == null ? new ArrayList<>() : (List<Object>) o; }
    static int[] ints(Object o) {
        List<Object> l = list(o);
        int[] a = new int[l.size()];
        for (int i = 0; i < a.length; i++) a[i] = ((Number) l.get(i)).intValue();
        return a;
    }
}
