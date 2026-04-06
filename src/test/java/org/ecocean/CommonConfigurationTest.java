package org.ecocean;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CommonConfigurationTest {

    @Test
    void getWildbookDataDir_returnsProvidedValue() {
        assertEquals("/custom/path", CommonConfiguration.getWildbookDataDir("/custom/path"));
    }

    @Test
    void getWildbookDataDir_defaultsWhenNull() {
        assertEquals(
            "/usr/local/tomcat/webapps/wildbook_data_dir",
            CommonConfiguration.getWildbookDataDir((String) null)
        );
    }

    @Test
    void getWildbookDataDir_defaultsWhenBlank() {
        assertEquals(
            "/usr/local/tomcat/webapps/wildbook_data_dir",
            CommonConfiguration.getWildbookDataDir("   ")
        );
    }

    @Test
    void getWildbookDataDir_trimsWhitespaceFromValue() {
        assertEquals("/custom/path", CommonConfiguration.getWildbookDataDir("  /custom/path  "));
    }
}
