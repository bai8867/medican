package com.campus.diet.service;

import com.campus.diet.entity.Feedback;
import com.campus.diet.mapper.FeedbackMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class FeedbackService {

    private final FeedbackMapper feedbackMapper;

    public FeedbackService(FeedbackMapper feedbackMapper) {
        this.feedbackMapper = feedbackMapper;
    }

    public void submit(Long userId, String content, String source) {
        Feedback f = new Feedback();
        f.setUserId(userId);
        f.setContent(content);
        f.setSource(source == null || source.isBlank() ? "campus_app" : source);
        f.setCreatedAt(LocalDateTime.now());
        feedbackMapper.insert(f);
    }
}
