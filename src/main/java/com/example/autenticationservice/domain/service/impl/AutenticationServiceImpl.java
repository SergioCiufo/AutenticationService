package com.example.autenticationservice.domain.service.impl;

import com.example.autenticationservice.domain.api.EmailService;
import com.example.autenticationservice.domain.exceptions.*;
import com.example.autenticationservice.domain.model.newAccessTokenByRefreshToken.FirstStepNewAccessTokenByRefreshTokenRequest;
import com.example.autenticationservice.domain.model.newAccessTokenByRefreshToken.FirstStepNewAccessTokenByRefreshTokenResponse;
import com.example.autenticationservice.domain.model.Otp;
import com.example.autenticationservice.domain.model.RefreshToken;
import com.example.autenticationservice.domain.model.ResendOtp.FirstStepResendOtpResponse;
import com.example.autenticationservice.domain.model.User;
import com.example.autenticationservice.domain.model.login.FirstStepLoginRequest;
import com.example.autenticationservice.domain.model.login.FirstStepLoginResponse;
import com.example.autenticationservice.domain.model.logout.FirstStepLogoutResponse;
import com.example.autenticationservice.domain.model.register.FirstStepRegisterRequest;
import com.example.autenticationservice.domain.model.register.FirstStepRegisterResponse;
import com.example.autenticationservice.domain.model.verifyOtp.FirstStepVerifyOtpRequest;
import com.example.autenticationservice.domain.model.verifyOtp.FirstStepVerifyOtpResponse;
import com.example.autenticationservice.domain.model.verifyToken.FirstStepVerifyTokenResponse;
import com.example.autenticationservice.domain.service.*;
import com.example.autenticationservice.domain.util.JwtUtil;
import com.example.autenticationservice.domain.util.OtpUtil;
import com.example.autenticationservice.domain.util.UserListUtil;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
@AllArgsConstructor
@Log4j2
public class AutenticationServiceImpl implements AutenticationService {

    private final UserService userService;
    private final UserListUtil userListUtil;
    private final RegisterService registerService;
    private final EmailService emailService;
    private final OtpUtil otpUtil;
    private final OtpService otpService;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;

    @Override
    public FirstStepRegisterResponse register(FirstStepRegisterRequest firstStepRegisterRequest) {
        String name = firstStepRegisterRequest.getName();
        String username = firstStepRegisterRequest.getUsername();
        String email = firstStepRegisterRequest.getEmail();
        String password = firstStepRegisterRequest.getPassword();

        User newUser = new User(null, name, username, email, password, new ArrayList<>(), null);

        String registerValid = registerService.registerValid(newUser);
        if(registerValid != null) {
            log.error(registerValid);
            throw new CredentialTakenException(registerValid);
        }
        userService.add(newUser);

        return FirstStepRegisterResponse.builder()
                .message("Registrazione effettuata")
                .build();
    }

    @Override
    public FirstStepLoginResponse firstStepLogin(FirstStepLoginRequest firstStepLoginRequest, HttpSession session) {
        String username = firstStepLoginRequest.getUsername();
        String password = firstStepLoginRequest.getPassword();
        String sessionId = session.getId();

        //validazione credenziali
        User user = userService.validateCredentials(username, password);

        //generazione otp
        Otp otp = otpService.generateOtp(user, sessionId);

        user.getOtpList().add(otp);

        userService.updateUserOtpList(user);

        otpService.add(otp);

        //invio opt per email
        emailService.sendEmail(user.getEmail(), "Chat4Me - OTP code", otp.getOtp());

        //salvataggio utente nella sessione (da cambiare)
        session.setAttribute("username", user.getUsername());

        log.info("OTP generato {} e inviato a: {}", otp.getOtp(), user.getEmail());

        return FirstStepLoginResponse.builder()
                .message("Login effettuato, OTP inviato")
                .sessionId(sessionId)
                .build();
    }

