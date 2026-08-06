package com.naengsam.quick.domain.payment.repository;

import com.naengsam.quick.domain.payment.entity.Exchange;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeRepository extends JpaRepository<Exchange, UUID> {
}
