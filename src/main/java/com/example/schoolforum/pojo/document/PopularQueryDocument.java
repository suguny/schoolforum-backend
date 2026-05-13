package com.example.schoolforum.pojo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PopularQueryDocument {

    public static final String INDEX_NAME = "schoolforum_popular_queries";

    private Long id;
    private String keyword;
    private Long count;
}
