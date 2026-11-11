package com.example.watercube;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.WorldEvents;

/**
 * A cube of water. While its owner holds sneak it hovers in front of them.
 * When sneak is released it flies toward the owner's crosshair.
 * All logic runs on the server; the client only renders it.
 */
public class WaterCubeEntity extends ProjectileEntity {
    // ---- Tweak these ----
    private static final double SPEED = 1.6;           // blocks per tick when fired
    private static final double GRAVITY = 0.01;        // 0 = perfectly straight
    private static final double HOVER_DISTANCE = 2.2;  // blocks in front of the eyes
    private static final int MAX_CHARGE_TICKS = 20 * 15;
    private static final int MAX_FLIGHT_TICKS = 20 * 5;
    private static final float DAMAGE = 3.0f;          // set to 0 for no damage
    private static final double KNOCKBACK = 0.9;

    private boolean launched = false;
    private int ticks = 0;

    public WaterCubeEntity(EntityType<? extends WaterCubeEntity> type, World world) {
        super(type, world);
        this.setNoGravity(true);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        // nothing to sync
    }

    @Override
    public void tick() {
        super.tick();
        if (!(getWorld() instanceof ServerWorld world)) return;

        ticks++;
        if (launched) {
            tickFlying(world);
        } else {
            tickCharging(world);
        }
    }

    private void tickCharging(ServerWorld world) {
        if (!(getOwner() instanceof ServerPlayerEntity owner)
                || !owner.isAlive() || owner.getWorld() != world) {
            discard();
            return;
        }
        if (!owner.isSneaking() || ticks > MAX_CHARGE_TICKS) {
            launch(world, owner);
            return;
        }

        // Glide from the water toward a point in front of the player.
        Vec3d target = owner.getEyePos().add(owner.getRotationVec(1.0f).multiply(HOVER_DISTANCE));
        Vec3d center = getBoundingBox().getCenter();
        Vec3d next = center.lerp(target, 0.25);
        setPosition(next.x, next.y - getHeight() / 2.0, next.z);

        Vec3d c = getBoundingBox().getCenter();
        world.spawnParticles(ParticleTypes.SPLASH, c.x, c.y, c.z, 3, 0.35, 0.35, 0.35, 0.0);
        world.spawnParticles(ParticleTypes.FALLING_WATER, c.x, c.y - 0.3, c.z, 2, 0.3, 0.2, 0.3, 0.0);
        world.spawnParticles(ParticleTypes.DRIPPING_WATER, c.x, c.y, c.z, 1, 0.4, 0.4, 0.4, 0.0);
    }

    private void launch(ServerWorld world, ServerPlayerEntity owner) {
        Vec3d eye = owner.getEyePos();
        Vec3d look = owner.getRotationVec(1.0f);
        Vec3d center = getBoundingBox().getCenter();

        // Aim at whatever the crosshair is pointing at (blocks only).
        Vec3d end = eye.add(look.multiply(64.0));
        BlockHitResult aim = world.raycast(new RaycastContext(
                eye, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, owner));
        Vec3d dir = aim.getPos().subtract(center);
        if (dir.lengthSquared() < 1.0E-4 || dir.dotProduct(look) <= 0) {
            dir = look;
        }

        setVelocity(dir.normalize().multiply(SPEED));
        launched = true;
        ticks = 0;
        world.playSound(null, getX(), getY(), getZ(),
                SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED, SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    private void tickFlying(ServerWorld world) {
        if (ticks > MAX_FLIGHT_TICKS) {
            burst(world, getBoundingBox().getCenter());
            return;
        }

        Vec3d vel = getVelocity();
        HitResult hit = ProjectileUtil.getCollision(this, this::canHit);
        if (hit.getType() != HitResult.Type.MISS) {
            onCollision(hit);
            if (isRemoved()) return;
        }

        setPosition(getPos().add(vel));
        setVelocity(vel.add(0.0, -GRAVITY, 0.0));

        Vec3d c = getBoundingBox().getCenter();
        world.spawnParticles(ParticleTypes.SPLASH, c.x, c.y, c.z, 4, 0.3, 0.3, 0.3, 0.0);
        world.spawnParticles(ParticleTypes.FALLING_WATER, c.x, c.y, c.z, 3, 0.3, 0.3, 0.3, 0.0);
    }

    @Override
    protected void onEntityHit(EntityHitResult hit) {
        super.onEntityHit(hit);
        if (!(getWorld() instanceof ServerWorld world)) return;

        Entity target = hit.getEntity();
        if (DAMAGE > 0) {
            target.damage(getDamageSources().thrown(this, getOwner()), DAMAGE);
        }
        target.extinguish();
        if (target instanceof LivingEntity living) {
            Vec3d v = getVelocity();
            living.takeKnockback(KNOCKBACK, -v.x, -v.z);
        }
        burst(world, hit.getPos());
    }

    @Override
    protected void onBlockHit(BlockHitResult hit) {
        super.onBlockHit(hit);
        if (!(getWorld() instanceof ServerWorld world)) return;

        BlockPos firePos = hit.getBlockPos().offset(hit.getSide());
        if (world.getBlockState(firePos).isIn(BlockTags.FIRE)) {
            world.removeBlock(firePos, false);
            world.syncWorldEvent(null, WorldEvents.FIRE_EXTINGUISHED, firePos, 0);
        }
        burst(world, hit.getPos());
    }

    private void burst(ServerWorld world, Vec3d at) {
        world.spawnParticles(ParticleTypes.SPLASH, at.x, at.y, at.z, 80, 0.5, 0.5, 0.5, 0.2);
        world.spawnParticles(ParticleTypes.FALLING_WATER, at.x, at.y, at.z, 30, 0.5, 0.5, 0.5, 0.0);
        world.playSound(null, at.x, at.y, at.z,
                SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.PLAYERS, 1.0f, 1.0f);
        discard();
    }
}
