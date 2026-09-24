package com.neueda.leap.controllers;

import com.neueda.leap.dtos.PositionResponse;
import com.neueda.leap.exceptions.PositionNotFoundException;
import com.neueda.leap.exceptions.AccountNotFoundException;
import com.neueda.leap.exceptions.InsufficientHoldingsException;
import com.neueda.leap.services.PositionsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/accounts")
public class PositionsController {
    
    @Autowired
    private PositionsService positionsService;
    
    @GetMapping("/{accountId}/positions")
    public ResponseEntity<?> getPositions(@PathVariable Long accountId) {
        try {
            List<PositionResponse> positions = positionsService.getPositions(accountId);
            return ResponseEntity.ok(positions);
        } catch (AccountNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Account " + accountId + " not found");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error retrieving positions: " + e.getMessage());
        }
    }
    
    @GetMapping("/{accountId}/positions/{positionId}")
    public ResponseEntity<?> getPosition(@PathVariable Long accountId, @PathVariable Long positionId) {
        try {
            PositionResponse position = positionsService.getPosition(accountId, positionId);
            return ResponseEntity.ok(position);
        } catch (AccountNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Account " + accountId + " not found");
        } catch (PositionNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error retrieving position: " + e.getMessage());
        }
    }
    
    @GetMapping("/{accountId}/positions/{symbol}")
    public ResponseEntity<?> getPositionBySymbol(@PathVariable Long accountId, @PathVariable String symbol) {
        try {
            Optional<PositionResponse> position = positionsService.getPositionBySymbol(accountId, symbol);
            if (position.isPresent()) {
                return ResponseEntity.ok(position.get());
            } else {
                return ResponseEntity.noContent().build();
            }
        } catch (AccountNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Account " + accountId + " not found");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error retrieving position: " + e.getMessage());
        }
    }
    
    @PutMapping("/{accountId}/positions/close/{positionId}")
    public ResponseEntity<?> closePosition(
            @PathVariable Long accountId,
            @PathVariable Long positionId,
            @RequestBody Map<String, Object> request) {
        try {
            PositionResponse closedPosition = positionsService.closePosition(
                    accountId, 
                    positionId, 
                    request
            );
            return ResponseEntity.ok(closedPosition);
        } catch (AccountNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Account " + accountId + " not found");
        } catch (PositionNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        } catch (InsufficientHoldingsException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Cannot close position: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error closing position: " + e.getMessage());
        }
    }
}