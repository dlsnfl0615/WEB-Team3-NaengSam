package com.naengsam.quick.domain.upload.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.naengsam.quick.domain.upload.service.InMemoryFileStore;
import com.naengsam.quick.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * PUT으로 저장한 바이트를 같은 key로 GET 했을 때 그대로 돌려받는지, 없는 key면 FILE_005로 거부하는지 검증한다.
 */
class DevStorageControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        InMemoryFileStore fileStore = new InMemoryFileStore();
        mockMvc = MockMvcBuilders.standaloneSetup(new DevStorageController(fileStore))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void PUT으로_저장한_바이트를_같은_key로_GET하면_그대로_반환한다() throws Exception {
        byte[] bytes = {1, 2, 3};

        mockMvc.perform(put("/api/v1/upload/dev-storage")
                        .param("key", "uploads/x/y-a.png")
                        .contentType(MediaType.IMAGE_PNG)
                        .content(bytes))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/upload/dev-storage")
                        .param("key", "uploads/x/y-a.png"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(bytes));
    }

    @Test
    void 저장되지_않은_key로_GET하면_FILE_005로_거부한다() throws Exception {
        mockMvc.perform(get("/api/v1/upload/dev-storage")
                        .param("key", "uploads/x/none.png"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FILE_005"));
    }
}
