package com.explosionwand;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

/**
 * Город Спящих (фаза 4.5): всё, что нужно городу сверх общего конвейера данжей.
 *  - Сонный туман: на улицах (под открытым небом внутри стены) игрок, простоявший на месте дольше 3 секунд, замедляется,
 *    дольше 6 секунд — слепнет. Стоять нельзя, надо двигаться; в домах, храме, склепе и во рву у ворот тумана нет.
 *  - Мобы Города (Лунатик, Дремотник, Страж Шва, Ночная моль, Сновидец, Книжный страж, Статуя-страж, Колыбельщик) — метки spawn explosionwand:<вид>,
 *    см. CityMobs и CityMobsB (заглушки stub:<вид> убраны в 4.6); Туманный странник живёт в обычном мире (CityMobsB.tickWorld).
 *  - Особая добыча сундуков (книги Архимага, Печать Храма).
 * Чистые правила (пороги тумана, зона) вынесены в статические методы для теста.
 */
final class City {
    private City() {}

    /** Внутренняя сторона стены: туман действует при |x|,|z| не больше этого значения (в координатах данжа). */
    static final int INNER = 42;
    /** Период опроса (тиков), порог «стоит» (0,5 с ходьбы медленнее 0,5 блока), пороги стадий тумана в тиках. */
    static final int SAMPLE = 10, WARN = 40, SLOW = 60, BLIND = 120;
    static final double MOVE = 0.5;

    // ================================================================ чистая логика
    /** Игрок почти не сдвинулся за период (dx, dz — смещение по горизонтали). */
    static boolean still(double dx, double dz) { return dx * dx + dz * dz < MOVE * MOVE; }

    /** Стадия тумана по времени без движения (тики): 0 — нет, 1 — замедление, 2 — замедление и слепота. */
    static int stage(int stillTicks) { return stillTicks >= BLIND ? 2 : stillTicks >= SLOW ? 1 : 0; }

    /** Точка внутри стены по горизонтали и не выше стен (координаты данжа). */
    static boolean inTown(double rx, double ry, double rz) {
        return Math.abs(rx) <= INNER + 0.5 && Math.abs(rz) <= INNER + 0.5 && ry >= -1.5 && ry <= 12;
    }

    /** Точка в стене, на ней и над ней, до фундамента (координаты данжа): сюда без открытых ворот попадать нельзя. */
    static boolean inWalls(double rx, double ry, double rz) {
        return Math.abs(rx) <= 45.5 && Math.abs(rz) <= 45.5 && ry >= -11.5 && ry <= 22;
    }

    /** Особая добыча сундука зала Города (мировой набор тира добавляется отдельно). Печать Храма лежит в сундуке склепа. */
    static List<String> extra(int hallId) {
        switch (hallId) {
            case 19: return List.of(BOOK_LIBRARY);
            case 21: return List.of("explosionwand:temple_seal");
            case 25: return List.of(BOOK_LAST);
            default: return List.of();
        }
    }

    private static String page(String text) { return "'{\"text\":\"" + text + "\"}'"; }

    /** Записи Архимага (библиотека): про город, туман и склеп. Без апострофов и кавычек в тексте (команда item replace). */
    static final String BOOK_LIBRARY = "minecraft:written_book{title:\"Записи Архимага: Город\",author:\"Последний Архимаг\",pages:["
            + page("Мы построили этот город для тех, кто не вынес бы Печати. Их усыпили, чтобы они не слышали, что творится наверху. Улицы затянуло туманом: он не даёт спящим проснуться, а чужакам не даёт стоять на месте.") + ","
            + page("Стражи Шва зашили сон крепче любой стены. Но Монолит в храме слишком долго слушал Печать. Теперь он видит сны сам, и эти сны рвутся наружу.") + ","
            + page("Если ты дочитал до этих строк, ты пришёл за Печатью Храма. Она лежит в склепе под домом, над которым горит лунный знак. Ищи его на фресках у ворот и на карте посреди площади.") + "]}";

    /** Последняя запись (тайник за склепом): намёк на следующий данж. */
    static final String BOOK_LAST = "minecraft:written_book{title:\"Последняя запись\",author:\"Последний Архимаг\",pages:["
            + page("Я остался в Городе один. Спящие дышат ровно, и это единственное, что меня радует. Печать Храма открывает храм, но не закрывает того, что внутри.") + ","
            + page("Если Монолит падёт, его осколок укажет путь дальше, к огню под землёй. Но берегись: то, что горит там, проснулось раньше срока.") + "]}";

