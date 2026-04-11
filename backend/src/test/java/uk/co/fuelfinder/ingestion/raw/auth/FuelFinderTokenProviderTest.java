package uk.co.fuelfinder.ingestion.raw.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.co.fuelfinder.ingestion.exception.FuelFinderIntegrationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FuelFinderTokenProviderTest {

    @Mock
    private OAuthTokenClient oAuthTokenClient;

    @InjectMocks
    private FuelFinderTokenProvider tokenProvider;

    @Test
    void getAccessTokenCachesValidToken() {
        when(oAuthTokenClient.generateAccessToken()).thenReturn(tokenResponse("token-1", 300));

        String first = tokenProvider.getAccessToken();
        String second = tokenProvider.getAccessToken();

        assertThat(first).isEqualTo("token-1");
        assertThat(second).isEqualTo("token-1");
        verify(oAuthTokenClient, times(1)).generateAccessToken();
    }

    @Test
    void getAccessTokenRefreshesExpiredToken() {
        when(oAuthTokenClient.generateAccessToken())
                .thenReturn(tokenResponse("token-1", 1))
                .thenReturn(tokenResponse("token-2", 300));

        String first = tokenProvider.getAccessToken();
        String second = tokenProvider.getAccessToken();

        assertThat(first).isEqualTo("token-1");
        assertThat(second).isEqualTo("token-2");
        verify(oAuthTokenClient, times(2)).generateAccessToken();
    }

    @Test
    void getAccessTokenPropagatesIntegrationException() {
        when(oAuthTokenClient.generateAccessToken())
                .thenThrow(new FuelFinderIntegrationException("token endpoint down"));

        assertThatThrownBy(() -> tokenProvider.getAccessToken())
                .isInstanceOf(FuelFinderIntegrationException.class)
                .hasMessage("token endpoint down");
    }

    @Test
    void getAccessTokenRefreshesTokenInsideExpirySafetyWindow() {
        when(oAuthTokenClient.generateAccessToken())
                .thenReturn(tokenResponse("token-1", 29))
                .thenReturn(tokenResponse("token-2", 300));

        String first = tokenProvider.getAccessToken();
        String second = tokenProvider.getAccessToken();

        assertThat(first).isEqualTo("token-1");
        assertThat(second).isEqualTo("token-2");
        verify(oAuthTokenClient, times(2)).generateAccessToken();
    }

    private static TokenResponse tokenResponse(String accessToken, long expiresInSeconds) {
        return new TokenResponse(
                true,
                new TokenResponse.TokenData(accessToken, "Bearer", expiresInSeconds, "refresh"),
                "ok"
        );
    }
}
