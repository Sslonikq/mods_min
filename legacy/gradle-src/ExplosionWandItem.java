package com.explosionwand;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

public class ExplosionWandItem extends Item {

    public ExplosionWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            double range = Config.RANGE.get();
            Vec3 eye = player.getEyePosition();
            Vec3 look = player.getViewVector(1.0F);
            Vec3 end = eye.add(look.scale(range));

            // 1) Луч до первого блока
            BlockHitResult blockHit = level.clip(new ClipContext(
                    eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            Vec3 target = blockHit.getType() != HitResult.Type.MISS ? blockHit.getLocation() : end;

            // 2) Если на пути есть моб/игрок — взрываем на нём
            AABB searchBox = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0D);
            EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                    level, player, eye, target, searchBox,
                    e -> !e.isSpectator() && e.isPickable());
            if (entityHit != null) {
                target = entityHit.getLocation();
            }

            // 3) Видимый магический луч
            double dist = eye.distanceTo(target);
            for (double d = 1.5D; d < dist; d += 0.75D) {
                Vec3 p = eye.add(look.scale(d));
                serverLevel.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }

            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 0.6F);

            // 4) БАБАХ
            level.explode(player, target.x, target.y, target.z,
                    Config.POWER.get().floatValue(), Config.FIRE.get(),
                    Level.ExplosionInteraction.TNT);

            player.getCooldowns().addCooldown(this, Config.COOLDOWN.get());
        }

        player.swing(hand, true);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true; // светится как зачарованный
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.explosionwand.explosion_wand.tooltip")
                .withStyle(ChatFormatting.GOLD));
    }
}
