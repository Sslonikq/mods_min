package com.explosionwand;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Алтарь-портал в Ад (4.13). Рамка 5x5 из Печатного камня (внутри проём 3x3), в центре верхней грани - гнездо из Углей Феникса.
 * Ключ Ада, использованный на гнезде, зажигает проём ПОСТОЯННЫМИ блоками портала (блок мира, не сущность: переживает перезапуск),
 * гнездо становится "занятым" (ключ остаётся в алтаре). Вход в проём переносит в измерение hell, на той стороне стоит обратный портал.
 * Чистая раскладка (FRAME/INNER/at) проверяется тестом HellGateTest.
 */
final class HellGate {
    /** Смещения (u вдоль рамки, dy вниз от гнезда) блоков рамки без гнезда: верх (u!=0), низ, бока. */
    static final int[][] FRAME;
    /** Смещения проёма 3x3. */
    static final int[][] INNER;
    static {
        java.util.List<int[]> f = new java.util.ArrayList<>(), in = new java.util.ArrayList<>();
        for (int u = -2; u <= 2; u++) { if (u != 0) f.add(new int[]{u, 0}); f.add(new int[]{u, -4}); }
        for (int dy = -1; dy >= -3; dy--) { f.add(new int[]{-2, dy}); f.add(new int[]{2, dy}); for (int u = -1; u <= 1; u++) in.add(new int[]{u, dy}); }
        FRAME = f.toArray(new int[0][]); INNER = in.toArray(new int[0][]);
    }

    /** Смещение {dx, dy, dz} клетки раскладки от гнезда: axis 0 - рамка вдоль X, 1 - вдоль Z. */
    static int[] off(int axis, int u, int dy) { return new int[]{axis == 0 ? u : 0, dy, axis == 1 ? u : 0}; }

    static BlockPos at(BlockPos socket, int axis, int u, int dy) {
        int[] o = off(axis, u, dy);
        return new BlockPos(socket.m_123341_() + o[0], socket.m_123342_() + o[1], socket.m_123343_() + o[2]);
    }

    /** Ключ игрока: тик последнего входа в проём (чтобы не гонять туда-сюда) и начало текущего стояния. */
    static final Map<UUID, long[]> STAND = new HashMap<>();
    static final int DWELL = 30, COOLDOWN = 100;
    /** Где стоит обратный портал в Аду (гнездо) и где появляется игрок. */
    static final int HELL_SOCKET_Y = 68;
    static final BlockPos HELL_SOCKET = new BlockPos(0, HELL_SOCKET_Y, -6);
    static final double HELL_X = 0.5D, HELL_Y = 64.0D, HELL_Z = -2.5D;

    // ---- блоки

    static boolean is(BlockState st, net.minecraft.world.level.block.Block b) { return st != null && st.m_60734_() == b; }
    static BlockState stone() { return ExplosionWandMod.SEAL_STONE.get().m_49966_(); }
    static BlockState embers() { return ExplosionWandMod.PHOENIX_EMBERS.get().m_49966_(); }
    static BlockState socketOn() { return ExplosionWandMod.HELL_SOCKET.get().m_49966_(); }
    static BlockState portal() { return ExplosionWandMod.HELL_PORTAL.get().m_49966_(); }

    /** Какой осью стоит полная рамка вокруг гнезда (0/1), -1 - нет. Проём должен быть пуст (воздух). */
    static int frameAxis(Level l, BlockPos socket) {
        for (int axis = 0; axis < 2; axis++) {
            boolean ok = true;
            for (int[] o : FRAME) if (!is(l.m_8055_(at(socket, axis, o[0], o[1])), ExplosionWandMod.SEAL_STONE.get())) { ok = false; break; }
            if (!ok) continue;
            for (int[] o : INNER) if (!l.m_8055_(at(socket, axis, o[0], o[1])).m_60795_()) { ok = false; break; }
            if (ok) return axis;
        }
        return -1;
    }

