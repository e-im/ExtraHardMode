package me.ryanhamshire.ExtraHardMode.module;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Wither;
import org.bukkit.metadata.FixedMetadataValue;

import me.ryanhamshire.ExtraHardMode.ExtraHardMode;
import me.ryanhamshire.ExtraHardMode.service.EHMModule;

public class EntityModule extends EHMModule {

   public EntityModule(ExtraHardMode plugin) {
      super(plugin);
   }

   /**
    * Marks an entity so that the plugin can remember not to drop loot or
    * experience if it's killed.
    * 
    * @param entity
    *           - Entity to modify.
    */
   public void markLootLess(LivingEntity entity) {
      entity.setMetadata("extrahard_environmentalDamage", new FixedMetadataValue(plugin, entity.getMaxHealth()));
   }

   // tracks total environmental damage done to an entity
   public void addEnvironmentalDamage(LivingEntity entity, int damage) {
      if(!entity.hasMetadata("extrahard_environmentalDamage")) {
         entity.setMetadata("extrahard_environmentalDamage", new FixedMetadataValue(plugin, damage));
      } else {
         int currentTotalDamage = entity.getMetadata("extrahard_environmentalDamage").get(0).asInt();
         entity.setMetadata("extrahard_environmentalDamage", new FixedMetadataValue(plugin, currentTotalDamage + damage));
      }
   }

   // checks whether an entity should drop items when it dies
   public boolean isLootLess(LivingEntity entity) {
      if(entity instanceof Creature && entity.hasMetadata("extrahard_environmentalDamage")) {
         int totalDamage = entity.getMetadata("extrahard_environmentalDamage").get(0).asInt();
         if(!(entity instanceof Wither)) {
            return (totalDamage > entity.getMaxHealth() / 2);
         } else {
            return false; // wither is exempt. he can't be farmed because
                          // creating him requires combining not-farmable
                          // components
         }
      }

      return false;
   }

   /**
    * Clears any webbing which may be trapping this entity (assumes
    * two-block-tall entity)
    * 
    * @param entity
    *           - Entity to help.
    */
   public void clearWebbing(Entity entity) {
      Block feetBlock = entity.getLocation().getBlock();
      Block headBlock = feetBlock.getRelative(BlockFace.UP);

      Block[] blocks = { feetBlock, headBlock };
      for(int i = 0; i < blocks.length; i++) {
         Block block = blocks[i];
         if(block.getType() == Material.WEB) {
            block.setType(Material.AIR);
         }
      }
   }

   @Override
   public void starting() {
   }

   @Override
   public void closing() {
   }

}
