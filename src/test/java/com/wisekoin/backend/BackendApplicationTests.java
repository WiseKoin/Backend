package com.wisekoin.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.sentry.DataCollection;
import io.sentry.KeyValueCollectionBehavior;
import io.sentry.spring.boot4.SentryProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "sentry.dsn=https://public@localhost/1")
class BackendApplicationTests {
    @Autowired
    private SentryProperties sentryProperties;

    @Test
    void contextLoads() {}

    @Test
    void sentryAutomaticSensitiveDataCollectionIsDisabled() {
        DataCollection dataCollection = sentryProperties.getDataCollection();

        assertFalse(dataCollection.getUserInfo());
        assertEquals(KeyValueCollectionBehavior.Mode.OFF, dataCollection.getCookies().getMode());
        assertEquals(
                KeyValueCollectionBehavior.Mode.OFF,
                dataCollection.getHttpHeaders().getRequest().getMode());
        assertEquals(
                KeyValueCollectionBehavior.Mode.OFF,
                dataCollection.getHttpHeaders().getResponse().getMode());
        assertTrue(dataCollection.getHttpBodies().isEmpty());
        assertEquals(
                KeyValueCollectionBehavior.Mode.OFF, dataCollection.getUrlQueryParams().getMode());
        assertFalse(dataCollection.getGraphql().getDocument());
        assertFalse(dataCollection.getGraphql().getVariables());
        assertFalse(dataCollection.getFilePaths());
    }
}
