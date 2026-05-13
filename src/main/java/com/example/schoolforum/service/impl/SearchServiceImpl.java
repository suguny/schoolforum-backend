package com.example.schoolforum.service.impl;

import com.example.schoolforum.component.PostQueryHelper;
import com.example.schoolforum.mapper.UsersMapper;
import com.example.schoolforum.pojo.Posts;
import com.example.schoolforum.pojo.Users;
import com.example.schoolforum.pojo.document.PopularQueryDocument;
import com.example.schoolforum.pojo.document.PostDocument;
import com.example.schoolforum.pojo.document.UserDocument;
import com.example.schoolforum.pojo.dto.CombinedSearchResult;
import com.example.schoolforum.pojo.dto.KeywordSuggestion;
import com.example.schoolforum.pojo.dto.PostSearchDocument;
import com.example.schoolforum.pojo.dto.SearchResult;
import com.example.schoolforum.pojo.dto.UserSearchDocument;
import com.example.schoolforum.service.SearchService;
import com.manticoresearch.client.ApiException;
import com.manticoresearch.client.api.IndexApi;
import com.manticoresearch.client.api.SearchApi;
import com.manticoresearch.client.api.UtilsApi;
import com.manticoresearch.client.model.DeleteDocumentRequest;
import com.manticoresearch.client.model.InsertDocumentRequest;
import com.manticoresearch.client.model.SearchRequest;
import com.manticoresearch.client.model.SearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final IndexApi indexApi;
    private final SearchApi searchApi;
    private final UtilsApi utilsApi;
    private final PostQueryHelper postQueryHelper;
    private final UsersMapper usersMapper;

    @Override
    public void deletePost(Long postId) {
        try {
            DeleteDocumentRequest deleteRequest = new DeleteDocumentRequest();
            deleteRequest.index(PostDocument.INDEX_NAME).setId(postId);
            indexApi.delete(deleteRequest);
        } catch (ApiException e) {
            log.error("Failed to delete post {}: {}", postId, e.getMessage(), e);
        }
    }

    @Override
    public void indexUser(UserSearchDocument document) {
        try {
            InsertDocumentRequest docRequest = new InsertDocumentRequest();
            Map<String, Object> doc = new HashMap<>();
            doc.put("username", document.getUsername());
            doc.put("email", document.getEmail());
            doc.put("avatar_url", document.getAvatarUrl());
            doc.put("bio", document.getBio());
            doc.put("role", toRoleInt(document.getRole()));
            doc.put("is_active", toActiveBool(document.getIsActive()));
            doc.put("created_at", parseTimestamp(document.getCreatedAt()));
            docRequest.index(UserDocument.INDEX_NAME).id(document.getId()).setDoc(doc);
            indexApi.replace(docRequest);
        } catch (ApiException e) {
            log.error("Failed to index user {}: {}", document.getId(), e.getMessage(), e);
        }
    }

    @Override
    public void deleteUser(Long userId) {
        try {
            DeleteDocumentRequest deleteRequest = new DeleteDocumentRequest();
            deleteRequest.index(UserDocument.INDEX_NAME).setId(userId);
            indexApi.delete(deleteRequest);
        } catch (ApiException e) {
            log.error("Failed to delete user {}: {}", userId, e.getMessage(), e);
        }
    }

    @Override
    public CombinedSearchResult search(String query, int page, int pageSize) {
        SearchResult<PostSearchDocument> postResult = searchPostsInternal(query, page, pageSize);
        SearchResult<UserSearchDocument> userResult = searchUsersInternal(query, page, pageSize);

        return CombinedSearchResult.builder()
                .posts(postResult)
                .users(userResult)
                .build();
    }

    @Override
    public List<KeywordSuggestion> getKeywordSuggestions(String prefix, int limit) {
        try {
            SearchRequest searchRequest = new SearchRequest();
            searchRequest.setIndex(PopularQueryDocument.INDEX_NAME);

            Map<String, Object> matchQuery = new HashMap<>();
            matchQuery.put("keyword", prefix + "*");
            Map<String, Object> queryMap = new HashMap<>();
            queryMap.put("match", matchQuery);
            searchRequest.setQuery(queryMap);

            searchRequest.setLimit(limit);
            searchRequest.setSort(new ArrayList<>(List.of("count:desc")));

            SearchResponse response = searchApi.search(searchRequest);
            List<?> rawHits = response.getHits().getHits();

            return rawHits.stream()
                    .map(rawHit -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> hit = (Map<String, Object>) rawHit;
                        Map<String, Object> source = hit.get("_source") instanceof Map
                                ? (Map<String, Object>) hit.get("_source")
                                : new HashMap<>();
                        Object countObj = source.get("count");
                        Long count = countObj instanceof Number ? ((Number) countObj).longValue() : 0L;
                        Object scoreObj = hit.get("_score");
                        Double score = scoreObj instanceof Number ? ((Number) scoreObj).doubleValue() : 0.0;
                        return KeywordSuggestion.builder()
                                .keyword((String) source.get("keyword"))
                                .count(count)
                                .score(score)
                                .build();
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.debug("Failed to get keyword suggestions: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public void syncAllPosts() {
        try {
            createPostsIndex();

            List<Posts> posts = postQueryHelper.selectAllWithRelations();
            for (Posts post : posts) {
                PostDocument doc = PostDocument.fromEntity(post);
                insertPostDocument(doc);
            }
            log.info("Successfully synced {} posts to Manticore Search", posts.size());
        } catch (Exception e) {
            log.error("Failed to sync posts: {}", e.getMessage(), e);
            throw new RuntimeException("帖子索引同步失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void syncAllUsers() {
        try {
            createUsersIndex();

            List<Users> users = usersMapper.selectAll();
            for (Users user : users) {
                UserDocument doc = UserDocument.fromEntity(user);
                insertUserDocument(doc);
            }
            log.info("Successfully synced {} users to Manticore Search", users.size());
        } catch (Exception e) {
            log.error("Failed to sync users: {}", e.getMessage(), e);
            throw new RuntimeException("用户索引同步失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void indexPostById(Long postId) {
        Posts post = postQueryHelper.selectByIdWithRelations(postId);
        if (post != null) {
            PostDocument doc = PostDocument.fromEntity(post);
            insertPostDocument(doc);
        }
    }

    @Override
    public void deleteAllIndexes() {
        try {
            utilsApi.sql("DROP TABLE IF EXISTS " + PostDocument.INDEX_NAME, true);
            utilsApi.sql("DROP TABLE IF EXISTS " + UserDocument.INDEX_NAME, true);
            log.info("Deleted all search indexes");
        } catch (ApiException e) {
            log.error("Failed to delete indexes: {}", e.getMessage(), e);
        }
    }

    @Override
    public long getPostsCollectionCount() {
        try {
            SearchRequest searchRequest = new SearchRequest();
            searchRequest.setIndex(PostDocument.INDEX_NAME);
            Map<String, Object> queryMap = new HashMap<>();
            queryMap.put("match_all", null);
            searchRequest.setQuery(queryMap);
            searchRequest.setLimit(0);
            SearchResponse response = searchApi.search(searchRequest);
            return response.getHits().getTotal() != null ? response.getHits().getTotal() : 0L;
        } catch (ApiException e) {
            log.error("Failed to get posts count: {}", e.getMessage());
            return 0L;
        }
    }

    @Override
    public long getUsersCollectionCount() {
        try {
            SearchRequest searchRequest = new SearchRequest();
            searchRequest.setIndex(UserDocument.INDEX_NAME);
            Map<String, Object> queryMap = new HashMap<>();
            queryMap.put("match_all", null);
            searchRequest.setQuery(queryMap);
            searchRequest.setLimit(0);
            SearchResponse response = searchApi.search(searchRequest);
            return response.getHits().getTotal() != null ? response.getHits().getTotal() : 0L;
        } catch (ApiException e) {
            log.error("Failed to get users count: {}", e.getMessage());
            return 0L;
        }
    }

    private void createPostsIndex() throws ApiException {
        utilsApi.sql("DROP TABLE IF EXISTS " + PostDocument.INDEX_NAME, true);
        utilsApi.sql(
                "CREATE TABLE " + PostDocument.INDEX_NAME + " ("
                        + "author_id BIGINT, "
                        + "author_name TEXT, "
                        + "author_avatar STRING, "
                        + "title TEXT, "
                        + "content TEXT, "
                        + "category_id BIGINT, "
                        + "category_name STRING, "
                        + "parent_category_name STRING, "
                        + "tags JSON, "
                        + "like_count INTEGER, "
                        + "view_count INTEGER, "
                        + "comment_count INTEGER, "
                        + "favorite_count INTEGER, "
                        + "cover_image STRING, "
                        + "is_pinned BOOLEAN, "
                        + "is_essential BOOLEAN, "
                        + "created_at BIGINT, "
                        + "updated_at BIGINT"
                        + ") charset_table = 'chinese' morphology = 'icu_chinese'",
                true);
        log.info("Created posts index with ICU Chinese morphology");
    }

    private void createUsersIndex() throws ApiException {
        utilsApi.sql("DROP TABLE IF EXISTS " + UserDocument.INDEX_NAME, true);
        utilsApi.sql(
                "CREATE TABLE " + UserDocument.INDEX_NAME + " ("
                        + "username TEXT, "
                        + "email STRING, "
                        + "avatar_url STRING, "
                        + "bio TEXT, "
                        + "role INTEGER, "
                        + "is_active BOOLEAN, "
                        + "created_at BIGINT"
                        + ") charset_table = 'chinese' morphology = 'icu_chinese'",
                true);
        log.info("Created users index with ICU Chinese morphology");
    }

    private void insertPostDocument(PostDocument doc) {
        try {
            InsertDocumentRequest docRequest = new InsertDocumentRequest();
            Map<String, Object> docMap = new HashMap<>();
            docMap.put("author_id", doc.getAuthorId());
            docMap.put("author_name", doc.getAuthorName());
            docMap.put("author_avatar", doc.getAuthorAvatar());
            docMap.put("title", doc.getTitle());
            docMap.put("content", doc.getContent());
            docMap.put("category_id", doc.getCategoryId());
            docMap.put("category_name", doc.getCategoryName());
            docMap.put("parent_category_name", doc.getParentCategoryName());
            docMap.put("tags", doc.getTags());
            docMap.put("like_count", doc.getLikeCount());
            docMap.put("view_count", doc.getViewCount());
            docMap.put("comment_count", doc.getCommentCount());
            docMap.put("favorite_count", doc.getFavoriteCount());
            docMap.put("cover_image", doc.getCoverImage());
            docMap.put("is_pinned", doc.getIsPinned());
            docMap.put("is_essential", doc.getIsEssential());
            docMap.put("created_at", doc.getCreatedAt());
            docMap.put("updated_at", doc.getUpdatedAt());
            docRequest.index(PostDocument.INDEX_NAME).id(doc.getId()).setDoc(docMap);
            indexApi.replace(docRequest);
        } catch (ApiException e) {
            log.error("Failed to insert post {}: {}", doc.getId(), e.getMessage(), e);
        }
    }

    private void insertUserDocument(UserDocument doc) {
        try {
            InsertDocumentRequest docRequest = new InsertDocumentRequest();
            Map<String, Object> docMap = new HashMap<>();
            docMap.put("username", doc.getUsername());
            docMap.put("email", doc.getEmail());
            docMap.put("avatar_url", doc.getAvatarUrl());
            docMap.put("bio", doc.getBio());
            docMap.put("role", doc.getRole());
            docMap.put("is_active", doc.getIsActive());
            docMap.put("created_at", doc.getCreatedAt());
            docRequest.index(UserDocument.INDEX_NAME).id(doc.getId()).setDoc(docMap);
            indexApi.replace(docRequest);
        } catch (ApiException e) {
            log.error("Failed to insert user {}: {}", doc.getId(), e.getMessage(), e);
        }
    }

    private SearchResult<PostSearchDocument> searchPostsInternal(String query, int page, int pageSize) {
        try {
            SearchRequest searchRequest = new SearchRequest();
            searchRequest.setIndex(PostDocument.INDEX_NAME);

            Map<String, Object> matchQuery = new HashMap<>();
            matchQuery.put("title,content,author_name", query);
            Map<String, Object> queryMap = new HashMap<>();
            queryMap.put("match", matchQuery);
            searchRequest.setQuery(queryMap);

            searchRequest.setLimit(pageSize);
            searchRequest.setOffset((page - 1) * pageSize);

            SearchResponse response = searchApi.search(searchRequest);
            Long totalHits = response.getHits().getTotal() != null ? response.getHits().getTotal() : 0L;

            List<PostSearchDocument> hits = response.getHits().getHits().stream()
                    .map(rawHit -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> hit = (Map<String, Object>) rawHit;
                        Map<String, Object> source = hit.get("_source") instanceof Map
                                ? (Map<String, Object>) hit.get("_source")
                                : new HashMap<>();
                        return PostSearchDocument.builder()
                                .id(toLong(hit.get("_id")))
                                .authorId(toLong(source.get("author_id")))
                                .authorName((String) source.get("author_name"))
                                .authorAvatar((String) source.get("author_avatar"))
                                .title((String) source.get("title"))
                                .content((String) source.get("content"))
                                .coverImage((String) source.get("cover_image"))
                                .categoryId(toLong(source.get("category_id")))
                                .categoryName((String) source.get("category_name"))
                                .parentCategoryName((String) source.get("parent_category_name"))
                                .tagNames(toStringList(source.get("tags")))
                                .likeCount(toInteger(source.get("like_count")))
                                .commentCount(toInteger(source.get("comment_count")))
                                .favoriteCount(toInteger(source.get("favorite_count")))
                                .viewCount(toInteger(source.get("view_count")))
                                .isPinned(toBoolean(source.get("is_pinned")) ? "PINNED" : "NOT_PINNED")
                                .isEssential(toBoolean(source.get("is_essential")) ? "ESSENTIAL" : "NOT_ESSENTIAL")
                                .createdAt(formatTimestamp(toLong(source.get("created_at"))))
                                .updatedAt(formatTimestamp(toLong(source.get("updated_at"))))
                                .build();
                    })
                    .collect(Collectors.toList());

            return SearchResult.<PostSearchDocument>builder()
                    .query(query)
                    .totalHits(totalHits)
                    .hitsPerPage(pageSize)
                    .page(page)
                    .totalPages((int) Math.ceil((double) totalHits / pageSize))
                    .hits(hits)
                    .build();
        } catch (Exception e) {
            log.error("Failed to search posts: {}", e.getMessage(), e);
            return SearchResult.<PostSearchDocument>builder().query(query).hits(new ArrayList<>()).build();
        }
    }

    private SearchResult<UserSearchDocument> searchUsersInternal(String query, int page, int pageSize) {
        try {
            SearchRequest searchRequest = new SearchRequest();
            searchRequest.setIndex(UserDocument.INDEX_NAME);

            Map<String, Object> matchQuery = new HashMap<>();
            matchQuery.put("username,email,bio", query);
            Map<String, Object> queryMap = new HashMap<>();
            queryMap.put("match", matchQuery);
            searchRequest.setQuery(queryMap);

            searchRequest.setLimit(pageSize);
            searchRequest.setOffset((page - 1) * pageSize);

            SearchResponse response = searchApi.search(searchRequest);
            Long totalHits = response.getHits().getTotal() != null ? response.getHits().getTotal() : 0L;

            List<UserSearchDocument> hits = response.getHits().getHits().stream()
                    .map(rawHit -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> hit = (Map<String, Object>) rawHit;
                        Map<String, Object> source = hit.get("_source") instanceof Map
                                ? (Map<String, Object>) hit.get("_source")
                                : new HashMap<>();
                        return UserSearchDocument.builder()
                                .id(toLong(hit.get("_id")))
                                .username((String) source.get("username"))
                                .email((String) source.get("email"))
                                .avatarUrl((String) source.get("avatar_url"))
                                .bio((String) source.get("bio"))
                                .role(toRoleString(toInteger(source.get("role"))))
                                .isActive(toBoolean(source.get("is_active")) ? 1 : 0)
                                .build();
                    })
                    .collect(Collectors.toList());

            return SearchResult.<UserSearchDocument>builder()
                    .query(query)
                    .totalHits(totalHits)
                    .hitsPerPage(pageSize)
                    .page(page)
                    .totalPages((int) Math.ceil((double) totalHits / pageSize))
                    .hits(hits)
                    .build();
        } catch (Exception e) {
            log.error("Failed to search users: {}", e.getMessage(), e);
            return SearchResult.<UserSearchDocument>builder().query(query).hits(new ArrayList<>()).build();
        }
    }

    private int toRoleInt(String role) {
        if (role == null) return 2;
        return switch (role) {
            case "SUPER_ADMIN" -> 0;
            case "ADMIN" -> 1;
            default -> 2;
        };
    }

    private String toRoleString(Integer role) {
        if (role == null) return "USER";
        return switch (role) {
            case 0 -> "SUPER_ADMIN";
            case 1 -> "ADMIN";
            default -> "USER";
        };
    }

    private boolean toActiveBool(Integer isActive) {
        return isActive != null && isActive == 1;
    }

    private Long parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) return null;
        try {
            return Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String formatTimestamp(Long epochMillis) {
        if (epochMillis == null) return null;
        return Instant.ofEpochMilli(epochMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer toInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Boolean toBoolean(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).intValue() != 0;
        return Boolean.parseBoolean(value.toString());
    }

    @SuppressWarnings("unchecked")
    private List<String> toStringList(Object value) {
        if (value == null) return null;
        if (value instanceof List) return (List<String>) value;
        return new ArrayList<>();
    }
}
