package iwkms.roomflow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import iwkms.roomflow.modules.booking.impl.service.FileStorageService;
import iwkms.roomflow.modules.user.impl.domain.Role;
import iwkms.roomflow.modules.user.impl.domain.User;
import iwkms.roomflow.modules.user.impl.repository.UserRepository;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AdminRoomFileControllerIT {

    private static final UUID ROOM_A_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private FileStorageService fileStorageService;

    private User adminUser;

    @BeforeEach
    void setUp() {
        adminUser = User.builder()
                .id(UUID.randomUUID())
                .email("admin-room-file@test.com")
                .password(passwordEncoder.encode("password"))
                .roles(Set.of(Role.ROLE_ADMIN))
                .build();

        userRepository.save(adminUser);

        when(fileStorageService.uploadFile(any())).thenReturn("rooms/test-file.png");
        when(fileStorageService.generatePresignedUrl(anyString())).thenReturn("http://localhost:9000/presigned");
        doNothing().when(fileStorageService).deleteFile(anyString());
    }

    @Test
    @DisplayName("POST /admin/rooms/{id}/files: uploads valid PNG")
    void shouldUploadRoomFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "room.png", "image/png", "dummy-content".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/admin/rooms/{id}/files", ROOM_A_ID)
                        .file(file)
                        .with(user(adminUser)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileKey").value("rooms/test-file.png"));
    }

    @Test
    @DisplayName("GET /admin/rooms/{id}/files and DELETE /admin/rooms/files/{fileId}: list and delete file")
    void shouldListAndDeleteRoomFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "room.pdf",
                "application/pdf",
                "dummy-content".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        String response = mockMvc.perform(multipart("/api/v1/admin/rooms/{id}/files", ROOM_A_ID)
                        .file(file)
                        .with(user(adminUser)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String fileId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(response)
                .get("id")
                .asText();

        mockMvc.perform(get("/api/v1/admin/rooms/{id}/files", ROOM_A_ID).with(user(adminUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(fileId));

        mockMvc.perform(delete("/api/v1/admin/rooms/files/{fileId}", fileId).with(user(adminUser)))
                .andExpect(status().isNoContent());

        verify(fileStorageService).deleteFile("rooms/test-file.png");
    }
}
