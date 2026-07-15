package com.reydark.reycom.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class ReyComMetricsService {

    private final Counter ordersCreated;
    private final Counter ordersCancelled;
    private final Counter paymentsInitiated;
    private final Counter paymentsSuccess;
    private final Counter paymentsFailed;
    private final Counter notificationsCreated;

    public ReyComMetricsService(MeterRegistry meterRegistry) {
        ordersCreated = counter(meterRegistry, "reycom.orders.created", "Orders created successfully");
        ordersCancelled = counter(meterRegistry, "reycom.orders.cancelled", "Orders cancelled successfully");
        paymentsInitiated = counter(meterRegistry, "reycom.payments.initiated", "Payments initiated successfully");
        paymentsSuccess = counter(meterRegistry, "reycom.payments.success", "Payments completed successfully");
        paymentsFailed = counter(meterRegistry, "reycom.payments.failed", "Payments marked as failed");
        notificationsCreated = counter(meterRegistry, "reycom.notifications.created", "Notifications created successfully");
    }

    public void recordOrderCreated() {
        incrementAfterCommit(ordersCreated);
    }

    public void recordOrderCancelled() {
        incrementAfterCommit(ordersCancelled);
    }

    public void recordPaymentInitiated() {
        incrementAfterCommit(paymentsInitiated);
    }

    public void recordPaymentSuccess() {
        incrementAfterCommit(paymentsSuccess);
    }

    public void recordPaymentFailed() {
        incrementAfterCommit(paymentsFailed);
    }

    public void recordNotificationCreated() {
        incrementAfterCommit(notificationsCreated);
    }

    private Counter counter(MeterRegistry meterRegistry, String name, String description) {
        return Counter.builder(name)
                .description(description)
                .register(meterRegistry);
    }

    private void incrementAfterCommit(Counter counter) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            counter.increment();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                counter.increment();
            }
        });
    }
}
