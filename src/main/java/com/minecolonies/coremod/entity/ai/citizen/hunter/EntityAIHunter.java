package com.minecolonies.coremod.entity.ai.citizen.hunter;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.views.MobEntryView;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.entity.ModEntities;
import com.minecolonies.api.entity.ai.citizen.hunter.HunterTask;
import com.minecolonies.api.entity.ai.statemachine.AIEventTarget;
import com.minecolonies.api.entity.ai.statemachine.AIOneTimeEventTarget;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.AIBlockingEventType;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.entity.mobs.AbstractEntityMinecoloniesMob;
import com.minecolonies.api.entity.pathfinding.PathResult;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.SoundUtils;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.api.util.constant.ToolType;
import com.minecolonies.coremod.MineColonies;
import com.minecolonies.coremod.Network;
import com.minecolonies.coremod.colony.buildings.workerbuildings.BuildingHunter;
import com.minecolonies.coremod.colony.jobs.JobHunter;
import com.minecolonies.coremod.entity.SittingEntity;
import com.minecolonies.coremod.entity.ai.basic.AbstractEntityAIFight;
import com.minecolonies.coremod.entity.ai.basic.AbstractEntityAIHunt;
import com.minecolonies.coremod.entity.citizen.EntityCitizen;
import com.minecolonies.coremod.entity.pathfinding.MinecoloniesAdvancedPathNavigate;
import com.minecolonies.coremod.entity.pathfinding.pathjobs.PathJobCanSee;
import com.minecolonies.coremod.entity.pathfinding.pathjobs.PathJobWalkRandomEdge;
import com.minecolonies.coremod.network.messages.client.SleepingParticleMessage;
import com.minecolonies.coremod.research.AdditionModifierResearchEffect;
import com.minecolonies.coremod.research.MultiplierModifierResearchEffect;
import com.minecolonies.coremod.research.UnlockAbilityResearchEffect;
import com.minecolonies.coremod.util.NamedDamageSource;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.ArrowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.*;
import static com.minecolonies.api.research.util.ResearchConstants.*;
import static com.minecolonies.api.research.util.ResearchConstants.ARROW_ITEMS;
import static com.minecolonies.api.util.constant.CitizenConstants.BIG_SATURATION_FACTOR;
import static com.minecolonies.api.util.constant.Constants.*;
import static com.minecolonies.api.util.constant.HunterConstants.*;

public class EntityAIHunter extends AbstractEntityAIHunt<JobHunter, BuildingHunter>
{
    private static final int    TIME_STRAFING_BEFORE_SWITCHING_DIRECTIONS = 4;
    private static final double SWITCH_STRAFING_DIRECTION                 = 0.3d;
    private static final double STRAFING_SPEED                            = 0.7f;
    private static final double ARROW_EXTRA_DAMAGE                        = 2.0f;

    /**
     * Visible combat icon
     */
    private final static VisibleCitizenStatus ARCHER_COMBAT     =
            new VisibleCitizenStatus(new ResourceLocation(Constants.MOD_ID, "textures/icons/work/archer_combat.png"), "com.minecolonies.gui.visiblestatus.archer_combat");
    private static final int                  HUNTER_BONUS_RANGE = 10;

    /**
     * Whether the hunter is moving towards his target
     */
    private boolean movingToTarget = false;

    /**
     * Indicates if strafing should be clockwise or not.
     */
    private int strafingClockwise = 1;

    /**
     * Amount of time strafing is able to run.
     */
    private int strafingTime = -1;

    /**
     * Amount of time the hunter has been in one spot.
     */
    private int timeAtSameSpot = 0;

    /**
     * Number of ticks the hunter has been way too close to target.
     */
    private int tooCloseNumTicks = 0;

    /**
     * Boolean for fleeing pathfinding
     */
    private boolean fleeing = false;

    /**
     * Physical Attack delay in ticks.
     */
    public static final int RANGED_ATTACK_DELAY_BASE = 30;

    /**
     * The path for fleeing
     */
    private PathResult fleePath;

    /**
     * Amount of time the hunter has been able to see their target.
     */
    private int timeCanSee = 0;

    /**
     * Last distance to determine if the hunter is stuck.
     */
    private double lastDistance = 0.0f;

    /**
     * Entities to kill before dumping into chest.
     */
    private static final int ACTIONS_UNTIL_DUMPING = 5;

    /**
     * Max derivation of current position when patrolling.
     */
    private static final int MAX_PATROL_DERIVATION = 50;

    /**
     * Max derivation of current position when following..
     */
    private static final int MAX_FOLLOW_DERIVATION = 40;

    /**
     * Max derivation of current position when huntering.
     */
    private static final int MAX_HUNTER_DERIVATION = 10;

    /**
     * After this amount of ticks not seeing an entity stop persecution.
     */
    private static final int STOP_PERSECUTION_AFTER = TICKS_SECOND * 10;

    /**
     * How far off patrols are alterated to match a raider attack point, sq dist
     */
    private static final double PATROL_DEVIATION_RAID_POINT = 200 * 200;

    /**
     * Max bonus target search range from attack range
     */
    private static final int TARGET_RANGE_ATTACK_RANGE_BONUS = 18;

    /**
     * The amount of time the hunter counts as in combat after last combat action
     */
    protected static final int COMBAT_TIME = 30;

    /**
     * How many more ticks we have until next attack.
     */
    protected int currentAttackDelay = 0;

    /**
     * The last time the target was seen.
     */
    private int lastSeen = 0;

    /**
     * The current target for our hunter.
     */
    protected LivingEntity target = null;

    /**
     * The current blockPos we're patrolling at.
     */
    private BlockPos currentPatrolPoint = null;

    /**
     * The citizen this hunter is helping out.
     */
    private WeakReference<EntityCitizen> helpCitizen = new WeakReference<>(null);

    /**
     * The hunter building assigned to this job.
     */
    protected final BuildingHunter buildingHunter;

    /**
     * The interval between sleeping particles
     */
    private static final int PARTICLE_INTERVAL = 30;

    /**
     * Interval between sleep checks
     */
    private static final int SHOULD_SLEEP_INTERVAL = 200;

    /**
     * Check target interval
     */
    private static final int CHECK_TARGET_INTERVAL = 10;

