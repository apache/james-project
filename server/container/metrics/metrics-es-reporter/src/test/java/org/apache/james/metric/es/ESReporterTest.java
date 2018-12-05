/****************************************************************
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

package org.apache.james.metric.es;

import static io.restassured.RestAssured.when;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.greaterThan;

import java.util.Timer;
import java.util.TimerTask;

import org.apache.http.HttpStatus;
import org.apache.james.metrics.api.Metric;
import org.apache.james.metrics.api.TimeMetric;
import org.apache.james.metrics.dropwizard.DropWizardMetricFactory;
import org.apache.james.metrics.es.ESMetricReporter;
import org.apache.james.metrics.es.ESReporterConfiguration;
import org.apache.james.util.docker.Images;
import org.apache.james.util.docker.RateLimiters;
import org.apache.james.util.docker.SwarmGenericContainer;
import org.awaitility.Duration;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;

import com.codahale.metrics.MetricRegistry;

import io.restassured.RestAssured;

public class ESReporterTest {
    public static final String INDEX = "index_name";
    public static final long PERIOD_IN_SECOND = 1L;
    public static final int DELAY_IN_MS = 100;
    public static final int PERIOD_IN_MS = 100;
    public static final int ES_HTTP_PORT = 9200;

    @Rule
    public SwarmGenericContainer esContainer = new SwarmGenericContainer(Images.ELASTICSEARCH)
        .withAffinityToContainer()
        .withExposedPorts(ES_HTTP_PORT)
        .waitingFor(new HostPortWaitStrategy().withRateLimiter(RateLimiters.TWENTIES_PER_SECOND));

    private ESMetricReporter esMetricReporter;
    private MetricRegistry registry;
    private Timer timer;

    @Before
    public void setUp() {
        RestAssured.baseURI = String.format("http://%s:%d", esContainer.getHostIp(), esContainer.getMappedPort(ES_HTTP_PORT));
        await().atMost(Duration.ONE_MINUTE)
            .untilAsserted(() -> elasticSearchStarted());

        registry = new MetricRegistry();
        timer = new Timer();
        esMetricReporter = new ESMetricReporter(
            ESReporterConfiguration.builder()
                .enabled()
                .onHost(esContainer.getHostIp(), esContainer.getMappedPort(ES_HTTP_PORT))
                .onIndex(INDEX)
                .periodInSecond(PERIOD_IN_SECOND)
                .build(),
            registry);
    }

    @After
    public void tearDown() {
        timer.cancel();
        esMetricReporter.stop();
    }

    @Test
    public void esMetricReporterShouldProduceDocumentsOnAnElasticsearchContainer() {
        esMetricReporter.start();

        Metric metric = new DropWizardMetricFactory(registry).generate("probe");
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                metric.increment();
            }
        };
        timer.schedule(timerTask, DELAY_IN_MS, PERIOD_IN_MS);

        await().atMost(Duration.TEN_MINUTES)
            .untilAsserted(() -> done());
    }

    @Test
    public void esMetricReporterShouldProduceDocumentsOnAnElasticsearchContainerWhenRecordingTimeMetric() {
        esMetricReporter.start();

        TimeMetric metric = new DropWizardMetricFactory(registry).timer("itstime");
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                metric.stopAndPublish();
            }
        };
        timer.schedule(timerTask, DELAY_IN_MS, PERIOD_IN_MS);

        await().atMost(Duration.TEN_MINUTES)
            .untilAsserted(() -> done());
    }

    private void elasticSearchStarted() {
        when()
            .get("/")
        .then()
            .assertThat()
                .statusCode(HttpStatus.SC_OK);
    }

    private void done() {
        when()
            .get("/_search")
        .then()
            .assertThat()
                .body("hits.total", greaterThan(0));
    }
}
