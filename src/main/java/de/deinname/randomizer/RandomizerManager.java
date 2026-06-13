package de.deinname.randomizer;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.LootTables;

import java.util.*;

public class RandomizerManager {

    private final RandomizerPlugin plugin;

    private Map<Material, Material> blockDropMap = new HashMap<>();
    private Map<EntityType, EntityType> mobMap = new HashMap<>();
    private Map<Material, ItemStack> craftingMap = new HashMap<>();

    private final Map<Long, Map<Material, Material>> blockDropCache = new HashMap<>();
    private final Map<Long, Map<EntityType, EntityType>> mobCache = new HashMap<>();
    private final Map<Long, Map<Material, ItemStack>> craftingCache = new HashMap<>();

    private final List<Material> allValidItems = new ArrayList<>();
    private final List<Material> craftableItems = new ArrayList<>();
    private final List<EntityType> allMobs = new ArrayList<>();

    private final Set<Material> criticalItems = Set.of(
            Material.BLAZE_ROD, Material.BLAZE_POWDER,
            Material.ENDER_PEARL, Material.ENDER_EYE,
            Material.OBSIDIAN, Material.FLINT_AND_STEEL
    );
    private final Set<EntityType> hardMobs = Set.of(
            EntityType.ENDER_DRAGON, EntityType.WITHER,
            EntityType.SHULKER, EntityType.WARDEN, EntityType.ELDER_GUARDIAN
    );
    private final Set<Material> endItems = new HashSet<>();

    private long currentSeed;

    public RandomizerManager(RandomizerPlugin plugin, long seed) {
        this.plugin = plugin;
        for (Material m : Material.values()) {
            String n = m.name();
            if (n.startsWith("END_") || n.startsWith("PURPUR_") || n.startsWith("CHORUS_") ||
                    n.equals("DRAGON_EGG") || n.equals("DRAGON_HEAD") || n.equals("ELYTRA") ||
                    n.startsWith("SHULKER_")) {
                endItems.add(m);
            }
        }
        loadMaterials();
        loadMobs();
        this.currentSeed = seed;
        doShuffleGlobal(seed);
    }

    private void loadMaterials() {
        for (Material m : Material.values()) {
            if (m.isItem() && !m.isAir()) allValidItems.add(m);
        }
        Iterator<Recipe> recipeIterator = Bukkit.recipeIterator();
        Set<Material> uniqueCraftables = new HashSet<>();
        while (recipeIterator.hasNext()) {
            try {
                Recipe r = recipeIterator.next();
                uniqueCraftables.add(r.getResult().getType());
            } catch (Exception ignored) {}
        }
        craftableItems.addAll(uniqueCraftables);
        allValidItems.sort(Comparator.comparing(Enum::name));
        craftableItems.sort(Comparator.comparing(Enum::name));
    }

    private void loadMobs() {
        for (EntityType e : EntityType.values()) {
            if (e.isAlive() && e.isSpawnable()) {
                allMobs.add(e);
            }
        }
    }

    public void shuffle() {
        blockDropCache.clear();
        mobCache.clear();
        craftingCache.clear();
        this.currentSeed = new Random().nextLong();
        plugin.saveConfigSettings();
        doShuffleGlobal(this.currentSeed);
    }

    private void doShuffleGlobal(long seed) {
        this.blockDropMap = generateBlockMapping(seed);
        this.mobMap = generateMobMapping(seed);
        this.craftingMap = generateCraftingMapping(seed);

        enforceBlockSafety(this.blockDropMap);
        enforceMobSafety(this.mobMap);
        enforceCraftingSafety(this.craftingMap);
    }

    private Map<Material, Material> generateBlockMapping(long seed) {
        Random rand = new Random(seed);
        List<Material> shuffled = new ArrayList<>(allValidItems);
        Collections.shuffle(shuffled, rand);
        Map<Material, Material> map = new HashMap<>();
        for (int i = 0; i < allValidItems.size(); i++) map.put(allValidItems.get(i), shuffled.get(i));
        return map;
    }

    private Map<EntityType, EntityType> generateMobMapping(long seed) {
        Random rand = new Random(seed);
        List<EntityType> shuffled = new ArrayList<>(allMobs);
        Collections.shuffle(shuffled, rand);
        Map<EntityType, EntityType> map = new HashMap<>();
        for (int i = 0; i < allMobs.size(); i++) map.put(allMobs.get(i), shuffled.get(i));
        return map;
    }

    private Map<Material, ItemStack> generateCraftingMapping(long seed) {
        Random rand = new Random(seed);
        List<Material> pool = new ArrayList<>(allValidItems);
        Collections.shuffle(pool, rand);
        Map<Material, ItemStack> map = new HashMap<>();
        for (int i = 0; i < craftableItems.size(); i++) {
            map.put(craftableItems.get(i), new ItemStack(pool.get(i % pool.size())));
        }
        return map;
    }

    private void enforceBlockSafety(Map<Material, Material> map) {
        for (Map.Entry<Material, Material> entry : map.entrySet()) {
            if (criticalItems.contains(entry.getValue()) && endItems.contains(entry.getKey())) {
                swapBlockDrop(map, entry.getKey(), entry.getValue());
            }
        }
    }
    private void swapBlockDrop(Map<Material, Material> map, Material badSource, Material criticalItem) {
        for (Map.Entry<Material, Material> entry : map.entrySet()) {
            if (!endItems.contains(entry.getKey()) && !criticalItems.contains(entry.getValue())) {
                map.put(badSource, entry.getValue());
                map.put(entry.getKey(), criticalItem);
                return;
            }
        }
    }

