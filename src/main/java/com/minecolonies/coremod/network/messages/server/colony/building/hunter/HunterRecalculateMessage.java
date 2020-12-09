package com.minecolonies.coremod.network.messages.server.colony.building.hunter;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.coremod.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.coremod.colony.buildings.workerbuildings.BuildingHunter;
import com.minecolonies.coremod.network.messages.server.AbstractBuildingServerMessage;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

public class HunterRecalculateMessage extends AbstractBuildingServerMessage<BuildingHunter>
{
    public HunterRecalculateMessage()
    {
    }

    @Override
    protected void toBytesOverride(final PacketBuffer buf)
    {

    }

    @Override
    protected void fromBytesOverride(final PacketBuffer buf)
    {

    }

    public HunterRecalculateMessage(final IBuildingView building)
    {
        super(building);
    }

    @Override
    protected void onExecute(
            final NetworkEvent.Context ctxIn, final boolean isLogicalServer, final IColony colony, final BuildingHunter building)
    {
        building.calculateMobs();
    }
}
