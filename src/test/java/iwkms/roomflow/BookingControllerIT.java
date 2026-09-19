package iwkms.roomflow;

import static iwkms.roomflow.util.Constants.Test.*;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import iwkms.roomflow.modules.booking.api.dto.CreateBookingRequestDto;
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
import java.util.List;
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
class BookingControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser1, testUser2, testUser3, testUser4;
    private Room roomA;

    @BeforeEach
    void setUp() {
        testUser1 = User.builder()
                .id(USER_ID_1)
                .email("user1@test.com")
                .password(passwordEncoder.encode("password"))
                .roles(Set.of(Role.ROLE_USER))
                .build();
        testUser2 = User.builder()
                .id(USER_ID_2)
                .email("user2@test.com")
                .password(passwordEncoder.encode("password"))
                .roles(Set.of(Role.ROLE_USER))
                .build();
        testUser3 = User.builder()
                .id(USER_ID_3)
                .email("user3@test.com")
                .password(passwordEncoder.encode("password"))
                .roles(Set.of(Role.ROLE_USER))
                .build();
        testUser4 = User.builder()
                .id(USER_ID_4)
                .email("user4@test.com")
                .password(passwordEncoder.encode("password"))
                .roles(Set.of(Role.ROLE_USER))
                .build();
        userRepository.saveAll(List.of(testUser1, testUser2, testUser3, testUser4));
        roomA = roomRepository
                .findById(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))
                .orElseThrow();
    }

    @Test
    @DisplayName(
            "POST /bookings -> GET /my-bookings: должен создать бронирование для аутентифицированного пользователя и успешно его получить")
    void shouldCreateBookingAndRetrieveItSuccessfully() throws Exception {
        CreateBookingRequestDto createRequest = new CreateBookingRequestDto(
                ROOM_A_ID,
                LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withNano(0),
                LocalDateTime.now().plusDays(1).withHour(11).withMinute(0).withNano(0));

        mockMvc.perform(post("/api/v1/bookings")
                        .with(user(testUser1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId", is(USER_ID_1.toString())))
                .andExpect(jsonPath("$.status", is("CONFIRMED")));

        mockMvc.perform(get("/api/v1/my-bookings").with(user(testUser1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].userId", is(USER_ID_1.toString())));
    }

    @Test
    @DisplayName("GET /schedule: должен вернуть корректную сетку занятости")
    void shouldReturnCorrectScheduleForDate() throws Exception {
        LocalDate testDate = LocalDate.now().plusDays(1);
        Booking bookingForRoomA = Booking.builder()
                .id(UUID.randomUUID())
                .room(roomA)
                .userId(testUser2.getId())
                .startTime(testDate.atTime(10, 0))
                .endTime(testDate.atTime(11, 0))
                .status(BookingStatus.CONFIRMED)
                .build();
        bookingRepository.save(bookingForRoomA);

        mockMvc.perform(get("/api/v1/schedule")
                        .with(user(testUser1))
                        .param("date", testDate.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeSlots", hasSize(18)))
                .andExpect(jsonPath("$.timeSlots[2].rooms[0].isAvailable", is(false)))
                .andExpect(jsonPath("$.timeSlots[3].rooms[0].isAvailable", is(false)));
    }

    @Test
    @DisplayName("GET /schedule: должен быть доступен без авторизации")
    void shouldAllowAnonymousAccessToSchedule() throws Exception {
        LocalDate testDate = LocalDate.now().plusDays(1);

        mockMvc.perform(get("/api/v1/schedule").param("date", testDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeSlots", hasSize(18)));
    }

    @Test
    @DisplayName("DELETE /bookings/{id}: должен успешно отменить существующее бронирование")
    void shouldCancelBookingSuccessfully() throws Exception {
        Booking bookingToCancel = Booking.builder()
                .id(UUID.randomUUID())
                .room(roomA)
                .userId(testUser3.getId())
                .startTime(LocalDateTime.now().plusDays(2).withHour(14).withMinute(0))
                .endTime(LocalDateTime.now().plusDays(2).withHour(15).withMinute(0))
                .status(BookingStatus.CONFIRMED)
                .build();
        bookingRepository.saveAndFlush(bookingToCancel);

        mockMvc.perform(delete("/api/v1/bookings/" + bookingToCancel.getId()).with(user(testUser3)))
                .andExpect(status().isNoContent());

        Booking updatedBooking =
                bookingRepository.findById(bookingToCancel.getId()).orElseThrow();
        assertEquals(BookingStatus.CANCELLED, updatedBooking.getStatus());
    }

    @Test
    @DisplayName("DELETE /bookings/{id}: должен вернуть 404 Not Found для несуществующего бронирования")
    void shouldReturnNotFoundForNonExistentBooking() throws Exception {
        UUID nonExistentId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/bookings/" + nonExistentId).with(user(testUser1)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /bookings/{id}: должен вернуть 403, если пользователь отменяет чужую бронь")
    void shouldReturnForbiddenWhenUserCancelsForeignBooking() throws Exception {
        Booking bookingToCancel = Booking.builder()
                .id(UUID.randomUUID())
                .room(roomA)
                .userId(testUser1.getId())
                .startTime(LocalDateTime.now().plusDays(2).withHour(14).withMinute(0))
                .endTime(LocalDateTime.now().plusDays(2).withHour(15).withMinute(0))
                .status(BookingStatus.CONFIRMED)
                .build();
        bookingRepository.saveAndFlush(bookingToCancel);

        mockMvc.perform(delete("/api/v1/bookings/" + bookingToCancel.getId()).with(user(testUser2)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /bookings/{id}: администратор может отменить чужую бронь")
    void shouldAllowAdminToCancelForeignBooking() throws Exception {
        User adminUser = User.builder()
                .id(UUID.randomUUID())
                .email("admin@test.com")
                .password(passwordEncoder.encode("password"))
                .roles(Set.of(Role.ROLE_ADMIN))
                .build();
        userRepository.saveAndFlush(adminUser);

        Booking bookingToCancel = Booking.builder()
                .id(UUID.randomUUID())
                .room(roomA)
                .userId(testUser1.getId())
                .startTime(LocalDateTime.now().plusDays(2).withHour(14).withMinute(0))
                .endTime(LocalDateTime.now().plusDays(2).withHour(15).withMinute(0))
                .status(BookingStatus.CONFIRMED)
                .build();
        bookingRepository.saveAndFlush(bookingToCancel);

        mockMvc.perform(delete("/api/v1/bookings/" + bookingToCancel.getId()).with(user(adminUser)))
                .andExpect(status().isNoContent());

        Booking updatedBooking =
                bookingRepository.findById(bookingToCancel.getId()).orElseThrow();
        assertEquals(BookingStatus.CANCELLED, updatedBooking.getStatus());
    }

    @Test
    @DisplayName("GET /my-bookings: должен вернуть 401 без авторизации")
    void shouldReturnUnauthorizedForProtectedEndpointWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/my-bookings")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /bookings: должен вернуть 400 Bad Request, если время начала после времени окончания")
    void shouldReturnBadRequestWhenStartTimeIsAfterEndTime() throws Exception {
        CreateBookingRequestDto invalidRequest = new CreateBookingRequestDto(
                ROOM_A_ID,
                LocalDateTime.now().plusDays(1).withHour(11).withMinute(0).withNano(0),
                LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withNano(0));

        mockMvc.perform(post("/api/v1/bookings")
                        .with(user(testUser1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].description", is("End time must be after start time")));
    }

    @Test
    @DisplayName("POST /bookings: должен вернуть 409 Conflict при попытке забронировать уже занятое время")
    void shouldReturnConflictWhenBookingOverlaps() throws Exception {
        Booking initialBooking = Booking.builder()
                .id(UUID.randomUUID())
                .room(roomA)
                .userId(testUser4.getId())
                .startTime(LocalDateTime.now().plusDays(3).withHour(12).withMinute(0))
                .endTime(LocalDateTime.now().plusDays(3).withHour(13).withMinute(0))
                .status(BookingStatus.CONFIRMED)
                .build();
        bookingRepository.saveAndFlush(initialBooking);

        CreateBookingRequestDto conflictingRequest = new CreateBookingRequestDto(
                ROOM_A_ID,
                LocalDateTime.now().plusDays(3).withHour(12).withMinute(30),
                LocalDateTime.now().plusDays(3).withHour(13).withMinute(30));

        mockMvc.perform(post("/api/v1/bookings")
                        .with(user(testUser1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conflictingRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("Room is already booked for the selected time period.")));
    }

    @Test
    @DisplayName("POST /bookings: нельзя создать бронирование для неактивной комнаты")
    void shouldReturnNotFoundWhenRoomIsInactive() throws Exception {
        roomA.setActive(false);
        roomRepository.saveAndFlush(roomA);

        CreateBookingRequestDto request = new CreateBookingRequestDto(
                ROOM_A_ID,
                LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withNano(0),
                LocalDateTime.now().plusDays(1).withHour(11).withMinute(0).withNano(0));

        mockMvc.perform(post("/api/v1/bookings")
                        .with(user(testUser1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /schedule: не должен возвращать неактивные комнаты")
    void shouldHideInactiveRoomsFromSchedule() throws Exception {
        roomA.setActive(false);
        roomRepository.saveAndFlush(roomA);
        LocalDate testDate = LocalDate.now().plusDays(1);

        mockMvc.perform(get("/api/v1/schedule")
                        .with(user(testUser1))
                        .param("date", testDate.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeSlots[0].rooms", hasSize(1)))
                .andExpect(jsonPath("$.timeSlots[0].rooms[0].roomId", is("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")));
    }
}
