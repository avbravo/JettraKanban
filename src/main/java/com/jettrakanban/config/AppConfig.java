package com.jettrakanban.config;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.Properties;

public class AppConfig {
    private static final String CONFIG_RESOURCE = "/application.properties";
    private static final String CREDENTIALS_FILE = "credentials.md";
    private static final String CREDENTIALS_PREFIX = "ENC:";

    private final Properties properties = new Properties();

    public AppConfig() {
        loadDefaults();
        loadLocalFile();
    }

    private void loadDefaults() {
        try (InputStream is = getClass().getResourceAsStream(CONFIG_RESOURCE)) {
            if (is != null) {
                properties.load(is);
            }
        } catch (IOException ignored) {
            // Optional defaults file, ignore when missing or malformed.
        }
    }

    private void loadLocalFile() {
        Path local = Path.of("config.properties");
        if (!Files.exists(local)) {
            return;
        }

        try (InputStream is = Files.newInputStream(local)) {
            properties.load(is);
        } catch (IOException ignored) {
            // Local config is optional, fail softly.
        }
    }

    public String githubToken() {
        return envFirst("GITHUB_TOKEN", "github.token").orElse("");
    }

    public String githubUsername() {
        return envFirst("GITHUB_USERNAME", "github.username").orElse("");
    }

    public String githubPassword() {
        return envFirst("GITHUB_PASSWORD", "github.password").orElse("");
    }

    public String githubProjectId() {
        return envFirst("GITHUB_PROJECT_ID", "github.projectId").orElse("");
    }

    public String githubProjectUrl() {
        return envFirst("GITHUB_PROJECT_URL", "github.projectUrl").orElse("");
    }

    public void saveEncryptedCredentials(String username, String password, String token, String projectUrl) throws IOException {
        Properties data = new Properties();
        data.setProperty("github.username", safe(username));
        data.setProperty("github.password", safe(password));
        data.setProperty("github.token", safe(token));
        data.setProperty("github.projectUrl", safe(projectUrl));

        StringBuilder raw = new StringBuilder();
        for (String name : data.stringPropertyNames()) {
            raw.append(name).append('=').append(data.getProperty(name)).append('\n');
        }

        String encrypted = encrypt(raw.toString());
        String content = "# JettraKanban Credentials (encrypted)\n"
                + "# Auto-generated. Do not edit manually.\n\n"
                + CREDENTIALS_PREFIX + encrypted + "\n";

        Files.writeString(Path.of(CREDENTIALS_FILE), content, StandardCharsets.UTF_8);
    }

    public StoredCredentials loadEncryptedCredentials() {
        Path path = Path.of(CREDENTIALS_FILE);
        if (!Files.exists(path)) {
            return StoredCredentials.empty();
        }

        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            String encryptedLine = text.lines()
                    .map(String::trim)
                    .filter(line -> line.startsWith(CREDENTIALS_PREFIX))
                    .findFirst()
                    .orElse("");

            if (encryptedLine.isBlank()) {
                return StoredCredentials.empty();
            }

            String payload = decrypt(encryptedLine.substring(CREDENTIALS_PREFIX.length()));
            Properties data = new Properties();
            data.load(new java.io.StringReader(payload));

            return new StoredCredentials(
                    data.getProperty("github.username", ""),
                    data.getProperty("github.password", ""),
                    data.getProperty("github.token", ""),
                    data.getProperty("github.projectUrl", "")
            );
        } catch (IOException | GeneralSecurityException ex) {
            return StoredCredentials.empty();
        }
    }

    public boolean hasEncryptedCredentials() {
        return Files.exists(Path.of(CREDENTIALS_FILE));
    }

    public void deleteEncryptedCredentials() throws IOException {
        Files.deleteIfExists(Path.of(CREDENTIALS_FILE));
    }

    private Optional<String> envFirst(String envKey, String propertyKey) {
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isBlank()) {
            return Optional.of(envValue.trim());
        }

        String propertyValue = properties.getProperty(propertyKey);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return Optional.of(propertyValue.trim());
        }

        return Optional.empty();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String encrypt(String plainText) throws IOException {
        try {
            byte[] iv = new byte[12];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException ex) {
            throw new IOException("Unable to encrypt credentials", ex);
        }
    }

    private String decrypt(String encryptedText) throws GeneralSecurityException {
        byte[] combined = Base64.getDecoder().decode(encryptedText);
        if (combined.length < 13) {
            throw new GeneralSecurityException("Invalid encrypted payload");
        }

        byte[] iv = new byte[12];
        byte[] payload = new byte[combined.length - 12];
        System.arraycopy(combined, 0, iv, 0, iv.length);
        System.arraycopy(combined, iv.length, payload, 0, payload.length);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
        byte[] decrypted = cipher.doFinal(payload);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    private SecretKeySpec key() throws GeneralSecurityException {
        String source = Optional.ofNullable(System.getenv("JETTRAKANBAN_SECRET"))
                .filter(value -> !value.isBlank())
                .orElseGet(() -> System.getProperty("user.name", "jettra")
                        + "@" + System.getProperty("user.home", "home"));

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(source.getBytes(StandardCharsets.UTF_8));
        byte[] keyBytes = new byte[16];
        System.arraycopy(hash, 0, keyBytes, 0, keyBytes.length);
        return new SecretKeySpec(keyBytes, "AES");
    }

    public record StoredCredentials(String username, String password, String token, String projectUrl) {
        public static StoredCredentials empty() {
            return new StoredCredentials("", "", "", "");
        }
    }
}
