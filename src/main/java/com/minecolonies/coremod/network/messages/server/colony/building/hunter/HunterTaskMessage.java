package com.minecolonies.coremod.network.messages.server.colony.building.hunter;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.guardtype.registry.IGuardTypeRegistry;
import com.minecolonies.api.entity.ai.citizen.guards.GuardTask;
import com.minecolonies.api.entity.ai.citizen.hunter.HunterTask;
import com.minecolonies.coremod.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.coremod.colony.buildings.workerbuildings.BuildingHunter;
import com.minecolonies.coremod.network.messages.server.AbstractBuildingServerMessage;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.network.NetworkEvent;
import org.jetbrains.annotations.NotNull;

public class HunterTaskMessage extends AbstractBuildingServerMessage<BuildingHunter>
{
    private ResourceLocation job;
    private boolean          assignmentMode;
    private boolean          patrollingMode;
    private boolean          retrieval;
    private int              task;

    /**
     * Empty standard constructor.
     */
    public HunterTaskMessage()
    {
        super();
    }

    /**
     * Creates an instance of the guard task
     *
     * @param building       the building.
     * @param job            the new job.
     * @param assignmentMode the new assignment mode.
     * @param patrollingMode the new patrolling mode.
     * @param retrieval      the new retrievel mode.
     * @param task           the new task.
     */
    public HunterTaskMessage(
            @NotNull final BuildingHunter.View building,
            final ResourceLocation job,
            final boolean assignmentMode,
            final boolean patrollingMode,
            final boolean retrieval,
            final int task
    )
    {
        super(building);
        this.job = job;
        this.assignmentMode = assignmentMode;
        this.patrollingMode = patrollingMode;
        this.retrieval = retrieval;
        this.task = task;
    }

    @Override
    public void fromBytesOverride(@NotNull final PacketBuffer buf)
    {

        job = buf.readResourceLocation();
        assignmentMode = buf.readBoolean();
        patrollingMode = buf.readBoolean();
        retrieval = buf.readBoolean();
        task = buf.readInt();
    }

    @Override
    public void toBytesOverride(@NotNull final PacketBuffer buf)
    {

        buf.writeResourceLocation(job);
        buf.writeBoolean(assignmentMode);
        buf.writeBoolean(patrollingMode);
        buf.writeBoolean(retrieval);
        buf.writeInt(task);
    }

    @Override
    protected void onExecute(
            final NetworkEvent.Context ctxIn, final boolean isLogicalServer, final IColony colony, final BuildingHunter building)
    {
        building.setAssignManually(assignmentMode);
        building.setPatrolManually(patrollingMode);
        building.setRetrieveOnLowHealth(retrieval);
        building.setTask(HunterTask.values()[task]);
    }
}
