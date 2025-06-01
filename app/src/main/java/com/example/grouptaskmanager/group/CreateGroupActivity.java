package com.example.grouptaskmanager.group;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.grouptaskmanager.R;
import com.example.grouptaskmanager.databinding.ActivityCreateGroupBinding;
import com.example.grouptaskmanager.repository.GroupRepository;

public class CreateGroupActivity extends AppCompatActivity {

    private static final String TAG = "CreateGroupActivity";
    private ActivityCreateGroupBinding binding;
    private GroupRepository groupRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateGroupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        groupRepository = new GroupRepository();
        setupListeners();
        setupUI();
    }

    private void setupUI() {
        // Set initial focus to group name input
        binding.etGroupName.requestFocus();
    }

    private void setupListeners() {
        binding.ivBack.setOnClickListener(v -> onBackPressed());
        
        binding.btnCreateGroup.setOnClickListener(v -> {
            if (validateInputs()) {
                createGroup();
            }
        });

        // Add text change listeners for better UX
        binding.etGroupName.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                validateGroupName();
            }
        });
    }

    private boolean validateInputs() {
        return validateGroupName();
    }

    private boolean validateGroupName() {
        String groupName = binding.etGroupName.getText().toString().trim();
        
        if (TextUtils.isEmpty(groupName)) {
            binding.tilGroupName.setError(getString(R.string.error_group_name_empty));
            binding.etGroupName.requestFocus();
            return false;
        }
        
        if (groupName.length() < 2) {
            binding.tilGroupName.setError("Tên nhóm phải có ít nhất 2 ký tự");
            binding.etGroupName.requestFocus();
            return false;
        }
        
        if (groupName.length() > 50) {
            binding.tilGroupName.setError("Tên nhóm không được vượt quá 50 ký tự");
            binding.etGroupName.requestFocus();
            return false;
        }
        
        binding.tilGroupName.setError(null);
        return true;
    }

    private void createGroup() {
        String groupName = binding.etGroupName.getText().toString().trim();
        String groupDescription = binding.etGroupDescription.getText().toString().trim();
        boolean isPrivate = true; // Luôn luôn tạo nhóm private
        
        showLoading(true);
        
        groupRepository.createGroup(groupName, groupDescription, isPrivate)
                .addOnSuccessListener(documentReference -> {
                    showLoading(false);
                    
                    // Show success message with better UX
                    Toast.makeText(this, "🎉 " + getString(R.string.group_created_success), Toast.LENGTH_SHORT).show();
                    
                    // Trả về result để báo cho GroupsFragment biết cần reload
                    setResult(RESULT_OK);
                    finish();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Error creating group", e);
                    
                    String errorMessage = "Có lỗi xảy ra khi tạo nhóm";
                    if (e.getMessage() != null) {
                        if (e.getMessage().contains("network")) {
                            errorMessage = "Vui lòng kiểm tra kết nối mạng";
                        } else if (e.getMessage().contains("permission")) {
                            errorMessage = "Không có quyền thực hiện thao tác này";
                        }
                    }
                    
                    Toast.makeText(this, "❌ " + errorMessage, Toast.LENGTH_LONG).show();
                });
    }

    private void showLoading(boolean isLoading) {
        // Show/hide loading overlay
        binding.loadingOverlay.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        
        // Enable/disable form elements
        binding.btnCreateGroup.setEnabled(!isLoading);
        binding.etGroupName.setEnabled(!isLoading);
        binding.etGroupDescription.setEnabled(!isLoading);
        binding.ivBack.setEnabled(!isLoading);
        
        // Update FAB text
        if (isLoading) {
            binding.btnCreateGroup.setText("Đang tạo...");
            binding.btnCreateGroup.setIcon(null);
        } else {
            binding.btnCreateGroup.setText(getString(R.string.create_group));
            binding.btnCreateGroup.setIconResource(R.drawable.ic_add);
        }
    }

    @Override
    public void onBackPressed() {
        if (binding.loadingOverlay.getVisibility() == View.VISIBLE) {
            // Prevent back press while loading
            return;
        }
        super.onBackPressed();
    }
} 