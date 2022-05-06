package org.igor.klimov;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource("/application-test.properties")
public class SiteControllerTest {

    public SiteControllerTest() {
    }

    @Autowired
    MockMvc mockMvc;

    @Test
    public void startIndexingTest() throws Exception {
        MvcResult result = this.mockMvc.perform(get("/api/startIndexing"))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn();
        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result.getResponse().getContentType().equals("application/json"));
    }

    @Test
    public void stopIndexingSuccessTest() throws Exception {
        List<Thread> threads = new ArrayList<>();
        Thread start = new Thread(()-> {
            try {
                this.mockMvc.perform(get("/api/startIndexing"))
                        .andDo(print());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        threads.add(start);
        Thread stop = new Thread(()-> {
            try {
                MvcResult result = this.mockMvc.perform(get("/api/stopIndexing"))
                        .andDo(print())
                        .andExpect(status().isOk())
                        .andReturn();
                Assertions.assertThat(result.getResponse().getContentAsString().equals("{'result':true}"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        threads.add(stop);
        for(Thread t : threads){
            t.start();
            Thread.sleep(5000);
        }
    }

    @Test
    public void stopIndexingErrorTest()throws Exception{
        MvcResult result = this.mockMvc.perform(get("/api/stopIndexing"))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn();
        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result.getResponse().getContentType().equals("application/json"));
        Assertions.assertThat(result.getResponse().getContentAsString().equals(
                "{'result':false, 'error': 'Индексация не запущена'}"
        ));
    }
}
