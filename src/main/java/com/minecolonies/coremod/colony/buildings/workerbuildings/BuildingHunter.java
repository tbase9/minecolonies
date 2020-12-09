package com.minecolonies.coremod.colony.buildings.workerbuildings;

import com.ldtteam.blockout.views.Window;
import com.ldtteam.structurize.util.LanguageHandler;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.*;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.colony.buildings.views.MobEntryView;
import com.minecolonies.api.colony.jobs.IJob;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.entity.ai.citizen.hunter.HunterTask;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.Log;
import com.minecolonies.api.util.constant.ToolType;
import com.minecolonies.coremod.MineColonies;
import com.minecolonies.coremod.Network;
import com.minecolonies.coremod.client.gui.WindowHutHunter;
import com.minecolonies.coremod.colony.buildings.AbstractBuildingWorker;
import com.minecolonies.coremod.colony.jobs.JobBeekeeper;
import com.minecolonies.coremod.colony.jobs.JobHunter;
import com.minecolonies.coremod.colony.requestsystem.locations.EntityLocation;
import com.minecolonies.coremod.entity.ai.citizen.hunter.EntityAIHunter;
import com.minecolonies.coremod.entity.pathfinding.Pathfinding;
import com.minecolonies.coremod.entity.pathfinding.pathjobs.PathJobRandomPos;
import com.minecolonies.coremod.items.ItemBannerRallyHunter;
import com.minecolonies.coremod.network.messages.client.colony.building.hunter.HunterMobAttackListMessage;
import com.minecolonies.coremod.research.UnlockAbilityResearchEffect;
import com.minecolonies.coremod.util.AttributeModifierUtils;
import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ArrowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.network.PacketBuffer;
import net.minecraft.pathfinding.Path;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Tuple;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.Future;

import static com.minecolonies.api.research.util.ResearchConstants.ARROW_ITEMS;
import static com.minecolonies.api.util.constant.CitizenConstants.*;
import static com.minecolonies.api.util.constant.ToolLevelConstants.TOOL_LEVEL_WOOD_OR_GOLD;

/**
 * Guard Tower building.
 *
 * @author Asherslab
 */
@SuppressWarnings("squid:MaximumInheritanceDepth")
public class BuildingHunter extends AbstractBuildingWorker
{
    /**
     * Our constants. The Schematic names, Defence bonus, and Offence bonus.
     */
    private static final String SCHEMATIC_NAME        = "guardtower";
    private static final int    DEFENCE_BONUS         = 5;
    private static final int    OFFENCE_BONUS         = 0;
    private static final int    MAX_LEVEL             = 5;
    private static final int    BONUS_HP_SINGLE_GUARD = 20;

    ////// --------------------------- NBTConstants --------------------------- \\\\\\
    private static final String NBT_TASK           = "TASK";
    private static final String NBT_ASSIGN         = "assign";
    private static final String NBT_RETRIEVE       = "retrieve";
    private static final String NBT_PATROL         = "patrol";
    private static final String NBT_PATROL_TARGETS = "patrol targets";
    private static final String NBT_TARGET         = "target";
    private static final String NBT_Hunter         = "Hunter";
    private static final String NBT_MOBS           = "mobs";
    private static final String NBT_MOB_VIEW       = "mobview";

    ////// --------------------------- NBTConstants --------------------------- \\\\\\

    ////// --------------------------- HunterJob Enum --------------------------- \\\\\\

    /**
     * Description of the job executed in the hut.
     */
    private static final String HUNTER = "hunter";

    /**
     * Base patrol range
     */
    private static final int PATROL_BASE_DIST = 50;

    /**
     * The Bonus Health for each building level
     */
    private static final int BONUS_HEALTH_PER_LEVEL = 2;

    /**
     * Vision range per building level.
     */
    private static final int VISION_RANGE_PER_LEVEL = 3;

    /**
     * Base Vision range per building level.
     */
    private static final int BASE_VISION_RANGE = 15;

