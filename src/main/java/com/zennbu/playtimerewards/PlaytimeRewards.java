
package com.zennbu.playtimerewards;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class PlaytimeRewards extends JavaPlugin implements Listener {

    private Economy economy;
    private File dataFile;
    private YamlConfiguration data;

    private static final String ADMIN_PERMISSION =
            "playtimerewards.admin";

    @Override
    public void onEnable() {

        saveDefaultConfig();
        loadPlayerData();

        if (!setupEconomy()) {
            getLogger().severe("Vault economy not found!");
            getLogger().severe("Install Vault and an economy provider.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("PlaytimeRewards enabled!");
        getLogger().info("Version: " + getDescription().getVersion());
    }

    @Override
    public void onDisable() {
        savePlayerData();
        getLogger().info("PlaytimeRewards disabled!");
    }

    private boolean setupEconomy() {

        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }

        RegisteredServiceProvider<Economy> registration =
                getServer().getServicesManager()
                        .getRegistration(Economy.class);

        if (registration == null) {
            return false;
        }

        economy = registration.getProvider();
        return economy != null;
    }

    private void loadPlayerData() {

        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        dataFile = new File(getDataFolder(), "data.yml");

        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Could not create data.yml!");
            }
        }

        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void savePlayerData() {

        if (data == null || dataFile == null) {
            return;
        }

        try {
            data.save(dataFile);
        } catch (IOException e) {
            getLogger().severe("Could not save player data!");
            e.printStackTrace();
        }
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private long getPlaytimeMinutes(Player player) {

        int ticks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);

        return ticks / 1200L;
    }

    private String formatTime(long minutes) {

        long hours = minutes / 60;
        long remainingMinutes = minutes % 60;

        return hours + "h " + remainingMinutes + "m";
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {

        if (command.getName().equalsIgnoreCase("playtime")) {

            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can use this command!");
                return true;
            }

            long minutes = getPlaytimeMinutes(player);

            player.sendMessage(color(
                    "&8[&dPlaytimeRewards&8] &fYour playtime: &a"
                            + formatTime(minutes)
            ));

            return true;
        }

        if (command.getName().equalsIgnoreCase("playtimerewards")) {

            if (!(sender instanceof Player player)) {

                sender.sendMessage("Only players can use this command!");
                return true;
            }

            if (args.length > 0 &&
                    args[0].equalsIgnoreCase("reload")) {

                if (!player.hasPermission(ADMIN_PERMISSION)) {
                    player.sendMessage(color(
                            "&cYou don't have permission!"
                    ));
                    return true;
                }

                reloadConfig();

                player.sendMessage(color(
                        "&aPlaytimeRewards configuration reloaded!"
                ));

                return true;
            }

            openRewardsGUI(player);
            return true;
        }

        return false;
    }

    private void openRewardsGUI(Player player) {

        int size = getConfig().getInt("gui.size", 27);
        size = Math.max(9, Math.min(54, ((size + 8) / 9) * 9));

        String title = color(
                getConfig().getString(
                        "gui.title",
                        "&8Playtime Rewards"
                )
        );

        RewardsHolder holder = new RewardsHolder();

        Inventory inventory = Bukkit.createInventory(
                holder,
                size,
                title
        );

        holder.setInventory(inventory);

        ConfigurationSection rewards =
                getConfig().getConfigurationSection("rewards");

        if (rewards != null) {

            for (String id : rewards.getKeys(false)) {

                String path = "rewards." + id;

                int slot = getConfig().getInt(path + ".slot", 0);

                if (slot < 0 || slot >= size) {
                    continue;
                }

                long required = getConfig().getLong(
                        path + ".required-minutes", 0
                );

                double money = getConfig().getDouble(
                        path + ".money", 0
                );

                boolean claimed = data.getBoolean(
                        "claimed." + player.getUniqueId() + "." + id
                );

                ItemStack item = new ItemStack(
                        claimed ? Material.MINECART : Material.CHEST
                );

                ItemMeta meta = item.getItemMeta();

                if (meta != null) {

                    meta.setDisplayName(color(
                            getConfig().getString(
                                    path + ".display-name",
                                    "&a" + id
                            )
                    ));

                    List<String> lore = new ArrayList<>();

                    lore.add(color("&7Required: &f" +
                            formatTime(required)));

                    lore.add(color("&7Reward: &6$" + money));

                    lore.add("");

                    if (claimed) {
                        lore.add(color("&cAlready claimed!"));
                    } else if (getPlaytimeMinutes(player) >= required) {
                        lore.add(color("&aClick to claim!"));
                    } else {
                        lore.add(color("&cNot unlocked yet!"));
                    }

                    meta.setLore(lore);
                    item.setItemMeta(meta);
                }

                inventory.setItem(slot, item);
            }
        }

        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {

        if (!(event.getView().getTopInventory().getHolder()
                instanceof RewardsHolder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getRawSlot();

        if (slot < 0 ||
                slot >= event.getView().getTopInventory().getSize()) {
            return;
        }

        ConfigurationSection rewards =
                getConfig().getConfigurationSection("rewards");

        if (rewards == null) {
            return;
        }

        for (String id : rewards.getKeys(false)) {

            String path = "rewards." + id;

            if (getConfig().getInt(path + ".slot", -1) != slot) {
                continue;
            }

            claimReward(player, id);
            return;
        }
    }

    private void claimReward(Player player, String id) {

        String path = "rewards." + id;

        String claimPath =
                "claimed." + player.getUniqueId() + "." + id;

        if (data.getBoolean(claimPath)) {

            player.sendMessage(color(
                    "&cYou already claimed this reward!"
            ));

            return;
        }

        long required = getConfig().getLong(
                path + ".required-minutes", 0
        );

        if (getPlaytimeMinutes(player) < required) {

            player.sendMessage(color(
                    getConfig().getString(
                            "messages.not-enough-time",
                            "&cYou haven't played enough!"
                    )
            ));

            return;
        }

        double money = getConfig().getDouble(
                path + ".money", 0
        );

        if (money < 0) {
            player.sendMessage(color("&cInvalid reward amount!"));
            return;
        }

        if (money > 0) {

            if (economy == null) {
                player.sendMessage(color(
                        "&cEconomy is currently unavailable!"
                ));
                return;
            }

            if (!economy.depositPlayer(player, money).transactionSuccess()) {
                player.sendMessage(color(
                        "&cCould not deposit your reward!"
                ));
                return;
            }
        }

        data.set(claimPath, true);
        savePlayerData();

        String message = getConfig().getString(
                path + ".message",
                "&aReward claimed successfully!"
        );

        player.sendMessage(color(message));

        player.closeInventory();
        openRewardsGUI(player);
    }

    private static class RewardsHolder implements InventoryHolder {

        private Inventory inventory;

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
              }
          
