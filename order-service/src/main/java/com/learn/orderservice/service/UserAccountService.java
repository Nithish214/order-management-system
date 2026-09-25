package com.learn.orderservice.service;

import com.learn.orderservice.entity.AppUser;
import com.learn.orderservice.repository.AppUserRepository;
import org.springframework.stereotype.Service;

// The find-or-create every write path needs: a Cognito identity has no app_user row until
// something first needs one, and someone can open their profile or save an address before
// they've ever placed an order. Same placeholder email/name as CartController and
// OrderCreationService use -- the next login's POST /users/me sync replaces them with the
// real ones. (Those two keep their own private copies; this is the shared version for new code.)
@Service
public class UserAccountService {

    private final AppUserRepository appUserRepository;

    public UserAccountService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    public AppUser findOrCreate(String cognitoSub) {
        return appUserRepository.findOrCreate(cognitoSub);
    }
}
