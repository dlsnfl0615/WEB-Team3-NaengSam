package com.naengsam.quick.domain.upload.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.naengsam.quick.domain.upload.service.InMemoryFileStore.StoredFile;
import org.junit.jupiter.api.Test;

/**
 * 저장 전/후 exists·find의 동작을 검증한다.
 */
class InMemoryFileStoreTest {

    private final InMemoryFileStore fileStore = new InMemoryFileStore();

    @Test
    void 저장하기_전에는_존재하지_않고_find는_비어있다() {
        assertThat(fileStore.exists("uploads/x/y-a.png")).isFalse();
        assertThat(fileStore.find("uploads/x/y-a.png")).isEmpty();
    }

    @Test
    void 저장하면_exists가_true이고_find가_저장한_내용을_그대로_반환한다() {
        byte[] bytes = {1, 2, 3};

        fileStore.save("uploads/x/y-a.png", bytes, "image/png");

        assertThat(fileStore.exists("uploads/x/y-a.png")).isTrue();
        StoredFile found = fileStore.find("uploads/x/y-a.png").orElseThrow();
        assertThat(found.bytes()).isEqualTo(bytes);
        assertThat(found.contentType()).isEqualTo("image/png");
    }
}
