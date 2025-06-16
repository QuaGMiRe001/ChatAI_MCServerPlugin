package com.graphitact.chatai;

import com.graphitact.chatai.quest.QuestSystem;
import com.graphitact.chatai.quest.QuestSystem.Quest;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VillagerChatListener implements Listener {
  private final ChatAIPlugin plugin;
  private final AIClient ai;
  private final QuestSystem questSystem;
  private final int freezeSeconds;
  private final Random rng = new Random();

  // track active chat sessions
  private final Map<UUID, VillagerSession> sessions      = new ConcurrentHashMap<>();
  // track scheduled “end conversation” tasks
  private final Map<UUID, BukkitTask>      timeoutTasks  = new ConcurrentHashMap<>();
  // track scheduled “re-enable AI” tasks
  private final Map<UUID, BukkitTask>      aiSuspendTasks= new ConcurrentHashMap<>();

  public VillagerChatListener(ChatAIPlugin plugin,
                              AIClient ai,
                              QuestSystem questSystem) {
    this.plugin        = plugin;
    this.ai            = ai;
    this.questSystem   = questSystem;
    this.freezeSeconds = plugin.getConfig()
                              .getInt("villager-freeze-seconds", 30);
  }

  @EventHandler
  public void onVillagerChat(PlayerInteractEntityEvent evt) {
    if (!(evt.getRightClicked() instanceof Villager v)) return;
    if (!evt.getPlayer().isSneaking())           return;

    evt.setCancelled(true);
    Player player = evt.getPlayer();
    UUID pid = player.getUniqueId();
    UUID vid = v.getUniqueId();

    // if an old session exists, tear down its boss bar immediately
    VillagerSession old = sessions.remove(pid);
    if (old != null) {
      old.bossBar().removeAll();
      cancelTasks(pid);
    }

    // disable AI and schedule re-enable
    suspendVillagerAI(pid, v);

    // load village data
    var vc           = plugin.getVillagerConfig();
    String villageName   = vc.getString(vid + ".village.name", "the wilds");
    boolean isQuestGiver = vc.getBoolean(vid + ".village.isQuestGiver", false);

    // handle ready turn-in quests
    List<Quest> ready = questSystem.getReadyTurnInQuests(pid, vid);
    if (!ready.isEmpty()) {
      ready.forEach(q -> questSystem.completeQuest(q, player));
      return;
    }

    String profession   = v.getProfession().name();
    String villagerName = plugin.getOrCreateVillagerName(vid, profession);

    String overrideSystem = plugin.getPersonalityPrompt(profession, villagerName)
        + "\nYou live in the village of " + villageName + ".";
    if (isQuestGiver) {
      overrideSystem += "\nAs a quest-giver, you assign missions on request.";
    }

    // create conversation boss bar
    BossBar bar = Bukkit.createBossBar(
      "Chatting with " + villagerName,
      BarColor.BLUE,
      BarStyle.SOLID
    );
    bar.addPlayer(player);

    Deque<String> history = plugin.getVillagerMemory(vid);
    sessions.put(pid, new VillagerSession(
      vid, villagerName, overrideSystem, history,
      isQuestGiver, villageName, bar, v
    ));
    plugin.startVillagerChat(pid);

    // schedule the 30s end-of-chat
    scheduleSessionEnd(pid, vid, history);

    // “Thinking…” splash
    Bukkit.getScheduler().runTask(plugin, () ->
      Bukkit.broadcastMessage(ChatColor.GRAY + "[" + villagerName + "] Thinking...")
    );

    // “Talk ▶” action-bar: clicking will prefill chat with slash
    Bukkit.getScheduler().runTask(plugin, () -> {
      TextComponent openChat = new TextComponent("§e[ Talk ▶ ]");
      openChat.setClickEvent(new ClickEvent(
        ClickEvent.Action.SUGGEST_COMMAND,
        "/"
      ));
      player.spigot().sendMessage(
        ChatMessageType.ACTION_BAR,
        openChat
      );
    });

    // first greeting
    ai.askWithSystem(pid, history,
      String.format("Greet %s as a %s from %s.",
        player.getName(),
        profession.toLowerCase(),
        villageName
      ),
      overrideSystem,
      reply -> {
        sendVillagerReply(pid, villagerName, reply);
        plugin.saveVillagerMemory(vid, history);
      }
    );
  }

  @EventHandler
  public void onPlayerChat(AsyncPlayerChatEvent evt) {
    UUID pid = evt.getPlayer().getUniqueId();
    VillagerSession session = sessions.get(pid);
    if (session == null) return;

    evt.setCancelled(true);
    String msg = evt.getMessage().trim();
    if (msg.isEmpty()) return;

    Player player = evt.getPlayer();

    // extend timers
    scheduleSessionEnd(pid, session.villagerId, session.history);
    suspendVillagerAI(pid, session.villager);
    session.bossBar().setProgress(1.0);

    String lower = msg.toLowerCase();

    // static quest assignment
    if (session.isQuestGiver && lower.contains("quest")) {
      Quest q = new Quest();
      q.id             = UUID.randomUUID().toString();
      q.giver          = session.villagerId;
      q.player         = pid;
      int pick         = rng.nextInt(3);
      q.requiresTurnIn = (pick == 1);
      switch (pick) {
        case 0 -> {
          q.type          = Quest.Type.KILL;
          q.target        = "ZOMBIE";
          q.amount        = 5;
          q.locationHint  = session.villageName + " outskirts";
          q.rewardCommand = "give %player% iron_sword 1";
        }
        case 1 -> {
          q.type          = Quest.Type.FETCH;
          q.target        = "DIAMOND";
          q.amount        = 1;
          q.locationHint  = "hidden cave";
          q.rewardCommand = "give %player% diamond 2";
        }
        default -> {
          q.type          = Quest.Type.KILL;
          q.target        = "SKELETON";
          q.amount        = 3;
          q.locationHint  = "ancient ruins";
          q.rewardCommand = "give %player% bow 1";
        }
      }
      questSystem.addQuest(q);
      return;
    }

    // hint for non-quest-givers
    if (!session.isQuestGiver && lower.contains("quest")) {
      String hint = "I don’t give quests—look for the quest-giver in "
                    + session.villageName + ".";
      Bukkit.getScheduler().runTask(plugin, () ->
        player.sendMessage(ChatColor.LIGHT_PURPLE + hint)
      );
      return;
    }

    // fallback to AI chat
    Bukkit.getScheduler().runTask(plugin, () ->
      Bukkit.broadcastMessage(
        ChatColor.GRAY + "[" + session.villagerName + "] Thinking..."
      )
    );

    session.history.addLast("User: " + msg);
    if (session.history.size() > 20) session.history.removeFirst();

    ai.askWithSystem(
      pid,
      session.history,
      msg,
      session.overrideSystem,
      reply -> sendVillagerReply(pid, session.villagerName, reply)
    );
  }

  private void cancelTasks(UUID pid) {
    BukkitTask t1 = timeoutTasks.remove(pid);
    if (t1 != null) t1.cancel();
    BukkitTask t2 = aiSuspendTasks.remove(pid);
    if (t2 != null) t2.cancel();
  }

  /** disable villager AI and schedule its re-enable */
  private void suspendVillagerAI(UUID pid, Villager v) {
    v.setAI(false);
    BukkitTask old = aiSuspendTasks.remove(pid);
    if (old != null && !old.isCancelled()) old.cancel();
    BukkitTask t = Bukkit.getScheduler().runTaskLater(plugin, () -> {
      v.setAI(true);
      aiSuspendTasks.remove(pid);
    }, freezeSeconds * 20L);
    aiSuspendTasks.put(pid, t);
  }

  /** (Re-)start the end-of-conversation timer */
  private void scheduleSessionEnd(UUID pid,
                                  UUID vid,
                                  Deque<String> history) {
    BukkitTask old = timeoutTasks.remove(pid);
    if (old != null && !old.isCancelled()) old.cancel();
    BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
      VillagerSession s = sessions.remove(pid);
      if (s != null) s.bossBar().removeAll();
      plugin.endVillagerChat(pid);
      plugin.saveVillagerMemory(vid, history);
      timeoutTasks.remove(pid);
    }, freezeSeconds * 20L);
    timeoutTasks.put(pid, task);
  }

  private void sendVillagerReply(UUID pid,
                                 String name,
                                 String reply) {
    if (reply.isBlank()) return;
    VillagerSession s = sessions.get(pid);
    if (s != null) {
      scheduleSessionEnd(pid, s.villagerId, s.history);
      suspendVillagerAI(pid, s.villager);
      s.bossBar().setProgress(1.0);
    }
    String filled = ChatAIPlugin.processVillagerPlaceholders(reply);
    Bukkit.broadcastMessage(
      ChatColor.YELLOW + "[" + name + "] " + filled
    );
  }

  private static record VillagerSession(
    UUID      villagerId,
    String    villagerName,
    String    overrideSystem,
    Deque<String> history,
    boolean   isQuestGiver,
    String    villageName,
    BossBar   bossBar,
    Villager  villager
  ) {}
}

