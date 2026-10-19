package com.explosionwand;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3f;

import java.util.*;

/**
 * Посох Кита — добыча Небесного Кита.
 *  ПКМ        — «Нырок»: ныряешь в землю и через секунду выныриваешь в точке взгляда (до 16 блоков)
 *               с ударной волной: урон и подброс всем вокруг.
 *  Shift+ПКМ  — «Пожирание»: Кит съедает посох из левой руки. Следующие 5 нырков при выныривании
 *               дополнительно выпускают эхо способности съеденного посоха. Новый посох заменяет старый.
 */
final class WhaleStaff {
    static final Map<UUID, Long> CD = new HashMap<>();
    static final int DIVE_CD = 240, ECHOES = 8;
    static final String NS = "explosionwand";
    /** Что нельзя скормить Киту (не посохи). */
    static final Set<String> NOT_STAFF = Set.of("whale_staff", "rift_core", "eclipse_seal", "eclipse_heart", "dream_seal", "dreaming_eye", "sky_seal");
    static DustParticleOptions SKY, WHITE;

    static void init() {
        if (SKY != null) return;
        SKY = new DustParticleOptions(new Vector3f(0.45F, 0.8F, 1.0F), 1.5F);
        WHITE = new DustParticleOptions(new Vector3f(1, 1, 1), 1.3F);
    }

    static void bar(ServerLevel l, ServerPlayer p, String json) {
        Cmd.run(l, p.m_20185_(), p.m_20186_(), p.m_20189_(), "title " + p.m_36316_().getName() + " actionbar " + json);
    }
    static String text(String s, String color) { return "{\"text\":\"" + s + "\",\"color\":\"" + color + "\"}"; }
    static String itemName(String path) { return "{\"translate\":\"item." + NS + "." + path + "\",\"color\":\"gold\"}"; }

    static void use(ServerLevel l, ServerPlayer p, ItemStack staff) {
        init();
        if (p.m_6144_()) { devour(l, p, staff); return; }
        long now = EclipseStaff.time(l);
        Long until = CD.get(p.m_20148_());
        if (until != null && now < until) { bar(l, p, text("Нырок — перезарядка " + ((until - now) / 20 + 1) + " сек", "red")); return; }
        CompoundTag tg = staff.m_41783_();
        boolean charged = tg != null && tg.m_128441_("Devoured") && tg.m_128451_("Echoes") > 0;
        CD.put(p.m_20148_(), now + (long) ((charged ? DIVE_CD / 2 : DIVE_CD) * ArcaneSet.cd(p)));
        dive(l, p, staff);
    }

    // ================= Пожирание =================
    static void devour(ServerLevel l, ServerPlayer p, ItemStack staff) {
        ItemStack off = p.m_21206_();
        ResourceLocation key = (off == null || off.m_41619_()) ? null : ForgeRegistries.ITEMS.getKey(off.m_41720_());
        if (key == null || !NS.equals(key.m_135827_()) || NOT_STAFF.contains(key.m_135815_())) {
            bar(l, p, text("Возьми в левую руку другой посох — Кит его съест", "gray"));
            Fx.sound(l, p.m_20185_(), p.m_20186_(), p.m_20189_(), "entity.dolphin.ambient", 0.6F, 1.4F);
            return;
        }
        String path = key.m_135815_();
        off.m_41774_(1);
        CompoundTag tag = staff.m_41784_();
        tag.m_128359_("Devoured", path);
        tag.m_128405_("Echoes", ECHOES);
        Vec3 v = p.m_20182_();
        Fx.send(l, SKY, v.f_82479_, v.f_82480_ + 1, v.f_82481_, 40, 0.6, 0.8, 0.6, 0.05);
        Fx.send(l, Fx.particle("bubble_pop"), v.f_82479_, v.f_82480_ + 1, v.f_82481_, 30, 0.5, 0.6, 0.5, 0.05);
        Fx.sound(l, v.f_82479_, v.f_82480_, v.f_82481_, "entity.generic.eat", 1.0F, 0.6F);
        Fx.sound(l, v.f_82479_, v.f_82480_, v.f_82481_, "entity.elder_guardian.ambient", 0.8F, 0.9F);
        Later.run(l, 6, () -> cast(l, p, path, p.m_20185_(), p.m_20186_() + 0.5, p.m_20189_(), 1.5));
        bar(l, p, "[" + text("Кит поглотил: ", "aqua") + "," + itemName(path) + "," + text(" — эхо: " + ECHOES, "aqua") + "]");
    }

