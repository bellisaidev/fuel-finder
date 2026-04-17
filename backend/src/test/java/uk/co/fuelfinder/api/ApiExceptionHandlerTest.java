package uk.co.fuelfinder.api;

import org.junit.jupiter.api.Test;
import uk.co.fuelfinder.api.dto.ApiErrorResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler apiExceptionHandler = new ApiExceptionHandler();

    @Test
    void returnsBadRequestPayloadForIllegalArgumentException() {
        ApiErrorResponse response = apiExceptionHandler.handleIllegalArgument(
                new IllegalArgumentException("fuelType must not be blank")
        );

        assertEquals(400, response.status());
        assertEquals("Bad Request", response.error());
        assertEquals("fuelType must not be blank", response.message());
    }
}
