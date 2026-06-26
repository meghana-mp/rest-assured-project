package com.apiframework.models.booker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Nested value object representing a booking's check-in and check-out dates.
 *
 * WHY a separate class?
 *   The Restful-Booker API nests booking dates under a "bookingdates" key in the JSON
 *   body, so Jackson needs a dedicated POJO to map the nested object correctly.
 *   Embedding two String fields directly in BookingRequest would prevent Jackson from
 *   reading/writing the nested structure.
 *
 * HOW it is used:
 *   Set via BookingRequest.builder().bookingdates(BookingDates.builder()...build()).build()
 *   Jackson serializes it as: "bookingdates": { "checkin": "2025-01-01", "checkout": "2025-01-07" }
 *   Use TestUtils.dateOffset(n) to generate relative dates dynamically in tests.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingDates {

    /** Expected format: yyyy-MM-dd */
    private String checkin;

    /** Expected format: yyyy-MM-dd */
    private String checkout;
}
