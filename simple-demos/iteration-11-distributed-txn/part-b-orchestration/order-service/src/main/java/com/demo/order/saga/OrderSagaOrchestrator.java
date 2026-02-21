package com.demo.order.saga;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class OrderSagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaOrchestrator.class);

    private final RestTemplate restTemplate;
    private final String inventoryServiceUrl;
    private final String paymentServiceUrl;

    public OrderSagaOrchestrator(RestTemplate restTemplate,
                                  @Value("${inventory.service.url}") String inventoryServiceUrl,
                                  @Value("${payment.service.url}") String paymentServiceUrl) {
        this.restTemplate = restTemplate;
        this.inventoryServiceUrl = inventoryServiceUrl;
        this.paymentServiceUrl = paymentServiceUrl;
    }

    public SagaState execute(Long orderId, String productId, int quantity, BigDecimal amount) {
        log.info("SAGA [Order {}] Starting saga execution", orderId);

        List<SagaStep> steps = new ArrayList<>();

        // Step 1: Reserve Inventory
        steps.add(new SagaStep(
                "Reserve Inventory",
                () -> {
                    log.info("SAGA [Order {}] Step 1: Reserving inventory for {} x{}", orderId, productId, quantity);
                    restTemplate.postForObject(
                            inventoryServiceUrl + "/api/inventory/reserve",
                            Map.of("orderId", orderId, "productId", productId, "quantity", quantity),
                            Map.class
                    );
                    log.info("SAGA [Order {}] Step 1: Inventory reserved successfully", orderId);
                },
                () -> {
                    log.info("SAGA [Order {}] COMPENSATING: Releasing inventory", orderId);
                    restTemplate.postForObject(
                            inventoryServiceUrl + "/api/inventory/release/" + orderId,
                            null,
                            Map.class
                    );
                    log.info("SAGA [Order {}] COMPENSATED: Inventory released", orderId);
                }
        ));

        // Step 2: Process Payment
        steps.add(new SagaStep(
                "Process Payment",
                () -> {
                    log.info("SAGA [Order {}] Step 2: Processing payment of ${}", orderId, amount);
                    restTemplate.postForObject(
                            paymentServiceUrl + "/api/payments/process",
                            Map.of("orderId", orderId, "amount", amount),
                            Map.class
                    );
                    log.info("SAGA [Order {}] Step 2: Payment processed successfully", orderId);
                },
                () -> {
                    log.info("SAGA [Order {}] COMPENSATING: Refunding payment", orderId);
                    restTemplate.postForObject(
                            paymentServiceUrl + "/api/payments/refund/" + orderId,
                            null,
                            Map.class
                    );
                    log.info("SAGA [Order {}] COMPENSATED: Payment refunded", orderId);
                }
        ));

        // Execute saga steps
        List<SagaStep> completedSteps = new ArrayList<>();
        SagaState state = SagaState.STARTED;

        for (SagaStep step : steps) {
            try {
                step.getForwardAction().run();
                completedSteps.add(step);
            } catch (Exception e) {
                log.error("SAGA [Order {}] Step '{}' FAILED: {}", orderId, step.getName(), e.getMessage());
                state = SagaState.COMPENSATING;

                // Compensate completed steps in reverse order
                for (int i = completedSteps.size() - 1; i >= 0; i--) {
                    try {
                        completedSteps.get(i).getCompensatingAction().run();
                    } catch (Exception compEx) {
                        log.error("SAGA [Order {}] Compensation for '{}' FAILED: {}",
                                orderId, completedSteps.get(i).getName(), compEx.getMessage());
                    }
                }

                log.info("SAGA [Order {}] Saga FAILED — all completed steps compensated", orderId);
                return SagaState.FAILED;
            }
        }

        log.info("SAGA [Order {}] Saga COMPLETED successfully", orderId);
        return SagaState.COMPLETED;
    }
}
