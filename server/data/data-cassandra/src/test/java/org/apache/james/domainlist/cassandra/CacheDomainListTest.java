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

package org.apache.james.domainlist.cassandra;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.UnknownHostException;
import java.time.Duration;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.StatementRecorder;
import org.apache.james.core.Domain;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.InMemoryDNSService;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

class CacheDomainListTest {

    Domain domain1 = Domain.of("domain1.tld");

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraDomainListModule.MODULE);

    CassandraDomainList domainList;

    @BeforeEach
    public void setUp(CassandraCluster cassandra) throws Exception {
        domainList = new CassandraDomainList(getDNSServer("localhost"), cassandra.getConf());
        domainList.configure(DomainListConfiguration.builder()
            .autoDetect(false)
            .autoDetectIp(false)
            .cacheEnabled(true)
            .cacheExpiracy(Duration.ofSeconds(1))
            .build());
    }

    @Test
    void containsShouldBeCached(CassandraCluster cassandra) throws DomainListException {
        domainList.addDomain(domain1);

        StatementRecorder statementRecorder = cassandra.getConf().recordStatements();

        Flux.range(0, 10)
            .doOnNext(Throwing.consumer(i -> domainList.containsDomain(domain1)))
            .blockLast();

        assertThat(statementRecorder.listExecutedStatements(
            StatementRecorder.Selector.preparedStatement("SELECT domain FROM domains WHERE domain=:domain")))
            .hasSize(1);
    }

    @Test
    void cacheShouldBeRefreshedPeriodicallyUnderReadLoad(CassandraCluster cassandra) throws DomainListException {
        domainList.addDomain(domain1);

        StatementRecorder statementRecorder = cassandra.getConf().recordStatements();

        Flux.range(0, 6)
            .delayElements(Duration.ofMillis(500))
            .flatMap(Throwing.function(i -> Mono.fromCallable(() -> domainList.containsDomain(domain1)).subscribeOn(Schedulers.boundedElastic())))
            .subscribeOn(Schedulers.newSingle("test"))
            .blockLast();

        assertThat(statementRecorder.listExecutedStatements(
            StatementRecorder.Selector.preparedStatement("SELECT domain FROM domains WHERE domain=:domain")))
            .hasSizeBetween(2, 3);
    }

    @Test
    void additionIsNotInstant() throws DomainListException {
        domainList.containsDomain(domain1);

        domainList.addDomain(domain1);

        assertThat(domainList.containsDomain(domain1)).isEqualTo(false);
    }

    @Test
    void removalIsNotInstant() throws DomainListException {
        domainList.addDomain(domain1);

        domainList.containsDomain(domain1);

        domainList.removeDomain(domain1);

        assertThat(domainList.containsDomain(domain1)).isEqualTo(true);
    }

    @Test
    void listShouldRefreshNewEntriesInCache() throws DomainListException {
        domainList.containsDomain(domain1);

        domainList.addDomain(domain1);

        domainList.getDomains();

        assertThat(domainList.containsDomain(domain1)).isEqualTo(true);
    }

    private DNSService getDNSServer(final String hostName) throws UnknownHostException {
        return new InMemoryDNSService()
            .registerMxRecord(hostName, "127.0.0.1")
            .registerMxRecord("127.0.0.1", "127.0.0.1");
    }
}
