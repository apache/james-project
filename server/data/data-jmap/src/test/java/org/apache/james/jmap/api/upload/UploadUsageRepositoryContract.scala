package org.apache.james.jmap.api.upload

import org.apache.james.core.Username
import org.apache.james.core.quota.QuotaSizeUsage
import org.apache.james.jmap.api.upload.UploadUsageRepositoryContract.USER_NAME
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import reactor.core.scala.publisher.SMono

object UploadUsageRepositoryContract {
  val USER_NAME = Username.of("james@abc.com")
}

trait UploadUsageRepositoryContract {
  def uploadUsageRepository: UploadUsageRepository

  def resetCounterToZero(): Unit = {
    Mono.from(uploadUsageRepository.resetSpace(USER_NAME, QuotaSizeUsage.size(0))).block
  }

  @Test
  def increaseSpaceShouldIncreaseSuccessfully(): Unit = {
    SMono.fromPublisher(uploadUsageRepository.increaseSpace(USER_NAME, QuotaSizeUsage.size(100))).block();
    val expected = SMono.fromPublisher(uploadUsageRepository.getSpaceUsage(USER_NAME)).block();
    assertThat(expected.asLong()).isEqualTo(100);
  }

  @Test
  def decreaseSpaceShouldDecreaseSuccessfully(): Unit = {
    SMono.fromPublisher(uploadUsageRepository.increaseSpace(USER_NAME, QuotaSizeUsage.size(200))).block();
    SMono.fromPublisher(uploadUsageRepository.decreaseSpace(USER_NAME, QuotaSizeUsage.size(100))).block();
    val expected = SMono.fromPublisher(uploadUsageRepository.getSpaceUsage(USER_NAME)).block();
    assertThat(expected.asLong()).isEqualTo(100);
  }

  @Test
  def resetSpaceShouldResetSuccessfully(): Unit = {
    SMono.fromPublisher(uploadUsageRepository.increaseSpace(USER_NAME, QuotaSizeUsage.size(200))).block();
    SMono.fromPublisher(uploadUsageRepository.resetSpace(USER_NAME, QuotaSizeUsage.size(100))).block();
    val expected = SMono.fromPublisher(uploadUsageRepository.getSpaceUsage(USER_NAME)).block();
    assertThat(expected.asLong()).isEqualTo(100);
  }

  @Test
  def getSpaceUsageShouldReturnZeroWhenRecordDoesNotExist(): Unit = {
    assertThat(SMono.fromPublisher(uploadUsageRepository.getSpaceUsage(Username.of("aaa"))).block().asLong()).isEqualTo(0);
  }

}
