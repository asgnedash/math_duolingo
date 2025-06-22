package com.example.duolingomathbot.bot;

import com.example.duolingomathbot.model.Task;
import com.example.duolingomathbot.model.User;
import com.example.duolingomathbot.model.Topic;
import com.example.duolingomathbot.model.TopicType;
import com.example.duolingomathbot.model.Magnet;
import com.example.duolingomathbot.model.Test;
import com.example.duolingomathbot.service.UserTrainingService;
import com.example.duolingomathbot.service.MagnetService;
import com.example.duolingomathbot.bot.BotConfig;
import com.example.duolingomathbot.config.SrsConfig;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.objects.InputFile;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Telegram bot implementation for delivering math training tasks.
 */

@Component
public class MathSrTelegramBot extends TelegramLongPollingBot {

    private static final Logger logger = LoggerFactory.getLogger(MathSrTelegramBot.class);

    private final BotConfig botConfig;
    private final UserTrainingService userTrainingService;
    private final MagnetService magnetService;

    // Храним ID текущей задачи для каждого пользователя (internalUserId -> taskId)
    private final ConcurrentHashMap<Long, Long> userCurrentTaskIdMap = new ConcurrentHashMap<>();
    // Храним внутренний ID пользователя (telegramUserId -> internalUserId)
    // Это для оптимизации, чтобы не дергать БД каждый раз за internalUserId
    private final ConcurrentHashMap<Long, Long> telegramToInternalUserIdMap = new ConcurrentHashMap<>();

    /**
     * Tracks short training sessions for each user. A session limits how many
     * tasks are served consecutively before the user must explicitly request to
     * continue with /train.
     */
    private static class TrainingSession {
        int served = 0;
        int limit = 7; // start with easy limit
        boolean hasMedium = false;
        boolean hasHard = false;
    }

    private final ConcurrentHashMap<Long, TrainingSession> userSessions = new ConcurrentHashMap<>();

    private static final long ADMIN_CHAT_ID = 262398881L;

    private enum AddTaskStep {
        WAITING_FOR_PHOTO,
        WAITING_FOR_ANSWER,
        WAITING_FOR_TOPIC,
        WAITING_FOR_NEW_TOPIC_NAME,
        WAITING_FOR_NEW_TOPIC_TYPE
    }

    private enum ManageTopicsStep {
        CHOOSING_ACTION,
        ADD_WAITING_NAME,
        ADD_WAITING_TYPE,
        ORDER_CHOOSE_TYPE,
        ORDER_WAITING_LIST
    }

    private static class PendingTaskData {
        AddTaskStep step;
        String fileId;
        String answer;
        String newTopicName;
    }

    private static class ManageState {
        ManageTopicsStep step;
        String newTopicName;
        TopicType orderType;
    }

    private enum AddMagnetStep {
        WAITING_FOR_FILE,
        WAITING_FOR_MESSAGE
    }

    private static class MagnetData {
        AddMagnetStep step;
        String fileId;
        String message;
    }

    private final ConcurrentHashMap<Long, PendingTaskData> pendingTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ManageState> manageStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, MagnetData> magnetStates = new ConcurrentHashMap<>();

    private enum MakeTestStep {
        WAITING_COUNT,
        WAITING_PHOTO,
        WAITING_ANSWER,
        WAITING_ADVICE
    }

    private static class MakeTestState {
        MakeTestStep step;
        int total;
        int current;
        long testId;
        int startId;
        String fileId;
    }

    private enum TestSessionStep { WAITING_ID, IN_PROGRESS }

    private static class TestSession {
        TestSessionStep step;
        Test test;
        java.util.List<Task> tasks;
        int index;
        int correct;
    }

    private final ConcurrentHashMap<Long, MakeTestState> makeTestStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, TestSession> testSessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();


