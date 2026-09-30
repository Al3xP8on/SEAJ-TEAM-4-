package com.neueda.leap.services;

import com.neueda.leap.dtos.PositionResponse;
import com.neueda.leap.exceptions.PositionNotFoundException;
import com.neueda.leap.exceptions.AccountNotFoundException;
import com.neueda.leap.exceptions.InsufficientHoldingsException;
import com.neueda.leap.models.Positions;
import com.neueda.leap.repositories.PositionsRepository;
import com.neueda.leap.validators.PositionsValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PositionsService {
    
    @Autowired
    private PositionsRepository positionsRepository;
    
    @Autowired
    private AccountService accountService;
    
    @Autowired
    private PositionsValidator validator;
  
    public List<PositionResponse> getPositions(Long accountId) throws AccountNotFoundException {
        validator.validateAccountId(accountId);
        accountService.getAccount(accountId);
        
        List<Positions> positions = positionsRepository.findByAccountId(accountId);
        return positions.stream().map(this::toResponse).collect(Collectors.toList());
    }
    
    public PositionResponse getPosition(Long accountId, Long positionId) throws AccountNotFoundException, PositionNotFoundException {
        accountService.getAccount(accountId);
        
        Positions position = positionsRepository.findByPositionIdAndAccountId(positionId, accountId).orElseThrow(() -> new PositionNotFoundException(
                String.format("Position %d not found for account %d", positionId, accountId)));
        
        return toResponse(position);
    }
    
    public Optional<PositionResponse> getPositionBySymbol(Long accountId, String symbol) throws AccountNotFoundException {
        validator.validateAccountId(accountId);
        validator.validateSymbol(symbol);
        accountService.getAccount(accountId);
        
        return positionsRepository.findByAccountIdAndSymbol(accountId, symbol).map(this::toResponse);
    }
    
    @Transactional
    public PositionResponse closePosition(Long accountId, Long positionId, Map<String, Object> request) 
            throws AccountNotFoundException, PositionNotFoundException, InsufficientHoldingsException {
        validator.validateAccountId(accountId);
        accountService.getAccount(accountId);
        
        Positions position = positionsRepository.findByPositionIdAndAccountId(positionId, accountId)
                .orElseThrow(() -> new PositionNotFoundException(
                        String.format("Position %d not found for account %d", positionId, accountId)));
        
        BigDecimal closingPrice = new BigDecimal(request.get("closingPrice").toString());
        validator.validatePrice(closingPrice);
        int closingQuantity = 0;

        Object quantityObj = request.get("closingQuantity");
        if (quantityObj instanceof Number) {
            closingQuantity = ((Number) quantityObj).intValue();
        } else if (quantityObj instanceof String) {
            closingQuantity = Integer.parseInt((String) quantityObj);
        }
        validator.validateQuantity(closingQuantity);
        
        if (closingQuantity <= 0 || closingQuantity > position.getQuantity()) {
            throw new IllegalArgumentException(
                    String.format("Invalid closing quantity %d; current position quantity is %d", 
                            closingQuantity, position.getQuantity()));
        }
        
        position.apply(-closingQuantity, closingPrice);

        positionsRepository.save(position);
        return toResponse(position);
    }
    
    private PositionResponse toResponse(Positions position) {
        return new PositionResponse(
            String.valueOf(position.getAccountId()),
            position.getSymbol(),
            position.getQuantity(),
            position.getAverageCost()
        );
    }
}