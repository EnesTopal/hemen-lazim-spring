package com.tpl.hemen_lazim.services;

import com.tpl.hemen_lazim.model.ApiResponse;
import com.tpl.hemen_lazim.model.DTOs.CreateUserDTO;
import com.tpl.hemen_lazim.model.DTOs.UserDTO;
import com.tpl.hemen_lazim.model.enums.Role;
import com.tpl.hemen_lazim.security.JwtGenerate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
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
                Arguments.of("ab", "ali@example.com", "abcdefg1"),
                Arguments.of("   ", "ali@example.com", "abcdefg1"),
                Arguments.of("a".repeat(51), "ali@example.com", "abcdefg1"),
                Arguments.of("ali", "ali.example.com", "abcdefg1"),
                Arguments.of("ali", null, "abcdefg1"),
                Arguments.of("ali", "ali@example.com", "abcdefgh"),
                Arguments.of("ali", "ali@example.com", "12345678"),
                Arguments.of("ali", "ali@example.com", "abc1"),
                Arguments.of("ali", "ali@example.com", null),
                // 74 bayt: BCrypt 72 bayttan uzununu reddediyor
                Arguments.of("ali", "ali@example.com", "a1" + "ş".repeat(36))
        );
    }

    @ParameterizedTest
    @MethodSource("invalidRegistrations")
    void invalidInputIsRejectedWithoutCreatingAnAccount(String userName, String email, String password) {
        ResponseEntity<ApiResponse<UserDTO>> res = authServices.register(dto(userName, email, password));

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertNotNull(res.getBody().getMessage());
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
    void takenEmailKeepsTheTextTheAppLooksFor() {
        when(userServices.existsByEmail("ali@example.com")).thenReturn(true);

        ResponseEntity<ApiResponse<UserDTO>> res = authServices.register(dto("ali", "ali@example.com", "abcdefg1"));

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertEquals("Email already in use", res.getBody().getMessage());
    }

    private static CreateUserDTO dto(String userName, String email, String password) {
        CreateUserDTO dto = new CreateUserDTO();
        dto.setUserName(userName);
        dto.setEmail(email);
        dto.setUserPassword(password);
        return dto;
    }
}