    /**
     * Search area for target interval
     */
    private static final int SEARCH_TARGET_INTERVAL = 40;

    /**
     * Interval between hunter task updates
     */
    private static final int HUNTER_TASK_INTERVAL = 100;

    /**
     * Interval between hunter regen updates
     */
    private static final int HUNTER_REGEN_INTERVAL = 40;

    /**
     * Interval between saturation losses during rallying. BIG_SATURATION_FACTOR loss per interval.
     */
    private static final int RALLY_SATURATION_LOSS_INTERVAL = TICKS_SECOND * 12;

    /**
     * Amount of regular actions before the action counter is increased
     */
    private static final int ACTION_INCREASE_INTERVAL = 10;

    /**
     * The timer for sleeping.
     */
    private int sleepTimer = 0;

    /**
     * Timer for the wakeup AI.
     */
    private int wakeTimer = 0;

    /**
     * Timer for fighting, goes down to 0 when hasnt been fighting for a while
     */
    protected int fighttimer = 0;

    /**
     * The sleeping hunter we found
     */
    private WeakReference<EntityCitizen> sleepingHunter = new WeakReference<>(null);

    /**
     * Random generator for this AI.
     */
    private Random randomGenerator = new Random();

    /**
     * Small timer for increasing actions done for continuous actions
     */
    private int regularActionTimer = 0;

    /**
     * Creates the abstract part of the AI. Always use this constructor!
     *
     * @param job the job to fulfill
     */
    public EntityAIHunter(@NotNull final JobHunter job)
    {
        super(job);
        super.registerTargets(
                // Note that DECIDE is only here for compatibility purposes. The hunter should use HUNTER_DECIDE internally.
                new AITarget(DECIDE, this::decide, HUNTER_TASK_INTERVAL),
                new AITarget(HUNTER_DECIDE, this::decide, HUNTER_TASK_INTERVAL),
                new AITarget(HUNTER_PATROL, this::shouldSleep, () -> HUNTER_SLEEP, SHOULD_SLEEP_INTERVAL),
                new AIEventTarget(AIBlockingEventType.STATE_BLOCKING, this::checkAndAttackTarget, CHECK_TARGET_INTERVAL),
                new AITarget(HUNTER_PATROL, () -> searchNearbyTarget() != null, this::checkAndAttackTarget, SEARCH_TARGET_INTERVAL),
                new AITarget(HUNTER_PATROL, this::decide, HUNTER_TASK_INTERVAL),
                new AITarget(HUNTER_SLEEP, this::sleep, 1),
                new AITarget(HUNTER_SLEEP, this::sleepParticles, PARTICLE_INTERVAL),
                new AITarget(HUNTER_WAKE, this::wakeUpHunter, TICKS_SECOND),
                new AITarget(HUNTER_FOLLOW, this::decide, HUNTER_TASK_INTERVAL),
                new AITarget(HUNTER_FOLLOW, () -> searchNearbyTarget() != null, this::checkAndAttackTarget, SEARCH_TARGET_INTERVAL),
                new AITarget(HUNTER_RALLY, this::decide, HUNTER_TASK_INTERVAL),
                new AITarget(HUNTER_RALLY, () -> searchNearbyTarget() != null, this::checkAndAttackTarget, SEARCH_TARGET_INTERVAL),
                new AITarget(HUNTER_RALLY, this::decreaseSaturation, RALLY_SATURATION_LOSS_INTERVAL),
                new AITarget(HUNTER_GUARD, this::shouldSleep, () -> HUNTER_SLEEP, SHOULD_SLEEP_INTERVAL),
                new AITarget(HUNTER_GUARD, this::decide, HUNTER_TASK_INTERVAL),
                new AITarget(HUNTER_GUARD, () -> searchNearbyTarget() != null, this::checkAndAttackTarget, SEARCH_TARGET_INTERVAL),
                new AITarget(HUNTER_REGEN, this::regen, HUNTER_REGEN_INTERVAL),
                new AITarget(HELP_CITIZEN, this::helping, HUNTER_TASK_INTERVAL)
        );
        buildingHunter = getOwnBuilding();
        super.registerTargets(
                new AITarget(HUNTER_ATTACK_RANGED, this::attackRanged, 10)
        );
        toolsNeeded.add(ToolType.BOW);
        worker.getNavigator().getPathingOptions().withJumpDropCost(0.95D);
    }

    /**
     * Wake up a nearby sleeping hunter
     *
     * @return next state
     */
    private IAIState wakeUpHunter()
    {
        if (sleepingHunter.get() == null || !(sleepingHunter.get().getCitizenJobHandler().getColonyJob() instanceof JobHunter) || !sleepingHunter.get()
                .getCitizenJobHandler()
                .getColonyJob(JobHunter.class)
                .isAsleep())
        {
            return HUNTER_DECIDE;
        }

        wakeTimer++;
        // Wait 1 sec
        if (wakeTimer == 1)
        {
            return getState();
        }

        // Move into range
        if (BlockPosUtil.getDistanceSquared(sleepingHunter.get().getPosition(), worker.getPosition()) > 4 && wakeTimer <= 10)
        {
            worker.getNavigator().moveToLivingEntity(sleepingHunter.get(), getCombatMovementSpeed());
        }
        else
        {
            worker.swingArm(Hand.OFF_HAND);
            sleepingHunter.get().attackEntityFrom(new NamedDamageSource("wakeywakey", worker).setDamageBypassesArmor(), 1);
            sleepingHunter.get().setRevengeTarget(worker);
            return HUNTER_DECIDE;
        }

        return getState();
    }

    /**
     * Whether the hunter should fall asleep.
     *
     * @return true if so
     */
    private boolean shouldSleep()
    {
        if (worker.getRevengeTarget() != null || target != null || fighttimer > 0)
        {
            return false;
        }

        double chance = 1;
        final MultiplierModifierResearchEffect
                effect = worker.getCitizenColonyHandler().getColony().getResearchManager().getResearchEffects().getEffect(SLEEP_LESS, MultiplierModifierResearchEffect.class);
        if (effect != null)
        {
            chance = 1 - effect.getEffect();
        }

        // Chance to fall asleep every 10sec, Chance is 1 in (10 + level/2) = 1 in Level1:5,Level2:6 Level6:8 Level 12:11 etc
        if (worker.getRandom().nextInt((int) (worker.getCitizenData().getCitizenSkillHandler().getLevel(Skill.Adaptability) * 0.5) + 20) == 1
                && worker.getRandom().nextDouble() < chance)
        {
            // Sleep for 2500-3000 ticks
            sleepTimer = worker.getRandom().nextInt(500) + 2500;

            final SittingEntity entity = (SittingEntity) ModEntities.SITTINGENTITY.create(world);
            entity.setPosition(worker.getPosX(), worker.getPosY() - 1f, worker.getPosZ());
            entity.setMaxLifeTime(sleepTimer);
            world.addEntity(entity);
            worker.startRiding(entity);
            worker.getNavigator().clearPath();

            return true;
        }

        return false;
    }

