package com.neueda.leap.repositories;

import com.neueda.leap.models.OrderOutboxEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import java.util.List;

public interface OrderOutboxRepository extends JpaRepository<OrderOutboxEvent, Long> {
    // Do not skip locked rows: another relay must not overtake earlier events for an order.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from OrderOutboxEvent e where e.publishedAt is null order by e.id")
    List<OrderOutboxEvent> pending(Pageable page);
}
