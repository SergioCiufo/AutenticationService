package com.example.autenticationservice.application;

import com.example.autenticationservice.application.mapper.*;
import com.example.autenticationservice.application.service.JwtServiceImpl;
import com.example.autenticationservice.domain.model.autentication.ThirdStepResendOtpResponse;
import com.example.autenticationservice.domain.model.autentication.ThridStepResendOtpRequest;
import com.example.autenticationservice.domain.model.register.StepRegisterRequest;
import com.example.autenticationservice.domain.model.register.StepRegisterResponse;
import com.example.autenticationservice.domain.model.verifyToken.SecondStepGetAccessTokenByRefreshTokenRequest;
import com.example.autenticationservice.domain.model.verifyToken.SecondStepGetAccessTokenByRefreshTokenResponse;
import com.example.autenticationservice.domain.model.autentication.FirstStepLoginRequest;
import com.example.autenticationservice.domain.model.autentication.FirstStepLoginResponse;
import com.example.autenticationservice.domain.model.autentication.FourthStepLogoutResponse;
import com.example.autenticationservice.domain.model.autentication.SecondStepVerifyOtpRequest;
import com.example.autenticationservice.domain.model.autentication.SecondStepVerifyOtpResponse;
import com.example.autenticationservice.domain.model.verifyToken.FirstStepVerifyTokenResponse;
import com.example.autenticationservice.domain.service.*;

import com.example.autenticationservice.generated.application.api.ServizioAutenticazioneApiDelegate;
import com.example.autenticationservice.generated.application.model.*;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RequiredArgsConstructor //creami costruttore con parametri richiesti (final) @Service
@RestController
public class ServizioAutenticazioneApiDelegateImpl implements ServizioAutenticazioneApiDelegate {

    private final AutenticationMappers autenticationMappers;
    private final AutenticationService autenticationService;
    private final JwtServiceImpl jwtServiceImpl;

    // TODO non c'è bisogno che fai First,Second,Third,Fourth le operazioni connesse tra loro sono solo  firstStep e secondStep il resto 
    // confonde e basta, mi sembra di giocare a tombola a guardare i nomi delle classi
    // - FirstStepLoginRequest
    // - SecondStepVerifyOtpRequest
    // - ThridStepResendOtpRequest(third)
    // - FirstStepVerifyTokenResponse
    // - SecondStepGetAccessTokenByRefreshTokenRequest
    // - FourthStepLogoutResponse
    // inoltre la notazione first,second si mette con lo stesso nome a seguito per far capire che è uno step successivo della stessa operazione
    // firstStepLogin, secondStepLogin(firstStep, secondStep servono per dire è il primo/secondo step di LOGIN) se cambi pure l'operazione diventa appunto una tombola
    @Override
    public ResponseEntity<Register200Response> register(RegisterRequest registerRequest){
        StepRegisterRequest request = autenticationMappers.convertToDomain(registerRequest);
        StepRegisterResponse response = autenticationService.register(request);
        Register200Response convertedResponse = autenticationMappers.convertFromDomain(response);
        return ResponseEntity.ok(convertedResponse);
    }

    @Override
    public ResponseEntity<Login200Response> login(LoginRequest loginRequest){
        // TODO come crei il sessionId è roba di dominio non di application, il sessionId ti verrà staccato dal domain a richiesta avvenuta con successo
        // serve a qualcosa averlo creato se poi le credenziali non sono valide?
        //String sessionId = UUID.randomUUID().toString(); //si utilizza per creare stringhe randomiche al posto del sessionID
        //FirstStepLoginRequest request = autenticationMappers.convertToDomain(loginRequest, sessionId); //SCOPO DI ESEMPIO
        //TODO DONE
        FirstStepLoginRequest request = autenticationMappers.convertToDomain(loginRequest);

        FirstStepLoginResponse response = autenticationService.firstStepLogin(request);

        Login200Response convertedResponse = autenticationMappers.convertFromDomain(response);
        return ResponseEntity.ok(convertedResponse);
    }

    @Override
    public ResponseEntity<VerifyOTP200Response> verifyOTP(VerifyOTPRequest verifyOTPRequest){
        SecondStepVerifyOtpRequest request = autenticationMappers.convertToDomain(verifyOTPRequest);
        SecondStepVerifyOtpResponse response = autenticationService.secondStepVerifyOtp(request);

        //impostiamo l'accessToken nell'header (bearer token)
        jwtServiceImpl.setAuthorizationHeader(response.getAccessToken());
        jwtServiceImpl.generateRefreshCookie(response.getRefreshToken());

        VerifyOTP200Response convertedResponse = autenticationMappers.convertFromDomain(response);
        return ResponseEntity.ok(convertedResponse);
    }

    @Override
    public ResponseEntity<ReSendOtp200Response> reSendOtp(ReSendOtpRequest reSendOtpRequest){
        ThridStepResendOtpRequest request = autenticationMappers.convertToDomain(reSendOtpRequest);
        ThirdStepResendOtpResponse response = autenticationService.thirdStepResendOtp(request);
        ReSendOtp200Response convertedResponse = autenticationMappers.convertFromDomain(response);
        return ResponseEntity.ok(convertedResponse);
    }

    @Override
    public ResponseEntity<VerifyToken200Response>  verifyToken(){
        FirstStepVerifyTokenResponse response = autenticationService.firstStepVerifyToken();
        VerifyToken200Response convertedResponse = autenticationMappers.convertFromDomain(response);
        return ResponseEntity.ok(convertedResponse);
    }

    @Override
    public ResponseEntity<RefreshToken200Response>  refreshToken(RefreshTokenRequest refreshTokenRequest){
        SecondStepGetAccessTokenByRefreshTokenRequest request = autenticationMappers.convertToDomain(refreshTokenRequest);
        SecondStepGetAccessTokenByRefreshTokenResponse response = autenticationService.secondStepGetNewAccessToken(request);

        //impostiamo l'accessToken nell'header (bearer token)
        jwtServiceImpl.setAuthorizationHeader(response.getAccessToken());

        RefreshToken200Response convertedResponse = autenticationMappers.convertFromDomain(response);
        return ResponseEntity.ok(convertedResponse);
    }

    @Override
    public ResponseEntity<Logout200Response> logout(){
        FourthStepLogoutResponse response = autenticationService.fourthStepLogout();

        ResponseCookie cleanRefreshCookie = jwtServiceImpl.getCleanJwtCookie();

        Logout200Response convertedResponse = autenticationMappers.convertFromDomain(response);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cleanRefreshCookie.toString())
                .header(HttpHeaders.AUTHORIZATION, "")
                .body(convertedResponse);
    }
}
