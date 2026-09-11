package com.learn.orderservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
@SequenceGenerator(name = "app_user_seq", sequenceName = "app_user_seq", allocationSize = 1)
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "app_user_seq")
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String name;

    // Links this row to a Cognito identity via its "sub" claim (permanent, immutable,
    // unique per user -- the OIDC-standard identifier, unlike email which can change).
    // Null for rows never created through Cognito (the original Alice/Bob seed data).
    @Column(name = "cognito_sub", unique = true)
    private String cognitoSub;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Order> orders = new ArrayList<>();
}
