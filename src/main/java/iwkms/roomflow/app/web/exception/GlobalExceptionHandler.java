package iwkms.roomflow.app.web.exception;

import iwkms.roomflow.app.web.exception.dto.ErrorResponseDto;
import iwkms.roomflow.app.web.exception.dto.ValidationErrorResponseDto;
import iwkms.roomflow.exception.BookingConflictException;
import iwkms.roomflow.exception.InvalidFileException;
import iwkms.roomflow.exception.InvalidRefreshTokenException;
import iwkms.roomflow.exception.RefreshTokenExpiredException;
import iwkms.roomflow.exception.ResourceNotFoundException;
import iwkms.roomflow.exception.UserAlreadyExistsException;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({
        org.springframework.web.method.annotation.HandlerMethodValidationException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
        org.springframework.http.converter.HttpMessageNotReadableException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponseDto handleInvalidInput(Exception ex) {
        return new ErrorResponseDto(HttpStatus.BAD_REQUEST.value(), "Invalid request parameters");
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<ErrorResponseDto> handleStatus(
            org.springframework.web.server.ResponseStatusException ex) {
        return org.springframework.http.ResponseEntity.status(ex.getStatusCode())
                .body(new ErrorResponseDto(ex.getStatusCode().value(), ex.getReason()));
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public ErrorResponseDto handleLargeUpload(org.springframework.web.multipart.MaxUploadSizeExceededException ex) {
        return new ErrorResponseDto(HttpStatus.PAYLOAD_TOO_LARGE.value(), "File size exceeds 5MB");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ValidationErrorResponseDto handleValidationExceptions(MethodArgumentNotValidException ex) {
        List<ValidationErrorResponseDto.Violation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ValidationErrorResponseDto.Violation(error.getField(), error.getDefaultMessage()))
                .collect(Collectors.toList());
        return new ValidationErrorResponseDto(HttpStatus.BAD_REQUEST.value(), "Validation failed", violations);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponseDto handleResourceNotFoundException(ResourceNotFoundException ex) {
        return new ErrorResponseDto(HttpStatus.NOT_FOUND.value(), ex.getMessage());
    }

    @ExceptionHandler(BookingConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponseDto handleBookingConflictException(BookingConflictException ex) {
        return new ErrorResponseDto(HttpStatus.CONFLICT.value(), ex.getMessage());
    }

    @ExceptionHandler(InvalidFileException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponseDto handleInvalidFileException(InvalidFileException ex) {
        return new ErrorResponseDto(HttpStatus.BAD_REQUEST.value(), ex.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponseDto handleBadCredentialsException(BadCredentialsException ex) {
        return new ErrorResponseDto(HttpStatus.UNAUTHORIZED.value(), "Invalid email or password");
    }

    @ExceptionHandler({InvalidRefreshTokenException.class, RefreshTokenExpiredException.class})
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponseDto handleRefreshTokenException(RuntimeException ex) {
        return new ErrorResponseDto(HttpStatus.UNAUTHORIZED.value(), ex.getMessage());
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponseDto handleUserAlreadyExistsException(UserAlreadyExistsException ex) {
        return new ErrorResponseDto(HttpStatus.CONFLICT.value(), ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponseDto handleAccessDeniedException(AccessDeniedException ex) {
        return new ErrorResponseDto(HttpStatus.FORBIDDEN.value(), ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponseDto handleAllUncaughtException(Exception ex) {
        log.error("An unexpected error occurred", ex);
        return new ErrorResponseDto(HttpStatus.INTERNAL_SERVER_ERROR.value(), "An unexpected server error occurred");
    }
}
