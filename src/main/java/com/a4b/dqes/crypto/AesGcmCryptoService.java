/**
 * Created: Jan 09, 2026 10:21:04 AM
 * Copyright © 2026 by A4B. All rights reserved
 */
package com.a4b.dqes.crypto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class AesGcmCryptoService implements CryptoService {

  private static final String ALG_AES_GCM = "AES_GCM";
  private static final String CIPHER = "AES/GCM/NoPadding";
  private static final int IV_LEN = 12;          // 96-bit
  private static final int TAG_LEN_BITS = 128;   // 16 bytes tag
  private static final String PREFIX = "v1";

  private final SecretKeySpec keySpec;
  private final byte[] aadBytes;
  private final SecureRandom random = new SecureRandom();

  public AesGcmCryptoService(
      @Value("${dqes.crypto.masterKeyBase64}") String masterKeyBase64,
      @Value("${dqes.crypto.aad:dqes-password}") String aad
  ) {
    byte[] key = Base64.getDecoder().decode(masterKeyBase64);
    if (key.length != 16 && key.length != 24 && key.length != 32) {
      throw new IllegalArgumentException("masterKey must be 16/24/32 bytes (Base64)");
    }
    this.keySpec = new SecretKeySpec(key, "AES");
    this.aadBytes = aad.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public String encrypt(String plaintext, String alg) {
    if (plaintext == null) return null;

    if (alg == null || alg.isBlank() || ALG_AES_GCM.equalsIgnoreCase(alg)) {
      try {
        byte[] iv = new byte[IV_LEN];
        random.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(CIPHER);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LEN_BITS, iv));
        cipher.updateAAD(aadBytes);

        byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        String ivB64 = Base64.getEncoder().encodeToString(iv);
        String ctB64 = Base64.getEncoder().encodeToString(ct);
        return PREFIX + ":" + ivB64 + ":" + ctB64;
      } catch (Exception e) {
        throw new CryptoException("Encrypt failed", e);
      }
    }
    throw new IllegalArgumentException("Unsupported alg: " + alg);
  }

  @Override
  public String decrypt(String ciphertext, String alg) {
    if (ciphertext == null) return null;

    if (alg == null || alg.isBlank() || ALG_AES_GCM.equalsIgnoreCase(alg)) {
      try {
        // accept both "v1:iv:ct" or legacy base64 only (optional)
        if (!ciphertext.startsWith(PREFIX + ":")) {
          // If you want to forbid legacy, remove this block.
          // Here we assume it's base64(ct) with a fixed IV - NOT recommended.
          throw new IllegalArgumentException("Ciphertext must start with v1:");
        }

        String[] parts = ciphertext.split(":", 3);
        if (parts.length != 3) {
          throw new IllegalArgumentException("Invalid ciphertext format, expected v1:iv:ct");
        }

        byte[] iv = Base64.getDecoder().decode(parts[1]);
        byte[] ct = Base64.getDecoder().decode(parts[2]);

        Cipher cipher = Cipher.getInstance(CIPHER);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LEN_BITS, iv));
        cipher.updateAAD(aadBytes);

        byte[] pt = cipher.doFinal(ct);
        return new String(pt, StandardCharsets.UTF_8);
      } catch (Exception e) {
        throw new CryptoException("Decrypt failed", e);
      }
    }
    throw new IllegalArgumentException("Unsupported alg: " + alg);
  }

  public static class CryptoException extends RuntimeException {
    public CryptoException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}

