package com.explosionwand;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

/**
 * Сеты брони данжей Земли (4.12): Кристальный доспех (Разлом), Облачение Сновидца (Город Спящих), Пепельный доспех (Пепельное святилище).
 * Не крафтятся: части падают с мобов своего данжа ({@link #onMobKilled}, зовётся из RiftMobs.onDeath). Бонусы 2 и 4 частей считаются
 * по каждому сету отдельно (2 части одного сета + 2 части другого дают два бонуса «2 части»), числа - {@link ArmorSetLogic}.
 * <p>
 * Что где: отбрасывание, видимость, снаряды, урон - события Forge ({@code on*}); скорость, ночное зрение, горение, «Грань» - {@link #tick}.
 * Модификаторы атрибутов временные (в сохранение не попадают), у каждого свой UUID; {@link #MODS} помнит, у кого они могли остаться.
 */
final class ArmorSets {
    private ArmorSets() {}

    /** Сет: id (часть имён предметов, lang и текстур), материал, свой данж, цвет названия в подсказке и ключи строк бонусов в lang. */
    enum Set {
        CRYSTAL("crystal", SetMaterial.CRYSTAL, Mats.Dungeon.RIFT, "AQUA", new String[]{"p2"}, new String[]{"p4a", "p4b"}),
        DREAMER("dreamer", SetMaterial.DREAMER, Mats.Dungeon.SLEEP, "BLUE", new String[]{"p2"}, new String[]{"p4a", "p4b", "p4c"}),
        ASH("ash", SetMaterial.ASH, Mats.Dungeon.ASH, "GOLD", new String[]{"p2"}, new String[]{"p4a", "p4b"});

        final String id, color; final SetMaterial material; final Mats.Dungeon dungeon; final String[] p2, p4;
        Set(String id, SetMaterial m, Mats.Dungeon d, String color, String[] p2, String[] p4) {
            this.id = id; this.material = m; this.dungeon = d; this.color = color; this.p2 = p2; this.p4 = p4;
        }

        static Set of(Mats.Dungeon d) {
            for (Set s : values()) if (s.dungeon == d) return s;
            return null;
        }
    }

    /** Части по порядку слотов: шлем, нагрудник, поножи, сапоги (суффиксы id предметов). */
    static final String[] PART = {"helmet", "chestplate", "leggings", "boots"};
    private static EquipmentSlot[] SLOTS;

    static final UUID SPEED_ID = UUID.fromString("3f1b6a52-7c0e-4d8a-9a41-52c1d0e6a001");
    static final UUID EDGE_ID = UUID.fromString("3f1b6a52-7c0e-4d8a-9a41-52c1d0e6a002");

    /** У кого могут висеть наши модификаторы атрибутов. */
    static final java.util.Set<UUID> MODS = new HashSet<>();
    /** «Грань»: {когда сработала в последний раз, до какого тика +2 брони}. */
    static final Map<UUID, long[]> EDGE = new HashMap<>();

    // ================================================================ части на игроке
    private static EquipmentSlot[] slots() {
        if (SLOTS == null) SLOTS = new EquipmentSlot[]{EquipmentSlot.valueOf("HEAD"), EquipmentSlot.valueOf("CHEST"), EquipmentSlot.valueOf("LEGS"), EquipmentSlot.valueOf("FEET")};
        return SLOTS;
    }

    /** Сколько частей сета надето (0-4). */
    static int parts(LivingEntity e, Set set) {
        if (e == null) return 0;
        int n = 0;
        for (EquipmentSlot sl : slots()) {
            ItemStack s = e.m_6844_(sl);
            if (s != null && !s.m_41619_() && s.m_41720_() instanceof SetArmorItem a && a.set == set) n++;
        }
        return n;
    }

    /** Множитель расстояния, с которого мобы замечают игрока (Облачение Сновидца). */
    static double noticeMul(ServerPlayer p) {
        int d = parts(p, Set.DREAMER);
        return d < 2 ? 1.0 : ArmorSetLogic.noticeMul(d, p.m_6144_());
    }

    /** Сонный туман и усыпление не действуют (Облачение Сновидца, 4 части). */
    static boolean sleepImmune(LivingEntity p) { return parts(p, Set.DREAMER) >= 4; }

