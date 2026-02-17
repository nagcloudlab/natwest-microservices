package com.example.repository;

import com.example.entity.Account;

public interface AccountRepository {
    Account findById(String id);

    void update(Account account);
}
