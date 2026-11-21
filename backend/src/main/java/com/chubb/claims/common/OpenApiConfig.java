package com.chubb.claims.common;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI / Swagger UI setup. The fake-auth headers are modelled as two API-key security schemes, so Swagger UI
 * shows an "Authorize" button: enter X-User-Id and X-User-Role once and they're sent with every request.
 */
@Configuration
public class OpenApiConfig {

    static {
        // CurrentUser is injected by our argument resolver, not sent by the client: keep it out of the spec.
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(CurrentUser.class);
    }

    @Bean
    public OpenAPI claimsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Chubb APAC Claims API")
                        .version("v1")
                        .description("""
                                Motor and property claims across SG, MY, HK, TH, ID, AU.
                                Identity is faked with the X-User-Id and X-User-Role headers: click "Authorize" \
                                (claimants C001-C004 with CLAIMANT, staff S001-S004 with STAFF)."""))
                .components(new Components()
                        .addSecuritySchemes(IdentityInterceptor.USER_ID_HEADER, headerScheme(
                                IdentityInterceptor.USER_ID_HEADER,
                                "Seeded user id: C001-C004 (claimants) or S001-S004 (staff)"))
                        .addSecuritySchemes(IdentityInterceptor.USER_ROLE_HEADER, headerScheme(
                                IdentityInterceptor.USER_ROLE_HEADER,
                                "CLAIMANT or STAFF; must match the user's role")))
                // One requirement listing both schemes = both headers are sent together.
                .addSecurityItem(new SecurityRequirement()
                        .addList(IdentityInterceptor.USER_ID_HEADER)
                        .addList(IdentityInterceptor.USER_ROLE_HEADER))
                .tags(List.of(
                        new Tag().name("Claimant").description("Policy holders: submit and track claims, answer questions"),
                        new Tag().name("Staff").description("Claims officers: work queue, assessment and decisions")));
    }

    private static SecurityScheme headerScheme(String headerName, String description) {
        return new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name(headerName)
                .description(description);
    }
}
