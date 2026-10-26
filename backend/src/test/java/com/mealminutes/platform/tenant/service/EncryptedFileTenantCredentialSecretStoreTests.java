package com.mealminutes.platform.tenant.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptedFileTenantCredentialSecretStoreTests {
    @TempDir Path directory;
    private String key() { return Base64.getEncoder().encodeToString(new byte[32]); }

    @Test void encryptsAtRestAndCanRecoverCredentialsAcrossInstances() throws Exception {
        Path file = directory.resolve("secrets.properties");
        var firstStore = new EncryptedFileTenantCredentialSecretStore(file.toString(), key());
        var first = firstStore.createOrGet("secret://tenant/a", "mm_tenant_a", "A".repeat(43));
        assertThat(first.username()).isEqualTo("mm_tenant_a");
        String onDisk = Files.readString(file);
        assertThat(onDisk).doesNotContain("mm_tenant_a").doesNotContain("A".repeat(43));

        var secondStore = new EncryptedFileTenantCredentialSecretStore(file.toString(), key());
        assertThat(secondStore.get("secret://tenant/a")).isEqualTo(first);
        assertThat(secondStore.createOrGet("secret://tenant/a", "mm_tenant_a", "B".repeat(43))).isEqualTo(first);
    }

    @Test void rejectsInvalidKeyAndUnknownReference() {
        assertThatThrownBy(() -> new EncryptedFileTenantCredentialSecretStore(
                directory.resolve("bad.properties").toString(), "not-base64")).isInstanceOf(IllegalArgumentException.class);
        var store = new EncryptedFileTenantCredentialSecretStore(directory.resolve("empty.properties").toString(), key());
        assertThatThrownBy(() -> store.get("secret://missing")).isInstanceOf(IllegalArgumentException.class);
    }
}