    /** Ключ использован на гнезде: зажечь портал. Возвращает true, если ключ потрачен. */
    static boolean activate(ServerLevel sl, BlockPos socket, ServerPlayer p) {
        if (Dungeons.protectedAt(sl, socket)) { p.m_5661_(Component.m_237115_("explosionwand.hellgate.protected"), true); return false; }
        int axis = frameAxis(sl, socket);
        if (axis < 0) { p.m_5661_(Component.m_237115_("explosionwand.hellgate.incomplete"), true); return false; }
        for (int[] o : INNER) if (Dungeons.protectedAt(sl, at(socket, axis, o[0], o[1]))) { p.m_5661_(Component.m_237115_("explosionwand.hellgate.protected"), true); return false; }
        light(sl, socket, axis);
        double cx = at(socket, axis, 0, -2).m_123341_() + 0.5D, cy = socket.m_123342_() - 2 + 0.5D, cz = at(socket, axis, 0, -2).m_123343_() + 0.5D;
        Fx.sound(sl, cx, cy, cz, "block.end_portal.spawn", 3.0F, 0.7F);
        Fx.sound(sl, cx, cy, cz, "entity.blaze.shoot", 2.0F, 0.5F);
        Fx.send(sl, Fx.particle("flash"), cx, cy, cz, 1, 0, 0, 0, 0);
        Fx.send(sl, Fx.particle("lava"), cx, cy, cz, 30, 1.0, 1.0, 1.0, 0.1);
        p.m_5661_(Component.m_237115_("explosionwand.hellgate.lit"), true);
        Journal.set(p, Journal.HELL);
        return true;
    }

    /** Заполнить проём блоками портала, гнездо - занятое. */
    static void light(Level l, BlockPos socket, int axis) {
        for (int[] o : INNER) l.m_7731_(at(socket, axis, o[0], o[1]), portal(), 3);
        l.m_7731_(socket, socketOn(), 3);
    }

    /** Построить целую рамку и сразу зажечь (обратный портал в Аду). */
    static void buildLit(Level l, BlockPos socket, int axis) {
        for (int[] o : FRAME) l.m_7731_(at(socket, axis, o[0], o[1]), stone(), 3);
        light(l, socket, axis);
    }

    // ---- вход в проём

    /** Каждый тик, пока игрок в блоке портала: нужно простоять DWELL тиков подряд, после прохода - перезарядка COOLDOWN. */
    static void inside(ServerLevel sl, ServerPlayer p, BlockPos pos) {
        UUID id = p.m_20148_();
        long now = sl.m_46467_();
        long[] st = STAND.get(id);
        if (st == null || now < st[1] || now - st[1] > 3) st = new long[]{now, now};     // новое стояние
        st[1] = now;
        STAND.put(id, st);
        Long cd = LAST.get(id);
        if (cd != null && now >= cd && now - cd < COOLDOWN) { st[0] = now; return; }
        if (now - st[0] < DWELL) {
            if (now % 4 == 0) Fx.send(sl, Fx.particle("portal"), p.m_20185_(), p.m_20186_() + 1.0D, p.m_20189_(), 6, 0.3, 0.6, 0.3, 0.2);
            return;
        }
        LAST.put(id, now);
        STAND.remove(id);
        go(sl, p, pos);
    }
    static final Map<UUID, Long> LAST = new HashMap<>();

    static void onLogoutEvent(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent ev) {
        if (ev.getEntity() == null) return;
        STAND.remove(ev.getEntity().m_20148_()); LAST.remove(ev.getEntity().m_20148_());
    }

    static boolean isPortalPart(Level l, BlockPos p) {
        BlockState s = l.m_8055_(p);
        return is(s, ExplosionWandMod.HELL_PORTAL.get()) || is(s, ExplosionWandMod.SEAL_STONE.get()) || is(s, ExplosionWandMod.HELL_SOCKET.get());
    }

