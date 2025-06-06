package ru.top.server;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// Аннотация @Component указывает, что этот класс является компонентом Spring и будет автоматически обнаружен и зарегистрирован в контексте приложения
@Component
public class MainRoute extends RouteBuilder {
    private static final Logger log = LoggerFactory.getLogger(MainRoute.class);

    @Override
    public void configure() {
        // Логирование начала конфигурации маршрутов
        log.info("Configuring Camel REST routes");

        // Настройка REST-конфигурации для всех эндпоинтов
        restConfiguration()
                .component("servlet") // Использование сервлета для обработки HTTP-запросов
                .bindingMode(RestBindingMode.off) // Отключение автоматической привязки данных
                .dataFormatProperty("prettyPrint", "true"); // Включение форматирования JSON
    }
}

