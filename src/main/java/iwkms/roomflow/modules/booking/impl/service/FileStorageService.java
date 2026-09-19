package iwkms.roomflow.modules.booking.impl.service;

import iwkms.roomflow.config.storage.S3Properties;
import iwkms.roomflow.exception.InvalidFileException;
import java.io.IOException;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@Service
@RequiredArgsConstructor
public class FileStorageService {

    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "application/pdf");

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;

    public String uploadFile(MultipartFile file) {
        validateFile(file);

        String contentType = normalizeContentType(file.getContentType());
        String extension =
                switch (contentType) {
                    case "application/pdf" -> "pdf";
                    case "image/png" -> "png";
                    default -> "jpg";
                };
        String key = "rooms/" + UUID.randomUUID() + "." + extension;

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(key)
                .contentType(contentType)
                .build();

        try {
            byte[] bytes = file.getBytes();
            boolean matches =
                    switch (contentType) {
                        case "image/png" -> bytes.length >= 8
                                && bytes[0] == (byte) 0x89
                                && bytes[1] == 'P'
                                && bytes[2] == 'N'
                                && bytes[3] == 'G'
                                && bytes[4] == 13
                                && bytes[5] == 10
                                && bytes[6] == 26
                                && bytes[7] == 10;
                        case "image/jpeg" -> bytes.length >= 3
                                && bytes[0] == (byte) 0xff
                                && bytes[1] == (byte) 0xd8
                                && bytes[2] == (byte) 0xff;
                        case "application/pdf" -> bytes.length >= 5
                                && bytes[0] == '%'
                                && bytes[1] == 'P'
                                && bytes[2] == 'D'
                                && bytes[3] == 'F'
                                && bytes[4] == '-';
                        default -> false;
                    };
            if (!matches) {
                throw new InvalidFileException("File content does not match its declared type");
            }
            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(bytes));
        } catch (IOException ex) {
            throw new InvalidFileException("Failed to read uploaded file", ex);
        }

        return key;
    }

    public String generatePresignedUrl(String fileKey) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(fileKey)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(s3Properties.getPresignTtlMinutes()))
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
        return presignedRequest.url().toString();
    }

    public void deleteFile(String fileKey) {
        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(fileKey)
                .build();
        s3Client.deleteObject(deleteObjectRequest);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("File is empty");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new InvalidFileException("File size exceeds 5MB");
        }

        String contentType = normalizeContentType(file.getContentType());
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new InvalidFileException("Unsupported file type");
        }
    }

    private String normalizeContentType(String contentType) {
        return contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
    }
}
