package ru.top.server.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "CHAT_GROUP")
public class ChatGroup {
    @Id
    private String id;

    @JsonProperty("name")
    private String name;

    @JsonCreator
    public ChatGroup(@JsonProperty("name") String name) {
        this.name = name;
    }

    public ChatGroup() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}