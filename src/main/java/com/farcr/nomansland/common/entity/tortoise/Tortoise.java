package com.farcr.nomansland.common.entity.tortoise;

import com.farcr.nomansland.common.registry.NMLTags;
import com.farcr.nomansland.common.registry.entities.NMLEntities;
import com.google.common.base.Suppliers;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.function.Supplier;

public class Tortoise extends Animal {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final EntityDataAccessor<BlockPos> HOME_POS = SynchedEntityData.defineId(Tortoise.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<BlockPos> TRAVEL_POS = SynchedEntityData.defineId(Tortoise.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Boolean> HAS_EGG = SynchedEntityData.defineId(Tortoise.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> LAYING_EGG = SynchedEntityData.defineId(Tortoise.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> GOING_HOME = SynchedEntityData.defineId(Tortoise.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> TRAVELLING = SynchedEntityData.defineId(Tortoise.class, EntityDataSerializers.BOOLEAN);

    private static final float BABY_SCALE = 0.3F;
    private static final Supplier<EntityDimensions> BABY_DIMENSIONS = Suppliers.memoize(() -> NMLEntities.TORTOISE.get().getDimensions()
            .withAttachments(EntityAttachments.builder()
                    .attach(EntityAttachment.PASSENGER, 0.0F, NMLEntities.TORTOISE.get().getHeight(), -0.25F))
            .scale(BABY_SCALE));

    private int layEggCounter;

    public Tortoise(EntityType<? extends Tortoise> entityType, Level level) {
        super(entityType, level);
    }

    public static boolean checkTortoiseSpawnRules(EntityType<Tortoise> tortoise, LevelAccessor level, MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        return pos.getY() < level.getSeaLevel() + 4 && TurtleEggBlock.onSand(level, pos) && isBrightEnoughToSpawn(level, pos);
    }


    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0F)
                .add(Attributes.MOVEMENT_SPEED, 0.25F)
                .add(Attributes.STEP_HEIGHT, 1.0F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(Tortoise.HOME_POS, BlockPos.ZERO);
        builder.define(Tortoise.TRAVEL_POS, BlockPos.ZERO);
        builder.define(Tortoise.HAS_EGG, false);
        builder.define(Tortoise.LAYING_EGG, false);
        builder.define(Tortoise.GOING_HOME, false);
        builder.define(Tortoise.TRAVELLING, false);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        BlockPos.CODEC.encodeStart(NbtOps.INSTANCE, this.getHomePos()).resultOrPartial(LOGGER::error)
                .ifPresent(tag -> compound.put("home_pos", tag));
        BlockPos.CODEC.encodeStart(NbtOps.INSTANCE, this.getTravelPos()).resultOrPartial(LOGGER::error)
                .ifPresent(tag -> compound.put("travel_pos", tag));

        compound.putBoolean("HasEgg", this.hasEgg());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        BlockPos.CODEC.decode(NbtOps.INSTANCE, compound.get("home_pos")).resultOrPartial(LOGGER::error)
                .ifPresent(pair -> this.setHomePos(pair.getFirst()));

        BlockPos.CODEC.decode(NbtOps.INSTANCE, compound.get("travel_pos")).resultOrPartial(LOGGER::error)
                .ifPresent(pair -> this.setTravelPos(pair.getFirst()));

        this.setHasEgg(compound.getBoolean("HasEgg"));
    }

    @Override
    protected Brain.Provider<Tortoise> brainProvider() {
        return TortoiseAi.brainProvider();
    }

    @Override
    protected Brain<?> makeBrain(Dynamic<?> dynamic) {
        return TortoiseAi.makeBrain(this.brainProvider().makeBrain(dynamic));
    }

    @Override
    protected void customServerAiStep() {
        this.level().getProfiler().push("nmlTortoiseBrain");
        TortoiseAi.tick(this);
        this.level().getProfiler().pop();

        this.level().getProfiler().push("nmlTortoiseActivityUpdate");
        TortoiseAi.updateActivity(this);
        this.level().getProfiler().pop();

        super.customServerAiStep();
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData) {
        this.setHomePos(this.blockPosition());
        this.setTravelPos(BlockPos.ZERO);
        return super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
    }

    @Override
    @Nullable
    protected SoundEvent getAmbientSound() {
        return SoundEvents.TURTLE_AMBIENT_LAND;
    }

    @Override
    protected SoundEvent getSwimSound() {
        return SoundEvents.TURTLE_SWIM;
    }

    @Override
    @Nullable
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return this.isBaby() ? SoundEvents.TURTLE_HURT_BABY : SoundEvents.TURTLE_HURT;
    }

    @Override
    @Nullable
    protected SoundEvent getDeathSound() {
        return this.isBaby() ? SoundEvents.TURTLE_DEATH_BABY : SoundEvents.TURTLE_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState block) {
        SoundEvent soundevent = this.isBaby() ? SoundEvents.TURTLE_SHAMBLE_BABY : SoundEvents.TURTLE_SHAMBLE;
        this.playSound(soundevent, 0.15F, 1.0F);
    }

    @Override
    public boolean canFallInLove() {
        return super.canFallInLove() && !this.hasEgg();
    }

    @Override
    protected float nextStep() {
        return this.moveDist + 0.15F;
    }

    @Override
    public float getAgeScale() {
        return this.isBaby() ? BABY_SCALE : 1.0F;
    }

    @Override
    @Nullable
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        return NMLEntities.TORTOISE.get().create(level);
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(NMLTags.TORTOISE_FOOD);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.isAlive() && this.isLayingEgg() && this.layEggCounter >= 1 && this.layEggCounter % 5 == 0) {
            BlockPos blockpos = this.blockPosition();
            if (TurtleEggBlock.onSand(this.level(), blockpos)) {
                this.level().levelEvent(2001, blockpos, Block.getId(this.level().getBlockState(blockpos.below())));
                this.gameEvent(GameEvent.ENTITY_ACTION);
            }
        }

    }

    @Override
    protected void ageBoundaryReached() {
        super.ageBoundaryReached();
        if (!this.isBaby() && this.level().getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) {
            this.spawnAtLocation(Items.TURTLE_SCUTE, 1); //  TODO: tortoise scute
        }

    }

    @Override
    public void travel(Vec3 travelVector) {
        super.travel(travelVector);
    }

    @Override
    protected void sendDebugPackets() {
        super.sendDebugPackets();
        DebugPackets.sendEntityBrain(this);
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return this.isBaby() ? BABY_DIMENSIONS.get() : super.getDefaultDimensions(pose);
    }

    public void setHomePos(BlockPos homePos) {
        this.entityData.set(HOME_POS, homePos);
    }

    public BlockPos getHomePos() {
        return this.entityData.get(HOME_POS);
    }

    public void setTravelPos(BlockPos travelPos) {
        this.entityData.set(TRAVEL_POS, travelPos);
    }

    public BlockPos getTravelPos() {
        return this.entityData.get(TRAVEL_POS);
    }

    public boolean hasEgg() {
        return this.entityData.get(HAS_EGG);
    }

    public void setHasEgg(boolean hasEgg) {
        this.entityData.set(HAS_EGG, hasEgg);
    }

    public boolean isLayingEgg() {
        return this.entityData.get(LAYING_EGG);
    }

    public void setLayingEgg(boolean isLayingEgg) {
        this.layEggCounter = isLayingEgg ? 1 : 0;
        this.entityData.set(LAYING_EGG, isLayingEgg);
    }

    public boolean isGoingHome() {
        return this.entityData.get(GOING_HOME);
    }

    public void setGoingHome(boolean isGoingHome) {
        this.entityData.set(GOING_HOME, isGoingHome);
    }

    public boolean isTravelling() {
        return this.entityData.get(TRAVELLING);
    }

    public void setTravelling(boolean isTravelling) {
        this.entityData.set(TRAVELLING, isTravelling);
    }
}
