# ChatAI

**Version:** 1.0  
**API:** Bukkit / Spigot 1.21+

A drop‑in Bukkit plugin that brings vanilla Minecraft villagers (and server chat) to life with AI‑powered dialogue, dynamic “villages,” and a simple quest system—no client‑side mods required.

---

## 🔥 Features

### Global Server Chat Bot

- **Welcome Messages**: Unique, AI‑generated greetings for new players.
- **In‑Character Responses**: Replies to chat messages as your friendly in‑game assistant.
- **Real‑Time Placeholders**: Access world time, server uptime, online player count, current weather, and more.

### Villager NPC AI Chat

- **Easy Interaction**: Sneak + right‑click any vanilla villager to start an AI chat session.
- **Persistent Identities**: Each villager is assigned a unique name (e.g., “Brutus the Blacksmith”).
- **Dynamic Villages**: Villagers cluster by proximity into villages with generated names.
- **Quest‑Givers**: ~25% of villagers in each village become quest‑giving NPCs.
- **Guidance**: Non‑quest villagers point players to local quest‑givers.

### Simple Quest System

- **Task Types**: “Kill X skeletons/zombies” or “Fetch Y diamonds” with built‑in action‑bar progress.
- **Reward Delivery**: Rewards issued via console commands (configurable).
- **Progress Notifications**: Updates on kills or item pickups, with completion announcements in the action bar and chat.

---

## 📦 Requirements

- **Minecraft Server**: Paper 1.20+ (or any Bukkit‑compatible fork)
- **Java**: 17+
- **Plugin Jar**: `ChatAI.jar` (built from this project)
- **AI Backend**: LM Studio running a Llama 3.2 Instruct model for chat and quest logic

---

## 🗂️ Server Directory Layout

```
/mc-server
├── paper.jar
├── start.sh          # your server launcher script
└── plugins
    ├── ChatAI.jar
    └── ChatAI         # ChatAI’s data folder
        ├── config.yml
        ├── personalities.yml
        ├── names.yml
        ├── villagers.yml
        ├── memory.yml
        ├── villages.yml
        └── missions.json
```

> **Tip:** Copy the `resources/` folder from this repo into `plugins/ChatAI/` to generate all stub configuration files.

---

## 🚀 Installation & Deployment

1. **Stop** your server if it’s running.
2. **Copy** the `ChatAI.jar` file into your server’s `plugins/` directory.
3. **Ensure** there’s a `plugins/ChatAI/` folder.
4. **Populate** the `plugins/ChatAI/` folder with the following files (use the provided stubs):
   - `config.yml`
   - `personalities.yml`
   - `names.yml`
   - `villagers.yml`
   - `memory.yml`
   - `villages.yml`
   - `missions.json`
5. **Configure** each file:
   - **config.yml**: Set LLM endpoint, chat settings, quest parameters.
   - **personalities.yml**: Define different NPC personalities and prompts.
   - **names.yml**: Customize name pools for villagers and villages.
   - **missions.json**: Adjust quest types, objectives, and reward commands.
6. **Start** your server using your launcher script (e.g., `./start.sh`).
7. **Watch** the console for ChatAI initialization logs.

---

## ⚙️ Configuration Overview

- **LLM Connection** (`config.yml`)
  ```yaml
  llm:
    host: 127.0.0.1
    port: 8000
    model: llama-3.2-instruct
  ```
- **NPC Personalities** (`personalities.yml`)
  ```yaml
  blacksmith:
    greeting: "Ah, welcome to my forge, %player%!"
    style: friendly
  ```
- **Village Naming** (`names.yml`)
  ```yaml
  prefixes: ["Green", "Silver", "Iron"]
  suffixes: ["Vale", "Hollow", "Grove"]
  ```
- **Quest Definitions** (`missions.json`)
  ```json
  [
    {
      "type": "kill",
      "entity": "skeleton",
      "count": 10,
      "reward": "give %player% minecraft:bow 1"
    }
  ]
  ```

---

## 🎓 Usage Examples

- **Chat Bot**: In‑game type `/say hello` and watch the AI respond.
- **Villager Chat**: Sneak + right‑click a villager to chat, then ask for quests.
- **Quest Progress**: Slay skeletons and watch the action bar update in real time.

---

## 🆘 Support & Contribution

1. **Report Issues**: Open a GitHub issue in this repo.
2. **Feature Requests**: Use the "Discussions" tab for suggestions.
3. **Pull Requests**: Fork the repo, implement your changes, and submit a PR.

---

<center>Made with ❤️ by graphitact</center>
