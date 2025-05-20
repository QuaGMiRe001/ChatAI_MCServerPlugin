# ChatAI

**Version:** 1.0  
**API:** Bukkit / Spigot 1.21+  

A drop-in Bukkit plugin that brings vanilla Minecraft villagers (and server chat) to life with AI-powered dialogue, dynamic “villages,” and a simple quest system—no client-side mods required.

---

## 🔥 Features

- **Global Server Chat Bot**  
  - Welcomes new players with a unique AI-generated greeting  
  - Responds to chat messages in character as your friendly in-game assistant  
  - Knows real-time placeholders: world time, uptime, online player count, weather  

- **Villager NPC AI Chat**  
  - Sneak + right-click any vanilla villager to start a session  
  - Each villager gets a persistent “Brutus the Blacksmith”–style name  
  - Villagers clustered by proximity into dynamically named villages  
  - ~25% of villagers per village become **quest-givers**  
  - Non-quest villagers will point you to your local quest-giver  

- **Simple Quest System**  
  - “Kill X skeletons” or “Fetch Y diamonds” tasks, with built-in action-bar progress  
  - Rewards are given via console commands  
  - Progress notifications on kill/item pickup, completion sends action-bar + chat  

---

Installation:   (-I will make this a simple installer soon) -Still busy putting the first version in place along with decent easy to understand installer.

A step-by-step walkthrough for getting ChatAI up and running on a vanilla Paper (or Bukkit) server, plus hooking it up to an LLM in LM Studio (using Llama 3.2 Instruct).

Requirements
------------
- Minecraft Server: Paper 1.20+ (or any Bukkit-compatible fork)
- Java 17+
- ChatAI.jar (built from this project)
- LM Studio with a running Llama 3.2 Instruct model (for AI chat & quests)

1. Server Directory Layout
--------------------------
/mc-server
├── paper.jar
├── start.sh        ← your server launcher script
├── plugins
│   ├── ChatAI.jar
│   └── ChatAI        ← ChatAI’s data folder
│       ├── config.yml
│       ├── personalities.yml
│       ├── names.yml
│       ├── villagers.yml
│       ├── memory.yml
│       ├── villages.yml
│       └── missions.json
└── ...

prepped files above can be emm

2. Deploying the Plugin
------------------------
1. Stop your server.
2. Copy ChatAI.jar into your server’s plugins/ folder.
3. Create the plugins/ChatAI/ folder (if needed).
4. Inside plugins/ChatAI/, add stub files (from the GitHub repo’s resources):
   - config.yml
   - personalities.yml
   - names.yml
   - villagers.yml
   - memory.yml
   - villages.yml
   - missions.json
  

   








