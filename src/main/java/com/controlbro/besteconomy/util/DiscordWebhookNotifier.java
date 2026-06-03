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
        String payload = "{\"content\":\"" + escape(message) + "\"}";
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> post(url, payload));
    }

    public void sendEmbed(String path, String title, String description, int color) {
        String url = plugin.getConfig().getString(path, "");
        if (url == null || url.isBlank() || title == null || title.isBlank()) {
            return;
        }
        String safeDescription = description == null ? "" : description;
        String payload = "{\"embeds\":[{\"title\":\"" + escape(title) + "\",\"description\":\"" + escape(safeDescription) + "\",\"color\":" + color + "}]}";
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> post(url, payload));
    }

    private void post(String url, String payload) {
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setDoOutput(true);
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
