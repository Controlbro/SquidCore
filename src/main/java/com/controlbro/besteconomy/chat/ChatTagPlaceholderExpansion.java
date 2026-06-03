package com.controlbro.besteconomy.chat;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public class ChatTagPlaceholderExpansion extends PlaceholderExpansion {
    private final ChatService chatService;

    public ChatTagPlaceholderExpansion(ChatService chatService) {
        this.chatService = chatService;
    }

    @Override
    public String getIdentifier() {
        return "squidcore";
    }

    @Override
    public String getAuthor() {
        return "Controlbro";
    }

    @Override
    public String getVersion() {
        return "2.7";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, String params) {
        if (!params.equalsIgnoreCase("tag") || !(offlinePlayer instanceof Player player)) {
            return null;
        }
        return chatService.renderedTag(player);
    }
}
