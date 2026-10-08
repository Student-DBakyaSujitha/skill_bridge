package com.skillbridge;

import org.junit.jupiter.api.Test;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {
    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void verifiesNewScryptHashes() {
        String hash = hasher.hash("SkillBridge42");

        assertTrue(hasher.matches("SkillBridge42", hash));
        assertFalse(hasher.matches("not-the-password", hash));
    }

    @Test
    void verifiesExistingWerkzeugPbkdf2Hashes() throws Exception {
        String password = "LegacyPass42";
        String salt = "existing-salt";
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(),
                salt.getBytes(StandardCharsets.UTF_8), 1_000, 32 * 8);
        byte[] derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).getEncoded();
        String hash = "pbkdf2:sha256:1000$" + salt + "$" + HexFormat.of().formatHex(derived);

        assertTrue(hasher.matches(password, hash));
    }
}
