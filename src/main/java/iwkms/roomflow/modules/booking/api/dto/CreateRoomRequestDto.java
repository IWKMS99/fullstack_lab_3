package iwkms.roomflow.modules.booking.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRoomRequestDto(@NotBlank @Size(max = 255) String name, @Min(1) int floor, @Min(1) int capacity) {}
