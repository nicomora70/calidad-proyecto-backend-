package co.ritech.security;

import java.time.Clock;
import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import co.ritech.config.RitechProperties;
import co.ritech.user.User;

/** Issues short-lived access tokens (HS256). Refresh tokens are opaque (see auth package). */
@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final RitechProperties.Security settings;
    private final Clock clock;

    public JwtService(JwtEncoder encoder, RitechProperties properties, Clock clock) {
        this.encoder = encoder;
        this.settings = properties.security();
        this.clock = clock;
    }

    public String issueAccessToken(User user) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(settings.jwtIssuer())
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(settings.accessTokenTtl()))
                .claim(CurrentUser.CLAIM_EMAIL, user.getEmail())
                .claim(CurrentUser.CLAIM_ROLE, user.getRole().name())
                .claim(CurrentUser.CLAIM_PASSWORD_CHANGE, user.isMustChangePassword())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long accessTokenTtlSeconds() {
        return settings.accessTokenTtl().toSeconds();
    }
}
