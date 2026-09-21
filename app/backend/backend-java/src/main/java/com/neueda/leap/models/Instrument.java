
package com.neueda.leap.models;
import com.neueda.leap.validators.InstrumentsValidator;

public class Instrument {

    private final String symbol;
    private final String name;
    private final String assetClass;
    private final String currency;
    private final boolean tradable;

    public Instrument(String symbol, String name, String assetClass, String currency, boolean tradable) {
        this(symbol, name, assetClass, currency, tradable, new InstrumentsValidator());
    }

    public Instrument(String symbol, String name, String assetClass, String currency, boolean tradable, InstrumentsValidator validator) {
        this.symbol = validator.validateSymbol(symbol);
        this.name = validator.validateName(name);
        this.assetClass = validator.validateAssetClass(assetClass);
        this.currency = validator.validateCurrency(currency);
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