    /**
     * Whether the HunterType will be assigned manually.
     */
    private boolean assignManually = false;

    /**
     * Whether to retrieve the Hunter on low health.
     */
    private boolean retrieveOnLowHealth = true;

    /**
     * Whether to patrol manually or not.
     */
    protected boolean patrolManually = false;

    /**
     * The task of the Hunter, following the {@link HunterTask} enum.
     */
    private HunterTask task = HunterTask.PATROL;

    /**
     * The position at which the Hunter should Hunter at.
     */
    private BlockPos HunterPos = this.getID();

    /**
     * The list of manual patrol targets.
     */
    protected List<BlockPos> patrolTargets = new ArrayList<>();

    /**
     * Hashmap of mobs we may or may not attack.
     */
    private Map<ResourceLocation, MobEntryView> mobsToAttack = new HashMap<>();

    /**
     * The location the Hunter has been set to rally to.
     */
    private ILocation rallyLocation;

    /**
     * A temporary next patrol point, which gets consumed and used once
     */
    protected BlockPos tempNextPatrolPoint = null;

    /**
     * Pathing future for the next patrol target.
     */
    private Future<Path> pathingFuture;

    /**
     * Worker gets this distance times building level away from his/her hut to patrol.
     */
    private int PATROL_DISTANCE = 30;

    /**
     * The abstract constructor of the building.
     *
     * @param c the colony
     * @param l the position
     */
    public BuildingHunter(@NotNull final IColony c, final BlockPos l)
    {
        super(c, l);

        keepX.put(itemStack -> ItemStackUtils.hasToolLevel(itemStack, ToolType.BOW, TOOL_LEVEL_WOOD_OR_GOLD, getMaxToolLevel()), new Tuple<>(1, true));
        keepX.put(itemStack -> !ItemStackUtils.isEmpty(itemStack) && ItemStackUtils.doesItemServeAsWeapon(itemStack), new Tuple<>(1, true));

//        keepX.put(itemStack -> !ItemStackUtils.isEmpty(itemStack)
//                && itemStack.getItem() instanceof ArmorItem
//                && ((ArmorItem) itemStack.getItem()).getEquipmentSlot() == EquipmentSlotType.CHEST, new Tuple<>(1, true));
//        keepX.put(itemStack -> !ItemStackUtils.isEmpty(itemStack)
//                && itemStack.getItem() instanceof ArmorItem
//                && ((ArmorItem) itemStack.getItem()).getEquipmentSlot() == EquipmentSlotType.HEAD, new Tuple<>(1, true));
//        keepX.put(itemStack -> !ItemStackUtils.isEmpty(itemStack)
//                && itemStack.getItem() instanceof ArmorItem
//                && ((ArmorItem) itemStack.getItem()).getEquipmentSlot() == EquipmentSlotType.LEGS, new Tuple<>(1, true));
//        keepX.put(itemStack -> !ItemStackUtils.isEmpty(itemStack)
//                && itemStack.getItem() instanceof ArmorItem
//                && ((ArmorItem) itemStack.getItem()).getEquipmentSlot() == EquipmentSlotType.FEET, new Tuple<>(1, true));

        keepX.put(itemStack -> {
            if (ItemStackUtils.isEmpty(itemStack) || !(itemStack.getItem() instanceof ArrowItem))
            {
                return false;
            }

            final UnlockAbilityResearchEffect arrowItemEffect =
                    getColony().getResearchManager().getResearchEffects().getEffect(ARROW_ITEMS, UnlockAbilityResearchEffect.class);

            return arrowItemEffect != null && arrowItemEffect.getEffect();
        }, new Tuple<>(128, true));

        calculateMobs();
    }

    /**
     * The abstract method which creates a job for the building.
     *
     * @param citizen the citizen to take the job.
     * @return the Job.
     */
    @NotNull
    @Override
    public IJob createJob(final ICitizenData citizen)
    {
        return new JobBeekeeper(citizen);
    }

