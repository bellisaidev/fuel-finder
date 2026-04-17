package uk.co.fuelfinder.api;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
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
        ApiErrorResponse response = apiExceptionHandler.handleIllegalArgument(
                new IllegalArgumentException("fuelType must not be blank")
        );

        assertEquals(400, response.status());
        assertEquals("Bad Request", response.error());
        assertEquals("fuelType must not be blank", response.message());
    }

    @Test
    void returnsBadRequestPayloadForMissingServletRequestParameterException() {
        ApiErrorResponse response = apiExceptionHandler.handleMissingServletRequestParameter(
                new MissingServletRequestParameterException("fuelType", "String")
        );

        assertEquals(400, response.status());
        assertEquals("Bad Request", response.error());
        assertEquals("fuelType is required", response.message());
    }

    @Test
    void returnsBadRequestPayloadForMethodArgumentTypeMismatchException() throws NoSuchMethodException {
        Method method = SampleController.class.getDeclaredMethod("sample", double.class);
        MethodParameter parameter = new MethodParameter(method, 0);

        ApiErrorResponse response = apiExceptionHandler.handleMethodArgumentTypeMismatch(
                new MethodArgumentTypeMismatchException("north", Double.class, "lat", parameter, null)
        );

        assertEquals(400, response.status());
        assertEquals("Bad Request", response.error());
        assertEquals("lat has an invalid value", response.message());
    }

    @Test
    void returnsBadRequestPayloadForConstraintViolationException() {
        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        when(violation.getMessage()).thenReturn("radiusMeters must be greater than 0");

        ApiErrorResponse response = apiExceptionHandler.handleConstraintViolation(
                new ConstraintViolationException(Set.of(violation))
        );

        assertEquals(400, response.status());
        assertEquals("Bad Request", response.error());
        assertEquals("radiusMeters must be greater than 0", response.message());
    }

    private static final class SampleController {

        @SuppressWarnings("unused")
        private void sample(double lat) {
        }
    }
}
