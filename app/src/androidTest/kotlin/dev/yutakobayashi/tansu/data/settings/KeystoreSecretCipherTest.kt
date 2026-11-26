package dev.yutakobayashi.tansu.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.Base64

class KeystoreSecretCipherTest {
  private val cipher = KeystoreSecretCipher(alias = "tansu.test.api_key")

  @Test
  fun encrypt_roundTripsWithoutPlaintext() {
    val secret = "sk-test-0123456789"

    val first = cipher.encrypt(secret)
    val second = cipher.encrypt(secret)

    assertFalse(first.contains(secret))
    assertNotEquals("IV must differ per encryption", first, second)
    assertEquals(secret, cipher.decrypt(first))
    assertEquals(secret, cipher.decrypt(second))
  }

  @Test(expected = javax.crypto.AEADBadTagException::class)
  fun decrypt_rejectsTamperedCiphertext() {
    val (version, iv, body) = cipher.encrypt("sk-test").split(":")
    val bytes = Base64.getDecoder().decode(body)
    bytes[0] = (bytes[0].toInt() xor 0x01).toByte()
    cipher.decrypt(listOf(version, iv, Base64.getEncoder().encodeToString(bytes)).joinToString(":"))
  }
}
