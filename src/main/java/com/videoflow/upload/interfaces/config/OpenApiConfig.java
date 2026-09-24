package com.videoflow.upload.interfaces.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata. The temporary user identity header is documented so the
 * API can be exercised from Swagger before JWT is introduced.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI uploadApiOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Video Upload API")
                        .version("v1")
                        .description("Direct-to-storage multipart upload orchestration for large media files."))
                .addSecurityItem(new SecurityRequirement().addList("UserIdentity"))
                .components(new Components().addSecuritySchemes("UserIdentity",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-User-Id")
                                .description("Temporary user identity header (JWT will replace it)")));
    }
}