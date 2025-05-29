package ru.top.server.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.top.server.model.ChatUser;

import java.util.Optional;

public interface ChatUserRepository extends JpaRepository<ChatUser, String> {
    Optional<ChatUser> findByUsername(String username);
}