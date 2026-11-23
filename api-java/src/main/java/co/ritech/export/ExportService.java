package co.ritech.export;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.ritech.audit.AuditLog;
import co.ritech.audit.AuditService;
import co.ritech.common.DateRange;
import co.ritech.config.RitechProperties;
import co.ritech.simulation.Simulation;
import co.ritech.simulation.SimulationService;

/** CSV exports (module EXPORTS). Capped at MAX_ROWS per file; every export is audited. */
@Service
public class ExportService {

    static final int MAX_ROWS = 50_000;

    private final SimulationService simulations;
    private final AuditService audit;
    private final RitechProperties properties;

    public ExportService(SimulationService simulations, AuditService audit, RitechProperties properties) {
        this.simulations = simulations;
        this.audit = audit;
        this.properties = properties;
    }

    /** @param ownerId null = every user (ADMIN) */
    @Transactional
    public byte[] simulations(UUID actorId, UUID ownerId, LocalDate from, LocalDate to) {
        var range = DateRange.of(from, to, properties.timezone());
        var page = simulations.search(ownerId, range, firstRows());
        var csv = new Csv(List.of(
                "id",
                "user_id",
                "created_at",
                "initial_capital",
                "currency",
                "symbol",
                "market_date",
                "market_variation_pct",
                "leverage",
                "return_pct",
                "final_amount",
                "profit"));
        for (Simulation s : page.getContent()) {
            csv.row(List.of(
                    s.getId(),
                    s.getUserId(),
                    s.getCreatedAt(),
                    s.getInitialCapital().toPlainString(),
                    s.getCurrency(),
                    s.getSymbol(),
                    s.getMarketDate(),
                    s.getMarketVariationPct().toPlainString(),
                    s.getLeverage().toPlainString(),
                    s.getReturnPct().toPlainString(),
                    s.getFinalAmount().toPlainString(),
                    s.getProfit().toPlainString()));
        }
        audited(actorId, "simulations", page.getNumberOfElements());
        return csv.bytes();
    }

    @Transactional
    public byte[] audit(UUID actorId, String action, UUID filterActor, LocalDate from, LocalDate to) {
        var range = DateRange.of(from, to, properties.timezone());
        var page = audit.search(action, filterActor, range, firstRows());
        var csv = new Csv(List.of(
                "id", "created_at", "actor_id", "action", "entity_type", "entity_id", "details", "ip", "user_agent"));
        for (AuditLog a : page.getContent()) {
            csv.row(java.util.Arrays.asList(
                    a.getId(),
                    a.getCreatedAt(),
                    a.getActorId(),
                    Csv.text(a.getAction()),
                    Csv.text(a.getEntityType()),
                    a.getEntityId(),
                    Csv.text(a.getDetails()),
                    Csv.text(a.getIpAddress()),
                    Csv.text(a.getUserAgent())));
        }
        audited(actorId, "audit", page.getNumberOfElements());
        return csv.bytes();
    }

    private static PageRequest firstRows() {
        return PageRequest.of(0, MAX_ROWS, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private void audited(UUID actorId, String type, int rows) {
        audit.record(actorId, "EXPORT_CREATED", "EXPORT", null, "type=" + type + " rows=" + rows);
    }
}
