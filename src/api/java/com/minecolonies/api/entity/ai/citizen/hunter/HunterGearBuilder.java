package com.minecolonies.api.entity.ai.citizen.hunter;

import com.minecolonies.api.entity.ai.citizen.hunter.HunterGear;
import com.minecolonies.api.util.constant.ToolType;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.util.Tuple;

import java.util.ArrayList;
import java.util.List;

public class HunterGearBuilder
{
    /**
     * Private constructor to hide implicit one.
     */
    private HunterGearBuilder()
    {
        /*
         * Intentionally left empty.
         */
    }

    /**
     * Build the gear for a certain armor level and level range.
     *
     * @param minArmorLevel      the min armor level.
     * @param maxArmorLevel      the max armor level.
     * @param levelRange         the level range of the hunter.
     * @param buildingLevelRange the building level range.
     * @return the list of items.
     */
    public static List<HunterGear> buildGearForLevel(
            final int minArmorLevel,
            final int maxArmorLevel,
            final Tuple<Integer, Integer> levelRange,
            final Tuple<Integer, Integer> buildingLevelRange)
    {
        final List<HunterGear> armorList = new ArrayList<>();
        armorList.add(new HunterGear(ToolType.BOOTS, EquipmentSlotType.FEET, minArmorLevel, maxArmorLevel, levelRange, buildingLevelRange));
        armorList.add(new HunterGear(ToolType.CHESTPLATE, EquipmentSlotType.CHEST, minArmorLevel, maxArmorLevel, levelRange, buildingLevelRange));
        armorList.add(new HunterGear(ToolType.HELMET, EquipmentSlotType.HEAD, minArmorLevel, maxArmorLevel, levelRange, buildingLevelRange));
        armorList.add(new HunterGear(ToolType.LEGGINGS, EquipmentSlotType.LEGS, minArmorLevel, maxArmorLevel, levelRange, buildingLevelRange));
        return armorList;
    }
}
