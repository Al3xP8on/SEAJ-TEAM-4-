package com.neueda.leap.services;

import com.neueda.leap.models.Instrument;
import com.neueda.leap.repositories.InstrumentsRepository;
import com.neueda.leap.validators.InstrumentsValidator;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class InstrumentsService {
    private final InstrumentsRepository instrumentsRepository;
    private final InstrumentsValidator validator;

    public InstrumentsService(InstrumentsRepository instrumentsRepository, InstrumentsValidator validator) {
        this.instrumentsRepository = instrumentsRepository;
        this.validator = validator;
    }

    public List<Instrument> getAllInstruments() {
        return instrumentsRepository.findAll();
    }

    public List<Instrument> getTradableInstruments() {
        return instrumentsRepository.findByTradable(true);
    }

    public Optional<Instrument> getInstrumentBySymbol(String symbol) {
        String validatedSymbol = validator.validateSymbol(symbol);
        return instrumentsRepository.findBySymbol(validatedSymbol);
    }

    public List<Instrument> getInstrumentsByAssetClass(String assetClass) {
        String validatedAssetClass = validator.validateAssetClass(assetClass);
        return instrumentsRepository.findByAssetClass(validatedAssetClass);
    }

    public List<Instrument> getInstrumentsByCurrency(String currency) {
        String validatedCurrency = validator.validateCurrency(currency);
        return instrumentsRepository.findByCurrency(validatedCurrency);
    }

    public List<Instrument> getInstrumentsByAssetClassAndTradable(String assetClass, boolean tradable) {
        String validatedAssetClass = validator.validateAssetClass(assetClass);
        return instrumentsRepository.findByAssetClassAndTradable(validatedAssetClass, tradable);
    }
}