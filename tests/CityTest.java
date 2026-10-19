package com.explosionwand;

import java.util.*;

/**
 * Город Спящих (4.5), чистые правила без мира: сонный туман (порог «стоит», стадии по времени, зона), мобы-заглушки, особая добыча
 * и книги Архимага (кавычки в команде item replace). Проходимость и расстановка города — в DungeonTest.
 */
public class CityTest {
    static int fails = 0;

    static void check(boolean ok, String msg) { if (!ok) { fails++; System.out.println("FAIL: " + msg); } }

    static int count(String s, String sub) { int n = 0, i = 0; while ((i = s.indexOf(sub, i)) >= 0) { n++; i += sub.length(); } return n; }

    public static void main(String[] a) {
        // ---- «стоит на месте»: за 10 тиков бега/ходьбы игрок уходит дальше 0,5 блока, на месте и в прыжке — нет
        check(City.still(0, 0) && City.still(0.3, 0.3) && City.still(-0.49, 0), "почти не двигался: стоит");
        check(!City.still(0.5, 0.1) && !City.still(0, -0.7) && !City.still(2.1, 0), "идёт: не стоит (ходьба 4,3 б/с = 2,1 за период, крадётся 0,65)");

        // ---- стадии: >3 с замедление, >6 с слепота (опрос раз в SAMPLE тиков)
        check(City.stage(0) == 0 && City.stage(50) == 0 && City.stage(60) == 1 && City.stage(110) == 1 && City.stage(120) == 2, "стадии тумана по времени");
        int slow = -1, blind = -1, warn = -1, t = 0;
        for (int i = 1; i <= 30; i++) {
            t += City.SAMPLE;
            if (warn < 0 && t >= City.WARN) warn = t;
            if (slow < 0 && City.stage(t) >= 1) slow = t;
            if (blind < 0 && City.stage(t) >= 2) blind = t;
        }
        check(warn > 0 && warn < slow && slow == 60 && blind == 120, "предупреждение " + warn + ", замедление " + slow + ", слепота " + blind + " тиков стояния");
        check(City.SLOW == 60 && City.BLIND == 120 && City.SAMPLE == 10 && City.WARN < City.SLOW, "пороги: 3 с и 6 с, опрос раз в 10 тиков");

        // ---- зона тумана: внутри стены, у земли и стен; склеп (y < -1,5), рвы и небо над стенами — нет
        check(City.inTown(0, 0, 0) && City.inTown(42.4, 0, -42.4) && City.inTown(-42, 5, 10), "на улицах туман");
        check(!City.inTown(43, 0, 0) && !City.inTown(0, 0, 44) && !City.inTown(0, 13, 0) && !City.inTown(0, -8, 20), "за стеной, над городом и в склепе тумана нет");

        // ---- обход ворот: стена, её верх и подземелье города — внутри; ворота снаружи (старт z=47) и далёкая земля — нет
        check(City.inWalls(0, 10, 44) && City.inWalls(45, 3, -45) && City.inWalls(0, 20, 0) && City.inWalls(-30, -10, 5), "в стене и над ней: возврат к воротам");
        check(!City.inWalls(0, 0, 47) && !City.inWalls(46.5, 0, 0) && !City.inWalls(0, 30, 0) && !City.inWalls(0, -20, 0), "старт у ворот и далёкое небо: не трогаем");

        // ---- запасные точки города: рядом с основной (±48), чтобы чанки были загружены вместе с ней
        long[] seeds = {1L, 42L, -7L, 123456789L};
        for (long sd : seeds) for (int cx = -8; cx < 8; cx++) for (int cz = -8; cz < 8; cz++) {
            int[] s0 = Dungeons.site(sd, Dungeons.SLEEP, cx, cz, 0);
            for (int at = 1; at < 8; at++) {
                int[] s1 = Dungeons.site(sd, Dungeons.SLEEP, cx, cz, at);
                check(Math.abs(s1[0] - s0[0]) <= 48 && Math.abs(s1[1] - s0[1]) <= 48, "запасная точка " + at + " дальше 48 от основной");
            }
        }

        // ---- мобы-заглушки: ванильные хосты с именами, неизвестный вид — лунатик
        Set<String> hosts = Set.of("husk", "vex", "skeleton");
        for (String id : List.of("stub:sleeper", "stub:stitch", "stub:dreamer", "stub:bookguard", "stub:statue", "stub:unknown")) {
            String[] s = City.stub(id);
            check(hosts.contains(s[0]) && s[1].contains("CustomName"), "заглушка " + id + ": " + s[0]);
            check(!s[1].contains("\n") && count(s[1], "[") == count(s[1], "]") && count(s[1], "{") == count(s[1], "}"), "скобки NBT заглушки " + id);
        }
        check(City.stub("stub:stitch")[0].equals("husk") && City.stub("stub:dreamer")[0].equals("vex") && City.stub("stub:statue")[0].equals("skeleton"), "хосты заглушек");
        check(City.STUB.equals("stub:") && "stub:sleeper".startsWith(City.STUB), "префикс заглушек");

        // ---- особая добыча и книги
        check(City.extra(21).equals(List.of("explosionwand:temple_seal")) && City.extra(19).size() == 1 && City.extra(25).size() == 1 && City.extra(1).isEmpty() && City.extra(20).isEmpty(),
                "Печать Храма в склепе (21), книги в библиотеке (19) и ложе Архимага (25), больше ничего");
        for (String book : List.of(City.BOOK_LIBRARY, City.BOOK_LAST)) {
            int pages = count(book, "{\"text\":\"");
            check(book.startsWith("minecraft:written_book{title:\"") && book.endsWith("]}") && pages >= 2, "книга: страницы " + pages);
            check(count(book, "'") == pages * 2, "в тексте книги нет апострофов (страниц " + pages + ", апострофов " + count(book, "'") + ")");
            check(count(book, "{") == count(book, "}") && count(book, "[") == count(book, "]"), "скобки книги");
            for (String chunk : book.split("\\{\"text\":\"")) if (chunk.contains("\"}'")) check(chunk.substring(0, chunk.indexOf("\"}'")).indexOf('"') < 0, "в странице нет двойных кавычек");
            check(book.length() < 1800, "книга помещается в команду: " + book.length());
        }

        // ---- каждая метка spawn из шаблонов города — известная заглушка
        try {
            for (Dungeons.Hall h : Dungeons.SLEEP.halls())
                for (DungeonTemplate.Mark m : h.spawns) check(m.a.startsWith(City.STUB) && !City.stub(m.a)[1].isEmpty(), "метка города: " + m.a);
        } catch (IllegalStateException e) { check(false, "шаблоны города: " + e.getMessage()); }

        if (fails > 0) { System.out.println(fails + " проверок не прошли"); System.exit(1); }
        System.out.println("city ok: туман (стояние 3 с / 6 с, зона), заглушки мобов, добыча и книги");
    }
}
