package org.igor.klimov;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource("/application-test.properties")
public class SearchControllerTest {

    @Autowired
    MockMvc mockMvc;

    public SearchControllerTest() {
    }

    @Test
    public void testSearch() throws Exception {
        this.mockMvc.perform(get("/api/startIndexing"))
                .andDo(print());
        MvcResult mvcResult = this.mockMvc.perform(get("/api/search?query=Россия&offset=0&limit=10"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(result -> result.getResponse().getContentType().equals(MediaType.APPLICATION_JSON_VALUE))
                .andReturn();
        String stringResult = mvcResult.getResponse().getContentAsString();
        List<String> fields = List.of("result", "count", "data", "site", "siteName", "uri", "title", "snippet", "relevance");
        fields.forEach(f -> Assertions.assertThat(stringResult.contains(f)).isTrue());
    }

    @Test
    public void testSearchWithEmptyQuery() throws Exception{
        MvcResult mvcResult = this.mockMvc.perform(get("/api/search?query=&offset=0&limit=10"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(result -> result.getResponse().getContentType().equals(MediaType.APPLICATION_JSON_VALUE))
                .andReturn();
        Assertions.assertThat(mvcResult.getResponse().getContentAsString().equals("{\"error\":\"The search query is empty\",\"result\":false}"))
                .isTrue();
    }
}
