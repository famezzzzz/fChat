package ru.top.server.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import java.util.List;

@Entity
public class Group {
    @Id
    private String id; // Уникальный ID группы
    private String name;

    @ManyToMany
    private List<User> members;

    // Геттеры и сеттеры
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<User> getMembers() { return members; }
    public void setMembers(List<User> members) { this.members = members; }
}