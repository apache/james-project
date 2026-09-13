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
 * RFC 6532 addresses. There is no before/after to compare here -- James used
 * to reject these outright -- only the absolute cost of the new path.
 *
 * Unicode domains are the expensive part by a wide margin: {@code Domain.of}
 * runs {@link java.net.IDN#toASCII} and then, seeing the {@code xn--} labels
 * it just produced, {@link java.net.IDN#toUnicode} to get back, so a Unicode
 * domain pays for a full Punycode round trip.
 *
 * Not run by the build: remove {@link Disabled} to measure locally.
 */
public class MailAddressUnicodeBenchmark {
    // Checkstyle forbids \\uXXXX escapes and a bare combining mark cannot be
    // spelled out as a source literal, so build it by code point.
    private static final String COMBINING_ACUTE = String.valueOf((char) 0x0301);

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

    /** Already NFC: normalisation should be skipped outright. */
    @Benchmark
    public void unicodeAddressAlreadyNfc(Blackhole bh) throws Exception {
        bh.consume(new MailAddress("pelé@exemple.com"));
    }

    /** NFD input: the normaliser has real work to do. */
    @Benchmark
    public void unicodeAddressNeedingNfc(Blackhole bh) throws Exception {
        bh.consume(new MailAddress("pele" + COMBINING_ACUTE + "@example.com"));
    }

    @Benchmark
    public void cjkAddress(Blackhole bh) throws Exception {
        bh.consume(new MailAddress("二ノ宮@黒川.日本"));
    }

    @Benchmark
    public void unicodeDomain(Blackhole bh) {
        bh.consume(Domain.of("παράδειγμα.δοκιμή"));
    }

    @Benchmark
    public void aceDomain(Blackhole bh) {
        bh.consume(Domain.of("xn--hxajbheg2az3al.xn--jxalpdlp"));
    }
}
