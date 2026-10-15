package com.neueda.leap.services;

import com.neueda.leap.utils.LogMaskingUtil;

import com.neueda.leap.models.PriceHistory;
import com.neueda.leap.repositories.PriceHistoryRepository;
import com.neueda.leap.validators.PriceHistoryValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class PriceHistoryService {
    
    private static final Logger logger = LoggerFactory.getLogger(PriceHistoryService.class);
    
    @Autowired
    private PriceHistoryRepository priceHistoryRepository;
    
    @Autowired
    private PriceHistoryValidator validator;
    
    public List<PriceHistory> getPriceHistoryBySymbol(String symbol) {
        validator.validateSymbol(symbol);
        if (logger.isInfoEnabled()) {
            logger.info("Fetching price history for symbol: {}", LogMaskingUtil.sanitize(symbol));
        }
        List<PriceHistory> history = priceHistoryRepository.findBySymbol(symbol);
        if (logger.isInfoEnabled()) {
            logger.info("Found {} price history records for symbol {}", history.size(), LogMaskingUtil.sanitize(symbol));
        }
        return history;
    }
    
    public List<PriceHistory> getPriceHistoryBySymbolAndDateRange(String symbol, LocalDate fromDate, LocalDate toDate) {
        validator.validateSymbol(symbol);
        if (logger.isInfoEnabled()) {
            logger.info("Fetching price history for symbol: {} from {} to {}", LogMaskingUtil.sanitize(symbol), LogMaskingUtil.sanitize(fromDate), LogMaskingUtil.sanitize(toDate));
        }
        
        if (fromDate.isAfter(toDate)) {
            if (logger.isWarnEnabled()) {
                logger.warn("Invalid date range: fromDate {} is after toDate {}", LogMaskingUtil.sanitize(fromDate), LogMaskingUtil.sanitize(toDate));
            }
            throw new IllegalArgumentException("fromDate must be before or equal to toDate");
        }
        
        List<PriceHistory> history = priceHistoryRepository.findBySymbolAndDateRange(symbol, fromDate, toDate);
        if (logger.isInfoEnabled()) {
            logger.info("Found {} price history records for symbol {} in date range", history.size(), LogMaskingUtil.sanitize(symbol));
        }
        return history;
    }
    
    public Optional<PriceHistory> getLatestPriceHistory(String symbol) {
        validator.validateSymbol(symbol);
        if (logger.isInfoEnabled()) {
            logger.info("Fetching latest price history for symbol: {}", LogMaskingUtil.sanitize(symbol));
        }
        PriceHistory latestPrice = priceHistoryRepository.findLatestBySymbol(symbol);
        if (latestPrice != null) {
            if (logger.isInfoEnabled()) {
                logger.info("Found latest price history for symbol {}", LogMaskingUtil.sanitize(symbol));
            }
            return Optional.of(latestPrice);
        }
        if (logger.isWarnEnabled()) {
            logger.warn("No price history found for symbol {}", LogMaskingUtil.sanitize(symbol));
        }
        return Optional.empty();
    }
    
    public PriceHistory savePriceHistory(PriceHistory priceHistory) {
        validator.validateSymbol(priceHistory.getSymbol());
        validator.validateOHLCRelationships(priceHistory.getOpen(), priceHistory.getHigh(), priceHistory.getLow(), priceHistory.getClose());
        if (logger.isInfoEnabled()) {
            logger.info("Saving price history for symbol: {} on date: {}", priceHistory.getSymbol(), priceHistory.getPriceDate());
        }
        PriceHistory savedPriceHistory = priceHistoryRepository.save(priceHistory);
        if (logger.isInfoEnabled()) {
            logger.info("Price history saved successfully for symbol: {}", priceHistory.getSymbol());
        }
        return savedPriceHistory;
    }
}
