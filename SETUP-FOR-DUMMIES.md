# SETUP-FOR-DUMMIES   -NO API KEYS 
Use your own GPU to power your AI locally for free - Or I guess you COULD point it to an AI API if you wanted ofcourse.

A step‑by‑step guide to get ChatAI up and running, even if you’ve never done this before.

---

## 🎮 1. Prepare Your Minecraft Server

1. **Download Paper (recommended) or Spigot:**
   - Paper 1.20+: https://papermc.io/downloads  
   - Or Spigot: https://www.spigotmc.org/
2. **Install:**
   - Place `paper.jar` (or `spigot.jar`) in an empty folder (e.g. `mc-server/`).
   - Create a launcher script (`start.sh` on Linux/macOS or `start.bat` on Windows):
     ```bash
     java -Xms1G -Xmx2G -jar paper.jar --nogui
     ```
3. **Run once to generate folders:**
   ```bash
   ./start.sh    # or double‑click start.bat
   ```
4. **Agree to EULA:**
   - Open `eula.txt`, change `eula=false` to `eula=true`, save.

---

## 🤖 2. Install LM Studio & Llama 3.2 Instruct

1. **Download LM Studio:**
   - https://github.com/nomic-ai/LMStudio/releases  
   - Choose the appropriate OS installer and follow instructions.
2. **Obtain Llama 3.2 Instruct 3B model:**
   - In LM Studio, go to **Model Browser** → search for **Llama 3.2 Instruct** → install.
3. **Start LM Studio & load the model:**
   - Launch LM Studio, open the Llama 3.2 Instruct model.
   - Note the API endpoint (default: `http://127.0.0.1:1234/v1/chat/completions`).

---

## 🔄 3. Set Up the Minecraft‑to‑LM Proxy

This small Node.js proxy translates Minecraft plugin requests into LM Studio calls.

1. **Prerequisites:** Node.js 18+ installed (https://nodejs.org/)
2. **Create `simple-proxy.js`:**
   ```js
   // simple-proxy.js
   const express = require('express');
   const fetch   = require('node-fetch');
   const app     = express();

   app.use(express.json());

   const LM_HOST = 'http://127.0.0.1:1234';
   const LM_PATH = '/v1/chat/completions';

   const mcSchema = {
     type: 'object',
     properties: {
       answer: {
         type: 'string',
         description: 'The AI’s chat reply'
       }
     },
     required: ['answer']
   };

   app.post('/ai', async (req, res) => {
     const { model, system, messages } = req.body;
     if (!Array.isArray(messages) || !system || !model) {
       return res.status(400).json({ error: 'Bad request' });
     }
     const payload = { model, messages: [{ role:'system',content:system }, ...messages], response_format:{ type:'json_schema', json_schema:{ schema:mcSchema } } };
     try {
       const upstream = await fetch(`${LM_HOST}${LM_PATH}`, { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(payload) });
       if (!upstream.ok) return res.status(502).json({ error:`Upstream ${upstream.status}` });
       const data = await upstream.json();
       return res.json(data);
     } catch (err) {
       return res.status(500).json({ error: err.message });
     }
   });

   const PORT = 3004;
   app.listen(PORT, () => console.log(`AI‑proxy listening on http://localhost:${PORT}/ai`));
   ```
3. **Install dependencies & run:**
   ```bash
   npm init -y
   npm install express node-fetch
   node simple-proxy.js
   ```
4. **Verify:**
   - In your browser or via `curl`, POST a test JSON to `http://localhost:3004/ai` → should get a JSON `{ "answer": "…" }`.

---

## ⚙️ 4. Configure the ChatAI Plugin

1. **Place files:**
   - Put `ChatAI.jar` in `mc-server/plugins/`
   - Create `mc-server/plugins/ChatAI/`
2. **Copy stub configs:** From the GitHub repo’s `resources/` folder into `plugins/ChatAI/`:
   - `config.yml`, `personalities.yml`, `names.yml`, `villagers.yml`, `memory.yml`, `villages.yml`, `missions.json`
3. **Edit `config.yml`:**
   ```yaml
   proxy-url: 'http://localhost:3004/ai'
   system-prompt: |
     You are a helpful Minecraft villager.
   model-name: 'llama-3.2-instruct'
   temperature: 0.7
   max-tokens: 4000
   ```
4. **Review `missions.json`** to tweak quests, rewards, counts.
5. **(Optional)** Examine `AIClient.java` in the plugin source for deeper customization (e.g. history length).

---

## ▶️ 5. Start Everything & Test

1. **Run LM Studio** (with Llama 3.2 Instruct).
2. **Start the proxy:** `node simple-proxy.js`.
3. **Launch Minecraft server:** `./start.sh`.
4. **In-Game:**
   - Join the server, watch the welcome message.
   - Sneak + right-click a villager → chat UI appears.
   - Complete a quest: kill or fetch items → see action-bar progress.

---

Congratulations! ChatAI should now be fully functional. 🎉

If you hit any issues, double‑check log output in both the proxy console and the server console for error messages and missing configurations.
