package com.example.grouptaskmanager.model;

import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.List;

public class User {
    private String id;
    private String name;
    private String email;
    private String photoURL;
    private List<String> groups;
    private Timestamp joinedAt;
    private String fcmToken; // Firebase Cloud Messaging token for notifications

    // Empty constructor for Firestore
    public User() {
        this.groups = new ArrayList<>();
    }

    // Constructor with required fields
    public User(String id, String name, String email) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.groups = new ArrayList<>();
    }

    // Full constructor
    public User(String id, String name, String email, String photoURL, List<String> groups, Timestamp joinedAt) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.photoURL = photoURL;
        this.groups = groups != null ? groups : new ArrayList<>();
        this.joinedAt = joinedAt;
    }

    // Getters and setters
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhotoURL() {
        return photoURL;
    }

    public void setPhotoURL(String photoURL) {
        this.photoURL = photoURL;
    }

    public List<String> getGroups() {
        return groups;
    }

    public void setGroups(List<String> groups) {
        this.groups = groups;
    }

    public Timestamp getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(Timestamp joinedAt) {
        this.joinedAt = joinedAt;
    }

    public String getFcmToken() {
        return fcmToken;
    }

    public void setFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }
} 