package com.example.duolingomathbot.controller;

import com.example.duolingomathbot.model.Task;
import com.example.duolingomathbot.model.Topic;
import com.example.duolingomathbot.service.UserTrainingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private UserTrainingService userTrainingService;

    @GetMapping("/topics")
    public List<Topic> getTopics() {
        return userTrainingService.getAllTopics();
    }

    @PostMapping("/task")
    public ResponseEntity<?> addTask(@RequestParam Long topicId,
                                     @RequestParam String answer,
                                     @RequestParam("image") MultipartFile image) {
        try {
            String base64 = Base64.getEncoder().encodeToString(image.getBytes());
            Task task = userTrainingService.addTask(topicId, "DATA:" + base64, answer);
            return ResponseEntity.ok(task.getId());
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Ошибка загрузки файла");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Ошибка сохранения задачи");
        }
    }
}