    // ================= Нырок =================
    static void dive(ServerLevel l, ServerPlayer p, ItemStack staff) {
        Aim aim = Aim.of(l, p, 16.0D);
        Vec3 tgt = aim.target;
        if (aim.hit && aim.entity == null) tgt = tgt.m_82546_(aim.look.m_82490_(0.5));
        Integer y = stand(l, tgt.f_82479_, tgt.f_82480_, tgt.f_82481_);
        final double dx = Math.floor(tgt.f_82479_) + 0.5, dz = Math.floor(tgt.f_82481_) + 0.5;
        final double dy = y != null ? y : tgt.f_82480_;
        Vec3 from = p.m_20182_();
        var inv = Fx.effect("invisibility");
        if (inv != null) p.m_7292_(new MobEffectInstance(inv, 24, 0, false, false));
        Fx.send(l, Fx.particle("splash"), from.f_82479_, from.f_82480_ + 0.2, from.f_82481_, 60, 0.5, 0.2, 0.5, 0.3);
        Fx.send(l, Fx.particle("bubble_pop"), from.f_82479_, from.f_82480_ + 0.2, from.f_82481_, 20, 0.6, 0.1, 0.6, 0.05);
        Fx.sound(l, from.f_82479_, from.f_82480_, from.f_82481_, "entity.player.splash.high_speed", 1.0F, 0.7F);
        p.m_20256_(new Vec3(0, -0.3, 0)); p.f_19864_ = true;
        CompoundTag t0 = staff.m_41783_();
        if (t0 != null && t0.m_128441_("Devoured") && t0.m_128451_("Echoes") > 0) { String pth = t0.m_128461_("Devoured"); cast(l, p, pth, from.f_82479_, from.f_82480_ + 0.5, from.f_82481_, 1.2); }
        // рябь бежит к точке выныривания
        for (int i = 1; i <= 5; i++) {
            final double k = i / 6.0;
            Later.run(l, i * 3, () -> Fx.send(l, Fx.particle("splash"), from.f_82479_ + (dx - from.f_82479_) * k, Math.min(from.f_82480_, dy) + 0.1, from.f_82481_ + (dz - from.f_82481_) * k, 12, 0.4, 0.05, 0.4, 0.1));
        }
        Later.run(l, 20, () -> {
            if (!p.m_6084_() || p.m_9236_() != l) return;
            p.m_6021_(dx, dy, dz);
            p.f_19789_ = 0;
            p.m_20256_(new Vec3(0, 0.5, 0)); p.f_19864_ = true;
            surface(l, p, dx, dy, dz);
            echo(l, p, staff, dx, dy, dz);
        });
    }

    /** Безопасная высота: пол под ногами и два блока воздуха. */
    static Integer stand(ServerLevel l, double x, double y, double z) {
        int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
        for (int d = 2; d >= -24; d--) {
            int yy = by + d;
            if (!Dims.air(l, bx, yy - 1, bz) && Dims.air(l, bx, yy, bz) && Dims.air(l, bx, yy + 1, bz)) return yy;
        }
        return null;
    }

