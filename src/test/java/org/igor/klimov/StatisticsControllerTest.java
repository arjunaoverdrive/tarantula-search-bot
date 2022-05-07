package org.igor.klimov;

import org.assertj.core.api.Assertions;
import org.igor.klimov.app.config.ConfigProperties;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
public class StatisticsControllerTest {

    public StatisticsControllerTest() {
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ConfigProperties properties;

    @Test
    public void statisticsTest() throws Exception{
        ConfigProperties.Site site = (ConfigProperties.Site) properties.getSites().toArray()[0];
        this.mockMvc.perform(get("/api/startIndexing"));
        MvcResult result = this.mockMvc
                .perform(get("/api/statistics"))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn();
        Assertions.assertThat(result).isNotNull();
        String contentAsString = result.getResponse().getContentAsString();
        Assertions.assertThat(contentAsString.contains(site.getUrl())).isTrue();
        Assertions.assertThat(contentAsString.contains(site.getName())).isTrue();
        List<String> fields = List.of("result", "statistics", "total", "sites", "pages", "lemmas", "isIndexing", "detailed"
        , "url", "name", "status", "statusTime", "error");

        fields.forEach(f -> Assertions.assertThat(contentAsString.contains(f)).isTrue());
    }
}
