package iwkms.roomflow.modules.booking.impl.service;

import iwkms.roomflow.exception.ResourceNotFoundException;
import iwkms.roomflow.modules.booking.api.dto.RoomFileDto;
import iwkms.roomflow.modules.booking.impl.domain.Room;
import iwkms.roomflow.modules.booking.impl.domain.RoomFile;
import iwkms.roomflow.modules.booking.impl.repository.RoomFileRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminRoomFileService {

    private final AdminRoomService adminRoomService;
    private final RoomFileRepository roomFileRepository;
    private final FileStorageService fileStorageService;

    public RoomFileDto upload(UUID roomId, MultipartFile file) {
        Room room = adminRoomService.requireActiveRoom(roomId);
        String fileKey = fileStorageService.uploadFile(file);
        String contentType = file.getContentType();

        RoomFile roomFile = RoomFile.builder()
                .id(UUID.randomUUID())
                .room(room)
                .fileKey(fileKey)
                .originalName(file.getOriginalFilename() == null ? "file" : file.getOriginalFilename())
                .contentType(
                        contentType == null
                                ? "application/octet-stream"
                                : contentType.trim().toLowerCase(Locale.ROOT))
                .size(file.getSize())
                .build();

        try {
            RoomFile saved = roomFileRepository.saveAndFlush(roomFile);
            return toDto(saved);
        } catch (RuntimeException ex) {
            try {
                fileStorageService.deleteFile(fileKey);
            } catch (RuntimeException cleanupFailure) {
                ex.addSuppressed(cleanupFailure);
            }
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public List<RoomFileDto> list(UUID roomId) {
        adminRoomService.requireActiveRoom(roomId);
        return roomFileRepository.findByRoom_Id(roomId).stream()
                .map(this::toDto)
                .toList();
    }

    public void delete(UUID fileId) {
        RoomFile file = roomFileRepository
                .findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("Room file with id " + fileId + " not found"));
        fileStorageService.deleteFile(file.getFileKey());
        roomFileRepository.delete(file);
    }

    private RoomFileDto toDto(RoomFile roomFile) {
        return new RoomFileDto(
                roomFile.getId(),
                roomFile.getFileKey(),
                roomFile.getOriginalName(),
                roomFile.getContentType(),
                roomFile.getSize(),
                fileStorageService.generatePresignedUrl(roomFile.getFileKey()));
    }
}
