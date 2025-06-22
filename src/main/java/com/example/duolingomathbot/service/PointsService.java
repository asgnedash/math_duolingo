package com.example.duolingomathbot.service;

import com.example.duolingomathbot.bot.MathSrTelegramBot;
import com.example.duolingomathbot.model.TopicType;
import com.example.duolingomathbot.model.User;
import com.example.duolingomathbot.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;

@Service
public class PointsService {
    private static final Logger logger = LoggerFactory.getLogger(PointsService.class);

    private final UserRepository userRepository;
    private final MathSrTelegramBot bot;

    @Autowired
    public PointsService(UserRepository userRepository, MathSrTelegramBot bot) {
        this.userRepository = userRepository;
        this.bot = bot;
    }

    private String examName(TopicType exam) {
        if (exam == TopicType.EGE) return "ЕГЭ";
        if (exam == TopicType.OGE) return "ОГЭ";
        return "экзамен";
    }

    private void send(long chatId, String text) {
        try {
            bot.execute(new SendMessage(String.valueOf(chatId), text));
        } catch (TelegramApiException e) {
            logger.error("Error sending points message: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "0 0 17 ? * MON", zone = "Europe/Moscow")
    @Transactional
    public void resetWeekly() {
        List<User> users = userRepository.findAll();
        for (User u : users) {
            int pts = u.getWeeklyPoints();
            String msg;
            String ex = examName(u.getExam());
            if (pts > 40) {
                msg = "Хорошо поработали, ты набрал за неделю " + pts + " очков! Продолжай в том же духе, практикуйся и сдашь " + ex + " на высокий балл!";
            } else {
                msg = "За неделю набрано " + pts + " баллов. Нужно поднажать, чтобы хорошо сдать " + ex + "!";
            }
            send(u.getTelegramId(), msg);
            u.setWeeklyPoints(0);
        }
        userRepository.saveAll(users);
    }

    @Scheduled(cron = "0 0 17 1 * ?", zone = "Europe/Moscow")
    @Transactional
    public void resetMonthly() {
        List<User> users = userRepository.findAll();
        for (User u : users) {
            int pts = u.getMonthlyPoints();
            String msg;
            String ex = examName(u.getExam());
            if (pts > 180) {
                msg = "Хорошо поработали, ты набрал за месяц " + pts + " очков! Продолжай в том же духе, практикуйся и сдашь " + ex + " на высокий балл!";
            } else {
                msg = "За месяц набрано " + pts + " баллов. Нужно поднажать, чтобы хорошо сдать " + ex + "!";
            }
            send(u.getTelegramId(), msg);
            u.setMonthlyPoints(0);
        }
        userRepository.saveAll(users);
    }
}
