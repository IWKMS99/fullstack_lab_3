package iwkms.roomflow;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import iwkms.roomflow.modules.user.impl.domain.Role;
import iwkms.roomflow.modules.user.impl.domain.User;
import iwkms.roomflow.modules.user.impl.repository.UserRepository;
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
class AdminRoomControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User adminUser;
    private User regularUser;

    @BeforeEach
    void setUp() {
        adminUser = User.builder()
                .id(UUID.randomUUID())
                .email("admin-room@test.com")
                .password(passwordEncoder.encode("password"))
                .roles(Set.of(Role.ROLE_ADMIN))
                .build();

        regularUser = User.builder()
                .id(UUID.randomUUID())
                .email("user-room@test.com")
                .password(passwordEncoder.encode("password"))
                .roles(Set.of(Role.ROLE_USER))
                .build();

        userRepository.save(adminUser);
        userRepository.save(regularUser);
    }

    @Test
    void shouldRejectInvalidFilterAndPaginationParameters() throws Exception {
        for (var parameter : Map.of("page", "-1", "size", "101", "floor", "0", "minCapacity", "-1", "sort", "id,asc")
                .entrySet()) {
            mockMvc.perform(get("/api/v1/admin/rooms")
                            .with(user(adminUser))
                            .param(parameter.getKey(), parameter.getValue()))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    @DisplayName("GET /admin/rooms: admin receives paginated active rooms")
    void shouldReturnRoomsForAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/rooms")
                        .with(user(adminUser))
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    @DisplayName("GET /admin/rooms: regular user gets 403")
    void shouldReturnForbiddenForRegularUser() throws Exception {
        mockMvc.perform(get("/api/v1/admin/rooms").with(user(regularUser))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST/PUT/DELETE /admin/rooms: admin can execute CRUD")
    void shouldAllowCrudForAdmin() throws Exception {
        Map<String, Object> createRequest = Map.of("name", "Room Z", "floor", 7, "capacity", 14);

        String createResponse = mockMvc.perform(post("/api/v1/admin/rooms")
                        .with(user(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Room Z"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String roomId = objectMapper.readTree(createResponse).get("id").asText();

        Map<String, Object> updateRequest = Map.of("name", "Room Z Updated", "floor", 8, "capacity", 20);

        mockMvc.perform(put("/api/v1/admin/rooms/{id}", roomId)
                        .with(user(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Room Z Updated"));

        mockMvc.perform(delete("/api/v1/admin/rooms/{id}", roomId).with(user(adminUser)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/admin/rooms").with(user(adminUser)).param("search", "Room Z Updated"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }
}
