package com.example.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Component
@Aspect
public class TransferCountMetricAspect {

    int transferRequestCount = 0;
    int transferSuccessCount = 0;
    int transferFailureCount = 0;

    // @Before("execution(* com.example.service.TransferService.transfer(..))")
    // public void countTransfer() {
    // // Logic to count the number of transfers
    // transferCount++;
    // System.out.println("Transfer count: " + transferCount);
    // }

    // @AfterReturning("execution(*
    // com.example.service.TransferService.transfer(..))")
    // public void logTransferSuccess() {
    // System.out.println("Transfer successful. Total transfers so far: " +
    // transferCount);
    // }

    // @AfterThrowing(pointcut = "execution(*
    // com.example.service.TransferService.transfer(..))", throwing = "ex")
    // public void logTransferFailure(Exception ex) {
    // System.out.println("Transfer failed with exception: " + ex.getMessage());
    // }

    // @After("execution(* com.example.service.TransferService.transfer(..))")
    // public void logTransferCompletion() {
    // System.out.println("Transfer method execution completed.");
    // }

    @Around("execution(* com.example.service.TransferService.transfer(..))")
    public Object countAndLogTransfer(ProceedingJoinPoint joinPoint) throws Throwable {
        transferRequestCount++;
        try {
            Object result = joinPoint.proceed();
            transferSuccessCount++;
            System.out.println("Transfer successful. Total requests: " + transferRequestCount + ", Successes: "
                    + transferSuccessCount);
            return result;
        } catch (Exception ex) {
            System.out.println("Transfer failed with exception: " + ex.getMessage());
            transferFailureCount++;
            throw ex; // rethrow the exception to maintain the original behavior
        } finally {
            System.out.println("Transfer method execution completed.");
        }
    }

}
