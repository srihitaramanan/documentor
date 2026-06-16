package com.srihitaramanan.documentor.common;

import com.srihitaramanan.documentor.auth.EmailAlreadyRegisteredException;
import com.srihitaramanan.documentor.auth.InvalidCredentialsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.stream.Collectors;

/**
 * Translates exceptions into RFC 7807 Problem Details responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ProblemDetail handleEmailExists(EmailAlreadyRegisteredException ex) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        p.setTitle("Email already registered");
        p.setType(URI.create("https://documentor.dev/errors/email-already-registered"));
        return p;
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        p.setTitle("Invalid credentials");
        p.setType(URI.create("https://documentor.dev/errors/invalid-credentials"));
        return p;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
        p.setTitle("Validation failed");
        p.setType(URI.create("https://documentor.dev/errors/validation-failed"));
        return p;
    }
}