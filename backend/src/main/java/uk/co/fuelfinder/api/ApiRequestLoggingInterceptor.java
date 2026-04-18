package uk.co.fuelfinder.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
public class ApiRequestLoggingInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(ApiRequestLogAttributes.START_TIME_NANOS, System.nanoTime());
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            @Nullable Exception ex
    ) {
        int status = response.getStatus();
        long durationMs = calculateDurationMs(request);

        if (status >= 200 && status < 300) {
            log.info(
                    "event=station_query_completed method={} path={} lat={} lon={} radiusMeters={} fuelType={} limit={} status={} durationMs={} resultCount={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    request.getParameter("lat"),
                    request.getParameter("lon"),
                    request.getParameter("radiusMeters"),
                    request.getParameter("fuelType"),
                    request.getParameter("limit"),
                    status,
                    durationMs,
                    request.getAttribute(ApiRequestLogAttributes.RESULT_COUNT)
            );
            return;
        }

        if (status == HttpServletResponse.SC_BAD_REQUEST) {
            log.warn(
                    "event=station_query_bad_request method={} path={} lat={} lon={} radiusMeters={} fuelType={} limit={} status={} durationMs={} error={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    request.getParameter("lat"),
                    request.getParameter("lon"),
                    request.getParameter("radiusMeters"),
                    request.getParameter("fuelType"),
                    request.getParameter("limit"),
                    status,
                    durationMs,
                    resolveErrorMessage(request)
            );
        }
    }

    private long calculateDurationMs(HttpServletRequest request) {
        Object startTime = request.getAttribute(ApiRequestLogAttributes.START_TIME_NANOS);
        if (startTime instanceof Long startTimeNanos) {
            return (System.nanoTime() - startTimeNanos) / 1_000_000;
        }

        return -1L;
    }

    private Object resolveErrorMessage(HttpServletRequest request) {
        Object errorMessage = request.getAttribute(ApiRequestLogAttributes.ERROR_MESSAGE);
        return errorMessage != null ? errorMessage : "Bad Request";
    }
}
