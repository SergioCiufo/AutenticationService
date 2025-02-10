package com.example.autenticationservice.domain.api;

import com.example.autenticationservice.domain.model.RefreshToken;

import java.util.Optional;

public interface RefreshTokenServiceApi {
    void addRefreshToken(RefreshToken refreshToken);
    Optional<RefreshToken> getRefreshToken(String refreshToken);
    void invalidateRefreshToken(String refreshToken);
}
