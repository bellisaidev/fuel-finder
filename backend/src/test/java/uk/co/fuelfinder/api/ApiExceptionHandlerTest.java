package uk.co.fuelfinder.api;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import uk.co.fuelfinder.api.dto.ApiErrorResponse;

import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler apiExceptionHandler = new ApiExceptionHandler();

    @Test
    void returnsBadRequestPayloadForIllegalArgumentException() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        ApiErrorResponse response = apiExceptionHandler.handleIllegalArgument(
                new IllegalArgumentException("fuelType must not be blank"),
                request
        );

        assertEquals(400, response.status());
        assertEquals("Bad Request", response.error());
        assertEquals("fuelType must not be blank", response.message());
        assertEquals("fuelType must not be blank", request.getAttribute(ApiRequestLogAttributes.ERROR_MESSAGE));
    }

    @Test
    void returnsBadRequestPayloadForMissingServletRequestParameterException() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        ApiErrorResponse response = apiExceptionHandler.handleMissingServletRequestParameter(
                new MissingServletRequestParameterException("fuelType", "String"),
                request
        );

        assertEquals(400, response.status());
        assertEquals("Bad Request", response.error());
        assertEquals("fuelType is required", response.message());
        assertEquals("fuelType is required", request.getAttribute(ApiRequestLogAttributes.ERROR_MESSAGE));
    }

    @Test
    void returnsBadRequestPayloadForMethodArgumentTypeMismatchException() throws NoSuchMethodException {
        Method method = SampleController.class.getDeclaredMethod("sample", double.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        MockHttpServletRequest request = new MockHttpServletRequest();

        ApiErrorResponse response = apiExceptionHandler.handleMethodArgumentTypeMismatch(
                new MethodArgumentTypeMismatchException("north", Double.class, "lat", parameter, null),
                request
        );

        assertEquals(400, response.status());
        assertEquals("Bad Request", response.error());
        assertEquals("lat has an invalid value", response.message());
        assertEquals("lat has an invalid value", request.getAttribute(ApiRequestLogAttributes.ERROR_MESSAGE));
    }

    @Test
    void returnsBadRequestPayloadForConstraintViolationException() {
        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        when(violation.getMessage()).thenReturn("radiusMeters must be greater than 0");
        MockHttpServletRequest request = new MockHttpServletRequest();

        ApiErrorResponse response = apiExceptionHandler.handleConstraintViolation(
                new ConstraintViolationException(Set.of(violation)),
                request
        );

        assertEquals(400, response.status());
        assertEquals("Bad Request", response.error());
        assertEquals("radiusMeters must be greater than 0", response.message());
        assertEquals("radiusMeters must be greater than 0", request.getAttribute(ApiRequestLogAttributes.ERROR_MESSAGE));
    }

    @Test
    void returnsNotFoundPayloadForStationNotFoundException() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        ApiErrorResponse response = apiExceptionHandler.handleStationNotFound(
                new StationNotFoundException(java.util.UUID.fromString("123e4567-e89b-12d3-a456-426614174000")),
                request
        );

        assertEquals(404, response.status());
        assertEquals("Not Found", response.error());
        assertEquals("Station not found: 123e4567-e89b-12d3-a456-426614174000", response.message());
        assertEquals(
                "Station not found: 123e4567-e89b-12d3-a456-426614174000",
                request.getAttribute(ApiRequestLogAttributes.ERROR_MESSAGE)
        );
    }

    private static final class SampleController {

        @SuppressWarnings("unused")
        private void sample(double lat) {
        }
    }
}
