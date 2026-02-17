package com.ftgo.kitchen;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KitchenTicketRepository extends JpaRepository<KitchenTicket, Long> {
    List<KitchenTicket> findByStatus(TicketStatus status);
}
