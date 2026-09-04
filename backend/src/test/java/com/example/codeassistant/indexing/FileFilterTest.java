package com.example.codeassistant.indexing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FileFilterTest {

    private final FileFilter fileFilter = new FileFilter();

    @Test
    void indexesCommonSourceFiles() {
        assertThat(fileFilter.shouldIndex("src/main/java/App.java", 1000L)).isTrue();
        assertThat(fileFilter.shouldIndex("frontend/app/page.tsx", 1000L)).isTrue();
        assertThat(fileFilter.shouldIndex("README.md", 500L)).isTrue();
    }

    @Test
    void ignoresBuildAndDependencyDirectories() {
        assertThat(fileFilter.shouldIndex("node_modules/react/index.js", 1000L)).isFalse();
        assertThat(fileFilter.shouldIndex("target/classes/App.class", 1000L)).isFalse();
        assertThat(fileFilter.shouldIndex(".git/HEAD", 100L)).isFalse();
    }

    @Test
    void ignoresBinaryAndMediaExtensions() {
        assertThat(fileFilter.shouldIndex("logo.png", 1000L)).isFalse();
        assertThat(fileFilter.shouldIndex("archive.zip", 1000L)).isFalse();
        assertThat(fileFilter.shouldIndex("app.jar", 1000L)).isFalse();
    }

    @Test
    void ignoresFilesLargerThanTheConfiguredLimit() {
        long tooLarge = FileFilter.DEFAULT_MAX_FILE_SIZE_BYTES + 1;
        assertThat(fileFilter.shouldIndex("BigFile.java", tooLarge)).isFalse();
    }

    @Test
    void allowsUnknownSizeToBeIndexed() {
        assertThat(fileFilter.shouldIndex("App.java", null)).isTrue();
    }
}
