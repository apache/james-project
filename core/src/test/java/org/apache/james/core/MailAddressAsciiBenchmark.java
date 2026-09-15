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

package org.apache.james.core;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

/**
 * The addresses James sees the most: pure US-ASCII. RFC 6532 support must not
 * make this path more expensive, and it very nearly did -- {@code Domain.of}
 * used to run {@link java.net.IDN#toASCII} over every domain, ASCII or not,
 * which cost 5x on this benchmark.
 *
 * Deliberately uses no API beyond what an unpatched James exposes, so the same
 * class can be run against another revision of james-core to compare.
 *
 * Not run by the build: remove {@link Disabled} to measure locally.
 */
public class MailAddressAsciiBenchmark {
    @Test
    @Disabled("JMH benchmark, run on demand rather than on every build")
    public void launchBenchmark() throws Exception {
        Options opt = new OptionsBuilder()
            .include(this.getClass().getName() + ".*")
            .mode(Mode.AverageTime)
            .timeUnit(TimeUnit.NANOSECONDS)
            .warmupTime(TimeValue.seconds(1))
            .warmupIterations(5)
            .measurementTime(TimeValue.seconds(1))
            .measurementIterations(10)
            .threads(1)
            .forks(2)
            .shouldFailOnError(true)
            .shouldDoGC(true)
            .build();

        new Runner(opt).run();
    }

    @Benchmark
    public void shortAsciiAddress(Blackhole bh) throws Exception {
        bh.consume(new MailAddress("server-dev@james.apache.org"));
    }

    @Benchmark
    public void asciiAddressWithDetails(Blackhole bh) throws Exception {
        bh.consume(new MailAddress("user+mailbox/department=shipping@subdomain.example.com"));
    }

    @Benchmark
    public void longAsciiAddress(Blackhole bh) throws Exception {
        bh.consume(new MailAddress("a-fairly-long-local-part-as-mailing-lists-generate@lists.deeply.nested.subdomain.example.org"));
    }

    @Benchmark
    public void quotedAsciiLocalPart(Blackhole bh) throws Exception {
        bh.consume(new MailAddress("\"Fred Bloggs\"@example.com"));
    }

    @Benchmark
    public void asciiDomain(Blackhole bh) {
        bh.consume(Domain.of("lists.deeply.nested.subdomain.example.org"));
    }

    @Benchmark
    public void asciiAddressAsString(Blackhole bh) throws Exception {
        bh.consume(new MailAddress("server-dev@james.apache.org").asString());
    }
}
