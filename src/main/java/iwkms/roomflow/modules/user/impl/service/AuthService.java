package iwkms.roomflow.modules.user.impl.service;

import iwkms.roomflow.config.security.JwtService;
import iwkms.roomflow.exception.InvalidRefreshTokenException;
import iwkms.roomflow.exception.RefreshTokenExpiredException;
import iwkms.roomflow.exception.UserAlreadyExistsException;
import iwkms.roomflow.modules.user.api.dto.CurrentUserResponseDto;
import iwkms.roomflow.modules.user.api.dto.LoginRequestDto;
import iwkms.roomflow.modules.user.api.dto.RegisterRequestDto;
import iwkms.roomflow.modules.user.impl.domain.Role;
import iwkms.roomflow.modules.user.impl.domain.User;
import iwkms.roomflow.modules.user.impl.repository.UserRepository;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;

    public AuthTokens register(RegisterRequestDto request) {
        userRepository.findByEmail(request.email()).ifPresent(user -> {
            throw new UserAlreadyExistsException("User with email " + request.email() + " already exists.");
        });

        var user = User.builder()
                .id(UUID.randomUUID())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .roles(Set.of(Role.ROLE_USER))
                .build();
        userRepository.save(user);

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = refreshTokenService.createRefreshToken(user);
        return new AuthTokens(accessToken, refreshToken);
    }

    public AuthTokens login(LoginRequestDto request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        var user = userRepository.findByEmail(request.email()).orElseThrow();

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = refreshTokenService.createRefreshToken(user);
        return new AuthTokens(accessToken, refreshToken);
    }

    @Transactional(noRollbackFor = {InvalidRefreshTokenException.class, RefreshTokenExpiredException.class})
    public AuthTokens refresh(String refreshToken) {
        RefreshRotationResult rotationResult = refreshTokenService.rotate(refreshToken);
        Set<String> roles =
                rotationResult.roles().stream().map(Role::name).collect(java.util.stream.Collectors.toSet());
        String accessToken = jwtService.generateAccessToken(rotationResult.userEmail(), roles);
        return new AuthTokens(accessToken, rotationResult.refreshToken());
    }

    public void logout(String refreshToken) {
        refreshTokenService.revokeCurrentToken(refreshToken);
    }

    @Transactional(readOnly = true)
    public CurrentUserResponseDto me(User user) {
        Set<String> roles = user.getRoles().stream().map(Role::name).collect(java.util.stream.Collectors.toSet());
        return new CurrentUserResponseDto(user.getId(), user.getEmail(), roles);
    }
}
