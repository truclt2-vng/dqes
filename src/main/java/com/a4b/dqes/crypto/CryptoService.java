/**
 * Created: Jan 09, 2026 10:22:15 AM
 * Copyright © 2026 by A4B. All rights reserved
 */
package com.a4b.dqes.crypto;

public interface CryptoService {
  String encrypt(String plaintext, String alg);
  String decrypt(String ciphertext, String alg);
}

