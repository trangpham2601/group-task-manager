package com.example.grouptaskmanager.model;

import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Task {
    private String id;
    private String groupId; // Group that this task belongs to
    private String title;
    private String description;
    private String createdBy;
    private String assignedTo;
    private String status; // "todo", "in_progress", "done"
    private String priority; // "low", "medium", "high"
    private Timestamp deadline;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private Timestamp dueReminder;
    private List<String> tags;
    private int commentsCount;
    private List<Map<String, String>> attachments;

    // Constants for status and priority
    public static final String STATUS_TODO = "todo";
    public static final String STATUS_IN_PROGRESS = "in_progress";
    public static final String STATUS_DONE = "done";

    public static final String PRIORITY_LOW = "low";
    public static final String PRIORITY_MEDIUM = "medium";
    public static final String PRIORITY_HIGH = "high";

    // Empty constructor for Firestore
    public Task() {
        tags = new ArrayList<>();
        attachments = new ArrayList<>();
    }

    // Constructor with required fields
    public Task(String id, String title, String description, String createdBy, 
                String assignedTo, String status, String priority, Timestamp deadline,
                Timestamp createdAt, Timestamp updatedAt) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.createdBy = createdBy;
        this.assignedTo = assignedTo;
        this.status = status;
        this.priority = priority;
        this.deadline = deadline;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.tags = new ArrayList<>();
        this.attachments = new ArrayList<>();
        this.commentsCount = 0;
    }

    // Full constructor
    public Task(String id, String title, String description, String createdBy, 
                String assignedTo, String status, String priority, Timestamp deadline,
                Timestamp createdAt, Timestamp updatedAt, Timestamp dueReminder,
                List<String> tags, int commentsCount, List<Map<String, String>> attachments) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.createdBy = createdBy;
        this.assignedTo = assignedTo;
        this.status = status;
        this.priority = priority;
        this.deadline = deadline;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.dueReminder = dueReminder;
        this.tags = tags;
        this.commentsCount = commentsCount;
        this.attachments = attachments;
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(String assignedTo) {
        this.assignedTo = assignedTo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public Timestamp getDeadline() {
        return deadline;
    }

    public void setDeadline(Timestamp deadline) {
        this.deadline = deadline;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Timestamp getDueReminder() {
        return dueReminder;
    }

    public void setDueReminder(Timestamp dueReminder) {
        this.dueReminder = dueReminder;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public int getCommentsCount() {
        return commentsCount;
    }

    public void setCommentsCount(int commentsCount) {
        this.commentsCount = commentsCount;
    }

    public List<Map<String, String>> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<Map<String, String>> attachments) {
        this.attachments = attachments;
    }

    // Transient field - not stored in Firestore
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }
    
    public String getGroupId() {
        return groupId;
    }
} 