package ru.top.server.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_message")
@NamedQueries({
        @NamedQuery(
                name = "Message.findConversationMessages",
                query = "SELECT m FROM Message m WHERE m.chatType = 'PRIVATE' AND " +
                        "((m.sender.id = :userId AND m.recipient.id = :otherUserId) OR " +
                        "(m.sender.id = :otherUserId AND m.recipient.id = :userId)) AND " +
                        "(:since IS NULL OR m.timestamp > :since) " +
                        "ORDER BY m.timestamp ASC"
        ),
        @NamedQuery(
                name = "Message.findByGroupId",
                query = "SELECT m FROM Message m WHERE m.group.id = :groupId AND m.chatType = 'GROUP'"
        ),
        @NamedQuery(
                name = "Message.findChatHistory",
                query = "SELECT m FROM Message m WHERE m.chatType = 'PRIVATE' AND " +
                        "((m.sender.id = :userId AND m.recipient.id = :otherUserId) OR " +
                        "(m.sender.id = :otherUserId AND m.recipient.id = :userId)) " +
                        "ORDER BY m.timestamp ASC"
        ),
        @NamedQuery(
                name = "Message.searchMessages",
                query = "SELECT m FROM Message m WHERE (m.sender.id = :userId OR m.recipient.id = :userId OR m.group.id IN (SELECT cug.group.id FROM ChatUserGroups cug WHERE cug.user.id = :userId))" +
                        "AND (:keyword IS NULL OR m.content LIKE :keyword)" +
                        "AND (:start IS NULL OR m.timestamp >= :start)" +
                        "AND (:end IS NULL OR m.timestamp <= :end)" +
                        "ORDER BY m.timestamp ASC"
        )
})
@JsonIgnoreProperties({"sender", "recipient", "group"})
public class Message {
    @Id
    private String id;

    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id")
    private ChatUser sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id")
    private ChatUser recipient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private ChatGroup group;

    private String chatType;

    private LocalDateTime timestamp;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    @JsonProperty("senderId")
    public String getSenderId() {
        return sender != null ? sender.getId() : null;
    }

    @JsonProperty("senderId")
    public void setSenderId(String senderId) {
        if (sender == null) {
            sender = new ChatUser();
        }
        sender.setId(senderId);
    }

    public ChatUser getSender() {
        return sender;
    }

    public void setSender(ChatUser sender) {
        this.sender = sender;
    }

    @JsonProperty("recipientId")
    public String getRecipientId() {
        return recipient != null ? recipient.getId() : null;
    }

    @JsonProperty("recipientId")
    public void setRecipientId(String recipientId) {
        if (recipient == null) {
            recipient = new ChatUser();
        }
        recipient.setId(recipientId);
    }

    public ChatUser getRecipient() {
        return recipient;
    }

    public void setRecipient(ChatUser recipient) {
        this.recipient = recipient;
    }

    @JsonProperty("groupId")
    public String getGroupId() {
        return group != null ? group.getId() : null;
    }

    @JsonProperty("groupId")
    public void setGroupId(String groupId) {
        if (group == null) {
            group = new ChatGroup();
        }
        group.setId(groupId);
    }

    public ChatGroup getGroup() {
        return group;
    }

    public void setGroup(ChatGroup group) {
        this.group = group;
    }

    public String getChatType() {
        return chatType;
    }

    public void setChatType(String chatType) {
        this.chatType = chatType;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}