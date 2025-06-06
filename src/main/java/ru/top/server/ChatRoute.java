package ru.top.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import ru.top.server.model.ChatGroup;
import ru.top.server.model.ChatUser;
import ru.top.server.model.LoginRequest;
import ru.top.server.model.Message;
import ru.top.server.repository.ChatGroupRepository;
import ru.top.server.repository.ChatUserRepository;
import ru.top.server.security.JwtUtil;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

// Аннотация @Component указывает, что этот класс является компонентом Spring и будет автоматически обнаружен и зарегистрирован в контексте приложения
@Component
public class ChatRoute extends RouteBuilder {
    // Логгер для записи информации и ошибок в процессе выполнения
    private static final Logger log = LoggerFactory.getLogger(ChatRoute.class);

    // Внедрение зависимостей через аннотацию @Autowired для работы с JSON, репозиториями, безопасностью и JWT
    @Autowired
    private ObjectMapper objectMapper; // Объект для сериализации/десериализации JSON

    @Autowired
    private ChatUserRepository userRepository; // Репозиторий для работы с пользователями в базе данных

    @Autowired
    private ChatGroupRepository groupRepository; // Репозиторий для работы с группами в базе данных

    @Autowired
    private AuthenticationManager authenticationManager; // Менеджер аутентификации Spring Security

    @Autowired
    private JwtUtil jwtUtil; // Утилита для работы с JWT-токенами

    @Autowired
    private PasswordEncoder passwordEncoder; // Кодировщик паролей для безопасного хранения

    @Autowired
    private UserDetailsService userDetailsService; // Сервис для загрузки данных пользователя

