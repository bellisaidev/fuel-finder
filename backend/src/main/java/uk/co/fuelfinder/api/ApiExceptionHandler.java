package uk.co.fuelfinder.api;

import jakarta.validation.ConstraintViolationException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import uk.co.fuelfinder.api.dto.ApiErrorResponse;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleIllegalArgument(IllegalArgumentException ex) {
        return badRequest(ex.getMessage());
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleHandlerMethodValidation(HandlerMethodValidationException ex) {
        String message = ex.getParameterValidationResults().stream()
                .map(this::extractValidationMessage)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("Request parameter validation failed");

        return badRequest(message);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return badRequest(ex.getName() + " has an invalid value");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleMissingServletRequestParameter(MissingServletRequestParameterException ex) {
        return badRequest(ex.getParameterName() + " is required");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(violation -> violation.getMessage())
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("Request parameter validation failed");

        return badRequest(message);
    }

    private String extractValidationMessage(ParameterValidationResult result) {
        List<MessageSourceResolvable> errors = result.getResolvableErrors();
        if (errors.isEmpty()) {
            return null;
        }

        return errors.stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private ApiErrorResponse badRequest(String message) {
        return new ApiErrorResponse(
                OffsetDateTime.now().toString(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                message
        );
    }
}