    /** Ось портала по соседям клетки проёма. */
    static int axisOf(Level l, BlockPos pos) {
        return (isPortalPart(l, new BlockPos(pos.m_123341_() + 1, pos.m_123342_(), pos.m_123343_())) || isPortalPart(l, new BlockPos(pos.m_123341_() - 1, pos.m_123342_(), pos.m_123343_()))) ? 0 : 1;
    }

    static void go(ServerLevel sl, ServerPlayer p, BlockPos pos) {
        if (Dims.is(sl, Dims.HELL)) { back(sl, p); return; }
        if (Dims.isOurs(sl)) return;      // из других наших миров портала не бывает
        ServerLevel hell = Dims.get(sl, Dims.HELL);
        if (hell == null) return;
        int axis = axisOf(sl, pos);
        int[] spot = exitSpot(sl, pos, axis);
        CompoundTag d = p.getPersistentData();
        ResourceLocation dim = sl.m_46472_().m_135782_();
        d.m_128359_("ewHellDim", dim.m_135827_() + ":" + dim.m_135815_());
        d.m_128405_("ewHellX", spot[0]); d.m_128405_("ewHellY", spot[1]); d.m_128405_("ewHellZ", spot[2]);
        ensureHell(hell);
        Fx.sound(sl, p.m_20185_(), p.m_20186_(), p.m_20189_(), "block.portal.travel", 0.6F, 0.7F);
        Entity moved = Dims.teleport(p, hell, HELL_X, HELL_Y, HELL_Z);
        if (moved instanceof ServerPlayer q) {
            Fx.sound(hell, HELL_X, HELL_Y, HELL_Z, "block.portal.travel", 0.6F, 0.7F);
            title(hell, q);
        }
    }

    static void title(ServerLevel hell, ServerPlayer q) {
        String id = q.m_20148_().toString();
        Cmd.run(hell, HELL_X, HELL_Y, HELL_Z, "title " + id + " times 10 70 20");
        Cmd.run(hell, HELL_X, HELL_Y, HELL_Z, "title " + id + " subtitle {\"translate\":\"explosionwand.hell.building.sub\",\"color\":\"gray\",\"italic\":true}");
        Cmd.run(hell, HELL_X, HELL_Y, HELL_Z, "title " + id + " title {\"translate\":\"explosionwand.hell.building\",\"color\":\"red\",\"bold\":true}");
    }

    /** Из Ада - к тому порталу, откуда игрок пришёл (или на мировой спавн, если записи нет). */
    static void back(ServerLevel hell, ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        ServerLevel dest = null; int x = 0, y = 0, z = 0;
        if (d.m_128441_("ewHellDim")) {
            String s = d.m_128461_("ewHellDim");
            int i = s.indexOf(':');
            if (i > 0) dest = Dims.get(hell, ResourceKey.m_135785_(Registries.f_256858_, new ResourceLocation(s.substring(0, i), s.substring(i + 1))));
            x = d.m_128451_("ewHellX"); y = d.m_128451_("ewHellY"); z = d.m_128451_("ewHellZ");
        }
        if (dest == null || dest == hell) {
            dest = Dims.get(hell, Level.f_46428_);
            BlockPos sp = dest.m_220360_();
            x = sp.m_123341_(); z = sp.m_123343_(); y = Dims.safeY(dest, x, z);
        } else dest.m_6325_(x >> 4, z >> 4);
        Fx.sound(hell, p.m_20185_(), p.m_20186_(), p.m_20189_(), "block.portal.travel", 0.6F, 0.7F);
        Dims.teleport(p, dest, x + 0.5D, y, z + 0.5D);
        Fx.sound(dest, x + 0.5D, y, z + 0.5D, "block.portal.travel", 0.6F, 1.2F);
    }

