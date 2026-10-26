package com.mealminutes.platform.tenant.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Properties;

/**
 * Local-only encrypted file secret store. It is not a replacement for a
 * managed secret manager in multi-instance production deployments.
 */
@Component
@Profile("local")
@ConditionalOnProperty(prefix = "mealminutes.tenant-provisioning", name = "enabled", havingValue = "true")
public class EncryptedFileTenantCredentialSecretStore implements TenantCredentialSecretStore {
    private static final int IV_BYTES = 12;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Path file;
    private final SecretKeySpec key;

    public EncryptedFileTenantCredentialSecretStore(
            @Value("${mealminutes.tenant-provisioning.local-secret-file:./.local-secrets/tenant-db-secrets.properties}") String file,
            @Value("${mealminutes.tenant-provisioning.local-secret-key:}") String base64Key) {
        if (file == null || file.isBlank()) throw new IllegalArgumentException("Local secret-store path is required");
        if (base64Key == null || base64Key.isBlank()) throw new IllegalArgumentException("A local AES-256 key is required");
        byte[] decoded;
        try { decoded = Base64.getDecoder().decode(base64Key); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Local secret-store key must be Base64", ex); }
        if (decoded.length != 32) throw new IllegalArgumentException("Local secret-store key must decode to exactly 32 bytes");
        this.file = Path.of(file).toAbsolutePath().normalize();
        this.key = new SecretKeySpec(decoded, "AES");
        Arrays.fill(decoded, (byte) 0);
    }

    @Override
    public synchronized TenantCredentials createOrGet(String secretReference, String proposedUsername, String proposedPassword) {
        requireReference(secretReference);
        TenantCredentials proposed = new TenantCredentials(proposedUsername, proposedPassword);
        Properties properties = load();
        String storageKey = referenceKey(secretReference);
        String existing = properties.getProperty(storageKey);
        if (existing != null) return decrypt(existing);
        properties.setProperty(storageKey, encrypt(proposed.username() + "\n" + proposed.password()));
        persist(properties);
        return proposed;
    }

    @Override
    public synchronized TenantCredentials get(String secretReference) {
        requireReference(secretReference);
        String encrypted = load().getProperty(referenceKey(secretReference));
        if (encrypted == null) throw new IllegalArgumentException("Tenant database secret reference does not exist");
        return decrypt(encrypted);
    }

    private Properties load() {
        Properties properties = new Properties();
        if (!Files.exists(file)) return properties;
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
            return properties;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read local encrypted secret store", exception);
        }
    }

    private void persist(Properties properties) {
        Path parent = file.getParent();
        Path temporary = null;
        try {
            Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, "tenant-secrets-", ".tmp");
            try (OutputStream output = Files.newOutputStream(temporary)) {
                properties.store(output, "Encrypted local tenant secret references; values are AES-GCM ciphertext");
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.setPosixFilePermissions(file, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) { }
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to persist local encrypted secret store", exception);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (Exception ignored) { }
            }
        }
    }

    private String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to encrypt tenant credentials", exception);
        }
    }

    private TenantCredentials decrypt(String encoded) {
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            if (combined.length <= IV_BYTES) throw new IllegalArgumentException("Invalid encrypted secret");
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(combined, IV_BYTES, combined.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            String plaintext = new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
            int separator = plaintext.indexOf('\n');
            if (separator <= 0) throw new IllegalArgumentException("Invalid stored tenant credential payload");
            return new TenantCredentials(plaintext.substring(0, separator), plaintext.substring(separator + 1));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to decrypt tenant credentials; verify the local secret-store key", exception);
        }
    }

    private static String referenceKey(String reference) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(reference.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void requireReference(String reference) {
        if (reference == null || reference.isBlank() || reference.length() > 255)
            throw new IllegalArgumentException("Valid secret reference is required");
    }
}