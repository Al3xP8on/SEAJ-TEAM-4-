package com.neueda.leap.repositories;

import com.neueda.leap.models.Order;
import com.neueda.leap.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    Optional<Order> findByIdempotencyKey(String idempotencyKey);
    

    //When Spring Data JPA sees findByAccountId(String accountId), it's trying to match against the Account's id field (which is Long), not the accountId field. We need to add explicit @Query annotations to clarify the intent:
    @Query("SELECT o FROM Order o WHERE o.account.accountId = :accountId")
    List<Order> findByAccountId(@Param("accountId") String accountId);
    
    List<Order> findByStatus(OrderStatus status);
    
    @Query("SELECT o FROM Order o WHERE o.account.accountId = :accountId AND o.status = :status")
    List<Order> findByAccountIdAndStatus(@Param("accountId") String accountId, @Param("status") OrderStatus status);
    
    /**
     * Find orders with a specific status created before a given timestamp.
     * Used for republishing stale orders (e.g., those created before Kafka was available).
     */
    @Query("SELECT o FROM Order o WHERE o.status = :status AND o.createdAt < :createdBefore")
    List<Order> findByStatusAndCreatedBefore(@Param("status") OrderStatus status, @Param("createdBefore") LocalDateTime createdBefore);
}
