package org.apache.james.backends.redis

import java.util.concurrent.TimeUnit

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.junit.jupiter.api.Test
import reactor.core.scala.publisher.SMono

trait RedisHealthCheckTest {
  def getRedisHealthCheck(): RedisHealthCheck

  def pauseRedis(): Unit

  def unpauseRedis(): Unit

  @Test
  def checkShouldReturnHealthyWhenRedisIsRunning(): Unit = {
    val result = SMono.fromPublisher(getRedisHealthCheck().check()).block()

    assertThat(result.isHealthy).isTrue
  }

  @Test
  def checkShouldReturnDegradedWhenRedisIsDown(): Unit = {
    pauseRedis()

    Awaitility.await()
      .pollInterval(2, TimeUnit.SECONDS)
      .atMost(20, TimeUnit.SECONDS)
      .untilAsserted(() => assertThat(SMono.fromPublisher(getRedisHealthCheck().check()).block().isDegraded).isTrue)
  }

  @Test
  def checkShouldReturnHealthyWhenRedisIsRecovered(): Unit = {
    pauseRedis()
    unpauseRedis()

    Awaitility.await()
      .pollInterval(2, TimeUnit.SECONDS)
      .atMost(20, TimeUnit.SECONDS)
      .untilAsserted(() => assertThat(SMono.fromPublisher(getRedisHealthCheck().check()).block().isHealthy).isTrue)
  }
}
