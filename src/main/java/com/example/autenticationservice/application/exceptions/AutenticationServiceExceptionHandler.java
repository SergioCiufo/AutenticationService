package com.example.autenticationservice.application.exceptions;

import com.example.autenticationservice.application.jwt.RefreshTokenApp;
import com.example.autenticationservice.domain.exceptions.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

@ControllerAdvice
@Log4j2 //fornisce logger statico accessibile con log
@RequiredArgsConstructor
public class AutenticationServiceExceptionHandler {

    private RefreshTokenApp refreshTokenApp;

    private void logError(Exception ex, WebRequest request) {
        log.error(
                "An error happened while calling {} API: {}",
                ((ServletWebRequest) request).getRequest().getRequestURI(),
                ex.getMessage(), ex
        );
    }

    @ExceptionHandler({AutenticationServiceException.class})
    public ResponseEntity<Object> handleAutenticationServiceException(AutenticationServiceException ex, WebRequest request) {
        logError(ex, request);
        return ResponseEntity.unprocessableEntity().build();
    }

    @ExceptionHandler({InvalidCredentialsException.class})
    public ResponseEntity<Object> handleInvalidCredentialsException(InvalidCredentialsException ex, WebRequest request) {
        logError(ex, request);
        return ResponseEntity
                .status(401)
                .body(ex.getMessage());
    }

    @ExceptionHandler({CredentialTakenException.class})
    public ResponseEntity<Object> handleCredentialTakenException(CredentialTakenException ex, WebRequest request) {
        logError(ex, request);
        return ResponseEntity
                .status(401)
                .body(ex.getMessage());
    }

    @ExceptionHandler({ExpireOtpException.class})
    public ResponseEntity<Object> handleExpireOtpException(ExpireOtpException ex, WebRequest request) {
        logError(ex, request);
        return ResponseEntity
                .status(401)
                .body(ex.getMessage());
    }

    @ExceptionHandler({MissingTokenException.class})
    public ResponseEntity<Object> handleMissingTokenException(MissingTokenException ex, WebRequest request) {
        logError(ex, request);

        ResponseCookie cleanRefreshCookie = refreshTokenApp.getCleanJwtCookie();

        return ResponseEntity
                .status(401)
                .header(HttpHeaders.SET_COOKIE, cleanRefreshCookie.toString())
                .body(ex.getMessage());
    }

    @ExceptionHandler({InvalidSessionException.class})
    public ResponseEntity<Object> handleInvalidSessionException(InvalidSessionException ex, WebRequest request) {
        logError(ex, request);
        return ResponseEntity
                .status(401)
                .body(ex.getMessage());
    }

    @ExceptionHandler({TokenExpiredException.class})
    public ResponseEntity<Object> handleTokenExpiredException(TokenExpiredException ex, WebRequest request) {
        logError(ex, request);
        return ResponseEntity
                .status(403)
                .body(ex.getMessage());
    }

}