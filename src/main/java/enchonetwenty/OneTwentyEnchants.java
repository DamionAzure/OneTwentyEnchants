package enchonetwenty;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.event.world.LootGenerateEvent;

import java.util.*;
import java.util.stream.Collectors;

public class OneTwentyEnchants extends JavaPlugin implements Listener, CommandExecutor {

    private final Map<String, NamespacedKey> keys = new HashMap<>();
    private NamespacedKey heartModifierKey;
    private NamespacedKey heartBoostKey;
    private final Random random = new Random();

    // Compatibility Groups
    private final List<String> SWORD_ENCHANTS = Arrays.asList("heartsteal", "corpse_explosion", "frosted", "skulk_harvester", "lifesteal", "beheading", "midas", "blessing_of_midas", "static_charge", "solar_flare");
    private final List<String> ARMOR_ENCHANTS = Arrays.asList("life_fountain", "blast_proof", "deflection", "juggernaut", "heartboost", "molten", "photosynthesis");
    private final List<String> LEGGING_ENCHANTS = Arrays.asList("auto_kick");
    private final List<String> HELMET_ENCHANTS = Arrays.asList("dreboost", "night_owl", "thunder_rod");
    private final List<String> BOOT_ENCHANTS = Arrays.asList("zephyr", "hermes_blessed", "void_step", "skater", "cloud_walker");
    private final List<String> PICKAXE_ENCHANTS = Arrays.asList("frozen_pick", "alchemy", "diamond_rough");
    private final List<String> BOW_ENCHANTS = Arrays.asList("overtuned", "heavy_arrows", "laced_arrows", "death_lace", "boomstick", "windy");
    private final List<String> ELYTRA_ENCHANTS = Arrays.asList("avian", "lurkers_aegis", "dimensional_drift", "thermal_lift");
    private final List<String> TRIDENT_ENCHANTS = Arrays.asList("empyrian", "avyzziu", "ahab");
    private final List<String> MACE_ENCHANTS = Arrays.asList("mace_spray", "impact", "crushing_gravity");
    private final List<String> SHIELD_ENCHANTS = Arrays.asList("back_off", "diffuse");
    private final List<String> ROD_ENCHANTS = Arrays.asList("magnetic_hook");

    @Override
    public void onEnable() {
        String[] allEnchants = {
                "heartsteal", "corpse_explosion", "frosted", "auto_kick", "skulk_harvester",
                "life_fountain", "blast_proof", "diffuse", "back_off", "deflection",
                "juggernaut", "heartboost", "dreboost", "zephyr", "hermes_blessed",
                "reinforced", "frozen_pick", "alchemy", "diamond_rough", "overtuned",
                "midas", "blessing_of_midas", "shear_luck",
                "heavy_arrows", "laced_arrows",
                "death_lace", "boomstick", "windy", "magnetic_hook", "lurkers_aegis",
                "dimensional_drift", "lifesteal", "beheading", "molten", "void_step",
                "ahab", "empyrian", "avyzziu", "mace_spray", "impact", "avian", "skater"
        };

        for (String name : allEnchants) {
            keys.put(name, new NamespacedKey(this, name));
        }
        heartModifierKey = new NamespacedKey(this, "heartsteal_hp");
        heartBoostKey = new NamespacedKey(this, "heartboost_hp");

        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("giveenchant") != null) {
            getCommand("giveenchant").setExecutor(this);
        }

