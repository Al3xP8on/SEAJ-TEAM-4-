package com.neueda.leap.repositories;

import com.neueda.leap.models.ComplianceCheck;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ComplianceCheckRepository extends JpaRepository<ComplianceCheck, Long> {

    List<ComplianceCheck> findByCheckGroupIdOrderById(UUID checkGroupId);

    List<ComplianceCheck> findByOrderIdOrderById(UUID orderId);

    /**
     * The newest submissions (check groups) matching the filters, newest first. Empty strings mean
     * "any", which avoids untyped null parameters on Postgres. The decision is worked out from each
     * group's rows: BLOCKED if any rule blocked, NEEDS_ACKNOWLEDGEMENT if a warning wasn't
     * acknowledged, otherwise ALLOWED.
     */
    @Query("""
            select c.checkGroupId from ComplianceCheck c
            where (:accountId = '' or c.accountId = :accountId)
              and (:symbol = '' or c.symbol = :symbol)
            group by c.checkGroupId
            having :decision = ''
                or (:decision = 'BLOCKED'
                    and sum(case when c.outcome = com.neueda.leap.compliance.ComplianceOutcome.BLOCK then 1 else 0 end) > 0)
                or (:decision = 'NEEDS_ACKNOWLEDGEMENT'
                    and sum(case when c.outcome = com.neueda.leap.compliance.ComplianceOutcome.BLOCK then 1 else 0 end) = 0
                    and sum(case when c.outcome = com.neueda.leap.compliance.ComplianceOutcome.WARN and c.acknowledged = false then 1 else 0 end) > 0)
                or (:decision = 'ALLOWED'
                    and sum(case when c.outcome = com.neueda.leap.compliance.ComplianceOutcome.BLOCK then 1 else 0 end) = 0
                    and sum(case when c.outcome = com.neueda.leap.compliance.ComplianceOutcome.WARN and c.acknowledged = false then 1 else 0 end) = 0)
            order by max(c.id) desc
            """)
    List<UUID> recentGroups(@Param("accountId") String accountId, @Param("symbol") String symbol,
                            @Param("decision") String decision, Pageable page);

    List<ComplianceCheck> findByCheckGroupIdInOrderById(Collection<UUID> checkGroupIds);

    /** Links a submission's checks to the order it created, in the order's own transaction */
    @Modifying
    @Query("update ComplianceCheck c set c.orderId = :orderId where c.checkGroupId = :groupId")
    int linkOrder(@Param("groupId") UUID groupId, @Param("orderId") UUID orderId);
}