    /**
     * The abstract method which returns the name of the job.
     *
     * @return the job name.
     */
    @NotNull
    @Override
    public String getJobName()
    {
        return HUNTER;
    }

    /**
     * Primary skill getter.
     *
     * @return the primary skill.
     */
    @NotNull
    @Override
    public Skill getPrimarySkill()
    {
        return Skill.Dexterity;
    }

    /**
     * Secondary skill getter.
     *
     * @return the secondary skill.
     */
    @NotNull
    @Override
    public Skill getSecondarySkill()
    {
        return Skill.Adaptability;
    }

    //// ---- NBT Overrides ---- \\\\

    /**
     * We use this to set possible health multipliers and give achievements.
     *
     * @param newLevel The new level.
     */
    @Override
    public void onUpgradeComplete(final int newLevel)
    {
        if (getAssignedEntities() != null)
        {
            for (final Optional<AbstractEntityCitizen> optCitizen : getAssignedEntities())
            {
                if (optCitizen.isPresent())
                {
                    final AttributeModifier healthModBuildingHP = new AttributeModifier(HUNTER_HEALTH_MOD_BUILDING_NAME, getBonusHealth(), AttributeModifier.Operation.ADDITION);
                    AttributeModifierUtils.addHealthModifier(optCitizen.get(), healthModBuildingHP);
                }
            }
        }

        super.onUpgradeComplete(newLevel);
        colony.getBuildingManager().hunterBuildingChangedAt(this, newLevel);
    }

    @Override
    public boolean assignCitizen(final ICitizenData citizen)
    {
        // Only change HP values if assign successful
        if (super.assignCitizen(citizen) && citizen != null)
        {
            final Optional<AbstractEntityCitizen> optCitizen = citizen.getEntity();
            if (optCitizen.isPresent())
            {
                final AbstractEntityCitizen citizenEntity = optCitizen.get();
                AttributeModifierUtils.addHealthModifier(citizenEntity,
                        new AttributeModifier(HUNTER_HEALTH_MOD_BUILDING_NAME, getBonusHealth(), AttributeModifier.Operation.ADDITION));
                AttributeModifierUtils.addHealthModifier(citizenEntity,
                        new AttributeModifier(HUNTER_HEALTH_MOD_CONFIG_NAME,
                                MineColonies.getConfig().getServer().hunterHealthMult.get() - 1.0,
                                AttributeModifier.Operation.MULTIPLY_TOTAL));
            }

            // Set new home, since Hunters are housed at their workerbuilding.
            final IBuilding building = citizen.getHomeBuilding();
            if (building != null && !building.getID().equals(this.getID()))
            {
                building.removeCitizen(citizen);
            }
            citizen.setHomeBuilding(this);
            // Start timeout to not be stuck with an old patrol target
            patrolTimer = 5;

            return true;
        }
        return false;
    }

    //// ---- NBT Overrides ---- \\\\

    //// ---- Overrides ---- \\\\

    @Override
    public void deserializeNBT(final CompoundNBT compound)
    {
        super.deserializeNBT(compound);

        task = HunterTask.values()[compound.getInt(NBT_TASK)];
        assignManually = compound.getBoolean(NBT_ASSIGN);
        retrieveOnLowHealth = compound.getBoolean(NBT_RETRIEVE);
        patrolManually = compound.getBoolean(NBT_PATROL);

        final ListNBT wayPointTagList = compound.getList(NBT_PATROL_TARGETS, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < wayPointTagList.size(); ++i)
        {
            final CompoundNBT blockAtPos = wayPointTagList.getCompound(i);
            final BlockPos pos = BlockPosUtil.read(blockAtPos, NBT_TARGET);
            patrolTargets.add(pos);
        }

        final ListNBT mobsTagList = compound.getList(NBT_MOBS, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < mobsTagList.size(); i++)
        {
            final CompoundNBT mobCompound = mobsTagList.getCompound(i);
            final MobEntryView mobEntry = MobEntryView.read(mobCompound, NBT_MOB_VIEW);
            if (mobEntry.getEntityEntry() != null)
            {
                mobsToAttack.put(mobEntry.getEntityEntry().getRegistryName(), mobEntry);
            }
        }

        HunterPos = NBTUtil.readBlockPos(compound.getCompound(NBT_Hunter));
    }

