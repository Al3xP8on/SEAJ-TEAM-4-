package com.neueda.leap.services;

import com.neueda.leap.models.Instrument;
import com.neueda.leap.repositories.InstrumentsRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class InstrumentsService {
    private final InstrumentsRepository instrumentsRepository;

    public InstrumentsService(InstrumentsRepository instrumentsRepository) {
        this.instrumentsRepository = instrumentsRepository;
    }

    public List<Instrument> getAllInstruments() {
        return instrumentsRepository.findAll();
    }

    public List<Instrument> getTradableInstruments() {
        return instrumentsRepository.findByTradable(true);
    }

    public Optional<Instrument> getInstrumentBySymbol(String symbol) {
        return instrumentsRepository.findBySymbol(symbol);
    }

    public List<Instrument> getInstrumentsByAssetClass(String assetClass) {
        return instrumentsRepository.findByAssetClass(assetClass);
    }

    public List<Instrument> getInstrumentsByCurrency(String currency) {
        return instrumentsRepository.findByCurrency(currency);
    }

    public List<Instrument> getInstrumentsByAssetClassAndTradable(String assetClass, boolean tradable) {
        return instrumentsRepository.findByAssetClassAndTradable(assetClass, tradable);
    }
}