package com.apiframework.models.wiremock;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight booking POJO used exclusively by the WireMock test suite.
 *
 * WHY a separate model?
 *   The real BookingRequest (src/main/java/.../booker/BookingRequest.java) contains
 *   nested BookingDates and additionalneeds fields that match the Restful-Booker API
 *   contract. The WireMock mock server intentionally uses a simpler, flat response shape
 *   (id + 4 scalar fields) to keep stub bodies concise and to show that the framework
 *   can deserialise any JSON shape — not just the real Booker API format.
 *
 * HOW Jackson uses this:
 *   @Data        → generates getters/setters so Jackson can read/write fields.
 *   @Builder     → fluent construction in test assertions.
 *   @NoArgsConstructor → Jackson requires a no-arg constructor for deserialization.
 *   @AllArgsConstructor→ required by Lombok @Builder.
 *   @JsonIgnoreProperties(ignoreUnknown = true) → any extra fields in the mock JSON
 *                  body are silently ignored instead of throwing UnrecognizedPropertyException.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MockBooking {

    /** Server-assigned unique identifier — present in responses, absent in create requests. */
    private Integer id;

    private String firstname;
    private String lastname;

    /** Room rate in whole currency units. */
    private Integer totalprice;

    /** Whether a deposit has already been collected from the guest. */
    private Boolean depositpaid;
}
