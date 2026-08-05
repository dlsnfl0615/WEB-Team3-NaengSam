package com.naengsam.quick.domain.payment.repository;

import com.naengsam.quick.domain.payment.entity.PaymentMethod;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, UUID> {
}
