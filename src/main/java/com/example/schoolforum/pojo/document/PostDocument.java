package com.example.schoolforum.pojo.document;

import com.example.schoolforum.pojo.Posts;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostDocument {

    public static final String INDEX_NAME = "schoolforum_posts";

    private Long id;
    private Long authorId;
    private String authorName;
    private String authorAvatar;
    private String title;
    private String content;
    private Long categoryId;
    private String categoryName;
    private String parentCategoryName;
    private List<String> tags;
    private Integer likeCount;
    private Integer viewCount;
    private Integer commentCount;
    private Integer favoriteCount;
    private String coverImage;
    private Boolean isPinned;
    private Boolean isEssential;
    private Long createdAt;
    private Long updatedAt;

    public static PostDocument fromEntity(Posts post) {
        return PostDocument.builder()
                .id(post.getId())
                .authorId(post.getAuthorId())
                .authorName(post.getAuthorName())
                .authorAvatar(post.getAuthorAvatar())
                .title(post.getTitle())
                .content(post.getContent())
                .categoryId(post.getCategoryId())
                .categoryName(post.getCategoryName())
                .parentCategoryName(post.getParentCategoryName())
                .tags(post.getTagNames())
                .coverImage(post.getCoverImage())
                .likeCount(post.getLikeCount())
                .viewCount(post.getViewCount())
                .commentCount(post.getCommentCount())
                .favoriteCount(post.getFavoriteCount())
                .isPinned(post.getIsPinned() != null && post.getIsPinned().name().equals("PINNED"))
                .isEssential(post.getIsEssential() != null && post.getIsEssential().name().equals("ESSENTIAL"))
                .createdAt(toTimestamp(post.getCreatedAt()))
                .updatedAt(toTimestamp(post.getUpdatedAt()))
                .build();
    }

    private static Long toTimestamp(LocalDateTime dateTime) {
        if (dateTime == null) return null;
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
