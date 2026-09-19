package iwkms.roomflow;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import iwkms.roomflow.modules.booking.impl.domain.Booking;
import iwkms.roomflow.modules.booking.impl.domain.BookingStatus;
import iwkms.roomflow.modules.booking.impl.domain.Room;
import iwkms.roomflow.modules.booking.impl.repository.BookingRepository;
import iwkms.roomflow.modules.booking.impl.repository.RoomRepository;
import iwkms.roomflow.modules.user.impl.domain.Role;
import iwkms.roomflow.modules.user.impl.domain.User;
import iwkms.roomflow.modules.user.impl.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AdminControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private RoomRepository roomRepository;

    private User adminUser;
    private User regularUser;
    private Room roomA;

    @BeforeEach
    void setUp() {
        adminUser = User.builder()
                .id(UUID.randomUUID())
                .email("admin@test.com")
                .password(passwordEncoder.encode("password"))
                .roles(Set.of(Role.ROLE_ADMIN))
                .build();

        regularUser = User.builder()
                .id(UUID.randomUUID())
                .email("user@test.com")
                .password(passwordEncoder.encode("password"))
                .roles(Set.of(Role.ROLE_USER))
                .build();

        userRepository.save(adminUser);
        userRepository.save(regularUser);
        roomA = roomRepository
                .findById(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))
                .orElseThrow();
    }

    @Test
    @DisplayName("GET /admin/users: должен возвращать 403 для обычного пользователя")
    void shouldReturnForbiddenForRegularUserWhenFetchingUsers() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users").with(user(regularUser))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /admin/users: должен возвращать список пользователей для администратора")
    void shouldReturnUsersForAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users").with(user(adminUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("PUT /admin/users/{id}/role: администратор должен успешно менять роль")
    void shouldUpdateRoleForUser() throws Exception {
        Map<String, String> request = Map.of("role", "ROLE_ADMIN");

        mockMvc.perform(put("/api/v1/admin/users/{userId}/role", regularUser.getId())
                        .with(user(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(regularUser.getId().toString())))
                .andExpect(jsonPath("$.roles[0]", is("ROLE_ADMIN")));
    }

    @Test
    @DisplayName("PUT /admin/users/{id}/role: должен возвращать 403 при self-demote")
    void shouldReturnForbiddenWhenAdminDemotesThemselves() throws Exception {
        Map<String, String> request = Map.of("role", "ROLE_USER");

        mockMvc.perform(put("/api/v1/admin/users/{userId}/role", adminUser.getId())
                        .with(user(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /admin/users/{id}/role: должен возвращать 404 для несуществующего пользователя")
    void shouldReturnNotFoundWhenUserDoesNotExist() throws Exception {
        Map<String, String> request = Map.of("role", "ROLE_ADMIN");

        mockMvc.perform(put("/api/v1/admin/users/{userId}/role", UUID.randomUUID())
                        .with(user(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /admin/bookings: администратор видит бронирования с фильтром по дате")
    void shouldReturnAdminBookingsForDate() throws Exception {
        LocalDate targetDate = LocalDate.now().plusDays(1);
        Booking booking = Booking.builder()
                .id(UUID.randomUUID())
                .room(roomA)
                .userId(regularUser.getId())
                .startTime(targetDate.atTime(10, 0))
                .endTime(targetDate.atTime(10, 30))
                .status(BookingStatus.CONFIRMED)
                .build();
        bookingRepository.saveAndFlush(booking);

        mockMvc.perform(get("/api/v1/admin/bookings").with(user(adminUser)).param("date", targetDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(booking.getId().toString())))
                .andExpect(jsonPath("$[0].userEmail", is(regularUser.getEmail())));
    }

    @Test
    @DisplayName("DELETE /admin/bookings/{id}: админ может отменять чужие бронирования")
    void shouldAllowAdminCancelAnyBookingFromAdminEndpoint() throws Exception {
        LocalDateTime start = LocalDateTime.now()
                .plusDays(2)
                .withHour(11)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);
        Booking booking = Booking.builder()
                .id(UUID.randomUUID())
                .room(roomA)
                .userId(regularUser.getId())
                .startTime(start)
                .endTime(start.plusMinutes(30))
                .status(BookingStatus.CONFIRMED)
                .build();
        bookingRepository.saveAndFlush(booking);

        mockMvc.perform(delete("/api/v1/admin/bookings/{bookingId}", booking.getId())
                        .with(user(adminUser)))
                .andExpect(status().isNoContent());

        Booking updated = bookingRepository.findById(booking.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(BookingStatus.CANCELLED, updated.getStatus());
    }
}
