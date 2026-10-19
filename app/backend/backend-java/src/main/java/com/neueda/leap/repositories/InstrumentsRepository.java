package com.neueda.leap.repositories;

import com.neueda.leap.models.Instrument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InstrumentsRepository extends JpaRepository<Instrument, String> {
    @Override
    @Query("select i from Instrument i where i.catalogVisible = true")
    List<Instrument> findAll();

    Optional<Instrument> findBySymbol(String symbol);
    @Query("select i from Instrument i where i.catalogVisible = true and i.assetClass = ?1")
    List<Instrument> findByAssetClass(String assetClass);
    @Query("select i from Instrument i where i.catalogVisible = true and i.tradable = ?1")
    List<Instrument> findByTradable(boolean tradable);
    @Query("select i from Instrument i where i.catalogVisible = true and i.assetClass = ?1 and i.tradable = ?2")
    List<Instrument> findByAssetClassAndTradable(String assetClass, boolean tradable);
    @Query("select i from Instrument i where i.catalogVisible = true and i.currency = ?1")
    List<Instrument> findByCurrency(String currency);
}