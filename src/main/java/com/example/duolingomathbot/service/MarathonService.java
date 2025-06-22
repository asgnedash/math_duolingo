package com.example.duolingomathbot.service;

import com.example.duolingomathbot.bot.MathSrTelegramBot;
import com.example.duolingomathbot.config.MarathonConfig;
import com.example.duolingomathbot.model.TopicType;
import com.example.duolingomathbot.model.User;
import com.example.duolingomathbot.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;

@Service
public class MarathonService {

    private static final Logger logger = LoggerFactory.getLogger(MarathonService.class);
    private final MarathonConfig config;
    private final UserRepository userRepository;
    private final MathSrTelegramBot bot;
    private final RestTemplate restTemplate = new RestTemplate();

    private final Map<TopicType, List<MarathonEntry>> cache = new EnumMap<>(TopicType.class);

    @Autowired
    public MarathonService(MarathonConfig config, UserRepository userRepository, MathSrTelegramBot bot) {
        this.config = config;
        this.userRepository = userRepository;
        this.bot = bot;
    }

    private static class MarathonEntry {
        LocalDate date;
        LocalTime time;
        String text;
        List<String> fileIds;
    }

    private List<MarathonEntry> loadEntries(String sheet) {
        String range = sheet + "!A:M";
        String url = "https://sheets.googleapis.com/v4/spreadsheets/" +
                config.getSpreadsheetId() + "/values/" + range + "?key=" + config.getApiKey();

        ResponseEntity<Map> resp;
        try {
            resp = restTemplate.getForEntity(url, Map.class);
        } catch (RestClientException ex) {
            logger.error("Failed to load marathon data from Google Sheets: {}", ex.getMessage());
            return java.util.Collections.emptyList();
        }

        List<MarathonEntry> result = new ArrayList<>();
        Object valuesObj = resp.getBody().get("values");
        if (valuesObj instanceof List<?> values) {
            for (Object rowObj : values) {
                if (!(rowObj instanceof List<?> row) || row.size() < 3) continue;
                if ("Date".equals(row.get(0))) continue; // skip header
                MarathonEntry e = new MarathonEntry();
                e.date = LocalDate.parse(row.get(0).toString());
                e.time = LocalTime.parse(row.get(1).toString());
                e.text = row.get(2).toString();
                e.fileIds = new ArrayList<>();
                for (int i = 3; i < row.size(); i++) {
                    String val = row.get(i).toString();
                    if (!val.isBlank()) e.fileIds.add(val);
                }
                result.add(e);
            }
        }
        return result;
    }

    private void refresh() {
        cache.put(TopicType.OGE, loadEntries("OGE"));
        cache.put(TopicType.EGE, loadEntries("EGE"));
    }

    @Scheduled(fixedDelay = 60000)
    public void checkAndSend() {
        if (cache.isEmpty()) {
            refresh();
        }
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Europe/Moscow")).withSecond(0).withNano(0);
        for (TopicType type : List.of(TopicType.OGE, TopicType.EGE)) {
            List<MarathonEntry> entries = cache.getOrDefault(type, Collections.emptyList());
            Iterator<MarathonEntry> it = entries.iterator();
            while (it.hasNext()) {
                MarathonEntry e = it.next();
                LocalDateTime dt = LocalDateTime.of(e.date, e.time);
                if (dt.equals(now)) {
                    sendToAll(type, e);
                    it.remove();
                }
            }
        }
    }

    private void sendToAll(TopicType type, MarathonEntry entry) {
        List<User> users = userRepository.findByMarathonTrue();
        for (User u : users) {
            if (type.equals(u.getExam())) {
                sendEntry(u.getTelegramId(), entry);
            }
        }
    }

    private void sendEntry(long chatId, MarathonEntry entry) {
        try {
            bot.execute(new org.telegram.telegrambots.meta.api.methods.send.SendMessage(String.valueOf(chatId), entry.text));
            for (String fileId : entry.fileIds) {
                org.telegram.telegrambots.meta.api.methods.send.SendPhoto photo = new org.telegram.telegrambots.meta.api.methods.send.SendPhoto();
                photo.setChatId(String.valueOf(chatId));
                photo.setPhoto(new org.telegram.telegrambots.meta.api.objects.InputFile(fileId));
                bot.execute(photo);
            }
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            logger.error("Error sending marathon message: {}", e.getMessage());
        }
    }
}