    @Override
    public FirstStepVerifyOtpResponse firstStepVerifyOtp(FirstStepVerifyOtpRequest firstStepVerifyOtpRequest, HttpSession session, HttpServletResponse response) {
        final int MAX_OTP_ATTEMPTS = 3;
        final int MAX_SESSION_ATTEMPTS = 3;

        String otp = firstStepVerifyOtpRequest.getOtp();
        String sessionId = session.getId();

        Otp checkOtp3 = otpService.getOtpBySessionId(sessionId);

        //controllo per attacchi esterni

        Integer sessionAttempt = (Integer) session.getAttribute("sessionAttempt");
        sessionAttempt = (sessionAttempt == null) ? 0 : sessionAttempt;

        if(checkOtp3 == null){
            session.setAttribute("sessionAttempt", sessionAttempt + 1);
            throw new InvalidCredentialsException("OTP non valido");
        }

        if(sessionAttempt >= MAX_SESSION_ATTEMPTS){
            session.invalidate();
        }

        //fine controllo attacchi esterni

        Integer otpAttempt = checkOtp3.getAttempts();
        //otpAttempt preso dalla sessione è null? allora imposta a 0, sennò metti il valore
        otpAttempt = (otpAttempt == null) ? 0 : otpAttempt;

        if (otpAttempt >= MAX_OTP_ATTEMPTS){
            session.invalidate();
            otpService.setOtpInvalid(checkOtp3);
            log.error("Tentativi inserimento OTP esauriti");
            throw new ExpireOtpException("Tentativi inserimento OTP esauriti");
        }

        if (!checkOtp3.getOtp().equals(otp)) {
            otpService.updateAttempt(checkOtp3, otpAttempt+1);
            throw new InvalidCredentialsException("OTP non valido");
        }

        long otpExpireTime = checkOtp3.getExpiresAt();

        if (otpUtil.isOtpExpired(otpExpireTime)) {
            session.invalidate();
            otpService.setOtpInvalid(checkOtp3);
            log.error("OTP scaduto");
            throw new ExpireOtpException("OTP scaduto");
        }

        log.info("Tutto corretto. Generare Token");
        String username = session.getAttribute("username").toString();

        ResponseCookie refreshToken = jwtUtil.generateRefreshToken(username);

        String accessToken = jwtUtil.generateAccessToken(username);
        response.setHeader("Authorization", "Bearer " + accessToken);

        response.addCookie(new Cookie(refreshToken.getName(), refreshToken.getValue()));

        log.info(String.format("Access Token: %s",accessToken));
        log.info(String.format("Refresh Token: %s",refreshToken.getValue()));

        User user = userService.getUserFromUsername(username);
        if(user == null) {
            log.error("Utente non esistente");
        }

        RefreshToken refreshJwt = refreshTokenService.addRefreshToken(refreshToken, user);
        log.info("Oggetto Refresh User: {}, Token: {}", refreshJwt.getUser().getUsername(), refreshJwt.getRefreshToken());

        session.removeAttribute("sessionAttempt");
        session.removeAttribute("username");

        return FirstStepVerifyOtpResponse.builder()
                .token(accessToken)
                .build();
    }

    @Override
    public FirstStepResendOtpResponse firstStepResendOtp(HttpSession session) {
        String username = (String) session.getAttribute("username");

        // annulliamo l'otp precedente
        String sessionId = session.getId();
        Otp otpToInvalidate = otpService.getOtpBySessionId(sessionId);
        log.info("OTP da annullare: {}", otpToInvalidate.getOtp());
        otpService.setOtpInvalid(otpToInvalidate);

        session.removeAttribute("otpAttempt");
        log.info("OTP cancellato");

        //creiamo il nuovo otp
        User user = userService.getUserFromUsername(username);

        if(user == null) {
            log.warn("Utente non trovato per username: {}", username);
            throw new InvalidSessionException("Utente non valido o inesistente");
        }

        Otp newOtp = otpService.generateOtp(user, sessionId);
        otpService.add(newOtp);

        log.info("New otp: {}", newOtp.getOtp());

        String emailReceiver = user.getEmail();
        String emailSubject = "Chat4Me - OTP code";
        emailService.sendEmail(emailReceiver, emailSubject, newOtp.getOtp());

        return FirstStepResendOtpResponse.builder()
                .message("Nuovo Otp inviato")
                .build();
    }

