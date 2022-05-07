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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource("/application-test.properties")
public class ExceptionControllerTest {

    @Autowired
    MockMvc mockMvc;

    public ExceptionControllerTest() {
    }

    @Test
    public void indexSeparatePageMissingRequestParameter() throws Exception {
        MvcResult result = this.mockMvc.perform(post("/api/indexPage"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andReturn();
        Assertions.assertThat(result.getResponse().getContentType().equals(MediaType.APPLICATION_JSON_VALUE)).isTrue();
    }

    @Test
    public void testUnsupportedMethod() throws Exception {
        this.mockMvc.perform(post("/api/startIndexing"))
                .andDo(print())
                .andExpect(status().isMethodNotAllowed())
                .andReturn();
    }

    @Test
    public void testUnsupportedEndPoint() throws Exception {
        this.mockMvc.perform(get("/startIndexing"))
                .andDo(print())
                .andExpect(status().isNotFound());
    }
}
