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
 * ************************************************************** */

package org.apache.james.jmap.api.upload

import java.nio.charset.StandardCharsets

import org.apache.commons.io.IOUtils
import org.apache.james.core.Username
import org.apache.james.core.quota.QuotaSizeUsage
import org.apache.james.jmap.api.upload.JMAPCurrentUploadUsageCalculatorContract.USER_1
import org.apache.james.mailbox.model.ContentType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono

object JMAPCurrentUploadUsageCalculatorContract {
  val USER_1: Username = Username.of("user1")
}

trait JMAPCurrentUploadUsageCalculatorContract {
  def uploadRepository: UploadRepository
  def uploadUsageRepository: UploadUsageRepository
  def currentUploadUsageRecomputator: JMAPCurrentUploadUsageCalculator

  @Test
  def recomputeCurrentUploadUsageShouldRecomputeSuccessfully(): Unit = {
    uploadUsageRepository.increaseSpace(USER_1, QuotaSizeUsage.size(100L));

    val data1 = IOUtils.toInputStream("123456", StandardCharsets.UTF_8)
    Mono.from(uploadRepository.upload(data1, ContentType.of("text/html"), USER_1)).block()
    val data2 = IOUtils.toInputStream("12345678", StandardCharsets.UTF_8)
    Mono.from(uploadRepository.upload(data2, ContentType.of("text/html"), USER_1)).block();

    currentUploadUsageRecomputator.recomputeCurrentUploadUsage(USER_1).block();
    assertThat(Mono.from(uploadUsageRepository.getSpaceUsage(USER_1)).block.asLong()).isEqualTo(14L)
  }

  @Test
  def recomputeCurrentUploadUsageShouldRecomputeSuccessfullyWhenUserHasNoUpload(): Unit = {
    uploadUsageRepository.increaseSpace(USER_1, QuotaSizeUsage.size(100L));

    currentUploadUsageRecomputator.recomputeCurrentUploadUsage(USER_1).block();
    assertThat(Mono.from(uploadUsageRepository.getSpaceUsage(USER_1)).block.asLong()).isEqualTo(0L)
  }
}
