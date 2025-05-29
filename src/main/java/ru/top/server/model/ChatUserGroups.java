package ru.top.server.model;

import jakarta.persistence.*;

@Entity
@Table(name = "chat_user_groups")
public class ChatUserGroups {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private ChatUser user;

    @ManyToOne
    @JoinColumn(name = "group_id", referencedColumnName = "id")
    private ChatGroup group;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ChatUser getUser() {
        return user;
    }

    public void setUser(ChatUser user) {
        this.user = user;
    }

    public ChatGroup getGroup() {
        return group;
    }

    public void setGroup(ChatGroup group) {
        this.group = group;
    }
}