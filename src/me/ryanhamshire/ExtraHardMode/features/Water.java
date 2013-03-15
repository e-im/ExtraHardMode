package me.ryanhamshire.ExtraHardMode.features;

import me.ryanhamshire.ExtraHardMode.ExtraHardMode;
import me.ryanhamshire.ExtraHardMode.config.RootConfig;
import me.ryanhamshire.ExtraHardMode.config.RootNode;
import me.ryanhamshire.ExtraHardMode.config.messages.MessageConfig;
import me.ryanhamshire.ExtraHardMode.config.messages.MessageNode;
import me.ryanhamshire.ExtraHardMode.module.DataStoreModule;
import me.ryanhamshire.ExtraHardMode.module.UtilityModule;
import me.ryanhamshire.ExtraHardMode.service.PermissionNode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.util.Vector;

import java.util.List;


public class Water implements Listener
{
    ExtraHardMode plugin;
    RootConfig rootC;
    UtilityModule utils;

    public Water (ExtraHardMode plugin)
    {
        this.plugin = plugin;
        rootC = plugin.getModuleForClass(RootConfig.class);
        utils = plugin.getModuleForClass(UtilityModule.class);
    }
    /**
     * when a player moves...
     *
     * @param event - Event that occurred.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    void onPlayerMove(PlayerMoveEvent event)
    {
        Player player = event.getPlayer();
        World world = player.getWorld();
        Location from = event.getFrom();
        Location to = event.getTo();
        Block fromBlock = from.getBlock();
        Block toBlock = to.getBlock();

        List<String> worlds = rootC.getStringList(RootNode.WORLDS);

        float maxWeight = (float)rootC.getDouble(RootNode.NO_SWIMMING_IN_ARMOR_MAX_POINTS);
        float armorPoints = (float)rootC.getDouble(RootNode.NO_SWIMMING_IN_ARMOR_ARMOR_POINTS);
        float inventoryPoints = (float)rootC.getDouble(RootNode.NO_SWIMMING_IN_ARMOR_INV_POINTS);
        float toolPoints = (float)rootC.getDouble(RootNode.NO_SWIMMING_IN_ARMOR_TOOL_POINTS);

        int drowningRate = rootC.getInt(RootNode.NO_SWIMMING_IN_ARMOR_DROWN_RATE);
        int overEncumbranceExtra = rootC.getInt(RootNode.NO_SWIMMING_IN_ARMOR_ENCUMBRANCE_EXTRA);

        float normalDrownVel = -.5F;
        float overwaterDrownVel = -.7F;

        // FEATURE: no swimming while heavy, only enabled worlds, players without bypass permission and not in creative
        if (rootC.getBoolean(RootNode.NO_SWIMMING_IN_ARMOR) && worlds.contains(world.getName())
                &! player.hasPermission(PermissionNode.BYPASS.getNode()) &! player.getGameMode().equals(GameMode.CREATIVE))
        {
            // only care about moving up
            if (to.getY() > from.getY())
            {
                DataStoreModule.PlayerData playerData = plugin.getModuleForClass(DataStoreModule.class).getPlayerData(player.getName());
                // only when in water
                if (fromBlock.isLiquid() && toBlock.isLiquid() &&
                        //Water Elevators, there is usually one wide and dont have water on the sides
                        (      toBlock.getRelative(BlockFace.WEST).getType().equals(Material.WATER)
                                && toBlock.getRelative(BlockFace.NORTH).getType().equals(Material.WATER)
                                && toBlock.getRelative(BlockFace.EAST).getType().equals(Material.WATER)
                                && toBlock.getRelative(BlockFace.SOUTH).getType().equals(Material.WATER) ) )
                {
                    // only when in 1 deep water
                    Block underFromBlock = fromBlock.getRelative(BlockFace.DOWN);
                    if (underFromBlock.isLiquid())
                    {
                        // if no cached value, calculate
                        if (playerData.cachedWeightStatus <= 0)
                        {
                            playerData.cachedWeightStatus = utils.inventoryWeight(player, armorPoints, inventoryPoints, toolPoints);
                        }
                        // if too heavy let player feel the weight by pulling them down, if in boat can always swim
                        if (playerData.cachedWeightStatus > maxWeight &! player.isInsideVehicle())
                        {
                            drown(player, drowningRate, overEncumbranceExtra, playerData.cachedWeightStatus, maxWeight, normalDrownVel, overwaterDrownVel);
                        }
                    }
                }
                //when you swim up waterfalls and basically are flying with only a tip of your body in water
                else if (rootC.getBoolean(RootNode.NO_SWIMMING_IN_ARMOR_BLOCK_ELEVATORS) &!
                        utils.isPlayerOnLadder(player) &! player.isInsideVehicle())
                {
                    if (playerData.cachedWeightStatus <= 0)
                    {
                        playerData.cachedWeightStatus = utils.inventoryWeight(player, armorPoints, inventoryPoints, toolPoints);
                    }
                    else if (playerData.cachedWeightStatus > maxWeight)
                    {
                        //Detect waterfalls
                        BlockFace[] faces = {
                                BlockFace.WEST,
                                BlockFace.NORTH_WEST,
                                BlockFace.NORTH,
                                BlockFace.NORTH_EAST,
                                BlockFace.EAST,
                                BlockFace.SOUTH_EAST,
                                BlockFace.SOUTH,
                                BlockFace.SOUTH_WEST };
                        Location loc = player.getLocation();
                        boolean isWaterNear = false;
                        for (BlockFace face : faces)
                        {
                            Material nearType = loc.getBlock().getRelative(face).getType();
                            if (nearType.equals(Material.STATIONARY_WATER))
                                isWaterNear = true;
                        }
                        if (isWaterNear) drown(player, drowningRate, overEncumbranceExtra, playerData.cachedWeightStatus, maxWeight, normalDrownVel + 0.3F, normalDrownVel + 0.3F); //the water flowing down pulls you down
                    }
                }
            }
        }
    }

    /**
     * Drowns the player at the given rate
     */
    public void drown (Player player, int drowningRate, int overEncumbranceExtra, float cachedWeightStatus, float maxWeight, float normalDrownVel, float overwaterDrownVel)
    {
        if (cachedWeightStatus > maxWeight)
        {
            MessageConfig messages = plugin.getModuleForClass(MessageConfig.class);
            float rdm = plugin.getRandom().nextFloat(); //how expensive is this
            //drownrate + extra when overencumbered
            float drownPercent = ((float)drowningRate / 500F) + ((cachedWeightStatus - maxWeight) * overEncumbranceExtra) / 500F;
            if (rdm < drownPercent)
            {
                Vector vec = player.getVelocity();
                //when floating on top of water pull down more
                Material material = player.getLocation().getBlock().getRelative((BlockFace.UP)).getType();
                if (material.equals(Material.AIR))
                    vec.setY(overwaterDrownVel);
                else  //when under water
                    vec.setY(normalDrownVel);
                player.setVelocity(vec);
                plugin.sendMessage(player, messages.getString(MessageNode.NO_SWIMMING_IN_ARMOR));
            }
        }
    }

