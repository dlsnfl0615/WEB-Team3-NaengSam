package com.naengsam.quick.global.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@link GlobalExceptionHandler} 가 Spring MVC 표준 예외를 공통 오류코드(COMMON_xxx)로
 * 올바르게 변환하는지 검증한다. 전체 컨텍스트 없이 테스트 전용 컨트롤러 + 핸들러만 standalone 으로 띄워
 * 각 예외를 실제 HTTP 디스패치로 유발한다.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 검증_실패시_COMMON_001_반환() throws Exception {
        mockMvc.perform(post("/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_001"))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    void 타입_불일치시_COMMON_002_반환() throws Exception {
        mockMvc.perform(get("/type").param("value", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_002"))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    void 필수_파라미터_누락시_COMMON_003_반환() throws Exception {
        mockMvc.perform(get("/param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_003"))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    void 필수_헤더_누락시_COMMON_003_반환() throws Exception {
        mockMvc.perform(get("/header"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_003"))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    void JSON_파싱_실패시_COMMON_004_반환() throws Exception {
        mockMvc.perform(post("/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_004"))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    void 지원하지_않는_메서드시_COMMON_006_반환() throws Exception {
        mockMvc.perform(post("/get-only"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_006"))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    void 지원하지_않는_컨텐츠타입시_COMMON_007_반환() throws Exception {
        mockMvc.perform(post("/body")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("plain text"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_007"))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    void 낙관적_락_충돌시_COMMON_008_반환() throws Exception {
        mockMvc.perform(get("/lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_008"))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    void 미분류_예외시_COMMON_010_반환() throws Exception {
        mockMvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_010"))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    /**
     * 각 표준 예외를 유발하기 위한 최소 엔드포인트 모음.
     */
    @RestController
    static class TestController {

        @PostMapping("/valid")
        public void valid(@Valid @RequestBody SampleRequest request) {
        }

        @GetMapping("/type")
        public void type(@RequestParam int value) {
        }

        @GetMapping("/param")
        public void param(@RequestParam String value) {
        }

        @GetMapping("/header")
        public void header(@RequestHeader("X-Foo") String value) {
        }

        @PostMapping("/body")
        public void body(@RequestBody SampleRequest request) {
        }

        @GetMapping("/get-only")
        public void getOnly() {
        }

        @GetMapping("/lock")
        public void lock() {
            throw new OptimisticLockingFailureException("optimistic lock failed");
        }

        @GetMapping("/boom")
        public void boom() {
            throw new IllegalStateException("boom");
        }
    }

    record SampleRequest(@NotBlank String name) {
    }
}
