package com.example.duolingomathbot.service;

import com.example.duolingomathbot.bot.MathSrTelegramBot;
import com.example.duolingomathbot.config.MarathonConfig;
import com.example.duolingomathbot.model.TopicType;
import com.example.duolingomathbot.model.User;
import com.example.duolingomathbot.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

/**
 * Service for sending scheduled messages to users based on Google Sheets data.
 * Unlike {@link MarathonService}, messages are sent to all users regardless of
 * the {@code marathon} flag. Sheet names containing "ОГЭ" or "ЕГЭ" limit
 * delivery to users with the corresponding exam set; otherwise messages are sent
 * to everyone.
 */
@Service
public class BroadcastService {

    private static final Logger logger = LoggerFactory.getLogger(BroadcastService.class);
    private final MarathonConfig config;
    private final UserRepository userRepository;
    private final MathSrTelegramBot bot;
    private final RestTemplate restTemplate = new RestTemplate();

    private final List<BroadcastEntry> cache = new ArrayList<>();

    @Autowired
    public BroadcastService(MarathonConfig config, UserRepository userRepository, MathSrTelegramBot bot) {
        this.config = config;
        this.userRepository = userRepository;
        this.bot = bot;
    }

    private static class BroadcastEntry {
        LocalDate date;
        LocalTime time;
        String text;
        List<String> fileIds;
        TopicType exam; // null means send to all
    }

    private TopicType examFromSheet(String sheet) {
        String lower = sheet.toLowerCase();
        if (lower.contains("огэ")) return TopicType.OGE;
        if (lower.contains("еге")) return TopicType.EGE;
        return null;
    }

    private List<String> fetchSheetNames() {
        String url = "https://sheets.googleapis.com/v4/spreadsheets/" +
                config.getSpreadsheetId() + "?fields=sheets.properties.title&key=" + config.getApiKey();
        ResponseEntity<Map> resp;
        try {
            resp = restTemplate.getForEntity(url, Map.class);
        } catch (RestClientException ex) {
            logger.error("Failed to load sheet names from Google Sheets: {}", ex.getMessage());
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<>();
        Object sheetsObj = resp.getBody().get("sheets");
        if (sheetsObj instanceof List<?> sheets) {
            for (Object sheetObj : sheets) {
                if (!(sheetObj instanceof Map<?, ?> sheetMap)) continue;
                Object props = sheetMap.get("properties");
                if (!(props instanceof Map<?, ?> p)) continue;
                Object title = p.get("title");
                if (title != null) names.add(title.toString());
            }
        }
        return names;
    }

    private List<BroadcastEntry> loadEntries(String sheet) {
        String range = sheet + "!A:M";
        String url = "https://sheets.googleapis.com/v4/spreadsheets/" +
                config.getSpreadsheetId() + "/values/" + range + "?key=" + config.getApiKey();

        ResponseEntity<Map> resp;
        try {
            resp = restTemplate.getForEntity(url, Map.class);
        } catch (RestClientException ex) {
            logger.error("Failed to load broadcast data from Google Sheets: {}", ex.getMessage());
            return Collections.emptyList();
        }

        TopicType exam = examFromSheet(sheet);
        List<BroadcastEntry> result = new ArrayList<>();
        Object valuesObj = resp.getBody().get("values");
        if (valuesObj instanceof List<?> values) {
            for (Object rowObj : values) {
                if (!(rowObj instanceof List<?> row) || row.size() < 3) continue;
                if ("Date".equals(row.get(0))) continue;
                BroadcastEntry e = new BroadcastEntry();
                e.date = LocalDate.parse(row.get(0).toString());
                e.time = LocalTime.parse(row.get(1).toString());
                e.text = row.get(2).toString();
                e.exam = exam;
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
        cache.clear();
        for (String name : fetchSheetNames()) {
            cache.addAll(loadEntries(name));
        }
    }

    @Scheduled(fixedDelay = 60000)
    public void checkAndSend() {
        if (cache.isEmpty()) {
            refresh();
        }
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Europe/Moscow")).withSecond(0).withNano(0);
        Iterator<BroadcastEntry> it = cache.iterator();
        while (it.hasNext()) {
            BroadcastEntry e = it.next();
            LocalDateTime dt = LocalDateTime.of(e.date, e.time);
            if (dt.equals(now)) {
                sendToUsers(e);
                it.remove();
            }
        }
    }

    private void sendToUsers(BroadcastEntry entry) {
        List<User> users = userRepository.findAll();
        for (User u : users) {
            if (entry.exam == null || entry.exam.equals(u.getExam())) {
                sendEntry(u.getTelegramId(), entry);
            }
        }
    }

    private void sendEntry(long chatId, BroadcastEntry entry) {
        try {
            bot.execute(new org.telegram.telegrambots.meta.api.methods.send.SendMessage(String.valueOf(chatId), entry.text));
            for (String fileId : entry.fileIds) {
                org.telegram.telegrambots.meta.api.methods.send.SendPhoto photo = new org.telegram.telegrambots.meta.api.methods.send.SendPhoto();
                photo.setChatId(String.valueOf(chatId));
                photo.setPhoto(new org.telegram.telegrambots.meta.api.objects.InputFile(fileId));
                bot.execute(photo);
            }
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            logger.error("Error sending broadcast message: {}", e.getMessage());
        }
    }
}
