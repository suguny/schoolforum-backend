package com.example.schoolforum.pojo.document;

import com.example.schoolforum.pojo.Users;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDocument {

    public static final String INDEX_NAME = "schoolforum_users";

    private Long id;
    private String username;
    private String email;
    private String avatarUrl;
    private String bio;
    private Integer role;
    private Boolean isActive;
    private Long createdAt;

    public static UserDocument fromEntity(Users user) {
        return UserDocument.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .bio(user.getBio())
                .role(user.getRole() != null ? user.getRole().getCode() : 2)
                .isActive(user.getIsActive() != null && user.getIsActive().name().equals("ACTIVE"))
                .createdAt(toTimestamp(user.getCreatedAt()))
                .build();
    }

    private static Long toTimestamp(LocalDateTime dateTime) {
        if (dateTime == null) return null;
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
