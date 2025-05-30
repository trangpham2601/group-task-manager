package com.example.grouptaskmanager.group;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.grouptaskmanager.R;
import com.example.grouptaskmanager.databinding.ActivityEditGroupBinding;
import com.example.grouptaskmanager.model.Group;
import com.example.grouptaskmanager.repository.GroupRepository;
import com.google.firebase.firestore.DocumentSnapshot;

public class EditGroupActivity extends AppCompatActivity {

    private static final String TAG = "EditGroupActivity";
    private ActivityEditGroupBinding binding;
    private GroupRepository groupRepository;
    private String groupId;
    private Group currentGroup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEditGroupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        groupRepository = new GroupRepository();

        // Lấy groupId từ intent
        groupId = getIntent().getStringExtra("GROUP_ID");
        if (groupId == null) {
            Toast.makeText(this, "Không tìm thấy thông tin nhóm", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupToolbar();
        setupListeners();
        loadGroupDetails();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle(R.string.update_group);
        }
    }

    private void setupListeners() {
        binding.btnSave.setOnClickListener(v -> {
            if (validateInputs()) {
                updateGroup();
            }
        });

        binding.btnCancel.setOnClickListener(v -> finish());
    }

    private void loadGroupDetails() {
        showLoading(true);
        
        groupRepository.getGroupDetails(groupId)
                .addOnSuccessListener(this::processGroupDetails)
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Error loading group details", e);
                    Toast.makeText(this, "Lỗi khi tải thông tin nhóm", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void processGroupDetails(DocumentSnapshot document) {
        if (document.exists()) {
            currentGroup = document.toObject(Group.class);
            
            if (currentGroup != null) {
                // Hiển thị thông tin nhóm hiện tại
                binding.etGroupName.setText(currentGroup.getName());
                binding.etGroupDescription.setText(currentGroup.getDescription());
                
                // Đảm bảo nhóm luôn là private
                currentGroup.setPrivate(true);
            }
        } else {
            Toast.makeText(this, "Không tìm thấy thông tin nhóm", Toast.LENGTH_SHORT).show();
            finish();
        }
        
        showLoading(false);
    }

    private boolean validateInputs() {
        String groupName = binding.etGroupName.getText().toString().trim();
        
        if (TextUtils.isEmpty(groupName)) {
            binding.tilGroupName.setError(getString(R.string.error_group_name_empty));
            return false;
        }
        
        binding.tilGroupName.setError(null);
        return true;
    }

    private void updateGroup() {
        String groupName = binding.etGroupName.getText().toString().trim();
        String groupDescription = binding.etGroupDescription.getText().toString().trim();
        boolean isPrivate = true; // Luôn luôn giữ nhóm là private
        
        showLoading(true);
        
        groupRepository.updateGroupWithPrivacy(groupId, groupName, groupDescription, isPrivate)
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    Toast.makeText(this, R.string.group_updated_success, Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Error updating group", e);
                    Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void showLoading(boolean isLoading) {
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.btnSave.setEnabled(!isLoading);
        binding.btnCancel.setEnabled(!isLoading);
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