    public MathSrTelegramBot(BotConfig botConfig, UserTrainingService userTrainingService, MagnetService magnetService) {
        super(botConfig.getBotToken());
        this.botConfig = botConfig;
        this.userTrainingService = userTrainingService;
        this.magnetService = magnetService;
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
            commands.add(new BotCommand("/cancel", "Отмена текущего действия"));
            commands.add(new BotCommand("/addtask", "Добавить задачу (админ)"));
            commands.add(new BotCommand("/managetopics", "Управление темами (админ)"));
            commands.add(new BotCommand("/makemagnet", "Создать лидмагнит (админ)"));
            commands.add(new BotCommand("/maketest", "Создать тест (админ)"));
            commands.add(new BotCommand("/test", "Пройти тест"));
            commands.add(new BotCommand("/settings", "Настройки"));
            commands.add(new BotCommand("/marathon", "Участвовать в марафоне"));
            commands.add(new BotCommand("/finishmarathon", "Завершить марафон (админ)"));

            SetMyCommands setMyCommands = new SetMyCommands(); // Создаем объект
            setMyCommands.setCommands(commands);               // Устанавливаем команды
            setMyCommands.setScope(new BotCommandScopeDefault()); // Устанавливаем область видимости по умолчанию (для всех)
            // setMyCommands.setLanguageCode("ru"); // Опционально, если хотите указать язык для команд

            this.execute(setMyCommands); // Выполняем
            logger.info("Bot commands registered: /start, /train, /help, /cancel, /addtask, /managetopics, /maketest, /test, /makemagnet, /settings, /marathon, /finishmarathon");
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
        } else if (update.hasMessage() && update.getMessage().hasDocument()) {
            handleDocumentMessage(update);
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

        if (manageStates.containsKey(chatId)) {
            processManageTopicsText(chatId, messageText);
            return;
        }

        MagnetData magnetData = magnetStates.get(chatId);
        if (magnetData != null && magnetData.step == AddMagnetStep.WAITING_FOR_MESSAGE) {
            magnetData.message = messageText;
            Magnet magnet = magnetService.createMagnet(magnetData.fileId, magnetData.message);
            magnetStates.remove(chatId);
            String link = "https://t.me/" + getBotUsername() + "?start=" + magnet.getStartId();
            sendMessage(chatId, "Лидмагнит успешно создан! Вот ссылка: " + link);
            return;
        }
        if (magnetData != null && magnetData.step == AddMagnetStep.WAITING_FOR_FILE) {
            sendMessage(chatId, "Пожалуйста, пришлите PDF файл.");
            return;
        }

         if (makeTestStates.containsKey(chatId)) {
            processMakeTestText(chatId, messageText);
            return;
        }

        TestSession ts = testSessions.get(internalUserId);
        if (ts != null) {
            if (ts.step == TestSessionStep.WAITING_ID) {
                try {
                    int sid = Integer.parseInt(messageText.trim());
                    Optional<Test> opt = userTrainingService.getTestByStartId(sid);
                    if (opt.isPresent()) {
                        ts.test = opt.get();
                        ts.tasks = userTrainingService.getTasksForTest(ts.test);
                        if (ts.tasks.isEmpty()) {
                            sendMessage(chatId, "Тест пуст.");
                            testSessions.remove(internalUserId);
                        } else {
                            ts.step = TestSessionStep.IN_PROGRESS;
                            ts.index = 0;
                            ts.correct = 0;
                            sendTestTask(chatId, ts, null);
                        }
                    } else {
                        sendMessage(chatId, "Тест не найден. Попробуйте еще раз.");
                    }
                } catch (NumberFormatException e) {
                    sendMessage(chatId, "Неверный формат номера теста.");
                }
                return;
            } else if (ts.step == TestSessionStep.IN_PROGRESS) {
                processTestAnswer(chatId, internalUserId, messageText);
                return;
            }
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

        if ("/makemagnet".equals(messageText)) {
            if (chatId != ADMIN_CHAT_ID) {
                sendMessage(chatId, "Команда доступна только администратору");
                return;
            }
            MagnetData md = new MagnetData();
            md.step = AddMagnetStep.WAITING_FOR_FILE;
            magnetStates.put(chatId, md);
            sendMessage(chatId, "Пришлите PDF файл лидмагнита:");
            return;
        }

        if ("/managetopics".equals(messageText)) {
            if (chatId != ADMIN_CHAT_ID) {
                sendMessage(chatId, "Команда доступна только администратору");
                return;
            }
            ManageState state = new ManageState();
            state.step = ManageTopicsStep.CHOOSING_ACTION;
            manageStates.put(chatId, state);
            sendManageTopicsMenu(chatId);
            return;
        }

        if ("/maketest".equals(messageText)) {
            if (chatId != ADMIN_CHAT_ID) {
                sendMessage(chatId, "Команда доступна только администратору");
                return;
            }
            MakeTestState st = new MakeTestState();
            st.step = MakeTestStep.WAITING_COUNT;
            makeTestStates.put(chatId, st);
            sendMessage(chatId, "Сколько заданий будет в тесте?");
            return;
        }

        if ("/test".equals(messageText)) {
            TestSession session = new TestSession();
            session.step = TestSessionStep.WAITING_ID;
            testSessions.put(internalUserId, session);
            sendMessage(chatId, "Введите номер теста:");
            return;
        }

        if ("/marathon".equals(messageText)) {
            userTrainingService.updateUserMarathon(internalUserId, true);
            Optional<TopicType> ex = userTrainingService.getUserExam(internalUserId);
            String examName = ex.map(TopicType::getDisplayName).orElse("экзамен не выбран");
            sendMessage(chatId, "Вы участвуете в марафоне по подготовке к " + examName + "!");
            return;
        }

        if ("/finishmarathon".equals(messageText)) {
            if (chatId != ADMIN_CHAT_ID) {
                sendMessage(chatId, "Команда доступна только администратору");
                return;
            }
            userTrainingService.finishMarathonForAll();
            sendMessage(chatId, "Марафон завершен для всех пользователей");
            return;
        }

        if ("/settings".equals(messageText)) {
            sendSettings(chatId, internalUserId);
            return;
        }

        if ("/cancel".equals(messageText)) {
            resetUserState(chatId, internalUserId);
            sendMessage(chatId, "Действие отменено.");
            return;
        }

        if (messageText.startsWith("/start")) {
            String[] parts = messageText.split("\\s+", 2);
            if (parts.length > 1) {
                try {
                    int startId = Integer.parseInt(parts[1]);
                    Optional<Magnet> magnetOpt = magnetService.getByStartId(startId);
                    if (magnetOpt.isPresent()) {
                        Magnet m = magnetOpt.get();
                        sendDocument(chatId, m.getFileId(), m.getMessage());
                        sendExamPromptDelayed(chatId);
                    } else {
                        Optional<Test> testOpt = userTrainingService.getTestByStartId(startId);
                        if (testOpt.isPresent()) {
                            TestSession session = new TestSession();
                            session.step = TestSessionStep.IN_PROGRESS;
                            session.test = testOpt.get();
                            session.tasks = userTrainingService.getTasksForTest(session.test);
                            if (session.tasks.isEmpty()) {
                                sendMessage(chatId, "Тест пуст.");
                            } else {
                                session.index = 0;
                                session.correct = 0;
                                testSessions.put(internalUserId, session);
                                sendTestTask(chatId, session, null);
                            }
                        } else {
                            sendMessage(chatId, "Ссылка недействительна");
                        }
                    }
                } catch (NumberFormatException e) {
                    sendMessage(chatId, "Неверный параметр ссылки");
                }
            } else {
                sendMessage(chatId, "Добро пожаловать! Используйте /train для получения задачи.");
                sendExamPrompt(chatId);
            }
        } else if ("/train".equals(messageText) || "задача".equalsIgnoreCase(messageText) || "next".equalsIgnoreCase(messageText)) {
            sendNextTask(chatId, internalUserId, true);
        } else if ("/help".equals(messageText)) {
            sendMessage(chatId, "Этот бот поможет тебе подготовиться к экзаменам по математике с помощью интервального повторения.\n\n" +
                    "Просто отвечай 'Правильно' или 'Неправильно' на предложенные задачи.\n" +
                    "Команда /train или сообщение 'задача' - получить новую задачу.");
        } else if (userCurrentTaskIdMap.containsKey(internalUserId)) {
            processUserAnswer(chatId, internalUserId, messageText);
        } else {
            sendMessage(chatId, "Привет, " + user.getUsername() + "! Используй команду /train или 'задача', чтобы получить задание. /help для помощи.");
        }
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
                data.newTopicName = text.trim();
                data.step = AddTaskStep.WAITING_FOR_NEW_TOPIC_TYPE;
                sendTopicTypePrompt(chatId);
            }
            case WAITING_FOR_NEW_TOPIC_TYPE -> {
                sendMessage(chatId, "Выберите тип с помощью кнопок.");
            }
            default -> sendMessage(chatId, "Ожидалось изображение задачи");
        }
    }

    private void processManageTopicsText(long chatId, String text) {
        ManageState state = manageStates.get(chatId);
        if (state == null) return;

        switch (state.step) {
            case ADD_WAITING_NAME -> {
                state.newTopicName = text.trim();
                state.step = ManageTopicsStep.ADD_WAITING_TYPE;
                sendTopicTypePrompt(chatId);
            }
            case ORDER_CHOOSE_TYPE -> sendMessage(chatId, "Выберите тип с помощью кнопок.");
            case ORDER_WAITING_LIST -> {
                String[] parts = text.trim().split("\\s+");
                try {
                    List<Long> ids = new ArrayList<>();
                    for (String p : parts) {
                        if (!p.isBlank()) ids.add(Long.parseLong(p));
                    }
                    userTrainingService.updateTopicOrder(ids, state.orderType);
                    sendMessage(chatId, "Очередность обновлена");
                } catch (Exception e) {
                    sendMessage(chatId, "Ошибка обработки списка");
                }
                manageStates.remove(chatId);
            }
            default -> sendMessage(chatId, "Используйте меню.");
        }
    }

    private void processMakeTestText(long chatId, String text) {
        MakeTestState state = makeTestStates.get(chatId);
        if (state == null) return;

        switch (state.step) {
            case WAITING_COUNT -> {
                try {
                    state.total = Integer.parseInt(text.trim());
                    if (state.total <= 0) throw new NumberFormatException();
                    Test t = userTrainingService.createTest();
                    state.testId = t.getId();
                    state.startId = t.getStartId();
                    state.current = 0;
                    state.step = MakeTestStep.WAITING_PHOTO;
                    sendMessage(chatId, "Пришлите изображение задачи 1");
                } catch (NumberFormatException e) {
                    sendMessage(chatId, "Неверный ввод. Введите число.");
                }
            }
            case WAITING_ANSWER -> {
                userTrainingService.addTaskToTest(
                        userTrainingService.getTestByStartId(state.startId).orElseThrow(),
                        "FILE_ID:" + state.fileId, text.trim());
                state.current++;
                if (state.current < state.total) {
                    state.step = MakeTestStep.WAITING_PHOTO;
                    sendMessage(chatId, "Пришлите изображение задачи " + (state.current + 1));
                } else {
                    state.step = MakeTestStep.WAITING_ADVICE;
                    sendMessage(chatId, "Введите совет для учеников:");
                }
            }
            case WAITING_ADVICE -> {
                Test t = userTrainingService.getTestByStartId(state.startId).orElseThrow();
                userTrainingService.updateTestAdvice(t, text);
                makeTestStates.remove(chatId);
                String link = "https://t.me/" + getBotUsername() + "?start=" + state.startId;
                sendMessage(chatId, "Тест создан. Номер: " + state.startId + "\n" + link);
            }
            default -> sendMessage(chatId, "Отправьте изображение задачи");
        }
    }

    private void handlePhotoMessage(Update update) {
        long chatId = update.getMessage().getChatId();
        PendingTaskData data = pendingTasks.get(chatId);
        if (data != null && data.step == AddTaskStep.WAITING_FOR_PHOTO) {
            String fileId = update.getMessage().getPhoto().get(update.getMessage().getPhoto().size() - 1).getFileId();
            data.fileId = fileId;
            data.step = AddTaskStep.WAITING_FOR_ANSWER;
            sendMessage(chatId, "Введите правильный ответ на задачу");
            return;
        }

        MakeTestState st = makeTestStates.get(chatId);
        if (st != null && st.step == MakeTestStep.WAITING_PHOTO) {
            String fileId = update.getMessage().getPhoto().get(update.getMessage().getPhoto().size() - 1).getFileId();
            st.fileId = fileId;
            st.step = MakeTestStep.WAITING_ANSWER;
            sendMessage(chatId, "Введите правильный ответ");
        }
    }

    private void handleDocumentMessage(Update update) {
        long chatId = update.getMessage().getChatId();
        MagnetData magnetData = magnetStates.get(chatId);
        if (magnetData == null || magnetData.step != AddMagnetStep.WAITING_FOR_FILE) {
            return;
        }
        String fileId = update.getMessage().getDocument().getFileId();
        magnetData.fileId = fileId;
        magnetData.step = AddMagnetStep.WAITING_FOR_MESSAGE;
        sendMessage(chatId, "Введите приветственное сообщение:");
    }

    private void handleCallbackQuery(Update update) {
        String callbackData = update.getCallbackQuery().getData();
        long chatId = update.getCallbackQuery().getMessage().getChatId();
        org.telegram.telegrambots.meta.api.objects.User telegramUserObj = update.getCallbackQuery().getFrom();
        int messageId = update.getCallbackQuery().getMessage().getMessageId();
        deleteMessage(chatId, messageId);

        User user = getPersistedUser(telegramUserObj);
        Long internalUserId = user.getId();

        if (internalUserId == null) {
            logger.error("CRITICAL: Could not obtain internal user ID for Telegram user: {} ({}) during callback. Aborting callback processing.",
                    telegramUserObj.getId(), user.getUsername());
            sendMessage(chatId, "Произошла внутренняя ошибка при обработке вашего ответа. Пожалуйста, попробуйте команду /start.");
            return;
        }

        if ("addtask_new_topic".equals(callbackData)) {
            PendingTaskData data = pendingTasks.get(chatId);
            if (data != null && data.step == AddTaskStep.WAITING_FOR_TOPIC) {
                data.step = AddTaskStep.WAITING_FOR_NEW_TOPIC_NAME;
                sendMessage(chatId, "Введите название новой темы:");
            }
            return;
        }

        if ("manage_add".equals(callbackData)) {
            ManageState state = manageStates.get(chatId);
            if (state != null && state.step == ManageTopicsStep.CHOOSING_ACTION) {
                state.step = ManageTopicsStep.ADD_WAITING_NAME;
                sendMessage(chatId, "Введите название темы:");
            }
            return;
        }

        if ("manage_order".equals(callbackData)) {
            ManageState state = manageStates.get(chatId);
            if (state != null && state.step == ManageTopicsStep.CHOOSING_ACTION) {
                state.step = ManageTopicsStep.ORDER_CHOOSE_TYPE;
                sendOrderTypePrompt(chatId);
            }
            return;
        }

        if (callbackData.startsWith("newtopic_type_")) {
            String typeStr = callbackData.substring("newtopic_type_".length());
            PendingTaskData taskData = pendingTasks.get(chatId);
            if (taskData != null && taskData.step == AddTaskStep.WAITING_FOR_NEW_TOPIC_TYPE) {
                try {
                    if ("BOTH".equals(typeStr)) {
                        Topic oge = userTrainingService.createTopic(taskData.newTopicName, TopicType.OGE);
                        userTrainingService.addTask(oge.getId(), "FILE_ID:" + taskData.fileId, taskData.answer);
                        Topic ege = userTrainingService.createTopic(taskData.newTopicName, TopicType.EGE);
                        userTrainingService.addTask(ege.getId(), "FILE_ID:" + taskData.fileId, taskData.answer);
                    } else {
                        TopicType type = TopicType.valueOf(typeStr);
                        long newTopicId = userTrainingService.createTopic(taskData.newTopicName, type).getId();
                        userTrainingService.addTask(newTopicId, "FILE_ID:" + taskData.fileId, taskData.answer);
                    }
                    sendMessage(chatId, "Новая тема создана и задача добавлена");
                } catch (Exception e) {
                    logger.error("Error creating topic or saving task", e);
                    sendMessage(chatId, "Ошибка при создании темы или сохранении задачи");
                } finally {
                    pendingTasks.remove(chatId);
                }
            } else {
                ManageState state = manageStates.get(chatId);
                if (state != null && state.step == ManageTopicsStep.ADD_WAITING_TYPE) {
                    try {
                        if ("BOTH".equals(typeStr)) {
                            userTrainingService.createTopic(state.newTopicName, TopicType.OGE);
                            userTrainingService.createTopic(state.newTopicName, TopicType.EGE);
                        } else {
                            TopicType type = TopicType.valueOf(typeStr);
                            userTrainingService.createTopic(state.newTopicName, type);
                        }
                        sendMessage(chatId, "Тема успешно создана");
                    } catch (Exception e) {
                        logger.error("Error creating topic", e);
                        sendMessage(chatId, "Ошибка при создании темы");
                    } finally {
                        manageStates.remove(chatId);
                    }
                }
            }
            return;
        }

        if (callbackData.startsWith("order_type_")) {
            String typeStr = callbackData.substring("order_type_".length());
            ManageState state = manageStates.get(chatId);
            if (state != null && state.step == ManageTopicsStep.ORDER_CHOOSE_TYPE) {
                state.orderType = TopicType.valueOf(typeStr);
                state.step = ManageTopicsStep.ORDER_WAITING_LIST;
                sendCurrentOrder(chatId, state.orderType);
            }
            return;
        }

        if ("settings_change_exam".equals(callbackData)) {
            sendExamPrompt(chatId);
            return;
        }

        if (callbackData.startsWith("exam_select_")) {
            String typeStr = callbackData.substring("exam_select_".length());
            TopicType type = TopicType.valueOf(typeStr);
            userTrainingService.updateUserExam(internalUserId, type);
            sendMessage(chatId, "Вы выбрали подготовку к " + type.getDisplayName() + "\nИзменить выбор можно с помощью команды /settings");
            return;
        }

        Long currentTaskId = userCurrentTaskIdMap.get(internalUserId);

        if (currentTaskId == null) {
            sendMessage(chatId, "Не удалось определить, на какую задачу вы отвечаете. Попробуйте получить новую задачу: /start");
            return;
        }

        if (callbackData.startsWith("answer_")) {
            boolean isCorrect = "answer_correct".equals(callbackData);

            userTrainingService.processAnswer(internalUserId, currentTaskId, isCorrect);
            userCurrentTaskIdMap.remove(internalUserId); // Очищаем ID текущей задачи после ответа

            sendNextTask(chatId, internalUserId, false, isCorrect); // Сразу следующую задачу
        }
    }

    private void processUserAnswer(long chatId, Long internalUserId, String userAnswer) {
        Long taskId = userCurrentTaskIdMap.get(internalUserId);
        if (taskId == null) {
            sendMessage(chatId, "Используйте /train, чтобы получить новую задачу.");
            return;
        }

        boolean isCorrect = userTrainingService.processAnswer(internalUserId, taskId, userAnswer);
        userCurrentTaskIdMap.remove(internalUserId);

        sendNextTask(chatId, internalUserId, false, isCorrect);
    }

    private void sendNextTask(long chatId, Long internalUserId, boolean userInitiated) {
        sendNextTask(chatId, internalUserId, userInitiated, null);
    }

    private void sendNextTask(long chatId, Long internalUserId, boolean userInitiated, Boolean prevCorrect) {
        if (userTrainingService.getUserExam(internalUserId).isEmpty()) {
            sendExamPrompt(chatId);
            return;
        }

        TrainingSession session = userSessions.get(internalUserId);
        if (session == null || userInitiated) {
            session = new TrainingSession();
            userSessions.put(internalUserId, session);
        } else if (session.served >= session.limit) {
            userSessions.remove(internalUserId);
            userCurrentTaskIdMap.remove(internalUserId);
            sendMessage(chatId, "На сегодня достаточно. Чтобы продолжить, отправь /train.");
            return;
        }

        Optional<Task> optionalTask = userTrainingService.getNextTaskForUser(internalUserId);
        if (optionalTask.isPresent()) {
            Task task = optionalTask.get();
            userCurrentTaskIdMap.put(internalUserId, task.getId());

            double ratio = task.getDifficulty() / Math.max(1.0, task.getTopic().getMaxDifficultyInTopic());
            if (ratio >= SrsConfig.HARD_TASK_DIFFICULTY_THRESHOLD_FACTOR) {
                session.hasHard = true;
                session.limit = 5;
            } else if (ratio >= SrsConfig.MEDIUM_TASK_DIFFICULTY_THRESHOLD_FACTOR && !session.hasHard) {
                session.hasMedium = true;
                session.limit = Math.min(session.limit, 6);
            }
            session.served++;

            String prefix = "";
            if (prevCorrect != null) {
                prefix = prevCorrect ? "Верно\n" : "Неверно\n";
            }

            if (task.getContent() != null && task.getContent().startsWith("FILE_ID:")) {
                SendPhoto photo = new SendPhoto();
                photo.setChatId(String.valueOf(chatId));
                photo.setPhoto(new InputFile(task.getContent().substring(8)));
                photo.setCaption(prefix + "Введите ответ:");
                tryExecute(photo);
            } else {
                SendMessage message = new SendMessage();
                message.setChatId(String.valueOf(chatId));
                message.setText(prefix + task.getContent() + "\n\nВведите ответ:");
                tryExecute(message);
            }
        } else {
            userCurrentTaskIdMap.remove(internalUserId);
            userSessions.remove(internalUserId);
            String prefix = "";
            if (prevCorrect != null) {
                prefix = prevCorrect ? "Верно\n" : "Неверно\n";
            }
            sendMessage(chatId, prefix + "На сегодня задач больше нет или не удалось подобрать подходящую. Заглядывай позже!");
        }
    }

    private void sendTestTask(long chatId, TestSession session, Boolean prevCorrect) {
        Task task = session.tasks.get(session.index);
        String prefix = "";
        if (prevCorrect != null) {
            prefix = prevCorrect ? "Верно\n" : "Неверно\n";
        }
        if (task.getContent() != null && task.getContent().startsWith("FILE_ID:")) {
            SendPhoto photo = new SendPhoto();
            photo.setChatId(String.valueOf(chatId));
            photo.setPhoto(new InputFile(task.getContent().substring(8)));
            photo.setCaption(prefix + "Введите ответ:");
            tryExecute(photo);
        } else {
            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(chatId));
            message.setText(prefix + task.getContent() + "\n\nВведите ответ:");
            tryExecute(message);
        }
    }

    private void processTestAnswer(long chatId, Long internalUserId, String answer) {
        TestSession session = testSessions.get(internalUserId);
        if (session == null || session.step != TestSessionStep.IN_PROGRESS) return;

        Task task = session.tasks.get(session.index);
        boolean isCorrect = userTrainingService.isAnswerCorrect(task, answer);
        if (isCorrect) {
            session.correct++;
        }
        session.index++;
        if (session.index < session.tasks.size()) {
            sendTestTask(chatId, session, isCorrect);
        } else {
            int total = session.tasks.size();
            int wrong = total - session.correct;
            StringBuilder sb = new StringBuilder();
            sb.append("Правильных ответов: ").append(session.correct)
                    .append("\nНеправильных ответов: ").append(wrong);
            if (wrong > total / 3) {
                if (session.test.getAdvice() != null) sb.append("\n\n").append(session.test.getAdvice());
            }
            String prefix = isCorrect ? "Верно\n" : "Неверно\n";
            sendMessage(chatId, prefix + sb.toString());
            testSessions.remove(internalUserId);
            sendExamPromptDelayed(chatId);
        }
    }

    private void sendMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        tryExecute(message);
    }

    private void sendDocument(long chatId, String fileId, String caption) {
        SendDocument doc = new SendDocument();
        doc.setChatId(String.valueOf(chatId));
        doc.setDocument(new InputFile(fileId));
        if (caption != null) {
            doc.setCaption(caption);
        }
        tryExecute(doc);
    }

    private void resetUserState(long chatId, Long internalUserId) {
        pendingTasks.remove(chatId);
        manageStates.remove(chatId);
        magnetStates.remove(chatId);
        makeTestStates.remove(chatId);
        if (internalUserId != null) {
            userCurrentTaskIdMap.remove(internalUserId);
            userSessions.remove(internalUserId);
            testSessions.remove(internalUserId);
        }
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

    private void sendTopicTypePrompt(long chatId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(InlineKeyboardButton.builder().text("ОГЭ").callbackData("newtopic_type_OGE").build());
        row.add(InlineKeyboardButton.builder().text("ЕГЭ").callbackData("newtopic_type_EGE").build());
        row.add(InlineKeyboardButton.builder().text("ОБА").callbackData("newtopic_type_BOTH").build());
        markup.setKeyboard(Collections.singletonList(row));

        SendMessage msg = new SendMessage();
        msg.setChatId(String.valueOf(chatId));
        msg.setText("Выберите тип темы:");
        msg.setReplyMarkup(markup);
        tryExecute(msg);
    }

    private void sendManageTopicsMenu(long chatId) {
        InlineKeyboardButton addBtn = InlineKeyboardButton.builder()
                .text("Добавить тему")
                .callbackData("manage_add")
                .build();
        InlineKeyboardButton orderBtn = InlineKeyboardButton.builder()
                .text("Настроить очередность")
                .callbackData("manage_order")
                .build();
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(addBtn);
        row.add(orderBtn);
        markup.setKeyboard(Collections.singletonList(row));

        SendMessage msg = new SendMessage();
        msg.setChatId(String.valueOf(chatId));
        msg.setText("Выберите действие:");
        msg.setReplyMarkup(markup);
        tryExecute(msg);
    }

    private void sendOrderTypePrompt(long chatId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(InlineKeyboardButton.builder().text("ОГЭ").callbackData("order_type_OGE").build());
        row.add(InlineKeyboardButton.builder().text("ЕГЭ").callbackData("order_type_EGE").build());
        markup.setKeyboard(Collections.singletonList(row));

        SendMessage msg = new SendMessage();
        msg.setChatId(String.valueOf(chatId));
        msg.setText("Для какого типа настроить очередность?");
        msg.setReplyMarkup(markup);
        tryExecute(msg);
    }

    private void sendCurrentOrder(long chatId, TopicType type) {
        StringBuilder sb = new StringBuilder("Текущая очередность:\n");
        List<Topic> ordered = userTrainingService.getOrderedTopics(type);
        for (int i = 0; i < ordered.size(); i++) {
            Topic t = ordered.get(i);
            sb.append(i + 1).append(". ").append(t.getId()).append(" - ")
                    .append(t.getName()).append(" (" + t.getType().getDisplayName() + ")\n");
        }
        List<Topic> others = userTrainingService.getUnorderedTopics(type);
        if (!others.isEmpty()) {
            sb.append("\nНе в очереди:\n");
            others.forEach(t -> sb.append(t.getId()).append(" - ").append(t.getName()).append("\n"));
        }
        sb.append("\nВведите список id через пробел в нужном порядке:");
        SendMessage msg = new SendMessage();
        msg.setChatId(String.valueOf(chatId));
        msg.setText(sb.toString());
        tryExecute(msg);
    }

    private void sendExamPrompt(long chatId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(InlineKeyboardButton.builder().text("ОГЭ").callbackData("exam_select_OGE").build());
        row.add(InlineKeyboardButton.builder().text("ЕГЭ").callbackData("exam_select_EGE").build());
        markup.setKeyboard(Collections.singletonList(row));

        SendMessage msg = new SendMessage();
        msg.setChatId(String.valueOf(chatId));
        msg.setText("Выберите, к какому экзамену вы готовитесь");
        msg.setReplyMarkup(markup);
        tryExecute(msg);
    }

    private void sendExamPromptDelayed(long chatId) {
        scheduler.schedule(() -> sendExamPrompt(chatId), 5, TimeUnit.SECONDS);
    }

    private void sendSettings(long chatId, Long userId) {
        Optional<TopicType> examOpt = userTrainingService.getUserExam(userId);
        String examName = examOpt.map(TopicType::getDisplayName).orElse("не выбран");

        InlineKeyboardButton changeBtn = InlineKeyboardButton.builder()
                .text("Изменить экзамен")
                .callbackData("settings_change_exam")
                .build();
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(Collections.singletonList(Collections.singletonList(changeBtn)));

        SendMessage msg = new SendMessage();
        msg.setChatId(String.valueOf(chatId));
        msg.setText("Вы готовитесь к экзамену: " + examName + "\n\nВыберите действие:");
        msg.setReplyMarkup(markup);
        tryExecute(msg);
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

    private void tryExecute(SendDocument document) {
        try {
            execute(document);
        } catch (TelegramApiException e) {
            logger.error("Telegram API execution error for SendDocument: {}", e.getMessage(), e);
        }
    }

    private void deleteMessage(long chatId, int messageId) {
        org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage dm =
                new org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage();
        dm.setChatId(String.valueOf(chatId));
        dm.setMessageId(messageId);
        tryExecute(dm);
    }
}