    /**
     * Emits sleeping particles and regens hp when asleep
     *
     * @return the next state to go into
     */
    private IAIState sleepParticles()
    {
        Network.getNetwork().sendToTrackingEntity(new SleepingParticleMessage(worker.getPosX(), worker.getPosY() + 2.0d, worker.getPosZ()), worker);

        if (worker.getHealth() < worker.getMaxHealth())
        {
            worker.setHealth(worker.getHealth() + 0.5f);
        }

        return null;
    }

    /**
     * Sleep activity
     *
     * @return the next state to go into
     */
    private IAIState sleep()
    {
        if (worker.getRevengeTarget() != null || (sleepTimer -= getTickRate()) < 0)
        {
            stopSleeping();
            return HUNTER_DECIDE;
        }

        worker.getLookController()
                .setLookPosition(worker.getPosX() + worker.getHorizontalFacing().getXOffset(),
                        worker.getPosY() + worker.getHorizontalFacing().getYOffset(),
                        worker.getPosZ() + worker.getHorizontalFacing().getZOffset(),
                        0f,
                        30f);
        return null;
    }

    /**
     * Stops the hunter from sleeping
     */
    private void stopSleeping()
    {
        if (getState() == HUNTER_SLEEP)
        {
            resetTarget();
            worker.setRevengeTarget(null);
            worker.stopRiding();
            worker.setPosition(worker.getPosX(), worker.getPosY() + 1, worker.getPosZ());
            worker.getCitizenExperienceHandler().addExperience(1);
        }
    }

    /**
     * Regen at the building and continue when more than half health.
     *
     * @return next state to go to.
     */
    private IAIState regen()
    {
        final AdditionModifierResearchEffect
                effect = worker.getCitizenColonyHandler().getColony().getResearchManager().getResearchEffects().getEffect(FLEEING_SPEED, AdditionModifierResearchEffect.class);
        if (effect != null)
        {
            if (!worker.isPotionActive(Effects.SPEED))
            {
                worker.addPotionEffect(new EffectInstance(Effects.SPEED, 200, (int) (0 + effect.getEffect())));
            }
        }

        if (walkToBuilding())
        {
            return HUNTER_REGEN;
        }

        if (worker.getHealth() < ((int) worker.getMaxHealth() * 0.75D) && buildingHunter.shallRetrieveOnLowHealth())
        {
            if (!worker.isPotionActive(Effects.REGENERATION))
            {
                worker.addPotionEffect(new EffectInstance(Effects.REGENERATION, 200));
            }
            return HUNTER_REGEN;
        }

        return START_WORKING;
    }

    /**
     * Checks and attacks the target
     *
     * @return next state
     */
    private IAIState checkAndAttackTarget()
    {
        if (getState() == HUNTER_SLEEP || getState() == HUNTER_REGEN || getState() == HUNTER_ATTACK_PROTECT || getState() == HUNTER_ATTACK_PHYSICAL
                || getState() == HUNTER_ATTACK_RANGED)
        {
            return null;
        }

        if (checkForTarget())
        {
            if (!hasTool())
            {
                return START_WORKING;
            }

            fighttimer = COMBAT_TIME;
            equipInventoryArmor();
            moveInAttackPosition();
            return getAttackState();
        }

        if (fighttimer > 0)
        {
            fighttimer--;
        }

        return null;
    }

    /**
     * Hunter at a specific position.
     *
     * @return the next state to run into.
     */
    private IAIState guard()
    {
        hunterMovement();
        return HUNTER_GUARD;
    }

    /**
     * Movement when hunting
     */
    public void hunterMovement()
    {
        worker.isWorkerAtSiteWithMove(buildingHunter.getHunterPos(), HUNTER_POS_RANGE);
        if (worker.isWorkerAtSiteWithMove(buildingHunter.getHunterPos(), 10) && Math.abs(buildingHunter.getHunterPos().getY() - worker.getPosition().getY()) < 3)
        {
            // Moves the ranger randomly to close edges, for better vision to mobs
            ((MinecoloniesAdvancedPathNavigate) worker.getNavigator()).setPathJob(new PathJobWalkRandomEdge(world, buildingHunter.getHunterPos(), 20, worker),
                    null,
                    getCombatMovementSpeed());
        }
    }

    @Override
    protected int getActionsDoneUntilDumping()
    {
        return ACTIONS_UNTIL_DUMPING * getOwnBuilding().getBuildingLevel();
    }

    /**
     * Rally to a location. This function assumes that the given location is reachable by the worker.
     *
     * @return the next state to run into.
     */
    private IAIState rally(final ILocation location)
    {
        final ICitizenData citizenData = worker.getCitizenData();
        if (citizenData != null)
        {
            if (!worker.isPotionActive(Effects.SPEED))
            {
                // Hunter will rally faster with higher skill.
                // Considering 99 is the maximum for any skill, the maximum theoretical getJobModifier() = 99 + 99/4 = 124. We want them to have Speed 5
                // when they're at half-max, so at about skill60. Therefore, divide the skill by 20.
                worker.addPotionEffect(new EffectInstance(Effects.SPEED,
                        5 * TICKS_SECOND,
                        MathHelper.clamp((citizenData.getCitizenSkillHandler().getLevel(Skill.Adaptability) / 20) + 2, 2, 5),
                        false,
                        false));
            }
        }

        return HUNTER_RALLY;
    }

