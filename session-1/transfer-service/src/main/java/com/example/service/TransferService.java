package com.example.service;

public interface TransferService {
    void transfer(String fromAccountId, String toAccountId, double amount);
}
