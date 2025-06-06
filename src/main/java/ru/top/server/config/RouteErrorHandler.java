package ru.top.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

// Утилитный класс для обработки ошибок в маршрутах
@Component
public class RouteErrorHandler {

    public RouteErrorHandler(ObjectMapper objectMapper) {
        // Внедрение ObjectMapper для сериализации ошибок в JSON
    }

    // Метод для обработки исключений
    public void handleError(Exchange exchange, Logger log, int defaultStatusCode) {
        Exception exception = exchange.getProperty("CamelExceptionCaught", Exception.class);
        log.error("Operation failed: {}", exception.getMessage(), exception);
        // Формирование сообщения об ошибке
        String errorMessage = exception.getCause() != null
                ? exception.getCause().getMessage()
                : exception.getMessage();
        exchange.getMessage().setBody("{\"error\":\"" + errorMessage + "\"}");
        exchange.getMessage().setHeader("Content-Type", "application/json");
        // Установка кода ответа в зависимости от типа ошибки
        int statusCode = exception.getMessage().contains("SQLITE_ERROR") ? 500 : defaultStatusCode;
        exchange.getMessage().setHeader("CamelHttpResponseCode", statusCode);
    }
}