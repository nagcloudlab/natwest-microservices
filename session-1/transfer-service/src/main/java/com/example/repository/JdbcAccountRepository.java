package com.example.repository;

import java.sql.Connection;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.batch.BatchProperties.Jdbc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.example.entity.Account;

/**
 * author: Team-1
 */

@Component("jdbcAccountRepository")
public class JdbcAccountRepository implements AccountRepository {

    private static Logger logger = LoggerFactory.getLogger(JdbcAccountRepository.class);

    private JdbcTemplate jdbcTemplate;

    public JdbcAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Account findById(String id) {
        logger.info("Fetching account with ID: {}", id);
        return jdbcTemplate.queryForObject("SELECT * FROM accounts WHERE id = ?", new Object[] { id }, (rs, rowNum) -> {
            Account account = new Account();
            account.setId(rs.getString("id"));
            account.setBalance(rs.getDouble("balance"));
            return account;
        });
    }

    public void update(Account account) {
        logger.info("Updating account: {}", account);
        jdbcTemplate.update("UPDATE accounts SET balance = ? WHERE id = ?", account.getBalance(),
                account.getAccountId());
    }

}
