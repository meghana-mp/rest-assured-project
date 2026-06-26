package com.apiframework.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Thread-safe singleton that loads API configuration from config.properties
 * and allows environment-variable overrides for CI/CD pipelines.
 *
 * Override convention: replace dots with underscores and uppercase.
 *   github.token  →  GITHUB_TOKEN
 *   booker.username  →  BOOKER_USERNAME
 */
public class ConfigManager {

    private static final Logger log = LoggerFactory.getLogger(ConfigManager.class);
    private static final String CONFIG_FILE = "config.properties";

    private static volatile ConfigManager instance;
    private final Properties properties;

    private ConfigManager() {
        properties = new Properties();
        loadProperties();
    }

    public static ConfigManager getInstance() {
        if (instance == null) {
            synchronized (ConfigManager.class) {
                if (instance == null) {
                    instance = new ConfigManager();
                }
            }
        }
        return instance;
    }

    private void loadProperties() {
        try (InputStream input = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(CONFIG_FILE)) {

            if (input == null) {
                throw new RuntimeException(CONFIG_FILE + " not found on classpath. "
                        + "Place it under src/test/resources/.");
            }
            properties.load(input);
            log.info("Loaded configuration from {}", CONFIG_FILE);

        } catch (IOException e) {
            throw new RuntimeException("Failed to load " + CONFIG_FILE, e);
        }
    }

    /**
     * Returns the property value, preferring an environment variable over the file value.
     * Returns {@code null} when the key is absent in both.
     */
    public String getProperty(String key) {
        // Environment variable: "github.token" → "GITHUB_TOKEN"
        String envKey = key.toUpperCase().replace('.', '_');
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isBlank()) {
            log.debug("Using env-var override for '{}' (key: {})", key, envKey);
            return envValue;
        }

        String value = properties.getProperty(key);
        if (value == null) {
            log.warn("Configuration key '{}' not found in {} or environment", key, CONFIG_FILE);
        }
        return value;
    }

    public String getProperty(String key, String defaultValue) {
        String value = getProperty(key);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }

    public long getLongProperty(String key, long defaultValue) {
        String value = getProperty(key);
        try {
            return (value != null) ? Long.parseLong(value.trim()) : defaultValue;
        } catch (NumberFormatException e) {
            log.warn("Cannot parse '{}' as long for key '{}'; using default {}", value, key, defaultValue);
            return defaultValue;
        }
    }
}
