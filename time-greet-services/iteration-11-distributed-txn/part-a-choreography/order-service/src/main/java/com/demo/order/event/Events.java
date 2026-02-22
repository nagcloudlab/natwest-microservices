package com.demo.order.event;

public class Events {

    // Order events
    public static final String ORDER_CREATED = "ORDER_CREATED";

    // Inventory events
    public static final String INVENTORY_RESERVED = "INVENTORY_RESERVED";
    public static final String INVENTORY_RELEASED = "INVENTORY_RELEASED";

    // Payment events
    public static final String PAYMENT_PROCESSED = "PAYMENT_PROCESSED";
    public static final String PAYMENT_FAILED = "PAYMENT_FAILED";
}
