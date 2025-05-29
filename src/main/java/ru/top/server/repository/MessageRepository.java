package ru.top.server.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.top.server.model.Message;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, String> {
    List<Message> findByGroup_IdAndChatType(String groupId, String chatType);
}