    private void enforceMobSafety(Map<EntityType, EntityType> map) {
        for (Map.Entry<EntityType, EntityType> entry : map.entrySet()) {
            EntityType target = entry.getValue();
            if ((target == EntityType.BLAZE || target == EntityType.ENDERMAN) && hardMobs.contains(entry.getKey())) {
                swapMobLoot(map, entry.getKey(), target);
            }
        }
    }
    private void swapMobLoot(Map<EntityType, EntityType> map, EntityType badMob, EntityType criticalTarget) {
        for (Map.Entry<EntityType, EntityType> entry : map.entrySet()) {
            EntityType otherTarget = entry.getValue();
            if (!hardMobs.contains(entry.getKey()) && otherTarget != EntityType.BLAZE && otherTarget != EntityType.ENDERMAN) {
                map.put(badMob, otherTarget);
                map.put(entry.getKey(), criticalTarget);
                return;
            }
        }
    }

    private void enforceCraftingSafety(Map<Material, ItemStack> map) {
        for (Map.Entry<Material, ItemStack> entry : map.entrySet()) {
            if (criticalItems.contains(entry.getValue().getType()) && endItems.contains(entry.getKey())) {
                swapCraftingResult(map, entry.getKey(), entry.getValue());
            }
        }
    }
    private void swapCraftingResult(Map<Material, ItemStack> map, Material badRecipe, ItemStack criticalResult) {
        for (Map.Entry<Material, ItemStack> entry : map.entrySet()) {
            if (!endItems.contains(entry.getKey()) && !criticalItems.contains(entry.getValue().getType())) {
                map.put(badRecipe, entry.getValue());
                map.put(entry.getKey(), criticalResult);
                return;
            }
        }
    }

    private long getScopedSeed(Player p) {
        if (p == null || plugin.currentScope == RandomizerPlugin.Scope.GLOBAL) return currentSeed;
        return currentSeed + p.getUniqueId().hashCode();
    }

    public Material getRandomBlockDrop(Material original, Player p) {
        long seed = getScopedSeed(p);
        Map<Material, Material> map = (seed == currentSeed) ? blockDropMap :
                blockDropCache.computeIfAbsent(seed, s -> { Map<Material, Material> m = generateBlockMapping(s); enforceBlockSafety(m); return m; });
        return map.getOrDefault(original, original);
    }

    public EntityType getRandomMobType(EntityType original, Player p) {
        long seed = getScopedSeed(p);
        Map<EntityType, EntityType> map = (seed == currentSeed) ? mobMap :
                mobCache.computeIfAbsent(seed, s -> { Map<EntityType, EntityType> m = generateMobMapping(s); enforceMobSafety(m); return m; });
        return map.get(original);
    }

    public int getMobXp(EntityType type) {
        if (type == null) return 0;
        switch (type) {
            case ENDER_DRAGON: return 12000;
            case WITHER: return 50;
            case ELDER_GUARDIAN: case GUARDIAN: case BLAZE: case EVOKER: case VINDICATOR: case RAVAGER: case PIGLIN_BRUTE: return 10;
            case SLIME: case MAGMA_CUBE: return 4;
            case VILLAGER: case WANDERING_TRADER: case BAT: case IRON_GOLEM: case SNOW_GOLEM: return 0;
            case CHICKEN: case COW: case SHEEP: case PIG: case HORSE: case WOLF: case OCELOT: case CAT: case PARROT: case RABBIT:
            case LLAMA: case PANDA: case FOX: case BEE: case STRIDER: case MOOSHROOM: case TURTLE: case DOLPHIN: case POLAR_BEAR: return 2;
            default: return 5;
        }
    }


    public ItemStack getRandomCraftingResult(Material original, Player p) {
        long seed = getScopedSeed(p);
        Map<Material, ItemStack> map = (seed == currentSeed) ? craftingMap :
                craftingCache.computeIfAbsent(seed, s -> { Map<Material, ItemStack> m = generateCraftingMapping(s); enforceCraftingSafety(m); return m; });
        return map.getOrDefault(original, new ItemStack(original));
    }

    public Material getBlockSource(Material targetDrop, Player p) {
        long seed = getScopedSeed(p);
        Map<Material, Material> map = (seed == currentSeed) ? blockDropMap : blockDropCache.computeIfAbsent(seed, s -> { Map<Material, Material> m = generateBlockMapping(s); enforceBlockSafety(m); return m; });
        for (Map.Entry<Material, Material> entry : map.entrySet()) if (entry.getValue() == targetDrop) return entry.getKey();
        return null;
    }

    public Material getCraftingSource(Material targetResult, Player p) {
        long seed = getScopedSeed(p);
        Map<Material, ItemStack> map = (seed == currentSeed) ? craftingMap : craftingCache.computeIfAbsent(seed, s -> { Map<Material, ItemStack> m = generateCraftingMapping(s); enforceCraftingSafety(m); return m; });
        for (Map.Entry<Material, ItemStack> entry : map.entrySet()) if (entry.getValue().getType() == targetResult) return entry.getKey();
        return null;
    }

    public EntityType getMobSource(EntityType targetLoot, Player p) {
        long seed = getScopedSeed(p);
        Map<EntityType, EntityType> map = (seed == currentSeed) ? mobMap : mobCache.computeIfAbsent(seed, s -> { Map<EntityType, EntityType> m = generateMobMapping(s); enforceMobSafety(m); return m; });
        for (Map.Entry<EntityType, EntityType> entry : map.entrySet()) if (entry.getValue() == targetLoot) return entry.getKey();
        return null;
    }

    public long getSeed() { return currentSeed; }
    public List<Material> getAllValidItems() { return allValidItems; }
}