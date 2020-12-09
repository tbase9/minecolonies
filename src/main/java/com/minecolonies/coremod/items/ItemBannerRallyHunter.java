package com.minecolonies.coremod.items;

import com.google.common.collect.ImmutableList;
import com.ldtteam.structurize.util.LanguageHandler;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.colony.requestsystem.StandardFactoryController;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.creativetab.ModCreativeTabs;
import com.minecolonies.api.util.Log;
import com.minecolonies.api.util.constant.TranslationConstants;
import com.minecolonies.coremod.MineColonies;
import com.minecolonies.coremod.colony.buildings.workerbuildings.BuildingHunter;
import com.minecolonies.coremod.colony.requestsystem.locations.EntityLocation;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import static com.minecolonies.api.util.constant.Constants.TAG_COMPOUND;
import static com.minecolonies.api.util.constant.NbtTagConstants.TAG_ID;
import static com.minecolonies.api.util.constant.NbtTagConstants.TAG_RALLIED_HUNTERHUT;

public class ItemBannerRallyHunter extends AbstractItemMinecolonies
{
    /**
     * The compound tag for the activity status of the banner
     */
    private static final String TAG_IS_ACTIVE = "isActive";

    /**
     * Rally Hunter Banner constructor. Sets max stack to 1, like other tools.
     *
     * @param properties the properties.
     */
    public ItemBannerRallyHunter(final Properties properties)
    {
        super("banner_rally_hunter", properties.maxStackSize(1).maxDamage(0).group(ModCreativeTabs.MINECOLONIES));
    }

    @NotNull
    @Override
    public ActionResultType onItemUse(final ItemUseContext context)
    {
        final PlayerEntity player = context.getPlayer();

        if (player == null)
        {
            return ActionResultType.FAIL;
        }

        final ItemStack banner = context.getPlayer().getHeldItem(context.getHand());

        final CompoundNBT compound = checkForCompound(banner);

        if (isHunterBuilding(context.getWorld(), context.getPos()))
        {
            if (context.getWorld().isRemote())
            {
                return ActionResultType.SUCCESS;
            }
            else
            {
                final BuildingHunter building = getHunterBuilding(context.getWorld(), context.getPos());
                if (!building.getColony().getPermissions().hasPermission(player, Action.RALLY_HUNTERS))
                {
                    LanguageHandler.sendPlayerMessage(player, "com.minecolonies.coremod.permission.no");
                    return ActionResultType.FAIL;
                }

                final ILocation location = building.getLocation();
                if (removeHunterTowerAtLocation(banner, location))
                {
                    LanguageHandler.sendPlayerMessage(context.getPlayer(),
                            TranslationConstants.COM_MINECOLONIES_BANNER_RALLY_HUNTER_DESELECTED,
                            building.getSchematicName(), location.toString());
                }
                else
                {
                    final ListNBT hunterTowers = compound.getList(TAG_RALLIED_HUNTERHUT, TAG_COMPOUND);
                    hunterTowers.add(StandardFactoryController.getInstance().serialize(location));
                    LanguageHandler.sendPlayerMessage(context.getPlayer(),
                            TranslationConstants.COM_MINECOLONIES_BANNER_RALLY_HUNTER_SELECTED,
                            building.getSchematicName(), location.toString());
                }
            }
        }
        else
        {
            handleRightClick(banner, context.getPlayer());
        }

        return ActionResultType.SUCCESS;
    }

    @NotNull
    @Override
    public ActionResult<ItemStack> onItemRightClick(final World worldIn, final PlayerEntity playerIn, final Hand handIn)
    {
        final ItemStack banner = playerIn.getHeldItem(handIn);
        handleRightClick(banner, playerIn);
        return ActionResult.resultSuccess(banner);
    }

    @Override
    public boolean onDroppedByPlayer(final ItemStack item, final PlayerEntity player)
    {
        if (!player.getEntityWorld().isRemote())
        {
            final CompoundNBT compound = checkForCompound(item);
            compound.putBoolean(TAG_IS_ACTIVE, false);
            broadcastPlayerToRally(item, player.getEntityWorld(), null);
        }

        return super.onDroppedByPlayer(item, player);
    }

