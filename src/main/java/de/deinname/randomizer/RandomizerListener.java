package de.deinname.randomizer;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.LootTables;

import java.util.Collection;
import java.util.List;
import java.util.Random;

public class RandomizerListener implements Listener {

    private final RandomizerPlugin plugin;
    private final Random random = new Random();

    public RandomizerListener(RandomizerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (plugin.isPaused()) return;

        plugin.getDiscovery().discoverBlockBreak(e.getPlayer(), e.getBlock().getType());
        if (!plugin.randomBlockDrops) return;

        e.setDropItems(false);
        Material original = e.getBlock().getType();
        Material randomMat = plugin.getManager().getRandomBlockDrop(original, e.getPlayer());

        if (randomMat != null && randomMat != Material.AIR && randomMat.isItem()) {
            int amount = 1;
            BlockData data = e.getBlock().getBlockData();

            if (data instanceof org.bukkit.block.data.type.Slab slab && slab.getType() == org.bukkit.block.data.type.Slab.Type.DOUBLE) {
                amount = 2;
            } else if (data instanceof org.bukkit.block.data.type.SeaPickle pickle) {
                amount = pickle.getPickles();
            } else if (data instanceof org.bukkit.block.data.type.TurtleEgg egg) {
                amount = egg.getEggs();
            }

            e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation(), new ItemStack(randomMat, amount));
        }
    }

    @EventHandler
    public void onBlockDropItem(BlockDropItemEvent e) {
        if (plugin.isPaused() || !plugin.randomBlockDrops) return;

        Material originalBlock = e.getBlockState().getType();
        Material randomMat = plugin.getManager().getRandomBlockDrop(originalBlock, e.getPlayer());

        if (randomMat != null) {
            int totalAmount = 0;
            for (org.bukkit.entity.Item item : e.getItems()) {
                totalAmount += item.getItemStack().getAmount();
            }
            e.getItems().clear();

            if (totalAmount > 0 && randomMat != Material.AIR && randomMat.isItem()) {
                e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation(), new ItemStack(randomMat, totalAmount));
            }
        }
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent e) {
        if (plugin.isPaused() || !plugin.randomBlockDrops) return;

        List<Block> blocks = e.blockList();
        e.setYield(0);

        for (Block b : blocks) {
            if (b.getType() == Material.AIR) continue;
            Material original = b.getType();
            Material randomMat = plugin.getManager().getRandomBlockDrop(original, null);

            if (randomMat != null && randomMat != Material.AIR && randomMat.isItem()) {
                b.getWorld().dropItemNaturally(b.getLocation(), new ItemStack(randomMat));
            }
        }
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent e) {
        if (plugin.isPaused()) return;

        if (e.getEntity().getKiller() != null) {
            plugin.getDiscovery().discoverMobKill(e.getEntity().getKiller(), e.getEntityType());
        }

        if (!plugin.randomMobLoot) return;
        if (e.getEntity().getType() == EntityType.PLAYER) return;

        EntityType targetType = plugin.getManager().getRandomMobType(e.getEntityType(), e.getEntity().getKiller());
        if (targetType == null || targetType == e.getEntityType()) return;

        e.getDrops().clear();
        e.setDroppedExp(plugin.getManager().getMobXp(targetType));

        Entity dummy = null;
        try {
            if (targetType == EntityType.ENDER_DRAGON || targetType == EntityType.WITHER) {
                LootTable table = LootTables.valueOf(targetType.name()).getLootTable();
                if (table != null) {
                    LootContext.Builder builder = new LootContext.Builder(e.getEntity().getLocation());
                    builder.lootedEntity(e.getEntity());
                    if (e.getEntity().getKiller() != null) builder.killer(e.getEntity().getKiller());
                    e.getDrops().addAll(table.populateLoot(random, builder.build()));
                }
            } else {
                dummy = e.getEntity().getWorld().spawn(e.getEntity().getLocation(), targetType.getEntityClass(), ent -> {
                    ent.setInvulnerable(true);
                    ent.setSilent(true);
                    ent.setGravity(false);
                    ent.setCustomNameVisible(false);
                    if (ent instanceof org.bukkit.entity.LivingEntity) {
                        ((org.bukkit.entity.LivingEntity) ent).setAI(false);
                        ((org.bukkit.entity.LivingEntity) ent).setInvisible(true);
                        ((org.bukkit.entity.LivingEntity) ent).setCollidable(false);
                    }
                    if (ent instanceof Ageable) {
                        ((Ageable) ent).setAdult();
                    }
                });

                LootTable table = LootTables.valueOf(targetType.name()).getLootTable();
                if (table != null) {
                    LootContext.Builder builder = new LootContext.Builder(dummy.getLocation());
                    builder.lootedEntity(dummy);
                    if (e.getEntity().getKiller() != null) builder.killer(e.getEntity().getKiller());
                    e.getDrops().addAll(table.populateLoot(random, builder.build()));
                }
            }
        } catch (Exception ex) {
        } finally {
            if (dummy != null) dummy.remove();
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent e) {
        if (plugin.isPaused()) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;

        if (e.getRecipe() != null) {
            plugin.getDiscovery().discoverRecipeUse(p, e.getRecipe().getResult().getType());
        }

        if (!plugin.randomCrafting) return;

        ItemStack original = e.getCurrentItem();
        if (original == null) return;

        ItemStack randomResult = plugin.getManager().getRandomCraftingResult(original.getType(), p).clone();

        randomResult.setAmount(original.getAmount());
        e.setCurrentItem(randomResult);
    }
}