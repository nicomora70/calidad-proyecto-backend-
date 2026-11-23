package co.ritech.notification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.ritech.notification.NotificationEnums.DeliveryStatus;
import co.ritech.notification.NotificationEnums.NotificationChannel;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    boolean existsByUserIdAndChannelAndDedupeKey(UUID userId, NotificationChannel channel, String dedupeKey);

    Page<Notification> findByUserIdAndChannel(UUID userId, NotificationChannel channel, Pageable pageable);

    Page<Notification> findByUserIdAndChannelAndReadAtIsNull(
            UUID userId, NotificationChannel channel, Pageable pageable);

    long countByUserIdAndChannelAndReadAtIsNull(UUID userId, NotificationChannel channel);

    Optional<Notification> findByIdAndUserIdAndChannel(UUID id, UUID userId, NotificationChannel channel);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.userId = :userId"
            + " and n.channel = co.ritech.notification.NotificationEnums.NotificationChannel.IN_APP"
            + " and n.readAt is null")
    int markAllRead(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("delete from Notification n where n.userId = :userId")
    int deleteAllForUser(@Param("userId") UUID userId);

    List<Notification> findByChannelAndStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAt(
            NotificationChannel channel, DeliveryStatus status, Instant now, Pageable pageable);
}
