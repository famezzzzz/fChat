package ru.top.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class ChatRoute extends RouteBuilder {
    private static final Logger log = LoggerFactory.getLogger(ChatRoute.class);

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

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
                        log.error("Authentication failed for user {}: {}",
                                loginRequest != null ? loginRequest.username() : "unknown", e.getMessage());
                        throw new IllegalArgumentException("Invalid credentials: " + e.getMessage(), e);
                    } catch (Exception e) {
                        log.error("Error processing login for user {}: {}",
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
                    exchange.getIn().setBody("{\"error\":\"" + errorMessage + "\"}");
                    exchange.getIn().setHeader("Content-Type", "application/json");
                    exchange.getIn().setHeader("CamelHttpResponseCode", 400);
                })
                .end();

        rest("/api/users/register")
                .post()
                .consumes("application/json")
                .to("direct:registerUser");

        from("direct:registerUser")
                .doTry()
                .process(exchange -> {
                    String body = exchange.getIn().getBody(String.class);
                    String contentType = exchange.getIn().getHeader("Content-Type", String.class);
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
                        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
                            throw new IllegalArgumentException("Username already registered");
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
                .setBody(simple("{\"message\":\"User registered successfully\",\"id\":\"${body.id}\",\"username\":\"${body.username}\"}"))
                .doCatch(Exception.class)
                .process(exchange -> {
                    Exception exception = exchange.getProperty("CamelExceptionCaught", Exception.class);
                    log.error("Failed to register user: {}", exception.getMessage(), exception);
                    exchange.getIn().setBody("{\"error\":\"" + exception.getMessage() + "\"}");
                    exchange.getIn().setHeader("Content-Type", "application/json");
                    exchange.getIn().setHeader("CamelHttpResponseCode", 400);
                })
                .end();

        rest("/api/create/group")
                .post()
                .consumes("application/json")
                .produces("application/json")
                .to("direct:createGroup");

        from("direct:createGroup")
                .doTry()
                .process(exchange -> {
                    String body = exchange.getIn().getBody(String.class);
                    String contentType = exchange.getIn().getHeader("Content-Type", String.class);
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
                    exchange.getIn().setBody("{\"error\":\"" + exception.getMessage() + "\"}");
                    exchange.getIn().setHeader("Content-Type", "application/json");
                    exchange.getIn().setHeader("CamelHttpResponseCode", 400);
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
                    exchange.getIn().setHeader("Content-Type", "application/json");
                    log.info("Retrieved {} users", users.size());
                })
                .doCatch(Exception.class)
                .process(exchange -> {
                    Exception exception = exchange.getProperty("CamelExceptionCaught", Exception.class);
                    log.error("Failed to fetch users: {}", exception.getMessage(), exception);
                    exchange.getIn().setBody("{\"error\":\"" + exception.getMessage() + "\"}");
                    exchange.getIn().setHeader("Content-Type", "application/json");
                    exchange.getIn().setHeader("CamelHttpResponseCode", 500);
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
                    exchange.getIn().setHeader("Content-Type", "application/json");
                    log.info("Retrieved {} groups", groups.size());
                })
                .doCatch(Exception.class)
                .process(exchange -> {
                    Exception exception = exchange.getProperty("CamelExceptionCaught", Exception.class);
                    log.error("Failed to fetch groups: {}", exception.getMessage(), exception);
                    exchange.getIn().setBody("{\"error\":\"" + exception.getMessage() + "\"}");
                    exchange.getIn().setHeader("Content-Type", "application/json");
                    exchange.getIn().setHeader("CamelHttpResponseCode", 500);
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
                .process(exchange -> {
                    Message message = exchange.getProperty("message", Message.class);
                    String recipientId = message.getRecipient().getId();
                    messagingTemplate.convertAndSend("/topic/private/" + recipientId, message);
                    log.info("Pushed private message to /topic/private/{}", recipientId);
                })
                .setBody(simple("{\"message\":\"Message sent successfully\"}"))
                .doCatch(Exception.class)
                .process(exchange -> {
                    Exception exception = exchange.getProperty("CamelExceptionCaught", Exception.class);
                    log.error("Failed to send private message: {}", exception.getMessage(), exception);
                    exchange.getIn().setBody("{\"error\":\"" + exception.getMessage() + "\"}");
                    exchange.getIn().setHeader("Content-Type", "application/json");
                    exchange.getIn().setHeader("CamelHttpResponseCode", 400);
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
                .process(exchange -> {
                    Message message = exchange.getProperty("message", Message.class);
                    String groupId = message.getGroup().getId();
                    messagingTemplate.convertAndSend("/topic/group/" + groupId, message);
                    log.info("Pushed group message to /topic/group/{}", groupId);
                })
                .setBody(simple("{\"message\":\"Message sent successfully\"}"))
                .doCatch(Exception.class)
                .process(exchange -> {
                    Exception exception = exchange.getProperty("CamelExceptionCaught", Exception.class);
                    log.error("Failed to send group message: {}", exception.getMessage(), exception);
                    exchange.getIn().setBody("{\"error\":\"" + exception.getMessage() + "\"}");
                    exchange.getIn().setHeader("Content-Type", "application/json");
                    exchange.getIn().setHeader("CamelHttpResponseCode", 400);
                })
                .end();

        rest("/api/messages/private/{userId}")
                .get()
                .produces("application/json")
                .to("direct:privateMessages");

        from("direct:privateMessages")
                .process(exchange -> {
                    String userId = exchange.getIn().getHeader("userId", String.class);
                    log.info("Fetching private messages for userId: {}", userId);
                    Map<String, Object> parameters = new HashMap<>();
                    parameters.put("userId", userId);
                    exchange.getIn().setHeader("CamelJpaParameters", parameters);
                })
                .to("jpa:ru.top.server.model.Message?namedQuery=Message.findByUserId")
                .process(exchange -> {
                    List<Message> messages = exchange.getIn().getBody(List.class);
                    log.info("Retrieved {} private messages for userId: {}", messages != null ? messages.size() : 0, exchange.getIn().getHeader("userId"));
                    String json = objectMapper.writeValueAsString(messages != null ? messages : List.of());
                    exchange.getIn().setBody(json);
                    exchange.getIn().setHeader("Content-Type", "application/json");
                });

        rest("/api/messages/group/{groupId}")
                .get()
                .produces("application/json")
                .to("direct:groupMessages");

        from("direct:groupMessages")
                .process(exchange -> {
                    String groupId = exchange.getIn().getHeader("groupId", String.class);
                    log.info("Fetching group messages for groupId: {}", groupId);
                    Map<String, Object> parameters = new HashMap<>();
                    parameters.put("groupId", groupId);
                    exchange.getIn().setHeader("CamelJpaParameters", parameters);
                })
                .to("jpa:ru.top.server.model.Message?namedQuery=Message.findByGroupId")
                .process(exchange -> {
                    List<Message> messages = exchange.getIn().getBody(List.class);
                    log.info("Retrieved {} messages for groupId: {}", messages != null ? messages.size() : 0, exchange.getIn().getHeader("groupId"));
                    String json = objectMapper.writeValueAsString(messages != null ? messages : List.of());
                    exchange.getIn().setBody(json);
                    exchange.getIn().setHeader("Content-Type", "application/json");
                });

    }
}