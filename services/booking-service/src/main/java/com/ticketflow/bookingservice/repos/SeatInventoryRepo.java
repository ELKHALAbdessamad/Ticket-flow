package com.ticketflow.bookingservice.repos;

import com.ticketflow.bookingservice.entities.SeatInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Check-then-act ("count the seats, then insert") is a race: two requests can both see one free seat.
 * Here the check and the change are the SAME statement, which the database runs atomically on the row.
 */
public interface SeatInventoryRepo extends JpaRepository<SeatInventory, Long> {

    /**
     * Creates the counter of an event if it does not exist yet, and NEVER touches an existing one.
     * Returns 1 if created, 0 if it already existed. (Not {@code save()}: for an entity whose id is
     * already set, save() is a merge, which would overwrite the live counter with {@code held = 0}.)
     */
    @Modifying
    @Query(value = "insert into seat_inventory (event_id, capacity, held) "
            + "select cast(:eventId as bigint), cast(:capacity as integer), 0 "
            + "where not exists (select 1 from seat_inventory where event_id = :eventId)", nativeQuery = true)
    int createIfAbsent(@Param("eventId") Long eventId, @Param("capacity") int capacity);

    /** Takes {@code quantity} seats if enough are left. Returns 1 if taken, 0 if sold out. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update SeatInventory s set s.held = s.held + :quantity, s.capacity = :capacity "
            + "where s.eventId = :eventId and s.held + :quantity <= :capacity")
    int reserve(@Param("eventId") Long eventId, @Param("quantity") int quantity, @Param("capacity") int capacity);

    /** Gives seats back. Returns 0 (and changes nothing) if that would make the count negative. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update SeatInventory s set s.held = s.held - :quantity "
            + "where s.eventId = :eventId and s.held >= :quantity")
    int release(@Param("eventId") Long eventId, @Param("quantity") int quantity);
}
