package com.explosionwand;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Парашют: купол из block_display (красно-белая шерсть и стропы) над головой. Пока открыт: падение не быстрее 0.12 за тик,
 * лёгкое планирование по взгляду, fallDistance = 0. Закрывается на земле, в воде/лаве, при Shift, смерти, выходе и смене измерения.
 * Облик сущностей телепортируется за игроком каждый тик; все с тегом ew_chute, осиротевшие убирает sweep.
 */
public class Parachute {
    static final List<Parachute> ACTIVE = new ArrayList<>();
    static final String TAG = "ew_chute";
    static final double MAX_FALL = -0.12D, GLIDE = 0.15D;
    static int seq = 0;

    final ServerLevel level; final ServerPlayer p; final String tag;
    final List<Entity> parts = new ArrayList<>();
    int t = 0; double prevX, prevY, prevZ;

    Parachute(ServerLevel level, ServerPlayer p) {
        this.level = level; this.p = p; this.tag = "ewchute" + p.m_20148_().toString().substring(0, 8) + "x" + Long.toString(level.m_46467_(), 36) + (seq++);
        prevX = p.m_20185_(); prevY = p.m_20186_(); prevZ = p.m_20189_();
        String pos = Cmd.f(prevX) + " " + Cmd.f(prevY) + " " + Cmd.f(prevZ);
        for (String[] part : shape()) {
            Cmd.run(level, prevX, prevY, prevZ, "summon minecraft:block_display " + pos + " {Tags:[\"" + TAG + "\",\"" + tag + "\"],brightness:{sky:15,block:15},view_range:2f,"
                    + Disp.block(part[0]) + "," + part[1] + "}");
        }
        Fx.sound(level, prevX, prevY, prevZ, "item.armor.equip_leather", 1.2F, 0.8F);
        Fx.sound(level, prevX, prevY, prevZ, "entity.phantom.flap", 1.0F, 0.7F);
        Fx.send(level, Fx.particle("cloud"), prevX, prevY + 3, prevZ, 12, 0.8, 0.2, 0.8, 0.02);
    }

    static boolean isOpen(Entity e) { for (Parachute c : ACTIVE) if (c.p == e) return true; return false; }

    /** Раскрыть можно только в воздухе: не на земле, не в воде и не в лаве. */
    static boolean canOpen(ServerPlayer p) { return !p.m_20096_() && !p.m_20069_() && !p.m_20077_() && !p.m_21255_()
            && !p.m_150110_().f_35935_ && !p.m_20159_(); }

    /** Купол: пары {id блока, трансформация} относительно ног игрока. Чистая математика (проверяется тестом). */
    static List<String[]> shape() {
        List<String[]> out = new ArrayList<>();
        String[] col = {"red_wool", "white_wool"};
        final double outerR = 1.7D, outerY = 2.75D, midR = 0.85D, midY = 3.2D;
        for (int k = 0; k < 8; k++) {                          // внешнее кольцо: края опущены вниз
            double a = k * Math.PI / 4;
            double[] q = Disp.mul(Disp.q(-Math.sin(a), 0, Math.cos(a), -0.5), Disp.q(0, 1, 0, -a));
            out.add(new String[]{col[k % 2], Disp.tf(Math.cos(a) * outerR, outerY, Math.sin(a) * outerR, 1.15, 0.14, 1.35, q)});
        }
        for (int k = 0; k < 8; k++) {                          // внутреннее кольцо: почти плоское, цвета сдвинуты
            double a = k * Math.PI / 4;
            double[] q = Disp.mul(Disp.q(-Math.sin(a), 0, Math.cos(a), -0.2), Disp.q(0, 1, 0, -a));
            out.add(new String[]{col[(k + 1) % 2], Disp.tf(Math.cos(a) * midR, midY, Math.sin(a) * midR, 1.0, 0.14, 0.95, q)});
        }
        out.add(new String[]{"red_wool", Disp.tf(0, midY + 0.07, 0, 0.9, 0.14, 0.9, Disp.ID)});      // макушка
        for (int k = 0; k < 4; k++) {                          // стропы от края купола к груди
            double a = k * Math.PI / 2 + Math.PI / 4;
            double sx = Math.cos(a) * 2.05D, sy = 2.4D, sz = Math.sin(a) * 2.05D, ex = 0, ey = 1.2D, ez = 0;
            double dx = ex - sx, dy = ey - sy, dz = ez - sz, len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double[] q = Disp.q(dz, 0, -dx, Math.acos(dy / len));          // ось = (0,1,0) x d
            out.add(new String[]{"white_concrete", Disp.tf((sx + ex) / 2, (sy + ey) / 2, (sz + ez) / 2, 0.04, len, 0.04, q)});
        }
        return out;
    }

