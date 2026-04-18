package uk.co.fuelfinder.api;

import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletRequest;
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
    public ApiErrorResponse handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return badRequest(request, ex.getMessage());
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleHandlerMethodValidation(
            HandlerMethodValidationException ex,
            HttpServletRequest request
    ) {
        String message = ex.getParameterValidationResults().stream()
                .map(this::extractValidationMessage)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("Request parameter validation failed");

        return badRequest(request, message);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request
    ) {
        return badRequest(request, ex.getName() + " has an invalid value");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex,
            HttpServletRequest request
    ) {
        return badRequest(request, ex.getParameterName() + " is required");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request
    ) {
        String message = ex.getConstraintViolations().stream()
                .map(violation -> violation.getMessage())
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("Request parameter validation failed");

        return badRequest(request, message);
    }

    @ExceptionHandler(StationNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiErrorResponse handleStationNotFound(
            StationNotFoundException ex,
            HttpServletRequest request
    ) {
        return error(request, HttpStatus.NOT_FOUND, ex.getMessage());
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

    private ApiErrorResponse badRequest(HttpServletRequest request, String message) {
        return error(request, HttpStatus.BAD_REQUEST, message);
    }

    private ApiErrorResponse error(HttpServletRequest request, HttpStatus status, String message) {
        request.setAttribute(ApiRequestLogAttributes.ERROR_MESSAGE, message);
        return new ApiErrorResponse(
                OffsetDateTime.now().toString(),
                status.value(),
                status.getReasonPhrase(),
                message
        );
    }
}
