package ru.top.server.route;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import ru.top.server.config.RouteErrorHandler;
import ru.top.server.model.ChatUser;
import ru.top.server.repository.ChatUserRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Класс для маршрутов, связанных с пользователями
@Component
public class UserRoute extends RouteBuilder {
    private static final Logger log = LoggerFactory.getLogger(UserRoute.class);

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ChatUserRepository userRepository;

    @Autowired
    private RouteErrorHandler errorHandler;

    @Override
    public void configure() {
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
                    ChatUser user = userRepository.findById(userId)
                            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
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
                .process(exchange -> errorHandler.handleError(exchange, log, 404))
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
                    List<ChatUser> users = userRepository.findAll();
                    List<Map<String, String>> userList = users.stream()
                            .map(user -> {
                                Map<String, String> userMap = new HashMap<>();
                                userMap.put("id", user.getId());
                                userMap.put("username", user.getUsername());
                                return userMap;
                            })
                            .collect(Collectors.toList());
                    String json = objectMapper.writeValueAsString(userList);
                    exchange.getIn().setBody(json);
                    exchange.getMessage().setHeader("Content-Type", "application/json");
                    log.info("Retrieved {} users", users.size());
                })
                .doCatch(Exception.class)
                .process(exchange -> errorHandler.handleError(exchange, log, 500))
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
                    long count = userRepository.count();
                    String json = objectMapper.writeValueAsString(Map.of("count", count));
                    exchange.getIn().setBody(json);
                    exchange.getMessage().setHeader("Content-Type", "application/json");
                })
                .doCatch(Exception.class)
                .process(exchange -> errorHandler.handleError(exchange, log, 500))
                .end();

        // Эндпоинт для получения ID текущего пользователя (GET /api/users/me)
        rest("/api/users/myInfo")
                .get()
                .produces("application/json")
                .to("direct:getCurrentUserId");

        // Маршрут для обработки запроса ID текущего пользователя
        from("direct:getCurrentUserId")
                .doTry()
                .process(exchange -> {
                    String username = SecurityContextHolder.getContext().getAuthentication().getName();
                    log.info("Fetching ID for authenticated user: {}", username);
                    if (username == null) {
                        throw new IllegalArgumentException("No authenticated user found");
                    }
                    ChatUser user = userRepository.findByUsername(username)
                            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
                    String json = objectMapper.writeValueAsString(Map.of("id", user.getId()));
                    exchange.getIn().setBody(json);
                    exchange.getMessage().setHeader("Content-Type", "application/json");
                })
                .doCatch(Exception.class)
                .process(exchange -> errorHandler.handleError(exchange, log, 400))
                .end();
    }
}