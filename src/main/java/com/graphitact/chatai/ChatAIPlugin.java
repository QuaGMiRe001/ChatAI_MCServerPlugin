package com.graphitact.chatai;

import com.graphitact.chatai.quest.QuestSystem;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class ChatAIPlugin extends JavaPlugin {
    private static ChatAIPlugin instance;
    private AIClient ai;
    private QuestSystem questSystem;
    private long serverStart;
    private List<String> baseNames;
    private final Random rng = new Random();

    // configuration templates
    private final Map<String,String> personalityTemplates = new HashMap<>();
    // villagers.yml persistence
    private YamlConfiguration villagerCfg;
    private File villagerFile;
    // memory.yml persistence
    private YamlConfiguration memoryCfg;
    private File memoryFile;

    // active chat sessions
    private final Set<UUID> activeVillagerChat = new HashSet<>();

    // clustering radius
    private static final double VILLAGE_RADIUS = 50.0;

    @Override
    public void onEnable() {
        instance = this;

        // load config and personalities
        saveDefaultConfig();
        loadPersonalities();

        // load memory.yml
        memoryFile = new File(getDataFolder(), "memory.yml");
        if (!memoryFile.exists()) saveResource("memory.yml", false);
        memoryCfg = YamlConfiguration.loadConfiguration(memoryFile);

        // load villagers.yml
        villagerFile = new File(getDataFolder(), "villagers.yml");
        if (!villagerFile.exists()) saveResource("villagers.yml", false);
        villagerCfg = YamlConfiguration.loadConfiguration(villagerFile);

        // load base villager names
        YamlConfiguration namesCfg = YamlConfiguration.loadConfiguration(
            new File(getDataFolder(), "names.yml")
        );
        baseNames = namesCfg.getStringList("BASE");
        getLogger().info("Loaded " + baseNames.size() + " base villager names");

        // record start time
        serverStart = System.currentTimeMillis();

        // load village names for clustering
        File villagesFile = new File(getDataFolder(), "villages.yml");
        if (!villagesFile.exists()) saveResource("villages.yml", false);
        YamlConfiguration villagesCfg = YamlConfiguration.loadConfiguration(villagesFile);
        List<String> villageNames = villagesCfg.getStringList("names");

        // schedule clustering
        getLogger().info("Scheduling village clustering (10s initial, every 10m)...");
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            getLogger().info("Detecting and assigning villages...");
            detectAndAssignVillages(villageNames);
        }, 200L, 200L * 60);

        // init AI and quest system
        ai = new AIClient(this);
        questSystem = new QuestSystem(this);

        // register listeners
        getServer().getPluginManager().registerEvents(new ServerChatListener(this, ai), this);
        getServer().getPluginManager().registerEvents(
            new VillagerChatListener(this, ai, questSystem),
            this
        );

        getLogger().info("ChatAI enabled, proxy at " + getConfig().getString("proxy-url"));
    }

    private void detectAndAssignVillages(List<String> villageNames) {
        List<Villager> all = Bukkit.getWorlds().stream()
            .flatMap(w -> w.getEntitiesByClass(Villager.class).stream())
            .collect(Collectors.toList());
        if (all.isEmpty()) {
            getLogger().warning("No villagers found, skipping.");
            return;
        }
        Set<UUID> visited = new HashSet<>();
        int nameIdx = 0;
        for (Villager v : all) {
            if (visited.contains(v.getUniqueId())) continue;
            List<Villager> cluster = new ArrayList<>();
            Deque<Villager> queue = new LinkedList<>();
            queue.add(v);
            visited.add(v.getUniqueId());
            while (!queue.isEmpty()) {
                Villager cur = queue.remove();
                cluster.add(cur);
                for (Villager o : all) {
                    if (visited.contains(o.getUniqueId())) continue;
                    if (!o.getWorld().equals(cur.getWorld())) continue;
                    if (cur.getLocation().distanceSquared(o.getLocation()) <= VILLAGE_RADIUS * VILLAGE_RADIUS) {
                        visited.add(o.getUniqueId());
                        queue.add(o);
                    }
                }
            }
            String key0 = cluster.get(0).getUniqueId().toString();
            String gid, gname;
            if (villagerCfg.contains(key0 + ".village.id")) {
                gid = villagerCfg.getString(key0 + ".village.id");
                gname = villagerCfg.getString(key0 + ".village.name");
            } else {
                gid = UUID.randomUUID().toString();
                gname = villageNames.get(nameIdx++ % villageNames.size());
            }
            Collections.shuffle(cluster, rng);
            int qcount = Math.max(1, cluster.size()/4);
            for (int i=0;i<cluster.size();i++) {
                String k = cluster.get(i).getUniqueId().toString();
                villagerCfg.set(k + ".village.id", gid);
                villagerCfg.set(k + ".village.name", gname);
                villagerCfg.set(k + ".village.isQuestGiver", i<qcount);
            }
        }
        try { villagerCfg.save(villagerFile); } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    public void onDisable() {
        try {
            villagerCfg.save(villagerFile);
            memoryCfg.save(memoryFile);
        } catch (Exception e) {
            getLogger().warning("Save failed: " + e.getMessage());
        }
    }

    public static ChatAIPlugin getInstance() { return instance; }

    /** Accessor for shared villager config */
    public YamlConfiguration getVillagerConfig() {
        return villagerCfg;
    }

    public void startVillagerChat(UUID pid) { activeVillagerChat.add(pid); }
    public void endVillagerChat(UUID pid)   { activeVillagerChat.remove(pid); }
    public boolean isInVillagerChat(UUID pid){ return activeVillagerChat.contains(pid);} 

    private void loadPersonalities() {
        File f = new File(getDataFolder(), "personalities.yml");
        if (!f.exists()) saveResource("personalities.yml", false);
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        personalityTemplates.clear();
        for (String k:cfg.getKeys(false)) personalityTemplates.put(k.toUpperCase(), cfg.getString(k));
    }

    public String getPersonalityPrompt(String prof, String name) {
        String tpl = personalityTemplates.getOrDefault(prof.toUpperCase(), personalityTemplates.get("DEFAULT"));
        return tpl.replace("{name}", name);
    }

    public String getOrCreateVillagerName(UUID vid, String prof) {
        String key = vid.toString()+".baseName";
        String base = villagerCfg.getString(key);
        if (base==null) { base = baseNames.get(rng.nextInt(baseNames.size())); villagerCfg.set(key, base);}        
        String pf = prof.substring(0,1).toUpperCase()+prof.substring(1).toLowerCase();
        return base+" the "+pf;
    }

    @SuppressWarnings("unchecked")
    public Deque<String> getVillagerMemory(UUID vid) {
        Deque<String> dq = new LinkedList<>(memoryCfg.getStringList(vid.toString()));
        while(dq.size()>20) dq.removeFirst();
        return dq;
    }

    public void saveVillagerMemory(UUID vid, Deque<String> hist) {
        memoryCfg.set(vid.toString(), new ArrayList<>(hist));
    }

    public static String processPlaceholders(String txt) {
        ChatAIPlugin p=getInstance();
        if(txt.contains("{{current_time}}")){
            long ticks=Bukkit.getWorlds().get(0).getTime(); long h=(ticks/1000+6)%24; long m=(ticks%1000)*60/1000;
            txt=txt.replace("{{current_time}}",String.format("%02d:%02d",h,m));
        }
        if(txt.contains("{{uptime}}")){
            long ms=System.currentTimeMillis()-p.serverStart; long s=ms/1000, H=s/3600; s%=3600; long M=s/60; s%=60;
            txt=txt.replace("{{uptime}}",String.format("%dh %dm %ds",H,M,s));
        }
        if(txt.contains("{{online_players}}")) txt=txt.replace("{{online_players}}",String.valueOf(Bukkit.getOnlinePlayers().size()));
        if(txt.contains("{{current_weather_state}}")){
            World w=Bukkit.getWorlds().get(0); txt=txt.replace("{{current_weather_state}}",w.hasStorm()?"rain":"clear skies");
        }
        return txt;
    }

    public static String processVillagerPlaceholders(String txt) {
        if(txt.contains("{{current_time}}")){
            long t=Bukkit.getWorlds().get(0).getTime();
            txt=txt.replace("{{current_time}}",String.format("%02d:%02d",(t/1000+6)%24,(t%1000)*60/1000));
        }
        if(txt.contains("{{current_weather_state}}")){
            World w=Bukkit.getWorlds().get(0);
            txt=txt.replace("{{current_weather_state}}",w.hasStorm()?"rain":"clear skies");
        }
        return txt;
    }
}
