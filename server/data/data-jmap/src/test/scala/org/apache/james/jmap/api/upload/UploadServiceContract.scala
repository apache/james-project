 /***************************************************************
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

package org.apache.james.jmap.api.upload

import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.time.{Duration, Instant}

import org.apache.commons.io.IOUtils
import org.apache.james.core.Username
import org.apache.james.core.quota.QuotaSizeUsage
import org.apache.james.jmap.api.model.{UploadMetaData, UploadNotFoundException}
import org.apache.james.jmap.api.upload.UploadServiceContract.{BOB, CONTENT_TYPE, TEN_BYTES_DATA_STRING, UPLOAD_QUOTA_LIMIT, awaitAtMostTwoMinutes}
import org.apache.james.mailbox.model.ContentType
import org.assertj.core.api.Assertions.{assertThat, assertThatThrownBy}
import org.awaitility.Awaitility
import org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS
import org.junit.jupiter.api.Test
import reactor.core.scala.publisher.{SFlux, SMono}

object UploadServiceContract {
  private lazy val UPLOAD_QUOTA_LIMIT: Long = 100L
  lazy val TEST_CONFIGURATION: JmapUploadQuotaConfiguration = new JmapUploadQuotaConfiguration(UPLOAD_QUOTA_LIMIT)
  private lazy val CONTENT_TYPE: ContentType = ContentType
    .of("text/html")
  private lazy val TEN_BYTES_DATA_STRING: String = "0123456789"
  private lazy val BOB: Username = Username.of("Bob")

  private lazy val awaitAtMostTwoMinutes = Awaitility.`with`
    .pollInterval(ONE_HUNDRED_MILLISECONDS)
    .and.`with`.pollDelay(ONE_HUNDRED_MILLISECONDS)
    .atMost(Duration.ofMinutes(2))
    .await
}

trait UploadServiceContract {

  def uploadRepository: UploadRepository

  def uploadUsageRepository: UploadUsageRepository

  def testee: UploadService

  def asInputStream(string: String): InputStream = IOUtils.toInputStream(string, StandardCharsets.UTF_8)

  @Test
  def uploadShouldIncreaseUsedSpace(): Unit = {
    SMono.fromPublisher(testee.upload(asInputStream(TEN_BYTES_DATA_STRING), CONTENT_TYPE, BOB))
      .block()

    assertThat(SMono.fromPublisher(uploadUsageRepository.getSpaceUsage(BOB)).block().asLong())
      .isEqualTo(10L)
  }

  @Test
  def uploadShouldNotCleanJustUploadedFile(): Unit = {
    // given UPLOAD_QUOTA_LIMIT = 100bytes

    // upload 50 bytes
    val uploadId50 = SMono.fromPublisher(testee.upload(asInputStream(new String(new Array[Byte](50), java.nio.charset.StandardCharsets.UTF_8)), CONTENT_TYPE, BOB))
      .block().uploadId

    // upload 70 bytes
    val uploadId70 = SMono.fromPublisher(testee.upload(asInputStream(new String(new Array[Byte](70), java.nio.charset.StandardCharsets.UTF_8)), CONTENT_TYPE, BOB))
      .block().uploadId

    assertThat(SMono(testee.retrieve(uploadId70, BOB)).block().uploadId)
      .isEqualTo(uploadId70)

    awaitAtMostTwoMinutes.untilAsserted(() =>
      assertThatThrownBy(() => SMono(testee.retrieve(uploadId50, BOB)).block().uploadId)
        .isInstanceOf(classOf[UploadNotFoundException]))
  }

  @Test
  def uploadShouldIncreaseUsedSpaceWhenMultipleUploads(): Unit = {
    Range.inclusive(1, 9)
      .foreach(_ => SMono.fromPublisher(testee.upload(asInputStream(TEN_BYTES_DATA_STRING), CONTENT_TYPE, BOB))
        .block())

    assertThat(SMono.fromPublisher(uploadUsageRepository.getSpaceUsage(BOB)).block().asLong())
      .isEqualTo(90L)
  }

  @Test
  def givenUploadQuotaExceededThenUploadShouldCleanAtLeast50PercentSpace(): Unit = {
    Range.inclusive(1, 10)
      .foreach(_ => SMono.fromPublisher(testee.upload(asInputStream(TEN_BYTES_DATA_STRING), CONTENT_TYPE, BOB))
        .block())

    // Exceed 100 bytes limit
    SMono.fromPublisher(testee.upload(asInputStream(TEN_BYTES_DATA_STRING), CONTENT_TYPE, BOB))
      .block()

    awaitAtMostTwoMinutes.untilAsserted(() =>
      assertThat(SFlux(uploadRepository.listUploads(BOB))
        .map(_.size.value)
        .sum
        .block())
        .isLessThanOrEqualTo(UPLOAD_QUOTA_LIMIT / 2))
  }

  @Test
  def givenUploadQuotaExceededThenUploadShouldNotCleanAllSpace(): Unit = {
    Range.inclusive(1, 10)
      .foreach(_ => SMono.fromPublisher(testee.upload(asInputStream(TEN_BYTES_DATA_STRING), CONTENT_TYPE, BOB))
        .block())

    // Exceed 100 bytes limit
    SMono.fromPublisher(testee.upload(asInputStream(TEN_BYTES_DATA_STRING), CONTENT_TYPE, BOB))
      .block()

    awaitAtMostTwoMinutes.untilAsserted(() =>
      assertThat(SFlux(uploadRepository.listUploads(BOB))
        .map(_.size.value)
        .sum
        .block())
        .isGreaterThan(0L))
  }

  @Test
  def givenUploadQuotaExceededThenUploadShouldCleanOldestUploads(): Unit = {
    val uploads: Seq[UploadMetaData] = SFlux.fromIterable(Range.inclusive(1, 10))
      .concatMap(_ => SMono.fromPublisher(testee.upload(asInputStream(TEN_BYTES_DATA_STRING), CONTENT_TYPE, BOB)))
      .sort(Ordering.by[UploadMetaData, Instant](upload => upload.uploadDate))
      .collectSeq()
      .block()

    // Exceed 100 bytes limit
    SMono.fromPublisher(testee.upload(asInputStream(TEN_BYTES_DATA_STRING), CONTENT_TYPE, BOB))
      .block()

    // let the upload cleanup finish first
    awaitAtMostTwoMinutes.untilAsserted(() => {
      assertThat(SFlux(uploadRepository.listUploads(BOB))
        .map(_.size.value)
        .sum
        .block())
        .isLessThanOrEqualTo(UPLOAD_QUOTA_LIMIT / 2)
    })

    val remainingUploads: Seq[UploadMetaData] = SFlux(uploadRepository.listUploads(BOB)).sort(Ordering.by[UploadMetaData, Instant](_.uploadDate)).collectSeq().block()
    val oldestRemainingUpload: UploadMetaData = remainingUploads.head
    val deletedUploads: Seq[UploadMetaData] = uploads.filter(!remainingUploads.contains(_)).sorted(Ordering.by[UploadMetaData, Instant](_.uploadDate))
    val newestDeletedUpload: UploadMetaData = deletedUploads.last

    assertThat(oldestRemainingUpload.uploadDate).isAfterOrEqualTo(newestDeletedUpload.uploadDate)
  }

  @Test
  def uploadShouldUpdateCurrentStoredUsageUponCleaningUploadSpace(): Unit = {
    Range.inclusive(1, 10)
      .foreach(_ => SMono.fromPublisher(testee.upload(asInputStream(TEN_BYTES_DATA_STRING), CONTENT_TYPE, BOB))
        .block())

    // Exceed 100 bytes limit
    SMono.fromPublisher(testee.upload(asInputStream(TEN_BYTES_DATA_STRING), CONTENT_TYPE, BOB))
      .block()

    awaitAtMostTwoMinutes.untilAsserted(() =>
      assertThat(SMono.fromPublisher(uploadUsageRepository.getSpaceUsage(BOB)).block().asLong())
        .isLessThanOrEqualTo(UPLOAD_QUOTA_LIMIT / 2))
  }

  @Test
  def givenQuotaExceededThenUploadShouldRepairInconsistentCurrentUsage(): Unit = {
    Range.inclusive(1, 10)
      .foreach(_ => SMono.fromPublisher(testee.upload(asInputStream(TEN_BYTES_DATA_STRING), CONTENT_TYPE, BOB))
        .block())

    // Try to make the current stored usage inconsistent
    SMono(uploadUsageRepository.resetSpace(BOB, QuotaSizeUsage.size(105L))).block()

    // Exceed 100 bytes limit
    SMono.fromPublisher(testee.upload(asInputStream(TEN_BYTES_DATA_STRING), CONTENT_TYPE, BOB)).block()

    // The current stored usage should be eventually consistent
    awaitAtMostTwoMinutes.untilAsserted(() => {
      val currentStoredUsage: Long = SMono(uploadUsageRepository.getSpaceUsage(BOB))
        .block()
        .asLong()

      val totalCurrentUsage: Long = SFlux(uploadRepository.listUploads(BOB))
        .map(_.size.value)
        .sum
        .block()

      assertThat(currentStoredUsage).isEqualTo(totalCurrentUsage)
    })
  }

}
