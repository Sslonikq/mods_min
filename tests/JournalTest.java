package com.explosionwand;

import java.util.List;

/** Страницы Дневника Архимага открываются по прогрессу и ничего не раскрывают раньше времени. */
public class JournalTest {
    static void check(boolean ok, String m) { if (!ok) { System.out.println("FAIL: " + m); System.exit(1); } }

    public static void main(String[] a) {
        check(Journal.pages(false, false, false, false, false).equals(List.of(0, 1)), "start: only intro and first shard");
        check(Journal.pages(true, false, false, false, false).equals(List.of(0, 1, 2)), "rift page");
        check(Journal.pages(true, true, false, false, false).equals(List.of(0, 1, 2, 3)), "monolith page");
        check(Journal.pages(true, true, true, false, false).equals(List.of(0, 1, 2, 3, 4)), "phoenix page");
        check(Journal.pages(false, false, false, true, false).equals(List.of(0, 1, 4)), "key opens the key page");
        check(Journal.pages(true, true, true, true, true).size() == Journal.PAGES, "all pages");
        check(!Journal.pages(true, true, true, true, false).contains(5), "portal page only after lighting");
        System.out.println("JournalTest OK");
    }
}
