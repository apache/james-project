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

import org.apache.james.rate.limiter.memory.MemoryRateLimiterFactoryProvider
import org.apache.mailet.base.test.{FakeMail, FakeMailetConfig}
import org.apache.mailet.{Mail, MailetConfig}
import org.assertj.core.api.Assertions.{assertThat, assertThatCode, assertThatThrownBy}
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.{Nested, Test}

class PerSenderRateLimitTest {
  def testee(mailetConfig: MailetConfig): PerSenderRateLimit = {
    val mailet = new PerSenderRateLimit(new MemoryRateLimiterFactoryProvider())
    mailet.init(mailetConfig)
    mailet
  }

  @Test
  def rateLimitingShouldBeAppliedPerSender(): Unit = {
    val mailet = testee(FakeMailetConfig.builder()
      .mailetName("PerSenderRateLimit")
      .setProperty("duration", "20s")
      .setProperty("count", "1")
      .build())

    val mail1: Mail = FakeMail.builder()
      .name("mail1")
      .sender("sender1@domain.tld")
      .recipients("rcpt1@linagora.com", "rcpt2@linagora.com")
      .state("transport")
      .build()

    val mail2: Mail = FakeMail.builder()
      .name("mail2")
      .sender("sender2@domain.tld")
      .recipients("rcpt1@linagora.com", "rcpt3@linagora.com", "rcpt4@linagora.com")
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
  def rateLimitedEmailsShouldFlowToTheIntendedProcessor(): Unit = {
    val mailet = testee(FakeMailetConfig.builder()
      .mailetName("PerSenderRateLimit")
      .setProperty("duration", "20s")
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
      .recipients("rcpt1@linagora.com", "rcpt3@linagora.com", "rcpt4@linagora.com")
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
  def shouldRateLimitCountOfEmails(): Unit = {
    val mailet = testee(FakeMailetConfig.builder()
      .mailetName("PerSenderRateLimit")
      .setProperty("duration", "20s")
      .setProperty("count", "1")
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
      .recipients("rcpt1@linagora.com", "rcpt3@linagora.com", "rcpt4@linagora.com")
      .state("transport")
      .build()

    mailet.service(mail1)
    mailet.service(mail2)

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(mail1.getState).isEqualTo("transport")
      softly.assertThat(mail2.getState).isEqualTo("error")
    })
  }

  @Test
  def shouldRateLimitRecipientsOfEmails(): Unit = {
    val mailet = testee(FakeMailetConfig.builder()
      .mailetName("PerSenderRateLimit")
      .setProperty("duration", "20s")
      .setProperty("recipients", "4")
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
      .recipients("rcpt1@linagora.com", "rcpt3@linagora.com", "rcpt4@linagora.com")
      .state("transport")
      .build()

    val mail3: Mail = FakeMail.builder()
      .name("mail3")
      .sender("sender@domain.tld")
      .recipients("rcpt5@linagora.com")
      .state("transport")
      .build()

    mailet.service(mail1)
    mailet.service(mail2)
    mailet.service(mail3)

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(mail1.getState).isEqualTo("transport")
      softly.assertThat(mail2.getState).isEqualTo("error")
      softly.assertThat(mail3.getState).isEqualTo("transport")
    })
  }

  @Test
  def shouldRateLimitSizeOfEmails(): Unit = {
    val mailet = testee(FakeMailetConfig.builder()
      .mailetName("PerSenderRateLimit")
      .setProperty("duration", "20s")
      .setProperty("size", "100K")
      .build())

    val mail1: Mail = FakeMail.builder()
      .name("mail1")
      .sender("sender@domain.tld")
      .size(50 * 1024)
      .state("transport")
      .build()

    val mail2: Mail = FakeMail.builder()
      .name("mail2")
      .sender("sender@domain.tld")
      .size(51 * 1024)
      .state("transport")
      .build()

    val mail3: Mail = FakeMail.builder()
      .name("mail3")
      .sender("sender@domain.tld")
      .size(49 * 1024)
      .state("transport")
      .build()

    mailet.service(mail1)
    mailet.service(mail2)
    mailet.service(mail3)

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(mail1.getState).isEqualTo("transport")
      softly.assertThat(mail2.getState).isEqualTo("error")
      softly.assertThat(mail3.getState).isEqualTo("transport")
    })
  }

  @Test
  def shouldRateLimitTotalSizeOfEmails(): Unit = {
    val mailet = testee(FakeMailetConfig.builder()
      .mailetName("PerSenderRateLimit")
      .setProperty("duration", "20s")
      .setProperty("totalSize", "1M")
      .build())

    val mail1: Mail = FakeMail.builder()
      .name("mail1")
      .sender("sender@domain.tld")
      .recipients("rcpt1@linagora.com", "rcpt2@linagora.com")
      .size(50 * 1024)
      .state("transport")
      .build()

    val mail2: Mail = FakeMail.builder()
      .name("mail2")
      .sender("sender@domain.tld")
      .recipients("rcpt1@linagora.com", "rcpt3@linagora.com", "rcpt4@linagora.com")
      .size(311 * 1024)
      .state("transport")
      .build()

    val mail3: Mail = FakeMail.builder()
      .name("mail3")
      .sender("sender@domain.tld")
      .recipients("rcpt1@linagora.com", "rcpt3@linagora.com")
      .size(210 * 1024)
      .state("transport")
      .build()

    mailet.service(mail1)
    mailet.service(mail2)
    mailet.service(mail3)

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(mail1.getState).isEqualTo("transport")
      softly.assertThat(mail2.getState).isEqualTo("error")
      softly.assertThat(mail3.getState).isEqualTo("transport")
    })
  }

  @Test
  def severalLimitsShouldBeApplied(): Unit = {
    val mailet = testee(FakeMailetConfig.builder()
      .mailetName("PerSenderRateLimit")
      .setProperty("duration", "20s")
      .setProperty("size", "100K")
      .setProperty("recipients", "3")
      .build())

    val mail1: Mail = FakeMail.builder()
      .name("mail1")
      .sender("sender@domain.tld")
      .recipients("rcpt1@linagora.com", "rcpt2@linagora.com")
      .size(50 * 1024)
      .state("transport")
      .build()

    // Will exceed the size rate limit
    val mail2: Mail = FakeMail.builder()
      .name("mail2")
      .sender("sender@domain.tld")
      .recipients("rcpt1@linagora.com", "rcpt3@linagora.com", "rcpt4@linagora.com")
      .size(60 * 1024)
      .state("transport")
      .build()

    // Will exceed the recipient rate limit
    val mail3: Mail = FakeMail.builder()
      .name("mail3")
      .sender("sender@domain.tld")
      .recipients("rcpt1@linagora.com", "rcpt3@linagora.com")
      .size(10 * 1024)
      .state("transport")
      .build()

    // Will exceed the recipient rate limit
    val mail4: Mail = FakeMail.builder()
      .name("mail4")
      .sender("sender@domain.tld")
      .recipients("rcpt1@linagora.com")
      .size(10 * 1024)
      .state("transport")
      .build()

    mailet.service(mail1)
    mailet.service(mail2)
    mailet.service(mail3)
    mailet.service(mail4)

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(mail1.getState).isEqualTo("transport")
      softly.assertThat(mail2.getState).isEqualTo("error")
      softly.assertThat(mail3.getState).isEqualTo("error")
      softly.assertThat(mail4.getState).isEqualTo("transport")
    })
  }

  @Nested
  class Configuration {
    @Test
    def shouldFailWhenNoDuration(): Unit = {
      assertThatThrownBy(() => testee(FakeMailetConfig.builder()
        .mailetName("PerSenderRateLimit")
        .setProperty("recipients", "3")
        .build()))
        .isInstanceOf(classOf[IllegalArgumentException])
    }

    @Test
    def shouldFailWhenEmptyDuration(): Unit = {
      assertThatThrownBy(() => testee(FakeMailetConfig.builder()
        .mailetName("PerSenderRateLimit")
        .setProperty("recipients", "3")
        .setProperty("duration", "")
        .build()))
        .isInstanceOf(classOf[IllegalArgumentException])
    }

    @Test
    def shouldFailWhenBadDuration(): Unit = {
      assertThatThrownBy(() => testee(FakeMailetConfig.builder()
        .mailetName("PerSenderRateLimit")
        .setProperty("recipients", "3")
        .setProperty("duration", "bad")
        .build()))
        .isInstanceOf(classOf[IllegalArgumentException])
    }

    @Test
    def shouldFailWhenNegativeDuration(): Unit = {
      assertThatThrownBy(() => testee(FakeMailetConfig.builder()
        .mailetName("PerSenderRateLimit")
        .setProperty("recipients", "3")
        .setProperty("duration", "-3s")
        .build()))
        .isInstanceOf(classOf[IllegalArgumentException])
    }

    @Test
    def shouldFailWhenZeroDuration(): Unit = {
      assertThatThrownBy(() => testee(FakeMailetConfig.builder()
        .mailetName("PerSenderRateLimit")
        .setProperty("recipients", "3")
        .setProperty("duration", "0s")
        .build()))
        .isInstanceOf(classOf[IllegalArgumentException])
    }

    @Test
    def shouldFailWhenTooSmallDuration(): Unit = {
      assertThatThrownBy(() => testee(FakeMailetConfig.builder()
        .mailetName("PerSenderRateLimit")
        .setProperty("recipients", "3")
        .setProperty("duration", "10ms")
        .build()))
        .isInstanceOf(classOf[IllegalArgumentException])
    }

    @Test
    def durationWithNoUnitShouldDefaultToSeconds(): Unit = {
      assertThat(
        testee(FakeMailetConfig.builder()
          .mailetName("PerSenderRateLimit")
          .setProperty("recipients", "3")
          .setProperty("duration", "10")
          .build()).parseDuration().getSeconds)
        .isEqualTo(10L)
    }

    @Test
    def durationShouldSupportUnits(): Unit = {
      assertThat(
        testee(FakeMailetConfig.builder()
          .mailetName("PerSenderRateLimit")
          .setProperty("recipients", "3")
          .setProperty("duration", "1h")
          .build()).parseDuration().getSeconds)
        .isEqualTo(3600L)
    }

    @Test
    def shouldFailWithEmptyRecipients(): Unit = {
      assertThatThrownBy(() => testee(FakeMailetConfig.builder()
          .mailetName("PerSenderRateLimit")
          .setProperty("recipients", "")
          .setProperty("duration", "10s")
          .build()).parseDuration().getSeconds)
        .isInstanceOf(classOf[IllegalArgumentException])
    }

    @Test
    def shouldFailWithZeroRecipients(): Unit = {
      assertThatThrownBy(() => testee(FakeMailetConfig.builder()
          .mailetName("PerSenderRateLimit")
          .setProperty("recipients", "0")
          .setProperty("duration", "10s")
          .build()).parseDuration().getSeconds)
        .isInstanceOf(classOf[IllegalArgumentException])
    }

    @Test
    def shouldFailWithNegativeRecipients(): Unit = {
      assertThatThrownBy(() => testee(FakeMailetConfig.builder()
          .mailetName("PerSenderRateLimit")
          .setProperty("recipients", "-1")
          .setProperty("duration", "10s")
          .build()).parseDuration().getSeconds)
        .isInstanceOf(classOf[IllegalArgumentException])
    }

    @Test
    def shouldFailWithBadRecipients(): Unit = {
      assertThatThrownBy(() => testee(FakeMailetConfig.builder()
          .mailetName("PerSenderRateLimit")
          .setProperty("recipients", "bad")
          .setProperty("duration", "10s")
          .build()).parseDuration().getSeconds)
        .isInstanceOf(classOf[IllegalArgumentException])
    }

    @Test
    def shouldFailWithEmptyCount(): Unit = {
      assertThatThrownBy(() => testee(FakeMailetConfig.builder()
          .mailetName("PerSenderRateLimit")
          .setProperty("count", "")
          .setProperty("duration", "10s")
          .build()).parseDuration().getSeconds)
        .isInstanceOf(classOf[IllegalArgumentException])
    }

    @Test
    def shouldFailWithZeroCount(): Unit = {
      assertThatThrownBy(() => testee(FakeMailetConfig.builder()
          .mailetName("PerSenderRateLimit")
          .setProperty("count", "0")
          .setProperty("duration", "10s")
          .build()).parseDuration().getSeconds)
        .isInstanceOf(classOf[IllegalArgumentException])
    }

    @Test
    def shouldFailWithNegativeCount(): Unit = {
      assertThatThrownBy(() => testee(FakeMailetConfig.builder()
          .mailetName("PerSenderRateLimit")
          .setProperty("count", "-1")
          .setProperty("duration", "10s")
          .build()).parseDuration().getSeconds)
        .isInstanceOf(classOf[IllegalArgumentException])
    }

    @Test
    def shouldFailWithBadCount(): Unit = {
      assertThatThrownBy(() => testee(FakeMailetConfig.builder()
          .mailetName("PerSenderRateLimit")
          .setProperty("count", "bad")
          .setProperty("duration", "10s")
          .build()).parseDuration().getSeconds)
        .isInstanceOf(classOf[IllegalArgumentException])
    }

    @Test
    def shouldFailWithEmptySize(): Unit = {
      assertThatThrownBy(() => testee(FakeMailetConfig.builder()
          .mailetName("PerSenderRateLimit")
          .setProperty("size", "")
          .setProperty("duration", "10s")
          .build()).parseDuration().getSeconds)
        .isInstanceOf(classOf[IllegalArgumentException])
    }

    @Test
    def shouldFailWithZeroSize(): Unit = {
      assertThatThrownBy(() => testee(FakeMailetConfig.builder()
          .mailetName("PerSenderRateLimit")
          .setProperty("size", "0")
          .setProperty("duration", "10s")
          .build()).parseDuration().getSeconds)
        .isInstanceOf(classOf[IllegalArgumentException])
    }

    @Test
    def shouldFailWithNegativeSize(): Unit = {
      assertThatThrownBy(() => testee(FakeMailetConfig.builder()
          .mailetName("PerSenderRateLimit")
          .setProperty("size", "-1000")
          .setProperty("duration", "10s")
          .build()).parseDuration().getSeconds)
        .isInstanceOf(classOf[IllegalArgumentException])
    }

    @Test
    def shouldFailWithBadSize(): Unit = {
      assertThatThrownBy(() => testee(FakeMailetConfig.builder()
          .mailetName("PerSenderRateLimit")
          .setProperty("size", "bad")
          .setProperty("duration", "10s")
          .build()).parseDuration().getSeconds)
        .isInstanceOf(classOf[IllegalArgumentException])
    }

    @Test
    def sizeShouldSupportUnits(): Unit = {
      assertThatCode(() => testee(FakeMailetConfig.builder()
          .mailetName("PerSenderRateLimit")
          .setProperty("size", "1k")
          .setProperty("duration", "10s")
          .build()).parseDuration().getSeconds)
        .doesNotThrowAnyException()
    }

    @Test
    def shouldFailWithEmptyTotalSize(): Unit = {
      assertThatThrownBy(() => testee(FakeMailetConfig.builder()
          .mailetName("PerSenderRateLimit")
          .setProperty("totalSize", "")
          .setProperty("duration", "10s")
          .build()).parseDuration().getSeconds)
        .isInstanceOf(classOf[IllegalArgumentException])
    }

    @Test
    def shouldFailWithZeroTotalSize(): Unit = {
      assertThatThrownBy(() => testee(FakeMailetConfig.builder()
          .mailetName("PerSenderRateLimit")
          .setProperty("totalSize", "0")
          .setProperty("duration", "10s")
          .build()).parseDuration().getSeconds)
        .isInstanceOf(classOf[IllegalArgumentException])
    }

    @Test
    def shouldFailWithNegativeTotalSize(): Unit = {
      assertThatThrownBy(() => testee(FakeMailetConfig.builder()
          .mailetName("PerSenderRateLimit")
          .setProperty("totalSize", "-1000")
          .setProperty("duration", "10s")
          .build()).parseDuration().getSeconds)
        .isInstanceOf(classOf[IllegalArgumentException])
    }

    @Test
    def shouldFailWithBadToatalSize(): Unit = {
      assertThatThrownBy(() => testee(FakeMailetConfig.builder()
          .mailetName("PerSenderRateLimit")
          .setProperty("totalSize", "bad")
          .setProperty("duration", "10s")
          .build()).parseDuration().getSeconds)
        .isInstanceOf(classOf[IllegalArgumentException])
    }

    @Test
    def totalSizeShouldSupportUnits(): Unit = {
      assertThatCode(() => testee(FakeMailetConfig.builder()
          .mailetName("PerSenderRateLimit")
          .setProperty("totalSize", "1k")
          .setProperty("duration", "10s")
          .build()).parseDuration().getSeconds)
        .doesNotThrowAnyException()
    }
  }
}
