package com.example.grouptaskmanager.auth;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.grouptaskmanager.MainActivity;
import com.example.grouptaskmanager.R;
import com.example.grouptaskmanager.databinding.ActivityRegisterBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.example.grouptaskmanager.repository.UserRepository;

public class RegisterActivity extends AppCompatActivity {

    private static final String TAG = "RegisterActivity";
    private ActivityRegisterBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private Uri selectedImageUri;

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
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        setupListeners();
    }

    private void setupListeners() {
        binding.ivBack.setOnClickListener(v -> onBackPressed());
        binding.tvLogin.setOnClickListener(v -> onBackPressed());
        binding.btnRegister.setOnClickListener(v -> handleRegister());
        binding.llProfileImage.setOnClickListener(v -> openImagePicker());
    }

    private void openImagePicker() {
        getContent.launch("image/*");
    }

    private void handleRegister() {
        String name = binding.etName.getText().toString().trim();
        String email = binding.etEmail.getText().toString().trim();
        String password = binding.etPassword.getText().toString().trim();
        String confirmPassword = binding.etConfirmPassword.getText().toString().trim();

        // Validate inputs
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email) ||
                TextUtils.isEmpty(password) || TextUtils.isEmpty(confirmPassword)) {
            Toast.makeText(this, R.string.error_empty_fields, Toast.LENGTH_SHORT).show();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, R.string.error_invalid_email, Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 6) {
            Toast.makeText(this, R.string.error_password_length, Toast.LENGTH_SHORT).show();
            return;
        }

        if (!password.equals(confirmPassword)) {
            Toast.makeText(this, R.string.error_passwords_dont_match, Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(true);
        
        // Create user with email and password
        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = auth.getCurrentUser();
                        if (user != null) {
                            if (selectedImageUri != null) {
                                uploadProfileImage(user, name);
                            } else {
                                updateUserProfile(user, name, null);
                            }
                        } else {
                            showLoading(false);
                            Toast.makeText(this, R.string.error_registration_failed, Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        showLoading(false);
                        Log.w(TAG, "createUserWithEmail:failure", task.getException());
                        Toast.makeText(this, R.string.error_registration_failed, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void uploadProfileImage(FirebaseUser user, String name) {
        StorageReference storageRef = storage.getReference();
        StorageReference profileImagesRef = storageRef.child("profile_images/" + UUID.randomUUID().toString());

        profileImagesRef.putFile(selectedImageUri)
                .addOnSuccessListener(taskSnapshot -> {
                    profileImagesRef.getDownloadUrl().addOnSuccessListener(uri -> {
                        updateUserProfile(user, name, uri);
                    }).addOnFailureListener(e -> {
                        updateUserProfile(user, name, null);
                    });
                })
                .addOnFailureListener(e -> {
                    updateUserProfile(user, name, null);
                });
    }

    private void updateUserProfile(FirebaseUser user, String name, Uri photoUri) {
        UserProfileChangeRequest.Builder profileUpdates = new UserProfileChangeRequest.Builder()
                .setDisplayName(name);

        if (photoUri != null) {
            profileUpdates.setPhotoUri(photoUri);
        }

        user.updateProfile(profileUpdates.build())
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        createUserDocument(user, name, photoUri);
                    } else {
                        createUserDocument(user, name, null);
                    }
                });
    }

    private void createUserDocument(FirebaseUser user, String name, Uri photoUri) {
        UserRepository userRepository = new UserRepository();
        String photoUrl = photoUri != null ? photoUri.toString() : null;
        
        userRepository.createUser(user.getUid(), name, user.getEmail(), photoUrl)
            .addOnCompleteListener(task -> {
                showLoading(false);
                if (task.isSuccessful()) {
                    Toast.makeText(this, R.string.success_registration, Toast.LENGTH_SHORT).show();
                    navigateToMainActivity();
                } else {
                    Log.w(TAG, "Error creating user document", task.getException());
                    navigateToMainActivity();
                }
            });
    }

    private void navigateToMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showLoading(boolean isLoading) {
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.btnRegister.setEnabled(!isLoading);
        binding.etName.setEnabled(!isLoading);
        binding.etEmail.setEnabled(!isLoading);
        binding.etPassword.setEnabled(!isLoading);
        binding.etConfirmPassword.setEnabled(!isLoading);
        binding.llProfileImage.setEnabled(!isLoading);
    }
} 