    // ================================================================ дроп
    /** Моб данжа убит игроком: с шансом по рангу падает случайная часть сета этого данжа. Призванные боссом сюда не попадают (фильтр в RiftMobs.onDeath). */
    static void onMobKilled(ServerLevel level, ServerPlayer killer, RiftModels.Kind kind, double x, double y, double z) {
        Set set = Set.of(Mats.dungeonOf(kind));
        if (set == null) return;
        ArmorSetLogic.Tier tier = ArmorSetLogic.tier(kind);
        if (tier == ArmorSetLogic.Tier.NONE) return;
        boolean[] have = new boolean[4];
        for (int i = 0; i < 4; i++) {
            Item it = item(set, i);
            ItemStack worn = killer.m_6844_(slots()[i]);
            boolean wearing = worn != null && !worn.m_41619_() && worn.m_41720_() == it;
            have[i] = wearing || (it != null && killer.m_150109_().m_18947_(it) > 0);
        }
        int i = ArmorSetLogic.roll(tier, EnchantmentHelper.m_44930_(killer), have, Fx.RNG);
        if (i < 0) return;
        Item it = item(set, i);
        if (it == null) return;
        level.m_7967_(new ItemEntity(level, x, y + 0.5, z, new ItemStack(it)));
        Fx.send(level, Fx.particle("enchant"), x, y + 0.8, z, 20, 0.4, 0.4, 0.4, 0.6);
        Fx.sound(level, x, y, z, "entity.experience_orb.pickup", 0.8F, 0.7F);
    }

    static Item item(Set set, int part) { return ForgeRegistries.ITEMS.getValue(new ResourceLocation("explosionwand", set.id + "_" + PART[part])); }