    boolean tick() {
        t++;
        if (p.m_213877_() || !p.m_6084_() || p.m_9236_() != level) { remove(false); return true; }
        if (t == 2) parts.addAll(Cmd.tagged(level, p.m_20185_(), p.m_20186_(), p.m_20189_(), 12, tag));
        double x = p.m_20185_(), y = p.m_20186_(), z = p.m_20189_();
        if (t > 3 && (p.m_20096_() || p.m_20069_() || p.m_20077_() || p.m_21255_() || p.m_150110_().f_35935_)) { remove(true); return true; }
        if (t > 8 && p.m_6144_()) { remove(true); return true; }

        // скорость по сдвигу позиции (скорость игрока на сервере ненадёжна)
        double ex = x - prevX, ey = y - prevY, ez = z - prevZ;
        prevX = x; prevY = y; prevZ = z;
        Vec3 lk = p.m_20252_(1.0F);
        double h = Math.sqrt(lk.f_82479_ * lk.f_82479_ + lk.f_82481_ * lk.f_82481_);
        double tx = h < 0.05 ? 0 : lk.f_82479_ / h * GLIDE, tz = h < 0.05 ? 0 : lk.f_82481_ / h * GLIDE;
        double vx = ex + (tx - ex) * 0.25, vz = ez + (tz - ez) * 0.25, vy = Math.max(ey, MAX_FALL);
        p.m_20256_(new Vec3(vx, vy, vz)); p.f_19864_ = true;
        p.f_19789_ = 0.0F;
        MobEffect sf = Fx.effect("slow_falling");
        if (sf != null) p.m_7292_(new MobEffectInstance(sf, 8, 0, false, false));

        for (Entity e : parts) if (e.m_6084_()) e.m_6034_(x, y, z);
        if (t % 6 == 0) Fx.send(level, Fx.particle("cloud"), x, y + 2.9, z, 1, 1.2, 0.1, 1.2, 0.0);
        return false;
    }

    /** Убрать купол. fold - «сложился» (частицы и звук). */
    void remove(boolean fold) {
        for (Entity e : parts) e.m_146870_();
        parts.clear();
        Cmd.run(level, prevX, prevY, prevZ, "kill @e[tag=" + tag + "]");
        if (fold) {
            Vec3 v = p.m_20182_();
            Fx.send(level, Fx.particle("cloud"), v.f_82479_, v.f_82480_ + 2.8, v.f_82481_, 25, 0.9, 0.3, 0.9, 0.03);
            Fx.send(level, Fx.particle("poof"), v.f_82479_, v.f_82480_ + 2.5, v.f_82481_, 15, 0.7, 0.3, 0.7, 0.05);
            Fx.sound(level, v.f_82479_, v.f_82480_, v.f_82481_, "item.armor.equip_leather", 1.0F, 1.2F);
        }
    }

    /** Страховка: купола без активного парашюта (после сбоя или перезапуска) удаляются раз в 5 секунд. */
    static void sweep(ServerLevel l) {
        if (l.m_46467_() % 100 != 7) return;      // время самого уровня: Ticker.ticks растёт за каждый уровень
        List<Entity> dead = new ArrayList<>();
        for (Entity e : l.m_8583_()) {
            if (!e.m_19880_().contains(TAG)) continue;
            boolean owned = false;
            for (Parachute c : ACTIVE) if (c.level == l && e.m_19880_().contains(c.tag)) { owned = true; break; }
            if (!owned) dead.add(e);
        }
        for (Entity e : dead) e.m_146870_();
    }
}
