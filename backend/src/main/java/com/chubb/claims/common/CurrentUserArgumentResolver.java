package com.chubb.claims.common;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Lets controller methods declare a {@link CurrentUser} parameter, filled in by {@link IdentityInterceptor}. */
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(CurrentUser.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Object user = webRequest.getAttribute(IdentityInterceptor.CURRENT_USER_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (user == null) {
            throw new UnauthorizedException("No authenticated user for this request");
        }
        return user;
    }
}