    @Override
    protected IAIState startWorkingAtOwnBuilding()
    {
        final ILocation rallyLocation = buildingHunter.getRallyLocation();
        if (rallyLocation != null && rallyLocation.isReachableFromLocation(worker.getLocation()) || !canBeInterrupted())
        {
            return PREPARING;
        }

        // Walks to our building, only when not busy with another task
        return super.startWorkingAtOwnBuilding();
    }

    /**
     * Decrease the saturation while rallying. Rallying is hard work for the hunter, so make sure they suffer from it.
     *
     * @return The next state
     */
    protected IAIState decreaseSaturation()
    {
        final ICitizenData citizenData = worker.getCitizenData();

        if (citizenData != null)
        {
            citizenData.decreaseSaturation(citizenData.getSaturation() * BIG_SATURATION_FACTOR);
        }

        return getState();
    }

    /**
     * Patrol between a list of patrol points.
     *
     * @return the next patrol point to go to.
     */
    public IAIState patrol()
    {
        if (buildingHunter.requiresManualTarget())
        {
            if (currentPatrolPoint == null || worker.isWorkerAtSiteWithMove(currentPatrolPoint, 3))
            {
                if (worker.getRandom().nextInt(5) <= 1)
                {
                    currentPatrolPoint = buildingHunter.getColony().getBuildingManager().getRandomBuilding(b -> true);
                }
                else
                {
                    currentPatrolPoint = findRandomPositionToWalkTo(20);
                }

                if (currentPatrolPoint != null)
                {
                    setNextPatrolTarget(currentPatrolPoint);
                }
            }
        }
        else
        {
            if (currentPatrolPoint == null)
            {
                currentPatrolPoint = buildingHunter.getNextPatrolTarget(false);
            }

            if (currentPatrolPoint != null && (worker.isWorkerAtSiteWithMove(currentPatrolPoint, 3)))
            {
                buildingHunter.arrivedAtPatrolPoint(worker);
            }
        }
        return HUNTER_PATROL;
    }

    /**
     * Sets the next patrol target, and moves to it if patrolling
     *
     * @param target the next patrol target.
     */
    public void setNextPatrolTarget(final BlockPos target)
    {
        currentPatrolPoint = target;
        if (getState() == HUNTER_PATROL)
        {
            worker.isWorkerAtSiteWithMove(currentPatrolPoint, 2);
        }
    }

