package com.example.grouptaskmanager.group;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.grouptaskmanager.R;
import com.example.grouptaskmanager.databinding.ActivityJoinGroupBinding;
import com.example.grouptaskmanager.repository.GroupRepository;

public class JoinGroupActivity extends AppCompatActivity {

    private static final String TAG = "JoinGroupActivity";
    public static final String EXTRA_GROUP_JOINED = "group_joined";
    private ActivityJoinGroupBinding binding;
    private GroupRepository groupRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityJoinGroupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        groupRepository = new GroupRepository();
        setupListeners();
        setupUI();
    }

    private void setupUI() {
        // Set initial focus to invite code input
        binding.etInviteCode.requestFocus();
    }

    private void setupListeners() {
        binding.ivBack.setOnClickListener(v -> onBackPressed());
        
        binding.btnJoinGroup.setOnClickListener(v -> {
            if (validateInputs()) {
                joinGroup();
            }
        });
        
        binding.btnScanQr.setOnClickListener(v -> {
            // Future: Implement QR scanning
            Toast.makeText(this, "📷 Tính năng quét QR sẽ có trong phiên bản tới", Toast.LENGTH_LONG).show();
        });

        // Add text change listeners for better UX
        binding.etInviteCode.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                validateInviteCode();
            }
        });

        // Auto-format invite code as user types
        binding.etInviteCode.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {
                // Clear error when user starts typing
                if (s.length() > 0 && binding.tilInviteCode.getError() != null) {
                    binding.tilInviteCode.setError(null);
                }
                
                // Enable/disable join button based on input
                binding.btnJoinGroup.setEnabled(s.length() == 6);
            }
        });
    }

    private boolean validateInputs() {
        return validateInviteCode();
    }

    private boolean validateInviteCode() {
        String inviteCode = binding.etInviteCode.getText().toString().trim();
        
        if (TextUtils.isEmpty(inviteCode)) {
            binding.tilInviteCode.setError(getString(R.string.error_invite_code_empty));
            binding.etInviteCode.requestFocus();
            return false;
        }
        
        if (inviteCode.length() != 6) {
            binding.tilInviteCode.setError("Mã mời phải có đúng 6 ký tự");
            binding.etInviteCode.requestFocus();
            return false;
        }
        
        // Check if contains only alphanumeric characters
        if (!inviteCode.matches("[A-Z0-9]{6}")) {
            binding.tilInviteCode.setError("Mã mời chỉ chứa chữ cái và số");
            binding.etInviteCode.requestFocus();
            return false;
        }
        
        binding.tilInviteCode.setError(null);
        return true;
    }

    private void joinGroup() {
        String inviteCode = binding.etInviteCode.getText().toString().trim().toUpperCase();
        
        showLoading(true);
        
        groupRepository.joinGroupByInviteCode(inviteCode)
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    showLoading(false);
                    
                    if (queryDocumentSnapshots.isEmpty()) {
                        binding.tilInviteCode.setError("Không tìm thấy nhóm với mã này");
                        Toast.makeText(this, "❌ " + getString(R.string.group_join_failed), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "🎉 " + getString(R.string.group_join_success), Toast.LENGTH_SHORT).show();
                        
                        // Trả về result để báo cho GroupsFragment biết cần reload
                        Intent resultIntent = new Intent();
                        resultIntent.putExtra(EXTRA_GROUP_JOINED, true);
                        setResult(RESULT_OK, resultIntent);
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Error joining group", e);
                    
                    String errorMessage = "Có lỗi xảy ra khi tham gia nhóm";
                    if (e.getMessage() != null) {
                        if (e.getMessage().contains("already a member")) {
                            errorMessage = "Bạn đã là thành viên của nhóm này";
                        } else if (e.getMessage().contains("network")) {
                            errorMessage = "Vui lòng kiểm tra kết nối mạng";
                        } else if (e.getMessage().contains("permission")) {
                            errorMessage = "Không có quyền tham gia nhóm này";
                        }
                    }
                    
                    Toast.makeText(this, "❌ " + errorMessage, Toast.LENGTH_LONG).show();
                });
    }

    private void showLoading(boolean isLoading) {
        // Show/hide loading overlay
        binding.loadingOverlay.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        
        // Enable/disable form elements
        binding.btnJoinGroup.setEnabled(!isLoading);
        binding.btnScanQr.setEnabled(!isLoading);
        binding.etInviteCode.setEnabled(!isLoading);
        binding.ivBack.setEnabled(!isLoading);
        
        // Update FAB text
        if (isLoading) {
            binding.btnJoinGroup.setText("Đang tham gia...");
            binding.btnJoinGroup.setIcon(null);
        } else {
            binding.btnJoinGroup.setText(getString(R.string.join_group));
            binding.btnJoinGroup.setIcon(getDrawable(R.drawable.ic_group_add));
            
            // Re-enable join button only if invite code is valid
            String inviteCode = binding.etInviteCode.getText().toString().trim();
            binding.btnJoinGroup.setEnabled(inviteCode.length() == 6);
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