package com.campus.diet.controller;

import com.campus.diet.common.ApiResponse;
import com.campus.diet.security.LoginUserHolder;
import com.campus.diet.service.FeedbackService;
import lombok.Data;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api")
public class FeedbackApiController {

    private final FeedbackService feedbackService;

    public FeedbackApiController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping("/feedback")
    public ApiResponse<Map<String, Object>> feedback(@Valid @RequestBody FeedbackBody body) {
        var u = LoginUserHolder.get();
        feedbackService.submit(u == null ? null : u.getUserId(), body.getContent(), body.getSource());
        Map<String, Object> m = new HashMap<>();
        m.put("ok", true);
        return ApiResponse.ok(m);
    }

    @Data
    public static class FeedbackBody {
        @NotBlank
        private String content;
        private String source;
    }
}