    /** Куда ставить игрока у портала: в двух блоках по любую сторону от плоскости портала, где есть место; иначе у самой клетки. {x, y, z} клетки ног. */
    static int[] exitSpot(Level l, BlockPos pos, int axis) {
        int nx = axis == 0 ? 0 : 1, nz = axis == 0 ? 1 : 0;
        for (int sgn : new int[]{1, -1}) {
            int x = pos.m_123341_() + nx * 2 * sgn, z = pos.m_123343_() + nz * 2 * sgn;
            for (int dy = 2; dy >= -3; dy--) {
                int y = pos.m_123342_() + dy;
                if (l.m_8055_(new BlockPos(x, y, z)).m_60795_() && l.m_8055_(new BlockPos(x, y + 1, z)).m_60795_() && !l.m_8055_(new BlockPos(x, y - 1, z)).m_60795_())
                    return new int[]{x, y, z};
            }
        }
        return new int[]{pos.m_123341_(), pos.m_123342_(), pos.m_123343_()};
    }

    // ---- Ад-заглушка (4.13)

    /** Маркер "площадка построена": бедрок под центром пола (добраться можно только копая вниз). Раньше маркером был блок пола (0,63,0). */
    static final BlockPos MARKER = new BlockPos(0, 62, 0);

    static boolean isGateBlock(BlockState s) { return is(s, ExplosionWandMod.HELL_PORTAL.get()) || is(s, ExplosionWandMod.HELL_SOCKET.get()); }

    /** Вид клетки раскладки для проверки целостности: "frame" / "portal" / "socket" / "" (прочее). */
    static String kind(BlockState s) {
        if (is(s, ExplosionWandMod.SEAL_STONE.get())) return "frame";
        if (is(s, ExplosionWandMod.HELL_PORTAL.get())) return "portal";
        if (is(s, ExplosionWandMod.HELL_SOCKET.get())) return "socket";
        return "";
    }

    /** Портал цел: гнездо занято, вся рамка и весь проём на местах. */
    static boolean intact(BlockPos socket, int axis, java.util.function.Function<BlockPos, String> kindAt) {
        if (!"socket".equals(kindAt.apply(socket))) return false;
        for (int[] o : FRAME) if (!"frame".equals(kindAt.apply(at(socket, axis, o[0], o[1])))) return false;
        for (int[] o : INNER) if (!"portal".equals(kindAt.apply(at(socket, axis, o[0], o[1])))) return false;
        return true;
    }

    /** Клетка принадлежит обратному порталу в Аду (рамка, гнездо, проём): ломать нельзя. */
    static boolean inReturnGate(BlockPos p) {
        if (p.m_123341_() == HELL_SOCKET.m_123341_() && p.m_123342_() == HELL_SOCKET.m_123342_() && p.m_123343_() == HELL_SOCKET.m_123343_()) return true;
        for (int[][] cells : new int[][][]{FRAME, INNER}) for (int[] o : cells) {
            BlockPos c = at(HELL_SOCKET, 0, o[0], o[1]);
            if (c.m_123341_() == p.m_123341_() && c.m_123342_() == p.m_123342_() && c.m_123343_() == p.m_123343_()) return true;
        }
        return false;
    }

