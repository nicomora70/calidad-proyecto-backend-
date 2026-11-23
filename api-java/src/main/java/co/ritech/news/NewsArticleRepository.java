package co.ritech.news;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NewsArticleRepository extends JpaRepository<NewsArticle, UUID>, JpaSpecificationExecutor<NewsArticle> {

    @Query("select a.url from NewsArticle a where a.url in :urls")
    Set<String> findExistingUrls(@Param("urls") Collection<String> urls);

    List<NewsArticle> findByPublishedAtGreaterThanEqualOrderByPublishedAtDesc(Instant from, Pageable pageable);

    @Query("select a.sentimentScore from NewsArticle a"
            + " where a.publishedAt >= :from and a.publishedAt < :to and a.sentimentScore is not null")
    List<BigDecimal> sentimentScoresBetween(@Param("from") Instant from, @Param("to") Instant to);

    static Specification<NewsArticle> publishedFrom(Instant from) {
        return (root, query, cb) -> from == null ? null : cb.greaterThanOrEqualTo(root.get("publishedAt"), from);
    }

    static Specification<NewsArticle> publishedBefore(Instant to) {
        return (root, query, cb) -> to == null ? null : cb.lessThan(root.get("publishedAt"), to);
    }

    static Specification<NewsArticle> withSentiment(String label) {
        return (root, query, cb) -> label == null || label.isBlank()
                ? null
                : cb.equal(cb.lower(root.get("sentimentLabel")), label.toLowerCase());
    }
}
