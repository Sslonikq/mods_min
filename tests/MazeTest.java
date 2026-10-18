package com.explosionwand;

/** Генератор лабиринта Некто: 300 прогонов, выход всегда достижим, длина пути в разумных пределах. */
public class MazeTest {
    public static void main(String[] a) {
        int runs = 300, tot = 0, min = Integer.MAX_VALUE, max = 0;
        for (int run = 0; run < runs; run++) {
            Maze.Session s = new Maze.Session(null, null, 0);
            Maze.generate(s);
            int L = Maze.bfs(s.open, s.sx, s.sz)[s.ex][s.ez];
            if (L < 0) { System.out.println("FAIL: exit unreachable on run " + run); System.exit(1); }
            if (L < 40) { System.out.println("FAIL: exit too close (" + L + ") on run " + run); System.exit(1); }
            tot += L; min = Math.min(min, L); max = Math.max(max, L);
        }
        System.out.println("maze ok: path to exit avg " + tot / runs + ", min " + min + ", max " + max + " blocks");
    }
}
