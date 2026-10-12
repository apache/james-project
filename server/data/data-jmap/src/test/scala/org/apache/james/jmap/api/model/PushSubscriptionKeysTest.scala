/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.jmap.api.model

import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.interfaces.{ECPrivateKey, ECPublicKey}
import java.util.Base64

import com.google.crypto.tink.apps.webpush.WebPushHybridDecrypt
import com.google.crypto.tink.subtle.EllipticCurves
import com.google.crypto.tink.subtle.EllipticCurves.{CurveType, PointFormatType}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PushSubscriptionKeysTest {
  private val keyPair: KeyPair = EllipticCurves.generateKeyPair(CurveType.NIST_P256)
  private val publicKey: ECPublicKey = keyPair.getPublic.asInstanceOf[ECPublicKey]
  private val authSecret: Array[Byte] = "secret123secret1".getBytes(StandardCharsets.UTF_8)
  private val auth: String = Base64.getUrlEncoder.withoutPadding().encodeToString(authSecret)
  // RFC 8291: what `PushSubscription.getKey("p256dh")` gives in a browser
  private val uncompressedPoint: Array[Byte] = EllipticCurves.pointEncode(CurveType.NIST_P256, PointFormatType.UNCOMPRESSED, publicKey.getW)

  private def decrypt(encrypted: Array[Byte]): String =
    new String(new WebPushHybridDecrypt.Builder()
      .withAuthSecret(authSecret)
      .withRecipientPublicKey(publicKey)
      .withRecipientPrivateKey(keyPair.getPrivate.asInstanceOf[ECPrivateKey])
      .build()
      .decrypt(encrypted, null), StandardCharsets.UTF_8)

  @Test
  def shouldAcceptTheUncompressedPointOfBrowsers(): Unit = {
    val keys = PushSubscriptionKeys(Base64.getUrlEncoder.withoutPadding().encodeToString(uncompressedPoint), auth)

    assertThat(uncompressedPoint).hasSize(65)
    assertThat(keys.validate.isRight).isTrue
    assertThat(decrypt(keys.encrypt("payload".getBytes(StandardCharsets.UTF_8)))).isEqualTo("payload")
  }

  @Test
  def shouldStillAcceptTheX509Encoding(): Unit = {
    val keys = PushSubscriptionKeys(Base64.getUrlEncoder.encodeToString(publicKey.getEncoded), auth)

    assertThat(keys.validate.isRight).isTrue
    assertThat(decrypt(keys.encrypt("payload".getBytes(StandardCharsets.UTF_8)))).isEqualTo("payload")
  }

  @Test
  def shouldRejectAKeyThatIsNotAPointOfTheCurve(): Unit = {
    val notAPoint: Array[Byte] = Array[Byte](0x04) ++ Array.fill[Byte](64)(1)

    assertThat(PushSubscriptionKeys(Base64.getUrlEncoder.encodeToString(notAPoint), auth).validate.isLeft).isTrue
  }

  @Test
  def shouldRejectGarbage(): Unit =
    assertThat(PushSubscriptionKeys("not a key", auth).validate.isLeft).isTrue
}
