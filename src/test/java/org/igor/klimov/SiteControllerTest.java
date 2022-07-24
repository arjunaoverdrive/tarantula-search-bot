package org.igor.klimov;

import org.assertj.core.api.Assertions;
import org.igor.klimov.app.config.ConfigProperties;
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

import java.util.ArrayList;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource("/application-test.properties")
public class SiteControllerTest {
    @Autowired
    private WebApplicationContext context;
    private MockMvc mockMvc;
    @Autowired
    private ConfigProperties properties;

    @Before
    public void setup() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @WithMockUser("spring")
    @Test
    public void startIndexingSuccessTest() throws Exception {
        MvcResult result = this.mockMvc.perform(get("/api/startIndexing"))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn();
        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result.getResponse().getContentType().equals(MediaType.APPLICATION_JSON_VALUE)).isTrue();
    }

    @Test
    public void startIndexingErrorTest() throws Exception {
        List<Thread> threads = new ArrayList<>();
        Thread thread = new Thread(() -> {
            try {
                this.mockMvc.perform(get("/api/startIndexing").with(user("spring"))).andExpect(status().isOk());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        threads.add(thread);
        Thread threadToFail = new Thread(() -> {
            try {
                MvcResult result = this.mockMvc.perform(get("/api/startIndexing").with(user("spring"))).andDo(print())
                        .andReturn();
                Assertions.assertThat(result.getResponse().getContentType().equals(MediaType.APPLICATION_JSON_VALUE)).isTrue();
                Assertions.assertThat(result.getResponse().getContentAsString().equals("{\"result\":false,\"error\":\"Indexing is already in progress\"}"))
                        .isTrue();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        threads.add(threadToFail);
        threads.forEach(t -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            t.start();
        });
    }

    @Test
    public void stopIndexingSuccessTest() throws Exception {
        List<Thread> threads = new ArrayList<>();
        Thread start = new Thread(() -> {
            try {
                this.mockMvc.perform(get("/api/startIndexing")
                                .with(user("spring")))
                        .andDo(print());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        threads.add(start);
        Thread stop = new Thread(() -> {
            try {
                MvcResult result = this.mockMvc.perform(get("/api/stopIndexing")
                                .with(user("spring")))
                        .andDo(print())
                        .andExpect(status().isOk())
                        .andReturn();
                Assertions.assertThat(result.getResponse().getContentAsString().equals("{\"result\":true}")).isTrue();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        threads.add(stop);
        for (Thread t : threads) {
            t.start();
            Thread.sleep(3000);
        }
    }

    @WithMockUser("spring")
    @Test
    public void stopIndexingErrorTest() throws Exception {
        MvcResult result = this.mockMvc.perform(get("/api/stopIndexing"))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn();
        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result.getResponse().getContentType().equals(MediaType.APPLICATION_JSON_VALUE)).isTrue();
        Assertions.assertThat(result.getResponse().getContentAsString().equals(
                "{\"result\":false,\"error\":\"Indexing is not in progress\"}"
        )).isTrue();
    }

    @WithMockUser("spring")
    @Test
    public void indexSeparatePageSuccess() throws Exception {
        this.mockMvc.perform(get("/api/startIndexing"))
                .andExpect(status().isOk());
        ConfigProperties.Site site = (ConfigProperties.Site) properties.getSites().toArray()[0];
        MvcResult result = this.mockMvc.perform(post("/api/indexPage?url=" + site.getUrl() + "/"))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn();
        Assertions.assertThat(result.getResponse().getContentType().equals(MediaType.APPLICATION_JSON_VALUE)).isTrue();
        Assertions.assertThat(result.getResponse().getContentAsString().equals("{\"result\":true}")).isTrue();
    }

    @WithMockUser("spring")
    @Test
    public void indexSeparatePageForAWrongWebsite() throws Exception {
        MvcResult result = this.mockMvc.perform(post("/api/indexPage?url=http://www.google.com"))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn();
        Assertions.assertThat(result.getResponse().getContentType().equals(MediaType.APPLICATION_JSON_VALUE)).isTrue();
        Assertions.assertThat(result.getResponse().getContentAsString().equals("{\"result\":false," +
                "\"error\":\"This page is outside of the specified websites\"}")).isTrue();
    }
}
