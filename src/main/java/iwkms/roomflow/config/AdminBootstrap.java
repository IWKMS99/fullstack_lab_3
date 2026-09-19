package iwkms.roomflow.config;

import iwkms.roomflow.modules.user.impl.domain.Role;
import iwkms.roomflow.modules.user.impl.domain.User;
import iwkms.roomflow.modules.user.impl.repository.UserRepository;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${BOOTSTRAP_ADMIN_EMAIL:}")
    private String email;

    @Value("${BOOTSTRAP_ADMIN_PASSWORD:}")
    private String password;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank() && password.isBlank()) {
            return;
        }
        if (!email.contains("@") || password.length() < 12) {
            throw new IllegalStateException("Bootstrap requires an email and a password of at least 12 characters");
        }
        if (userRepository.findByEmail(email).isPresent()) {
            return;
        }
        userRepository.save(User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .password(passwordEncoder.encode(password))
                .roles(Set.of(Role.ROLE_ADMIN))
                .build());
    }
}
