package com.ticketflow.bookingservice.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketflow.bookingservice.exceptions.BookingRuleException;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BookingTest {

    private static Booking booking(int quantity) {
        return new Booking(1L, "Jazz night", 42L, quantity, new BigDecimal("150.00"), Instant.now());
    }

    @Test
    void startsPendingAndComputesTheTotal() {
        Booking b = booking(3);

        assertThat(b.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(b.getTotalPrice()).isEqualByComparingTo("450.00");
    }

    @Test
    void cancelledOnlyOnce() {
        Booking b = booking(1);
        b.cancel();

        assertThat(b.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThatThrownBy(b::cancel).isInstanceOf(BookingRuleException.class);
    }

    @Test
    void refusesAnEmptyBooking() {
        assertThatThrownBy(() -> booking(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onlyACancelledBookingReleasesItsSeats() {
        assertThat(BookingStatus.PENDING.holdsSeats()).isTrue();
        assertThat(BookingStatus.CONFIRMED.holdsSeats()).isTrue();
        assertThat(BookingStatus.CANCELLED.holdsSeats()).isFalse();
    }
}
