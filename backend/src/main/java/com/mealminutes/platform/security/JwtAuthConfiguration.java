package com.mealminutes.platform.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "mealminutes.security", name = "enabled", havingValue = "true")
public class JwtAuthConfiguration {
    @Bean
    JwtEncoder jwtEncoder(@Value("${mealminutes.security.private-key-location}") String privateKeyLocation,
                          @Value("${mealminutes.security.public-key-location}") String publicKeyLocation,
                          ResourceLoader resourceLoader) {
        RSAPrivateKey privateKey = readPrivateKey(resourceLoader, privateKeyLocation);
        RSAPublicKey publicKey = readPublicKey(resourceLoader, publicKeyLocation);
        RSAKey jwk = new RSAKey.Builder(publicKey).privateKey(privateKey).keyID("mealminutes-rs256-v1").build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(jwk)));
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${mealminutes.security.public-key-location}") String publicKeyLocation,
                          @Value("${mealminutes.security.issuer}") String issuer,
                          ResourceLoader resourceLoader) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(readPublicKey(resourceLoader, publicKeyLocation)).build();
        OAuth2TokenValidator<Jwt> validator = JwtValidators.createDefaultWithIssuer(issuer);
        decoder.setJwtValidator(validator);
        return decoder;
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    private static RSAPrivateKey readPrivateKey(ResourceLoader loader, String location) {
        try {
            byte[] encoded = pemBytes(loader.getResource(requireLocation(location)));
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to load configured PKCS#8 RSA private key", ex);
        }
    }

    private static RSAPublicKey readPublicKey(ResourceLoader loader, String location) {
        try {
            byte[] encoded = pemBytes(loader.getResource(requireLocation(location)));
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(encoded));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to load configured X.509 RSA public key", ex);
        }
    }

    private static String requireLocation(String location) {
        if (location == null || location.isBlank()) {
            throw new IllegalStateException("RSA key locations are required when security is enabled");
        }
        return location;
    }

    private static byte[] pemBytes(Resource resource) throws Exception {
        String pem;
        try (var input = resource.getInputStream()) {
            pem = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        String base64 = pem.replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "").replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }
}