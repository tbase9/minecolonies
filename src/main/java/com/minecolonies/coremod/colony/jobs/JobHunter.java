package com.minecolonies.coremod.colony.jobs;

import com.minecolonies.api.client.render.modeltype.BipedModelType;
import com.minecolonies.api.client.render.modeltype.IModelType;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.jobs.ModJobs;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.coremod.entity.ai.citizen.hunter.EntityAIHunter;
import net.minecraft.util.DamageSource;
import net.minecraftforge.registries.ForgeRegistryEntry;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.GUARD_SLEEP;

public class JobHunter extends AbstractJob<EntityAIHunter, JobHunter>
{
    /**
     * The name associated with the job.
     */
    public static final String DESC = "com.minecolonies.coremod.job.Hunter";

    /**
     * Initialize citizen data.
     *
     * @param entity the citizen data.
     */
    public JobHunter(final ICitizenData entity)
    {
        super(entity);
    }

    @Override
    public void triggerDeathAchievement(final DamageSource source, final AbstractEntityCitizen citizen)
    {
        super.triggerDeathAchievement(source, citizen);
    }

    @Override
    public boolean allowsAvoidance()
    {
        return false;
    }

    /**
     * Whether the guard is asleep.
     *
     * @return true if sleeping
     */
    public boolean isAsleep()
    {
        return getWorkerAI() != null && getWorkerAI().getState() == GUARD_SLEEP;
    }

    /**
     * Generates the {@link EntityAIHunter} job for our ranger.
     *
     * @return The AI.
     */
    @Override
    public EntityAIHunter generateAI()
    {
        return new EntityAIHunter(this);
    }

    @Override
    public JobEntry getJobRegistryEntry()
    {
        return ModJobs.hunter;
    }

    /**
     * Gets the name of our ranger.
     *
     * @return The name.
     */
    @Override
    public String getName()
    {
        return DESC;
    }

    /**
     * Gets the {@link BipedModelType} to use for our ranger.
     *
     * @return The model to use.
     */
    @Override
    public IModelType getModel()
    {
        return BipedModelType.ARCHER_GUARD;
    }
}
