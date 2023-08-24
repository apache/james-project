package org.apache.james.jmap.api.upload

import org.apache.james.core.Username
import org.apache.james.core.quota.QuotaSizeUsage
import org.apache.james.jmap.api.upload.UploadUsageRepositoryContract.USER_NAME
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import reactor.core.scala.publisher.SMono

object UploadUsageRepositoryContract {
  val USER_NAME = Username.of("james@abc.com")
}

trait UploadUsageRepositoryContract {
  def uploadUsageRepository: UploadUsageRepository

  @Test
  def increaseSpaceShouldIncreaseSuccessfully(): Unit = {
    Assertions.assertThat(SMono.fromPublisher(uploadUsageRepository.getSpaceUsage(USER_NAME)).block().asLong()).isEqualTo(0);
    SMono.fromPublisher(uploadUsageRepository.increaseSpace(USER_NAME, QuotaSizeUsage.size(100))).block();
    val expected = SMono.fromPublisher(uploadUsageRepository.getSpaceUsage(USER_NAME)).block();
    Assertions.assertThat(expected.asLong()).isEqualTo(100);
  }

  @Test
  def decreaseSpaceShouldDecreaseSuccessfully(): Unit = {
    SMono.fromPublisher(uploadUsageRepository.increaseSpace(USER_NAME, QuotaSizeUsage.size(200))).block();
    Assertions.assertThat(SMono.fromPublisher(uploadUsageRepository.getSpaceUsage(USER_NAME)).block().asLong()).isEqualTo(200);
    SMono.fromPublisher(uploadUsageRepository.decreaseSpace(USER_NAME, QuotaSizeUsage.size(100))).block();
    val expected = SMono.fromPublisher(uploadUsageRepository.getSpaceUsage(USER_NAME)).block();
    Assertions.assertThat(expected.asLong()).isEqualTo(100);
  }

  @Test
  def resetSpaceShouldResetSuccessfully(): Unit = {
    SMono.fromPublisher(uploadUsageRepository.increaseSpace(USER_NAME, QuotaSizeUsage.size(200))).block();
    Assertions.assertThat(SMono.fromPublisher(uploadUsageRepository.getSpaceUsage(USER_NAME)).block().asLong()).isEqualTo(200);
    SMono.fromPublisher(uploadUsageRepository.resetSpace(USER_NAME, QuotaSizeUsage.size(100))).block();
    val expected = SMono.fromPublisher(uploadUsageRepository.getSpaceUsage(USER_NAME)).block();
    Assertions.assertThat(expected.asLong()).isEqualTo(100);
  }

  @Test
  def getSpaceUsageShouldReturnZeroWhenRecordDoesNotExist(): Unit = {
    Assertions.assertThat(SMono.fromPublisher(uploadUsageRepository.getSpaceUsage(Username.of("aaa"))).block().asLong()).isEqualTo(0);
  }

}
