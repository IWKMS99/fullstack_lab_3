package iwkms.roomflow.modules.user.impl.service;

import iwkms.roomflow.exception.InvalidRefreshTokenException;
import iwkms.roomflow.exception.RefreshTokenExpiredException;
import iwkms.roomflow.modules.user.impl.domain.RefreshToken;
import iwkms.roomflow.modules.user.impl.domain.User;
import iwkms.roomflow.modules.user.impl.repository.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class RefreshTokenService {

    private static final int REFRESH_TOKEN_RANDOM_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    public String createRefreshToken(User user) {
        return createRefreshToken(user, UUID.randomUUID(), null);
    }

    @Transactional(noRollbackFor = {InvalidRefreshTokenException.class, RefreshTokenExpiredException.class})
    public RefreshRotationResult rotate(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidRefreshTokenException("Refresh token is missing");
        }

        RefreshToken token = refreshTokenRepository
                .findByTokenHash(hashToken(rawRefreshToken))
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token is invalid"));

        Instant now = Instant.now();
        if (token.getRevokedAt() != null) {
            refreshTokenRepository.revokeByFamilyId(token.getFamilyId(), now);
            throw new InvalidRefreshTokenException("Refresh token reuse detected");
        }

        if (!token.getExpiresAt().isAfter(now)) {
            token.setRevokedAt(now);
            refreshTokenRepository.save(token);
            throw new RefreshTokenExpiredException("Refresh token expired");
        }

        token.setRevokedAt(now);
        refreshTokenRepository.save(token);

        String nextRefreshToken = createRefreshToken(token.getUser(), token.getFamilyId(), token);
        return new RefreshRotationResult(
                token.getUser().getEmail(), token.getUser().getRoles(), nextRefreshToken);
    }

    public void revokeCurrentToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }

        refreshTokenRepository.findByTokenHash(hashToken(rawRefreshToken)).ifPresent(token -> {
            Instant now = Instant.now();
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(now);
                refreshTokenRepository.save(token);
            }
            refreshTokenRepository.revokeByFamilyId(token.getFamilyId(), now);
        });
    }

    private String createRefreshToken(User user, UUID familyId, RefreshToken rotatedFrom) {
        String rawToken = generateRawToken();
        RefreshToken refreshToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(user)
                .tokenHash(hashToken(rawToken))
                .expiresAt(Instant.now().plusMillis(refreshExpirationMs))
                .createdAt(Instant.now())
                .revokedAt(null)
                .familyId(familyId)
                .rotatedFrom(rotatedFrom)
                .build();

        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    private String generateRawToken() {
        byte[] random = new byte[REFRESH_TOKEN_RANDOM_BYTES];
        SECURE_RANDOM.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", ex);
        }
    }
}