        // 1. LIGHTWEIGHT TICK TASK (Every 1 Tick)
        // Only for things that MUST be smooth, like movement/velocity.
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                handleMovementEnchants(p);
            }
        }, 0L, 1L);

        // 2. HEAVY TICK TASK (Every 20 Ticks / 1 Second)
        // For attributes, healing, potion effects, and sky-light checks.
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                handleTickingEnchants(p);
            }
        }, 0L, 20L);
    }

    private void handleMovementEnchants(Player p) {
        ItemStack boots = p.getInventory().getBoots();
        if (boots != null && hasData(boots, "skater")) {
            // Only run the logic if the player is actually moving on ice
            if (p.getLocation().getBlock().getRelative(0, -1, 0).getType().name().contains("ICE")) {
                // Check if player is actually moving (prevents sliding while standing still)
                if (p.getVelocity().lengthSquared() > 0.01) {
                    p.setVelocity(p.getVelocity().multiply(1.05));
                }
            }
        }
    }

    private void handleTickingEnchants(Player p) {
        int fountainLevel = 0;
        boolean hasHermes = false;

        // 1. Check Hand Items (Trident logic)
        ItemStack mainHand = p.getInventory().getItemInMainHand();
        ItemStack offHand = p.getInventory().getItemInOffHand();

        for (ItemStack handItem : new ItemStack[]{mainHand, offHand}) {
            if (handItem != null && handItem.getType() == Material.TRIDENT && hasData(handItem, "avyzziu")) {

                // 1. Existing Potion Effects
                p.addPotionEffect(new PotionEffect(PotionEffectType.CONDUIT_POWER, 60, 0, false, false));
                p.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 60, 0, false, false));

                // 2. Rapid Repair in Water
                if (p.isInWater() || p.getWorld().hasStorm()) {
                    damageItem(p, handItem, -2); // Repairs 2 durability per second

                    // Optional: Small visual cue that it's repairing
                    if (random.nextDouble() < 0.2) {
                        p.getWorld().spawnParticle(Particle.WAX_OFF, p.getLocation().add(0, 1, 0), 3, 0.3, 0.3, 0.3, 0.1);
                    }
                }
            }
        }

        // 2. Loop through all armor once to handle multi-piece and individual enchants
        for (ItemStack armor : p.getInventory().getArmorContents()) {
            if (armor == null || armor.getType() == Material.AIR) continue;

            // Cumulative/Global Armor Checks
            if (hasData(armor, "life_fountain")) fountainLevel++;
            if (hasData(armor, "hermes_blessed")) hasHermes = true;
            if (hasData(armor, "heartboost")) applyHeartBoost(p);
            if (hasData(armor, "void_step")) p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 0, false, false));

            // Photosynthesis (Moved inside the loop so 'armor' is valid)
            if (hasData(armor, "photosynthesis") && p.getWorld().getTime() < 12000) {
                if (p.getLocation().getBlock().getLightFromSky() > 13) {
                    damageItem(p, armor, -1); // Repair
                    double maxH = p.getAttribute(Attribute.MAX_HEALTH).getValue();
                    p.setHealth(Math.min(p.getHealth() + 0.1, maxH));
                }
            }

            if (armor.getType().name().contains("HELMET") && hasData(armor, "night_owl")) {
                long time = p.getWorld().getTime();
                if (time > 13000 && time < 23000) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 300, 0, false, false));
                }
            }
        }

        // 3. Post-Loop Calculations (Things that depend on the total count/state)
        if (fountainLevel > 0) {
            double limit = p.getAttribute(Attribute.MAX_HEALTH).getValue();
            if (p.getHealth() < limit) {
                p.setHealth(Math.min(p.getHealth() + (fountainLevel * 0.5), limit)); // Balanced healing
            }
        }

        if (hasHermes) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 0, false, false));
            p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 40, 0, false, false));
        }
    }

    // Helper to keep the main logic clean
    private boolean isCustomTrident(ItemStack item, String key) {
        return item != null && item.getType() == Material.TRIDENT && hasData(item, key);
    }

    @EventHandler
    public void onEnchant(EnchantItemEvent event) {
        ItemStack item = event.getItem();
        List<String> validEnchants;
        if (item.getType() == Material.BOOK) {
            // Collect all possible custom enchantments for books
            validEnchants = new ArrayList<>(keys.keySet());
        } else {
            validEnchants = getValidEnchantsForItem(item.getType());
        }

        if (validEnchants.isEmpty()) return;

        if (random.nextDouble() < 0.15) {
            Bukkit.getScheduler().runTask(this, () -> {
                ItemMeta meta = item.getItemMeta();
                if (meta == null) return;
                String choice = validEnchants.get(random.nextInt(validEnchants.size()));
                meta.getPersistentDataContainer().set(keys.get(choice), PersistentDataType.BYTE, (byte) 1);
                List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
                lore.add(ChatColor.DARK_PURPLE + formatName(choice) + " I");
                meta.setLore(lore);

                if (choice.equals("avyzziu")) {
                    // Force Riptide III even if it didn't roll naturally
                    meta.addEnchant(Enchantment.RIPTIDE, 3, true);

                    // Remove conflicting enchants (Loyalty/Channeling) if the table added them
                    meta.removeEnchant(Enchantment.LOYALTY);
                }

                if (meta.getEnchants().isEmpty()) {
                    meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                }
                item.setItemMeta(meta);
                event.getEnchanter().sendMessage(ChatColor.DARK_PURPLE + "" + ChatColor.ITALIC + "Yog-Sothoth has blessed your tool...");
            });
        }
    }

    @EventHandler
    public void onWindyChargeHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof WindCharge charge)) return;
        // Only trigger if this was a "Windy" bow shot
        if (!charge.getPersistentDataContainer().has(keys.get("windy"), PersistentDataType.BYTE)) return;

        Entity hit = event.getHitEntity();
        if (hit instanceof LivingEntity victim) {
            // Calculate a powerful knockback vector (2.5 multiplier for "Max Punch" feel)
            Vector direction = charge.getVelocity().normalize().multiply(6.5).setY(0.5);
            victim.setVelocity(direction);

            // Visual and sound feedback
            victim.getWorld().spawnParticle(Particle.EXPLOSION, victim.getLocation(), 1);
            victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.5f, 0.8f);
        }
    }

    @EventHandler
    public void onEntityHit(EntityDamageByEntityEvent event) {
        Player attacker = null;

        // Check if the damager is a player (melee)
        if (event.getDamager() instanceof Player) {
            attacker = (Player) event.getDamager();
        }
        // Check if the damager is a projectile (arrow/trident) shot by a player
        else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player shooter) {
            attacker = shooter;
        }

        // If no player was involved, stop
        if (attacker == null) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        // Use a blank itemstack if air to avoid null issues in hasData()
        if (weapon == null || weapon.getType() == Material.AIR) weapon = new ItemStack(Material.AIR);

        // LIFESTEAL
        if (hasData(weapon, "lifesteal") && random.nextDouble() < 0.15) {
            double maxHealth = attacker.getAttribute(Attribute.MAX_HEALTH).getValue();
            double heal = Math.min(attacker.getHealth() + 2.0, maxHealth);
            attacker.setHealth(heal);
            attacker.getWorld().spawnParticle(Particle.HEART, attacker.getLocation().add(0, 1, 0), 2);
        }

        // CRUSHING GRAVITY
        if (hasData(weapon, "crushing_gravity")) {
            for (Entity nearby : victim.getNearbyEntities(5, 5, 5)) {
                if (nearby instanceof LivingEntity target && target != attacker && target != victim) {
                    Vector pull = victim.getLocation().toVector().subtract(target.getLocation().toVector()).normalize().multiply(0.8);
                    target.setVelocity(pull);
                }
            }
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 4));
        }

        // AUTO KICK (Fixed 'p' to 'attacker' and added null check for leggings)
        ItemStack leggings = attacker.getInventory().getLeggings();
        if (leggings != null && hasData(leggings, "auto_kick")) {
            victim.setVelocity(attacker.getLocation().getDirection().multiply(1.5).setY(0.4));
            attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.5f, 1.5f);
        }

        // STATIC CHARGE
        if (hasData(weapon, "static_charge") && random.nextDouble() < 0.20) {
            victim.getWorld().strikeLightningEffect(victim.getLocation());
            for (Entity nearby : victim.getNearbyEntities(3, 3, 3)) {
                if (nearby instanceof LivingEntity target && target != attacker) {
                    target.damage(4.0, attacker);
                }
            }
        }

        // SOLAR FLARE
        if (hasData(weapon, "solar_flare")) {
            victim.setFireTicks(100);
            victim.getWorld().spawnParticle(Particle.FLAME, victim.getLocation(), 20, 0.5, 0.5, 0.5, 0.1);
            if (random.nextDouble() < 0.10) {
                victim.getWorld().createExplosion(victim.getLocation(), 1.0f, false, false);
            }
        }

        // MIDAS
        if (hasData(weapon, "midas") && random.nextDouble() < 0.15) {
            victim.getWorld().dropItemNaturally(victim.getLocation(), new ItemStack(Material.GOLD_NUGGET));
            victim.getWorld().playSound(victim.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 2f);
        }

        // MACE LOGIC
        if (weapon.getType() == Material.MACE) {
            if (hasData(weapon, "mace_spray")) {
                for (Entity nearby : victim.getNearbyEntities(3, 3, 3)) {
                    if (nearby instanceof LivingEntity target && target != attacker && target != victim) {
                        target.damage(event.getDamage() * 0.5, attacker);
                        target.setVelocity(attacker.getLocation().getDirection().multiply(0.5));
                    }
                }
            }
            if (hasData(weapon, "impact") && attacker.getFallDistance() > 1.5) {
                for (Entity e : attacker.getNearbyEntities(4, 4, 4)) {
                    if (e instanceof LivingEntity le && le != attacker) {
                        le.setVelocity(le.getVelocity().add(new Vector(0, 0.6, 0)));
                    }
                }
            }
        }

        // MISC
        if (hasData(weapon, "skulk_harvester") && victim.getType() == EntityType.WARDEN) event.setDamage(event.getDamage() * 5);
        if (hasData(weapon, "frosted")) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 0));
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1));
        }

        // SHIELD BLOCK LOGIC (Fixed 'p' to 'attacker')
        if (attacker.isBlocking()) {
            ItemStack shield = attacker.getInventory().getItemInOffHand();
            if (shield != null && hasData(shield, "back_off")) {
                // Note: In an 'EntityDamageByEntityEvent', the attacker is the one hitting the shield.
                victim.setVelocity(attacker.getLocation().getDirection().multiply(1.8).setY(0.4));
            }
        }

        // RANGED LOGIC
        if (weapon.getType() == Material.BOW || weapon.getType() == Material.CROSSBOW) {
            if (hasData(weapon, "overtuned")) event.setDamage(event.getDamage() * 1.5);
            if (hasData(weapon, "laced_arrows")) victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 1));
            if (hasData(weapon, "death_lace")) {
                victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 1));
                victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 160, 2));
            }
            if (hasData(weapon, "heavy_arrows")) victim.setVelocity(victim.getVelocity().add(new Vector(0, -0.5, 0)));
        }
    }

    @EventHandler
    public void onMagneticHook(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_ENTITY) {
            ItemStack rod = event.getPlayer().getInventory().getItemInMainHand();
            if (hasData(rod, "magnetic_hook")) {
                Entity caught = event.getCaught();
                if (caught != null) {
                    Vector pull = event.getPlayer().getLocation().toVector().subtract(caught.getLocation().toVector()).normalize().multiply(1.5);
                    caught.setVelocity(pull);
                }
            }
        }
    }

    @EventHandler
    public void onShear(PlayerShearEntityEvent event) {
        ItemStack shears = event.getItem();
        if (hasData(shears, "shear_luck") && random.nextDouble() < 0.40) {
            Entity victim = event.getEntity();
            // Drop an extra item manually based on context
            ItemStack extra = new ItemStack(Material.WHITE_WOOL); // Simplified
            victim.getWorld().dropItemNaturally(victim.getLocation(), extra);
        }
    }

    @EventHandler
    public void onAhabLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof Trident trident && trident.getShooter() instanceof Player player) {
            ItemStack item = trident.getItemStack();

            if (hasData(item, "ahab")) {
                trident.setVelocity(trident.getVelocity().multiply(1.55));

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (trident.isDead() || trident.isOnGround() || !trident.isValid()) {
                            this.cancel();
                            return;
                        }
                        Location loc = trident.getLocation();
                        trident.getWorld().spawnParticle(Particle.BUBBLE, loc, 2, 0.05, 0.05, 0.05, 0.01);
                        trident.getWorld().spawnParticle(Particle.CLOUD, loc, 1, 0, 0, 0, 0.02);

                        if (loc.getBlock().isLiquid()) {
                            trident.getWorld().spawnParticle(Particle.BUBBLE_POP, loc, 1, 0.1, 0.1, 0.1, 0.02);
                        }
                    }
                }.runTaskTimer(this, 0L, 1L);
            }
        }
    }

    @EventHandler
    public void onAhabHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        ItemStack weapon = null;

        // Check if damage came from a thrown trident or a melee hit
        if (event.getDamager() instanceof Trident trident) {
            weapon = trident.getItemStack();
        } else if (event.getDamager() instanceof Player player) {
            weapon = player.getInventory().getItemInMainHand();
        }

        if (weapon != null && hasData(weapon, "ahab")) {
            if (isAquatic(victim.getType())) {
                event.setDamage(event.getDamage() * 3.0);
                victim.getWorld().spawnParticle(Particle.BUBBLE, victim.getLocation().add(0, 1, 0), 10);
            }
        }
    }

    @EventHandler
    public void onBoomstickHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow)) return;
        if (!(arrow.getShooter() instanceof Player p)) return;

        // Use the item that shot the arrow (check both hands for bows/crossbows)
        ItemStack main = p.getInventory().getItemInMainHand();
        if (hasData(main, "boomstick")) {
            // Get location regardless of hitting block or entity
            Location loc = event.getHitEntity() != null ? event.getHitEntity().getLocation() :
                    (event.getHitBlock() != null ? event.getHitBlock().getLocation() : null);

            if (loc != null) {
                loc.getWorld().createExplosion(loc, 3.0f, false, false);
                arrow.remove();
            }
        }
    }

    @EventHandler
    public void onTridentProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Trident trident)) return;
        if (!(trident.getShooter() instanceof Player p)) return;

        ItemStack item = trident.getItemStack();

        if (hasData(item, "empyrian")) {
            Location loc = event.getHitEntity() != null ? event.getHitEntity().getLocation() :
                    (event.getHitBlock() != null ? event.getHitBlock().getLocation() : null);

            if (loc != null) {
                // Cross pattern lightning
                for (int i = 0; i < 4; i++) {
                    double angle = i * Math.PI / 2;
                    loc.getWorld().strikeLightning(loc.clone().add(Math.cos(angle) * 2, 0, Math.sin(angle) * 2));
                }
            }
        }
    }

    @EventHandler
    public void onAvianFlap(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        ItemStack chest = p.getInventory().getChestplate();
        ItemStack helmet = p.getInventory().getHelmet();

        if (p.isGliding() && (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_AIR)) {
            if (hasData(chest, "avian")) {
                Vector boost = p.getLocation().getDirection().multiply(0.3).setY(0.4);
                p.setVelocity(p.getVelocity().add(boost));
                p.setExhaustion(p.getExhaustion() + 3.0f);
                damageItem(p, chest, 2);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1f, 1.6f);
            }
        }
        if (hasData(chest, "thermal_lift") && p.getLocation().getPitch() < -45) {
            p.setVelocity(p.getVelocity().add(new Vector(0, 0.6, 0)));
            p.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, p.getLocation(), 10);
        }

        if (hasData(chest, "dimensional_drift") && p.isSneaking()) {
            Location target = p.getLocation().add(p.getLocation().getDirection().multiply(8));
            p.teleport(target);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
        }
    }

    @EventHandler
    public void onWindyShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        if (hasData(event.getBow(), "windy")) {
            event.setCancelled(true);
            WindCharge charge = p.launchProjectile(WindCharge.class);
            charge.setVelocity(event.getProjectile().getVelocity());
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WIND_CHARGE_THROW, 1f, 1.2f);
            damageItem(p, event.getBow(), 1);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        ItemStack tool = p.getInventory().getItemInMainHand();
        Block b = event.getBlock();
        Material Mat = b.getType();

        if (hasData(tool, "frozen_pick")) {
            if (b.getType() == Material.ICE) {
                event.setDropItems(false);
                b.getWorld().dropItemNaturally(b.getLocation(), new ItemStack(Material.PACKED_ICE));
            } else if (b.getType() == Material.PACKED_ICE) {
                event.setDropItems(false);
                b.getWorld().dropItemNaturally(b.getLocation(), new ItemStack(Material.BLUE_ICE));
            }
        }
        if (hasData(tool, "alchemy") && b.getType() == Material.IRON_ORE) {
            if (random.nextDouble() < 0.20) {
                event.setDropItems(false);
                b.getWorld().dropItemNaturally(b.getLocation(), new ItemStack(Material.RAW_GOLD));
                p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, b.getLocation(), 5);
            }
        }
        if (tool.getType().name().contains("PICKAXE") && hasData(tool, "diamond_rough")) {
            double chance = 0.0;
            if (Mat == Material.STONE || Mat == Material.DEEPSLATE) {
                chance = 0.005; // 0.5% for stone
            } else if (Mat == Material.IRON_ORE || Mat == Material.DEEPSLATE_IRON_ORE || Mat == Material.COAL_ORE || Mat == Material.DEEPSLATE_COAL_ORE) {
                chance = 0.025; // 2% for ores
            }

            if (chance > 0 && random.nextDouble() < chance) {
                b.getWorld().dropItemNaturally(b.getLocation(), new ItemStack(Material.DIAMOND));
                p.getWorld().playSound(b.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
                p.getWorld().spawnParticle(Particle.WAX_OFF, b.getLocation().add(0.5, 0.5, 0.5), 20);
            }
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        ItemStack chest = p.getInventory().getChestplate();
        ItemStack helmet = p.getInventory().getHelmet();

        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && hasData(p.getInventory().getBoots(), "zephyr")) {
            event.setCancelled(true);
            damageItem(p, p.getInventory().getBoots(), 3);
        }
        if (hasData(chest, "deflection") && random.nextDouble() < 0.07) event.setCancelled(true);
        if (hasData(chest, "juggernaut")) event.setDamage(event.getDamage() * 0.30);

        if (hasData(helmet, "thunder_rod") &&  event instanceof EntityDamageByEntityEvent edee) {
            if (edee.getDamager() instanceof LivingEntity attacker) {
                attacker.getWorld().strikeLightning(attacker.getLocation());
            }
        }

        if (hasData(chest, "molten") && event instanceof EntityDamageByEntityEvent edee) {
            if (edee.getDamager() instanceof LivingEntity attacker) {
                attacker.setFireTicks(60);
            }
        }
    }

    @EventHandler
    public void onLootGenerate(LootGenerateEvent event) {
        for (ItemStack item : event.getLoot()) {
            if (item == null || item.getType() == Material.AIR) continue;
            if (item.getType() == Material.ELYTRA || item.getType() == Material.SHIELD) {
                if (random.nextDouble() < 1.00) {
                    applyRandomCustomEnchant(item);
                }
            }
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        LivingEntity victim = event.getEntity();
        Player attacker = victim.getKiller();
        if (attacker == null) return;
        ItemStack weapon = attacker.getInventory().getItemInMainHand();

        if (hasData(weapon, "heartsteal")) applyHeartsteal(attacker);
        if (hasData(weapon, "corpse_explosion")) {
            victim.getWorld().createExplosion(victim.getLocation(), 2.0f, false, false);
        }

        if (hasData(weapon, "blessing_of_midas")) {
            AttributeInstance maxHealth = victim.getAttribute(Attribute.MAX_HEALTH);
            int amount = (int) ((maxHealth != null ? maxHealth.getValue() : 20.0) / 5);
            victim.getWorld().dropItemNaturally(victim.getLocation(), new ItemStack(Material.GOLD_NUGGET, Math.max(6, amount)));
        }

        if (hasData(weapon, "beheading") && random.nextDouble() < 0.10) {
            Material headType = switch (victim.getType()) {
                case ZOMBIE -> Material.ZOMBIE_HEAD;
                case SKELETON -> Material.SKELETON_SKULL;
                case CREEPER -> Material.CREEPER_HEAD;
                case PIGLIN -> Material.PIGLIN_HEAD;
                case PLAYER -> Material.PLAYER_HEAD;
                default -> null;
            };
            if (headType != null) {
                victim.getWorld().dropItemNaturally(victim.getLocation(), new ItemStack(headType));
            }
        }

    }

    // --- HELPER METHODS ---

    private void applyRandomCustomEnchant(ItemStack item) {
        List<String> validEnchants = getValidEnchantsForItem(item.getType());
        if (validEnchants.isEmpty()) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        String choice = validEnchants.get(random.nextInt(validEnchants.size()));

        // Set Data
        meta.getPersistentDataContainer().set(keys.get(choice), PersistentDataType.BYTE, (byte) 1);

        // Add Lore
        List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
        lore.add(ChatColor.DARK_PURPLE + formatName(choice) + " I");
        meta.setLore(lore);

        // Visual glow (Add a dummy enchant if it has none)
        if (meta.getEnchants().isEmpty()) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        item.setItemMeta(meta);
    }

    private boolean isAquatic(EntityType type) {
        return type == EntityType.GUARDIAN || type == EntityType.ELDER_GUARDIAN ||
                type == EntityType.DROWNED || type == EntityType.SQUID ||
                type == EntityType.GLOW_SQUID || type == EntityType.COD ||
                type == EntityType.SALMON || type == EntityType.PUFFERFISH ||
                type == EntityType.TROPICAL_FISH || type == EntityType.DOLPHIN;
    }

    private boolean hasData(ItemStack item, String key) {
        if (item == null || !item.hasItemMeta() || !keys.containsKey(key)) return false;
        return item.getItemMeta().getPersistentDataContainer().has(keys.get(key), PersistentDataType.BYTE);
    }

    private void damageItem(Player p, ItemStack item, int amount) {
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable d) {
            d.setDamage(d.getDamage() + amount);
            item.setItemMeta(meta);
        }
    }

    private String formatName(String raw) {
        return Arrays.stream(raw.split("_"))
                .map(s -> s.substring(0, 1).toUpperCase() + s.substring(1))
                .collect(Collectors.joining(" "));
    }

    private void applyHeartBoost(Player p) {
        AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null && hp.getModifiers().stream().noneMatch(m -> m.getName().equals("heartboost_hp"))) {
            hp.addModifier(new AttributeModifier(UUID.randomUUID(), "heartboost_hp", 2.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.CHEST));
        }
    }

    private void applyHeartsteal(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            double currentBonus = maxHealth.getModifiers().stream()
                    .filter(m -> m.getName().equals("heartsteal_hp"))
                    .mapToDouble(AttributeModifier::getAmount).sum();

            maxHealth.getModifiers().stream().filter(m -> m.getName().equals("heartsteal_hp"))
                    .collect(Collectors.toList()).forEach(maxHealth::removeModifier);

            maxHealth.addModifier(new AttributeModifier(UUID.randomUUID(), "heartsteal_hp", currentBonus + 2.0, AttributeModifier.Operation.ADD_NUMBER));
            player.setHealth(Math.min(player.getHealth() + 2.0, maxHealth.getValue()));
        }
    }

    private List<String> getValidEnchantsForItem(Material mat) {
        String name = mat.name();
        List<String> valid = new ArrayList<>();
        if (name.contains("SWORD") || name.contains("_AXE")) valid.addAll(SWORD_ENCHANTS);
        if (name.contains("_CHESTPLATE")) valid.addAll(ARMOR_ENCHANTS);
        if (name.contains("_HELMET")) valid.addAll(HELMET_ENCHANTS);
        if (name.contains("_LEGGINGS")) valid.addAll(LEGGING_ENCHANTS);
        if (name.contains("_BOOTS")) valid.addAll(BOOT_ENCHANTS);
        if (name.contains("PICKAXE")) valid.addAll(PICKAXE_ENCHANTS);
        if (name.contains("BOW") || name.contains("CROSSBOW")) valid.addAll(BOW_ENCHANTS);
        if (mat == Material.TRIDENT) valid.addAll(TRIDENT_ENCHANTS);
        if (mat == Material.MACE) valid.addAll(MACE_ENCHANTS);
        if (mat == Material.ELYTRA) {
            valid.addAll(ELYTRA_ENCHANTS);
        }

        if (mat == Material.SHIELD) {
            valid.addAll(SHIELD_ENCHANTS);
        }
        return valid;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player) || args.length < 1) return false;
        ItemStack item = player.getInventory().getItemInMainHand();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return true;
        String type = args[0].toLowerCase();
        if (keys.containsKey(type)) {
            meta.getPersistentDataContainer().set(keys.get(type), PersistentDataType.BYTE, (byte) 1);
            List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
            lore.add(ChatColor.GRAY + formatName(type) + " I");
            meta.setLore(lore);
            item.setItemMeta(meta);
            player.sendMessage(ChatColor.GREEN + "Added " + type);
        }
        return true;
    }
}