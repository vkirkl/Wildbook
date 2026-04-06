package org.ecocean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Answers.CALLS_REAL_METHODS;

class JsonPropertiesTest {

    @TempDir
    Path tempDir;

    @Test
    void loadFullPath_loadsOverrideWhenPresent() throws Exception {
        // Write a minimal valid JSON file at the override path
        Path bundlesDir = tempDir.resolve("WEB-INF/classes/bundles");
        Files.createDirectories(bundlesDir);
        Path overrideFile = bundlesDir.resolve("IA.json");
        Files.writeString(overrideFile, "{\"test\": true}");

        try (MockedStatic<CommonConfiguration> mockCfg =
                 mockStatic(CommonConfiguration.class, CALLS_REAL_METHODS)) {
            mockCfg.when(CommonConfiguration::getWildbookDataDir)
                   .thenReturn(tempDir.toString());

            JsonProperties jp = new JsonProperties("IA.json");
            assertEquals(overrideFile.toString(), jp.getFullPath());
        }
    }

    @Test
    void constructor_setsNullPathWhenNeitherLocationExists() throws Exception {
        try (MockedStatic<CommonConfiguration> mockCfg =
                 mockStatic(CommonConfiguration.class, CALLS_REAL_METHODS)) {
            mockCfg.when(CommonConfiguration::getWildbookDataDir)
                   .thenReturn("/nonexistent/path/that/does/not/exist");

            // JsonProperties constructor catches the exception — verify via getFullPath being null
            JsonProperties jp = new JsonProperties("IA.json");
            assertNull(jp.getFullPath(),
                "fullPath should be null when neither override nor default file exists");
        }
    }
}
