package com.example.grouptaskmanager.chat;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.grouptaskmanager.R;
import com.example.grouptaskmanager.model.ChatMessage;
import com.example.grouptaskmanager.model.Group;
import com.example.grouptaskmanager.model.GroupWithUnread;
import com.example.grouptaskmanager.repository.ChatRepository;
import com.example.grouptaskmanager.repository.GroupRepository;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ChatFragment extends Fragment implements GroupChatAdapter.OnGroupChatClickListener {

    private static final String TAG = "ChatFragment";
    
    private RecyclerView rvGroups;
    private CircularProgressIndicator progressBar;
    private View layoutEmptyChat;
    
    private GroupRepository groupRepository;
    private ChatRepository chatRepository;
    private GroupChatAdapter groupAdapter;
    private List<GroupWithUnread> groupList;
    private String currentUserId;
    private ListenerRegistration unreadCountListener;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        groupRepository = new GroupRepository();
        chatRepository = new ChatRepository();
        groupList = new ArrayList<>();
        
        FirebaseAuth auth = FirebaseAuth.getInstance();
        currentUserId = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        initViews(view);
        setupRecyclerView();
        loadGroupsWithUnreadCounts();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh data when returning from chat activities
        loadGroupsWithUnreadCounts();
        startListeningForUnreadUpdates();
    }
    
    @Override
    public void onPause() {
        super.onPause();
        stopListeningForUnreadUpdates();
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        stopListeningForUnreadUpdates();
    }

    private void initViews(View view) {
        rvGroups = view.findViewById(R.id.rv_groups);
        progressBar = view.findViewById(R.id.progress_bar);
        layoutEmptyChat = view.findViewById(R.id.layout_empty_chat);
    }

    private void setupRecyclerView() {
        groupAdapter = new GroupChatAdapter(requireContext(), groupList, this);
        rvGroups.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvGroups.setAdapter(groupAdapter);
    }

    private void loadGroupsWithUnreadCounts() {
        if (currentUserId == null) {
            showEmptyState(true);
            return;
        }
        
        showLoading(true);
        
        groupRepository.getUserGroups()
                .addOnSuccessListener(querySnapshot -> {
                    groupList.clear();
                    
                    if (querySnapshot.getDocuments().isEmpty()) {
                        groupAdapter.notifyDataSetChanged();
                        showEmptyState(true);
                        showLoading(false);
                        return;
                    }
                    
                    AtomicInteger pendingRequests = new AtomicInteger(querySnapshot.size() * 2); // 2 requests per group
                    
                    for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                        Group group = document.toObject(Group.class);
                        if (group != null) {
                            group.setId(document.getId());
                            
                            GroupWithUnread groupWithUnread = new GroupWithUnread();
                            groupWithUnread.setGroup(group);
                            groupWithUnread.setUnreadCount(0);
                            groupWithUnread.setLastMessage(null);
                            
                            groupList.add(groupWithUnread);
                            
                            // Load unread count
                            loadUnreadCount(group.getId(), groupWithUnread, pendingRequests);
                            
                            // Load last message
                            loadLastMessage(group.getId(), groupWithUnread, pendingRequests);
                        } else {
                            // Decrease counter if group is null
                            if (pendingRequests.addAndGet(-2) <= 0) {
                                finishLoading();
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading groups", e);
                    showEmptyState(true);
                    showLoading(false);
                });
    }

    private void loadUnreadCount(String groupId, GroupWithUnread groupWithUnread, AtomicInteger pendingRequests) {
        chatRepository.getUnreadMessagesCount(groupId, currentUserId)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        int unreadCount = task.getResult().size();
                        groupWithUnread.setUnreadCount(unreadCount);
                    }
                    
                    if (pendingRequests.decrementAndGet() <= 0) {
                        finishLoading();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading unread count for group: " + groupId, e);
                    if (pendingRequests.decrementAndGet() <= 0) {
                        finishLoading();
                    }
                });
    }

    private void loadLastMessage(String groupId, GroupWithUnread groupWithUnread, AtomicInteger pendingRequests) {
        chatRepository.getLastMessage(groupId)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                        DocumentSnapshot messageDoc = task.getResult().getDocuments().get(0);
                        ChatMessage lastMessage = messageDoc.toObject(ChatMessage.class);
                        if (lastMessage != null) {
                            lastMessage.setId(messageDoc.getId());
                            groupWithUnread.setLastMessage(lastMessage);
                        }
                    }
                    
                    if (pendingRequests.decrementAndGet() <= 0) {
                        finishLoading();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading last message for group: " + groupId, e);
                    if (pendingRequests.decrementAndGet() <= 0) {
                        finishLoading();
                    }
                });
    }

    private void finishLoading() {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                groupAdapter.notifyDataSetChanged();
                showEmptyState(groupList.isEmpty());
                showLoading(false);
            });
        }
    }

    private void showEmptyState(boolean isEmpty) {
        if (isEmpty) {
            rvGroups.setVisibility(View.GONE);
            layoutEmptyChat.setVisibility(View.VISIBLE);
        } else {
            rvGroups.setVisibility(View.VISIBLE);
            layoutEmptyChat.setVisibility(View.GONE);
        }
    }

    private void showLoading(boolean isLoading) {
        if (isLoading) {
            progressBar.setVisibility(View.VISIBLE);
            rvGroups.setVisibility(View.GONE);
            layoutEmptyChat.setVisibility(View.GONE);
        } else {
            progressBar.setVisibility(View.GONE);
        }
    }

    private void startListeningForUnreadUpdates() {
        if (currentUserId == null) return;
        
        // Listen for changes in all groups that user is a member of
        unreadCountListener = chatRepository.listenForUnreadUpdates(currentUserId, this::onUnreadUpdate);
    }

    private void stopListeningForUnreadUpdates() {
        if (unreadCountListener != null) {
            unreadCountListener.remove();
            unreadCountListener = null;
        }
    }

    private void onUnreadUpdate(String groupId, int unreadCount) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                // Find the group in our list and update its unread count
                boolean found = false;
                for (GroupWithUnread groupWithUnread : groupList) {
                    if (groupWithUnread.getGroup().getId().equals(groupId)) {
                        groupWithUnread.setUnreadCount(unreadCount);
                        found = true;
                        break;
                    }
                }
                
                // If group not found in current list, reload the entire list
                if (!found) {
                    loadGroupsWithUnreadCounts();
                } else {
                    // Update the adapter
                    groupAdapter.notifyDataSetChanged();
                }
            });
        }
    }

    @Override
    public void onGroupChatClick(Group group) {
        // Navigate to group chat activity
        Intent intent = new Intent(requireContext(), GroupChatActivity.class);
        intent.putExtra("GROUP_ID", group.getId());
        intent.putExtra("GROUP_NAME", group.getName());
        startActivity(intent);
    }
} 