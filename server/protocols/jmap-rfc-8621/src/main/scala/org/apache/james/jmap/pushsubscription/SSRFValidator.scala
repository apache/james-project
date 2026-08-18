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

import java.net.{Inet4Address, Inet6Address, InetAddress, InetSocketAddress, UnknownHostException}
import java.util
import java.util.Locale

import com.google.common.net.InetAddresses
import io.netty.resolver.{AddressResolver, AddressResolverGroup, InetNameResolver}
import io.netty.util.concurrent.{EventExecutor, Promise}
import org.apache.james.jmap.api.model.PushSubscriptionServerURL
import org.apache.james.jmap.pushsubscription.SSRFValidator.{ALLOWED_SCHEMES, HostResolver, SYSTEM_HOST_RESOLVER, forbiddenReason}
import reactor.core.publisher.Mono
import reactor.core.scala.publisher.SMono
import reactor.core.scheduler.Schedulers

import scala.jdk.CollectionConverters._

object SSRFValidator {
  /**
   * Resolves a host name into the addresses it points to. Extracted as a function so that tests can
   * exercise multi-record and DNS rebinding scenarios without relying on an actual DNS server.
   */
  type HostResolver = String => Seq[InetAddress]

  val SYSTEM_HOST_RESOLVER: HostResolver = host => InetAddress.getAllByName(host).toSeq

  val ALLOWED_SCHEMES: Set[String] = Set("http", "https")

  private val IPV4_LENGTH: Int = 4

  /**
   * Describes why the supplied address must not be reached, if it must not.
   *
   * The JDK predicates alone leave holes an attacker can walk through: `isSiteLocalAddress` only knows
   * about the deprecated fec0::/10 for IPv6, and none of them covers the wildcard address. On top of
   * them we thus reject:
   *
   *  - the wildcard addresses (0.0.0.0, ::), which reach local services,
   *  - 0.0.0.0/8 ("this network") and the 255.255.255.255 broadcast address,
   *  - IPv6 unique local addresses (fc00::/7), which notably hold the IPv6 cloud metadata endpoints,
   *  - multicast addresses,
   *  - the shared address space (100.64.0.0/10, RFC 6598).
   *
   * IPv6 addresses that embed an IPv4 one (IPv4-mapped, IPv4-compatible, NAT64 well-known prefix and
   * 6to4) are additionally validated against the IPv4 address they embed: traffic sent to them ends up
   * being delivered to that IPv4 address.
   */
  def forbiddenReason(address: InetAddress): Option[String] =
    directlyForbiddenReason(address)
      .orElse(embeddedIPv4(address).flatMap(forbiddenReason))

  private def directlyForbiddenReason(address: InetAddress): Option[String] = address match {
    case a if a.isAnyLocalAddress => Some("a wildcard address")
    case a if a.isLoopbackAddress => Some("a loopback address")
    case a if a.isLinkLocalAddress => Some("a link local address")
    case a if a.isSiteLocalAddress => Some("a site local address")
    case a if a.isMulticastAddress => Some("a multicast address")
    case a: Inet6Address if isUniqueLocal(a) => Some("an IPv6 unique local address")
    case a: Inet4Address if isThisNetwork(a) => Some("a 'this network' (0.0.0.0/8) address")
    case a: Inet4Address if isSharedAddressSpace(a) => Some("a shared address space (100.64.0.0/10) address")
    case a: Inet4Address if isBroadcast(a) => Some("a broadcast address")
    case _ => None
  }

  private def isUniqueLocal(address: Inet6Address): Boolean =
    (address.getAddress()(0) & 0xfe) == 0xfc

  private def isThisNetwork(address: Inet4Address): Boolean =
    (address.getAddress()(0) & 0xff) == 0

  private def isSharedAddressSpace(address: Inet4Address): Boolean = {
    val bytes: Array[Byte] = address.getAddress
    (bytes(0) & 0xff) == 100 && (bytes(1) & 0xff) >= 64 && (bytes(1) & 0xff) <= 127
  }

  private def isBroadcast(address: Inet4Address): Boolean =
    address.getAddress.forall(byte => (byte & 0xff) == 0xff)

  /**
   * Guava knows about the IPv4-compatible, 6to4 and Teredo forms. ISATAP it deliberately leaves out of
   * `hasEmbeddedIPv4ClientAddress` as being trivially spoofable, which does not matter here: we are after
   * where the packet gets delivered, not after who claims to have sent it.
   *
   * The IPv4-mapped and NAT64 forms are the two it does not expose as an `Inet6Address` predicate.
   */
  private def embeddedIPv4(address: InetAddress): Option[InetAddress] = address match {
    case a: Inet6Address if InetAddresses.hasEmbeddedIPv4ClientAddress(a) => Some(InetAddresses.getEmbeddedIPv4ClientAddress(a))
    case a: Inet6Address if InetAddresses.isIsatapAddress(a) => Some(InetAddresses.getIsatapIPv4Address(a))
    case a: Inet6Address if isIPv4Mapped(a.getAddress) || isNat64WellKnown(a.getAddress) => Some(ipv4At(a.getAddress, 12))
    case _ => None
  }

