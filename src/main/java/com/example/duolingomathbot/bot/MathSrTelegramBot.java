package com.example.duolingomathbot.bot;

import com.example.duolingomathbot.model.Task;
import com.example.duolingomathbot.model.User;
import com.example.duolingomathbot.service.UserTrainingService;
import com.example.duolingomathbot.bot.BotConfig;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MathSrTelegramBot extends TelegramLongPollingBot {

    private static final Logger logger = LoggerFactory.getLogger(MathSrTelegramBot.class);

    private final BotConfig botConfig;
    private final UserTrainingService userTrainingService;

    // Храним ID текущей задачи для каждого пользователя (internalUserId -> taskId)
    private final ConcurrentHashMap<Long, Long> userCurrentTaskIdMap = new ConcurrentHashMap<>();
    // Храним внутренний ID пользователя (telegramUserId -> internalUserId)
    // Это для оптимизации, чтобы не дергать БД каждый раз за internalUserId
    private final ConcurrentHashMap<Long, Long> telegramToInternalUserIdMap = new ConcurrentHashMap<>();

    private static final long ADMIN_CHAT_ID = 262398881L;

    private enum AddTaskStep {
        WAITING_FOR_PHOTO,
        WAITING_FOR_ANSWER,
        WAITING_FOR_TOPIC,
        WAITING_FOR_NEW_TOPIC_NAME
    }

    private static class PendingTaskData {
        AddTaskStep step;
        String fileId;
        String answer;
    }

    private final ConcurrentHashMap<Long, PendingTaskData> pendingTasks = new ConcurrentHashMap<>();


    public MathSrTelegramBot(BotConfig botConfig, UserTrainingService userTrainingService) {
        super(botConfig.getBotToken());
        this.botConfig = botConfig;
        this.userTrainingService = userTrainingService;
        this.adminChatId = botConfig.getAdminChatId();
    }

    @Override
    public String getBotUsername() {
        return botConfig.getBotUsername();
    }

    @PostConstruct
    public void init() {
        logger.info("Bot {} initialized. Token: {}",
                getBotUsername(),
                (getBotToken() != null && !getBotToken().isEmpty() && !"YOUR_VERY_LONG_TELEGRAM_BOT_TOKEN".equals(getBotToken())) ? "SET" : "NOT SET - PLEASE CONFIGURE!");
        if (getBotToken() == null || getBotToken().isEmpty() || "YOUR_VERY_LONG_TELEGRAM_BOT_TOKEN".equals(getBotToken())) {
            logger.error("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            logger.error("!!! FATAL: Telegram Bot Token is NOT configured in application.properties !!!");
            logger.error("!!! Bot will not work. Please set 'telegram.bot.token'                  !!!");
            logger.error("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        }
        // Регистрация команд бота
        try {
            List<BotCommand> commands = new ArrayList<>();
            commands.add(new BotCommand("/start", "Запуск бота"));
            commands.add(new BotCommand("/train", "Следующая задача"));
            commands.add(new BotCommand("/help", "Помощь"));
            commands.add(new BotCommand("/addtask", "Добавить задачу (админ)"));

            SetMyCommands setMyCommands = new SetMyCommands(); // Создаем объект
            setMyCommands.setCommands(commands);               // Устанавливаем команды
            setMyCommands.setScope(new BotCommandScopeDefault()); // Устанавливаем область видимости по умолчанию (для всех)
            // setMyCommands.setLanguageCode("ru"); // Опционально, если хотите указать язык для команд

            this.execute(setMyCommands); // Выполняем
            logger.info("Bot commands registered: /start, /train, /help, /addtask");
        } catch (TelegramApiException e) {
            logger.error("Error setting bot commands: " + e.getMessage(), e);
        }
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            handleTextMessage(update);
        } else if (update.hasMessage() && update.getMessage().hasPhoto()) {
            handlePhotoMessage(update);
        } else if (update.hasCallbackQuery()) {
            handleCallbackQuery(update);
        }
    }

    /**
     * Получает или создает пользователя в БД и кеширует его internalId.
     * Возвращает сущность User (может быть "легкой" если из кеша, или полной если из БД).
     */
    private User getPersistedUser(org.telegram.telegrambots.meta.api.objects.User telegramUserObj) {
        Long telegramId = telegramUserObj.getId();
        Long internalUserId = telegramToInternalUserIdMap.get(telegramId);
        User userEntity;

        if (internalUserId == null) {
            // Пользователя нет в кеше, получаем/создаем в БД
            String username = telegramUserObj.getUserName() != null ? telegramUserObj.getUserName() : telegramUserObj.getFirstName();
            userEntity = userTrainingService.getOrCreateUser(telegramId, username);
            if (userEntity.getId() != null) {
                telegramToInternalUserIdMap.put(telegramId, userEntity.getId());
                logger.info("User (TelegramID: {}) persisted/retrieved. InternalID: {}, Username: {}",
                        telegramId, userEntity.getId(), userEntity.getUsername());
            } else {
                // Этого не должно происходить, если сервис работает корректно
                logger.error("Critical error: UserTrainingService returned user without ID for TelegramID: {}", telegramId);
                // Возвращаем временную заглушку, чтобы избежать NPE, но это проблема
                userEntity = new User(telegramId, username);
            }
        } else {
            // Пользователь есть в кеше, создаем "легкую" сущность с известными данными
            // Этого достаточно для большинства операций в боте, где нужен только ID и username
            userEntity = new User();
            userEntity.setId(internalUserId);
            userEntity.setTelegramId(telegramId);
            userEntity.setUsername(telegramUserObj.getUserName() != null ? telegramUserObj.getUserName() : telegramUserObj.getFirstName());
            // Если вдруг понадобится username из БД, его можно было бы обновить:
            // User fullUser = userTrainingService.getOrCreateUser(telegramId, userEntity.getUsername());
            // userEntity.setUsername(fullUser.getUsername());
        }
        return userEntity;
    }


    private void handleTextMessage(Update update) {
        String messageText = update.getMessage().getText();
        long chatId = update.getMessage().getChatId();
        org.telegram.telegrambots.meta.api.objects.User telegramUserObj = update.getMessage().getFrom();

        User user = getPersistedUser(telegramUserObj);
        Long internalUserId = user.getId();

        if (internalUserId == null) {
            logger.error("CRITICAL: Could not obtain internal user ID for Telegram user: {} ({}). Aborting text message processing.",
                    telegramUserObj.getId(), user.getUsername());
            sendMessage(chatId, "Произошла внутренняя ошибка. Пожалуйста, попробуйте команду /start еще раз.");
            return;
        }

        if (pendingTasks.containsKey(chatId)) {
            processAddTaskText(chatId, messageText);
            return;
        }

        if ("/addtask".equals(messageText)) {
            if (chatId != ADMIN_CHAT_ID) {

                sendMessage(chatId, "Команда доступна только администратору");
                return;
            }
            PendingTaskData data = new PendingTaskData();
            data.step = AddTaskStep.WAITING_FOR_PHOTO;
            pendingTasks.put(chatId, data);
            sendMessage(chatId, "Пришлите изображение задачи");
            return;
        }

        if ("/start".equals(messageText)) {
            sendMessage(chatId, "Добро пожаловать! Используйте /train для получения задачи.");
        } else if ("/train".equals(messageText) || "задача".equalsIgnoreCase(messageText) || "next".equalsIgnoreCase(messageText)) {
            sendNextTask(chatId, internalUserId);
        } else if ("/help".equals(messageText)) {
            sendMessage(chatId, "Этот бот поможет тебе подготовиться к экзаменам по математике с помощью интервального повторения.\n\n" +
                    "Просто отвечай 'Правильно' или 'Неправильно' на предложенные задачи.\n" +
                    "Команда /train или сообщение 'задача' - получить новую задачу.");
        } else {
            sendMessage(chatId, "Привет, " + user.getUsername() + "! Используй команду /train или 'задача', чтобы получить задание. /help для помощи.");
          
        }
        String fileId = update.getMessage().getPhoto().get(update.getMessage().getPhoto().size() - 1).getFileId();
        data.fileId = fileId;
        data.step = AddTaskStep.WAITING_FOR_ANSWER;
        sendMessage(chatId, "Введите правильный ответ на задачу");
    }

    private void processAddTaskText(long chatId, String text) {
        PendingTaskData data = pendingTasks.get(chatId);
        if (data == null) return;

        switch (data.step) {
            case WAITING_FOR_ANSWER -> {
                data.answer = text;
                data.step = AddTaskStep.WAITING_FOR_TOPIC;
                sendTopicsPrompt(chatId);
            }
            case WAITING_FOR_TOPIC -> {
                String trimmed = text.trim();
                try {
                    long topicId = Long.parseLong(trimmed);
                    if (userTrainingService.getTopic(topicId).isEmpty()) {
                        sendMessage(chatId, "Такого id нет в списке, попробуйте еще раз.");
                        sendTopicsPrompt(chatId);
                        return;
                    }
                    userTrainingService.addTask(topicId, "FILE_ID:" + data.fileId, data.answer);
                    sendMessage(chatId, "Задача успешно добавлена");
                    pendingTasks.remove(chatId);
                } catch (NumberFormatException e) {
                    sendMessage(chatId, "Некорректный формат id. Попробуйте еще раз.");
                    sendTopicsPrompt(chatId);
                } catch (Exception e) {
                    logger.error("Error saving new task", e);
                    sendMessage(chatId, "Произошла ошибка при сохранении задачи");
                    pendingTasks.remove(chatId);
                }
            }
            case WAITING_FOR_NEW_TOPIC_NAME -> {
                String name = text.trim();
                try {
                    long newTopicId = userTrainingService.createTopic(name).getId();
                    userTrainingService.addTask(newTopicId, "FILE_ID:" + data.fileId, data.answer);
                    sendMessage(chatId, "Новая тема создана и задача добавлена");
                } catch (Exception e) {
                    logger.error("Error creating topic or saving task", e);
                    sendMessage(chatId, "Ошибка при создании темы или сохранении задачи");
                } finally {
                    pendingTasks.remove(chatId);
                }
            }
            default -> sendMessage(chatId, "Ожидалось изображение задачи");
        }
    }

    private void handlePhotoMessage(Update update) {
        long chatId = update.getMessage().getChatId();
        PendingTaskData data = pendingTasks.get(chatId);
        if (data == null || data.step != AddTaskStep.WAITING_FOR_PHOTO) {
            return;
        }
        String fileId = update.getMessage().getPhoto().get(update.getMessage().getPhoto().size() - 1).getFileId();
        data.fileId = fileId;
        data.step = AddTaskStep.WAITING_FOR_ANSWER;
        sendMessage(chatId, "Введите правильный ответ на задачу");
    }

    private void handleCallbackQuery(Update update) {
        String callbackData = update.getCallbackQuery().getData();
        long chatId = update.getCallbackQuery().getMessage().getChatId();
        org.telegram.telegrambots.meta.api.objects.User telegramUserObj = update.getCallbackQuery().getFrom();
        int messageId = update.getCallbackQuery().getMessage().getMessageId();

        User user = getPersistedUser(telegramUserObj);
        Long internalUserId = user.getId();

        if (internalUserId == null) {
            logger.error("CRITICAL: Could not obtain internal user ID for Telegram user: {} ({}) during callback. Aborting callback processing.",
                    telegramUserObj.getId(), user.getUsername());
            sendMessage(chatId, "Произошла внутренняя ошибка при обработке вашего ответа. Пожалуйста, попробуйте команду /start.");
            // Попытка убрать кнопки
            EditMessageReplyMarkup editMarkup = EditMessageReplyMarkup.builder()
                    .chatId(String.valueOf(chatId))
                    .messageId(messageId)
                    .replyMarkup(null)
                    .build();
            tryExecute(editMarkup);
            return;
        }

        if ("addtask_new_topic".equals(callbackData)) {
            PendingTaskData data = pendingTasks.get(chatId);
            if (data != null && data.step == AddTaskStep.WAITING_FOR_TOPIC) {
                data.step = AddTaskStep.WAITING_FOR_NEW_TOPIC_NAME;
                EditMessageReplyMarkup editMarkup = EditMessageReplyMarkup.builder()
                        .chatId(String.valueOf(chatId))
                        .messageId(messageId)
                        .replyMarkup(null)
                        .build();
                tryExecute(editMarkup);
                sendMessage(chatId, "Введите название новой темы:");
            }
            return;
        }

        Long currentTaskId = userCurrentTaskIdMap.get(internalUserId);

        if (currentTaskId == null) {
            sendMessage(chatId, "Не удалось определить, на какую задачу вы отвечаете. Попробуйте получить новую задачу: /start");
            EditMessageReplyMarkup editMarkup = EditMessageReplyMarkup.builder()
                    .chatId(String.valueOf(chatId))
                    .messageId(messageId)
                    .replyMarkup(null)
                    .build();
            tryExecute(editMarkup);
            return;
        }

        if (callbackData.startsWith("answer_")) {
            boolean isCorrect = "answer_correct".equals(callbackData);

            userTrainingService.processAnswer(internalUserId, currentTaskId, isCorrect);
            userCurrentTaskIdMap.remove(internalUserId); // Очищаем ID текущей задачи после ответа

            EditMessageReplyMarkup editMarkup = EditMessageReplyMarkup.builder()
                    .chatId(String.valueOf(chatId))
                    .messageId(messageId)
                    .replyMarkup(null) // Убираем клавиатуру
                    .build();
            tryExecute(editMarkup);

            String feedback = isCorrect ? "Отлично! Правильно! ✅" : "Неверно. ❌";
            sendMessage(chatId, feedback);
            sendNextTask(chatId, internalUserId); // Сразу следующую задачу
        }
    }

    private void sendNextTask(long chatId, Long internalUserId) {
        Optional<Task> optionalTask = userTrainingService.getNextTaskForUser(internalUserId);
        if (optionalTask.isPresent()) {
            Task task = optionalTask.get();
            userCurrentTaskIdMap.put(internalUserId, task.getId());

            InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
            List<InlineKeyboardButton> rowInline = new ArrayList<>();
            InlineKeyboardButton correctButton = InlineKeyboardButton.builder().text("✅ Правильно").callbackData("answer_correct").build();
            InlineKeyboardButton incorrectButton = InlineKeyboardButton.builder().text("❌ Неправильно").callbackData("answer_incorrect").build();
            rowInline.add(correctButton);
            rowInline.add(incorrectButton);
            inlineKeyboardMarkup.setKeyboard(Collections.singletonList(rowInline));

            if (task.getContent() != null && task.getContent().startsWith("FILE_ID:")) {
                SendPhoto photo = new SendPhoto();
                photo.setChatId(String.valueOf(chatId));
                photo.setPhoto(new InputFile(task.getContent().substring(8)));
                photo.setCaption("Введите ответ:");
                photo.setReplyMarkup(inlineKeyboardMarkup);
                tryExecute(photo);
            } else {
                SendMessage message = new SendMessage();
                message.setChatId(String.valueOf(chatId));
                message.setText(task.getContent() + "\n\nВведите ответ:");
                message.setReplyMarkup(inlineKeyboardMarkup);
                tryExecute(message);
            }
        } else {
            userCurrentTaskIdMap.remove(internalUserId);
            sendMessage(chatId, "На сегодня задач больше нет или не удалось подобрать подходящую. Заглядывай позже!");
        }
    }

    private void sendMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        tryExecute(message);
    }

    private void sendTopicsPrompt(long chatId) {
        StringBuilder sb = new StringBuilder("Доступные темы:\n");
        userTrainingService.getAllTopics().forEach(t -> sb.append(t.getId()).append(" - ").append(t.getName()).append("\n"));

        InlineKeyboardButton newTopicButton = InlineKeyboardButton.builder()
                .text("Новая тема")
                .callbackData("addtask_new_topic")
                .build();
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(Collections.singletonList(Collections.singletonList(newTopicButton)));

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(sb.toString() + "\nВведите id подходящей темы:");
        message.setReplyMarkup(markup);
        tryExecute(message);
    }

    private void tryExecute(org.telegram.telegrambots.meta.api.methods.BotApiMethod<?> method) {
        try {
            execute(method);
        } catch (TelegramApiException e) {
            logger.error("Telegram API execution error for method {}: {}", method.getClass().getSimpleName(), e.getMessage(), e);
            if (e.getMessage() != null && e.getMessage().contains("bot token is already in use")) {
                logger.error("CRITICAL: Bot token is already in use. Stop other instances of the bot.");
            }
        }
    }

    private void tryExecute(SendPhoto photo) {
        try {
            execute(photo);
        } catch (TelegramApiException e) {
            logger.error("Telegram API execution error for SendPhoto: {}", e.getMessage(), e);
        }
    }
}
