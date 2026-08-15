/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.jmap.pushsubscription

import java.net.{Inet6Address, InetAddress, InetSocketAddress, UnknownHostException}
import java.util.concurrent.atomic.AtomicInteger

import io.netty.resolver.AddressResolver
import io.netty.util.concurrent.ImmediateEventExecutor
import org.apache.james.jmap.api.model.PushSubscriptionServerURL
import org.apache.james.jmap.pushsubscription.SSRFValidator.HostResolver
import org.assertj.core.api.Assertions.{assertThat, assertThatCode, assertThatThrownBy}
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

object SSRFValidatorTest {
  val PUBLIC_ADDRESS: InetAddress = InetAddress.getByName("93.184.216.34")
  val LOOPBACK_ADDRESS: InetAddress = InetAddress.getByName("127.0.0.1")

  def resolvingTo(addresses: InetAddress*): HostResolver = _ => addresses.toSeq
}

class SSRFValidatorTest {
  import SSRFValidatorTest._

  @ParameterizedTest
  @ValueSource(strings = Array(
    // Wildcard: reaches local services, and is not covered by any of the JDK predicates
    "0.0.0.0",
    "::",
    // 0.0.0.0/8
    "0.1.2.3",
    // Loopback
    "127.0.0.1",
    "127.0.0.9",
    "127.255.255.254",
    "::1",
    // Site local
    "10.9.0.3",
    "172.16.0.1",
    "172.31.255.255",
    "192.168.102.35",
    "fec0::1",
    // Link local, including the IPv4 cloud metadata endpoint
    "169.254.169.254",
    "fe80::1",
    // IPv6 unique local (fc00::/7): isSiteLocalAddress only knows about the deprecated fec0::/10
    "fc00::1",
    "fd00::1",
    // The IPv6 cloud metadata endpoint
    "fd00:ec2::254",
    // Multicast
    "224.0.0.1",
    "239.255.255.255",
    "ff02::1",
    // Shared address space (RFC 6598)
    "100.64.0.1",
    "100.127.255.255",
    // Broadcast
    "255.255.255.255",
    // IPv6 addresses embedding a forbidden IPv4 one
    "::127.0.0.1",
    "64:ff9b::7f00:1",
    "2002:7f00:1::1",
    "2002:c0a8:1::1"))
  def forbiddenReasonShouldRejectAddressesReachingTheLocalNetwork(ip: String): Unit =
    assertThat(SSRFValidator.forbiddenReason(InetAddress.getByName(ip)).isDefined)
      .describedAs(s"$ip is expected to be rejected")
      .isTrue

  @ParameterizedTest
  @ValueSource(strings = Array(
    "8.8.8.8",
    "1.1.1.1",
    "93.184.216.34",
    // Just outside of the site local and shared address space ranges
    "172.15.255.255",
    "172.32.0.1",
    "100.63.255.255",
    "100.128.0.0",
    "2001:4860:4860::8888",
    "2606:4700:4700::1111",
    // Embedding a public IPv4 address
    "::ffff:8.8.8.8",
    "2002:808:808::1",
    "64:ff9b::808:808"))
  def forbiddenReasonShouldAcceptPublicAddresses(ip: String): Unit =
    assertThat(SSRFValidator.forbiddenReason(InetAddress.getByName(ip)).isEmpty)
      .describedAs(s"$ip is expected to be accepted")
      .isTrue

  @Test
  def forbiddenReasonShouldRejectIPv4MappedLoopbackHeldAsAnIPv6Address(): Unit = {
    // InetAddress.getByName folds the IPv4-mapped form back into an Inet4Address, Inet6Address::getByAddress does not
    val mappedLoopback: Inet6Address = Inet6Address.getByAddress(null,
      Array[Byte](0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0xff.toByte, 0xff.toByte, 127, 0, 0, 1), 0)

    assertThat(SSRFValidator.forbiddenReason(mappedLoopback).isDefined).isTrue
  }

  @Test
  def validateShouldRejectAHostResolvingToASingleForbiddenAddress(): Unit = {
    val validator = new SSRFValidator(resolvingTo(LOOPBACK_ADDRESS))

    assertThatThrownBy(() => validator.validate(url("http://push.example.com")).block())
      .isInstanceOf(classOf[IllegalArgumentException])
      .hasMessageContaining("server-side request forgery")
  }