    // ================================================================ сонный туман
    /** UUID игрока -> {x, z последнего опроса, тики без движения}. */
    static final Map<UUID, double[]> TRACK = new HashMap<>();

    static void clear() { TRACK.clear(); }

    /** Раз в SAMPLE тиков (вызывает Dungeons.tick0 в блоке «каждые 10 тиков»). */
    static void tick(ServerLevel sl, DungeonData data, List<ServerPlayer> ps) {
        Set<UUID> seen = new HashSet<>();
        for (DungeonData.Rec rec : data.recs.values()) {
            Dungeons.Def d = rec.def();
            if (d != Dungeons.SLEEP || rec.partsDone != d.allParts()) continue;
            Boolean gate = null;   // читаем блок ворот лениво: только когда кто-то у стены
            for (ServerPlayer p : ps) {
                if (!p.m_6084_() || p.m_7500_() || p.m_5833_()) continue;
                double rx = p.m_20185_() - rec.x, ry = p.m_20186_() - rec.y, rz = p.m_20189_() - rec.z;
                if (inWalls(rx, ry, rz) && !(gate != null ? gate : (gate = gateOpen(sl, rec, d)))) {   // обход ворот (столб, прыжок через ров, элитры): назад к воротам
                    DungeonTemplate.Mark st = d.start();
                    if (st != null) {
                        p.m_8999_(sl, rec.x + st.x + 0.5, rec.y + st.y, rec.z + st.z + 0.5, p.m_146908_(), p.m_146909_());
                        p.f_19789_ = 0.0F;
                        p.m_5661_(Component.m_237115_("explosionwand.city.gate_closed"), true);
                    }
                    continue;
                }
                if (!inTown(rx, ry, rz)) continue;
                // под открытым небом: в доме, храме и под землёй тумана нет
                if (!sl.m_45527_(new BlockPos((int) Math.floor(p.m_20185_()), (int) Math.floor(p.m_20186_() + 1.0), (int) Math.floor(p.m_20189_())))) continue;
                seen.add(p.m_20148_());
                fog(sl, p);
            }
        }
        TRACK.keySet().retainAll(seen);
    }

    /** Ворота открыты, если центр метки door «sleep» стал воздухом (Осколок Сна; после открытия так остаётся навсегда). Нет метки — считаем открытыми. */
    private static boolean gateOpen(ServerLevel sl, DungeonData.Rec rec, Dungeons.Def d) {
        for (DungeonTemplate.Mark m : d.doors()) {
            if (!m.a.equals("sleep")) continue;
            BlockPos gp = new BlockPos(rec.x + m.x + m.sx / 2, rec.y + m.y + m.sy / 2, rec.z + m.z + m.sz / 2);
            if (!sl.m_46805_(gp)) return true;   // не загружен: не грузим синхронно, не трогаем игрока
            return sl.m_8055_(gp).m_60795_();
        }
        return true;
    }

    private static void fog(ServerLevel sl, ServerPlayer p) {
        double x = p.m_20185_(), y = p.m_20186_(), z = p.m_20189_();
        double[] t = TRACK.get(p.m_20148_());
        if (t == null) { TRACK.put(p.m_20148_(), new double[]{x, z, 0}); t = TRACK.get(p.m_20148_()); }
        int before = (int) t[2];
        if (still(x - t[0], z - t[1])) t[2] += SAMPLE; else t[2] = 0;
        t[0] = x; t[1] = z;
        int now = (int) t[2];
        Fx.sendTo(sl, p, Fx.particle("cloud"), x, y + 0.5, z, now >= WARN ? 14 : 5, 4.0, 1.0, 4.0, 0.005);
        Fx.sendTo(sl, p, Fx.particle("soul"), x, y + 1.0, z, now >= WARN ? 6 : 1, 3.0, 1.0, 3.0, 0.01);
        if (now == 0) return;
        if (before < WARN && now >= WARN) p.m_5661_(Component.m_237115_("explosionwand.city.fog_warn"), true);
        if (stage(now) >= 1) {
            if (stage(before) < 1) p.m_5661_(Component.m_237115_("explosionwand.city.fog_slow"), true);
            Cmd.run(sl, x, y, z, "effect give " + p.m_20148_() + " minecraft:slowness 2 1 true");
        }
        if (stage(now) >= 2) {
            if (stage(before) < 2) p.m_5661_(Component.m_237115_("explosionwand.city.fog_blind"), true);
            Cmd.run(sl, x, y, z, "effect give " + p.m_20148_() + " minecraft:blindness 2 0 true");
        }
    }
}
