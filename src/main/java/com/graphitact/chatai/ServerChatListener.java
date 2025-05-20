package com.graphitact.chatai;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Deque;
import java.util.LinkedList;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ServerChatListener implements Listener {
    private static final UUID SERVER_ID = new UUID(0L,0L);

    private final JavaPlugin plugin;
    private final AIClient   ai;
    private final ConcurrentHashMap<UUID,Deque<String>> history = new ConcurrentHashMap<>();

    public ServerChatListener(JavaPlugin plugin, AIClient ai) {
        this.plugin = plugin;
        this.ai     = ai;
        history.putIfAbsent(SERVER_ID, new LinkedList<>());
    }

    private Deque<String> serverHistory() {
        return history.computeIfAbsent(SERVER_ID, k -> new LinkedList<>());
    }

    private void sendEventPrompt(UUID id, Deque<String> h, String prompt) {
        if (h.size() >= 20) h.removeFirst();
        h.addLast("System: " + prompt);

        Bukkit.getScheduler().runTask(plugin, () ->
            Bukkit.broadcastMessage(ChatColor.GRAY + "[AI] Thinking...")
        );
        ai.ask(id, h, prompt, reply -> {
            if (reply.isBlank()) return;
            String filled = ChatAIPlugin.processPlaceholders(reply);
            Bukkit.broadcastMessage(ChatColor.YELLOW + "[AI] " + filled);
            h.addLast("Assistant: " + reply);
            if (h.size() > 20) h.removeFirst();
        });
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent evt) {
        String prompt = "Write a short, unique welcome for player "
                      + evt.getPlayer().getName()
                      + ". Keep it under 25 words.";
        sendEventPrompt(SERVER_ID, serverHistory(), prompt);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent evt) {
        String prompt = "Say a fond farewell to "
                      + evt.getPlayer().getName()
                      + " as they leave the server.";
        sendEventPrompt(SERVER_ID, serverHistory(), prompt);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent evt) {
        String victim = evt.getEntity().getName();
        String killer = evt.getEntity().getKiller() != null
                      ? evt.getEntity().getKiller().getName()
                      : null;
        String prompt = (killer == null)
                      ? victim + " has fallen... comment briefly."
                      : victim + " was slain by " + killer + ". Give a witty remark.";
        sendEventPrompt(SERVER_ID, serverHistory(), prompt);
    }

    @EventHandler
    public void onAdvancement(PlayerAdvancementDoneEvent evt) {
        Advancement adv = evt.getAdvancement();
        String key = adv.getKey().getKey().replace('_',' ');
        String prompt = evt.getPlayer().getName()
                      + " just achieved “" + key + "”. Congratulate them!";
        sendEventPrompt(SERVER_ID, serverHistory(), prompt);
    }

    @EventHandler
    public void onWeatherChange(WeatherChangeEvent evt) {
        World w = evt.getWorld();
        String state = evt.toWeatherState() ? "rain" : "clear skies";
        String prompt = "The weather in " + w.getName()
                      + " has changed to " + state
                      + ". Comment on it.";
        sendEventPrompt(SERVER_ID, serverHistory(), prompt);
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent evt) {
        UUID pid = evt.getPlayer().getUniqueId();
        if (ChatAIPlugin.getInstance().isInVillagerChat(pid)) return;

        String msg = evt.getMessage().trim();
        if (msg.isEmpty() || msg.startsWith("/")) return;

        evt.setCancelled(true);
        String name = evt.getPlayer().getName();
        Bukkit.getScheduler().runTask(plugin, () ->
            Bukkit.broadcastMessage(ChatColor.GRAY + name + ": " + msg)
        );

        Bukkit.getScheduler().runTask(plugin, () ->
            Bukkit.broadcastMessage(ChatColor.GRAY + "[AI] Thinking...")
        );

        Deque<String> h = history.computeIfAbsent(pid, k -> new LinkedList<>());
        if (h.size() >= 6) h.removeFirst();
        ai.ask(pid, h, msg, reply -> {
            if (reply.isBlank()) return;
            String filled = ChatAIPlugin.processPlaceholders(reply);
            Bukkit.broadcastMessage(ChatColor.YELLOW + "[AI] " + filled);
        });
    }
}
