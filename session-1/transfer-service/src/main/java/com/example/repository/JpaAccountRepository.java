package com.example.repository;

import java.sql.Connection;

import javax.sql.DataSource;
import javax.swing.text.html.parser.Entity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.batch.BatchProperties.Jdbc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.example.entity.Account;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * author: Team-1
 */

@Component("jpaAccountRepository")
public class JpaAccountRepository implements AccountRepository {

    private static Logger logger = LoggerFactory.getLogger(JpaAccountRepository.class);

    @PersistenceContext
    private EntityManager entityManager;

    public Account findById(String id) {
        logger.info("Fetching account with ID: {}", id);
        return entityManager.find(Account.class, id);
    }

    public void update(Account account) {
        logger.info("Updating account: {}", account);
        entityManager.merge(account);
    }

}
