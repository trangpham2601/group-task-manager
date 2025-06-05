package com.example.grouptaskmanager.profile;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.grouptaskmanager.R;
import com.example.grouptaskmanager.databinding.ActivityEditProfileBinding;
import com.example.grouptaskmanager.repository.UserRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.UUID;

public class EditProfileActivity extends AppCompatActivity {

    private static final String TAG = "EditProfileActivity";
    private ActivityEditProfileBinding binding;
    private FirebaseAuth auth;
    private FirebaseStorage storage;
    private UserRepository userRepository;
    private Uri selectedImageUri;
    private String currentPhotoUrl;

    private final ActivityResultLauncher<String> getContent = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    selectedImageUri = uri;
                    Glide.with(this)
                            .load(uri)
                            .circleCrop()
                            .into(binding.ivProfile);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEditProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        storage = FirebaseStorage.getInstance();
        userRepository = new UserRepository();

        setupToolbar();
        setupListeners();
        loadCurrentUserInfo();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle("Chỉnh sửa hồ sơ");
        }
    }

    private void setupListeners() {
        binding.llProfileImage.setOnClickListener(v -> openImagePicker());
        binding.btnSave.setOnClickListener(v -> saveProfile());
        binding.btnCancel.setOnClickListener(v -> finish());
    }

    private void openImagePicker() {
        getContent.launch("image/*");
    }

    private void loadCurrentUserInfo() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            // Load display name
            String displayName = currentUser.getDisplayName();
            if (!TextUtils.isEmpty(displayName)) {
                binding.etName.setText(displayName);
            }

            // Load email (read-only)
            binding.etEmail.setText(currentUser.getEmail());

            // Load profile photo
            Uri photoUrl = currentUser.getPhotoUrl();
            if (photoUrl != null) {
                currentPhotoUrl = photoUrl.toString();
                Glide.with(this)
                        .load(photoUrl)
                        .circleCrop()
                        .placeholder(R.drawable.profile_placeholder)
                        .into(binding.ivProfile);
            }
        }
    }

    private void saveProfile() {
        String newName = binding.etName.getText().toString().trim();
        
        if (TextUtils.isEmpty(newName)) {
            binding.etName.setError("Tên không được để trống");
            return;
        }

        showLoading(true);

        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            showLoading(false);
            Toast.makeText(this, "Lỗi: Không tìm thấy thông tin người dùng", Toast.LENGTH_SHORT).show();
            return;
        }

        // If user selected a new image, upload it first
        if (selectedImageUri != null) {
            uploadProfileImage(currentUser, newName);
        } else {
            // Just update the display name
            updateUserProfile(currentUser, newName, currentPhotoUrl);
        }
    }

    private void uploadProfileImage(FirebaseUser user, String name) {
        StorageReference storageRef = storage.getReference();
        StorageReference profileImagesRef = storageRef.child("profile_images/" + user.getUid() + "/" + UUID.randomUUID().toString());

        profileImagesRef.putFile(selectedImageUri)
                .addOnSuccessListener(taskSnapshot -> {
                    profileImagesRef.getDownloadUrl().addOnSuccessListener(uri -> {
                        updateUserProfile(user, name, uri.toString());
                    }).addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to get download URL", e);
                        // Still update with old photo URL or null
                        updateUserProfile(user, name, currentPhotoUrl);
                    });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to upload image", e);
                    Toast.makeText(this, "Lỗi khi tải ảnh lên. Chỉ cập nhật tên.", Toast.LENGTH_SHORT).show();
                    // Still update the name even if image upload fails
                    updateUserProfile(user, name, currentPhotoUrl);
                });
    }

    private void updateUserProfile(FirebaseUser user, String name, String photoUrl) {
        UserProfileChangeRequest.Builder profileUpdates = new UserProfileChangeRequest.Builder()
                .setDisplayName(name);

        if (!TextUtils.isEmpty(photoUrl)) {
            profileUpdates.setPhotoUri(Uri.parse(photoUrl));
        }

        user.updateProfile(profileUpdates.build())
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // Also update in Firestore
                        updateFirestoreUserDocument(user.getUid(), name, photoUrl);
                    } else {
                        showLoading(false);
                        Log.e(TAG, "Failed to update Firebase Auth profile", task.getException());
                        Toast.makeText(this, "Lỗi khi cập nhật hồ sơ", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void updateFirestoreUserDocument(String userId, String name, String photoUrl) {
        userRepository.updateUserProfile(userId, name, photoUrl)
                .addOnCompleteListener(task -> {
                    showLoading(false);
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Cập nhật hồ sơ thành công", Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        finish();
                    } else {
                        Log.e(TAG, "Failed to update Firestore user document", task.getException());
                        Toast.makeText(this, "Cập nhật thành công nhưng có lỗi đồng bộ dữ liệu", Toast.LENGTH_LONG).show();
                        setResult(RESULT_OK);
                        finish();
                    }
                });
    }

    private void showLoading(boolean isLoading) {
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.btnSave.setEnabled(!isLoading);
        binding.btnCancel.setEnabled(!isLoading);
        binding.etName.setEnabled(!isLoading);
        binding.llProfileImage.setEnabled(!isLoading);
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