  // ::ffff:0:0/96
  private def isIPv4Mapped(bytes: Array[Byte]): Boolean =
    isZero(bytes, 0, 10) && (bytes(10) & 0xff) == 0xff && (bytes(11) & 0xff) == 0xff

  // 64:ff9b::/96
  private def isNat64WellKnown(bytes: Array[Byte]): Boolean =
    (bytes(0) & 0xff) == 0x00 && (bytes(1) & 0xff) == 0x64 &&
      (bytes(2) & 0xff) == 0xff && (bytes(3) & 0xff) == 0x9b &&
      isZero(bytes, 4, 12)

  private def isZero(bytes: Array[Byte], from: Int, until: Int): Boolean =
    (from until until).forall(i => bytes(i) == 0)

  private def ipv4At(bytes: Array[Byte], offset: Int): InetAddress =
    InetAddress.getByAddress(bytes.slice(offset, offset + IPV4_LENGTH))
}

/**
 * Guards JMAP push against being used as a server side request forgery primitive.
 *
 * Two layers are applied, both relying on the same address policy:
 *
 *  - [[validate]] rejects the push subscription URL upfront, which yields an explicit error to the user,
 *  - [[addressResolverGroup]] plugs the very same policy into the resolver the HTTP client connects with.
 *
 * The second layer is what actually holds: the URL is resolved again when the connection is established,
 * so validating a resolution performed beforehand leaves a window a DNS rebinding attack fits into.
 * Validating within the resolver makes the validated resolution the one that gets connected to.
 */
class SSRFValidator(hostResolver: HostResolver = SYSTEM_HOST_RESOLVER,
                    policy: InetAddress => Option[String] = forbiddenReason) {

  def validate(pushServerUrl: PushSubscriptionServerURL): SMono[PushSubscriptionServerURL] =
    validateScheme(pushServerUrl)
      .flatMap(url => SMono.fromCallable(() => checkedResolve(url.value.getHost, s"JMAP Push subscription $url"))
        .subscribeOn(Schedulers.boundedElastic())
        .`then`(SMono.just(url)))

  def addressResolverGroup: AddressResolverGroup[InetSocketAddress] = new SSRFPreventingAddressResolverGroup(this)

  /**
   * Resolves a host and returns its addresses, failing whenever a single one of them is forbidden.
   *
   * All the addresses are validated, and not only the first one: a host resolving to both a public and a
   * private address would otherwise let the connection land on the private one.
   */
  private[pushsubscription] def checkedResolve(host: String, context: String): Seq[InetAddress] = {
    val addresses: Seq[InetAddress] = hostResolver(host)

    if (addresses.isEmpty) {
      throw new UnknownHostException(host)
    }

    addresses.flatMap(address => policy(address).map(reason => (address, reason)))
      .headOption match {
        case Some((address, reason)) => throw new IllegalArgumentException(
          s"$context is targeting $reason $address. This could be an attempt for server-side request forgery.")
        case None => addresses
      }
  }

  private def validateScheme(pushServerUrl: PushSubscriptionServerURL): SMono[PushSubscriptionServerURL] =
    Option(pushServerUrl.value.getProtocol).map(_.toLowerCase(Locale.US)) match {
      case Some(scheme) if ALLOWED_SCHEMES.contains(scheme) => SMono.just(pushServerUrl)
      case scheme => SMono.error(new IllegalArgumentException(
        s"JMAP Push subscription $pushServerUrl is using the unsupported scheme ${scheme.getOrElse("<none>")}. " +
          s"Only ${ALLOWED_SCHEMES.toSeq.sorted.mkString(" and ")} are allowed."))
    }
}

private class SSRFPreventingAddressResolverGroup(validator: SSRFValidator) extends AddressResolverGroup[InetSocketAddress] {
  override protected def newResolver(executor: EventExecutor): AddressResolver[InetSocketAddress] =
    new SSRFPreventingNameResolver(executor, validator).asAddressResolver()
}

private class SSRFPreventingNameResolver(executor: EventExecutor, validator: SSRFValidator) extends InetNameResolver(executor) {
  override protected def doResolve(inetHost: String, promise: Promise[InetAddress]): Unit =
    safeResolve(inetHost).subscribe(
      (addresses: Seq[InetAddress]) => { promise.trySuccess(addresses.head); () },
      (error: Throwable) => { promise.tryFailure(error); () })

  override protected def doResolveAll(inetHost: String, promise: Promise[util.List[InetAddress]]): Unit =
    safeResolve(inetHost).subscribe(
      (addresses: Seq[InetAddress]) => { promise.trySuccess(addresses.asJava); () },
      (error: Throwable) => { promise.tryFailure(error); () })

  // Not named `resolve`: SimpleNameResolver::resolve is final
  private def safeResolve(inetHost: String): Mono[Seq[InetAddress]] =
    Mono.fromCallable(() => validator.checkedResolve(inetHost, s"JMAP Push subscription resolution of $inetHost"))
      .subscribeOn(Schedulers.boundedElastic())
}
