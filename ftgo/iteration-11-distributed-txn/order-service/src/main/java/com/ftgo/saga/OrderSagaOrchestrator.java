package com.ftgo.saga;

import com.ftgo.accounting.AccountingServiceClient;
import com.ftgo.kitchen.KitchenServiceClient;
import com.ftgo.delivery.DeliveryServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Saga Orchestrator for Order Creation.
 *
 * Defines the distributed transaction as a sequence of steps, each with a
 * forward action and a compensating action. If any step fails, all previously
 * completed steps are compensated in reverse order.
 *
 * Flow:
 *   Step 1: Authorize Payment    → compensate: Refund Payment
 *   Step 2: Create Kitchen Ticket → compensate: Cancel Kitchen Ticket
 *   Step 3: Create Delivery       → compensate: Cancel Delivery
 *
 * Every step is logged to the saga_log table for observability.
 */
@Slf4j
@Component
public class OrderSagaOrchestrator {

    private final AccountingServiceClient accountingService;
    private final KitchenServiceClient kitchenService;
    private final DeliveryServiceClient deliveryService;
    private final SagaLogRepository sagaLogRepository;
    private final SagaEventPublisher sagaEventPublisher;

    public OrderSagaOrchestrator(AccountingServiceClient accountingService,
                                  KitchenServiceClient kitchenService,
                                  DeliveryServiceClient deliveryService,
                                  SagaLogRepository sagaLogRepository,
                                  SagaEventPublisher sagaEventPublisher) {
        this.accountingService = accountingService;
        this.kitchenService = kitchenService;
        this.deliveryService = deliveryService;
        this.sagaLogRepository = sagaLogRepository;
        this.sagaEventPublisher = sagaEventPublisher;
    }

    public SagaState execute(Long orderId, BigDecimal totalAmount, String paymentMethod,
                              Long restaurantId, String itemsSummary,
                              String restaurantAddress, String deliveryAddress) {

        logStep(orderId, "SAGA", "START", "SUCCESS", "Starting order creation saga");

        // Define saga steps with forward + compensating actions
        List<SagaStep> steps = new ArrayList<>();

        // Step 1: Authorize Payment
        steps.add(new SagaStep(
                "Authorize Payment",
                () -> {
                    log.info("SAGA [Order {}] Step 1: Authorizing payment of Rs.{}", orderId, totalAmount);
                    accountingService.authorizePayment(orderId, totalAmount, paymentMethod);
                },
                () -> {
                    log.info("SAGA [Order {}] COMPENSATING: Refunding payment", orderId);
                    accountingService.refundPayment(orderId);
                }
        ));

        // Step 2: Create Kitchen Ticket
        steps.add(new SagaStep(
                "Create Kitchen Ticket",
                () -> {
                    log.info("SAGA [Order {}] Step 2: Creating kitchen ticket", orderId);
                    kitchenService.createTicket(orderId, restaurantId, itemsSummary);
                },
                () -> {
                    log.info("SAGA [Order {}] COMPENSATING: Cancelling kitchen ticket", orderId);
                    kitchenService.cancelTicket(orderId);
                }
        ));

        // Step 3: Create Delivery
        steps.add(new SagaStep(
                "Create Delivery",
                () -> {
                    log.info("SAGA [Order {}] Step 3: Creating delivery", orderId);
                    deliveryService.createDelivery(orderId, restaurantAddress, deliveryAddress);
                },
                () -> {
                    log.info("SAGA [Order {}] COMPENSATING: Cancelling delivery", orderId);
                    deliveryService.cancelDelivery(orderId);
                }
        ));

        // Execute saga steps
        List<SagaStep> completedSteps = new ArrayList<>();

        for (int i = 0; i < steps.size(); i++) {
            SagaStep step = steps.get(i);
            try {
                step.getForwardAction().run();
                completedSteps.add(step);
                logStep(orderId, step.getName(), "FORWARD", "SUCCESS",
                        "Step " + (i + 1) + " completed successfully");
            } catch (Exception e) {
                logStep(orderId, step.getName(), "FORWARD", "FAILED",
                        "Step " + (i + 1) + " failed: " + e.getMessage());
                log.error("SAGA [Order {}] Step '{}' FAILED: {}", orderId, step.getName(), e.getMessage());

                // Compensate completed steps in reverse order
                logStep(orderId, "SAGA", "COMPENSATING", "IN_PROGRESS",
                        "Compensating " + completedSteps.size() + " completed step(s)");

                for (int j = completedSteps.size() - 1; j >= 0; j--) {
                    SagaStep completedStep = completedSteps.get(j);
                    try {
                        completedStep.getCompensatingAction().run();
                        logStep(orderId, completedStep.getName(), "COMPENSATE", "SUCCESS",
                                "Compensation completed");
                    } catch (Exception compEx) {
                        logStep(orderId, completedStep.getName(), "COMPENSATE", "FAILED",
                                "Compensation failed: " + compEx.getMessage());
                        log.error("SAGA [Order {}] Compensation for '{}' FAILED: {}",
                                orderId, completedStep.getName(), compEx.getMessage());
                    }
                }

                logStep(orderId, "SAGA", "END", "FAILED",
                        "Saga failed — all completed steps compensated");
                sagaEventPublisher.publishSagaCompleted(orderId, "FAILED");
                return SagaState.FAILED;
            }
        }

        logStep(orderId, "SAGA", "END", "COMPLETED", "All steps completed successfully");
        sagaEventPublisher.publishSagaCompleted(orderId, "COMPLETED");
        return SagaState.COMPLETED;
    }

    private void logStep(Long orderId, String stepName, String action, String status, String details) {
        sagaLogRepository.save(new SagaLog(orderId, stepName, action, status, details));
    }
}
