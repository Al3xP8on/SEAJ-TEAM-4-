package com.neueda.leap.repositories;

import com.neueda.leap.models.ComplianceCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ComplianceCheckRepository extends JpaRepository<ComplianceCheck, Long> {

    List<ComplianceCheck> findByCheckGroupIdOrderById(UUID checkGroupId);

    List<ComplianceCheck> findByOrderIdOrderById(UUID orderId);

    /** Links a submission's checks to the order it created, in the order's own transaction */
    @Modifying
    @Query("update ComplianceCheck c set c.orderId = :orderId where c.checkGroupId = :groupId")
    int linkOrder(@Param("groupId") UUID groupId, @Param("orderId") UUID orderId);
}
