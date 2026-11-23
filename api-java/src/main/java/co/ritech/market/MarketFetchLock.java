package co.ritech.market;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Transaction-scoped PostgreSQL advisory lock: when the cache expires, only one
 * request calls the market provider; concurrent ones wait and then reuse its result.
 */
@Component
public class MarketFetchLock {

    private final JdbcTemplate jdbc;

    public MarketFetchLock(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Must be called inside a transaction; released automatically at commit/rollback. */
    public void acquire(String symbol) {
        jdbc.query("select pg_advisory_xact_lock(hashtext(?))", rs -> null, "market:" + symbol);
    }
}
