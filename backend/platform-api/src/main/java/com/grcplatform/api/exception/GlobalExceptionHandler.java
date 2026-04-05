package com.grcplatform.api.exception;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;
import com.grcplatform.core.exception.RecordNotFoundException;
import com.grcplatform.core.exception.ValidationException;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;

/**
 * Maps domain exceptions to GraphQL errors. Each exception type maps to one HTTP/GraphQL error
 * code. Logged once here — never re-logged in service code (see java-coding-standards §3).
 */
@Component
public class GlobalExceptionHandler extends DataFetcherExceptionResolverAdapter {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        return switch (ex) {
            case RecordNotFoundException e -> {
                log.debug("Record not found: {}", e.getMessage());
                yield GraphqlErrorBuilder.newError(env).errorType(ErrorType.NOT_FOUND)
                        .message(e.getMessage()).build();
            }
            case ValidationException e -> {
                log.debug("Validation failed: {}", e.getMessage());
                yield GraphqlErrorBuilder.newError(env).errorType(ErrorType.BAD_REQUEST)
                        .message(e.getMessage()).extensions(Map.of("errors", e.getErrors()))
                        .build();
            }
            default -> {
                log.error("Unhandled exception in GraphQL resolver", ex);
                yield GraphqlErrorBuilder.newError(env).errorType(ErrorType.INTERNAL_ERROR)
                        .message("An internal error occurred. Please contact support.").build();
            }
        };
    }
}
