package org.example.hospital.dto;

/**
 * Coze API 请求 DTO
 */
public class CozeRequest {
    private String content;
    // Backward compatibility: some clients send `message` instead of `content`.
    private String message;
    private Long userId;

    public CozeRequest() {}

    public CozeRequest(String content, Long userId) {
        this.content = content;
        this.userId = userId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * Normalized text accessor for service-side validation.
     */
    public String getNormalizedContent() {
        if (content != null && !content.trim().isEmpty()) {
            return content.trim();
        }
        if (message != null && !message.trim().isEmpty()) {
            return message.trim();
        }
        return "";
    }

    public boolean hasAnyInput() {
        return !getNormalizedContent().isEmpty();
    }
}
