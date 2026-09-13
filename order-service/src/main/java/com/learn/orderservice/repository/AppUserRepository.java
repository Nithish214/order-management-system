package com.learn.orderservice.repository;

import com.learn.orderservice.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByCognitoSub(String cognitoSub);
}