    /**
     * Handles a rightclick or rightclick-while-sneaking that's *not* adding/removing a hunter tower from the list
     *
     * @param banner   The banner with which the player rightclicked.
     * @param playerIn The player that rightclicked.
     */
    private void handleRightClick(final ItemStack banner, final PlayerEntity playerIn)
    {
        if (playerIn.isSneaking() && !playerIn.getEntityWorld().isRemote())
        {
            toggleBanner(banner, playerIn);
        }
        else if (!playerIn.isSneaking() && playerIn.getEntityWorld().isRemote())
        {
            if (getHunterTowerLocations(banner).isEmpty())
            {
                LanguageHandler.sendPlayerMessage(playerIn,
                        TranslationConstants.COM_MINECOLONIES_BANNER_RALLY_HUNTER_TOOLTIP_EMPTY);
            }
            else
            {
                MineColonies.proxy.openBannerRallyHunterWindow(banner);
            }
        }
    }

    /**
     * Toggles the banner. This cannot be done by "the system" but must happen from here by the player. (Note that it will also send chat messages to the player) Thus, this method
     * is private on purpose (for now).
     *
     * @param banner   The banner to toggle
     * @param playerIn The player toggling the banner
     */
    public static void toggleBanner(final ItemStack banner, final PlayerEntity playerIn)
    {
        if (playerIn.getEntityWorld().isRemote())
        {
            Log.getLogger().error("Tried to run server-side function #toggleBanner() on the client-side!");
            return;
        }
        final CompoundNBT compound = checkForCompound(banner);
        final ListNBT hunterTowers = (ListNBT) compound.get(TAG_RALLIED_HUNTERHUT);
        if (hunterTowers == null)
        {
            Log.getLogger().error("Compound corrupt, missing TAG_RALLIED_HUNTERTOWERS");
            return;
        }
        if (hunterTowers.isEmpty())
        {
            compound.putBoolean(TAG_IS_ACTIVE, false);
            LanguageHandler.sendPlayerMessage(playerIn,
                    TranslationConstants.COM_MINECOLONIES_BANNER_RALLY_HUNTER_TOOLTIP_EMPTY);
        }
        else if (compound.getBoolean(TAG_IS_ACTIVE))
        {
            compound.putBoolean(TAG_IS_ACTIVE, false);
            broadcastPlayerToRally(banner, playerIn.getEntityWorld(), null);
            LanguageHandler.sendPlayerMessage(playerIn, "item.minecolonies.banner_rally_hunter.deactivated");
        }
        else
        {
            compound.putBoolean(TAG_IS_ACTIVE, true);
            final int numHunter = broadcastPlayerToRally(banner, playerIn.getEntityWorld(), playerIn);

            if (numHunter > 0)
            {
                LanguageHandler.sendPlayerMessage(playerIn, "item.minecolonies.banner_rally_hunter.activated", numHunter);
            }
            else
            {
                LanguageHandler.sendPlayerMessage(playerIn, "item.minecolonies.banner_rally_hunter.activated.nohunter");
            }
        }
    }

    /**
     * Broadcasts the player all the huntertowers rallied by the item are supposed to follow.
     *
     * @param banner   The banner that should broadcast
     * @param playerIn The player to follow. Can be null, if the towers should revert to "normal" mode
     * @return The number of hunter rallied
     */
    public static int broadcastPlayerToRally(final ItemStack banner, final World worldIn, @Nullable final PlayerEntity playerIn)
    {
        if (worldIn.isRemote())
        {
            Log.getLogger().error("Tried to run server-side function #broadcastPlayerToRally() on the client-side!");
            return 0;
        }

        @Nullable ILocation rallyTarget = null;
        if (!isActive(banner) || playerIn == null)
        {
            rallyTarget = null;
        }
        else
        {
            rallyTarget = new EntityLocation(playerIn.getUniqueID());
        }

        int numHunter = 0;
        for (final ILocation hunterTowerLocation : getHunterTowerLocations(banner))
        {
            // Note: getCurrentServer().getWorld() must be used here because MineColonies.proxy.getWorld() fails on single player worlds
            // We are sure we are on the server-side in this function though, so it's fine.
            final BuildingHunter building =
                    getHunterBuilding(ServerLifecycleHooks.getCurrentServer().getWorld(hunterTowerLocation.getDimension()),
                            hunterTowerLocation.getInDimensionLocation());

            // If the building is null, it means that huntertower has been moved/destroyed since being added.
            // Safely ignore this case, the player must remove the tower from the rallying list manually.
            if (building != null && (playerIn == null || building.getColony().getPermissions().hasPermission(playerIn, Action.RALLY_HUNTERS)))
            {
                building.setRallyLocation(rallyTarget);
                numHunter += building.getAssignedCitizen().size();
            }
        }
        return numHunter;
    }