    @Override
    public CompoundNBT serializeNBT()
    {
        final CompoundNBT compound = super.serializeNBT();

        compound.putInt(NBT_TASK, task.ordinal());
        compound.putBoolean(NBT_ASSIGN, assignManually);
        compound.putBoolean(NBT_RETRIEVE, retrieveOnLowHealth);
        compound.putBoolean(NBT_PATROL, patrolManually);

        @NotNull final ListNBT wayPointTagList = new ListNBT();
        for (@NotNull final BlockPos pos : patrolTargets)
        {
            @NotNull final CompoundNBT wayPointCompound = new CompoundNBT();
            BlockPosUtil.write(wayPointCompound, NBT_TARGET, pos);

            wayPointTagList.add(wayPointCompound);
        }
        compound.put(NBT_PATROL_TARGETS, wayPointTagList);

        @NotNull final ListNBT mobsTagList = new ListNBT();
        for (@NotNull final MobEntryView entry : mobsToAttack.values())
        {
            @NotNull final CompoundNBT mobCompound = new CompoundNBT();
            MobEntryView.write(mobCompound, NBT_MOB_VIEW, entry);
            mobsTagList.add(mobCompound);
        }
        compound.put(NBT_MOBS, mobsTagList);

        compound.put(NBT_Hunter, NBTUtil.writeBlockPos(HunterPos));

        return compound;
    }

    @Override
    public void removeCitizen(final ICitizenData citizen)
    {
        if (citizen != null)
        {
            final Optional<AbstractEntityCitizen> optCitizen = citizen.getEntity();
            if (optCitizen.isPresent())
            {
                AttributeModifierUtils.removeAllHealthModifiers(optCitizen.get());
                optCitizen.get().setItemStackToSlot(EquipmentSlotType.CHEST, ItemStackUtils.EMPTY);
                optCitizen.get().setItemStackToSlot(EquipmentSlotType.FEET, ItemStackUtils.EMPTY);
                optCitizen.get().setItemStackToSlot(EquipmentSlotType.HEAD, ItemStackUtils.EMPTY);
                optCitizen.get().setItemStackToSlot(EquipmentSlotType.LEGS, ItemStackUtils.EMPTY);
                optCitizen.get().setItemStackToSlot(EquipmentSlotType.MAINHAND, ItemStackUtils.EMPTY);
                optCitizen.get().setItemStackToSlot(EquipmentSlotType.OFFHAND, ItemStackUtils.EMPTY);
            }
            citizen.setHomeBuilding(null);
        }
        super.removeCitizen(citizen);
    }

    @Override
    public void serializeToView(@NotNull final PacketBuffer buf)
    {
        super.serializeToView(buf);
        buf.writeBoolean(assignManually);
        buf.writeBoolean(retrieveOnLowHealth);
        buf.writeBoolean(patrolManually);
        buf.writeInt(task.ordinal());
        buf.writeInt(patrolTargets.size());

        for (final BlockPos pos : patrolTargets)
        {
            buf.writeBlockPos(pos);
        }

        if (mobsToAttack.isEmpty())
        {
            calculateMobs();
        }

        buf.writeInt(mobsToAttack.size());
        for (final MobEntryView entry : mobsToAttack.values())
        {
            MobEntryView.writeToByteBuf(buf, entry);
        }

        buf.writeBlockPos(HunterPos);

        buf.writeInt(this.getAssignedCitizen().size());
        for (final ICitizenData citizen : this.getAssignedCitizen())
        {
            buf.writeInt(citizen.getId());
        }
    }

    /**
     * Get the Hunter's {@link HunterTask}.
     *
     * @return The task of the Hunter.
     */
    public HunterTask getTask()
    {
        return this.task;
    }

