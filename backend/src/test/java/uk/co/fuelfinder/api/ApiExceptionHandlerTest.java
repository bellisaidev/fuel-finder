package uk.co.fuelfinder.api;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler apiExceptionHandler = new ApiExceptionHandler();

    @Test
    void returnsBadRequestPayloadForIllegalArgumentException() {
        Map<String, Object> response = apiExceptionHandler.handleIllegalArgument(
                new IllegalArgumentException("fuelType must not be blank")
        );

        assertEquals(400, response.get("status"));
        assertEquals("Bad Request", response.get("error"));
        assertEquals("fuelType must not be blank", response.get("message"));
    }
}
