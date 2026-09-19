package iwkms.roomflow;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import iwkms.roomflow.exception.InvalidRefreshTokenException;
import iwkms.roomflow.modules.user.api.dto.RegisterRequestDto;
import iwkms.roomflow.modules.user.impl.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(properties = "spring.application.name=refresh-session-regression")
@Import(TestcontainersConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RefreshSessionIT {
    @Autowired
    private AuthService authService;

    @Test
    void replayRevokesTheEntireFamilyAcrossCommittedTransactions() {
        var initial = authService.register(new RegisterRequestDto("replay@example.test", "long-password123"));
        var rotated = authService.refresh(initial.refreshToken());
        assertNotEquals(initial.refreshToken(), rotated.refreshToken());
        assertNotNull(rotated.accessToken());
        assertThrows(InvalidRefreshTokenException.class, () -> authService.refresh(initial.refreshToken()));
        assertThrows(InvalidRefreshTokenException.class, () -> authService.refresh(rotated.refreshToken()));
    }

    @Test
    void logoutRevokesTheSessionAcrossCommittedTransactions() {
        var initial =
                authService.register(new RegisterRequestDto("logout-regression@example.test", "long-password123"));
        authService.logout(initial.refreshToken());
        assertThrows(InvalidRefreshTokenException.class, () -> authService.refresh(initial.refreshToken()));
    }
}