    /**
     * Set the Hunter's {@link HunterTask}.
     *
     * @param task The task to set.
     */
    public void setTask(final HunterTask task)
    {
        this.task = task;
        this.markDirty();
    }

    /**
     * The Hunters which arrived at the patrol positions
     */
    private final Set<AbstractEntityCitizen> arrivedAtPatrol = new HashSet<>();

    /**
     * The last patrol position
     */
    private BlockPos lastPatrolPoint;

    /**
     * The patrol waiting for others timeout
     */
    private int patrolTimer = 0;

    @Override
    public void onColonyTick(@NotNull final IColony colony)
    {
        super.onColonyTick(colony);

        if (patrolTimer > 0 && task == HunterTask.PATROL)
        {
            patrolTimer--;
            if (patrolTimer <= 0 && !getAssignedCitizen().isEmpty())
            {
                // Next patrol point
                startPatrolNext();
            }
        }
    }

    public void arrivedAtPatrolPoint(final AbstractEntityCitizen Hunter)
    {
        // Start waiting timer for other Hunters
        if (arrivedAtPatrol.isEmpty())
        {
            patrolTimer = 1;
        }

        arrivedAtPatrol.add(Hunter);

        if (getAssignedCitizen().size() <= arrivedAtPatrol.size() || patrolTimer <= 0)
        {
            // Next patrol point
            startPatrolNext();
        }
    }

    /**
     * Starts the patrol to the next point
     */
    private void startPatrolNext()
    {
        getNextPatrolTarget(true);
        patrolTimer = 5;

        for (final ICitizenData curHunter : getAssignedCitizen())
        {
            if (curHunter.getEntity().isPresent())
            {
                if (curHunter.getEntity().get().getCitizenJobHandler().getColonyJob() instanceof JobHunter)
                {
                    ((EntityAIHunter) curHunter.getEntity().get().getCitizenJobHandler().getColonyJob().getWorkerAI()).setNextPatrolTarget(lastPatrolPoint);
                }
            }
        }
        arrivedAtPatrol.clear();
    }

    @Nullable
    public BlockPos getNextPatrolTarget(final boolean newTarget)
    {
        if (!newTarget && lastPatrolPoint != null)
        {
            return lastPatrolPoint;
        }

        if (tempNextPatrolPoint != null)
        {
            lastPatrolPoint = tempNextPatrolPoint;
            tempNextPatrolPoint = null;
            return lastPatrolPoint;
        }

        if (lastPatrolPoint == null)
        {
            lastPatrolPoint = getAssignedCitizen().get(0).getLastPosition();
            return lastPatrolPoint;
        }

        if (!patrolManually || patrolTargets == null || patrolTargets.isEmpty())
        {
            BlockPos pos = null;
            if (this.pathingFuture != null && this.pathingFuture.isDone())
            {
                try
                {
                    pos = this.pathingFuture.get().getTarget();
                }
                catch (final Exception e)
                {
                    Log.getLogger().warn("Hunter pathing interrupted", e);
                }
                this.pathingFuture = null;
            }
            else if (colony.getWorld().rand.nextBoolean() || (this.pathingFuture != null && this.pathingFuture.isCancelled()))
            {
                this.pathingFuture = Pathfinding.enqueue(new PathJobRandomPos(colony.getWorld(),lastPatrolPoint,10, 30,null));
            }
            else
            {
                pos = colony.getBuildingManager().getRandomBuilding(b -> true);
            }

            if (pos != null)
            {
                if (BlockPosUtil.getDistance2D(pos, getPosition()) > getPatrolDistance())
                {
                    lastPatrolPoint = getPosition();
                    return lastPatrolPoint;
                }
                lastPatrolPoint = pos;
            }
            return lastPatrolPoint;
        }

        if (patrolTargets.contains(lastPatrolPoint))
        {
            int index = patrolTargets.indexOf(lastPatrolPoint) + 1;

            if (index >= patrolTargets.size())
            {
                index = 0;
            }

            lastPatrolPoint = patrolTargets.get(index);
            return lastPatrolPoint;
        }
        lastPatrolPoint = patrolTargets.get(0);
        return lastPatrolPoint;
    }

