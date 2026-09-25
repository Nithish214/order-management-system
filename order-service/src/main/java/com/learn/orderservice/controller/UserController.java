package com.learn.orderservice.controller;

import com.learn.orderservice.dto.ProfileResponse;
import com.learn.orderservice.dto.SyncProfileRequest;
import com.learn.orderservice.dto.UpdateProfileRequest;
import com.learn.orderservice.dto.UserResponse;
import com.learn.orderservice.entity.AppUser;
import com.learn.orderservice.exception.ForbiddenException;
import com.learn.orderservice.repository.AppUserRepository;
import com.learn.orderservice.service.UserAccountService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;

// /users/me (below) is each signed-in user's own profile. GET /users and GET /users/{id} are
// the directory of ALL accounts -- originally a read-only lookup so a client (Postman/Swagger,
// no frontend yet) could discover valid userIds. Nothing uses them any more, and they expose
// every customer's name and email, so both are admin-only: enforced at the Gateway
// (SecurityConfig) AND re-checked here from X-User-Is-Admin, so one misconfigured rule in
// either place isn't the only thing between a shopper and everyone else's details.
@RestController
@RequestMapping("/users")
public class UserController {

    private final AppUserRepository appUserRepository;
    private final UserAccountService userAccountService;

    public UserController(AppUserRepository appUserRepository, UserAccountService userAccountService) {
        this.appUserRepository = appUserRepository;
        this.userAccountService = userAccountService;
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUsers(
            @RequestHeader(value = "X-User-Is-Admin", defaultValue = "false") boolean isAdmin
    ) {
        requireAdmin(isAdmin);
        List<UserResponse> users = appUserRepository.findAll()
                .stream()
                .map(UserResponse::from)
                .toList();
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUser(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Is-Admin", defaultValue = "false") boolean isAdmin
    ) {
        requireAdmin(isAdmin);
        AppUser user = appUserRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
        return ResponseEntity.ok(UserResponse.from(user));
    }

    // The caller's own profile, including the private fields (phone number) that UserResponse
    // above deliberately never carries -- see ProfileResponse. "me" is a literal path segment,
    // which Spring matches ahead of the /{id} pattern above, so this doesn't collide with it.
    @GetMapping("/me")
    @Transactional(readOnly = true)
    public ResponseEntity<ProfileResponse> getMyProfile(@RequestHeader("X-User-Sub") String cognitoSub) {
        AppUser user = appUserRepository.findByCognitoSub(cognitoSub)
                .orElseThrow(() -> new EntityNotFoundException("Profile not found"));
        return ResponseEntity.ok(ProfileResponse.from(user));
    }

    // Edits the caller's own name and phone number. Identity from the trusted header, never the
    // body or the URL -- there is no way to name a different user here. Email isn't editable
    // (it's the Cognito login; see UpdateProfileRequest). find-or-create rather than
    // "must exist": someone can reach their profile before ever having ordered.
    @PutMapping("/me")
    @Transactional
    public ResponseEntity<ProfileResponse> updateMyProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            @RequestHeader("X-User-Sub") String cognitoSub
    ) {
        AppUser user = userAccountService.findOrCreate(cognitoSub);
        user.setName(request.name().strip());
        String phone = request.phoneNumber();
        user.setPhoneNumber(phone == null || phone.isBlank() ? null : phone.strip());
        return ResponseEntity.ok(ProfileResponse.from(appUserRepository.save(user)));
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

    // Defaults to false when the header is absent, so a request that somehow reached this service
    // without going through the Gateway is treated as NOT an admin -- the safe side.
    private void requireAdmin(boolean isAdmin) {
        if (!isAdmin) {
            throw new ForbiddenException("The user directory is only available to admins");
        }
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
