package ru.top.server.route;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import ru.top.server.config.RouteErrorHandler;
import ru.top.server.model.ChatGroup;
import ru.top.server.model.ChatUser;
import ru.top.server.model.Message;
import ru.top.server.repository.ChatGroupRepository;
import ru.top.server.repository.ChatUserRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Класс для маршрутов, связанных с сообщениями
@Component
public class MessageRoute extends RouteBuilder {
    private static final Logger log = LoggerFactory.getLogger(MessageRoute.class);

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ChatUserRepository userRepository;

    @Autowired
    private ChatGroupRepository groupRepository;

    @Autowired
    private RouteErrorHandler errorHandler;

    @Override
    public void configure() {
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
                    Message message = objectMapper.readValue(body, Message.class);
                    if (message.getContent() == null || message.getSenderId() == null || message.getRecipientId() == null) {
                        throw new IllegalArgumentException("Invalid message JSON: missing content, senderId, or recipientId");
                    }
                    String username = SecurityContextHolder.getContext().getAuthentication().getName();
                    log.info("Authenticated user: {}", username);
                    if (username == null) {
                        throw new IllegalArgumentException("No authenticated user found");
                    }
                    ChatUser sender = userRepository.findByUsername(username)
                            .orElseThrow(() -> new IllegalArgumentException("Sender not found: " + username));
                    log.info("Sender ID: {}, Message Sender ID: {}", sender.getId(), message.getSenderId());
                    if (!message.getSenderId().equals(sender.getId())) {
                        log.error("Sender ID mismatch: expected {}, got {}", sender.getId(), message.getSenderId());
                        throw new IllegalArgumentException("Sender ID does not match authenticated user");
                    }
                    ChatUser recipient = userRepository.findById(message.getRecipientId())
                            .orElseThrow(() -> new IllegalArgumentException("Recipient not found: " + message.getRecipientId()));
                    message.setSender(sender);
                    message.setRecipient(recipient);
                    message.setId(UUID.randomUUID().toString());
                    message.setChatType("PRIVATE");
                    message.setTimestamp(LocalDateTime.now());
                    exchange.getIn().setBody(message);
                    exchange.setProperty("message", message);
                })
                .to("jpa:ru.top.server.model.Message")
                .setBody(simple("{\"message\":\"Message sent successfully\"}"))
                .doCatch(Exception.class)
                .process(exchange -> errorHandler.handleError(exchange, log, 400))
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
                    Message message = objectMapper.readValue(body, Message.class);
                    if (message.getContent() == null || message.getSenderId() == null || message.getGroupId() == null) {
                        throw new IllegalArgumentException("Invalid message JSON: missing content, senderId, or groupId");
                    }
                    String username = SecurityContextHolder.getContext().getAuthentication().getName();
                    log.info("Authenticated user: {}", username);
                    if (username == null) {
                        throw new IllegalArgumentException("No authenticated user found");
                    }
                    ChatUser sender = userRepository.findByUsername(username)
                            .orElseThrow(() -> new IllegalArgumentException("Sender not found: " + username));
                    log.info("Sender ID: {}, Message Sender ID: {}", sender.getId(), message.getSenderId());
                    if (!message.getSenderId().equals(sender.getId())) {
                        log.error("Sender ID mismatch: expected {}, got {}", sender.getId(), message.getSenderId());
                        throw new IllegalArgumentException("Sender ID does not match authenticated user");
                    }
                    ChatGroup group = groupRepository.findById(message.getGroupId())
                            .orElseThrow(() -> new IllegalArgumentException("Group not found: " + message.getGroupId()));
                    message.setSender(sender);
                    message.setGroup(group);
                    message.setId(UUID.randomUUID().toString());
                    message.setChatType("GROUP");
                    message.setTimestamp(LocalDateTime.now());
                    exchange.getIn().setBody(message);
                    exchange.setProperty("message", message);
                })
                .to("jpa:ru.top.server.model.Message")
                .setBody(simple("{\"message\":\"Message sent successfully\"}"))
                .doCatch(Exception.class)
                .process(exchange -> errorHandler.handleError(exchange, log, 400))
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
                    Map<String, Object> parameters = new HashMap<>();
                    parameters.put("otherUserId", otherUserId);
                    LocalDateTime since;
                    if (sinceParam != null && !sinceParam.isEmpty()) {
                        try {
                            since = LocalDateTime.parse(sinceParam, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                        } catch (DateTimeParseException e) {
                            log.error("Invalid 'since' timestamp format: {}", sinceParam);
                            throw new IllegalArgumentException("Invalid 'since' timestamp format: " + sinceParam);
                        }
                    } else {
                        since = LocalDateTime.now().minusHours(24);
                    }
                    parameters.put("since", since);
                    String username = SecurityContextHolder.getContext().getAuthentication().getName();
                    log.info("Authenticated user: {}", username);
                    if (username == null) {
                        throw new IllegalArgumentException("No authenticated user found");
                    }
                    ChatUser user = userRepository.findByUsername(username)
                            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
                    ChatUser otherUser = userRepository.findById(otherUserId)
                            .orElseThrow(() -> new IllegalArgumentException("Other user not found: " + otherUserId));
                    parameters.put("userId", user.getId());
                    exchange.getIn().setHeader("CamelJpaParameters", parameters);
                })
                .to("jpa:ru.top.server.model.Message?namedQuery=Message.findConversationMessages")
                .process(exchange -> {
                    List<Message> messages = exchange.getIn().getBody(List.class);
                    String otherUserId = exchange.getMessage().getHeader("otherUserId", String.class);
                    log.info("Retrieved {} private messages for conversation with otherUserId: {}", messages != null ? messages.size() : 0, otherUserId);
                    String json = objectMapper.writeValueAsString(messages != null ? messages : List.of());
                    exchange.getIn().setBody(json);
                    exchange.getMessage().setHeader("Content-Type", "application/json");
                })
                .doCatch(Exception.class)
                .process(exchange -> errorHandler.handleError(exchange, log, 400))
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
                    String username = SecurityContextHolder.getContext().getAuthentication().getName();
                    log.info("Authenticated user: {}", username);
                    if (username == null) {
                        throw new IllegalArgumentException("No authenticated user found");
                    }
                    ChatUser user = userRepository.findByUsername(username)
                            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
                    ChatUser otherUser = userRepository.findById(otherUserId)
                            .orElseThrow(() -> new IllegalArgumentException("Other user not found: " + otherUserId));
                    Map<String, Object> parameters = new HashMap<>();
                    parameters.put("userId", user.getId());
                    parameters.put("otherUserId", otherUserId);
                    exchange.getIn().setHeader("CamelJpaParameters", parameters);
                })
                .to("jpa:ru.top.server.model.Message?namedQuery=Message.findChatHistory")
                .process(exchange -> {
                    List<Message> messages = exchange.getIn().getBody(List.class);
                    String otherUserId = exchange.getMessage().getHeader("otherUserId", String.class);
                    log.info("Retrieved {} messages for chat history with otherUserId: {}", messages != null ? messages.size() : 0, otherUserId);
                    String json = objectMapper.writeValueAsString(messages != null ? messages : List.of());
                    exchange.getIn().setBody(json);
                    exchange.getMessage().setHeader("Content-Type", "application/json");
                })
                .doCatch(Exception.class)
                .process(exchange -> errorHandler.handleError(exchange, log, 400))
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
                    Map<String, Object> parameters = new HashMap<>();
                    parameters.put("groupId", groupId);
                    exchange.getIn().setHeader("CamelJpaParameters", parameters);
                })
                .to("jpa:ru.top.server.model.Message?namedQuery=Message.findByGroupId")
                .process(exchange -> {
                    List<Message> messages = exchange.getIn().getBody(List.class);
                    log.info("Retrieved {} messages for groupId: {}", messages != null ? messages.size() : 0, exchange.getMessage().getHeader("groupId"));
                    String json = objectMapper.writeValueAsString(messages != null ? messages : List.of());
                    exchange.getIn().setBody(json);
                    exchange.getMessage().setHeader("Content-Type", "application/json");
                })
                .doCatch(Exception.class)
                .process(exchange -> errorHandler.handleError(exchange, log, 500))
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
                    String keyword = exchange.getMessage().getHeader("keyword", String.class);
                    String startParam = exchange.getMessage().getHeader("start", String.class);
                    String endParam = exchange.getMessage().getHeader("end", String.class);
                    log.info("Searching messages with keyword: {}, start: {}, end: {}", keyword, startParam, endParam);
                    String username = SecurityContextHolder.getContext().getAuthentication().getName();
                    log.info("Authenticated user: {}", username);
                    if (username == null) {
                        throw new IllegalArgumentException("No authenticated user found");
                    }
                    ChatUser user = userRepository.findByUsername(username)
                            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
                    Map<String, Object> parameters = new HashMap<>();
                    parameters.put("userId", user.getId());
                    if (keyword != null && !keyword.trim().isEmpty()) {
                        parameters.put("keyword", "%" + keyword.trim() + "%");
                    } else {
                        parameters.put("keyword", null);
                    }
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
                .to("jpa:ru.top.server.model.Message?namedQuery=Message.searchMessages&resultClass=java.util.List")
                .process(exchange -> {
                    List<Message> messages = exchange.getIn().getBody(List.class);
                    log.info("Retrieved {} messages for search", messages != null ? messages.size() : 0);
                    String json = objectMapper.writeValueAsString(messages != null ? messages : List.of());
                    exchange.getIn().setBody(json);
                    exchange.getMessage().setHeader("Content-Type", "application/json");
                })
                .doCatch(Exception.class)
                .process(exchange -> errorHandler.handleError(exchange, log, 400))
                .end();
    }
}