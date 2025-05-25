package com.example.grouptaskmanager.model;

import com.google.firebase.Timestamp;

public class ChatMessage {
    private String id;
    private String groupId;
    private String senderId;
    private String senderName;
    private String senderPhotoUrl;
    private String message;
    private String messageType; // text, image, file
    private Timestamp timestamp;
    private boolean isEdited;
    private Timestamp editedAt;

    // Empty constructor for Firestore
    public ChatMessage() {}

    // Constructor for new message
    public ChatMessage(String groupId, String senderId, String senderName, String message) {
        this.groupId = groupId;
        this.senderId = senderId;
        this.senderName = senderName;
        this.message = message;
        this.messageType = "text";
        this.timestamp = Timestamp.now();
        this.isEdited = false;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getSenderPhotoUrl() {
        return senderPhotoUrl;
    }

    public void setSenderPhotoUrl(String senderPhotoUrl) {
        this.senderPhotoUrl = senderPhotoUrl;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isEdited() {
        return isEdited;
    }

    public void setEdited(boolean edited) {
        isEdited = edited;
    }

    public Timestamp getEditedAt() {
        return editedAt;
    }

    public void setEditedAt(Timestamp editedAt) {
        this.editedAt = editedAt;
    }

    // Helper method to check if message is from current user
    public boolean isFromCurrentUser(String currentUserId) {
        return senderId != null && senderId.equals(currentUserId);
    }
} 