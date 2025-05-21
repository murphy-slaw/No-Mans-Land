package com.farcr.nomansland.common.entity.tortoise;

import com.farcr.nomansland.common.registry.NMLTags;
import com.farcr.nomansland.common.registry.entities.NMLEntities;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.util.Pair;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.*;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;

import java.util.Set;
import java.util.function.Predicate;

public class TortoiseAi {
    private static final float SPEED_MULTIPLIER_WHEN_PANICKING = 2.0F;
    private static final float SPEED_MULTIPLIER_WHEN_IDLING = 1.0F;
    private static final float SPEED_MULTIPLIER_WHEN_TEMPTED = 1.25F;
    private static final float SPEED_MULTIPLIER_WHEN_FOLLOWING_ADULT = 1.25F;
    private static final float SPEED_MULTIPLIER_WHEN_MAKING_LOVE = 1.0F;
    private static final double DEFAULT_CLOSE_ENOUGH_DIST = 2.0;
    private static final double BABY_CLOSE_ENOUGH_DIST = 1.0;

    private static final UniformInt ADULT_FOLLOW_RANGE = UniformInt.of(5, 16);

    private static final ImmutableList<SensorType<? extends Sensor<? super Tortoise>>> SENSOR_TYPES = ImmutableList.of(
            SensorType.NEAREST_LIVING_ENTITIES,
            SensorType.HURT_BY,
//            SensorType.ARMADILLO_TEMPTATIONS,
            SensorType.NEAREST_ADULT
//            SensorType.ARMADILLO_SCARE_DETECTED
    );

    private static final ImmutableList<MemoryModuleType<?>> MEMORY_TYPES = ImmutableList.of(
            MemoryModuleType.IS_PANICKING,
            MemoryModuleType.HURT_BY,
            MemoryModuleType.HURT_BY_ENTITY,
            MemoryModuleType.WALK_TARGET,
            MemoryModuleType.LOOK_TARGET,
            MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE,
            MemoryModuleType.PATH,
            MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
            MemoryModuleType.TEMPTING_PLAYER,
            MemoryModuleType.TEMPTATION_COOLDOWN_TICKS,
            MemoryModuleType.GAZE_COOLDOWN_TICKS,
            MemoryModuleType.IS_TEMPTED,
            MemoryModuleType.BREED_TARGET,
            MemoryModuleType.NEAREST_VISIBLE_ADULT,
            MemoryModuleType.DANGER_DETECTED_RECENTLY
    );

    public static Brain.Provider<Tortoise> brainProvider() {
        return Brain.provider(MEMORY_TYPES, SENSOR_TYPES);
    }

    protected static Brain<?> makeBrain(Brain<Tortoise> brain) {
        initCoreActivity(brain);
        initIdleActivity(brain);
        brain.setCoreActivities(Set.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.useDefaultActivity();
        return brain;
    }

    private static void initCoreActivity(Brain<Tortoise> brain) {
        brain.addActivity(
                Activity.CORE,
                0,
                ImmutableList.of(
                        new Swim(0.8F),
                        new LookAtTargetSink(45, 90),
                        new MoveToTargetSink(),
                        new CountDownCooldownTicks(MemoryModuleType.TEMPTATION_COOLDOWN_TICKS),
                        new CountDownCooldownTicks(MemoryModuleType.GAZE_COOLDOWN_TICKS))
        );
    }

    private static void initIdleActivity(Brain<Tortoise> brain) {
        brain.addActivity(
                Activity.IDLE,
                ImmutableList.of(
                        Pair.of(0, SetEntityLookTargetSometimes.create(EntityType.PLAYER, 6.0F, UniformInt.of(30, 60))),
                        Pair.of(1, new AnimalMakeLove(NMLEntities.TORTOISE.get(), SPEED_MULTIPLIER_WHEN_MAKING_LOVE, 1)),
                        Pair.of(2, new RunOne<>(
                                        ImmutableList.of(
                                                Pair.of(new FollowTemptation(entity -> SPEED_MULTIPLIER_WHEN_TEMPTED, entity -> entity.isBaby() ? BABY_CLOSE_ENOUGH_DIST : DEFAULT_CLOSE_ENOUGH_DIST), 1),
                                                Pair.of(BabyFollowAdult.create(ADULT_FOLLOW_RANGE, SPEED_MULTIPLIER_WHEN_FOLLOWING_ADULT), 1)
                                        )
                                )
                        ),
                        Pair.of(3, new RandomLookAround(UniformInt.of(150, 250), 30.0F, 0.0F, 0.0F)),
                        Pair.of(4, new RunOne<>(
                                        ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT),
                                        ImmutableList.of(
                                                Pair.of(RandomStroll.stroll(SPEED_MULTIPLIER_WHEN_IDLING), 1),
                                                Pair.of(SetWalkTargetFromLookTarget.create(SPEED_MULTIPLIER_WHEN_IDLING, 3), 1),
                                                Pair.of(new DoNothing(30, 60), 1)
                                        )
                                )
                        )
                )
        );
    }

    public static void tick(Tortoise tortoise) {
        ((Brain<Tortoise>) tortoise.getBrain()).tick((ServerLevel) tortoise.level(), tortoise);
    }

    public static void updateActivity(Tortoise tortoise) {
        tortoise.getBrain().setActiveActivityToFirstValid(ImmutableList.of(Activity.IDLE));
    }

    public static Predicate<ItemStack> getTemptations() {
        return stack -> stack.is(NMLTags.TORTOISE_FOOD);
    }
}