    /**
     * Check if the worker has the required tool to fight.
     *
     * @return true if so.
     */
    public boolean hasTool()
    {
        for (final ToolType toolType : toolsNeeded)
        {
            if (!InventoryUtils.hasItemHandlerToolWithLevel(getInventory(), toolType, 0, buildingHunter.getMaxToolLevel()))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Assigning the hunter to help a citizen.
     *
     * @param citizen  the citizen to help.
     * @param attacker the citizens attacker.
     */
    public void startHelpCitizen(final EntityCitizen citizen, final LivingEntity attacker)
    {
        if (canHelp())
        {
            registerTarget(new AIOneTimeEventTarget(HELP_CITIZEN));
            target = attacker;
            helpCitizen = new WeakReference<>(citizen);
        }
    }

    /**
     * Check if we can help a citizen
     *
     * @return true if not fighting/helping already
     */
    public boolean canHelp()
    {
        if (!isEntityValidTarget(target) && (getState() == HUNTER_PATROL || getState() == HUNTER_SLEEP) && canBeInterrupted())
        {
            // Stop sleeping when someone called for help
            stopSleeping();
            return true;
        }
        return false;
    }

    /**
     * Helping out a citizen, moving into range and setting attack target.
     *
     * @return the next state to go into
     */
    private IAIState helping()
    {
        reduceAttackDelay(HUNTER_TASK_INTERVAL * getTickRate());
        if (helpCitizen.get() == null || !helpCitizen.get().isCurrentlyFleeing())
        {
            return HUNTER_DECIDE;
        }

        if (target == null || !target.isAlive())
        {
            target = helpCitizen.get().getRevengeTarget();
            if (target == null || !target.isAlive())
            {
                return HUNTER_DECIDE;
            }
        }

        currentPatrolPoint = null;
        // Check if we're ready to attack the target
        if (worker.getEntitySenses().canSee(target) && isWithinPersecutionDistance(new BlockPos(target.getPositionVec())))
        {
            target.setRevengeTarget(worker);
            return checkAndAttackTarget();
        }

        // Move towards the target
        moveInAttackPosition();

        return HELP_CITIZEN;
    }

    /**
     * Decide what we should do next! Ticked once every HUNTER_TASK_INTERVAL Ticks
     *
     * @return the next IAIState.
     */
    protected IAIState decide()
    {
        reduceAttackDelay(HUNTER_TASK_INTERVAL * getTickRate());

        final ILocation rallyLocation = buildingHunter.getRallyLocation();

        if (regularActionTimer++ > ACTION_INCREASE_INTERVAL)
        {
            incrementActionsDone();
            regularActionTimer = 0;
        }

        if (rallyLocation != null)
        {
            worker.addPotionEffect(new EffectInstance(GLOW_EFFECT, GLOW_EFFECT_DURATION, GLOW_EFFECT_MULTIPLIER, false, false));
        }
        else
        {
            worker.removeActivePotionEffect(GLOW_EFFECT);
        }

        if (rallyLocation != null && rallyLocation.isReachableFromLocation(worker.getLocation()))
        {
            return rally(rallyLocation);
        }

        switch (buildingHunter.getTask())
        {
            case PATROL:
                return patrol();
            case GUARD:
                return guard();
            default:
                return PREPARING;
        }
    }

    /**
     * Checks if the current targets is still valid, if not searches a new target. Adds experience if the current target died.
     *
     * @return true if we found a target, false if no target.
     */
    protected boolean checkForTarget()
    {
        if (target != null && !target.isAlive())
        {
            incrementActionsDoneAndDecSaturation();
            worker.getCitizenExperienceHandler().addExperience(EXP_PER_MOB_DEATH);
        }

        // Check Current target
        if (isEntityValidTarget(target))
        {
            if (target != worker.getRevengeTarget() && isEntityValidTargetAndCanbeSeen(worker.getRevengeTarget())
                    && worker.getDistanceSq(worker.getRevengeTarget()) < worker.getDistanceSq(target) - 15)
            {
                target = worker.getRevengeTarget();
                onTargetChange();
            }

            // Check sight
            if (!worker.canEntityBeSeen(target))
            {
                lastSeen += HUNTER_TASK_INTERVAL;
            }
            else
            {
                lastSeen = 0;
            }

            if (lastSeen > STOP_PERSECUTION_AFTER)
            {
                resetTarget();
                return false;
            }

            // Move into range
            if (!isInAttackDistance(new BlockPos(target.getPositionVec())))
            {
                if (worker.getNavigator().noPath())
                {
                    moveInAttackPosition();
                }
            }

            return true;
        }
        else
        {
            resetTarget();
        }

        // Check the revenge target
        if (isEntityValidTargetAndCanbeSeen(worker.getRevengeTarget()))
        {
            target = worker.getRevengeTarget();
            onTargetChange();
            return true;
        }

        return target != null;
    }

    /**
     * Actions on changing to a new target entity
     */
    protected void onTargetChange()
    {
        for (final ICitizenData citizen : getOwnBuilding().getAssignedCitizen())
        {
            if (citizen.getEntity().isPresent() && citizen.getEntity().get().getRevengeTarget() == null)
            {
                citizen.getEntity().get().setRevengeTarget(target);
            }
        }

        if (target instanceof AbstractEntityMinecoloniesMob)
        {
            for (final Map.Entry<BlockPos, IBuilding> entry : worker.getCitizenColonyHandler().getColony().getBuildingManager().getBuildings().entrySet())
            {
                if (entry.getValue() instanceof BuildingHunter &&
                        worker.getPosition().distanceSq(entry.getKey()) < PATROL_DEVIATION_RAID_POINT)
                {
                    final BuildingHunter building = (BuildingHunter) entry.getValue();
                    building.setTempNextPatrolPoint(target.getPosition());
                }
            }
        }
    }

    /**
     * Returns whether the entity is a valid target and is visisble.
     *
     * @param entity entity to check
     * @return boolean
     */
    public boolean isEntityValidTargetAndCanbeSeen(final LivingEntity entity)
    {
        return isEntityValidTarget(entity) && worker.canEntityBeSeen(entity);
    }

    /**
     * Checks whether the given entity is a valid target to attack.
     *
     * @param entity Entity to check
     * @return true if should attack
     */
    public boolean isEntityValidTarget(final LivingEntity entity)
    {
        if (entity == null || !entity.isAlive() || !isWithinPersecutionDistance(new BlockPos(entity.getPositionVec())))
        {
            return false;
        }

        if (entity == worker.getRevengeTarget())
        {
            return true;
        }

        if (entity instanceof IMob)
        {
            final MobEntryView entry = buildingHunter.getMobsToAttack().get(entity.getType().getRegistryName());
            if (entry != null && entry.shouldAttack())
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Resets the current target and removes it from all saved targets.
     */
    public void resetTarget()
    {
        if (target == null)
        {
            return;
        }

        if (worker.getLastAttackedEntity() == target)
        {
            worker.setLastAttackedEntity(null);
        }

        if (worker.getRevengeTarget() == target)
        {
            worker.setRevengeTarget(null);
        }

        target = null;
    }

    /**
     * Execute pre attack checks to check if worker can attack enemy.
     *
     * @return the next aiState to go to.
     */
    public IAIState preAttackChecks()
    {
        if (!hasMainWeapon())
        {
            resetTarget();
            return START_WORKING;
        }

        if (buildingHunter.shallRetrieveOnLowHealth() && worker.getHealth() < ((int) worker.getMaxHealth() * 0.2D))
        {
            final UnlockAbilityResearchEffect effect =
                    worker.getCitizenColonyHandler().getColony().getResearchManager().getResearchEffects().getEffect(RETREAT, UnlockAbilityResearchEffect.class);
            if (effect != null)
            {
                resetTarget();
                return HUNTER_REGEN;
            }
        }

        if (!checkForTarget())
        {
            return HUNTER_DECIDE;
        }

        wearWeapon();

        return getState();
    }

    /**
     * Get a target for the hunter. First check if we're under attack by anything and switch target if necessary.
     *
     * @return The next IAIState to go to.
     */
    protected LivingEntity searchNearbyTarget()
    {
        final IColony colony = worker.getCitizenColonyHandler().getColony();
        if (colony == null)
        {
            resetTarget();
            return null;
        }

        final List<LivingEntity> entities = world.getEntitiesWithinAABB(LivingEntity.class, getSearchArea());

        int closest = Integer.MAX_VALUE;
        LivingEntity targetEntity = null;

        for (final LivingEntity entity : entities)
        {
            if (!entity.isAlive())
            {
                continue;
            }

            // Found a sleeping hunter nearby
            if (entity instanceof EntityCitizen)
            {
                final EntityCitizen citizen = (EntityCitizen) entity;
                if (citizen.getCitizenJobHandler().getColonyJob() instanceof JobHunter && ((JobHunter) citizen.getCitizenJobHandler().getColonyJob()).isAsleep()
                        && worker.canEntityBeSeen(entity))
                {
                    sleepingHunter = new WeakReference<>(citizen);
                    wakeTimer = 0;
                    registerTarget(new AIOneTimeEventTarget(HUNTER_WAKE));
                    return null;
                }
            }

            if (isEntityValidTarget(entity) && worker.canEntityBeSeen(entity))
            {
                // Find closest
                final int tempDistance = (int) BlockPosUtil.getDistanceSquared(worker.getPosition(), new BlockPos(entity.getPositionVec()));
                if (tempDistance < closest)
                {
                    closest = tempDistance;
                    targetEntity = entity;
                }
            }
        }

        target = targetEntity;
        onTargetChange();
        return targetEntity;
    }

    /**
     * Check if a position is within regular attack distance.
     *
     * @param position the position to check.
     * @return true if so.
     */
    public boolean isInAttackDistance(final BlockPos position)
    {
        return BlockPosUtil.getDistanceSquared2D(worker.getPosition(), position) <= getAttackRange() * getAttackRange();
    }

    /**
     * Reduces the attack delay by the given value
     *
     * @param value amount to reduce by
     */
    public void reduceAttackDelay(final int value)
    {
        if (currentAttackDelay > 0)
        {
            currentAttackDelay -= value;
        }
    }

    /**
     * Check if a position is within the allowed persecution distance.
     *
     * @param entityPos the position to check.
     * @return true if so.
     */
    private boolean isWithinPersecutionDistance(final BlockPos entityPos)
    {
        return BlockPosUtil.getDistanceSquared(getTaskReferencePoint(), entityPos) <= Math.pow(getPersecutionDistance() + getAttackRange(), 2);
    }

    /**
     * Get the reference point from which the hunter comes.
     *
     * @return the position depending ont he task.
     */
    private BlockPos getTaskReferencePoint()
    {
        final ILocation location = buildingHunter.getRallyLocation();
        if (location != null)
        {
            return buildingHunter.getRallyLocation().getInDimensionLocation();
        }
        switch (buildingHunter.getTask())
        {
            case PATROL:
                return currentPatrolPoint != null ? currentPatrolPoint : worker.getPosition();
            default:
                return buildingHunter.getHunterPos();
        }
    }

    /**
     * Returns the block distance at which a hunter should chase his target
     *
     * @return the block distance at which a hunter should chase his target
     */
    private int getPersecutionDistance()
    {
        return MAX_FOLLOW_DERIVATION;
    }

    /**
     * Get the {@link AxisAlignedBB} we're searching for targets in.
     *
     * @return the {@link AxisAlignedBB}
     */
    private AxisAlignedBB getSearchArea()
    {
        final BuildingHunter building = getOwnBuilding();
        final int buildingBonus = building.getBonusVision() + Math.max(TARGET_RANGE_ATTACK_RANGE_BONUS, getAttackRange());

        final Direction randomDirection = Direction.byIndex(worker.getRandom().nextInt(4) + 2);

        final double x1 = worker.getPosition().getX() + (Math.max(buildingBonus * randomDirection.getXOffset() + DEFAULT_VISION, DEFAULT_VISION));
        final double x2 = worker.getPosition().getX() + (Math.min(buildingBonus * randomDirection.getXOffset() - DEFAULT_VISION, -DEFAULT_VISION));
        final double y1 = worker.getPosition().getY() + (Y_VISION >> 1);
        final double y2 = worker.getPosition().getY() - (Y_VISION << 1);
        final double z1 = worker.getPosition().getZ() + (Math.max(buildingBonus * randomDirection.getZOffset() + DEFAULT_VISION, DEFAULT_VISION));
        final double z2 = worker.getPosition().getZ() + (Math.min(buildingBonus * randomDirection.getZOffset() - DEFAULT_VISION, -DEFAULT_VISION));

        return new AxisAlignedBB(x1, y1, z1, x2, y2, z2);
    }

    @Override
    public boolean canBeInterrupted()
    {
        if (fighttimer > 0 || getState() == HUNTER_RALLY || target != null || buildingHunter.getRallyLocation() != null)
        {
            return false;
        }
        return super.canBeInterrupted();
    }

    public IAIState getAttackState()
    {
        strafingTime = 0;
        tooCloseNumTicks = 0;
        timeAtSameSpot = 0;
        timeCanSee = 0;
        fleeing = false;
        movingToTarget = false;
        worker.getCitizenData().setVisibleStatus(ARCHER_COMBAT);

        return HUNTER_ATTACK_RANGED;
    }

    /**
     * Getter for the attackrange
     */
    @Override
    protected int getAttackRange()
    {
        return getRealAttackRange();
    }

    /**
     * Calculates the actual attack range
     *
     * @return The attack range
     */
    private int getRealAttackRange()
    {
        int attackDist = BASE_DISTANCE_FOR_RANGED_ATTACK;
        // + 1 Blockrange per building level for a total of +5 from building level
        if (buildingHunter != null)
        {
            attackDist += buildingHunter.getBuildingLevel();
        }
        // ~ +1 each three levels for a total of +10 from hunter level
        if (worker.getCitizenData() != null)
        {
            attackDist += (worker.getCitizenData().getCitizenSkillHandler().getLevel(Skill.Adaptability) / 50.0f) * 15;
        }

        attackDist = Math.min(attackDist, MAX_DISTANCE_FOR_RANGED_ATTACK);

        if (target != null)
        {
            attackDist += worker.getPosY() - target.getPosY();
        }

        if (buildingHunter.getTask() == HunterTask.GUARD)
        {
            attackDist += HUNTER_BONUS_RANGE;
        }

        return attackDist;
    }

    public boolean hasMainWeapon()
    {
        return !checkForToolOrWeapon(ToolType.BOW);
    }

    public void wearWeapon()
    {
        final int bowSlot = InventoryUtils.getFirstSlotOfItemHandlerContainingTool(getInventory(), ToolType.BOW, 0, buildingHunter.getMaxToolLevel());
        if (bowSlot != -1)
        {
            worker.getCitizenItemHandler().setHeldItem(Hand.MAIN_HAND, bowSlot);
        }
    }

    /**
     * The ranged attack modus. Ticked every 10 Ticks.
     *
     * @return the next state to go to.
     */
    protected IAIState attackRanged()
    {
        final IAIState state = preAttackChecks();
        if (state != getState())
        {
            worker.getNavigator().clearPath();
            worker.getMoveHelper().strafe(0, 0);
            setDelay(STANDARD_DELAY);
            worker.getCitizenData().setVisibleStatus(VisibleCitizenStatus.WORKING);
            return state;
        }

        fighttimer = COMBAT_TIME;
        final double sqDistanceToEntity = BlockPosUtil.getDistanceSquared2D(worker.getPosition(), new BlockPos(target.getPositionVec()));
        final boolean canSee = worker.getEntitySenses().canSee(target);
        final double sqAttackRange = getRealAttackRange() * getRealAttackRange();

        if (canSee)
        {
            timeCanSee++;
        }
        else
        {
            timeCanSee--;
        }

        if (lastDistance == sqDistanceToEntity)
        {
            timeAtSameSpot++;
        }
        else
        {
            timeAtSameSpot = 0;
        }

        // Stuck
        if (sqDistanceToEntity > sqAttackRange && timeAtSameSpot > 8 || (!canSee && timeAtSameSpot > 8))
        {
            worker.getNavigator().clearPath();
            return DECIDE;
        }
        // Move inside attackrange
        else if (sqDistanceToEntity > sqAttackRange || !canSee)
        {
            if (worker.getNavigator().noPath())
            {
                moveInAttackPosition();
            }
            worker.getMoveHelper().strafe(0, 0);
            movingToTarget = true;
            strafingTime = -1;
        }
        // Clear chasing when in range
        else if (movingToTarget && sqDistanceToEntity < sqAttackRange)
        {
            worker.getNavigator().clearPath();
            movingToTarget = false;
            strafingTime = -1;
        }

        // Reset Fleeing status
        if (fleeing && !fleePath.isComputing())
        {
            fleeing = false;
            tooCloseNumTicks = 0;
        }

        // Check if the target is too close
        if (sqDistanceToEntity < RANGED_FLEE_SQDIST)
        {
            tooCloseNumTicks++;
            strafingTime = -1;

            // Fleeing
            if (!fleeing && !movingToTarget && sqDistanceToEntity < RANGED_FLEE_SQDIST && tooCloseNumTicks > 3)
            {
                fleePath = worker.getNavigator().moveAwayFromLivingEntity(target, getRealAttackRange() / 2.0, getCombatMovementSpeed());
                fleeing = true;
                worker.getMoveHelper().strafe(0, (float) strafingClockwise * 0.2f);
                strafingClockwise *= -1;
            }
        }
        else
        {
            tooCloseNumTicks = 0;
        }

        // Combat movement for hunter not on huntering block task
        if (buildingHunter.getTask() != HunterTask.GUARD)
        {
            // Toggle strafing direction randomly if strafing
            if (strafingTime >= TIME_STRAFING_BEFORE_SWITCHING_DIRECTIONS)
            {
                if ((double) worker.getRNG().nextFloat() < SWITCH_STRAFING_DIRECTION)
                {
                    strafingClockwise *= -1;
                }
                this.strafingTime = 0;
            }

            // Strafe when we're close enough
            if (sqDistanceToEntity < (sqAttackRange / 2.0) && tooCloseNumTicks < 1)
            {
                strafingTime++;
            }
            else if (sqDistanceToEntity > (sqAttackRange / 2.0) + 5)
            {
                strafingTime = -1;
            }

            // Strafe or flee, when not already fleeing or moving in
            if ((strafingTime > -1) && !fleeing && !movingToTarget)
            {
                worker.getMoveHelper().strafe(0, (float) (getCombatMovementSpeed() * strafingClockwise * STRAFING_SPEED));
                worker.faceEntity(target, (float) TURN_AROUND, (float) TURN_AROUND);
            }
            else
            {
                worker.faceEntity(target, (float) TURN_AROUND, (float) TURN_AROUND);
            }
        }

        if (worker.isHandActive())
        {
            if (!canSee && timeCanSee < -6)
            {
                worker.resetActiveHand();
            }
            else if (canSee && sqDistanceToEntity <= sqAttackRange)
            {
                worker.faceEntity(target, (float) TURN_AROUND, (float) TURN_AROUND);
                worker.swingArm(Hand.MAIN_HAND);

                int amountOfArrows = 1;
                final MultiplierModifierResearchEffect
                        effect = worker.getCitizenColonyHandler().getColony().getResearchManager().getResearchEffects().getEffect(DOUBLE_ARROWS, MultiplierModifierResearchEffect.class);
                if (effect != null)
                {
                    if (worker.getRandom().nextDouble() < effect.getEffect())
                    {
                        amountOfArrows++;
                    }
                }

                double extraDamage = 0.0D;
                final AdditionModifierResearchEffect
                        dmgEffect = worker.getCitizenColonyHandler().getColony().getResearchManager().getResearchEffects().getEffect(ARCHER_DAMAGE, AdditionModifierResearchEffect.class);
                if (dmgEffect != null)
                {
                    extraDamage += dmgEffect.getEffect();
                }

                for (int i = 0; i < amountOfArrows; i++)
                {
                    final ArrowEntity arrow = ModEntities.MC_NORMAL_ARROW.create(world);
                    arrow.setShooter(worker);

                    final UnlockAbilityResearchEffect arrowPierceEffect =
                            worker.getCitizenColonyHandler().getColony().getResearchManager().getResearchEffects().getEffect(ARROW_PIERCE, UnlockAbilityResearchEffect.class);
                    if (arrowPierceEffect != null && arrowPierceEffect.getEffect())
                    {
                        arrow.setPierceLevel((byte) 2);
                    }

                    arrow.setPosition(worker.getPosX(), worker.getPosY() + 1, worker.getPosZ());
                    final double xVector = target.getPosX() - worker.getPosX();
                    final double yVector = target.getBoundingBox().minY + target.getHeight() / getAimHeight() - arrow.getPosY();
                    final double zVector = target.getPosZ() - worker.getPosZ();

                    final double distance = (double) MathHelper.sqrt(xVector * xVector + zVector * zVector);
                    double damage = getRangedAttackDamage() + extraDamage;

                    final UnlockAbilityResearchEffect arrowItemEffect =
                            worker.getCitizenColonyHandler().getColony().getResearchManager().getResearchEffects().getEffect(ARROW_ITEMS, UnlockAbilityResearchEffect.class);

                    if (arrowItemEffect != null && arrowItemEffect.getEffect())
                    {
                        // Extra damage from arrows
                        int slot = InventoryUtils.findFirstSlotInItemHandlerWith(worker.getInventoryCitizen(), item -> item.getItem() instanceof ArrowItem);
                        if (slot != -1)
                        {
                            if (!ItemStackUtils.isEmpty(worker.getInventoryCitizen().extractItem(slot, 1, false)))
                            {
                                damage += ARROW_EXTRA_DAMAGE;
                            }
                        }
                    }


                    // Add bow enchant effects: Knocback and fire
                    final ItemStack bow = worker.getHeldItem(Hand.MAIN_HAND);

                    if (EnchantmentHelper.getEnchantmentLevel(Enchantments.FLAME, bow) > 0)
                    {
                        arrow.setFire(100);
                    }
                    final int k = EnchantmentHelper.getEnchantmentLevel(Enchantments.PUNCH, bow);
                    if (k > 0)
                    {
                        arrow.setKnockbackStrength(k);
                    }

                    final double chance = HIT_CHANCE_DIVIDER / (worker.getCitizenData().getCitizenSkillHandler().getLevel(Skill.Adaptability) + 1);

                    arrow.shoot(xVector, yVector + distance * RANGED_AIM_SLIGHTLY_HIGHER_MULTIPLIER, zVector, RANGED_VELOCITY, (float) chance);

                    if (worker.getHealth() <= worker.getMaxHealth() * 0.2D)
                    {
                        damage *= 2;
                    }

                    arrow.setDamage(damage);
                    worker.playSound(SoundEvents.ENTITY_SKELETON_SHOOT, (float) BASIC_VOLUME, (float) SoundUtils.getRandomPitch(worker.getRandom()));
                    worker.world.addEntity(arrow);
                }

                final double xDiff = target.getPosX() - worker.getPosX();
                final double zDiff = target.getPosZ() - worker.getPosZ();
                final double goToX = xDiff > 0 ? MOVE_MINIMAL : -MOVE_MINIMAL;
                final double goToZ = zDiff > 0 ? MOVE_MINIMAL : -MOVE_MINIMAL;
                worker.move(MoverType.SELF, new Vector3d(goToX, 0, goToZ));

                timeCanSee = 0;
                target.setRevengeTarget(worker);
                currentAttackDelay = getAttackDelay();
                worker.getCitizenItemHandler().damageItemInHand(Hand.MAIN_HAND, 1);
                worker.resetActiveHand();
                worker.decreaseSaturationForContinuousAction();
            }
            else
            {
                /*
                 * It is possible the object is higher than hunter and hunter can't get there.
                 * Hunter will try to back up to get some distance to be able to shoot target.
                 */
                if (target.getPosY() > worker.getPosY() + Y_VISION + Y_VISION)
                {
                    fleePath = worker.getNavigator().moveAwayFromLivingEntity(target, 10, getCombatMovementSpeed());
                    fleeing = true;
                    worker.getMoveHelper().strafe(0, 0);
                }
            }
        }
        else
        {
            reduceAttackDelay(10);
            if (currentAttackDelay <= 0)
            {
                worker.setActiveHand(Hand.MAIN_HAND);
            }
        }
        lastDistance = sqDistanceToEntity;

        return HUNTER_ATTACK_RANGED;
    }

    @Override
    protected void atBuildingActions()
    {
        super.atBuildingActions();


        final UnlockAbilityResearchEffect arrowItemEffect =
                worker.getCitizenColonyHandler().getColony().getResearchManager().getResearchEffects().getEffect(ARROW_ITEMS, UnlockAbilityResearchEffect.class);

        if (arrowItemEffect != null && arrowItemEffect.getEffect())
        {
            // Pickup arrows and request arrows
            InventoryUtils.transferXOfFirstSlotInProviderWithIntoNextFreeSlotInItemHandler(getOwnBuilding(),
                    item -> item.getItem() instanceof ArrowItem,
                    64,
                    worker.getInventoryCitizen());

            if (InventoryUtils.getItemCountInItemHandler(worker.getInventoryCitizen(), item -> item.getItem() instanceof ArrowItem) < 16)
            {
                checkIfRequestForItemExistOrCreateAsynch(new ItemStack(Items.ARROW), 64, 16);
            }
        }
    }

    /**
     * Gets the reload time for a physical hunter attack.
     *
     * @return the reload time, min PHYSICAL_ATTACK_DELAY_MIN Ticks
     */
    @Override
    protected int getAttackDelay()
    {
        if (worker.getCitizenData() != null)
        {
            final int attackDelay = RANGED_ATTACK_DELAY_BASE - (worker.getCitizenData().getCitizenSkillHandler().getLevel(Skill.Adaptability) / 2);
            return attackDelay < PHYSICAL_ATTACK_DELAY_MIN * 2 ? PHYSICAL_ATTACK_DELAY_MIN * 2 : attackDelay;
        }
        return RANGED_ATTACK_DELAY_BASE;
    }

    /**
     * Calculates the ranged attack damage
     *
     * @return the attack damage
     */
    private double getRangedAttackDamage()
    {
        if (worker.getCitizenData() != null)
        {
            int enchantDmg = 0;
            if (MineColonies.getConfig().getServer().rangerEnchants.get())
            {
                final ItemStack heldItem = worker.getHeldItem(Hand.MAIN_HAND);
                // Normalize to +1 dmg
                enchantDmg += EnchantmentHelper.getModifierForCreature(heldItem, target.getCreatureAttribute()) / 2.5;
                enchantDmg += EnchantmentHelper.getEnchantmentLevel(Enchantments.POWER, heldItem);
            }

            return (HUNTER_BASE_DMG + getLevelDamage() + enchantDmg) * MineColonies.getConfig().getServer().hunterDamageMult.get();
        }
        return HUNTER_BASE_DMG * MineColonies.getConfig().getServer().hunterDamageMult.get();
    }

    /**
     * Calculates the dmg increase per level
     *
     * @return the level damage.
     */
    public int getLevelDamage()
    {
        if (worker.getCitizenData() == null)
        {
            return 0;
        }
        // Level scaling damage, +1 every 5 levels
        return (worker.getCitizenData().getCitizenSkillHandler().getLevel(Skill.Agility) / 50) * 10;
    }

    @Override
    protected double getCombatSpeedBonus()
    {
        return worker.getCitizenData().getCitizenSkillHandler().getLevel(Skill.Agility) * SPEED_LEVEL_BONUS;
    }

    /**
     * Gets the aim height for ranged hunter.
     *
     * @return the aim height. Suppression because the method already explains the value.
     */
    @SuppressWarnings({"squid:S3400", "squid:S109"})
    private double getAimHeight()
    {
        return 3.0D;
    }

    public void moveInAttackPosition()
    {
        if (buildingHunter.getTask() == HunterTask.GUARD)
        {
            ((MinecoloniesAdvancedPathNavigate) worker.getNavigator()).setPathJob(new PathJobCanSee(worker, target, world, buildingHunter.getHunterPos(), 20),
                    null,
                    getCombatMovementSpeed());
            return;
        }

        worker.getNavigator()
                .tryMoveToBlockPos(worker.getPosition().offset(BlockPosUtil.getXZFacing(new BlockPos(target.getPositionVec()), worker.getPosition()).getOpposite(), 8), getCombatMovementSpeed());
    }

    @Override
    public Class<BuildingHunter> getExpectedBuildingClass()
    {
        return BuildingHunter.class;
    }
}