    /**
     * when a player drops an item
     *
     * @param event - Event that occurred.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onPlayerDropItem(PlayerDropItemEvent event)
    {
        // FEATURE: players can't swim when they're carrying a lot of weight
        Player player = event.getPlayer();
        DataStoreModule.PlayerData playerData = plugin.getModuleForClass(DataStoreModule.class).getPlayerData(player.getName());
        playerData.cachedWeightStatus = -1F;
    }

    /**
     * when a player picks up an item
     *
     * @param event - Event that occurred.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onPlayerPickupItem(PlayerPickupItemEvent event)
    {
        // FEATURE: players can't swim when they're carrying a lot of weight
        Player player = event.getPlayer();
        DataStoreModule.PlayerData playerData = plugin.getModuleForClass(DataStoreModule.class).getPlayerData(player.getName());
        playerData.cachedWeightStatus = -1F;
    }

    /**
     * When a player interacts with an inventory.
     *
     * @param event - Event that occurred.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onPlayerInventoryClick(InventoryClickEvent event)
    {
        // FEATURE: players can't swim when they're carrying a lot of weight
        HumanEntity humanEntity = event.getWhoClicked();
        if (humanEntity instanceof Player)
        {
            Player player = (Player) humanEntity;
            DataStoreModule.PlayerData playerData = plugin.getModuleForClass(DataStoreModule.class).getPlayerData(player.getName());
            playerData.cachedWeightStatus = -1F;
        }
    }
}
