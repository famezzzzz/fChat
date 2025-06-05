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

@Component
public class ChatRoute extends RouteBuilder {
    private static final Logger log = LoggerFactory.getLogger(ChatRoute.class);


    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ChatUserRepository userRepository;

    @Autowired
    private ChatGroupRepository groupRepository;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserDetailsService userDetailsService;

    @Override
    public void configure() {
        log.info("Configuring Camel REST routes");

        restConfiguration()
                .component("servlet")
                .bindingMode(RestBindingMode.off)
                .dataFormatProperty("prettyPrint", "true");

        rest("/api/auth/login")
                .post()
                .consumes("application/json")
                .produces("application/json")
                .to("direct:login");

        from("direct:login")
                .doTry()
                .process(exchange -> {
                    String body = exchange.getIn().getBody(String.class);
                    log.info("Received login request: {}", body);
                    if (body == null || body.trim().isEmpty()) {
                        throw new IllegalArgumentException("Request body is empty");
                    }
                    LoginRequest loginRequest = null;
                    try {
                        loginRequest = objectMapper.readValue(body, LoginRequest.class);
                        if (loginRequest.username() == null || loginRequest.password().isEmpty()) {
                            throw new IllegalArgumentException("Missing username or password");
                        }
                        log.info("Authenticating user: {}", loginRequest.username());
                        Authentication authentication = authenticationManager.authenticate(
                                new UsernamePasswordAuthenticationToken(
                                        loginRequest.username(), loginRequest.password()));
                        log.info("Authentication successful for user: {}", loginRequest.username());
                        UserDetails userDetails = userDetailsService.loadUserByUsername(loginRequest.username());
                        log.info("Loaded UserDetails: username={}, authorities={}",
                                userDetails.getUsername(), userDetails.getAuthorities());
                        String jwt = jwtUtil.generateToken(userDetails);
                        log.info("Generated JWT for user: {}", loginRequest.username());
                        exchange.getIn().setBody("{\"token\":\"" + jwt + "\"}");
                    } catch (AuthenticationException e) {
                        log.error("Authentication failed for user: {}: {}",
                                loginRequest != null ? loginRequest.username() : "unknown", e.getMessage());
                        throw new IllegalArgumentException("Invalid credentials: " + e.getMessage(), e);
                    } catch (Exception e) {
                        log.error("Error processing login for user: {}: {}",
                                loginRequest != null ? loginRequest.username() : "unknown", e.getMessage());
                        throw new IllegalArgumentException("Invalid JSON or processing error: " + e.getMessage(), e);
                    }
                })
                .doCatch(Exception.class)
                .process(exchange -> {
                    Exception exception = exchange.getProperty("CamelExceptionCaught", Exception.class);
                    log.error("Login failed: {}", exception.getMessage(), exception);
                    String errorMessage = exception.getCause() != null
                            ? exception.getCause().getMessage()
                            : exception.getMessage();
                    exchange.getMessage().setBody("{\"error\":\"" + errorMessage + "\"}");
                    exchange.getMessage().setHeader("Content-Type", "application/json");
                    exchange.getMessage().setHeader("CamelHttpResponseCode", 400);
                })
                .end();

        rest("/api/users/register")
                .post()
                .consumes("application/json")
                .produces("application/json")
                .to("direct:registerUser");

        from("direct:registerUser")
                .doTry()
                .process(exchange -> {
                    String body = exchange.getIn().getBody(String.class);
                    String contentType = exchange.getMessage().getHeader("Content-Type", String.class);
                    log.info("Received registration request: body={}, contentType={}", body, contentType);
                    if (body == null || body.trim().isEmpty()) {
                        throw new IllegalArgumentException("Request body is empty");
                    }
                    try {
                        ChatUser user = objectMapper.readValue(body, ChatUser.class);
                        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
                            throw new IllegalArgumentException("Missing username");
                        }
                        if (user.getPassword() == null || user.getPassword().trim().isEmpty()) {
                            throw new IllegalArgumentException("Missing password");
                        }
                        if (user.getEmail() != null && user.getEmail().trim().isEmpty()) {
                            throw new IllegalArgumentException("Email cannot be empty if provided");
                        }
                        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
                            throw new IllegalArgumentException("Username already registered");
                        }
                        if (user.getEmail() != null && userRepository.findByEmail(user.getEmail()).isPresent()) {
                            throw new IllegalArgumentException("Email already registered");
                        }
                        user.setId(UUID.randomUUID().toString());
                        user.setPassword(passwordEncoder.encode(user.getPassword()));
                        exchange.getIn().setBody(user);
                    } catch (Exception e) {
                        log.error("JSON parsing or authentication failed: {}", e.getMessage(), e);
                        throw new IllegalArgumentException("Invalid JSON or validation failed: " + e.getMessage());
                    }
                })
                .to("jpa:ru.top.server.model.ChatUser")
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

        rest("/api/users/{userId}")
                .get()
                .produces("application/json")
                .to("direct:getUser");

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
                .process(exchange -> {
                    Exception exception = exchange.getProperty("CamelExceptionCaught", Exception.class);
                    log.error("Failed to fetch user: {}", exception.getMessage(), exception);
                    exchange.getMessage().setBody("{\"error\":\"" + exception.getMessage() + "\"}");
                    exchange.getMessage().setHeader("Content-Type", "application/json");
                    exchange.getMessage().setHeader("CamelHttpResponseCode", 404);
                })
                .end();

        rest("/api/groups/create")
                .post()
                .consumes("application/json")
                .produces("application/json")
                .to("direct:group");

        from("direct:group")
                .doTry()
                .process(exchange -> {
                    String body = exchange.getIn().getBody(String.class);
                    String contentType = exchange.getMessage().getHeader("Content-Type", String.class);
                    log.info("Received group creation request: body={}, Content-Type={}", body, contentType);
                    if (body == null || body.trim().isEmpty()) {
                        throw new IllegalArgumentException("Request body is empty");
                    }
                    try {
                        ChatGroup group = objectMapper.readValue(body, ChatGroup.class);
                        if (group.getName() == null || group.getName().trim().isEmpty()) {
                            throw new IllegalArgumentException("Missing group name");
                        }
                        if (groupRepository.findByName(group.getName()).isPresent()) {
                            throw new Exception("Group name already exists");
                        }
                        group.setId(UUID.randomUUID().toString());
                        exchange.getIn().setBody(group);
                    } catch (Exception e) {
                        log.error("JSON parsing or validation error: {}", e.getMessage(), e);
                        throw new IllegalArgumentException("Invalid JSON or validation failed: " + e.getMessage());
                    }
                })
                .to("jpa:ru.top.server.model.ChatGroup")
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

        rest("/api/users")
                .get()
                .produces("application/json")
                .to("direct:getUsers");

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
                .process(exchange -> {
                    Exception exception = exchange.getProperty("CamelExceptionCaught", Exception.class);
                    log.error("Failed to fetch users: {}", exception.getMessage(), exception);
                    exchange.getMessage().setBody("{\"error\":\"" + exception.getMessage() + "\"}");
                    exchange.getMessage().setHeader("Content-Type", "application/json");
                    exchange.getMessage().setHeader("CamelHttpResponseCode", 500);
                })
                .end();

        rest("/api/groups")
                .get()
                .produces("application/json")
                .to("direct:getGroups");

        from("direct:getGroups")
                .doTry()
                .process(exchange -> {
                    log.info("Fetching all groups");
                    List<ChatGroup> groups = groupRepository.findAll();
                    List<Map<String, String>> groupList = groups.stream()
                            .map(group -> {
                                Map<String, String> groupMap = new HashMap<>();
                                groupMap.put("id", group.getId());
                                groupMap.put("name", group.getName());
                                return groupMap;
                            })
                            .collect(Collectors.toList());
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

        rest("/api/messages/private")
                .post()
                .consumes("application/json")
                .produces("application/json")
                .to("direct:sendPrivateMessage");

        from("direct:sendPrivateMessage")
                .doTry()
                .process(exchange -> {
                    String body = exchange.getIn().getBody(String.class);
                    log.info("Processing private message request: {}", body);
                    Message message = objectMapper.readValue(body, Message.class);
                    if (message.getContent() == null || message.getSenderId() == null || message.getRecipientId() == null) {
                        throw new IllegalArgumentException("Invalid message JSON: missing content, senderId, or recipientId");
                    }
                    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                    String username = auth != null ? auth.getName() : null;
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
                .process(exchange -> {
                    Exception exception = exchange.getProperty("CamelExceptionCaught", Exception.class);
                    log.error("Failed to send private message: {}", exception.getMessage(), exception);
                    exchange.getMessage().setBody("{\"error\":\"" + exception.getMessage() + "\"}");
                    exchange.getMessage().setHeader("Content-Type", "application/json");
                    exchange.getMessage().setHeader("CamelHttpResponseCode", 400);
                })
                .end();

        rest("/api/messages/group")
                .post()
                .consumes("application/json")
                .produces("application/json")
                .to("direct:sendGroupMessage");

        from("direct:sendGroupMessage")
                .doTry()
                .process(exchange -> {
                    String body = exchange.getIn().getBody(String.class);
                    log.info("Processing group message request: {}", body);
                    Message message = objectMapper.readValue(body, Message.class);
                    if (message.getContent() == null || message.getSenderId() == null || message.getGroupId() == null) {
                        throw new IllegalArgumentException("Invalid message JSON: missing content, senderId, or groupId");
                    }
                    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                    String username = auth != null ? auth.getName() : null;
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
                .process(exchange -> {
                    Exception exception = exchange.getProperty("CamelExceptionCaught", Exception.class);
                    log.error("Failed to send group message: {}", exception.getMessage(), exception);
                    exchange.getMessage().setBody("{\"error\":\"" + exception.getMessage() + "\"}");
                    exchange.getMessage().setHeader("Content-Type", "application/json");
                    exchange.getMessage().setHeader("CamelHttpResponseCode", 400);
                })
                .end();

        rest("/api/messages/private/conversation/{otherUserId}")
                .get()
                .produces("application/json")
                .to("direct:privateMessages");

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
                    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                    String username = auth != null ? auth.getName() : null;
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
                .process(exchange -> {
                    Exception exception = exchange.getProperty("CamelExceptionCaught", Exception.class);
                    log.error("Failed to fetch private messages: {}", exception.getMessage(), exception);
                    exchange.getMessage().setBody("{\"error\":\"" + exception.getMessage() + "\"}");
                    exchange.getMessage().setHeader("Content-Type", "application/json");
                    int statusCode = exception.getMessage().contains("SQLITE_ERROR") ? 500 : 400;
                    exchange.getMessage().setHeader("CamelHttpResponseCode", statusCode);
                })
                .end();

        rest("/api/messages/private/history/{otherUserId}")
                .get()
                .produces("application/json")
                .to("direct:privateChatHistory");

        from("direct:privateChatHistory")
                .doTry()
                .process(exchange -> {
                    String otherUserId = exchange.getMessage().getHeader("otherUserId", String.class);
                    log.info("Fetching chat history with otherUserId: {}", otherUserId);
                    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                    String username = auth != null ? auth.getName() : null;
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
                .process(exchange -> {
                    Exception exception = exchange.getProperty("CamelExceptionCaught", Exception.class);
                    log.error("Failed to fetch chat history: {}", exception.getMessage(), exception);
                    exchange.getMessage().setBody("{\"error\":\"" + exception.getMessage() + "\"}");
                    exchange.getMessage().setHeader("Content-Type", "application/json");
                    int statusCode = exception.getMessage().contains("SQLITE_ERROR") ? 500 : 400;
                    exchange.getMessage().setHeader("CamelHttpResponseCode", statusCode);
                })
                .end();

        rest("/api/messages/group/{groupId}")
                .get()
                .produces("application/json")
                .to("direct:groupMessages");

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
                .process(exchange -> {
                    Exception exception = exchange.getProperty("CamelExceptionCaught", Exception.class);
                    log.error("Failed to fetch group messages: {}", exception.getMessage(), exception);
                    exchange.getMessage().setBody("{\"error\":\"" + exception.getMessage() + "\"}");
                    exchange.getMessage().setHeader("Content-Type", "application/json");
                    exchange.getMessage().setHeader("CamelHttpResponseCode", 500);
                })
                .end();

        rest("/api/messages/search")
                .get()
                .produces("application/json")
                .to("direct:searchMessages");

        from("direct:searchMessages")
                .doTry()
                .process(exchange -> {
                    String keyword = exchange.getMessage().getHeader("keyword", String.class);
                    String startParam = exchange.getMessage().getHeader("start", String.class);
                    String endParam = exchange.getMessage().getHeader("end", String.class);
                    log.info("Searching messages with keyword: {}, start: {}, end: {}", keyword, startParam, endParam);

                    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                    String username = auth != null ? auth.getName() : null;
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
                .process(exchange -> {
                    Exception exception = exchange.getProperty("CamelExceptionCaught", Exception.class);
                    log.error("Failed to search messages: {}", exception.getMessage(), exception);
                    exchange.getMessage().setBody("{\"error\":\"" + exception.getMessage() + "\"}");
                    exchange.getMessage().setHeader("Content-Type", "application/json");
                    int statusCode = exception.getMessage().contains("SQLITE_ERROR") ? 500 : 400;
                    exchange.getMessage().setHeader("CamelHttpResponseCode", statusCode);
                })
                .end();

        rest("/api/users/count")
                .get()
                .produces("application/json")
                .to("direct:userCount");

        from("direct:userCount")
                .doTry()
                .process(exchange -> {
                    log.info("Fetching total user count"); // Логирование запроса
                    long count = userRepository.count(); // Подсчёт пользователей
                    String json = objectMapper.writeValueAsString(Map.of("count", count)); // Формирование JSON-ответа
                    exchange.getIn().setBody(json);
                    exchange.getMessage().setHeader("Content-Type", "application/json");
                })
                .doCatch(Exception.class)
                .process(exchange -> {
                    Exception exception = exchange.getProperty("CamelExceptionCaught", Exception.class);
                    log.error("Failed to fetch user count: {}", exception.getMessage(), exception);
                    exchange.getMessage().setBody("{\"error\":\"" + exception.getMessage() + "\"}"); // Формирование ответа об ошибке
                    exchange.getMessage().setHeader("Content-Type", "application/json");
                    exchange.getMessage().setHeader("CamelHttpResponseCode", 500);
                })
                .end();
    }
}