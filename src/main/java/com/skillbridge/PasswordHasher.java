package com.skillbridge;

import org.bouncycastle.crypto.generators.SCrypt;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

@Component
public class PasswordHasher {
    private static final int SCRYPT_N = 32768;
    private static final int SCRYPT_R = 8;
    private static final int SCRYPT_P = 1;
    private static final int SCRYPT_BYTES = 64;
    private static final SecureRandom RANDOM = new SecureRandom();

    public String hash(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        String saltText = HexFormat.of().formatHex(salt);
        byte[] derived = SCrypt.generate(
                password.getBytes(StandardCharsets.UTF_8), saltText.getBytes(StandardCharsets.UTF_8),
                SCRYPT_N, SCRYPT_R, SCRYPT_P, SCRYPT_BYTES);
        return "scrypt:" + SCRYPT_N + ":" + SCRYPT_R + ":" + SCRYPT_P
                + "$" + saltText + "$" + HexFormat.of().formatHex(derived);
    }

    public boolean matches(String password, String encoded) {
        if (password == null || encoded == null) {
            return false;
        }
        String[] parts = encoded.split("\\$", -1);
        if (parts.length != 3) {
            return false;
        }
        try {
            byte[] expected = HexFormat.of().parseHex(parts[2]);
            byte[] actual;
            if (parts[0].startsWith("scrypt:")) {
                actual = verifyScrypt(password, parts[0], parts[1], expected.length);
            } else if (parts[0].startsWith("pbkdf2:sha256:")) {
                actual = verifyPbkdf2(password, parts[0], parts[1], expected.length);
            } else {
                return false;
            }
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            return false;
        }
    }

    private static byte[] verifyScrypt(String password, String method, String salt, int outputLength) {
        String[] fields = method.split(":");
        if (fields.length != 4) {
            throw new IllegalArgumentException("Unsupported scrypt parameters.");
        }
        int cost = Integer.parseInt(fields[1]);
        int blockSize = Integer.parseInt(fields[2]);
        int parallelization = Integer.parseInt(fields[3]);
        if (cost < 2 || cost > 1_048_576 || blockSize < 1 || blockSize > 32
                || parallelization < 1 || parallelization > 16 || outputLength < 1 || outputLength > 128) {
            throw new IllegalArgumentException("Unsafe scrypt parameters.");
        }
        return SCrypt.generate(
                password.getBytes(StandardCharsets.UTF_8), salt.getBytes(StandardCharsets.UTF_8),
                cost, blockSize, parallelization, outputLength);
    }

    private static byte[] verifyPbkdf2(String password, String method, String salt, int outputLength)
            throws GeneralSecurityException {
        String[] fields = method.split(":");
        int iterations = fields.length == 3 ? Integer.parseInt(fields[2]) : 260_000;
        if (iterations < 1 || iterations > 5_000_000 || outputLength < 1 || outputLength > 128) {
            throw new IllegalArgumentException("Unsafe PBKDF2 parameters.");
        }
        PBEKeySpec spec = new PBEKeySpec(
                password.toCharArray(), salt.getBytes(StandardCharsets.UTF_8), iterations, outputLength * 8);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }
}
