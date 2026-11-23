package co.ritech.common;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DateRangeTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota"); // UTC-5

    @Test
    void inclusiveDatesBecomeHalfOpenInstantRangeInInstallationZone() {
        var range = DateRange.of(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 20), BOGOTA);

        assertThat(range.from()).isEqualTo(Instant.parse("2026-11-20T05:00:00Z"));
        assertThat(range.to()).isEqualTo(Instant.parse("2026-11-21T05:00:00Z"));
    }

    @Test
    void openBoundsStayNull() {
        var range = DateRange.of(null, null, BOGOTA);
        assertThat(range.from()).isNull();
        assertThat(range.to()).isNull();
    }

    @Test
    void fromAfterToIsRejected() {
        assertThatThrownBy(() -> DateRange.of(LocalDate.of(2026, 11, 21), LocalDate.of(2026, 11, 20), BOGOTA))
                .isInstanceOf(DateRange.InvalidDateRangeException.class);
    }
}