    static List<LivingEntity> around(ServerLevel l, Player owner, double x, double y, double z, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : l.m_6249_(owner, new AABB(x - r, y - r, z - r, x + r, y + r, z + r), e -> e instanceof LivingEntity && e.m_6084_() && !(e instanceof Player) && !e.m_5833_()))
            if (e.m_20275_(x, y, z) < r * r) out.add((LivingEntity) e);
        return out;
    }

    static void push(Entity e, double x, double z, double h, double up) {
        if (Minions.bossPart(e)) return;      // боссов и их хитбоксы не швыряем
        Vec3 v = e.m_20182_(); double dx = v.f_82479_ - x, dz = v.f_82481_ - z, l = Math.max(0.3, Math.sqrt(dx * dx + dz * dz));
        e.m_20256_(new Vec3(dx / l * h, up, dz / l * h)); e.f_19864_ = true;
    }

    static void surface(ServerLevel l, ServerPlayer p, double x, double y, double z) {
        Fx.send(l, Fx.particle("splash"), x, y + 0.3, z, 120, 1.5, 0.6, 1.5, 0.4);
        Fx.send(l, Fx.particle("cloud"), x, y + 0.3, z, 30, 1.5, 0.3, 1.5, 0.1);
        for (int i = 0; i < 32; i++) { double a = i * Math.PI / 16; Fx.send(l, SKY, x + Math.cos(a) * 4, y + 0.2, z + Math.sin(a) * 4, 1, 0, 0, 0, 0); }
        Fx.sound(l, x, y, z, "entity.generic.splash", 1.5F, 0.6F);
        Fx.sound(l, x, y, z, "entity.dolphin.jump", 1.2F, 0.7F);
        for (LivingEntity e : around(l, p, x, y + 1, z, 4.0)) {
            e.m_6469_(l.m_269111_().m_269075_(p), 6.0F);
            push(e, x, z, 0.4, 0.8);
        }
    }

    // ================= Эхо поглощённого посоха =================
    static void echo(ServerLevel l, ServerPlayer p, ItemStack staff, double x, double y, double z) {
        CompoundTag tag = staff.m_41783_();
        if (tag == null || !tag.m_128441_("Devoured")) return;
        int n = tag.m_128451_("Echoes");
        String path = tag.m_128461_("Devoured");
        if (n <= 0 || path == null || path.isEmpty()) { tag.m_128473_("Devoured"); tag.m_128473_("Echoes"); return; }
        Later.run(l, 4, () -> cast(l, p, path, x, y + 0.5, z, 2.0));
        SPECTRAL.add(new Spectral(l, p, path, x, y, z, p.m_20252_(1.0F)));
        n--;
        if (n <= 0) {
            tag.m_128473_("Devoured"); tag.m_128473_("Echoes");
            bar(l, p, "[" + text("Эхо ", "gray") + "," + itemName(path) + "," + text(" угасло. Скорми Киту новый посох.", "gray") + "]");
        } else {
            tag.m_128405_("Echoes", n);
            bar(l, p, "[" + text("Эхо: ", "aqua") + "," + itemName(path) + "," + text(" — осталось " + n, "aqua") + "]");
        }
    }

    static void ring(ServerLevel l, ParticleOptions o, double x, double y, double z, double r) {
        int n = (int) (12 + r * 6);
        for (int i = 0; i < n; i++) { double a = i * Math.PI * 2 / n; Fx.send(l, o, x + Math.cos(a) * r, y, z + Math.sin(a) * r, 1, 0, 0.05, 0, 0); }
    }

    static void cast(ServerLevel l, ServerPlayer p, String path, double x, double y, double z, double pow) {
        var src = l.m_269111_().m_269075_(p);
        double rm = 0.6 + 0.4 * pow; float dm = (float) pow;      // эхо Кита: шире и сильнее
        switch (path) {
            case "explosion_wand" -> {          // взрыв без разрушения блоков
                Fx.send(l, Fx.particle("explosion_emitter"), x, y, z, 1, 0, 0, 0, 0);
                Fx.sound(l, x, y, z, "entity.generic.explode", 2.0F, 0.8F);
                for (LivingEntity e : around(l, p, x, y, z, 4.5 * rm)) { e.m_6469_(src, 12.0F * dm); push(e, x, z, 0.9, 0.6); }
            }
            case "black_hole_wand" -> {         // стягивает всё в точку
                Fx.send(l, Fx.particle("reverse_portal"), x, y + 1, z, 120, 3, 1.5, 3, 0.3);
                Fx.sound(l, x, y, z, "block.end_portal.spawn", 0.8F, 1.6F);
                for (LivingEntity e : around(l, p, x, y, z, 10 * rm)) {
                    e.m_6469_(src, 6.0F * dm);
                    if (Minions.bossPart(e)) continue;
                    Vec3 v = e.m_20182_(); double dx = x - v.f_82479_, dz = z - v.f_82481_, d = Math.max(0.5, Math.sqrt(dx * dx + dz * dz));
                    e.m_20256_(new Vec3(dx / d * 1.2, 0.3, dz / d * 1.2)); e.f_19864_ = true;
                }
            }
            case "ice_storm_wand" -> {          // заморозка
                Fx.send(l, Fx.particle("snowflake"), x, y + 1, z, 150, 3, 1.5, 3, 0.05);
                Fx.sound(l, x, y, z, "block.glass.break", 1.2F, 0.6F);
                var slow = Fx.effect("slowness");
                for (LivingEntity e : around(l, p, x, y, z, 6 * rm)) { e.m_6469_(l.m_269111_().m_269109_(), 4.0F * dm); e.m_146917_(240); if (slow != null) e.m_7292_(new MobEffectInstance(slow, 120, 3)); }
            }
            case "dream_staff" -> {             // массовый сон
                DreamStaff.init();
                ring(l, DreamStaff.TEAL, x, y, z, 6 * rm);
                Fx.sound(l, x, y, z, "entity.elder_guardian.curse", 0.6F, 1.6F);
                for (LivingEntity e : around(l, p, x, y, z, 6 * rm)) DreamStaff.sleep(l, e, 70);
            }
            case "comet_wand", "fist_wand" -> {  // огненный удар с подбросом
                Fx.send(l, Fx.particle("flame"), x, y + 0.5, z, 120, 2.5, 0.6, 2.5, 0.15);
                Fx.send(l, Fx.particle("lava"), x, y + 0.5, z, 20, 2, 0.3, 2, 0);
                Fx.sound(l, x, y, z, "entity.blaze.shoot", 1.5F, 0.6F);
                for (LivingEntity e : around(l, p, x, y, z, 4.5 * rm)) { e.m_6469_(src, 10.0F * dm); e.m_20254_(5); push(e, x, z, 0.3, 1.1); }
            }
            case "timestop_wand", "chronos_wand" -> {   // время замирает вокруг
                ring(l, WHITE, x, y, z, 7 * rm);
                Fx.send(l, Fx.particle("end_rod"), x, y + 1, z, 60, 3.5, 1.5, 3.5, 0.01);
                Fx.sound(l, x, y, z, "block.bell.use", 1.5F, 0.5F);
                for (LivingEntity e : around(l, p, x, y, z, 7 * rm)) {
                    if (e instanceof Mob m && !m.m_21525_() && Minions.controllable(m)) { m.m_21557_(true); Later.run(l, 80, () -> { if (m.m_6084_()) m.m_21557_(false); }); }
                    var slow = Fx.effect("slowness"); if (slow != null) e.m_7292_(new MobEffectInstance(slow, 80, 4));
                }
            }
            case "eclipse_staff" -> {           // столп солнца
                for (double h = 0; h < 14; h += 0.4) Fx.send(l, Fx.particle("flame"), x, y + h, z, 3, 0.6, 0.1, 0.6, 0.01);
                Fx.send(l, Fx.particle("flash"), x, y + 1, z, 1, 0, 0, 0, 0);
                Fx.sound(l, x, y, z, "block.beacon.activate", 1.5F, 1.4F);
                for (LivingEntity e : around(l, p, x, y, z, 3.5 * rm)) { e.m_6469_(src, 16.0F * dm); e.m_20254_(8); }
            }
            case "possess_wand", "mimic_wand" -> {     // метка: подсветка и слабость
                Fx.send(l, Fx.particle("witch"), x, y + 1, z, 60, 4, 1, 4, 0.05);
                Fx.sound(l, x, y, z, "entity.illusioner.cast_spell", 1.2F, 0.9F);
                var glow = Fx.effect("glowing"); var weak = Fx.effect("weakness");
                for (LivingEntity e : around(l, p, x, y, z, 8 * rm)) { e.m_6469_(src, 4.0F * dm); if (glow != null) e.m_7292_(new MobEffectInstance(glow, 200, 0)); if (weak != null) e.m_7292_(new MobEffectInstance(weak, 200, 1)); }
            }
            case "void_wand", "haunt_wand", "inverted_wand", "rift_wand", "tesseract_wand", "portal_gun" -> {   // разлом: подкидывает в небо
                Fx.send(l, Fx.particle("portal"), x, y + 1, z, 150, 3, 1.5, 3, 0.5);
                Fx.sound(l, x, y, z, "entity.enderman.teleport", 1.5F, 0.6F);
                var lev = Fx.effect("levitation");
                for (LivingEntity e : around(l, p, x, y, z, 6 * rm)) { e.m_6469_(src, 6.0F * dm); if (lev != null && !Minions.bossPart(e)) e.m_7292_(new MobEffectInstance(lev, 40, 1)); }
            }
            default -> {                        // любое другое — волна чистой магии
                Fx.send(l, WHITE, x, y + 1, z, 60, 3, 1, 3, 0.1);
                ring(l, SKY, x, y, z, 5 * rm);
                Fx.sound(l, x, y, z, "block.amethyst_block.resonate", 1.5F, 0.8F);
                for (LivingEntity e : around(l, p, x, y, z, 5 * rm)) { e.m_6469_(src, 8.0F * dm); push(e, x, z, 0.6, 0.4); }
            }
        }
    }

    // ================= Призрачный кит =================
    static final List<Spectral> SPECTRAL = new ArrayList<>();
    static int seq = 0;
    static final double[][] SP = {   // x,y,z, sx,sy,sz — тело, хвост, глаза
            {0, 0, 2.2, 1.6, 1.3, 1.4}, {0, 0.05, 0.8, 1.7, 1.4, 1.4}, {0, 0, -0.6, 1.3, 1.1, 1.4}, {0, 0, -1.9, 0.8, 0.7, 1.2},
            {0.6, 0, -2.9, 1.2, 0.12, 0.8}, {-0.6, 0, -2.9, 1.2, 0.12, 0.8}, {0.8, 0.15, 2.6, 0.22, 0.22, 0.22}, {-0.8, 0.15, 2.6, 0.22, 0.22, 0.22}};
    static final String[] SPB = {"light_blue_stained_glass", "light_blue_stained_glass", "light_blue_stained_glass", "light_blue_stained_glass", "white_stained_glass", "white_stained_glass", "sea_lantern", "sea_lantern"};

    /** Выпрыгивает из земли в точке выныривания, делает дугу и бьёт волной при падении. */
    static final class Spectral {
        final ServerLevel l; final ServerPlayer p; final String path, tag; final String[] uuid = new String[SP.length];
        double x, y, z, vx, vy, vz, groundY; int t = 0; Entity host; boolean landed = false;
        Spectral(ServerLevel l, ServerPlayer p, String path, double x, double gy, double z, Vec3 look) {
            this.l = l; this.p = p; this.path = path; this.tag = "ewsw" + (seq++);
            this.x = x; this.z = z; this.groundY = gy; this.y = gy - 2.5;
            double hl = Math.max(1e-3, Math.sqrt(look.f_82479_ * look.f_82479_ + look.f_82481_ * look.f_82481_));
            vx = look.f_82479_ / hl * 0.22; vz = look.f_82481_ / hl * 0.22; vy = 0.75;
            StringBuilder sb = new StringBuilder("summon minecraft:armor_stand " + Cmd.f(x) + " " + Cmd.f(y) + " " + Cmd.f(z)
                    + " {Tags:[\"" + tag + "\",\"" + tag + "_h\"],Invisible:1b,Marker:1b,NoGravity:1b,Invulnerable:1b,Passengers:[");
            for (int i = 0; i < SP.length; i++) {
                if (i > 0) sb.append(",");
                sb.append("{id:\"minecraft:block_display\",Tags:[\"").append(tag).append("\",\"").append(tag).append("_").append(i).append("\"],")
                  .append(Disp.block(SPB[i])).append(",brightness:{sky:15,block:15},view_range:3f,").append(Disp.tf(0, 0, 0, 0.01, 0.01, 0.01, Disp.ID)).append("}");
            }
            Cmd.run(l, x, y, z, sb.append("]}").toString());
            Fx.sound(l, x, gy, z, "entity.elder_guardian.ambient", 1.5F, 0.9F);
        }
        boolean tick() {
            t++;
            if (t == 2) {
                for (Entity e : Cmd.tagged(l, x, y, z, 6, tag + "_h")) host = e;
                for (int i = 0; i < SP.length; i++) for (Entity e : Cmd.tagged(l, x, y, z, 6, tag + "_" + i)) uuid[i] = e.m_20149_();
            }
            x += vx; y += vy; z += vz; vy -= 0.05;
            if (host != null) host.m_6034_(x, y, z);
            if (t % 2 == 0) {
                double yaw = Math.atan2(vx, vz), pitch = Math.atan2(-vy, Math.sqrt(vx * vx + vz * vz));
                double[] Q = Disp.mul(Disp.q(0, 1, 0, yaw), Disp.q(1, 0, 0, pitch));
                double fade = t > 34 ? Math.max(0.05, 1 - (t - 34) / 8.0) : 1.0;
                for (int i = 0; i < SP.length; i++) {
                    if (uuid[i] == null) continue;
                    double[] o = SP[i], r = Disp.rot(Q, o[0], o[1], o[2]);
                    double[] q = i == 4 || i == 5 ? Disp.mul(Q, Disp.q(0, 1, 0, (i == 4 ? 0.4 : -0.4))) : Q;
                    Cmd.run(l, x, y, z, "data merge entity " + uuid[i] + " {start_interpolation:0,interpolation_duration:2," + Disp.tf(r[0], r[1], r[2], o[3] * fade, o[4] * fade, o[5] * fade, q) + "}");
                }
            }
            if (t % 2 == 0) Fx.send(l, Fx.particle("splash"), x, y, z, 6, 0.6, 0.4, 0.6, 0.1);
            if (!landed && t > 8 && vy < 0 && y <= groundY + 0.5) {        // удар хвостом о землю
                landed = true;
                Fx.send(l, Fx.particle("splash"), x, groundY + 0.3, z, 120, 1.8, 0.4, 1.8, 0.4);
                Fx.send(l, Fx.particle("cloud"), x, groundY + 0.3, z, 25, 1.5, 0.2, 1.5, 0.08);
                Fx.sound(l, x, groundY, z, "entity.generic.splash", 1.5F, 0.6F);
                for (LivingEntity e : around(l, p, x, groundY + 1, z, 4.5)) { e.m_6469_(l.m_269111_().m_269075_(p), 9.0F); push(e, x, z, 0.6, 0.7); }
                cast(l, p, path, x, groundY + 0.5, z, 1.3);
            }
            if (t >= 42) { if (host != null) host.m_146870_(); Cmd.run(l, x, y, z, "kill @e[tag=" + tag + "]"); return true; }
            return false;
        }
    }

    static void tick(ServerLevel l) {
        Iterator<Spectral> it = SPECTRAL.iterator();
        while (it.hasNext()) { Spectral s = it.next(); if (s.l != l) continue; if (s.tick()) it.remove(); }
    }
}
