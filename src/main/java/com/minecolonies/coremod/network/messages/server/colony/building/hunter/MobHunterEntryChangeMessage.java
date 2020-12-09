package com.minecolonies.coremod.network.messages.server.colony.building.hunter;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.views.MobEntryView;
import com.minecolonies.coremod.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.coremod.colony.buildings.workerbuildings.BuildingHunter;
import com.minecolonies.coremod.network.messages.server.AbstractBuildingServerMessage;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class MobHunterEntryChangeMessage extends AbstractBuildingServerMessage<BuildingHunter>
{
    private List<MobEntryView> mobsToAttack = new ArrayList<>();

    /**
     * Empty standard constructor.
     */
    public MobHunterEntryChangeMessage()
    {
        super();
    }

    public MobHunterEntryChangeMessage(
            @NotNull final BuildingHunter.View building,
            final List<MobEntryView> mobsToAttack
    )
    {
        super(building);
        this.mobsToAttack = new ArrayList<>(mobsToAttack);
    }

    @Override
    public void toBytesOverride(final PacketBuffer buf)
    {


        buf.writeInt(this.mobsToAttack.size());
        for (final MobEntryView entry : this.mobsToAttack)
        {
            MobEntryView.writeToByteBuf(buf, entry);
        }
    }

    @Override
    public void fromBytesOverride(final PacketBuffer buf)
    {


        final int mobSize = buf.readInt();
        for (int i = 0; i < mobSize; i++)
        {
            final MobEntryView mobEntry = MobEntryView.readFromByteBuf(buf);
            mobsToAttack.add(mobEntry);
        }
    }

    @Override
    protected void onExecute(
            final NetworkEvent.Context ctxIn, final boolean isLogicalServer, final IColony colony, final BuildingHunter building)
    {
        building.setMobsToAttack(mobsToAttack);
    }
}
