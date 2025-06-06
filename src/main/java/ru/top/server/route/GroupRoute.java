package ru.top.server.route;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.top.server.config.RouteErrorHandler;
import ru.top.server.model.ChatGroup;
import ru.top.server.repository.ChatGroupRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

// Класс для маршрутов, связанных с группами
@Component
public class GroupRoute extends RouteBuilder {
    private static final Logger log = LoggerFactory.getLogger(GroupRoute.class);

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ChatGroupRepository groupRepository;

    @Autowired
    private RouteErrorHandler errorHandler;

    @Override
    public void configure() {
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
                .process(exchange -> errorHandler.handleError(exchange, log, 400))
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
                .process(exchange -> errorHandler.handleError(exchange, log, 500))
                .end();
    }
}