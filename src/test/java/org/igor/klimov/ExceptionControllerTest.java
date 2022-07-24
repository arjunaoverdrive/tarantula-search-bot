package org.igor.klimov;

import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
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
    private WebApplicationContext context;
    private MockMvc mockMvc;

    @Before
    public void setup() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    public ExceptionControllerTest() {
    }

    @WithMockUser("spring")
    @Test
    public void indexSeparatePageMissingRequestParameter() throws Exception {
        MvcResult result = this.mockMvc.perform(post("/api/indexPage"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andReturn();
        Assertions.assertThat(result.getResponse().getContentType().equals(MediaType.APPLICATION_JSON_VALUE)).isTrue();
    }

    @WithMockUser("spring")
    @Test
    public void testUnsupportedMethod() throws Exception {
        this.mockMvc.perform(post("/api/startIndexing"))
                .andDo(print())
                .andExpect(status().isMethodNotAllowed())
                .andReturn();
    }

    @WithMockUser("spring")
    @Test
    public void testUnsupportedEndPoint() throws Exception {
        this.mockMvc.perform(get("/startIndexing"))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    public void testUnauthenticatedException_get() throws Exception{
        this.mockMvc.perform(get("/startIndexing"))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void testUnauthenticatedException_post() throws Exception{
        this.mockMvc.perform(post("/api/indexPage"))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }
}
