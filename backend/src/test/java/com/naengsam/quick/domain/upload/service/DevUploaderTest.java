package com.naengsam.quick.domain.upload.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * dev-storage 엔드포인트를 가리키는 URL을 발급하는지, exists는 {@link InMemoryFileStore}에 그대로 위임하는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class DevUploaderTest {

    @Mock
    private InMemoryFileStore fileStore;

    @InjectMocks
    private DevUploader devUploader;

    @BeforeEach
    void setUp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void 업로드_URL은_dev_storage_엔드포인트를_가리킨다() {
        String url = devUploader.generateUploadUrl("uploads/x/y-a.png", "image/png");

        assertThat(url).contains("/api/v1/upload/dev-storage?key=uploads%2Fx%2Fy-a.png");
    }

    @Test
    void 다운로드_URL도_dev_storage_엔드포인트를_가리킨다() {
        String url = devUploader.generateDownloadUrl("uploads/x/y-a.png");

        assertThat(url).contains("/api/v1/upload/dev-storage?key=uploads%2Fx%2Fy-a.png");
    }

    @Test
    void exists는_InMemoryFileStore에_그대로_위임한다() {
        when(fileStore.exists("uploads/x/y-a.png")).thenReturn(true);

        boolean result = devUploader.exists("uploads/x/y-a.png");

        assertThat(result).isTrue();
    }
}