    public int getPatrolDistance()
    {
        return PATROL_BASE_DIST + this.getBuildingLevel() * PATROL_DISTANCE;
    }

    /**
     * Sets a one time consumed temporary next position to patrol towards
     *
     * @param pos Position to set
     */
    public void setTempNextPatrolPoint(final BlockPos pos)
    {
        tempNextPatrolPoint = pos;
    }

    /**
     * The client view for the Hunter building.
     */
    public static class View extends AbstractBuildingWorker.View
    {

        /**
         * Assign the HunterType manually, knight, Hunter, or *Other* (Future usage)
         */
        private boolean assignManually = false;

        /**
         * Retrieve the Hunter on low health.
         */
        private boolean retrieveOnLowHealth = false;

        /**
         * Patrol manually or automatically.
         */
        private boolean patrolManually = false;

        /**
         * The {@link HunterTask} of the Hunter.
         */
        private HunterTask task = HunterTask.PATROL;

        /**
         * Position the Hunter should Hunter.
         */
        private BlockPos HunterPos = this.getID();

        /**
         * The list of manual patrol targets.
         */
        private List<BlockPos> patrolTargets = new ArrayList<>();

        /**
         * Hashmap of mobs we may or may not attack.
         */
        private List<MobEntryView> mobsToAttack = new ArrayList<>();

        @NotNull
        private final List<Integer> Hunters = new ArrayList<>();

        /**
         * The client view constructor for the AbstractHunterBuilding.
         *
         * @param c the colony.
         * @param l the location.
         */
        public View(final IColonyView c, @NotNull final BlockPos l)
        {
            super(c, l);
        }

        /**
         * Creates a new window for the building.
         *
         * @return a BlockOut window.
         */
        @NotNull
        @Override
        public Window getWindow()
        {
            return new WindowHutHunter(this);
        }

        /**
         * Getter for the list of residents.
         *
         * @return an unmodifiable list.
         */
        @NotNull
        public List<Integer> getHunters()
        {
            return Collections.unmodifiableList(Hunters);
        }

        @Override
        public void deserialize(@NotNull final PacketBuffer buf)
        {
            super.deserialize(buf);
            assignManually = buf.readBoolean();
            retrieveOnLowHealth = buf.readBoolean();
            patrolManually = buf.readBoolean();

            task = HunterTask.values()[buf.readInt()];

            final int targetSize = buf.readInt();
            patrolTargets = new ArrayList<>();

            for (int i = 0; i < targetSize; i++)
            {
                patrolTargets.add(buf.readBlockPos());
            }

            mobsToAttack.clear();
            final int mobSize = buf.readInt();
            for (int i = 0; i < mobSize; i++)
            {
                final MobEntryView mobEntry = MobEntryView.readFromByteBuf(buf);
                mobsToAttack.add(mobEntry);
            }

            HunterPos = buf.readBlockPos();

            Hunters.clear();
            final int numResidents = buf.readInt();
            for (int i = 0; i < numResidents; ++i)
            {
                Hunters.add(buf.readInt());
            }
        }

        public void setAssignManually(final boolean assignManually)
        {
            this.assignManually = assignManually;
        }

        public boolean isAssignManually()
        {
            return assignManually;
        }

        public void setRetrieveOnLowHealth(final boolean retrieveOnLowHealth)
        {
            this.retrieveOnLowHealth = retrieveOnLowHealth;
        }

        public boolean isRetrieveOnLowHealth()
        {
            return retrieveOnLowHealth;
        }

        public void setPatrolManually(final boolean patrolManually)
        {
            this.patrolManually = patrolManually;
        }

        public void setMobsToAttack(final List<MobEntryView> mobsToAttack)
        {
            this.mobsToAttack = new ArrayList<>(mobsToAttack);
        }

        public boolean isPatrolManually()
        {
            return patrolManually;
        }

