package com.example.duolingomathbot.config;

import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.List;

@Component
public class SrsConfig {
    public static final List<Integer> SRS_STAGES_INTERVALS = Arrays.asList(1, 3, 7, 14, 21, 30, 60);
    public static final int MIN_HARD_TASKS_FOR_TOPIC_COMPLETION = 5;
    public static final double HARD_TASK_DIFFICULTY_THRESHOLD_FACTOR = 0.8;
    public static final double MEDIUM_TASK_DIFFICULTY_THRESHOLD_FACTOR = 0.5;
}