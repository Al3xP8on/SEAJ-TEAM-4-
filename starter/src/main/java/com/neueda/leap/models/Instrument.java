
package com.neueda.leap;
import java.util.Objects;

import java.util.Objects;
import java.util.Optional;
public class Instrument {

    private final String symbol;
    private final String name;
    private final String assetClass;
    private final String currency;
    private final boolean tradable;

    public Instrument(String symbol, String name, String assetClass, String currency, boolean tradable) {

        this.symbol = validateSymbol(symbol);
        this.name = validateName(name);
        this.assetClass = validateAssetClass(assetClass);
        this.currency = validateCurrency(currency);
        this.tradable = tradable;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getName(){
        return name;
    }

    public String getAssetClass(){
        return assetClass;
    }

    public String getCurrency(){
        return currency;
    }

    public boolean isTradable(){
        return tradable;
    }

    private static String validateSymbol(String symbol){
        Objects.requireNonNull(symbol, "Symbol cannot be null");

        String trimmedSymbol = symbol.trim();
        if(trimmedSymbol.isEmpty()){
            throw new IllegalArgumentException("Symbol cannot be empty");
        }

        if(trimmedSymbol.length() > 20){
            throw new IllegalArgumentException("Symbol cannot be longer than 20 characters");
        }

        return trimmedSymbol;
    }

    private static String validateAssetClass(String assetClass){
        Objects.requireNonNull(assetClass, "Asset class cannot be null");

        String trimmedAssetClass = assetClass.trim();
        if(trimmedAssetClass.isEmpty()){
            throw new IllegalArgumentException("Asset class cannot be empty");
        }

        if(trimmedAssetClass.length() > 20){
            throw new IllegalArgumentException("Asset class cannot be longer than 50 characters");
        }

        return trimmedAssetClass;
    }

    private static String validateCurrency(String currency){
        Objects.requireNonNull(currency, "Currency cannot be null");

        String trimmedCurrency = currency.trim();
        if(trimmedCurrency.length() != 3){
            throw new IllegalArgumentException("Currency must be exactly 3 characters");
        }
        return trimmedCurrency.toUpperCase();
    }

    private static String validateName(String name){
        Objects.requireNonNull(name, "Name cannot be null");
        String trimmedName = name.trim();

        if(trimmedName.isEmpty()){
            throw new IllegalArgumentException("Name cannot be empty");
        }
        if(trimmedName.length() > 1000){
            throw new IllegalArgumentException("Name cannot be longer than 1000 characters");
        }
        return trimmedName;

    }

    @Override
    public boolean equals(Object object){
        if (this == object) {
            return true;
        }

        if(!(object instanceof Instrument instrument)){
            return false;
        }

        return symbol.equals(instrument.symbol);
    }

    @Override
    public int hashCode(){
        return symbol.hashCode();
    }

    @Override
    public String toString(){
        return "Instrument{" +
                "symbol='" + symbol + '\'' +
                ", name='" + name + '\'' +
                ", assetClass='" + assetClass + '\'' +
                ", currency='" + currency + '\'' +
                ", tradable=" + tradable +
                '}';
    }

}