        public void setTask(final HunterTask task)
        {
            this.task = task;
            this.getColony().markDirty();
        }

        public HunterTask getTask()
        {
            return task;
        }

        public BlockPos getHunterPos()
        {
            return HunterPos;
        }

        public List<BlockPos> getPatrolTargets()
        {
            return new ArrayList<>(patrolTargets);
        }

        public List<MobEntryView> getMobsToAttack()
        {
            return new ArrayList<>(mobsToAttack);
        }
    }

    public List<BlockPos> getPatrolTargets()
    {
        return new ArrayList<>(patrolTargets);
    }

    public boolean shallRetrieveOnLowHealth()
    {
        return retrieveOnLowHealth;
    }

    public void setRetrieveOnLowHealth(final boolean retrieve)
    {
        this.retrieveOnLowHealth = retrieve;
    }

    public boolean shallPatrolManually()
    {
        return patrolManually;
    }

    public void setPatrolManually(final boolean patrolManually)
    {
        this.patrolManually = patrolManually;
    }

    public boolean shallAssignManually()
    {
        return assignManually;
    }

    public void setAssignManually(final boolean assignManually)
    {
        this.assignManually = assignManually;
    }

    public BlockPos getHunterPos()
    {
        return HunterPos;
    }

    public void setHunterPos(final BlockPos HunterPos)
    {
        this.HunterPos = HunterPos;
    }

    public Map<ResourceLocation, MobEntryView> getMobsToAttack()
    {
        return mobsToAttack;
    }

    public void setMobsToAttack(final List<MobEntryView> list)
    {
        this.mobsToAttack = new HashMap<>();
        for (MobEntryView entry : list)
        {
            mobsToAttack.put(entry.getEntityEntry().getRegistryName(), entry);
        }
    }

    @Nullable
    public ILocation getRallyLocation()
    {
        if (rallyLocation == null)
        {
            return null;
        }

        boolean outOfRange = false;
        final IColony colonyAtPosition = IColonyManager.getInstance().getColonyByPosFromDim(rallyLocation.getDimension(), rallyLocation.getInDimensionLocation());
        if (colonyAtPosition == null || colonyAtPosition.getID() != colony.getID())
        {
            outOfRange = true;
        }

        if (rallyLocation instanceof EntityLocation)
        {
            final PlayerEntity player = ((EntityLocation) rallyLocation).getPlayerEntity();
            if (player == null)
            {
                setRallyLocation(null);
                return null;
            }

            if (outOfRange)
            {
                LanguageHandler.sendPlayerMessage(player, "item.minecolonies.banner_rally_Hunters.outofrange");
                setRallyLocation(null);
                return null;
            }

            final int size = player.inventory.getSizeInventory();
            for (int i = 0; i < size; i++)
            {
                final ItemStack stack = player.inventory.getStackInSlot(i);
                if (stack.getItem() instanceof ItemBannerRallyHunter)
                {
                    if (((ItemBannerRallyHunter) (stack.getItem())).isActiveForHunter(stack, this))
                    {
                        return rallyLocation;
                    }
                }
            }
            // Note: We do not reset the rallyLocation here.
            // So, if the player doesn't properly deactivate the banner, this will cause relatively minor lag.
            // But, in exchange, the player does not have to reactivate the banner so often, and it also works
            // if the user moves the banner around in the inventory.
            return null;
        }

        return rallyLocation;
    }

    public void setRallyLocation(final ILocation location)
    {
        boolean reduceSaturation = false;
        if (rallyLocation != null && location == null)
        {
            reduceSaturation = true;
        }

        rallyLocation = location;

        for (final ICitizenData iCitizenData : getAssignedCitizen())
        {
            if (reduceSaturation && iCitizenData.getSaturation() < LOW_SATURATION)
            {
                // In addition to the scaled saturation reduction during rallying, stopping a rally
                // will - if only LOW_SATURATION is left - set the saturation level to 0.
                iCitizenData.decreaseSaturation(LOW_SATURATION);
            }
        }
    }

