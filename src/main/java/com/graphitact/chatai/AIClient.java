package com.graphitact.chatai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Deque;
import java.util.UUID;
import java.util.function.Consumer;

public class AIClient {
    private final JavaPlugin plugin;
    private final HttpClient http;
    private final String proxyUrl, systemPrompt, modelName;
    private final double temperature;
    private final int maxTokens;

    public AIClient(JavaPlugin plugin) {
        this.plugin       = plugin;
        this.http         = HttpClient.newHttpClient();
        this.proxyUrl     = plugin.getConfig().getString("proxy-url", "http://localhost:3004/ai");
        this.systemPrompt = plugin.getConfig()
                              .getString("system-prompt", "")
                              .strip();
    this.modelName    = plugin.getConfig()
                              .getString("model-name",    "default")
                              .strip();

    plugin.getLogger().info("[ChatAI] Using model-name:  " + modelName);
    plugin.getLogger().info("[ChatAI] Using system-prompt:\n" 
        + (systemPrompt.isEmpty() ? "<blank!>" : systemPrompt));
        this.temperature  = plugin.getConfig().getDouble("temperature", 0.7);
        this.maxTokens    = plugin.getConfig().getInt("max-tokens", 4000);
        if (this.systemPrompt.isEmpty()) {
            plugin.getLogger().warning("system-prompt is blank — LM proxy will reject!");
        }
    }

