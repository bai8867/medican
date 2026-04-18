package com.campus.diet.common;

import com.campus.diet.config.RequestObservabilityInterceptor;
import com.campus.diet.service.RuntimeMetricService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final RuntimeMetricService runtimeMetricService;

    public GlobalExceptionHandler(RuntimeMetricService runtimeMetricService) {
        this.runtimeMetricService = runtimeMetricService;
    }

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBiz(BizException e, HttpServletRequest request) {
        markBizCode(request, e.getCode());
        runtimeMetricService.increment("error.category.biz");
        runtimeMetricService.increment("error.category.biz." + e.getCode());
        return ResponseEntity.status(resolveBizStatus(e.getCode()))
                .body(ApiResponse.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArg(IllegalArgumentException e, HttpServletRequest request) {
        markBizCode(request, 400);
        runtimeMetricService.increment("error.category.illegal_arg");
        return ResponseEntity.badRequest().body(ApiResponse.fail(400, e.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ApiResponse<Void>> handleValid(Exception e, HttpServletRequest request) {
        String msg = "参数错误";
        if (e instanceof MethodArgumentNotValidException) {
            var ex = (MethodArgumentNotValidException) e;
            if (ex.getBindingResult().getFieldError() != null) {
                msg = ex.getBindingResult().getFieldError().getDefaultMessage();
            }
        } else if (e instanceof BindException) {
            var ex = (BindException) e;
            if (ex.getBindingResult().getFieldError() != null) {
                msg = ex.getBindingResult().getFieldError().getDefaultMessage();
            }
        }
        markBizCode(request, 400);
        runtimeMetricService.increment("error.category.validation");
        return ResponseEntity.badRequest().body(ApiResponse.fail(400, msg));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadJson(HttpServletRequest request) {
        markBizCode(request, 400);
        runtimeMetricService.increment("error.category.bad_json");
        return ResponseEntity.badRequest().body(ApiResponse.fail(400, "请求体格式错误"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleOther(Exception e, HttpServletRequest request) {
        markBizCode(request, 500);
        runtimeMetricService.increment("error.category.unhandled");
        log.error("Unhandled exception on path={}", request.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(500, "服务器内部错误"));
    }

    private static void markBizCode(HttpServletRequest request, int bizCode) {
        if (request != null) {
            request.setAttribute(RequestObservabilityInterceptor.ATTR_BIZ_CODE, bizCode);
        }
    }

    private static HttpStatus resolveBizStatus(int code) {
        if (code == 401) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (code == 403 || code == ErrorCodes.ACCOUNT_DISABLED) {
            return HttpStatus.FORBIDDEN;
        }
        if (code == 404) {
            return HttpStatus.NOT_FOUND;
        }
        if (code == 409) {
            return HttpStatus.CONFLICT;
        }
        if (code >= 500) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return HttpStatus.BAD_REQUEST;
    }
}
