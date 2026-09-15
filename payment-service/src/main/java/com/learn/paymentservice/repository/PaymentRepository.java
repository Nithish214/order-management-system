package com.learn.paymentservice.repository;

import com.learn.paymentservice.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
}
