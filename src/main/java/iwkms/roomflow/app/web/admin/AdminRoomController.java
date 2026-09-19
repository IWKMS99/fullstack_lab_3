package iwkms.roomflow.app.web.admin;

import iwkms.roomflow.modules.booking.api.dto.AdminRoomDto;
import iwkms.roomflow.modules.booking.api.dto.CreateRoomRequestDto;
import iwkms.roomflow.modules.booking.api.dto.PageResponseDto;
import iwkms.roomflow.modules.booking.api.dto.UpdateRoomRequestDto;
import iwkms.roomflow.modules.booking.impl.service.AdminRoomService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/admin/rooms")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminRoomController {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("name", "capacity", "floor");

    private final AdminRoomService adminRoomService;

    @GetMapping
    public ResponseEntity<PageResponseDto<AdminRoomDto>> getRooms(
            @RequestParam(required = false) @Size(max = 255) String search,
            @RequestParam(required = false) @Min(1) Integer floor,
            @RequestParam(required = false) @Min(1) Integer minCapacity,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "name,asc") String sort) {
        Pageable pageable = buildPageable(page, size, sort);
        Page<AdminRoomDto> result = adminRoomService.findRooms(search, floor, minCapacity, pageable);

        PageResponseDto<AdminRoomDto> response = new PageResponseDto<>(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                sort);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<AdminRoomDto> createRoom(@Valid @RequestBody CreateRoomRequestDto request) {
        AdminRoomDto created = adminRoomService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/rooms/" + created.id()))
                .body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdminRoomDto> updateRoom(
            @PathVariable UUID id, @Valid @RequestBody UpdateRoomRequestDto request) {
        return ResponseEntity.ok(adminRoomService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRoom(@PathVariable UUID id) {
        adminRoomService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    private Pageable buildPageable(int page, int size, String sort) {
        String[] sortParts = sort.split(",");
        String field = sortParts.length > 0 ? sortParts[0].trim() : "name";
        String directionRaw = sortParts.length > 1 ? sortParts[1].trim() : "asc";

        if (sortParts.length > 2
                || !ALLOWED_SORT_FIELDS.contains(field)
                || !("asc".equalsIgnoreCase(directionRaw) || "desc".equalsIgnoreCase(directionRaw))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid sort: use name, capacity or floor and asc/desc");
        }

        Sort.Direction direction = "desc".equalsIgnoreCase(directionRaw) ? Sort.Direction.DESC : Sort.Direction.ASC;
        return PageRequest.of(page, size, Sort.by(direction, field));
    }
}
