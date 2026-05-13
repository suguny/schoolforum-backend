package com.example.schoolforum.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.example.schoolforum.pojo.dto.CombinedSearchResult;
import com.example.schoolforum.pojo.dto.KeywordSuggestion;
import com.example.schoolforum.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
@Tag(name = "搜索管理", description = "基于Manticore Search的全文搜索接口")
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    @Operation(summary = "综合搜索", description = "一次获取帖子和用户的搜索结果")
    public CombinedSearchResult search(
            @Parameter(description = "搜索关键词", required = true) @RequestParam String query,
            @Parameter(description = "页码，默认第1页") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量，默认10条") @RequestParam(defaultValue = "10") int size) {
        return searchService.search(query, page, size);
    }

    @GetMapping("/suggest")
    @Operation(summary = "搜索联想", description = "输入时实时获取关键词推荐")
    public List<KeywordSuggestion> suggest(
            @Parameter(description = "输入前缀", required = true) @RequestParam String prefix,
            @Parameter(description = "返回数量，默认8条") @RequestParam(defaultValue = "8") int limit) {
        return searchService.getKeywordSuggestions(prefix, limit);
    }

    @PostMapping("/sync")
    @Operation(summary = "重建索引", description = "删除旧索引并全量同步数据")
    @SaCheckRole(value = {"admin", "super_admin"}, mode = SaMode.OR)
    public String sync() {
        searchService.deleteAllIndexes();
        searchService.syncAllPosts();
        searchService.syncAllUsers();
        long postCount = searchService.getPostsCollectionCount();
        long userCount = searchService.getUsersCollectionCount();
        return String.format("索引重建完成（帖子：%d 条，用户：%d 条）", postCount, userCount);
    }

    @DeleteMapping("/sync")
    @Operation(summary = "清空索引", description = "清空所有搜索索引数据")
    @SaCheckRole(value = {"admin", "super_admin"}, mode = SaMode.OR)
    public String clear() {
        searchService.deleteAllIndexes();
        return "所有索引已清空";
    }
}