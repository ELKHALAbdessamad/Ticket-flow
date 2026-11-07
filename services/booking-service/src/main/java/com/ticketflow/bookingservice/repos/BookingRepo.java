package com.ticketflow.bookingservice.repos;

import com.ticketflow.bookingservice.entities.Booking;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepo extends JpaRepository<Booking, Long> {

    List<Booking> findByCustomerIdOrderByCreatedAtDescIdDesc(Long customerId);

    List<Booking> findAllByOrderByCreatedAtDescIdDesc();
}
