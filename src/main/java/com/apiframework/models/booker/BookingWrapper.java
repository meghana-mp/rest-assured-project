package com.apiframework.models.booker;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Deserializes the POST /booking response envelope.
 *
 * WHY a wrapper?
 *   The Restful-Booker API returns the creation response as a two-level object:
 *   { "bookingid": 1, "booking": { ...all booking fields... } }
 *   The top-level "bookingid" is the server-assigned ID. Jackson needs a wrapper
 *   class to read both the id and the nested booking object in one shot.
 *
 * HOW it is used:
 *   BookingWrapper w = response.as(BookingWrapper.class);
 *   int id = w.getBookingid();            // server-assigned ID for use in later tests
 *   String name = w.getBooking().getFirstname();  // access nested fields
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BookingWrapper {

    private Integer bookingid;
    private BookingRequest booking;
}
