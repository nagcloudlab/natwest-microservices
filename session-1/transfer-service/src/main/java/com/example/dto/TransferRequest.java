package com.example.dto;

public class TransferRequest {

    private String fromAccountId;
    private String toAccountId;
    private double amount;

    public String getFromAccountId() {
        return fromAccountId;
    }

    public String getToAccountId() {
        return toAccountId;
    }

    public double getAmount() {
        return amount;
    }

    public void setFromAccountId(String fromAccountId) {
        this.fromAccountId = fromAccountId;
    }

    public void setToAccountId(String toAccountId) {
        this.toAccountId = toAccountId;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

}