    @Override
    public void configure() {
        // Конфигурация REST-эндпоинтов с использованием Apache Camel
        log.info("Configuring Camel REST routes");

        // Настройка REST-конфигурации: использование компонента servlet, отключение автоматической привязки данных, включение красивого форматирования JSON
        restConfiguration()
                .component("servlet") // Использование сервлета для обработки HTTP-запросов
                .bindingMode(RestBindingMode.off) // Отключение автоматической привязки данных (ручная обработка JSON)
                .dataFormatProperty("prettyPrint", "true"); // Включение форматирования JSON для удобочитаемости

        // Эндпоинт для аутентификации пользователя (POST /api/auth/login)
        rest("/api/auth/login")
                .post() // Метод HTTP POST
                .consumes("application/json") // Ожидает JSON в теле запроса
                .produces("application/json") // Возвращает JSON в ответе
                .to("direct:login"); // Перенаправление на маршрут direct:login для обработки

        // Маршрут для обработки логина
        from("direct:login")
                .doTry() // Начало блока обработки исключений
                .process(exchange -> {
                    // Получение тела запроса как строки
                    String body = exchange.getIn().getBody(String.class);
                    log.info("Received login request: {}", body);
                    // Проверка, что тело запроса не пустое
                    if (body == null || body.trim().isEmpty()) {
                        throw new IllegalArgumentException("Request body is empty");
                    }
                    LoginRequest loginRequest = null;
                    try {
                        // Десериализация JSON в объект LoginRequest
                        loginRequest = objectMapper.readValue(body, LoginRequest.class);
                        // Проверка наличия имени пользователя и пароля
                        if (loginRequest.username() == null || loginRequest.password().isEmpty()) {
                            throw new IllegalArgumentException("Missing username or password");
                        }
                        log.info("Authenticating user: {}", loginRequest.username());
                        // Аутентификация пользователя через Spring Security
                        Authentication authentication = authenticationManager.authenticate(
                                new UsernamePasswordAuthenticationToken(
                                        loginRequest.username(), loginRequest.password()));
                        log.info("Authentication successful for user: {}", loginRequest.username());
                        // Загрузка данных пользователя
                        UserDetails userDetails = userDetailsService.loadUserByUsername(loginRequest.username());
                        log.info("Loaded UserDetails: username={}, authorities={}",
                                userDetails.getUsername(), userDetails.getAuthorities());
                        // Генерация JWT-токена
                        String jwt = jwtUtil.generateToken(userDetails);
                        log.info("Generated JWT for user: {}", loginRequest.username());
                        // Установка ответа с JWT-токеном
                        exchange.getIn().setBody("{\"token\":\"" + jwt + "\"}");
                    } catch (AuthenticationException e) {
                        // Обработка ошибок аутентификации
                        log.error("Authentication failed for user: {}: {}",
                                loginRequest != null ? loginRequest.username() : "unknown", e.getMessage());
                        throw new IllegalArgumentException("Invalid credentials: " + e.getMessage(), e);
                    } catch (Exception e) {
                        // Обработка ошибок десериализации или других проблем
                        log.error("Error processing login for user: {}: {}",
                                loginRequest != null ? loginRequest.username() : "unknown", e.getMessage());
                        throw new IllegalArgumentException("Invalid JSON or processing error: " + e.getMessage(), e);
                    }
                })
                .doCatch(Exception.class) // Обработка всех исключений
                .process(exchange -> {
                    // Получение исключения из контекста Camel
                    Exception exception = exchange.getProperty("CamelExceptionCaught", Exception.class);
                    log.error("Login failed: {}", exception.getMessage(), exception);
                    // Формирование сообщения об ошибке
                    String errorMessage = exception.getCause() != null
                            ? exception.getCause().getMessage()
                            : exception.getMessage();
                    exchange.getMessage().setBody("{\"error\":\"" + errorMessage + "\"}");
                    exchange.getMessage().setHeader("Content-Type", "application/json");
                    exchange.getMessage().setHeader("CamelHttpResponseCode", 400); // Код ошибки 400
                })
                .end();

        // Эндпоинт для регистрации нового пользователя (POST /api/users/register)
        rest("/api/users/register")
                .post()
                .consumes("application/json")
                .produces("application/json")
                .to("direct:registerUser");

        // Маршрут для обработки регистрации пользователя
        from("direct:registerUser")
                .doTry()
                .process(exchange -> {
                    String body = exchange.getIn().getBody(String.class);
                    String contentType = exchange.getMessage().getHeader("Content-Type", String.class);
                    log.info("Received registration request: body={}, contentType={}", body, contentType);
                    // Проверка, что тело запроса не пустое
                    if (body == null || body.trim().isEmpty()) {
                        throw new IllegalArgumentException("Request body is empty");
                    }
                    try {
                        // Десериализация JSON в объект ChatUser
                        ChatUser user = objectMapper.readValue(body, ChatUser.class);
                        // Валидация данных пользователя
                        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
                            throw new IllegalArgumentException("Missing username");
                        }
                        if (user.getPassword() == null || user.getPassword().trim().isEmpty()) {
                            throw new IllegalArgumentException("Missing password");
                        }
                        if (user.getEmail() != null && user.getEmail().trim().isEmpty()) {
                            throw new IllegalArgumentException("Email cannot be empty if provided");
                        }
                        // Проверка уникальности имени пользователя
                        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
                            throw new IllegalArgumentException("Username already registered");
                        }
                        // Проверка уникальности email (если предоставлен)
                        if (user.getEmail() != null && userRepository.findByEmail(user.getEmail()).isPresent()) {
                            throw new IllegalArgumentException("Email already registered");
                        }
                        // Генерация уникального ID и шифрование пароля
                        user.setId(UUID.randomUUID().toString());
                        user.setPassword(passwordEncoder.encode(user.getPassword()));
                        exchange.getIn().setBody(user);
                    } catch (Exception e) {
                        log.error("JSON parsing or authentication failed: {}", e.getMessage(), e);
                        throw new IllegalArgumentException("Invalid JSON or validation failed: " + e.getMessage());
                    }
                })
                .to("jpa:ru.top.server.model.ChatUser") // Сохранение пользователя в базе данных через JPA
                // Формирование ответа об успешной регистрации
                .setBody(simple("{\"message\":\"User registered successfully\",\"id\":\"${body.id}\",\"username\":\"${body.username}\",\"email\":\"${body.email}\"}"))
                .doCatch(Exception.class)
                .process(exchange -> {
                    Exception exception = exchange.getProperty("CamelExceptionCaught", Exception.class);
                    log.error("Failed to register user: {}", exception.getMessage(), exception);
                    exchange.getMessage().setBody("{\"error\":\"" + exception.getMessage() + "\"}");
                    exchange.getMessage().setHeader("Content-Type", "application/json");
                    exchange.getMessage().setHeader("CamelHttpResponseCode", 400);
                })
                .end();

        // Эндпоинт для получения информации о пользователе по ID (GET /api/users/{userId})
        rest("/api/users/{userId}")
                .get()
                .produces("application/json")
                .to("direct:getUser");

        // Маршрут для обработки запроса информации о пользователе
        from("direct:getUser")
                .doTry()
                .process(exchange -> {
                    String userId = exchange.getMessage().getHeader("userId", String.class);
                    log.info("Fetching user with ID: {}", userId);
                    // Поиск пользователя по ID
                    ChatUser user = userRepository.findById(userId)
                            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
                    // Формирование ответа с данными пользователя
                    Map<String, Object> userMap = new HashMap<>();
                    userMap.put("id", user.getId());
                    userMap.put("username", user.getUsername());
                    userMap.put("birthdate", user.getBirthdate() != null ? user.getBirthdate().toString() : null);
                    userMap.put("email", user.getEmail());
                    userMap.put("phone", user.getPhone());
                    userMap.put("avatarUrl", user.getAvatarUrl());
                    String json = objectMapper.writeValueAsString(userMap);
                    exchange.getIn().setBody(json);
                    exchange.getMessage().setHeader("Content-Type", "application/json");
                })
                .doCatch(Exception.class)
                .process(exchange -> {
                    Exception exception = exchange.getProperty("CamelExceptionCaught", Exception.class);
                    log.error("Failed to fetch user: {}", exception.getMessage(), exception);
                    exchange.getMessage().setBody("{\"error\":\"" + exception.getMessage() + "\"}");
                    exchange.getMessage().setHeader("Content-Type", "application/json");
                    exchange.getMessage().setHeader("CamelHttpResponseCode", 404); // Код ошибки 404 для ненайденного пользователя
                })
                .end();

        // Эндпоинт для создания группы (POST /api/groups/create)
        rest("/api/groups/create")
                .post()
                .consumes("application/json")
                .produces("application/json")
                .to("direct:group");

        // Маршрут для обработки создания группы
        from("direct:group")
                .doTry()
                .process(exchange -> {
                    String body = exchange.getIn().getBody(String.class);
                    String contentType = exchange.getMessage().getHeader("Content-Type", String.class);
                    log.info("Received group creation request: body={}, Content-Type={}", body, contentType);
                    // Проверка, что тело запроса не пустое
                    if (body == null || body.trim().isEmpty()) {
                        throw new IllegalArgumentException("Request body is empty");
                    }
                    try {
                        // Десериализация JSON в объект ChatGroup
                        ChatGroup group = objectMapper.readValue(body, ChatGroup.class);
                        // Валидация имени группы
                        if (group.getName() == null || group.getName().trim().isEmpty()) {
                            throw new IllegalArgumentException("Missing group name");
                        }
                        // Проверка уникальности имени группы
                        if (groupRepository.findByName(group.getName()).isPresent()) {
                            throw new Exception("Group name already exists");
                        }
                        // Генерация уникального ID для группы
                        group.setId(UUID.randomUUID().toString());
                        exchange.getIn().setBody(group);
                    } catch (Exception e) {
                        log.error("JSON parsing or validation error: {}", e.getMessage(), e);
                        throw new IllegalArgumentException("Invalid JSON or validation failed: " + e.getMessage());
                    }
                })
                .to("jpa:ru.top.server.model.ChatGroup") // Сохранение группы в базе данных через JPA
                // Формирование ответа об успешном создании группы
                .setBody(simple("{\"message\":\"Group created successfully\",\"id\":\"${body.id}\",\"name\":\"${body.name}\"}"))
                .doCatch(Exception.class)
                .process(exchange -> {
                    Exception exception = exchange.getProperty("CamelExceptionCaught", Exception.class);
                    log.error("Failed to create group: {}", exception.getMessage(), exception);
                    exchange.getMessage().setBody("{\"error\":\"" + exception.getMessage() + "\"}");
                    exchange.getMessage().setHeader("Content-Type", "application/json");
                    exchange.getMessage().setHeader("CamelHttpResponseCode", 400);
                })
                .end();

        // Эндпоинт для получения списка всех пользователей (GET /api/users)
        rest("/api/users")
                .get()
                .produces("application/json")
                .to("direct:getUsers");

        // Маршрут для обработки запроса списка пользователей
        from("direct:getUsers")
                .doTry()
                .process(exchange -> {
                    log.info("Fetching all users");
                    // Получение всех пользователей из репозитория
                    List<ChatUser> users = userRepository.findAll();
                    // Формирование списка пользователей с минимальной информацией (ID и имя)
                    List<Map<String, String>> userList = users.stream()
                            .map(user -> {
                                Map<String, String> userMap = new HashMap<>();
                                userMap.put("id", user.getId());
                                userMap.put("username", user.getUsername());
                                return userMap;
                            })
                            .collect(Collectors.toList());
                    // Сериализация списка в JSON
                    String json = objectMapper.writeValueAsString(userList);
                    exchange.getIn().setBody(json);
                    exchange.getMessage().setHeader("Content-Type", "application/json");
                    log.info("Retrieved {} users", users.size());
                })
                .doCatch(Exception.class)
                .process(exchange -> {
                    Exception exception = exchange.getProperty("CamelExceptionCaught", Exception.class);
                    log.error("Failed to fetch users: {}", exception.getMessage(), exception);
                    exchange.getMessage().setBody("{\"error\":\"" + exception.getMessage() + "\"}");
                    exchange.getMessage().setHeader("Content-Type", "application/json");
                    exchange.getMessage().setHeader("CamelHttpResponseCode", 500);
                })
                .end();

        // Эндпоинт для получения списка всех групп (GET /api/groups)
        rest("/api/groups")
                .get()
                .produces("application/json")
                .to("direct:getGroups");

        // Маршрут для обработки запроса списка групп
        from("direct:getGroups")
                .doTry()
                .process(exchange -> {
                    log.info("Fetching all groups");
                    // Получение всех групп из репозитория
                    List<ChatGroup> groups = groupRepository.findAll();
                    // Формирование списка групп с минимальной информацией (ID и имя)
                    List<Map<String, String>> groupList = groups.stream()
                            .map(group -> {
                                Map<String, String> groupMap = new HashMap<>();
                                groupMap.put("id", group.getId());
                                groupMap.put("name", group.getName());
                                return groupMap;
                            })
                            .collect(Collectors.toList());
                    // Сериализация списка в JSON
                    String json = objectMapper.writeValueAsString(groupList);
                    exchange.getIn().setBody(json);
                    exchange.getMessage().setHeader("Content-Type", "application/json");
                    log.info("Retrieved {} groups", groups.size());
                })
                .doCatch(Exception.class)
                .process(exchange -> {
                    Exception exception = exchange.getProperty("CamelExceptionCaught", Exception.class);
                    log.error("Failed to fetch groups: {}", exception.getMessage(), exception);
                    exchange.getMessage().setBody("{\"error\":\"" + exception.getMessage() + "\"}");
                    exchange.getMessage().setHeader("Content-Type", "application/json");
                    exchange.getMessage().setHeader("CamelHttpResponseCode", 500);
                })
                .end();

        // Эндпоинт для отправки личного сообщения (POST /api/messages/private)
        rest("/api/messages/private")
                .post()
                .consumes("application/json")
                .produces("application/json")
                .to("direct:sendPrivateMessage");

        // Маршрут для обработки отправки личного сообщения
        from("direct:sendPrivateMessage")
                .doTry()
                .process(exchange -> {
                    String body = exchange.getIn().getBody(String.class);
                    log.info("Processing private message request: {}", body);
                    // Десериализация JSON в объект Message
                    Message message = objectMapper.readValue(body, Message.class);
                    // Валидация обязательных полей сообщения
                    if (message.getContent() == null || message.getSenderId() == null || message.getRecipientId() == null) {
                        throw new IllegalArgumentException("Invalid message JSON: missing content, senderId, or recipientId");
                    }
                    // Получение информации об аутентифицированном пользователе
                    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                    String username = auth != null ? auth.getName() : null;
                    log.info("Authenticated user: {}", username);
                    if (username == null) {
                        throw new IllegalArgumentException("No authenticated user found");
                    }
                    // Проверка отправителя
                    ChatUser sender = userRepository.findByUsername(username)
                            .orElseThrow(() -> new IllegalArgumentException("Sender not found: " + username));
                    log.info("Sender ID: {}, Message Sender ID: {}", sender.getId(), message.getSenderId());
                    if (!message.getSenderId().equals(sender.getId())) {
                        log.error("Sender ID mismatch: expected {}, got {}", sender.getId(), message.getSenderId());
                        throw new IllegalArgumentException("Sender ID does not match authenticated user");
                    }
                    // Проверка получателя
                    ChatUser recipient = userRepository.findById(message.getRecipientId())
                            .orElseThrow(() -> new IllegalArgumentException("Recipient not found: " + message.getRecipientId()));
                    // Установка параметров сообщения
                    message.setSender(sender);
                    message.setRecipient(recipient);
                    message.setId(UUID.randomUUID().toString());
                    message.setChatType("PRIVATE");
                    message.setTimestamp(LocalDateTime.now());
                    exchange.getIn().setBody(message);
                    exchange.setProperty("message", message);
                })
                .to("jpa:ru.top.server.model.Message") // Сохранение сообщения в базе данных через JPA
                // Формирование ответа об успешной отправке
                .setBody(simple("{\"message\":\"Message sent successfully\"}"))
                .doCatch(Exception.class)
                .process(exchange -> {
                    Exception exception = exchange.getProperty("CamelExceptionCaught", Exception.class);
                    log.error("Failed to send private message: {}", exception.getMessage(), exception);
                    exchange.getMessage().setBody("{\"error\":\"" + exception.getMessage() + "\"}");
                    exchange.getMessage().setHeader("Content-Type", "application/json");
                    exchange.getMessage().setHeader("CamelHttpResponseCode", 400);
                })
                .end();

        // Эндпоинт для отправки сообщения в группу (POST /api/messages/group)
        rest("/api/messages/group")
                .post()
                .consumes("application/json")
                .produces("application/json")
                .to("direct:sendGroupMessage");

        // Маршрут для обработки отправки сообщения в группу
        from("direct:sendGroupMessage")
                .doTry()
                .process(exchange -> {
                    String body = exchange.getIn().getBody(String.class);
                    log.info("Processing group message request: {}", body);
                    // Десериализация JSON в объект Message
                    Message message = objectMapper.readValue(body, Message.class);
                    // Валидация обязательных полей сообщения
                    if (message.getContent() == null || message.getSenderId() == null || message.getGroupId() == null) {
                        throw new IllegalArgumentException("Invalid message JSON: missing content, senderId, or groupId");
                    }
                    // Получение информации об аутентифицированном пользователе
                    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                    String username = auth != null ? auth.getName() : null;
                    log.info("Authenticated user: {}", username);
                    if (username == null) {
                        throw new IllegalArgumentException("No authenticated user found");
                    }
                    // Проверка отправителя
                    ChatUser sender = userRepository.findByUsername(username)
                            .orElseThrow(() -> new IllegalArgumentException("Sender not found: " + username));
                    log.info("Sender ID: {}, Message Sender ID: {}", sender.getId(), message.getSenderId());
                    if (!message.getSenderId().equals(sender.getId())) {
                        log.error("Sender ID mismatch: expected {}, got {}", sender.getId(), message.getSenderId());
                        throw new IllegalArgumentException("Sender ID does not match authenticated user");
                    }
                    // Проверка группы
                    ChatGroup group = groupRepository.findById(message.getGroupId())
                            .orElseThrow(() -> new IllegalArgumentException("Group not found: " + message.getGroupId()));
                    // Установка параметров сообщения
                    message.setSender(sender);
                    message.setGroup(group);
                    message.setId(UUID.randomUUID().toString());
                    message.setChatType("GROUP");
                    message.setTimestamp(LocalDateTime.now());
                    exchange.getIn().setBody(message);
                    exchange.setProperty("message", message);
                })
                .to("jpa:ru.top.server.model.Message") // Сохранение сообщения в базе данных через JPA
                // Формирование ответа об успешной отправке
                .setBody(simple("{\"message\":\"Message sent successfully\"}"))
                .doCatch(Exception.class)
                .process(exchange -> {
                    Exception exception = exchange.getProperty("CamelExceptionCaught", Exception.class);
                    log.error("Failed to send group message: {}", exception.getMessage(), exception);
                    exchange.getMessage().setBody("{\"error\":\"" + exception.getMessage() + "\"}");
                    exchange.getMessage().setHeader("Content-Type", "application/json");
                    exchange.getMessage().setHeader("CamelHttpResponseCode", 400);
                })
                .end();

        // Эндпоинт для получения сообщений в личной переписке (GET /api/messages/private/conversation/{otherUserId})
        rest("/api/messages/private/conversation/{otherUserId}")
                .get()
                .produces("application/json")
                .to("direct:privateMessages");

        // Маршрут для обработки запроса сообщений личной переписки
        from("direct:privateMessages")
                .doTry()
                .process(exchange -> {
                    String otherUserId = exchange.getMessage().getHeader("otherUserId", String.class);
                    String sinceParam = exchange.getMessage().getHeader("since", String.class);
                    log.info("Fetching new private messages for conversation with otherUserId: {}, since: {}", otherUserId, sinceParam);
                    // Подготовка параметров для JPA-запроса
                    Map<String, Object> parameters = new HashMap<>();
                    parameters.put("otherUserId", otherUserId);
                    LocalDateTime since;
                    // Обработка параметра времени (since)
                    if (sinceParam != null && !sinceParam.isEmpty()) {
                        try {
                            since = LocalDateTime.parse(sinceParam, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                        } catch (DateTimeParseException e) {
                            log.error("Invalid 'since' timestamp format: {}", sinceParam);
                            throw new IllegalArgumentException("Invalid 'since' timestamp format: " + sinceParam);
                        }
                    } else {
                        since = LocalDateTime.now().minusHours(24); // По умолчанию за последние 24 часа
                    }
                    parameters.put("since", since);
                    // Проверка аутентифицированного пользователя
                    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                    String username = auth != null ? auth.getName() : null;
                    log.info("Authenticated user: {}", username);
                    if (username == null) {
                        throw new IllegalArgumentException("No authenticated user found");
                    }
                    // Проверка существования пользователей
                    ChatUser user = userRepository.findByUsername(username)
                            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
                    ChatUser otherUser = userRepository.findById(otherUserId)
                            .orElseThrow(() -> new IllegalArgumentException("Other user not found: " + otherUserId));
                    parameters.put("userId", user.getId());
                    exchange.getIn().setHeader("CamelJpaParameters", parameters);
                })
                .to("jpa:ru.top.server.model.Message?namedQuery=Message.findConversationMessages") // Выполнение JPA-запроса для получения сообщений
                .process(exchange -> {
                    // Обработка результатов запроса
                    List<Message> messages = exchange.getIn().getBody(List.class);
                    String otherUserId = exchange.getMessage().getHeader("otherUserId", String.class);
                    log.info("Retrieved {} private messages for conversation with otherUserId: {}", messages != null ? messages.size() : 0, otherUserId);
                    // Сериализация сообщений в JSON
                    String json = objectMapper.writeValueAsString(messages != null ? messages : List.of());
                    exchange.getIn().setBody(json);
                    exchange.getMessage().setHeader("Content-Type", "application/json");
                })
                .doCatch(Exception.class)
                .process(exchange -> {
                    Exception exception = exchange.getProperty("CamelExceptionCaught", Exception.class);
                    log.error("Failed to fetch private messages: {}", exception.getMessage(), exception);
                    exchange.getMessage().setBody("{\"error\":\"" + exception.getMessage() + "\"}");
                    exchange.getMessage().setHeader("Content-Type", "application/json");
                    // Установка кода ошибки в зависимости от типа
                    int statusCode = exception.getMessage().contains("SQLITE_ERROR") ? 500 : 400;
                    exchange.getMessage().setHeader("CamelHttpResponseCode", statusCode);
                })
                .end();

        // Эндпоинт для получения истории личной переписки (GET /api/messages/private/history/{otherUserId})
        rest("/api/messages/private/history/{otherUserId}")
                .get()
                .produces("application/json")
                .to("direct:privateChatHistory");

        // Маршрут для обработки запроса истории личной переписки
        from("direct:privateChatHistory")
                .doTry()
                .process(exchange -> {
                    String otherUserId = exchange.getMessage().getHeader("otherUserId", String.class);
                    log.info("Fetching chat history with otherUserId: {}", otherUserId);
                    // Проверка аутентифицированного пользователя
                    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                    String username = auth != null ? auth.getName() : null;
                    log.info("Authenticated user: {}", username);
                    if (username == null) {
                        throw new IllegalArgumentException("No authenticated user found");
                    }
                    // Проверка существования пользователей
                    ChatUser user = userRepository.findByUsername(username)
                            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
                    ChatUser otherUser = userRepository.findById(otherUserId)
                            .orElseThrow(() -> new IllegalArgumentException("Other user not found: " + otherUserId));
                    // Подготовка параметров для JPA-запроса
                    Map<String, Object> parameters = new HashMap<>();
                    parameters.put("userId", user.getId());
                    parameters.put("otherUserId", otherUserId);
                    exchange.getIn().setHeader("CamelJpaParameters", parameters);
                })
                .to("jpa:ru.top.server.model.Message?namedQuery=Message.findChatHistory") // Выполнение JPA-запроса для получения истории
                .process(exchange -> {
                    // Обработка результатов запроса
                    List<Message> messages = exchange.getIn().getBody(List.class);
                    String otherUserId = exchange.getMessage().getHeader("otherUserId", String.class);
                    log.info("Retrieved {} messages for chat history with otherUserId: {}", messages != null ? messages.size() : 0, otherUserId);
                    // Сериализация сообщений в JSON
                    String json = objectMapper.writeValueAsString(messages != null ? messages : List.of());
                    exchange.getIn().setBody(json);
                    exchange.getMessage().setHeader("Content-Type", "application/json");
                })
                .doCatch(Exception.class)
                .process(exchange -> {
                    Exception exception = exchange.getProperty("CamelExceptionCaught", Exception.class);
                    log.error("Failed to fetch chat history: {}", exception.getMessage(), exception);
                    exchange.getMessage().setBody("{\"error\":\"" + exception.getMessage() + "\"}");
                    exchange.getMessage().setHeader("Content-Type", "application/json");
                    int statusCode = exception.getMessage().contains("SQLITE_ERROR") ? 500 : 400;
                    exchange.getMessage().setHeader("CamelHttpResponseCode", statusCode);
                })
                .end();

        // Эндпоинт для получения сообщений группы (GET /api/messages/group/{groupId})
        rest("/api/messages/group/{groupId}")
                .get()
                .produces("application/json")
                .to("direct:groupMessages");

        // Маршрут для обработки запроса сообщений группы
        from("direct:groupMessages")
                .doTry()
                .process(exchange -> {
                    String groupId = exchange.getMessage().getHeader("groupId", String.class);
                    log.info("Fetching group messages for groupId: {}", groupId);
                    // Подготовка параметров для JPA-запроса
                    Map<String, Object> parameters = new HashMap<>();
                    parameters.put("groupId", groupId);
                    exchange.getIn().setHeader("CamelJpaParameters", parameters);
                })
                .to("jpa:ru.top.server.model.Message?namedQuery=Message.findByGroupId") // Выполнение JPA-запроса для получения сообщений группы
                .process(exchange -> {
                    // Обработка результатов запроса
                    List<Message> messages = exchange.getIn().getBody(List.class);
                    log.info("Retrieved {} messages for groupId: {}", messages != null ? messages.size() : 0, exchange.getMessage().getHeader("groupId"));
                    // Сериализация сообщений в JSON
                    String json = objectMapper.writeValueAsString(messages != null ? messages : List.of());
                    exchange.getIn().setBody(json);
                    exchange.getMessage().setHeader("Content-Type", "application/json");
                })
                .doCatch(Exception.class)
                .process(exchange -> {
                    Exception exception = exchange.getProperty("CamelExceptionCaught", Exception.class);
                    log.error("Failed to fetch group messages: {}", exception.getMessage(), exception);
                    exchange.getMessage().setBody("{\"error\":\"" + exception.getMessage() + "\"}");
                    exchange.getMessage().setHeader("Content-Type", "application/json");
                    exchange.getMessage().setHeader("CamelHttpResponseCode", 500);
                })
                .end();

        // Эндпоинт для поиска сообщений (GET /api/messages/search)
        rest("/api/messages/search")
                .get()
                .produces("application/json")
                .to("direct:searchMessages");

        // Маршрут для обработки поиска сообщений
        from("direct:searchMessages")
                .doTry()
                .process(exchange -> {
                    // Получение параметров запроса
                    String keyword = exchange.getMessage().getHeader("keyword", String.class);
                    String startParam = exchange.getMessage().getHeader("start", String.class);
                    String endParam = exchange.getMessage().getHeader("end", String.class);
                    log.info("Searching messages with keyword: {}, start: {}, end: {}", keyword, startParam, endParam);

                    // Проверка аутентифицированного пользователя
                    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                    String username = auth != null ? auth.getName() : null;
                    log.info("Authenticated user: {}", username);
                    if (username == null) {
                        throw new IllegalArgumentException("No authenticated user found");
                    }
                    ChatUser user = userRepository.findByUsername(username)
                            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

                    // Подготовка параметров для JPA-запроса
                    Map<String, Object> parameters = new HashMap<>();
                    parameters.put("userId", user.getId());
                    // Обработка ключевого слова для поиска
                    if (keyword != null && !keyword.trim().isEmpty()) {
                        parameters.put("keyword", "%" + keyword.trim() + "%");
                    } else {
                        parameters.put("keyword", null);
                    }
                    // Обработка временных границ
                    if (startParam != null && !startParam.isEmpty()) {
                        try {
                            LocalDateTime start = LocalDateTime.parse(startParam, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                            parameters.put("start", start);
                        } catch (DateTimeParseException e) {
                            log.error("Invalid 'start' timestamp format: {}", startParam);
                            throw new IllegalArgumentException("Invalid 'start' timestamp format: " + startParam);
                        }
                    } else {
                        parameters.put("start", null);
                    }
                    if (endParam != null && !endParam.isEmpty()) {
                        try {
                            LocalDateTime end = LocalDateTime.parse(endParam, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                            parameters.put("end", end);
                        } catch (DateTimeParseException e) {
                            log.error("Invalid 'end' timestamp format: {}", endParam);
                            throw new IllegalArgumentException("Invalid 'end' timestamp format: " + endParam);
                        }
                    } else {
                        parameters.put("end", null);
                    }
                    exchange.getIn().setHeader("CamelJpaParameters", parameters);
                    exchange.getIn().setBody(null);
                })
                .to("jpa:ru.top.server.model.Message?namedQuery=Message.searchMessages&resultClass=java.util.List") // Выполнение JPA-запроса для поиска сообщений
                .process(exchange -> {
                    // Обработка результатов запроса
                    List<Message> messages = exchange.getIn().getBody(List.class);
                    log.info("Retrieved {} messages for search", messages != null ? messages.size() : 0);
                    // Сериализация сообщений в JSON
                    String json = objectMapper.writeValueAsString(messages != null ? messages : List.of());
                    exchange.getIn().setBody(json);
                    exchange.getMessage().setHeader("Content-Type", "application/json");
                })
                .doCatch(Exception.class)
                .process(exchange -> {
                    Exception exception = exchange.getProperty("CamelExceptionCaught", Exception.class);
                    log.error("Failed to search messages: {}", exception.getMessage(), exception);
                    exchange.getMessage().setBody("{\"error\":\"" + exception.getMessage() + "\"}");
                    exchange.getMessage().setHeader("Content-Type", "application/json");
                    int statusCode = exception.getMessage().contains("SQLITE_ERROR") ? 500 : 400;
                    exchange.getMessage().setHeader("CamelHttpResponseCode", statusCode);
                })
                .end();

        // Эндпоинт для получения количества пользователей (GET /api/users/count)
        rest("/api/users/count")
                .get()
                .produces("application/json")
                .to("direct:userCount");

        // Маршрут для обработки запроса количества пользователей
        from("direct:userCount")
                .doTry()
                .process(exchange -> {
                    log.info("Fetching total user count");
                    // Получение общего количества пользователей из репозитория
                    long count = userRepository.count();
                    // Формирование JSON-ответа с количеством пользователей
                    String json = objectMapper.writeValueAsString(Map.of("count", count));
                    exchange.getIn().setBody(json);
                    exchange.getMessage().setHeader("Content-Type", "application/json");
                })
                .doCatch(Exception.class)
                .process(exchange -> {
                    Exception exception = exchange.getProperty("CamelExceptionCaught", Exception.class);
                    log.error("Failed to fetch user count: {}", exception.getMessage(), exception);
                    exchange.getMessage().setBody("{\"error\":\"" + exception.getMessage() + "\"}");
                    exchange.getMessage().setHeader("Content-Type", "application/json");
                    exchange.getMessage().setHeader("CamelHttpResponseCode", 500);
                })
                .end();
    }
}