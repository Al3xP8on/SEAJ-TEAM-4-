package com.neueda.leap.repositories;

import com.neueda.leap.models.Positions;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PositionsRepository extends JpaRepository<Positions, Long> {
    List<Positions> findByAccountId(Long accountId);
    
    Optional<Positions> findByAccountIdAndSymbol(Long accountId, String symbol);
    
    Optional<Positions> findByIdAndAccountId(Long positionId, Long accountId);
}
