package com.graphitact.chatai.quest;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class QuestSystem implements Listener {
    private final JavaPlugin plugin;
    private final File missionsFile;
    private final Gson gson = new Gson();
    private final Map<UUID, Map<String, Quest>> activeQuests = new ConcurrentHashMap<>();

    public QuestSystem(JavaPlugin plugin) {
        this.plugin = plugin;
        this.missionsFile = new File(plugin.getDataFolder(), "missions.json");
        loadMissions();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public static class Quest {
        public enum Type { FETCH, KILL }
        public String id;
        public Type type;
        public String target;
        public int amount;
        public String locationHint;
        public String rewardCommand;
        public UUID giver;
        public UUID player;

        // the two new flags:
        public boolean requiresTurnIn = false;
        public boolean readyToTurnIn  = false;
    }

    public void addQuest(Quest q) {
        activeQuests
          .computeIfAbsent(q.player, k -> new LinkedHashMap<>())
          .put(q.id, q);
        saveMissions();

        Player p = Bukkit.getPlayer(q.player);
        if (p != null && p.isOnline()) {
            // Chat message
            p.sendMessage(ChatColor.AQUA + "New Quest: "
                + q.type + " " + q.amount + "× " + q.target
                + " near " + q.locationHint);
            // ActionBar notification
            p.spigot().sendMessage(
                ChatMessageType.ACTION_BAR,
                new TextComponent("§bNew Quest: §f" 
                    + q.type + " " + q.amount + "× " + q.target)
            );
        }
    }

    /** 
     * Return all quests assigned by this villager that are ready for turn-in. 
     */
    public List<Quest> getReadyTurnInQuests(UUID playerId, UUID giverId) {
        return activeQuests
          .getOrDefault(playerId, Collections.emptyMap())
          .values()
          .stream()
          .filter(q -> q.giver.equals(giverId) && q.readyToTurnIn)
          .toList();
    }

    /**
     * Complete a turn-in quest at the villager. 
     */
    public void completeQuest(Quest q, Player p) {
        // Reuse your existing completion logic
        complete(q, p);
    }

    private void loadMissions() {
        try {
            if (!missionsFile.exists()) return;
            String json = Files.readString(missionsFile.toPath());
            Type type = new TypeToken<Map<String, List<Quest>>>(){}.getType();
            Map<String, List<Quest>> data = gson.fromJson(json, type);
            data.forEach((pid, list) -> {
                UUID playerId = UUID.fromString(pid);
                Map<String, Quest> map = new LinkedHashMap<>();
                for (Quest q : list) map.put(q.id, q);
                activeQuests.put(playerId, map);
            });
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load missions: " + e.getMessage());
        }
    }

    private void saveMissions() {
        new BukkitRunnable() {
            public void run() {
                try {
                    Map<String, List<Quest>> out = new LinkedHashMap<>();
                    activeQuests.forEach((pid, map) ->
                       out.put(pid.toString(), new ArrayList<>(map.values()))
                    );
                    String json = gson.toJson(out);
                    Files.writeString(missionsFile.toPath(), json);
                } catch (Exception e) {
                    plugin.getLogger().severe("Failed to save missions: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        if (!(e.getEntity().getKiller() instanceof Player)) return;
        Player p = e.getEntity().getKiller();
        Map<String, Quest> quests = activeQuests.getOrDefault(p.getUniqueId(), Collections.emptyMap());
        for (Quest q : quests.values()) {
            if (q.type == Quest.Type.KILL
             && e.getEntityType().name().equalsIgnoreCase(q.target)) {
                q.amount--;
                if (q.amount > 0) {
                    // Progress ActionBar
                    p.spigot().sendMessage(
                        ChatMessageType.ACTION_BAR,
                        new TextComponent("§bQuest Progress: §f" 
                            + q.amount + "× " + q.target + " left")
                    );
                } else if (q.requiresTurnIn) {
                    q.readyToTurnIn = true;
                    p.spigot().sendMessage(
                        ChatMessageType.ACTION_BAR,
                        new TextComponent("§eReturn to quest giver to complete!")
                    );
                } else {
                    complete(q, p);
                }
                saveMissions();
                break;
            }
        }
    }

    @EventHandler
    public void onItemPickup(PlayerPickupItemEvent e) {
        Player p = e.getPlayer();
        Map<String, Quest> quests = activeQuests.getOrDefault(p.getUniqueId(), Collections.emptyMap());
        for (Quest q : quests.values()) {
            if (q.type == Quest.Type.FETCH
             && e.getItem().getItemStack().getType().name().equalsIgnoreCase(q.target)) {
                q.amount--;
                if (q.amount > 0) {
                    p.spigot().sendMessage(
                        ChatMessageType.ACTION_BAR,
                        new TextComponent("§bQuest Progress: §f" 
                            + q.amount + "× " + q.target + " left")
                    );
                } else if (q.requiresTurnIn) {
                    q.readyToTurnIn = true;
                    p.spigot().sendMessage(
                        ChatMessageType.ACTION_BAR,
                        new TextComponent("§eReturn to quest giver to complete!")
                    );
                } else {
                    complete(q, p);
                }
                saveMissions();
                break;
            }
        }
    }

    private void complete(Quest q, Player p) {
        String cmd = q.rewardCommand.replace("%player%", p.getName());
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        p.spigot().sendMessage(
            ChatMessageType.ACTION_BAR,
            new TextComponent("§aQuest complete! §fYou earned your reward.")
        );
        p.sendMessage(ChatColor.GOLD + "Quest complete! You earned your reward.");
        Map<String, Quest> map = activeQuests.get(p.getUniqueId());
        if (map != null) map.remove(q.id);
        saveMissions();
    }
}
