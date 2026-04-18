package uk.co.fuelfinder.api;

public final class ApiRequestLogAttributes {

    public static final String START_TIME_NANOS = ApiRequestLogAttributes.class.getName() + ".startTimeNanos";
    public static final String RESULT_COUNT = ApiRequestLogAttributes.class.getName() + ".resultCount";
    public static final String ERROR_MESSAGE = ApiRequestLogAttributes.class.getName() + ".errorMessage";

    private ApiRequestLogAttributes() {
    }
}
