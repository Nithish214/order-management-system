package com.learn.orderservice.controller;

import com.learn.orderservice.dto.AddressRequest;
import com.learn.orderservice.dto.AddressResponse;
import com.learn.orderservice.service.AddressService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// The signed-in user's own address book. Deliberately /users/me/addresses, not
// /users/{id}/addresses: identity comes from the X-User-Sub header the Gateway sets from the
// caller's validated JWT, never from an id in the URL -- the same reasoning that replaced
// GET /users/{id}/orders with /orders/mine (see OrderController). A path id would make "whose
// addresses" something a client chooses, and every handler would have to remember to check it.
// Here there is nothing to choose: each operation is scoped to the caller by construction.
//
// Any authenticated user (including admins) can keep an address book -- no admin rule needed.
@RestController
@RequestMapping("/users/me/addresses")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    public ResponseEntity<List<AddressResponse>> list(@RequestHeader("X-User-Sub") String cognitoSub) {
        return ResponseEntity.ok(addressService.list(cognitoSub));
    }

    @PostMapping
    public ResponseEntity<AddressResponse> create(
            @Valid @RequestBody AddressRequest request,
            @RequestHeader("X-User-Sub") String cognitoSub
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(addressService.create(cognitoSub, request));
    }

    @PutMapping("/{addressId}")
    public ResponseEntity<AddressResponse> update(
            @PathVariable Long addressId,
            @Valid @RequestBody AddressRequest request,
            @RequestHeader("X-User-Sub") String cognitoSub
    ) {
        return ResponseEntity.ok(addressService.update(cognitoSub, addressId, request));
    }

    @DeleteMapping("/{addressId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long addressId,
            @RequestHeader("X-User-Sub") String cognitoSub
    ) {
        addressService.delete(cognitoSub, addressId);
        return ResponseEntity.noContent().build();
    }
}