    // ================================================================ атрибуты
    private static Attribute attr(String id) { return ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation("minecraft", id)); }

    /** Включить или снять временный модификатор (op: ADDITION, MULTIPLY_BASE, MULTIPLY_TOTAL). */
    static void setMod(LivingEntity p, String attribute, UUID id, String name, double v, String op, boolean on) {
        Attribute a = attr(attribute);
        AttributeInstance ai = a == null ? null : p.m_21051_(a);
        if (ai == null) return;
        boolean has = ai.m_22111_(id) != null;
        if (on && !has) ai.m_22118_(new AttributeModifier(id, name, v, AttributeModifier.Operation.valueOf(op)));
        else if (!on && has) ai.m_22120_(id);
    }

    // ================================================================ тик
    static void tick(ServerLevel l) {
        var players = l.m_6907_();
        if (players.isEmpty()) return;
        long now = EclipseStaff.time(l);
        for (ServerPlayer p : players) {
            if (!p.m_6084_()) continue;
            UUID id = p.m_20148_();
            int c = parts(p, Set.CRYSTAL), d = parts(p, Set.DREAMER), a = parts(p, Set.ASH);
            boolean tracked = MODS.contains(id);
            if (c + d + a == 0 && !tracked) continue;
            // Пепельный доспех: горение убывает вдвое быстрее, при 4 частях не дольше 3 с
            if (a >= 2) {
                int f = p.m_20094_();
                int n = ArmorSetLogic.fireNext(f, a);
                if (n != f) p.m_7311_(n);
            }
            // «Грань»: +2 брони кончились или сет разобран
            long[] st = EDGE.get(id);
            if (st != null && st[1] > 0 && (c < 4 || now >= st[1])) { setMod(p, "generic.armor", EDGE_ID, "ew_edge", 0, "ADDITION", false); st[1] = 0; }
            if (now % 10 != 0) continue;
            // Облачение Сновидца, 4 части: +8% скорости, ночное зрение
            boolean dream4 = d >= 4;
            setMod(p, "generic.movement_speed", SPEED_ID, "ew_dreamer_speed", ArmorSetLogic.SPEED_BONUS, "MULTIPLY_TOTAL", dream4);
            if (dream4 && now % 20 == 0 && !Dims.is(l, Dims.MAZE)) {
                var nv = Fx.effect("night_vision");
                if (nv != null) p.m_7292_(new MobEffectInstance(nv, 300, 0, true, false));
            }
            boolean edgeOn = st != null && st[1] > 0;
            if (!dream4 && !edgeOn) MODS.remove(id); else MODS.add(id);
        }
    }

    // ================================================================ события
    /** Урон до брони: Пепельный доспех смягчает огонь (не лаву); «+15% урона мобам своего данжа». */
    static void onHurt(LivingHurtEvent ev) {
        DamageSource src = ev.getSource();
        LivingEntity target = ev.getEntity();
        if (src == null || target == null) return;
        if (target instanceof ServerPlayer sp) {
            int a = parts(sp, Set.ASH);
            if (a >= 4) {
                float n = ArmorSetLogic.fireDamage(src.m_19385_(), ev.getAmount(), a);
                if (n != ev.getAmount()) ev.setAmount(n);
            }
        }
        if (RiftMobs.MOBS.isEmpty() || !(src.m_7639_() instanceof ServerPlayer atk)) return;
        RiftMobs.Mob m = RiftMobs.MOBS.get(target.m_20148_());
        if (m == null || m.kind == null) return;
        Set set = Set.of(Mats.dungeonOf(m.kind));
        if (set == null) return;
        int n = parts(atk, set);
        if (n > 0) ev.setAmount((float) (ev.getAmount() * (1.0 + ArmorSetLogic.dmgBonus(n))));
    }

    /** Урон после брони (дошёл до игрока): «Грань» Кристального доспеха, поджог ближнего бьющего Пепельным доспехом. */
    static void onDamage(LivingDamageEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p) || ev.getAmount() <= 0 || !(p.m_9236_() instanceof ServerLevel sl)) return;
        if (parts(p, Set.CRYSTAL) >= 4) edge(p, sl);
        DamageSource src = ev.getSource();
        if (src != null && parts(p, Set.ASH) >= 4) igniteAttacker(p, src);
    }

    private static void edge(ServerPlayer p, ServerLevel sl) {
        long now = EclipseStaff.time(sl);
        long[] st = EDGE.computeIfAbsent(p.m_20148_(), k -> new long[]{-1, 0});
        if (!ArmorSetLogic.edgeReady(now, st[0])) return;
        st[0] = now; st[1] = now + ArmorSetLogic.EDGE_TICKS;
        setMod(p, "generic.armor", EDGE_ID, "ew_edge", ArmorSetLogic.EDGE_ARMOR, "ADDITION", true);
        MODS.add(p.m_20148_());
        Fx.send(sl, Fx.particle("enchanted_hit"), p.m_20185_(), p.m_20186_() + 1, p.m_20189_(), 14, 0.4, 0.6, 0.4, 0.1);
        Fx.sound(sl, p.m_20185_(), p.m_20186_(), p.m_20189_(), "block.amethyst_block.chime", 0.8F, 1.5F);
    }

    /** Ближний бьющий (рядом, удар в руку, не снаряд) загорается на 3 с. Боссов (теги ewb*, ewd) не трогаем, мобы данжа горят через RiftMobs.ignite. */
    private static void igniteAttacker(ServerPlayer p, DamageSource src) {
        Entity atk = src.m_7639_();
        if (atk == null || atk == p || atk != src.m_7640_() || !(atk instanceof LivingEntity) || atk.m_20280_(p) > 36) return;
        if (RiftMobs.MOBS.containsKey(atk.m_20148_())) { RiftMobs.ignite(atk, ArmorSetLogic.IGNITE_SECONDS); return; }
        for (String t : atk.m_19880_()) if (t.equals("ewd") || t.startsWith("ewb")) return;
        atk.m_20254_(ArmorSetLogic.IGNITE_SECONDS);
    }

    /** Кристальный доспех, 4 части: 20% снарядов разворачиваются в стрелка. */
    static void onProjectile(ProjectileImpactEvent ev) {
        HitResult hr = ev.getRayTraceResult();
        if (!(hr instanceof EntityHitResult eh) || !(eh.m_82443_() instanceof ServerPlayer p)) return;
        Projectile pr = ev.getProjectile();
        if (pr == null || !(pr.m_9236_() instanceof ServerLevel sl)) return;
        int c = parts(p, Set.CRYSTAL);
        if (c < 4) return;
        Entity owner = pr.m_19749_();
        if (owner == null || owner == p || !owner.m_6084_() || owner instanceof net.minecraft.world.entity.player.Player) return;   // только снаряды мобов: стрелы, жемчуг и поплавок игроков не трогаем
        if (!ArmorSetLogic.reflects(c, Fx.RNG.nextDouble())) return;
        ev.setCanceled(true);
        Vec3 dir = owner.m_146892_().m_82546_(pr.m_20182_()).m_82541_();
        double speed = Math.max(0.8, pr.m_20184_().m_82553_());
        pr.m_20256_(dir.m_82490_(speed));
        pr.f_19864_ = true;
        if (pr instanceof net.minecraft.world.entity.projectile.AbstractHurtingProjectile h) {   // огненные шары: ускорение тоже разворачиваем, иначе шар тормозит и летит назад
            double[] w = ArmorSetLogic.turnPower(h.f_36813_, h.f_36814_, h.f_36815_, dir.f_82479_, dir.f_82480_, dir.f_82481_);
            h.f_36813_ = w[0]; h.f_36814_ = w[1]; h.f_36815_ = w[2];
        }
        pr.m_5602_(p);
        Fx.send(sl, Fx.particle("enchanted_hit"), pr.m_20185_(), pr.m_20186_(), pr.m_20189_(), 10, 0.2, 0.2, 0.2, 0.2);
        Fx.sound(sl, pr.m_20185_(), pr.m_20186_(), pr.m_20189_(), "block.amethyst_block.hit", 1.0F, 1.7F);
    }

    /**
     * Кристальный доспех, 4 части, «Грань»: луч/плевок моба данжа с шансом 20% возвращается в стрелка.
     * Единая точка для всех лучевых атак мобов данжей (RiftMobs.Mob.hitBeam). Стрелок получает тот же
     * урон dmg от имени игрока (его убийство засчитано игроку), поэтому цикла нет: ответа на ответ не бывает.
     * @return true - луч отражён (игрока не бить), false - обычное попадание.
     */
    static boolean tryReflectBeam(ServerPlayer target, LivingEntity shooter, float dmg) {
        if (target == null || shooter == null || !shooter.m_6084_() || !(target.m_9236_() instanceof ServerLevel sl)) return false;
        int c = parts(target, Set.CRYSTAL);
        if (c < 4 || !ArmorSetLogic.reflects(c, Fx.RNG.nextDouble())) return false;
        double ax = target.m_20185_(), ay = target.m_20186_() + 1.0, az = target.m_20189_();
        double bx = shooter.m_20185_(), by = shooter.m_20186_() + 1.0,bz = shooter.m_20189_();
        for (int i = 0; i <= 14; i++) {
            double u = i / 14.0;
            Fx.send(sl, Fx.particle("enchanted_hit"), ax + (bx - ax) * u, ay + (by - ay) * u, az + (bz - az) * u, 1, 0, 0, 0, 0);
        }
        Fx.send(sl, Fx.particle("end_rod"), ax, ay, az, 12, 0.3, 0.4, 0.3, 0.08);
        Fx.sound(sl, ax, ay, az, "block.amethyst_block.hit", 1.0F, 1.7F);
        Fx.sound(sl, bx, by, bz, "block.amethyst_cluster.hit", 1.0F, 1.3F);
        shooter.m_6469_(sl.m_269111_().m_269075_(target), dmg);
        return true;
    }

    /** Кристальный доспех, 2 части: толчок слабее на 10%. */
    static void onKnock(LivingKnockBackEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p)) return;
        float m = ArmorSetLogic.knockMul(parts(p, Set.CRYSTAL));
        if (m < 1.0F) ev.setStrength(ev.getStrength() * m);
    }

    /** Облачение Сновидца: ванильные мобы замечают игрока с меньшего расстояния (мобы данжей - в RiftMobs.Mob.nearestFrom и слухе Пожирателя). */
    static void onVisibility(LivingEvent.LivingVisibilityEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p)) return;
        double m = noticeMul(p);
        if (m < 1.0) ev.modifyVisibility(m);
    }

    static void clear() { MODS.clear(); EDGE.clear(); }

    static void onStopping(ServerStoppingEvent ev) { clear(); }

    /** Выход игрока: его запись в MODS/EDGE не нужна (модификаторы временные и при заходе ставятся заново). */
    static void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent ev) {
        if (ev.getEntity() == null) return;
        MODS.remove(ev.getEntity().m_20148_()); EDGE.remove(ev.getEntity().m_20148_());
    }
}
