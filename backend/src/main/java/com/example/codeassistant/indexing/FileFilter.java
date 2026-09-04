package com.example.codeassistant.indexing;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Decides which files from a repository tree are worth indexing. Kept
 * deliberately simple (extension allow-list + path ignore-list) rather than
 * content sniffing, so it stays fast, predictable, and easy to unit test.
 */
@Component
public class FileFilter {

    private static final Set<String> IGNORED_PATH_SEGMENTS = Set.of(
            ".git/", "node_modules/", "target/", "build/", "dist/", ".next/", "coverage/"
    );

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".java", ".js", ".ts", ".tsx", ".jsx", ".py", ".go", ".rs", ".c", ".cpp", ".h", ".hpp",
            ".cs", ".html", ".css", ".scss", ".sql", ".json", ".xml", ".yaml", ".yml", ".md"
    );

    public static final long DEFAULT_MAX_FILE_SIZE_BYTES = 200_000;

    public boolean shouldIndex(String path, Long sizeBytes) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String lowerPath = path.toLowerCase();

        for (String ignored : IGNORED_PATH_SEGMENTS) {
            if (lowerPath.contains(ignored)) {
                return false;
            }
        }

        boolean hasAllowedExtension = ALLOWED_EXTENSIONS.stream().anyMatch(lowerPath::endsWith);
        if (!hasAllowedExtension) {
            return false;
        }

        return sizeBytes == null || sizeBytes <= DEFAULT_MAX_FILE_SIZE_BYTES;
    }
}
