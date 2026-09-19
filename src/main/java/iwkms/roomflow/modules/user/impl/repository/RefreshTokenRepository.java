package iwkms.roomflow.modules.user.impl.repository;

import iwkms.roomflow.modules.user.impl.domain.RefreshToken;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query(
            """
            update RefreshToken rt
            set rt.revokedAt = :revokedAt
            where rt.familyId = :familyId and rt.revokedAt is null
            """)
    int revokeByFamilyId(@Param("familyId") UUID familyId, @Param("revokedAt") Instant revokedAt);

    @Modifying
    @Query(
            """
            delete from RefreshToken rt
            where rt.expiresAt < :now or (rt.revokedAt is not null and rt.revokedAt < :revokedBefore)
            """)
    int deleteExpiredAndOldRevoked(@Param("now") Instant now, @Param("revokedBefore") Instant revokedBefore);
}
