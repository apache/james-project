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
import org.apache.james.backends.es.DockerElasticSearch;
import org.apache.james.metrics.api.Metric;
import org.apache.james.metrics.api.TimeMetric;
import org.apache.james.metrics.dropwizard.DropWizardMetricFactory;
import org.apache.james.metrics.es.ESMetricReporter;
import org.apache.james.metrics.es.ESReporterConfiguration;
import org.awaitility.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.codahale.metrics.MetricRegistry;

import io.restassured.RestAssured;

abstract class ESReporterContract {
    public static final String INDEX = "index_name";
    public static final long PERIOD_IN_SECOND = 1L;
    public static final int DELAY_IN_MS = 100;
    public static final int PERIOD_IN_MS = 100;

    private ESMetricReporter esMetricReporter;
    private MetricRegistry registry;
    private Timer timer;

    @BeforeEach
    void setUp(DockerElasticSearch elasticSearch) {
        RestAssured.baseURI = String.format("http://%s:%d",
            elasticSearch.getHttpHost().getHostName(), elasticSearch.getHttpHost().getPort());
        await().atMost(Duration.ONE_MINUTE)
            .untilAsserted(this::elasticSearchStarted);

        registry = new MetricRegistry();
        timer = new Timer();
        esMetricReporter = new ESMetricReporter(
            ESReporterConfiguration.builder()
                .enabled()
                .onHost(elasticSearch.getHttpHost().getHostName(), elasticSearch.getHttpHost().getPort())
                .onIndex(INDEX)
                .periodInSecond(PERIOD_IN_SECOND)
                .build(),
            registry);

        esMetricReporter.start();
    }

    @AfterEach
    void tearDown() {
        timer.cancel();
        esMetricReporter.stop();
    }

    @Test
    void esMetricReporterShouldProduceDocumentsOnAnElasticsearchContainer() {
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
    void esMetricReporterShouldProduceDocumentsOnAnElasticsearchContainerWhenRecordingTimeMetric() {
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
