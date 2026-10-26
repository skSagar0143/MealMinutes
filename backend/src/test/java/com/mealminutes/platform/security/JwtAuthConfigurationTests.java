package com.mealminutes.platform.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthConfigurationTests {
    @TempDir Path directory;

    @Test
    void signsAndVerifiesRs256TokenUsingConfiguredPemKeys() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        Path privatePath = directory.resolve("private.pem");
        Path publicPath = directory.resolve("public.pem");
        Files.writeString(privatePath, pem("PRIVATE KEY", pair.getPrivate().getEncoded()));
        Files.writeString(publicPath, pem("PUBLIC KEY", pair.getPublic().getEncoded()));

        var configuration = new JwtAuthConfiguration();
        var loader = new DefaultResourceLoader();
        var encoder = configuration.jwtEncoder("file:" + privatePath, "file:" + publicPath, loader);
        var decoder = configuration.jwtDecoder("file:" + publicPath, "https://mealminutes.test", loader);
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer("https://mealminutes.test").subject("user-1")
                .issuedAt(now).expiresAt(now.plusSeconds(120)).build();
        var header = JwsHeader.with(SignatureAlgorithm.RS256).keyId("mealminutes-rs256-v1").build();

        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        assertThat(decoder.decode(token).getSubject()).isEqualTo("user-1");
        assertThat(decoder.decode(token).getIssuer().toString()).isEqualTo("https://mealminutes.test");
    }

    private String pem(String type, byte[] bytes) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(bytes)
                + "\n-----END " + type + "-----\n";
    }
}