  @Test
  def validateShouldRejectAHostResolvingToBothAPublicAndAForbiddenAddress(): Unit = {
    // Validating the first address only would let the connection land on the second one
    val validator = new SSRFValidator(resolvingTo(PUBLIC_ADDRESS, LOOPBACK_ADDRESS))

    assertThatThrownBy(() => validator.validate(url("http://push.example.com")).block())
      .isInstanceOf(classOf[IllegalArgumentException])
      .hasMessageContaining("server-side request forgery")
  }

  @Test
  def validateShouldAcceptAHostResolvingToPublicAddressesOnly(): Unit = {
    val validator = new SSRFValidator(resolvingTo(PUBLIC_ADDRESS, InetAddress.getByName("8.8.8.8")))

    assertThatCode(() => validator.validate(url("http://push.example.com")).block())
      .doesNotThrowAnyException()
  }

  @Test
  def validateShouldRejectUnsupportedSchemes(): Unit = {
    val validator = new SSRFValidator(resolvingTo(PUBLIC_ADDRESS))

    assertThatThrownBy(() => validator.validate(url("file:///etc/passwd")).block())
      .isInstanceOf(classOf[IllegalArgumentException])
      .hasMessageContaining("unsupported scheme")
  }

  @ParameterizedTest
  @ValueSource(strings = Array("http://push.example.com", "https://push.example.com"))
  def validateShouldAcceptHttpAndHttps(supportedUrl: String): Unit = {
    val validator = new SSRFValidator(resolvingTo(PUBLIC_ADDRESS))

    assertThatCode(() => validator.validate(url(supportedUrl)).block())
      .doesNotThrowAnyException()
  }

  @Test
  def validateShouldPropagateResolutionFailures(): Unit = {
    val validator = new SSRFValidator(_ => throw new UnknownHostException("push.example.com"))

    assertThatThrownBy(() => validator.validate(url("http://push.example.com")).block())
      .hasRootCauseInstanceOf(classOf[UnknownHostException])
  }

  @Test
  def addressResolverGroupShouldRejectForbiddenAddresses(): Unit = {
    assertThatThrownBy(() => resolveAll(new SSRFValidator(resolvingTo(LOOPBACK_ADDRESS))))
      .hasStackTraceContaining("server-side request forgery")
  }

  @Test
  def addressResolverGroupShouldRejectAHostResolvingToBothAPublicAndAForbiddenAddress(): Unit = {
    assertThatThrownBy(() => resolveAll(new SSRFValidator(resolvingTo(PUBLIC_ADDRESS, LOOPBACK_ADDRESS))))
      .hasStackTraceContaining("server-side request forgery")
  }

  @Test
  def addressResolverGroupShouldResolvePublicAddresses(): Unit = {
    assertThat(resolveAll(new SSRFValidator(resolvingTo(PUBLIC_ADDRESS))))
      .containsExactly(new InetSocketAddress(PUBLIC_ADDRESS, 443))
  }

  @Test
  def addressResolverGroupShouldRejectARebindingHost(): Unit = {
    // A host that passes validation once, then resolves to a forbidden address
    val counter = new AtomicInteger(0)
    val validator = new SSRFValidator(_ => if (counter.getAndIncrement() == 0) Seq(PUBLIC_ADDRESS) else Seq(LOOPBACK_ADDRESS))

    assertThatCode(() => validator.validate(url("http://push.example.com")).block())
      .doesNotThrowAnyException()
    assertThatThrownBy(() => resolveAll(validator))
      .hasStackTraceContaining("server-side request forgery")
  }

  private def resolveAll(validator: SSRFValidator): java.util.List[InetSocketAddress] = {
    val group = validator.addressResolverGroup
    try {
      val resolver: AddressResolver[InetSocketAddress] = group.getResolver(ImmediateEventExecutor.INSTANCE)
      resolver.resolveAll(InetSocketAddress.createUnresolved("push.example.com", 443)).sync().get()
    } finally {
      group.close()
    }
  }

  private def url(value: String): PushSubscriptionServerURL = PushSubscriptionServerURL.from(value).get
}
