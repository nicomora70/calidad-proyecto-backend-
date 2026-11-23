package co.ritech.market;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketVariationRepository extends JpaRepository<MarketVariation, UUID> {

    Optional<MarketVariation> findFirstBySymbolOrderByFetchedAtDesc(String symbol);

    Optional<MarketVariation> findBySymbolAndMarketDate(String symbol, LocalDate marketDate);

    /** Latest stored days (one row per market date), input of the projection. */
    List<MarketVariation> findTop250BySymbolOrderByMarketDateDesc(String symbol);
}
