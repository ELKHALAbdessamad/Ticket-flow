package com.ticketflow.bookingservice.repos;

import com.ticketflow.bookingservice.entities.Booking;
import com.ticketflow.bookingservice.entities.BookingStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepo extends JpaRepository<Booking, Long> {

    @EntityGraph(attributePaths = "lines")
    Optional<Booking> findWithLinesById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "lines")
    @Query("select booking from Booking booking where booking.id = :id")
    Optional<Booking> findLockedWithLinesById(@Param("id") Long id);

    @EntityGraph(attributePaths = "lines")
    Optional<Booking> findByBookingReference(String bookingReference);

    @Query("select booking.id from Booking booking where booking.customerId = :customerId "
            + "order by booking.createdAt desc")
    Page<Long> findIdsForCustomer(@Param("customerId") Long customerId, Pageable pageable);

    @EntityGraph(attributePaths = "lines")
    List<Booking> findWithLinesByIdIn(Collection<Long> ids);

    @Query("select booking.id from Booking booking where booking.status = :status "
            + "and booking.expiresAt <= :now order by booking.expiresAt")
    List<Long> findExpiredIds(@Param("status") BookingStatus status, @Param("now") Instant now,
                              Pageable pageable);
}
