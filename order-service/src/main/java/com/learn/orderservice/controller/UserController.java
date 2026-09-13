package com.learn.orderservice.controller;

import com.learn.orderservice.dto.SyncProfileRequest;
import com.learn.orderservice.dto.UserResponse;
import com.learn.orderservice.entity.AppUser;
import com.learn.orderservice.repository.AppUserRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;

// Read-only lookup so a client (Postman/Swagger, no frontend yet) can discover valid
// userIds before calling POST /orders, without querying the database directly.
@RestController
@RequestMapping("/users")
public class UserController {

    private final AppUserRepository appUserRepository;

    public UserController(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        List<UserResponse> users = appUserRepository.findAll()
                .stream()
                .map(UserResponse::from)
                .toList();
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUser(@PathVariable Long id) {
        AppUser user = appUserRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
        return ResponseEntity.ok(UserResponse.from(user));
    }

    // Called once right after every login (see the frontend's AuthContext) with the email
    // the user just typed in -- authenticated, so identity comes from the trusted
    // X-User-Sub header, never the request body itself. This is what replaces the
    // synthesized "<sub>@cognito.local" placeholder with a real address: find-or-create
    // (same as createUserForCognitoSub in OrderController) if this is the very first
    // sync, or just overwrite the placeholder/stale email if the row already exists.
    // Being idempotent and run on every login means an admin-created account (like the
    // testuser/admin test users, which never went through self-service signup) also
    // gets its email fixed the next time it logs in, at no extra cost.
    @PostMapping("/me")
    @Transactional
    public ResponseEntity<UserResponse> syncMyProfile(
            @Valid @RequestBody SyncProfileRequest request,
            @RequestHeader("X-User-Sub") String cognitoSub
    ) {
        AppUser user = appUserRepository.findByCognitoSub(cognitoSub).orElseGet(AppUser::new);
        user.setCognitoSub(cognitoSub);
        user.setEmail(request.getEmail());
        // Re-derive whenever it's missing OR still the generic placeholder -- checking only
        // for null would never fire for anyone auto-created by createOrder before this
        // endpoint existed, since that path always sets a non-null "Cognito User" placeholder,
        // leaving their name stuck forever even after their email gets fixed below it.
        if (user.getName() == null || "Cognito User".equals(user.getName())) {
            user.setName(deriveDisplayName(request.getEmail()));
        }
        AppUser saved = appUserRepository.save(user);
        return ResponseEntity.ok(UserResponse.from(saved));
    }

    // No dedicated "name" field on the signup form (kept deliberately minimal, per the
    // original brief) -- this derives something more presentable than a flat "Cognito
    // User" placeholder from the email's local part, e.g. "testuser@example.com" -> "Testuser".
    private String deriveDisplayName(String email) {
        String localPart = email.split("@")[0];
        if (localPart.isEmpty()) {
            return "Cognito User";
        }
        return localPart.substring(0, 1).toUpperCase(Locale.ROOT) + localPart.substring(1);
    }
}
