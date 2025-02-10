package com.example.autenticationservice.domain.util;

import com.example.autenticationservice.domain.model.Otp;
import com.example.autenticationservice.domain.model.User;
import com.example.autenticationservice.infrastructure.api.OtpUtil;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;

@Component
public class OtpUtilImpl implements OtpUtil {

    private final String WORDS = "0123456789";
    private final int OTP_LENGTH = 6;

//gestisce la scadenza dell'otp
//private final long OTP_EXPIRATION = 5 * 60 * 1000; //5 minuti

    public Otp generateOtp(User user, String sessionId) {
        //random più sicuro usato per generare chiavi
        SecureRandom secureRandom = new SecureRandom();
        StringBuilder otp = new StringBuilder();

        for (int i = 0; i < OTP_LENGTH; i++) {
            otp.append(WORDS.charAt(secureRandom.nextInt(WORDS.length())));
        }

        long creationDate = Instant.now().toEpochMilli();
        long expirationDate = calculateOtpExpirationTime();
        int attempts = 0;

        return Otp.builder()
                .user(user)
                .otp(otp.toString())
                .sessionId(sessionId)
                .createdAt(creationDate)
                .expiresAt(expirationDate)
                .attempts(attempts)
                .valid(true)
                .build();
    }

    public boolean isOtpExpired(long otpExpireTime) {
        long currentTime = Instant.now().toEpochMilli();
        return (currentTime > otpExpireTime);
    }

    public long calculateOtpExpirationTime() {
        return System.currentTimeMillis() + 1 * 60 * 1000; // 1 minuto
    }
}
