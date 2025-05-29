package ru.top.server.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.top.server.model.ChatGroup;

import java.util.Optional;

public interface ChatGroupRepository extends JpaRepository<ChatGroup, String> {
    Optional<ChatGroup> findByName(String name);
}