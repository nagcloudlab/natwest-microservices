package com.demo.inventory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final ReservationRepository reservationRepository;

    public InventoryService(ReservationRepository reservationRepository) {
        this.reservationRepository = reservationRepository;
    }

    public Reservation reserve(Long orderId, String productId, int quantity) {
        log.info("Reserving {} units of {} for order {}", quantity, productId, orderId);
        Reservation reservation = new Reservation(orderId, productId, quantity);
        return reservationRepository.save(reservation);
    }

    public Reservation release(Long orderId) {
        log.info("Releasing inventory for order {}", orderId);
        Reservation reservation = reservationRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("No reservation found for order: " + orderId));
        reservation.setStatus(ReservationStatus.RELEASED);
        return reservationRepository.save(reservation);
    }
}
