package com.chubb.claims.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Turns exceptions into RFC 7807 ProblemDetail responses.
 * <pre>
 * 400 validation errors (field list in "errors")     401 UnauthorizedException
 * 403 ForbiddenException                             404 NotFoundException
 * 409 InvalidStatusTransitionException, optimistic lock conflicts
 * 422 other BusinessRuleException                    500 anything unexpected (details logged, not returned)
 * </pre>
 * The base class already maps Spring MVC's own exceptions (malformed JSON, bad parameter types, ...) to 400 etc.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> Map.of("field", e.getField(), "message", Objects.toString(e.getDefaultMessage(), "is invalid")))
                .toList();
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "One or more fields are invalid: " + errors.stream().map(e -> e.get("field")).distinct().toList());
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(UnauthorizedException.class)
    ProblemDetail unauthorized(UnauthorizedException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "Unauthorized", ex.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    ProblemDetail forbidden(ForbiddenException ex) {
        return problem(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Not found", ex.getMessage());
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    ProblemDetail invalidTransition(InvalidStatusTransitionException ex) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Invalid status transition", ex.getMessage());
        problem.setProperty("claimNumber", ex.getClaimNumber());
        problem.setProperty("fromStatus", ex.getFromStatus());
        problem.setProperty("toStatus", ex.getToStatus());
        return problem;
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail concurrentUpdate(ObjectOptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "Concurrent update",
                "This claim was changed by someone else at the same time. Reload it and try again.");
    }

    @ExceptionHandler(BusinessRuleException.class)
    ProblemDetail businessRule(BusinessRuleException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Business rule violated", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", "Something went wrong. Please try again later.");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
