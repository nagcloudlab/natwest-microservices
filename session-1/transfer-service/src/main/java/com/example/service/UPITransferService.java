package com.example.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.example.exception.BalanceException;
import com.example.repository.AccountRepository;

/*

    design issues
    ---------------
    - tight coupling: UPITransferService is directly creating an instance of JdbcAccountRepository, 
      which makes it difficult to replace or mock the repository for testing or future changes.

        -> cant't extend with new features without modifying the existing code, which violates the Open/Closed Principle of software design.
        -> unit-testing not possible, as we cannot mock the JdbcAccountRepository, which can lead to tests that are slow and unreliable due to dependencies on external systems like databases.

    performance issues
    ------------------
    - resource management: creating a new instance of JdbcAccountRepository for each transfer can lead
      to resource leaks if the repository holds database connections or other resources that are not properly closed.  

      -> resource use high, response time high, and potential for memory leaks if the repository is not properly managed.


    why these issues arise?
    --------------------------------  
    -> dependency managing it's own dependency's lifecycle, which can lead to tight coupling and resource management issues.

    solution:
    -----------------
    -> Don't create, Use a factory to create instances of JdbcAccountRepository, which can manage the lifecycle and dependencies more effectively. ( Factory Pattern )
       
    -> but still we have performance issues, 

    best solution: Don't create / lookup, use third-party ( like Spring Framework ) to manage the lifecycle 
     aka Inverssion of Control ( IOC )

     how to implement IOC ?
     -> dependency injection ( DI )
     -> aspect-oriented programming ( AOP )


     simple-java-object with DI + AOP -> it beome powerful object


     ---------------------------------------------------------------
     SOLID Principles
     ---------------
     - Single Responsibility Principle (SRP): A class should have only one reason to change.
     - Open/Closed Principle (OCP): Software entities should be open for extension but closed for modification.
     - Liskov Substitution Principle (LSP): Objects of a superclass should be replaceable with objects of a subclass without affecting the correctness of the program.
     - Interface Segregation Principle (ISP): Clients should not be forced to depend on interfaces they do not use.
     - Dependency Inversion Principle (DIP): High-level modules should not depend on low-level modules. Both should depend on abstractions. Abstractions should not depend on details. Details should depend on abstractions.


    ---------------------------------------------------------------
*/

@Component("upiTransferService")
public class UPITransferService implements TransferService {

    private static Logger logger = LoggerFactory.getLogger(UPITransferService.class);

    private AccountRepository accountRepository;

    public UPITransferService(@Qualifier("jpaAccountRepository") AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
        logger.info("UPITransferService component initialized.");
    }

    // A -> Atomicity
    // C -> Consistency
    // I -> Isolation
    // D -> Durability
    @Transactional(transactionManager = "transactionManager", rollbackFor = RuntimeException.class, isolation = Isolation.READ_COMMITTED)
    public void transfer(String fromAccountId, String toAccountId, double amount) {
        logger.info("Initiating transfer from {} to {} of amount {}", fromAccountId, toAccountId, amount);
        // JdbcAccountRepository accountRepository = new JdbcAccountRepository();
        // var accountRepository =AccountRepositoryFactory.getAccountRepository("jdbc");
        try {
            // Fetch accounts
            var fromAccount = accountRepository.findById(fromAccountId);
            var toAccount = accountRepository.findById(toAccountId);

            // Check for sufficient balance
            if (fromAccount.getBalance() < amount) {
                throw new BalanceException("Insufficient balance in the source account.");
            }

            // Perform transfer
            fromAccount.setBalance(fromAccount.getBalance() - amount); // debit from source account
            toAccount.setBalance(toAccount.getBalance() + amount); // credit to destination account

            // Update accounts in the database
            accountRepository.update(fromAccount);

            boolean simulateError = false; // Set to true to simulate an error during transfer
            if (simulateError) {
                throw new RuntimeException("Simulated error during transfer.");
            }

            accountRepository.update(toAccount);

        } catch (Exception e) {
            logger.error("Transfer failed: {}", e.getMessage(), e);
            throw new RuntimeException("Transfer failed: " + e.getMessage(), e);
        }
        logger.info("Transfer completed successfully.");
    }

}
