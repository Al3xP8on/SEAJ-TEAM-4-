package com.neueda.leap.repositories;

import com.neueda.leap.models.Order;
import com.neueda.leap.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// OrderRepository handles database operations for Order entities.
@Repository
public interface OrderRepository extends JpaRepository<Order, String> {

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    List<Order> findByAccountId(String accountId);

    List<Order> findByStatus(OrderStatus status);

    List<Order> findByAccountIdAndStatus(String accountId, OrderStatus status);
}
