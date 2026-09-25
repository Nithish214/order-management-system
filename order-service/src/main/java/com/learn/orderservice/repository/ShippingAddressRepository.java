package com.learn.orderservice.repository;

import com.learn.orderservice.entity.ShippingAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ShippingAddressRepository extends JpaRepository<ShippingAddress, Long> {

    // Default first, then newest -- the order the address book and the checkout picker both
    // want to show them in. id as the final tie-break keeps the order stable when two rows
    // share a created_at.
    List<ShippingAddress> findByUserIdOrderByDefaultAddressDescCreatedAtDescIdDesc(Long userId);

    // The ONLY way any caller loads a single address: id AND owner together. There is no
    // findById-then-compare-owner step anywhere that someone could forget -- an address that
    // exists but belongs to someone else is simply "not found" to the wrong caller, which
    // also means its existence can't be probed by guessing ids.
    Optional<ShippingAddress> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    Optional<ShippingAddress> findFirstByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    // Clears the current default in one statement, executed immediately (not deferred to the
    // persistence context's flush) -- that ordering is load-bearing: the partial unique index
    // rejects a second default the instant it's written, so the old default must already be
    // cleared in the database before the new one is set. clearAutomatically drops stale
    // in-memory copies of the rows this just changed.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ShippingAddress a set a.defaultAddress = false where a.user.id = :userId and a.defaultAddress = true")
    int clearDefault(@Param("userId") Long userId);
}
