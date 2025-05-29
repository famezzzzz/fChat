package ru.top.server.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LoginRequest(String username, String password) {
}