    /** Сломана рамка: в Аду - нельзя (обратный портал вечный); на Земле - зажжённый проём гаснет, гнездо снова Угли (ключ нужен новый). */
    static void onBreak(net.minecraftforge.event.level.BlockEvent.BreakEvent ev) {
        if (!(ev.getLevel() instanceof ServerLevel sl)) return;
        BlockPos pos = ev.getPos();
        if (!is(sl.m_8055_(pos), ExplosionWandMod.SEAL_STONE.get())) return;
        if (Dims.is(sl, Dims.HELL)) { if (inReturnGate(pos)) ev.setCanceled(true); return; }
        for (int dx = -2; dx <= 2; dx++) for (int dy = 0; dy <= 4; dy++) for (int dz = -2; dz <= 2; dz++) {
            BlockPos socket = new BlockPos(pos.m_123341_() + dx, pos.m_123342_() + dy, pos.m_123343_() + dz);
            if (!is(sl.m_8055_(socket), ExplosionWandMod.HELL_SOCKET.get())) continue;
            for (int axis = 0; axis < 2; axis++) {
                boolean part = false;
                for (int[] o : FRAME) { BlockPos c = at(socket, axis, o[0], o[1]); if (c.m_123341_() == pos.m_123341_() && c.m_123342_() == pos.m_123342_() && c.m_123343_() == pos.m_123343_()) part = true; }
                if (!part || !is(sl.m_8055_(at(socket, axis, 0, -2)), ExplosionWandMod.HELL_PORTAL.get())) continue;
                BlockState air = Fx.state("air");
                for (int[] o : INNER) sl.m_7731_(at(socket, axis, o[0], o[1]), air, 3);
                sl.m_7731_(socket, embers(), 3);
                Fx.sound(sl, socket.m_123341_() + 0.5D, socket.m_123342_() - 2.0D, socket.m_123343_() + 0.5D, "block.fire.extinguish", 2.0F, 0.6F);
                return;
            }
        }
    }

    /** Площадка (строится один раз, маркер - бедрок MARKER) и обратный портал (проверяется при каждом входе и восстанавливается, если смыт/сломан). */
    static void ensureHell(ServerLevel hell) {
        for (int cx = -1; cx <= 0; cx++) for (int cz = -1; cz <= 0; cz++) hell.m_6325_(cx, cz);
        if (hell.m_8055_(MARKER).m_60734_() != Fx.state("bedrock").m_60734_()) {
            if (hell.m_8055_(new BlockPos(0, 63, 0)).m_60795_()) buildPlatform(hell);
            else hell.m_7731_(MARKER, Fx.state("bedrock"), 3);     // площадка 3.0.0 уже стоит: только поставить маркер
        }
        if (!intact(HELL_SOCKET, 0, b -> kind(hell.m_8055_(b)))) buildLit(hell, HELL_SOCKET, 0);
    }

    /** Круг радиуса 9 из адских блоков с оградой по краю. */
    static void buildPlatform(ServerLevel hell) {
        BlockState rock = Fx.state("netherrack"), black = Fx.state("blackstone"), brick = Fx.state("polished_blackstone_bricks"), magma = Fx.state("magma_block"),
                fence = Fx.state("nether_brick_fence"), lava = Fx.state("lava"), lamp = Fx.state("shroomlight"), air = Fx.state("air");
        for (int x = -9; x <= 9; x++) for (int z = -9; z <= 9; z++) {
            int r2 = x * x + z * z;
            if (r2 > 81) continue;
            int h = Math.floorMod(x * 7 + z * 13 + x * z, 11);
            BlockState top = h < 4 ? rock : h < 7 ? black : h < 9 ? brick : magma;
            hell.m_7731_(new BlockPos(x, 63, z), top, 3);
            hell.m_7731_(new BlockPos(x, 62, z), rock, 3);
            for (int y = 64; y <= 74; y++) hell.m_7731_(new BlockPos(x, y, z), air, 3);
            if (r2 >= 64) hell.m_7731_(new BlockPos(x, 64, z), fence, 3);
        }
        for (int x = 4; x <= 5; x++) for (int z = 3; z <= 4; z++) hell.m_7731_(new BlockPos(x, 63, z), lava, 3);
        for (int[] c : new int[][]{{-5, 5}, {5, -5}, {-5, -5}, {6, 6}}) { hell.m_7731_(new BlockPos(c[0], 64, c[1]), brick, 3); hell.m_7731_(new BlockPos(c[0], 65, c[1]), lamp, 3); }
        hell.m_7731_(MARKER, Fx.state("bedrock"), 3);
    }
}
