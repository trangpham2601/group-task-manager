package com.example.grouptaskmanager.model;

public class GroupWithUnread {
    private Group group;
    private int unreadCount;
    private ChatMessage lastMessage;

    public GroupWithUnread() {}

    public GroupWithUnread(Group group, int unreadCount, ChatMessage lastMessage) {
        this.group = group;
        this.unreadCount = unreadCount;
        this.lastMessage = lastMessage;
    }

    // Getters and Setters
    public Group getGroup() {
        return group;
    }

    public void setGroup(Group group) {
        this.group = group;
    }

    public int getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(int unreadCount) {
        this.unreadCount = unreadCount;
    }

    public ChatMessage getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(ChatMessage lastMessage) {
        this.lastMessage = lastMessage;
    }

    // Helper methods
    public boolean hasUnreadMessages() {
        return unreadCount > 0;
    }
} 