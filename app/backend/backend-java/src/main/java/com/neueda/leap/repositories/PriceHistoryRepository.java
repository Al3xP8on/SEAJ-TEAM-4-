package com.neueda.leap.repositories;

import com.neueda.leap.models.PriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {
    
    List<PriceHistory> findBySymbol(String symbol);
    
    @Query("SELECT ph FROM PriceHistory ph WHERE ph.symbol = :symbol AND ph.priceDate BETWEEN :fromDate AND :toDate ORDER BY ph.priceDate DESC")
    List<PriceHistory> findBySymbolAndDateRange(
        @Param("symbol") String symbol,
        @Param("fromDate") LocalDate fromDate,
        @Param("toDate") LocalDate toDate
    );
    
    @Query("SELECT ph FROM PriceHistory ph WHERE ph.symbol = :symbol ORDER BY ph.priceDate DESC LIMIT 1")
    PriceHistory findLatestBySymbol(@Param("symbol") String symbol);
}
