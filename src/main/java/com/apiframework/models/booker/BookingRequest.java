package com.apiframework.models.booker;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Dual-purpose POJO for the Restful-Booker booking body.
 *
 * WHY dual-purpose?
 *   The POST /booking request body and the GET /booking/{id} response body share
 *   the same JSON structure, so one class handles both serialization (test → API)
 *   and deserialization (API → test assertions) to avoid maintaining two classes.
 *
 * HOW Jackson uses this:
 *   Serialization (POST/PUT):   .body(booking) — Jackson converts POJO → JSON.
 *   Deserialization (GET):      response.as(BookingRequest.class) — JSON → POJO.
 *   The "bookingdates" key maps to the nested BookingDates object automatically.
 *   @JsonIgnoreProperties(ignoreUnknown = true) ensures any extra response fields
 *   (e.g. "bookingid" on the wrapper level) do not cause UnrecognizedPropertyException.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BookingRequest {

    private String firstname;
    private String lastname;
    private Integer totalprice;
    private Boolean depositpaid;
    private BookingDates bookingdates;
    private String additionalneeds;
}
