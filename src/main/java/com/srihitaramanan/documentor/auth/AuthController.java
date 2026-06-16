package com.srihitaramanan.documentor.auth;

import com.srihitaramanan.documentor.user.User;
import com.srihitaramanan.documentor.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Public auth endpoints — register and login.
 *
 * <p>Both return a JWT on success. See ADR-006 for stateless-JWT rationale.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "auth", description = "Registration and login")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    // ---- Register ----------------------------------------------------------

    @PostMapping("/register")
    @Operation(summary = "Create a new user and return a JWT")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new EmailAlreadyRegisteredException(req.email());
        }
        User user = User.newUser(req.email(), passwordEncoder.encode(req.password()));
        userRepository.save(user);

        String token = jwtService.issueToken(user.getId());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new AuthResponse(token, user.getId().toString(), expiryIso(token)));
    }

    // ---- Login -------------------------------------------------------------

    @PostMapping("/login")
    @Operation(summary = "Authenticate an existing user and return a JWT")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        User user = userRepository.findByEmail(req.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        String token = jwtService.issueToken(user.getId());
        return new AuthResponse(token, user.getId().toString(), expiryIso(token));
    }

    private String expiryIso(String token) {
        return Instant.now()
                .plus(jwtService.timeUntilExpiry(token).toMinutes(), ChronoUnit.MINUTES)
                .toString();
    }

    // ---- Request / Response records ---------------------------------------

    public record RegisterRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 8, max = 128) String password
    ) {}

    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password
    ) {}

    public record AuthResponse(String token, String userId, String expiresAt) {}
}