    /**
     * Send a chat request to LM studio
     * @param playerId   who to store history under (or null for one-shots)
     * @param history    last N messages, may be empty
     * @param prompt     what the “user” says
     * @param callback   runs on the main thread with the AI’s clean reply
     */
    public void ask(UUID playerId,
                    Deque<String> history,
                    String prompt,
                    Consumer<String> callback)
    {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // 1) build messages array
                JsonArray messages = new JsonArray();
                for (String e : history) {
                    JsonObject m = new JsonObject();
                    boolean asst = e.startsWith("Assistant:");
                    m.addProperty("role",    asst ? "assistant" : "user");
                    m.addProperty("content", e.substring(e.indexOf(':') + 2));
                    messages.add(m);
                }
                JsonObject user = new JsonObject();
                user.addProperty("role",    "user");
                user.addProperty("content", prompt);
                messages.add(user);

                // 2) envelope for LM Studio
                JsonObject body = new JsonObject();
                body.addProperty("model",  modelName);
                body.addProperty("system", systemPrompt);
                body.add("messages",      messages);
                body.addProperty("temperature", temperature);
                body.addProperty("max_tokens",   maxTokens);

                // 3) JSON-schema → { answer: string }
                JsonObject schema = new JsonObject();
                schema.addProperty("type", "object");
                JsonObject props = new JsonObject();
                JsonObject def   = new JsonObject();
                def.addProperty("type",        "string");
                def.addProperty("description", "The AI’s chat reply");
                props.add("answer", def);
                schema.add("properties", props);
                JsonArray req = new JsonArray(); req.add("answer");
                schema.add("required", req);

                JsonObject rf = new JsonObject();
                rf.addProperty("type", "json_schema");
                JsonObject js = new JsonObject();
                js.add("schema", schema);
                rf.add("json_schema", js);
                body.add("response_format", rf);

                String out = body.toString();
                plugin.getLogger().info("[ChatAI] ▶ " + out);

                // 4) HTTP POST
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(proxyUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(out, StandardCharsets.UTF_8))
                    .build();
                HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
                String raw = resp.body();
                plugin.getLogger().info("[ChatAI] ◀ " + raw);

                // 5) parse
                String answer = parse(raw);

                // 6) record history
                if (playerId != null) {
                    history.addLast("Assistant: " + answer);
                    if (history.size() > 6) history.removeFirst();
                }

                // 7) deliver on main thread
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    callback.accept(answer)
                );

            } catch (Exception ex) {
                plugin.getLogger().warning("AI proxy failed: " + ex.getMessage());
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    callback.accept("")
                );
            }
        });
    }

 /**
   * Like ask(...), but override the system prompt per request.
   */
  public void askWithSystem(UUID playerId,
                            Deque<String> history,
                            String prompt,
                            String overrideSystem,
                            Consumer<String> callback)
  {
      plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
          try {
              // 1) build messages array (same as ask)
              JsonArray messages = new JsonArray();
              for (String e : history) {
                  JsonObject m = new JsonObject();
                  boolean asst = e.startsWith("Assistant:");
                  m.addProperty("role",    asst ? "assistant" : "user");
                  m.addProperty("content", e.substring(e.indexOf(':') + 2));
                  messages.add(m);
              }
              JsonObject user = new JsonObject();
              user.addProperty("role",    "user");
              user.addProperty("content", prompt);
              messages.add(user);

              // 2) envelope, but use overrideSystem
              JsonObject body = new JsonObject();
              body.addProperty("model",       modelName);
              body.addProperty("system",      overrideSystem);
              body.add("messages",           messages);
              body.addProperty("temperature", temperature);
              body.addProperty("max_tokens",  maxTokens);

              // 3) same JSON-schema block
              JsonObject schema = new JsonObject();
              schema.addProperty("type", "object");
              JsonObject props = new JsonObject();
              JsonObject def   = new JsonObject();
              def.addProperty("type",        "string");
              def.addProperty("description", "The AI’s chat reply");
              props.add("answer", def);
              schema.add("properties", props);
              JsonArray req = new JsonArray(); req.add("answer");
              schema.add("required", req);

              JsonObject rf = new JsonObject();
              rf.addProperty("type", "json_schema");
              JsonObject js = new JsonObject();
              js.add("schema", schema);
              rf.add("json_schema", js);
              body.add("response_format", rf);

              String out = body.toString();
              plugin.getLogger().info("[ChatAI] ▶ " + out);

              // 4) HTTP call
              HttpRequest request = HttpRequest.newBuilder()
                  .uri(URI.create(proxyUrl))
                  .timeout(Duration.ofSeconds(10))
                  .header("Content-Type","application/json")
                  .POST(HttpRequest.BodyPublishers.ofString(out, StandardCharsets.UTF_8))
                  .build();
              HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
              String raw = resp.body();
              plugin.getLogger().info("[ChatAI] ◀ " + raw);

              // 5) parse & record history if needed
              String answer = parse(raw);
              if (playerId != null) {
                  history.addLast("Assistant: " + answer);
                  if (history.size() > 6) history.removeFirst();
              }

              // 6) callback on main thread
              plugin.getServer().getScheduler().runTask(plugin, () ->
                  callback.accept(answer)
              );

          } catch (Exception ex) {
              plugin.getLogger().warning("AI proxy failed: " + ex.getMessage());
              plugin.getServer().getScheduler().runTask(plugin, () ->
                  callback.accept("")
              );
          }
      });
  }

    private String parse(String body) {
    JsonObject root = JsonParser.parseString(body).getAsJsonObject();

    // 1) Top-level error
    if (root.has("error")) {
        return "";
    }

    // 2) JSON-schema top-level: { "answer": "…" }
    if (root.has("answer")) {
        return root.get("answer").getAsString();
    }

    // 3) choices[].data.answer (new LM Studio style)
    if (root.has("choices")) {
        var arr = root.getAsJsonArray("choices");
        if (!arr.isEmpty()) {
            JsonObject first = arr.get(0).getAsJsonObject();

            // new style: { choices:[{ data:{ answer } }] }
            if (first.has("data")) {
                JsonObject data = first.getAsJsonObject("data");
                if (data.has("answer")) {
                    return data.get("answer").getAsString();
                }
            }

            // OpenAI-style chat.completions: choices[].message.content
            if (first.has("message")) {
                JsonObject msg = first.getAsJsonObject("message");
                if (msg.has("content")) {
                    String raw = msg.get("content").getAsString().trim();

                    // if the LLM actually wrapped our JSON-schema in a string, unwrap it:
                    if (raw.startsWith("{") && raw.endsWith("}")) {
                        try {
                            JsonObject inner = JsonParser.parseString(raw).getAsJsonObject();
                            if (inner.has("answer")) {
                                return inner.get("answer").getAsString();
                            }
                        } catch (Exception ignored) { /* fall through to return raw */ }
                    }

                    // otherwise just return the plain content
                    return raw;
                }
            }

            // legacy OpenAI "text" field
            if (first.has("text")) {
                return first.get("text").getAsString();
            }
        }
    }

    // 4) LM Studio legacy
    if (root.has("response")) {
        return root.get("response").getAsString();
    }

    // nothing matched — avoid dumping JSON
    return "";
}

}
