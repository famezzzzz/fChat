package ru.top.server.route;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import ru.top.server.config.RouteErrorHandler;
import ru.top.server.model.ChatUser;
import ru.top.server.model.LoginRequest;
import ru.top.server.repository.ChatUserRepository;
import ru.top.server.security.JwtUtil;

import java.util.UUID;

// Класс для маршрутов аутентификации и регистрации пользователей
@Component
public class AuthRoute extends RouteBuilder {
    private static final Logger log = LoggerFactory.getLogger(AuthRoute.class);

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ChatUserRepository userRepository;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private RouteErrorHandler errorHandler;

    @Override
    public void configure() {
        // Эндпоинт для аутентификации пользователя (POST /api/auth/login)
        rest("/api/auth/login")
                .post()
                .consumes("application/json")
                .produces("application/json")
                .to("direct:login");

        // Маршрут для обработки логина
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
                .process(exchange -> errorHandler.handleError(exchange, log, 400))
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
                .process(exchange -> errorHandler.handleError(exchange, log, 400))
                .end();
    }
}