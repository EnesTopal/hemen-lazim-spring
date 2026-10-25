package com.tpl.hemen_lazim.services;

import com.tpl.hemen_lazim.errors.ApiException;
import com.tpl.hemen_lazim.errors.ErrorCode;
import com.tpl.hemen_lazim.model.ApiResponse;
import com.tpl.hemen_lazim.model.DTOs.CreateUserDTO;
import com.tpl.hemen_lazim.model.DTOs.UserDTO;
import com.tpl.hemen_lazim.model.enums.Role;
import com.tpl.hemen_lazim.security.JwtGenerate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServicesTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtGenerate jwtGenerate;

    @Mock
    private UserServices userServices;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokens;

    @InjectMocks
    private AuthServices authServices;

    static Stream<Arguments> invalidRegistrations() {
        return Stream.of(
                Arguments.of("ab", "ali@example.com", "abcdefg1", ErrorCode.USERNAME_INVALID),
                Arguments.of("   ", "ali@example.com", "abcdefg1", ErrorCode.USERNAME_INVALID),
                Arguments.of("a".repeat(51), "ali@example.com", "abcdefg1", ErrorCode.USERNAME_INVALID),
                Arguments.of("ali", "ali.example.com", "abcdefg1", ErrorCode.EMAIL_INVALID),
                Arguments.of("ali", null, "abcdefg1", ErrorCode.EMAIL_INVALID),
                Arguments.of("ali", "ali@example.com", "abcdefgh", ErrorCode.PASSWORD_INVALID),
                Arguments.of("ali", "ali@example.com", "12345678", ErrorCode.PASSWORD_INVALID),
                Arguments.of("ali", "ali@example.com", "abc1", ErrorCode.PASSWORD_INVALID),
                Arguments.of("ali", "ali@example.com", null, ErrorCode.PASSWORD_INVALID),
                // 74 bayt: BCrypt 72 bayttan uzununu reddediyor
                Arguments.of("ali", "ali@example.com", "a1" + "ş".repeat(36), ErrorCode.PASSWORD_TOO_LONG)
        );
    }

    @ParameterizedTest
    @MethodSource("invalidRegistrations")
    void invalidInputIsRejectedWithItsCode(String userName, String email, String password, ErrorCode expected) {
        ApiException e = assertThrows(ApiException.class, () -> authServices.register(dto(userName, email, password)));

        assertEquals(expected, e.getCode());
        assertEquals(HttpStatus.BAD_REQUEST, e.getCode().status());
        verifyNoInteractions(userServices, passwordEncoder);
    }

    @Test
    void validInputCreatesTheAccount() {
        when(passwordEncoder.encode("abcdefg1")).thenReturn("hash");
        when(userServices.createAccount(any()))
                .thenReturn(new UserDTO(UUID.randomUUID(), "ali", "ali@example.com", Role.USER, true));

        ResponseEntity<ApiResponse<UserDTO>> res = authServices.register(dto(" ali ", " Ali@Example.com ", "abcdefg1"));

        assertEquals(HttpStatus.CREATED, res.getStatusCode());
        ArgumentCaptor<CreateUserDTO> saved = ArgumentCaptor.forClass(CreateUserDTO.class);
        verify(userServices).createAccount(saved.capture());
        assertEquals("ali", saved.getValue().getUserName());
        assertEquals("ali@example.com", saved.getValue().getEmail());
        assertEquals("hash", saved.getValue().getUserPassword());
    }

    @Test
    void emailIsLowercasedTheSameWayUnderTurkishLocale() {
        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            when(passwordEncoder.encode(any())).thenReturn("hash");

            authServices.register(dto("ilker", "ILKER@EXAMPLE.COM", "abcdefg1"));

            verify(userServices).existsByEmail("ilker@example.com");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void takenUsernameAndEmailHaveTheirCodes() {
        when(userServices.existsByUserName("ali")).thenReturn(true);
        assertCode(ErrorCode.USERNAME_TAKEN, () -> authServices.register(dto("ali", "ali@example.com", "abcdefg1")));

        when(userServices.existsByUserName("veli")).thenReturn(false);
        when(userServices.existsByEmail("veli@example.com")).thenReturn(true);
        assertCode(ErrorCode.EMAIL_TAKEN, () -> authServices.register(dto("veli", "veli@example.com", "abcdefg1")));
    }

    @Test
    void failedLoginHasACodeForEachReason() {
        doThrow(new BadCredentialsException("bad")).when(authenticationManager).authenticate(any());
        assertCode(ErrorCode.INVALID_CREDENTIALS, () -> authServices.login(dto("ali", null, "yanlis123")));

        doThrow(new DisabledException("off")).when(authenticationManager).authenticate(any());
        assertCode(ErrorCode.ACCOUNT_DISABLED, () -> authServices.login(dto("ali", null, "abcdefg1")));
    }

    private static void assertCode(ErrorCode expected, Executable call) {
        ApiException e = assertThrows(ApiException.class, call);
        assertEquals(expected, e.getCode());
    }

    private static CreateUserDTO dto(String userName, String email, String password) {
        CreateUserDTO dto = new CreateUserDTO();
        dto.setUserName(userName);
        dto.setEmail(email);
        dto.setUserPassword(password);
        return dto;
    }
}
