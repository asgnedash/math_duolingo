package com.example.duolingomathbot.controller;

import com.example.duolingomathbot.model.Task;
import com.example.duolingomathbot.model.User;
import com.example.duolingomathbot.service.UserTrainingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/training")
public class TrainingController {

    @Autowired
    private UserTrainingService userTrainingService;

    @PostMapping("/user")
    public User registerOrGetUser(@RequestParam Long telegramId, @RequestParam(required = false) String username) {
        return userTrainingService.getOrCreateUser(telegramId, username != null ? username : "User_" + telegramId);
    }

    @GetMapping("/next-task")
    public ResponseEntity<?> getNextTask(@RequestParam Long userId) {
        Optional<Task> task = userTrainingService.getNextTaskForUser(userId);
        if (task.isPresent()) {
            TaskDto taskDto = new TaskDto(task.get().getId(), task.get().getContent(), task.get().getTopic().getName());
            return ResponseEntity.ok(taskDto);
        } else {
            return ResponseEntity.ok("Нет доступных задач на данный момент.");
        }
    }

    @PostMapping("/answer")
    public ResponseEntity<String> processAnswer(@RequestParam Long userId,
                                                @RequestParam Long taskId,
                                                @RequestParam boolean isCorrect) {
        try {
            userTrainingService.processAnswer(userId, taskId, isCorrect);
            User user = userTrainingService.getOrCreateUser(userId, null);
            return ResponseEntity.ok("Ответ обработан. Текущий training_counter пользователя: " + user.getTrainingCounter());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    public static class TaskDto {
        private Long id;
        private String content;
        private String topicName;

        public TaskDto(Long id, String content, String topicName) {
            this.id = id;
            this.content = content;
            this.topicName = topicName;
        }

        public Long getId() { return id; }
        public String getContent() { return content; }
        public String getTopicName() { return topicName; }
    }
}