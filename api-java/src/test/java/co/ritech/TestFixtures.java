package co.ritech;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import co.ritech.config.RitechProperties;

public final class TestFixtures {

    private TestFixtures() {
    }

    public static RitechProperties properties() {
        return new RitechProperties(
                new RitechProperties.Simulation(new BigDecimal("100000"), "COP"),
                new RitechProperties.Market("fixed", "QQQ", "", "http://unused",
                        new BigDecimal("1.0"), Duration.ofHours(6)),
                new RitechProperties.CalcEngine("http://unused", Duration.ofSeconds(1)),
                new RitechProperties.Cors(List.of("http://localhost:3000")));
    }
}
