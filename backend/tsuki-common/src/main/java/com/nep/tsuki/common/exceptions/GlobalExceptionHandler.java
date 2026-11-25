package com.nep.tsuki.common.exceptions;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.Errors;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.nep.tsuki.common.api.ApiCode;
import com.nep.tsuki.common.api.ApiResponse;
import com.nep.tsuki.common.api.FieldError;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String TYPE_MISMATCH_MESSAGE = "参数类型不正确";
    private static final String DEFAULT_VALIDATION_MESSAGE = "参数不合法";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        ApiCode code = exception.getApiCode();
        String message = Objects.requireNonNullElse(exception.getMessage(), code.message());
        return ResponseEntity.status(code.httpStatus()).body(ApiResponse.error(code, message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(
            ConstraintViolationException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
            String field = extractField(violation);
            errors.putIfAbsent(field, Objects.requireNonNullElse(violation.getMessage(), DEFAULT_VALIDATION_MESSAGE));
        }
        return ResponseEntity.status(ApiCode.VALIDATION_FAILED.httpStatus()).body(validationError(errors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
        log.error("请求处理失败", exception);
        ApiCode code = ApiCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(code.httpStatus()).body(ApiResponse.error(code));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ApiResponse<Void> response;
        if (status.is5xxServerError()) {
            log.error("请求处理失败", exception);
            response = ApiResponse.error(ApiCode.INTERNAL_SERVER_ERROR);
        } else {
            response = clientError(exception, status);
        }
        return super.handleExceptionInternal(exception, response, headers, status, request);
    }

    private static ApiResponse<Void> clientError(Exception exception, HttpStatusCode status) {
        if (exception instanceof MethodArgumentNotValidException invalidArgument) {
            Map<String, String> errors = new LinkedHashMap<>();
            addBindingErrors(errors, invalidArgument.getBindingResult());
            return validationError(errors);
        }
        if (exception instanceof HandlerMethodValidationException invalidMethod) {
            Map<String, String> errors = new LinkedHashMap<>();
            for (ParameterValidationResult result : invalidMethod.getParameterValidationResults()) {
                if (result instanceof ParameterErrors bindingErrors) {
                    addBindingErrors(errors, bindingErrors);
                } else if (!result.getResolvableErrors().isEmpty()) {
                    String field = result.getMethodParameter().getParameterName();
                    String message = result.getResolvableErrors().getFirst().getDefaultMessage();
                    errors.putIfAbsent(field, Objects.requireNonNullElse(message, DEFAULT_VALIDATION_MESSAGE));
                }
            }
            if (!invalidMethod.getCrossParameterValidationResults().isEmpty()) {
                String message = invalidMethod.getCrossParameterValidationResults().getFirst().getDefaultMessage();
                errors.putIfAbsent(null, Objects.requireNonNullElse(message, DEFAULT_VALIDATION_MESSAGE));
            }
            return validationError(errors);
        }
        if (exception instanceof MethodArgumentTypeMismatchException mismatch) {
            return validationError(Map.of(mismatch.getName(), TYPE_MISMATCH_MESSAGE));
        }
        return ApiResponse.error(switch (status.value()) {
            case 400 -> ApiCode.BAD_REQUEST;
            case 404 -> ApiCode.NOT_FOUND;
            case 405 -> ApiCode.METHOD_NOT_ALLOWED;
            case 415 -> ApiCode.UNSUPPORTED_MEDIA_TYPE;
            default -> ApiCode.REQUEST_FAILED;
        });
    }

    private static void addBindingErrors(Map<String, String> errors, Errors bindingErrors) {
        for (ObjectError error : bindingErrors.getAllErrors()) {
            String field = null;
            String message = error.getDefaultMessage();
            if (error instanceof org.springframework.validation.FieldError fieldError) {
                field = fieldError.getField();
                if (fieldError.isBindingFailure()) {
                    message = TYPE_MISMATCH_MESSAGE;
                }
            }
            errors.putIfAbsent(field, Objects.requireNonNullElse(message, DEFAULT_VALIDATION_MESSAGE));
        }
    }

    private static String extractField(ConstraintViolation<?> violation) {
        String parameterName = null;
        StringBuilder propertyPath = new StringBuilder();
        ElementKind lastKind = null;
        for (Path.Node node : violation.getPropertyPath()) {
            lastKind = node.getKind();
            if (lastKind == ElementKind.PARAMETER) {
                parameterName = node.getName();
            } else if (lastKind == ElementKind.PROPERTY) {
                if (!propertyPath.isEmpty()) {
                    propertyPath.append('.');
                }
                propertyPath.append(node.getName());
                if (node.isInIterable()) {
                    if (node.getIndex() != null) {
                        propertyPath.append('[').append(node.getIndex()).append(']');
                    } else if (node.getKey() != null) {
                        propertyPath.append('[').append(node.getKey()).append(']');
                    }
                }
            }
        }
        if (lastKind == ElementKind.CROSS_PARAMETER || lastKind == ElementKind.BEAN) {
            return null;
        }
        return !propertyPath.isEmpty() ? propertyPath.toString() : parameterName;
    }

    private static ApiResponse<Void> validationError(Map<String, String> errors) {
        ApiCode code = ApiCode.VALIDATION_FAILED;
        List<FieldError> fieldErrors = errors.entrySet().stream()
                .map(entry -> new FieldError(entry.getKey(), entry.getValue()))
                .toList();
        return new ApiResponse<>(null, code.code(), code.message(), fieldErrors);
    }
}
