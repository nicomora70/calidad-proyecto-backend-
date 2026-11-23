package co.ritech.common;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.http.HttpStatus;

/**
 * Half-open instant range [from, to) built from inclusive calendar dates in the
 * installation's timezone. Either bound may be null (open range).
 */
public record DateRange(Instant from, Instant to) {

    public static DateRange of(LocalDate fromDate, LocalDate toDate, ZoneId zone) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new InvalidDateRangeException();
        }
        Instant from = fromDate == null ? null : fromDate.atStartOfDay(zone).toInstant();
        Instant to =
                toDate == null ? null : toDate.plusDays(1).atStartOfDay(zone).toInstant();
        return new DateRange(from, to);
    }

    public static class InvalidDateRangeException extends BusinessException {
        public InvalidDateRangeException() {
            super(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "'from' must be on or before 'to'");
        }
    }
}