    /**
     * Returns the hunter tower positions of towers rallied by the given banner.
     *
     * @param banner The banner of which the hunter towers should be retrieved
     * @return The list of huntertower positions, or an empty list if anything goes wrong during retrieval.
     */
    public static ImmutableList<ILocation> getHunterTowerLocations(final ItemStack banner)
    {
        final CompoundNBT compound = checkForCompound(banner);
        final ListNBT hunterTowersListNBT = compound.getList(TAG_RALLIED_HUNTERHUT, TAG_COMPOUND);
        if (hunterTowersListNBT == null)
        {
            Log.getLogger().error("Compound corrupt, missing TAG_RALLIED_HUNTERTOWERS");
            return ImmutableList.of();
        }

        final List<ILocation> resultList = new ArrayList<>(hunterTowersListNBT.size());
        for (final INBT hunterTowerNBT : hunterTowersListNBT)
        {
            resultList.add(StandardFactoryController.getInstance().deserialize((CompoundNBT) hunterTowerNBT));
        }
        return ImmutableList.copyOf(resultList);
    }

    /**
     * Checks if the position is a hunter building
     *
     * @param worldIn  The world in which to check
     * @param position The position to check
     * @return true if there is a hunter building at the position
     */
    public static boolean isHunterBuilding(final World worldIn, final BlockPos position)
    {
        if (worldIn.isRemote())
        {
            return IColonyManager.getInstance().getBuildingView(worldIn.getDimensionKey(), position) instanceof BuildingHunter.View;
        }
        else
        {
            return IColonyManager.getInstance().getBuilding(worldIn, position) instanceof BuildingHunter;
        }
    }

    /**
     * Fetches the (client-side) View for the hunter tower at a specific position.
     *
     * @param worldIn  The world in which to search for the hunter tower.
     * @param position The position of the hunter tower.
     * @return The Hunter tower View, or null if no hunter tower present at the location.
     */
    @Nullable
    public static BuildingHunter.View getHunterBuildingView(final World worldIn, final BlockPos position)
    {
        if (!worldIn.isRemote())
        {
            Log.getLogger().error("Tried to run client-side function #getHunterBuildingView() on the server-side!");
            return null;
        }

        return isHunterBuilding(worldIn, position)
                ? (BuildingHunter.View) IColonyManager.getInstance().getBuildingView(worldIn.getDimensionKey(), position)
                : null;
    }

    /**
     * Fetches the (server-side) buildings for the hunter tower at a specific position.
     *
     * @param worldIn  The world in which to search for the hunter tower.
     * @param position The position of the hunter tower.
     * @return The building, or null if no hunter tower present at the location.
     */
    @Nullable
    public static BuildingHunter getHunterBuilding(final World worldIn, final BlockPos position)
    {
        if (worldIn.isRemote())
        {
            Log.getLogger().error("Tried to run server-side function #getHunterBuilding() on the client-side!");
            return null;
        }

        return isHunterBuilding(worldIn, position) ? (BuildingHunter) IColonyManager.getInstance().getBuilding(worldIn, position) : null;
    }

    /**
     * Fetches the (client-side) Views of the hunter towers rallied by the banner. If a rallied position is not a hunter tower anymore (tower was moved or destroyed), the
     * corresponding entry will be null.
     *
     * @return A list of maps. Map's key is the position, Map's value is a hunter tower or null.
     */
    public static List<Pair<ILocation, BuildingHunter.View>> getHunterTowerViews(final ItemStack banner)
    {
        final LinkedList<Pair<ILocation, BuildingHunter.View>> result = new LinkedList<>();
        for (final ILocation hunterTowerLocation : getHunterTowerLocations(banner))
        {
            result.add(new Pair<>(hunterTowerLocation,
                    getHunterBuildingView(MineColonies.proxy.getWorld(hunterTowerLocation.getDimension()), hunterTowerLocation.getInDimensionLocation())));
        }
        return ImmutableList.copyOf(result);
    }

