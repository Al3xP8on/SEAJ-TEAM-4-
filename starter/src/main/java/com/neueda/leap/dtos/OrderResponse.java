package com.neueda.leap.dtos;

public class OrderResponse {
    private String orderId;
    private OrderStatus status;

    public OrderResponse(String orderId, OrderStatus status){ // 
        this.orderId = orderId;
        this.status = status;
    }

    public String getOrderId(){
        return this.orderId;
    }

    public OrderStatus getStatus(){
        return this.status;
    }
}
