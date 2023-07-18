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

package org.apache.james.transport.mailets

import org.apache.james.core.MailAddress
import org.apache.james.rate.limiter.memory.MemoryRateLimiterFactory
import org.apache.mailet.base.test.{FakeMail, FakeMailContext, FakeMailetConfig}
import org.apache.mailet.{Mail, MailetConfig}
import org.assertj.core.api.Assertions.{assertThat, assertThatCode, assertThatThrownBy}
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.{Disabled, Nested, Test}
import org.mockito.Mockito.times
import org.mockito.{ArgumentCaptor, Mockito}

import scala.jdk.CollectionConverters._

class PerRecipientRateLimitTest {

  def testee(mailetConfig: MailetConfig) : PerRecipientRateLimit = {
    val mailet: PerRecipientRateLimit = new PerRecipientRateLimit(new MemoryRateLimiterFactory())
    mailet.init(mailetConfig)
    mailet
  }

  @Test
  def rateLimitingShouldBeAppliedPerRecipient(): Unit = {
    val mailet: PerRecipientRateLimit = testee(FakeMailetConfig.builder()
      .mailetName("PerRecipientRateLimit")
      .setProperty("duration", "20s")
      .setProperty("precision", "1s")
      .setProperty("count", "1")
      .build())

    val mail1: Mail = FakeMail.builder()
      .name("mail")
      .sender("sender1@domain.tld")
      .recipients("rcpt1@linagora.com")
      .state("transport")
      .build()

    val mail2: Mail = FakeMail.builder()
      .name("mail2")
      .sender("sender1@domain.tld")
      .recipients("rcpt2@linagora.com")
      .state("transport")
      .build()

    mailet.service(mail1)
    mailet.service(mail2)

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(mail1.getState).isEqualTo("transport")
      softly.assertThat(mail2.getState).isEqualTo("transport")
    })
  }

  @Test
  def rateLimitingShouldNotBeAppliedWhenDoNotHaveRecipient() : Unit = {
    val mailet: PerRecipientRateLimit = testee(FakeMailetConfig.builder()
      .mailetName("PerRecipientRateLimit")
      .setProperty("duration", "20s")
      .setProperty("precision", "1s")
      .setProperty("count", "1")
      .build())

    val mail: Mail = FakeMail.builder()
      .name("mail")
      .sender("sender1@domain.tld")
      .state("transport")
      .build()

    mailet.service(mail)
    assertThat(mail.getState).isEqualTo("transport")
  }

  @Test
  def rateLimitingShouldFlowToTheIntendedProcessor() : Unit = {
    val mailet: PerRecipientRateLimit = testee(FakeMailetConfig.builder()
      .mailetName("PerRecipientRateLimit")
      .setProperty("duration", "20s")
      .setProperty("precision", "1s")
      .setProperty("count", "1")
      .setProperty("exceededProcessor", "tooMuchMails")
      .build())

    val mail1: Mail = FakeMail.builder()
      .name("mail")
      .sender("sender1@domain.tld")
      .recipients("rcpt1@linagora.com")
      .state("transport")
      .build()

    val mail2: Mail = FakeMail.builder()
      .name("mail2")
      .sender("sender1@domain.tld")
      .recipients("rcpt1@linagora.com")
      .state("transport")
      .build()

    mailet.service(mail1)
    mailet.service(mail2)

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(mail1.getState).isEqualTo("transport")
      softly.assertThat(mail2.getState).isEqualTo("tooMuchMails")
    })
  }

  @Test
  def rateLimitingShouldNOTBeAppliedPerSender() : Unit = {
    val mailet: PerRecipientRateLimit = testee(FakeMailetConfig.builder()
      .mailetName("PerRecipientRateLimit")
      .setProperty("duration", "20s")
      .setProperty("precision", "1s")
      .setProperty("count", "1")
      .setProperty("exceededProcessor", "tooMuchMails")
      .build())

    val mail1: Mail = FakeMail.builder()
      .name("mail")
      .sender("sender1@domain.tld")
      .recipients("rcpt1@linagora.com")
      .state("transport")
      .build()

    val mail2: Mail = FakeMail.builder()
      .name("mail2")
      .sender("sender2@domain.tld")
      .recipients("rcpt1@linagora.com")
      .state("transport")
      .build()

    mailet.service(mail1)
    mailet.service(mail2)

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(mail1.getState).isEqualTo("transport")
      softly.assertThat(mail2.getState).isEqualTo("tooMuchMails")
    })
  }

  @Test
  def shouldRateLimitSizeOfEmails(): Unit = {
    val mailet: PerRecipientRateLimit = testee(FakeMailetConfig.builder()
      .mailetName("PerRecipientRateLimit")
      .setProperty("duration", "20s")
      .setProperty("precision", "1s")
      .setProperty("size", "100K")
      .setProperty("exceededProcessor", "tooMuchMails")
      .build())

    val mail1: Mail = FakeMail.builder()
      .name("mail1")
      .sender("sender@domain.tld")
      .recipients("rcpt1@linagora.com")
      .size(50 * 1024)
      .state("transport")
      .build()

    val mail2: Mail = FakeMail.builder()
      .name("mail2")
      .sender("sender@domain.tld")
      .recipients("rcpt1@linagora.com")
      .size(51 * 1024)
      .state("transport")
      .build()

    val mail3: Mail = FakeMail.builder()
      .name("mail3")
      .sender("sender@domain.tld")
      .recipients("rcpt1@linagora.com")
      .size(49 * 1024)
      .state("transport")
      .build()

    mailet.service(mail1)
    mailet.service(mail2)
    mailet.service(mail3)

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(mail1.getState).isEqualTo("transport")
      softly.assertThat(mail2.getState).isEqualTo("tooMuchMails")
      softly.assertThat(mail3.getState).isEqualTo("transport")
    })
  }

  @Test
  def shouldRateLimitPerRecipient(): Unit = {
    val mailetContext = Mockito.spy(FakeMailContext.defaultContext)

    val mailet: PerRecipientRateLimit = testee(FakeMailetConfig.builder()
      .mailetName("PerRecipientRateLimit")
      .setProperty("duration", "20s")
      .setProperty("precision", "1s")
      .setProperty("count", "1")
      .setProperty("exceededProcessor", "tooMuchMails")
      .mailetContext(mailetContext)
      .build())

    // acceptable
    val mail1: Mail = FakeMail.builder()
      .name("mail")
      .sender("sender1@domain.tld")
      .recipients("rcpt1@linagora.com")
      .state("transport")
      .build()

    // "rcpt1@linagora.com" : exceeded, "rcpt2@linagora.com" : acceptable
    val mail2: Mail = FakeMail.builder()
      .name("mail")
      .sender("sender1@domain.tld")
      .recipients("rcpt1@linagora.com", "rcpt2@linagora.com")
      .state("transport")
      .build()

    mailet.service(mail1)
    mailet.service(mail2)

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(mail1.getState).isEqualTo("transport")
      softly.assertThat(mail2.getState).isEqualTo("transport")
    })

    val mailCapture: ArgumentCaptor[Mail] = ArgumentCaptor.forClass(classOf[Mail])
    val stateCapture: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])

    Mockito.verify(mailetContext, times(1)).sendMail(mailCapture.capture(), stateCapture.capture())

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(mailCapture.getAllValues.size()).isEqualTo(1)

      softly.assertThat(mailCapture.getAllValues.asScala
        .find(mail => mail.getState.equals("tooMuchMails"))
        .map(mail => mail.getRecipients).get)
        .containsExactlyInAnyOrder(new MailAddress("rcpt1@linagora.com"))
      softly.assertThat(stateCapture.getAllValues)
        .containsExactlyInAnyOrder( "tooMuchMails")
    })
  }

  @Test
  def shouldRateLimitedWhenAllRecipientsExceeded(): Unit = {
    val mailet: PerRecipientRateLimit = testee(FakeMailetConfig.builder()
      .mailetName("PerRecipientRateLimit")
      .setProperty("duration", "20s")
      .setProperty("precision", "1s")
      .setProperty("count", "1")
      .setProperty("exceededProcessor", "tooMuchMails")
      .build())

    val mail1: Mail = FakeMail.builder()
      .name("mail1")
      .sender("sender@domain.tld")
      .recipients("rcpt1@linagora.com", "rcpt2@linagora.com")
      .state("transport")
      .build()

    val mail2: Mail = FakeMail.builder()
      .name("mail2")
      .sender("sender@domain.tld")
      .recipients("rcpt1@linagora.com", "rcpt2@linagora.com")
      .state("transport")
      .build()

    mailet.service(mail1)
    mailet.service(mail2)

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(mail1.getState).isEqualTo("transport")
      softly.assertThat(mail2.getState).isEqualTo("tooMuchMails")
    })
  }

  @Disabled("atomicity problem. https://github.com/apache/james-project/pull/851#issuecomment-1020792286")
  @Test
  def mailetShouldSupportBothCountAndSize(): Unit = {
    val mailet: PerRecipientRateLimit = testee(FakeMailetConfig.builder()
      .mailetName("PerRecipientRateLimit")
      .setProperty("duration", "20s")
      .setProperty("precision", "1s")
      .setProperty("count", "2")
      .setProperty("size", "100K")
      .setProperty("exceededProcessor", "tooMuchMails")
      .build())

    // acceptable
    val mail1: Mail = FakeMail.builder()
      .name("mail1")
      .sender("sender@domain.tld")
      .recipients("rcpt1@linagora.com")
      .size(50 * 1024)
      .state("transport")
      .build()

    // exceeded (size)
    val mail2: Mail = FakeMail.builder()
      .name("mail2")
      .sender("sender@domain.tld")
      .recipients("rcpt1@linagora.com")
      .size(51 * 1024)
      .state("transport")
      .build()

    // acceptable
    val mail3: Mail = FakeMail.builder()
      .name("mail3")
      .sender("sender@domain.tld")
      .recipients("rcpt1@linagora.com")
      .size(1)
      .state("transport")
      .build()

    // exceeded (count)
    val mail4: Mail = FakeMail.builder()
      .name("mail4")
      .sender("sender@domain.tld")
      .recipients("rcpt1@linagora.com")
      .size(1)
      .state("transport")
      .build()

    mailet.service(mail1)
    mailet.service(mail2)
    mailet.service(mail3)
    mailet.service(mail4)

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(mail1.getState).isEqualTo("transport")
      softly.assertThat(mail2.getState).isEqualTo("tooMuchMails")
      softly.assertThat(mail3.getState).isEqualTo("transport")
      softly.assertThat(mail4.getState).isEqualTo("tooMuchMails")
    })
  }

  @Nested
  class Configuration {
    @Test
    def shouldFailWhenNoDuration(): Unit = {
      assertThatThrownBy(() => testee(FakeMailetConfig.builder()
        .mailetName("PerRecipientRateLimit")
        .build()))
        .isInstanceOf(classOf[IllegalArgumentException])
    }

    @Test
    def shouldFailWhenEmptyDuration(): Unit = {
      assertThatThrownBy(() => testee(FakeMailetConfig.builder()
        .mailetName("PerRecipientRateLimit")
        .setProperty("duration", "")
        .build()))
        .isInstanceOf(classOf[IllegalArgumentException])
    }

    @Test
    def shouldFailWhenBadDuration(): Unit = {
      assertThatThrownBy(() => testee(FakeMailetConfig.builder()
        .mailetName("PerRecipientRateLimit")
        .setProperty("duration", "bad")
        .build()))
        .isInstanceOf(classOf[IllegalArgumentException])
    }

    @Test
    def shouldFailWhenNegativeDuration(): Unit = {
      assertThatThrownBy(() => testee(FakeMailetConfig.builder()
        .mailetName("PerRecipientRateLimit")
        .setProperty("duration", "-3s")
        .build()))
        .isInstanceOf(classOf[IllegalArgumentException])
    }

    @Test
    def shouldFailWhenZeroDuration(): Unit = {
      assertThatThrownBy(() => testee(FakeMailetConfig.builder()
        .mailetName("PerRecipientRateLimit")
        .setProperty("count", "1")
        .setProperty("duration", "0s")
        .build()))
        .isInstanceOf(classOf[IllegalArgumentException])
    }

    @Test
    def shouldFailWhenTooSmallDuration(): Unit = {
      assertThatThrownBy(() => testee(FakeMailetConfig.builder()
        .mailetName("PerRecipientRateLimit")
        .setProperty("count", "1")
        .setProperty("duration", "10ms")
        .build()))
        .isInstanceOf(classOf[IllegalArgumentException])
    }

    @Test
    def durationWithNoUnitShouldDefaultToSeconds(): Unit = {
      assertThat(
        testee(FakeMailetConfig.builder()
          .mailetName("PerRecipientRateLimit")
          .setProperty("duration", "10")
          .build()).parseDuration().getSeconds)
        .isEqualTo(10L)
    }

    @Test
    def durationShouldSupportUnits(): Unit = {
      assertThat(
        testee(FakeMailetConfig.builder()
          .mailetName("PerRecipientRateLimit")
          .setProperty("duration", "1h")
          .build()).parseDuration().getSeconds)
        .isEqualTo(3600L)
    }

    @Test
    def shouldFailWithEmptyCount(): Unit = {
      assertThatThrownBy(() => testee(FakeMailetConfig.builder()
        .mailetName("PerRecipientRateLimit")
        .setProperty("count", "")
        .setProperty("duration", "10s")
        .build()).parseDuration().getSeconds)
        .isInstanceOf(classOf[IllegalArgumentException])
    }

    @Test
    def shouldFailWithZeroCount(): Unit = {
      assertThatThrownBy(() => testee(FakeMailetConfig.builder()
        .mailetName("PerRecipientRateLimit")
        .setProperty("count", "0")
        .setProperty("duration", "10s")
        .build()).parseDuration().getSeconds)
        .isInstanceOf(classOf[IllegalArgumentException])
    }

    @Test
    def shouldFailWithNegativeCount(): Unit = {
      assertThatThrownBy(() => testee(FakeMailetConfig.builder()
        .mailetName("PerRecipientRateLimit")
        .setProperty("count", "-1")
        .setProperty("duration", "10s")
        .build()).parseDuration().getSeconds)
        .isInstanceOf(classOf[IllegalArgumentException])
    }

    @Test
    def shouldFailWithBadCount(): Unit = {
      assertThatThrownBy(() => testee(FakeMailetConfig.builder()
        .mailetName("PerRecipientRateLimit")
        .setProperty("count", "bad")
        .setProperty("duration", "10s")
        .build()).parseDuration().getSeconds)
        .isInstanceOf(classOf[IllegalArgumentException])
    }

    @Test
    def shouldFailWithEmptySize(): Unit = {
      assertThatThrownBy(() => testee(FakeMailetConfig.builder()
        .mailetName("PerRecipientRateLimit")
        .setProperty("size", "")
        .setProperty("duration", "10s")
        .build()).parseDuration().getSeconds)
        .isInstanceOf(classOf[IllegalArgumentException])
    }

    @Test
    def shouldFailWithZeroSize(): Unit = {
      assertThatThrownBy(() => testee(FakeMailetConfig.builder()
        .mailetName("PerRecipientRateLimit")
        .setProperty("size", "0")
        .setProperty("duration", "10s")
        .build()).parseDuration().getSeconds)
        .isInstanceOf(classOf[IllegalArgumentException])
    }

    @Test
    def shouldFailWithNegativeSize(): Unit = {
      assertThatThrownBy(() => testee(FakeMailetConfig.builder()
        .mailetName("PerRecipientRateLimit")
        .setProperty("size", "-1000")
        .setProperty("duration", "10s")
        .build()).parseDuration().getSeconds)
        .isInstanceOf(classOf[IllegalArgumentException])
    }

    @Test
    def shouldFailWithBadSize(): Unit = {
      assertThatThrownBy(() => testee(FakeMailetConfig.builder()
        .mailetName("PerRecipientRateLimit")
        .setProperty("size", "bad")
        .setProperty("duration", "10s")
        .build()).parseDuration().getSeconds)
        .isInstanceOf(classOf[IllegalArgumentException])
    }

    @Test
    def sizeShouldSupportUnits(): Unit = {
      assertThatCode(() => testee(FakeMailetConfig.builder()
        .mailetName("PerRecipientRateLimit")
        .setProperty("size", "1k")
        .setProperty("duration", "10s")
        .build()).parseDuration().getSeconds)
        .doesNotThrowAnyException()
    }
  }

}