    /**
     * Checks if the given banner is active and valid for the given huntertower.
     *
     * @param banner     The banner to check
     * @param hunterTower The huntertower to check
     * @return true if the banner is active and has hunterTower in the list.
     */
    public boolean isActiveForHunter(final ItemStack banner, final BuildingHunter hunterTower)
    {
        if (!isActive(banner))
        {
            return false;
        }

        for (final ILocation existingTower : getHunterTowerLocations(banner))
        {
            if (existingTower.equals(hunterTower.getLocation()))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if the banner is active
     *
     * @param banner The banner that should be checked
     * @return true if the banner is active
     */
    public static boolean isActive(final ItemStack banner)
    {
        final CompoundNBT compound = checkForCompound(banner);
        return compound.getBoolean(TAG_IS_ACTIVE);
    }

    /**
     * Removes the hunter tower from the rallying list based on its position
     *
     * @param banner             The banner to remove the hunter tower from
     * @param hunterTowerLocation The location of the hunter tower
     * @return true if a tower has been removed
     */
    public static boolean removeHunterTowerAtLocation(final ItemStack banner, final ILocation hunterTowerLocation)
    {
        final CompoundNBT compound = checkForCompound(banner);
        final ListNBT hunterTowers = compound.getList(TAG_RALLIED_HUNTERHUT, TAG_COMPOUND);

        for (int i = 0; i < hunterTowers.size(); i++)
        {
            if (StandardFactoryController.getInstance().deserialize((CompoundNBT) hunterTowers.get(i)).equals(hunterTowerLocation))
            {
                hunterTowers.remove(i);
                banner.setTag(compound);
                return true;
            }
        }

        return false;
    }

    /**
     * Check for the compound and return it. If not available create and return it.
     *
     * @param banner the banner to check for a compound.
     * @return the compound of the item.
     */
    public static CompoundNBT checkForCompound(final ItemStack banner)
    {
        if (!banner.hasTag())
        {
            final CompoundNBT compound = new CompoundNBT();
            banner.setTag(compound);
        }

        final CompoundNBT compound = banner.getTag();
        if (!compound.contains(TAG_RALLIED_HUNTERHUT))
        {
            compound.putUniqueId(TAG_ID, UUID.randomUUID());
            compound.putBoolean(TAG_IS_ACTIVE, false);
            @NotNull final ListNBT hunterTowerList = new ListNBT();
            compound.put(TAG_RALLIED_HUNTERHUT, hunterTowerList);
        }
        return compound;
    }

    @Override
    public boolean hasEffect(@NotNull final ItemStack stack)
    {
        final CompoundNBT compound = checkForCompound(stack);
        return compound.getBoolean(TAG_IS_ACTIVE);
    }

    @Override
    public void addInformation(
            @NotNull final ItemStack stack, @Nullable final World worldIn, @NotNull final List<ITextComponent> tooltip, @NotNull final ITooltipFlag flagIn)
    {
        final IFormattableTextComponent guiHint = LanguageHandler.buildChatComponent(TranslationConstants.COM_MINECOLONIES_BANNER_RALLY_HUNTER_TOOLTIP_GUI);
        guiHint.setStyle(Style.EMPTY.setFormatting(TextFormatting.GRAY));
        tooltip.add(guiHint);

        final IFormattableTextComponent rallyHint = LanguageHandler.buildChatComponent(TranslationConstants.COM_MINECOLONIES_BANNER_RALLY_HUNTER_TOOLTIP_RALLY);
        rallyHint.setStyle(Style.EMPTY.setFormatting(TextFormatting.GRAY));
        tooltip.add(rallyHint);

        final List<ILocation> hunterTowerPositions = getHunterTowerLocations(stack);

        if (hunterTowerPositions.isEmpty())
        {
            final IFormattableTextComponent emptyTooltip = LanguageHandler.buildChatComponent(TranslationConstants.COM_MINECOLONIES_BANNER_RALLY_HUNTER_TOOLTIP_EMPTY);
            emptyTooltip.setStyle(Style.EMPTY.setFormatting(TextFormatting.GRAY));
            tooltip.add(emptyTooltip);
        }
        else
        {
            final IFormattableTextComponent numHunterTowers = LanguageHandler.buildChatComponent(TranslationConstants.COM_MINECOLONIES_BANNER_RALLY_HUNTER_TOOLTIP, hunterTowerPositions.size());
            numHunterTowers.setStyle(Style.EMPTY.setFormatting(TextFormatting.DARK_AQUA));
            tooltip.add(numHunterTowers);
        }


        super.addInformation(stack, worldIn, tooltip, flagIn);
    }
}
