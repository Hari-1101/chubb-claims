package com.chubb.claims.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** OpenAPI / Swagger UI setup: API info, the two tags, and the fake-auth headers on every operation. */
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
                                Identity is faked with the X-User-Id and X-User-Role headers \
                                (claimants C001-C004, staff S001-S004)."""))
                .tags(List.of(
                        new Tag().name("Claimant").description("Policy holders: submit and track claims, answer questions"),
                        new Tag().name("Staff").description("Claims officers: work queue, assessment and decisions")));
    }

    @Bean
    public OperationCustomizer identityHeaders() {
        return (operation, handlerMethod) -> operation
                .addParametersItem(new HeaderParameter()
                        .name(IdentityInterceptor.USER_ID_HEADER)
                        .required(true)
                        .description("Seeded user id: C001-C004 (claimants) or S001-S004 (staff)")
                        .schema(new StringSchema().example("C001")))
                .addParametersItem(new HeaderParameter()
                        .name(IdentityInterceptor.USER_ROLE_HEADER)
                        .required(true)
                        .description("Must match the user's role")
                        .schema(new StringSchema()._enum(List.of(UserRole.CLAIMANT.name(), UserRole.STAFF.name()))));
    }
}