    @Override
    public FirstStepVerifyTokenResponse firstStepVerifyToken(HttpSession session, HttpServletRequest request, HttpServletResponse response) {
        //voglio recuperarel 'access token
        String accessToken = jwtUtil.getAccessJwtFromHeader(request);


        if (accessToken == null || accessToken.isEmpty()) {
            log.error("Access token mancante");
            throw new MissingTokenException("Token mancante o inesistente");
        }

        log.info("Access token: {}",accessToken);

        try{
            jwtUtil.validateAccessToken(accessToken);
        }catch (ExpiredJwtException e){
            log.error("Access token scaduto, prova ottenimento nuovo tramite refresh token");
            throw new TokenExpiredException("Access token scaduto, prova ottenimento nuovo tramite refresh token");
        }

        log.debug("Access token prima di estrarre lo username: {}", accessToken);

        String username = jwtUtil.getUsernameFromAccessToken(accessToken);
        log.info("Username dall'accessToken: {}",username);

        return FirstStepVerifyTokenResponse.builder()
                .username(username)
                .build();
    }

    @Override
    public FirstStepNewAccessTokenByRefreshTokenResponse firstStepGetNewAccessToken(FirstStepNewAccessTokenByRefreshTokenRequest firstStepRequest, HttpServletRequest request, HttpSession session, HttpServletResponse response) {
        String refreshTokenString = jwtUtil.getRefreshJwtFromCookie(request);

        if (refreshTokenString == null || refreshTokenString.isEmpty()) {
            log.error("Refresh token mancante");
            session.invalidate();
            response.setHeader("Set-Cookie", jwtUtil.getCleanRefreshTokenCookie().toString());
            throw new MissingTokenException("Refresh Token mancante, effettuare login");
        }

        RefreshToken refreshToken = refreshTokenService.getRefreshTokenList(refreshTokenString);

        log.info("Refresh token: {}",refreshTokenString);

        if (!jwtUtil.validateRefreshToken(refreshTokenString)) {
            log.error("Refresh token non valido");
            refreshTokenService.invalidateRefreshToken(refreshToken);
            session.invalidate();
            response.setHeader("Set-Cookie", jwtUtil.getCleanRefreshTokenCookie().toString());
            throw new MissingTokenException("Refresh Token non valido, effettuare login");
        }

        String username = refreshToken.getUser().getUsername();

        String accessToken = jwtUtil.generateAccessToken(username);
        response.setHeader("Authorization", "Bearer " + accessToken);
        log.info(String.format("Access Token: %s",accessToken));

        return FirstStepNewAccessTokenByRefreshTokenResponse.builder()
                .message("Access Token Rigenerato")
                .accessToken(accessToken)
                .build();
    }

    @Override
    public FirstStepLogoutResponse firstStepLogout(HttpSession session, HttpServletResponse response, HttpServletRequest request) {
        String refreshTokenString = jwtUtil.getRefreshJwtFromCookie(request);

        if (!(refreshTokenString == null || refreshTokenString.isEmpty())) {
            RefreshToken refreshToken = refreshTokenService.getRefreshTokenList(refreshTokenString);
            refreshTokenService.invalidateRefreshToken(refreshToken);

            //sostiuisce il token con un token "con scadenza immediata, rimuovendolo
            response.setHeader("Set-Cookie", jwtUtil.getCleanRefreshTokenCookie().toString());

        }

        //prendi ed invalida l'access token
        response.setHeader("Authorization", "Bearer " + null);

        //invalidazione della sessione :()
        session.invalidate();

        log.info("Logged out successfully");
        return FirstStepLogoutResponse.builder()
                .message("Logout effettuato con successo. Token invalidati.")
                .build();
    }
}
