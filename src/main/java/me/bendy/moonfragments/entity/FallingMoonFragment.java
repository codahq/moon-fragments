package me.bendy.moonfragments.entity;

import me.bendy.moonfragments.Config;
import me.bendy.moonfragments.registry.ModItems;
import me.bendy.moonfragments.util.MoonTime;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * A moon fragment falling from the night sky (issue #1).
 * <ol>
 * <li>{@link Stage#FALLING}: a ball of light plummeting with a sparkle trail. Explodes on impact.</li>
 * <li>{@link Stage#BEACON}: once settled, a ball of light with a white beacon beam. Fades at sunrise.</li>
 * <li>{@link Stage#FRAGMENT}: when a player comes within {@value #REVEAL_RADIUS} blocks, the beacon is replaced by the
 * collectible fragment (right-click to pick up). It melts away after {@value #MELT_TICKS} ticks of daylight.</li>
 * </ol>
 *
 * <p><b>Why a {@link LivingEntity}:</b> minimap mods (Xaero's, JourneyMap, VoxelMap) only build a radar icon for an
 * entity whose renderer is a {@code LivingEntityRenderer}, by reading its model's head. A plain {@code Entity} never
 * shows up. This isn't a creature, though: like an armor stand it has no AI, can't be hurt, pushed or given effects,
 * and runs its own physics in {@link #travel}. The icon model is {@code MoonFragmentIconModel}.
 */
public class FallingMoonFragment extends LivingEntity {
    public enum Stage { FALLING, BEACON, FRAGMENT }

    public static final double REVEAL_RADIUS = 15.0;
    /** 30 seconds of daylight. */
    public static final int MELT_TICKS = 30 * 20;
    /** Time after impact before the beacon can switch to a fragment, so the crater can settle. */
    private static final int SETTLE_TICKS = 20;
    private static final double MAX_FALL_SPEED = 3.0;

    private static final EntityDataAccessor<Integer> DATA_STAGE =
            SynchedEntityData.defineId(FallingMoonFragment.class, EntityDataSerializers.INT);
    /** Resting tilt of the revealed fragment in degrees: 0 = dome up, 90 = on its round side, 180 = flat face up. */
    private static final EntityDataAccessor<Float> DATA_TILT =
            SynchedEntityData.defineId(FallingMoonFragment.class, EntityDataSerializers.FLOAT);
    /** Game time when the fragment was revealed, for the client's roll animation. */
    private static final EntityDataAccessor<Long> DATA_REVEAL_TIME =
            SynchedEntityData.defineId(FallingMoonFragment.class, EntityDataSerializers.LONG);
    /** How long the reveal roll animation lasts. */
    public static final int ROLL_TICKS = 18;

    private int stageTicks;
    private int daylightTicks;
    /** Set when called into an unloaded chunk: keep the chunk loaded until the fragment has landed and settled. */
    private boolean keepLoaded;
    private long ticketTimer;

    public FallingMoonFragment(EntityType<? extends FallingMoonFragment> type, Level level) {
        super(type, level);
        // Invulnerable also keeps mobs that hunt any living thing (wardens) from targeting it.
        setInvulnerable(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes();
    }

    /** Starts a fragment falling in a straight line at a constant velocity (it can come in at an angle). */
    public void startFalling(Vec3 velocity) {
        setStage(Stage.FALLING);
        setDeltaMovement(velocity);
    }

    /** Keep this fragment's chunk loaded until it has landed and settled (see {@link #placeChunkTicket}). */
    public void setKeepLoaded(boolean keepLoaded) {
        this.keepLoaded = keepLoaded;
    }

    /**
     * Loads and simulates the chunks around {@code pos} for a couple of seconds, the same way a thrown ender pearl does.
     * The ticket expires on its own unless refreshed.
     */
    public static long placeChunkTicket(ServerLevel level, ChunkPos pos) {
        level.getChunkSource().addTicketWithRadius(TicketType.ENDER_PEARL, pos, 2);
        return TicketType.ENDER_PEARL.timeout();
    }

    private void refreshChunkTicket(Stage stage) {
        if (!keepLoaded || !(level() instanceof ServerLevel server)) {
            return;
        }
        boolean settling = stage == Stage.FALLING || (stage == Stage.BEACON && stageTicks <= SETTLE_TICKS * 2);
        if (!settling) {
            keepLoaded = false; // let the ticket expire; the beacon waits until someone comes near
            return;
        }
        if (--ticketTimer <= 0L) {
            ticketTimer = placeChunkTicket(server, chunkPosition()) - 1L;
        }
    }

    public Stage getStage() {
        int ordinal = entityData.get(DATA_STAGE);
        return Stage.values()[Math.clamp(ordinal, 0, Stage.values().length - 1)];
    }

    private void setStage(Stage stage) {
        entityData.set(DATA_STAGE, stage.ordinal());
        stageTicks = 0;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STAGE, Stage.FALLING.ordinal());
        builder.define(DATA_TILT, 0.0F);
        builder.define(DATA_REVEAL_TIME, Long.MIN_VALUE / 2); // long ago: no roll animation
    }

    public float getTilt() {
        return entityData.get(DATA_TILT);
    }

    /** 0 when the fragment has just been revealed, 1 once its roll has finished. */
    public float getRollProgress(float partialTick) {
        long ticks = level().getGameTime() - entityData.get(DATA_REVEAL_TIME);
        if (ticks >= ROLL_TICKS) {
            return 1.0F;
        }
        float elapsed = ticks + partialTick;
        return Math.clamp(elapsed / ROLL_TICKS, 0.0F, 1.0F);
    }

    @Override
    protected double getDefaultGravity() {
        return 0.08;
    }

    /**
     * Replaces living-entity movement with the fragment's own physics. The falling stage flies in a straight line at
     * constant velocity (so angled entries hit the chosen spot); later stages settle with gravity and ground friction.
     * Runs on the server; clients interpolate to the synced position like any living entity.
     */
    @Override
    public void travel(Vec3 input) {
        Stage stage = getStage();
        if (stage != Stage.FALLING) {
            if (!onGround()) {
                applyGravity();
            }
            Vec3 motion = getDeltaMovement();
            if (motion.y < -MAX_FALL_SPEED) {
                setDeltaMovement(motion.x, -MAX_FALL_SPEED, motion.z);
            }
        }
        resetFallDistance(); // impact() is the landing effect: no fall damage or landing dust
        move(MoverType.SELF, getDeltaMovement());
        if (onGround()) {
            setDeltaMovement(getDeltaMovement().multiply(0.6, 0.0, 0.6));
        } else if (stage != Stage.FALLING) {
            setDeltaMovement(getDeltaMovement().multiply(0.98, 1.0, 0.98));
        }
    }

    @Override
    public void tick() {
        super.tick(); // moves via travel()
        Stage stage = getStage();

        if (level().isClientSide()) {
            spawnAmbientParticles(stage);
            return;
        }

        stageTicks++;
        refreshChunkTicket(stage);
        if (getY() < level().getMinY() - 64) {
            discard();
            return;
        }

        switch (stage) {
            case FALLING -> {
                if (onGround() || verticalCollision || horizontalCollision || isInWater()) {
                    impact();
                    setStage(Stage.BEACON);
                }
            }
            case BEACON -> {
                if (!MoonTime.isNight(level())) {
                    poof(false);
                } else if (stageTicks >= SETTLE_TICKS && onGround()
                        && level().getNearestPlayer(getX(), getY(), getZ(), REVEAL_RADIUS, EntitySelector.NO_SPECTATORS) != null) {
                    revealFragment();
                }
            }
            case FRAGMENT -> {
                if (!MoonTime.isNight(level())) {
                    daylightTicks++;
                    if (daylightTicks >= MELT_TICKS) {
                        poof(true);
                    } else if (daylightTicks % 10 == 0 && level() instanceof ServerLevel server) {
                        // Melting: a little steam that thickens as time runs out.
                        int count = 1 + daylightTicks * 4 / MELT_TICKS;
                        server.sendParticles(ParticleTypes.WHITE_SMOKE, getX(), getY() + 0.2, getZ(), count, 0.15, 0.05, 0.15, 0.01);
                    }
                }
            }
        }
    }

    private void impact() {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        float power = Config.IMPACT_EXPLOSION_POWER.get().floatValue();
        if (power > 0.0F) {
            Level.ExplosionInteraction interaction = Config.IMPACT_BREAKS_BLOCKS.getAsBoolean()
                    ? Level.ExplosionInteraction.MOB // MOB respects the mobGriefing gamerule
                    : Level.ExplosionInteraction.NONE;
            ExplosionDamageCalculator calculator = Config.IMPACT_DAMAGES_ENTITIES.getAsBoolean() ? null : NO_ENTITY_DAMAGE;
            server.explode(this, null, calculator, getX(), getY(), getZ(), power, false, interaction);
        } else {
            server.sendParticles(ParticleTypes.EXPLOSION, getX(), getY(), getZ(), 1, 0.0, 0.0, 0.0, 0.0);
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 2.0F, 1.2F);
        }
        server.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 0.5, getZ(), 40, 0.4, 0.4, 0.4, 0.25);
    }

    private void revealFragment() {
        setStage(Stage.FRAGMENT);
        // Settle: a little hop, rolling end over end in the direction it moves, coming to rest in a random pose.
        double angle = random.nextDouble() * Math.PI * 2.0;
        double dx = Math.cos(angle);
        double dz = Math.sin(angle);
        setDeltaMovement(dx * 0.12, 0.18, dz * 0.12);
        setYRot((float) Math.toDegrees(Math.atan2(-dx, dz))); // facing the roll direction
        float pose = random.nextFloat();
        float tilt;
        if (pose < 0.35F) {
            tilt = (random.nextFloat() - 0.5F) * 16.0F;          // dome up, slight wobble
        } else if (pose < 0.75F) {
            tilt = 75.0F + random.nextFloat() * 30.0F;           // lying on its round side
        } else {
            tilt = 140.0F + random.nextFloat() * 40.0F;          // tipped over, flat face up
        }
        entityData.set(DATA_TILT, tilt);
        entityData.set(DATA_REVEAL_TIME, level().getGameTime());
        playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.5F, 0.8F);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 0.3, getZ(), 20, 0.3, 0.3, 0.3, 0.05);
        }
    }

    /** Vanishes with a puff of particles: melting in daylight, or a beacon fading at sunrise. */
    private void poof(boolean melted) {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.2, getZ(), 12, 0.2, 0.15, 0.2, 0.02);
            server.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 0.3, getZ(), 8, 0.2, 0.2, 0.2, 0.05);
        }
        playSound(melted ? SoundEvents.FIRE_EXTINGUISH : SoundEvents.BEACON_DEACTIVATE, 1.0F, 1.2F);
        discard();
    }

    private void spawnAmbientParticles(Stage stage) {
        Level level = level();
        switch (stage) {
            case FALLING -> {
                // A dense trail along the whole path covered this tick (the fragment moves 1.5+ blocks per tick).
                // "Always visible" particles skip the ~32-block cutoff so the streak can be seen from far away.
                // End rods glow and linger for about 3 seconds; soul flames add a short blue flicker.
                Vec3 motion = getDeltaMovement();
                double steps = 10;
                for (int i = 0; i < steps; i++) {
                    double t = i / steps;
                    double x = getX() - motion.x * t + (random.nextDouble() - 0.5) * 0.3;
                    double y = getY() + 0.25 - motion.y * t + (random.nextDouble() - 0.5) * 0.3;
                    double z = getZ() - motion.z * t + (random.nextDouble() - 0.5) * 0.3;
                    level.addAlwaysVisibleParticle(ParticleTypes.END_ROD, true, x, y, z,
                            (random.nextDouble() - 0.5) * 0.02, (random.nextDouble() - 0.5) * 0.02, (random.nextDouble() - 0.5) * 0.02);
                    if (i % 3 == 0) {
                        level.addAlwaysVisibleParticle(ParticleTypes.SOUL_FIRE_FLAME, true, x, y, z, 0.0, 0.0, 0.0);
                    }
                }
                level.addAlwaysVisibleParticle(ParticleTypes.FIREWORK, true, getX(), getY() + 0.25, getZ(),
                        (random.nextDouble() - 0.5) * 0.1, 0.05, (random.nextDouble() - 0.5) * 0.1);
            }
            case BEACON -> {
                if (random.nextInt(4) == 0) {
                    level.addParticle(ParticleTypes.END_ROD,
                            getX() + (random.nextDouble() - 0.5) * 0.6, getY() + 0.2, getZ() + (random.nextDouble() - 0.5) * 0.6,
                            0.0, 0.08, 0.0);
                }
            }
            case FRAGMENT -> {
                if (random.nextInt(12) == 0) {
                    level.addParticle(ParticleTypes.END_ROD,
                            getX() + (random.nextDouble() - 0.5) * 0.4, getY() + 0.3, getZ() + (random.nextDouble() - 0.5) * 0.4,
                            0.0, 0.02, 0.0);
                }
            }
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (getStage() != Stage.FRAGMENT || isRemoved()) {
            return InteractionResult.PASS;
        }
        if (!level().isClientSide()) {
            ItemStack stack = new ItemStack(ModItems.MOON_FRAGMENT.get());
            if (!player.addItem(stack)) {
                player.drop(stack, false);
            }
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 1.4F);
            discard();
        }
        return InteractionResult.SUCCESS;
    }

    /** Only the fragment can be targeted (for right-click pickup); the beam and falling light can't. */
    @Override
    public boolean isPickable() {
        return getStage() == Stage.FRAGMENT && !isRemoved();
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return true;
    }

    /** {@code /kill} on a living entity goes through {@link #hurtServer}, which refuses everything; remove it outright. */
    @Override
    public void kill(ServerLevel level) {
        remove(RemovalReason.KILLED);
        gameEvent(GameEvent.ENTITY_DIE);
    }

    // Not a creature: none of the living-entity side effects apply.

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    public boolean isAffectedByPotions() {
        return false;
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    /** No drowning bubbles when it lands in water. */
    @Override
    public boolean canDrownInFluidType(FluidType type) {
        return false;
    }

    @Override
    public boolean canFreeze() {
        return false;
    }

    /** Visible from far away so the beacon can be spotted across the landscape. */
    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double max = 512.0 * getViewScale();
        return distance < max * max;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        entityData.set(DATA_STAGE, Math.clamp(input.getIntOr("Stage", Stage.BEACON.ordinal()), 0, Stage.values().length - 1));
        stageTicks = input.getIntOr("StageTicks", 0);
        daylightTicks = input.getIntOr("DaylightTicks", 0);
        keepLoaded = input.getBooleanOr("KeepLoaded", false);
        entityData.set(DATA_TILT, input.getFloatOr("Tilt", 0.0F));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Stage", getStage().ordinal());
        output.putInt("StageTicks", stageTicks);
        output.putInt("DaylightTicks", daylightTicks);
        output.putBoolean("KeepLoaded", keepLoaded);
        output.putFloat("Tilt", getTilt());
    }

    private static final ExplosionDamageCalculator NO_ENTITY_DAMAGE = new ExplosionDamageCalculator() {
        @Override
        public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
            return false;
        }

        @Override
        public float getKnockbackMultiplier(Entity entity) {
            return 0.0F;
        }
    };
}
