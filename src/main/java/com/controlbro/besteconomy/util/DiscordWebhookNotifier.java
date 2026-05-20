package com.controlbro.besteconomy.util;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.bukkit.plugin.java.JavaPlugin;

public class DiscordWebhookNotifier {
    private final JavaPlugin plugin;

    public DiscordWebhookNotifier(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void send(String path, String message) {
        String url = plugin.getConfig().getString(path, "");
        if (url == null || url.isBlank() || message == null || message.isBlank()) {
            return;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> post(url, message));
    }

    private void post(String url, String message) {
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setDoOutput(true);
            String payload = "{\"content\":\"" + escape(message) + "\"}";
            byte[] data = payload.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(data.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(data);
            }
            connection.getInputStream().close();
            connection.disconnect();
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed Discord webhook post: " + ex.getMessage());
        }
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
