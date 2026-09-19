package iwkms.roomflow;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import iwkms.roomflow.config.storage.S3Config;
import iwkms.roomflow.config.storage.S3Properties;
import iwkms.roomflow.exception.InvalidFileException;
import iwkms.roomflow.modules.booking.impl.service.FileStorageService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

@Testcontainers
class FileStorageIT {
    @Container
    private static final GenericContainer<?> MINIO = new GenericContainer<>(
                    DockerImageName.parse("quay.io/minio/minio:RELEASE.2025-04-22T22-12-26Z"))
            .withEnv("MINIO_ROOT_USER", "test-storage-user")
            .withEnv("MINIO_ROOT_PASSWORD", "test-storage-password")
            .withCommand("server", "/data")
            .withExposedPorts(9000);

    @Test
    void uploadDownloadAndDeleteThroughRealPrivateObjectStorage() throws Exception {
        S3Properties properties = new S3Properties();
        String endpoint = "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
        properties.setEndpoint(endpoint);
        properties.setPublicEndpoint(endpoint);
        properties.setAccessKey("test-storage-user");
        properties.setSecretKey("test-storage-password");
        S3Config config = new S3Config();
        try (var client = config.s3Client(properties);
                var presigner = config.s3Presigner(properties);
                var http = HttpClient.newHttpClient()) {
            client.createBucket(
                    CreateBucketRequest.builder().bucket(properties.getBucket()).build());
            FileStorageService storage = new FileStorageService(client, presigner, properties);
            byte[] content = "%PDF-1.4\nRoomFlow test\n%%EOF".getBytes(StandardCharsets.UTF_8);
            String key = storage.uploadFile(new MockMultipartFile("file", "guide.pdf", "application/pdf", content));
            URI signedUrl = URI.create(storage.generatePresignedUrl(key));
            var downloaded =
                    http.send(HttpRequest.newBuilder(signedUrl).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, downloaded.statusCode());
            assertArrayEquals(content, downloaded.body());
            URI unsignedUrl = URI.create(endpoint + "/" + properties.getBucket() + "/" + key);
            assertEquals(
                    403,
                    http.send(HttpRequest.newBuilder(unsignedUrl).GET().build(), HttpResponse.BodyHandlers.discarding())
                            .statusCode());
            storage.deleteFile(key);
            assertEquals(
                    404,
                    http.send(HttpRequest.newBuilder(signedUrl).GET().build(), HttpResponse.BodyHandlers.discarding())
                            .statusCode());
            assertThrows(
                    InvalidFileException.class,
                    () -> storage.uploadFile(new MockMultipartFile("file", "fake.png", "image/png", content)));
            assertThrows(
                    InvalidFileException.class,
                    () -> storage.uploadFile(new MockMultipartFile(
                            "file", "large.pdf", "application/pdf", new byte[5 * 1024 * 1024 + 1])));
        }
    }
}
