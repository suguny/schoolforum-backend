package com.example.schoolforum;

import com.example.schoolforum.repository.PopularQueryRepository;
import com.example.schoolforum.repository.PostSearchRepository;
import com.example.schoolforum.repository.UserSearchRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.mail.javamail.JavaMailSender;

@SpringBootTest
class SchoolforumApplicationTests {

    @MockBean
    private ElasticsearchOperations elasticsearchOperations;

    @MockBean
    private PostSearchRepository postSearchRepository;

    @MockBean
    private UserSearchRepository userSearchRepository;

    @MockBean
    private PopularQueryRepository popularQueryRepository;

    @MockBean
    private JavaMailSender mailSender;

    @Test
    void contextLoads() {
    }

}
