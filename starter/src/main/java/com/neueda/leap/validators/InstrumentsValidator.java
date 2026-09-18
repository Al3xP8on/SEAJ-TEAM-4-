package com.neueda.leap.validators;

import java.util.Objects;

public class InstrumentsValidator {

    public String validateSymbol(String symbol) {
        Objects.requireNonNull(symbol, "Symbol can't be null");

        String trimmedSymbol = symbol.trim();
        if (trimmedSymbol.isEmpty()) {
            throw new IllegalArgumentException("Symbol can't be empty");
        }

        if (trimmedSymbol.length() > 20) {
            throw new IllegalArgumentException("Symbol can't be longer than 20 characters");
        }

        return trimmedSymbol;
    }

    public String validateName(String name) {
        Objects.requireNonNull(name, "Name can't be null");
        String trimmedName = name.trim();

        if (trimmedName.isEmpty()) {
            throw new IllegalArgumentException("Name can't be empty");
        }
        if (trimmedName.length() > 255) {
            throw new IllegalArgumentException("Name can't be longer than 255 characters");
        }
        return trimmedName;
    }

    public String validateAssetClass(String assetClass) {
        Objects.requireNonNull(assetClass, "Asset class can't be null");

        String trimmedAssetClass = assetClass.trim();
        if (trimmedAssetClass.isEmpty()) {
            throw new IllegalArgumentException("Asset class can't be empty");
        }

        if (trimmedAssetClass.length() > 50) {
            throw new IllegalArgumentException("Asset class can't be longer than 50 characters");
        }

        return trimmedAssetClass;
    }

    public String validateCurrency(String currency) {
        Objects.requireNonNull(currency, "Currency can't be null");

        String trimmedCurrency = currency.trim();
        if (trimmedCurrency.length() != 3) {
            throw new IllegalArgumentException("Currency must be exactly 3 characters");
        }
        return trimmedCurrency.toUpperCase();
    }
}
