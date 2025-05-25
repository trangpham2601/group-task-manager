package com.example.grouptaskmanager.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.PropertyName;

import java.util.Objects;

public class Comment {
    private String id;
    private String taskId;
    private String groupId;
    private String content;
    private String authorId;
    private String authorName;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    
    // Reply functionality
    private String replyToCommentId; // ID của comment được reply
    private String replyToAuthorName; // Tên người được reply
    private boolean isReply; // Có phải là reply không
    
    // Constructors
    public Comment() {
        // Required empty constructor for Firestore
    }
    
    public Comment(String taskId, String groupId, String content, String authorId, String authorName) {
        this.taskId = taskId;
        this.groupId = groupId;
        this.content = content;
        this.authorId = authorId;
        this.authorName = authorName;
        this.createdAt = Timestamp.now();
        this.updatedAt = Timestamp.now();
        this.isReply = false;
    }
    
    public Comment(String taskId, String groupId, String content, String authorId, String authorName, 
                   String replyToCommentId, String replyToAuthorName) {
        this.taskId = taskId;
        this.groupId = groupId;
        this.content = content;
        this.authorId = authorId;
        this.authorName = authorName;
        this.replyToCommentId = replyToCommentId;
        this.replyToAuthorName = replyToAuthorName;
        this.isReply = true;
        this.createdAt = Timestamp.now();
        this.updatedAt = Timestamp.now();
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @PropertyName("taskId")
    public String getTaskId() {
        return taskId;
    }

    @PropertyName("taskId")
    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    @PropertyName("groupId")
    public String getGroupId() {
        return groupId;
    }

    @PropertyName("groupId")
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    @PropertyName("authorId")
    public String getAuthorId() {
        return authorId;
    }

    @PropertyName("authorId")
    public void setAuthorId(String authorId) {
        this.authorId = authorId;
    }

    @PropertyName("authorName")
    public String getAuthorName() {
        return authorName;
    }

    @PropertyName("authorName")
    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    @PropertyName("createdAt")
    public Timestamp getCreatedAt() {
        return createdAt;
    }

    @PropertyName("createdAt")
    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    @PropertyName("updatedAt")
    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    @PropertyName("updatedAt")
    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    @PropertyName("replyToCommentId")
    public String getReplyToCommentId() {
        return replyToCommentId;
    }

    @PropertyName("replyToCommentId")
    public void setReplyToCommentId(String replyToCommentId) {
        this.replyToCommentId = replyToCommentId;
    }

    @PropertyName("replyToAuthorName")
    public String getReplyToAuthorName() {
        return replyToAuthorName;
    }

    @PropertyName("replyToAuthorName")
    public void setReplyToAuthorName(String replyToAuthorName) {
        this.replyToAuthorName = replyToAuthorName;
    }

    @PropertyName("isReply")
    public boolean isReply() {
        return isReply;
    }

    @PropertyName("isReply")
    public void setReply(boolean reply) {
        isReply = reply;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Comment comment = (Comment) o;
        return Objects.equals(id, comment.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Comment{" +
                "id='" + id + '\'' +
                ", content='" + content + '\'' +
                ", authorName='" + authorName + '\'' +
                ", isReply=" + isReply +
                ", replyToAuthorName='" + replyToAuthorName + '\'' +
                '}';
    }
} 