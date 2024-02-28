package org.green.webapp.model;

public record Comment (Long postId, Long id, String name, String email, String body){
}
