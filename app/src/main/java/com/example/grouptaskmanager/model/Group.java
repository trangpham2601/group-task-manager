package com.example.grouptaskmanager.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.PropertyName;

import java.util.ArrayList;
import java.util.List;

public class Group {
    private String id;
    private String name;
    private String description;
    private String createdBy;
    private List<String> members;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private String inviteCode;
    @PropertyName("isPrivate")
    private boolean isPrivate;

    // Cần constructor rỗng cho Firestore
    public Group() {
        // Required empty public constructor
        members = new ArrayList<>();
    }

    public Group(String id, String name, String description, String createdBy, List<String> members,
                Timestamp createdAt, Timestamp updatedAt, String inviteCode, boolean isPrivate) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.createdBy = createdBy;
        this.members = members;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.inviteCode = inviteCode;
        this.isPrivate = isPrivate;
    }

    // Getter và Setter
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public List<String> getMembers() {
        return members;
    }

    public void setMembers(List<String> members) {
        this.members = members;
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

    public String getInviteCode() {
        return inviteCode;
    }

    public void setInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }

    @PropertyName("isPrivate")
    public boolean isPrivate() {
        return isPrivate;
    }

    @PropertyName("isPrivate")
    public void setPrivate(boolean isPrivate) {
        this.isPrivate = isPrivate;
    }
} 