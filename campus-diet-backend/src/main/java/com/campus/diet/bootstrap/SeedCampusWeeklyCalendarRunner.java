package com.campus.diet.bootstrap;

import com.campus.diet.service.CampusWeeklyCalendarService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(40)
public class SeedCampusWeeklyCalendarRunner implements ApplicationRunner {

    @Value("${campus.diet.seed-weekly-calendar:true}")
    private boolean seedWeeklyCalendar;

    private final CampusWeeklyCalendarService campusWeeklyCalendarService;

    public SeedCampusWeeklyCalendarRunner(CampusWeeklyCalendarService campusWeeklyCalendarService) {
        this.campusWeeklyCalendarService = campusWeeklyCalendarService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            campusWeeklyCalendarService.seedCurrentWeekIfEmpty(seedWeeklyCalendar);
        } catch (Exception ignored) {
            // 表未就绪或 JSON 异常时不阻塞启动
        }
    }
}