    /**
     * Bonus Hunter hp per bulding level
     *
     * @return the bonus health.
     */
    protected int getBonusHealth()
    {
        return BONUS_HP_SINGLE_GUARD + getBuildingLevel() * BONUS_HEALTH_PER_LEVEL;
    }

    /**
     * Adds new patrolTargets.
     *
     * @param target the target to add
     */
    public void addPatrolTargets(final BlockPos target)
    {
        this.patrolTargets.add(target);
        this.markDirty();
    }

    /**
     * Resets the patrolTargets list.
     */
    public void resetPatrolTargets()
    {
        this.patrolTargets = new ArrayList<>();
        this.markDirty();
    }

    /**
     * Get the Vision bonus range for the building level
     *
     * @return an integer for the additional range.
     */
    public int getBonusVision()
    {
        return BASE_VISION_RANGE + getBuildingLevel() * VISION_RANGE_PER_LEVEL;
    }

    /**
     * Populates the mobs list from the ForgeRegistries.
     */
    public void calculateMobs()
    {
        mobsToAttack = new HashMap<>();

        int i = 0;
        for (final Map.Entry<RegistryKey<EntityType<?>>, EntityType<?>> entry : ForgeRegistries.ENTITIES.getEntries())
        {
            if (entry.getValue().getClassification() == EntityClassification.MONSTER)
            {
                i++;
                mobsToAttack.put(entry.getKey().getLocation(), new MobEntryView(entry.getKey().getLocation(), true, i));
            }
            else
            {
                for (final String location : MineColonies.getConfig().getServer().hunterResourceLocations.get())
                {
                    if (entry.getKey() != null && entry.getKey().toString().equals(location))
                    {
                        i++;
                        mobsToAttack.put(entry.getKey().getLocation(), new MobEntryView(entry.getKey().getLocation(), true, i));
                    }
                }
            }
        }

        getColony().getPackageManager().getCloseSubscribers().forEach(player -> Network
                .getNetwork()
                .sendToPlayer(new HunterMobAttackListMessage(getColony().getID(),
                                getID(),
                                new ArrayList<>(mobsToAttack.values())),
                        player));
    }

    @Override
    public boolean canWorkDuringTheRain()
    {
        return true;
    }

    public int getDefenceBonus()
    {
        return DEFENCE_BONUS;
    }

    public int getOffenceBonus()
    {
        return OFFENCE_BONUS;
    }

    @NotNull
    @Override
    public String getSchematicName()
    {
        return SCHEMATIC_NAME;
    }

    @Override
    public int getMaxBuildingLevel()
    {
        return MAX_LEVEL;
    }

    @Override
    public int getClaimRadius(final int newLevel)
    {
        switch (newLevel)
        {
            case 1:
                return 2;
            case 2:
            case 3:
                return 3;
            case 4:
                return 4;
            case 5:
                return 5;
            default:
                return 0;
        }
    }

    @Override
    public void onDestroyed()
    {
        super.onDestroyed();
        colony.getBuildingManager().guardBuildingChangedAt(this, 0);
    }

//    @Override
//    public void onUpgradeComplete(final int newLevel)
//    {
//        super.onUpgradeComplete(newLevel);
//        colony.getBuildingManager().guardBuildingChangedAt(this, newLevel);
//    }

    public boolean requiresManualTarget()
    {
        return (!patrolManually || patrolTargets == null || patrolTargets.isEmpty() || tempNextPatrolPoint != null) && tempNextPatrolPoint == null;
    }

    @Override
    public BuildingEntry getBuildingRegistryEntry()
    {
        return ModBuildings.guardTower;
    }

//    /**
//     * The client view for the bakery building.
//     */
//    public static class View extends BuildingHunter.View
//    {
//        /**
//         * The client view constructor for the AbstractGuardBuilding.
//         *
//         * @param c the colony.
//         * @param l the location.
//         */
//        public View(final IColonyView c, @NotNull final BlockPos l)
//        {
//            super(c, l);
//        }
//    }
}
