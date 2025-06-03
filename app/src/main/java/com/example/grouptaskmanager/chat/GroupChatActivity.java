package com.example.grouptaskmanager.chat;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.grouptaskmanager.databinding.ActivityGroupChatBinding;
import com.example.grouptaskmanager.model.ChatMessage;
import com.example.grouptaskmanager.repository.ChatRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

public class GroupChatActivity extends AppCompatActivity implements ChatRepository.OnMessagesChangedListener {

    private static final String TAG = "GroupChatActivity";
    
    private ActivityGroupChatBinding binding;
    private ChatRepository chatRepository;
    private ChatMessageAdapter messageAdapter;
    private List<ChatMessage> messageList;
    private String groupId;
    private String groupName;
    private String currentUserId;
    private ListenerRegistration messagesListener;
    private boolean isFirstLoad = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityGroupChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Get data from intent
        groupId = getIntent().getStringExtra("GROUP_ID");
        groupName = getIntent().getStringExtra("GROUP_NAME");
        
        if (groupId == null) {
            Toast.makeText(this, "Không tìm thấy thông tin nhóm", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null ? 
                       FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        
        chatRepository = new ChatRepository();
        messageList = new ArrayList<>();
        
        setupToolbar();
        setupRecyclerView();
        setupListeners();
        loadMessages();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Mark group as read when user enters
        markGroupAsRead();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Remove listener to prevent memory leaks
        if (messagesListener != null) {
            messagesListener.remove();
        }
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle(groupName != null ? groupName : "Chat nhóm");
        }
    }

    private void setupRecyclerView() {
        messageAdapter = new ChatMessageAdapter(this, messageList, currentUserId);
        
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true); // Start from bottom
        
        binding.rvMessages.setLayoutManager(layoutManager);
        binding.rvMessages.setAdapter(messageAdapter);
    }

    private void setupListeners() {
        binding.btnSend.setOnClickListener(v -> sendMessage());
        
        binding.etMessage.setOnEditorActionListener((v, actionId, event) -> {
            sendMessage();
            return true;
        });
    }

    private void loadMessages() {
        showLoading(true);
        // Setup realtime listener
        messagesListener = chatRepository.getMessagesRealtime(groupId, this);
    }

    private void sendMessage() {
        String messageText = binding.etMessage.getText().toString().trim();
        
        if (TextUtils.isEmpty(messageText)) {
            return;
        }
        
        // Clear input immediately for better UX
        binding.etMessage.setText("");
        
        // Send message
        chatRepository.sendMessage(groupId, messageText)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "Message sent successfully");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error sending message", e);
                    Toast.makeText(this, "Không thể gửi tin nhắn", Toast.LENGTH_SHORT).show();
                    // Restore message text on failure
                    binding.etMessage.setText(messageText);
                });
    }

    private void markGroupAsRead() {
        if (groupId != null && currentUserId != null) {
            chatRepository.markGroupAsRead(groupId)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Group marked as read");
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error marking group as read", e);
                    });
        }
    }

    private void showLoading(boolean isLoading) {
        if (isLoading) {
            binding.progressBar.setVisibility(View.VISIBLE);
            binding.rvMessages.setVisibility(View.GONE);
            binding.layoutEmptyChat.setVisibility(View.GONE);
        } else {
            binding.progressBar.setVisibility(View.GONE);
            
            if (messageList.isEmpty()) {
                binding.rvMessages.setVisibility(View.GONE);
                binding.layoutEmptyChat.setVisibility(View.VISIBLE);
            } else {
                binding.rvMessages.setVisibility(View.VISIBLE);
                binding.layoutEmptyChat.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void onMessagesChanged(QuerySnapshot snapshot) {
        if (snapshot != null) {
            if (isFirstLoad) {
                // First load - add all messages
                messageList.clear();
                for (DocumentChange dc : snapshot.getDocumentChanges()) {
                    if (dc.getType() == DocumentChange.Type.ADDED) {
                        ChatMessage message = dc.getDocument().toObject(ChatMessage.class);
                        message.setId(dc.getDocument().getId());
                        messageList.add(message);
                    }
                }
                messageAdapter.notifyDataSetChanged();
                
                // Scroll to bottom
                if (!messageList.isEmpty()) {
                    binding.rvMessages.scrollToPosition(messageList.size() - 1);
                }
                
                showLoading(false);
                isFirstLoad = false;
            } else {
                // Subsequent updates
                for (DocumentChange dc : snapshot.getDocumentChanges()) {
                    ChatMessage message = dc.getDocument().toObject(ChatMessage.class);
                    message.setId(dc.getDocument().getId());
                    
                    switch (dc.getType()) {
                        case ADDED:
                            // Add new message
                            messageList.add(message);
                            messageAdapter.notifyItemInserted(messageList.size() - 1);
                            // Scroll to bottom for new messages
                            binding.rvMessages.scrollToPosition(messageList.size() - 1);
                            
                            // Mark as read when receiving new messages while active
                            markGroupAsRead();
                            break;
                        case MODIFIED:
                            // Update existing message
                            for (int i = 0; i < messageList.size(); i++) {
                                if (messageList.get(i).getId().equals(message.getId())) {
                                    messageList.set(i, message);
                                    messageAdapter.notifyItemChanged(i);
                                    break;
                                }
                            }
                            break;
                        case REMOVED:
                            // Remove message
                            messageList.removeIf(msg -> msg.getId().equals(message.getId()));
                            messageAdapter.notifyDataSetChanged();
                            break;
                    }
                }
                
                // Update empty state
                showLoading(false);
            }
        }
    }

    @Override
    public void onError(Exception e) {
        Log.e(TAG, "Error listening to messages", e);
        Toast.makeText(this, "Lỗi kết nối chat", Toast.LENGTH_SHORT).show();
        showLoading(false);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
} 