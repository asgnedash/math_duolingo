package com.example.duolingomathbot.service;

import com.example.duolingomathbot.bot.MathSrTelegramBot;
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

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class TrainingNotificationService {
    private static final Logger logger = LoggerFactory.getLogger(TrainingNotificationService.class);
    private final UserRepository userRepository;
    private final MathSrTelegramBot bot;

    @Autowired
    public TrainingNotificationService(UserRepository userRepository, MathSrTelegramBot bot) {
        this.userRepository = userRepository;
        this.bot = bot;
    }

    private String dayWord(int n) {
        int nAbs = Math.abs(n);
        if (nAbs % 10 == 1 && nAbs % 100 != 11) return "день";
        if (nAbs % 10 >= 2 && nAbs % 10 <= 4 && (nAbs % 100 < 10 || nAbs % 100 >= 20)) return "дня";
        return "дней";
    }

    private void send(long chatId, String text) {
        try {
            bot.execute(new SendMessage(String.valueOf(chatId), text));
        } catch (TelegramApiException e) {
            logger.error("Error sending training notification: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "0 0 19 * * *", zone = "Europe/Moscow")
    @Transactional
    public void dailyCheck() {
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Moscow"));
        List<User> users = userRepository.findAll();
        for (User u : users) {
            if (!u.isTrainNotification()) continue;
            LocalDate last = u.getLastTrainingDate();
            long days = last == null ? Long.MAX_VALUE : ChronoUnit.DAYS.between(last, today);
            if (days == 0) {
                continue; // already trained today
            }
            if (days == 1 && u.getStreak() > 0) {
                String text = "Ты " + u.getStreak() + " " + dayWord(u.getStreak()) +
                        " подряд тренируешься, это очень здорово! Не забывай пройти тренировку и сегодня, это не займет много времени!" +
                        "\n\n/train - начать тренировку\n/settings - отключить уведомления о тренировках";
                send(u.getTelegramId(), text);
            } else {
                if (u.getStreak() != 0) {
                    u.setStreak(0);
                }
                if (days >= 3) {
                    if (days == 3 || days == 13 || (days > 13 && (days - 13) % 14 == 0)) {
                        String text = "Прошло " + days + " " + dayWord((int) days) + " без тренировок. Возвращайтесь к занятиям!" +
                                "\n\n/train - начать тренировку\n/settings - отключить уведомления о тренировках";
                        send(u.getTelegramId(), text);
                    }
                }
            }
        }
        userRepository.saveAll(users);
    }
}
