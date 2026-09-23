package com.neueda.leap.repositories;

import com.neueda.leap.models.Instrument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InstrumentsRepository extends JpaRepository<Instrument, String> {
    Optional<Instrument> findBySymbol(String symbol);
    List<Instrument> findByAssetClass(String assetClass);
    List<Instrument> findByTradable(boolean tradable);
    List<Instrument> findByAssetClassAndTradable(String assetClass, boolean tradable);
    List<Instrument> findByCurrency(String currency);
}