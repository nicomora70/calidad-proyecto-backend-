package co.ritech.config;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

@Configuration
@OpenAPIDefinition(
        info =
                @Info(
                        title = "RiTech API",
                        version = "1.0.0",
                        description = "Investment simulation backend. Authenticate with POST /api/v1/auth/login "
                                + "and send the access token as 'Authorization: Bearer <token>'."))
@SecurityScheme(name = "bearer", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {}
