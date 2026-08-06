package com.naengsam.quick.domain.payment.repository;

import com.naengsam.quick.domain.payment.entity.Payment;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
}
