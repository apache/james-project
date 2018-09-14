/** **************************************************************
  * Licensed to the Apache Software Foundation (ASF) under one   *
  * or more contributor license agreements.  See the NOTICE file *
  * distributed with this work for additional information        *
  * regarding copyright ownership.  The ASF licenses this file   *
  * to you under the Apache License, Version 2.0 (the            *
  * "License"); you may not use this file except in compliance   *
  * with the License.  You may obtain a copy of the License at   *
  * *
  * http://www.apache.org/licenses/LICENSE-2.0                 *
  * *
  * Unless required by applicable law or agreed to in writing,   *
  * software distributed under the License is distributed on an  *
  * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
  * KIND, either express or implied.  See the License for the    *
  * specific language governing permissions and limitations      *
  * under the License.                                           *
  * ***************************************************************/

package org.apache.james.transport.mailets

import org.apache.james.core.MailAddress
import org.apache.james.core.builder.MimeMessageBuilder
import org.apache.james.dnsservice.api.{DNSService, InMemoryDNSService}
import org.apache.james.transport.mailets.XOriginatingIpInNetwork.X_ORIGINATING_IP
import org.apache.mailet.base.test.{FakeMail, FakeMatcherConfig}
import org.junit.runner.RunWith
import org.specs2.matcher.Matchers
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

import scala.jdk.CollectionConverters._

@RunWith(classOf[JUnitRunner])
class XOriginatingIpInNetworkSpec extends Specification with Matchers {
  val dnsServer: DNSService =
    new InMemoryDNSService()
      .registerMxRecord("192.168.0.1", "192.168.0.1")
      .registerMxRecord("192.168.200.1", "192.168.200.1")
      .registerMxRecord("192.168.200.0", "192.168.200.0")
      .registerMxRecord("255.255.255.0", "255.255.255.0")
  val matcherConfig: FakeMatcherConfig =
    FakeMatcherConfig.builder
      .matcherName("AllowedNetworkIs")
      .condition("192.168.200.0/24")
      .build

  "RemoteAddrOrOriginatingIpInNetwork" should {
    val testRecipient = new MailAddress("test@james.apache.org")
    val matcher = new XOriginatingIpInNetwork()
    matcher.setDNSService(dnsServer)
    matcher.init(matcherConfig)

    s"match when ip of header $X_ORIGINATING_IP is on the same network" in {
      val fakeMail =
        FakeMail.builder
          .name("mailname")
          .recipient(testRecipient)
          .remoteAddr("10.0.0.1")
          .mimeMessage(
            MimeMessageBuilder.mimeMessageBuilder()
              .addToRecipient(testRecipient.toInternetAddress)
              .addHeader(X_ORIGINATING_IP,"192.168.200.1")
              .build())
          .build

      val actual = matcher.`match`(fakeMail).asScala

      actual must contain(exactly(testRecipient))
    }

    s"match when ip of header $X_ORIGINATING_IP is between brackets" in {
      val fakeMail =
        FakeMail.builder
          .name("mailname")
          .recipient(testRecipient)
          .remoteAddr("10.0.0.1")
          .mimeMessage(
            MimeMessageBuilder.mimeMessageBuilder()
              .addToRecipient(testRecipient.toInternetAddress)
              .addHeader(X_ORIGINATING_IP,"[192.168.200.1]")
              .build())
          .build

      val actual = matcher.`match`(fakeMail).asScala

      actual must contain(exactly(testRecipient))
    }

    s"not match when ip of header $X_ORIGINATING_IP is not on the same network" in {
      val fakeMail =
        FakeMail.builder
          .name("mailname")
          .recipient(testRecipient)
          .remoteAddr("10.0.0.1")
          .mimeMessage(
            MimeMessageBuilder.mimeMessageBuilder()
              .addToRecipient(testRecipient.toInternetAddress)
              .addHeader(X_ORIGINATING_IP,"10.0.0.2")
              .build())
          .build

      val actual = matcher.`match`(fakeMail).asScala

      actual must beEmpty
    }

    s"not match when header $X_ORIGINATING_IP is missing" in {
      val fakeMail =
        FakeMail.builder
          .name("mailname")
          .recipient(testRecipient)
          .remoteAddr("10.0.0.1")
          .mimeMessage(
            MimeMessageBuilder.mimeMessageBuilder()
              .addToRecipient(testRecipient.toInternetAddress)
              .build())
          .build

      val actual = matcher.`match`(fakeMail).asScala

      actual must beEmpty
    }
  }
}
