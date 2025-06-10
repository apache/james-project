/******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one                 *
 * or more contributor license agreements.  See the NOTICE file               *
 * distributed with this work for additional information                      *
 * regarding copyright ownership.  The ASF licenses this file                 *
 * to you under the Apache License, Version 2.0 (the                          *
 * "License"); you may not use this file except in compliance                 *
 * with the License.  You may obtain a copy of the License at                 *
 *                                                                            *
 *   http://www.apache.org/licenses/LICENSE-2.0                               *
 *                                                                            *
 * Unless required by applicable law or agreed to in writing,                 *
 * software distributed under the License is distributed on an                *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY                     *
 * KIND, either express or implied.  See the License for the                  *
 * specific language governing permissions and limitations                    *
 * under the License.                                                         *
 ******************************************************************************/

package org.apache.james.jdkim.mailets;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

public class CRLFInputStreamBenchmark {
    private static final byte[] CONTENT = loadMessage("long-multipart.msg");

    @Test
    @Disabled
    public void launchBenchmark() throws Exception {
        Options opt = new OptionsBuilder()
                .include(this.getClass().getName() + ".measure*")
                .mode(Mode.AverageTime)
                .addProfiler(GCProfiler.class)
                .timeUnit(TimeUnit.MICROSECONDS)
                .warmupTime(TimeValue.seconds(5))
                .warmupIterations(2)
                .measurementTime(TimeValue.seconds(5))
                .measurementIterations(5)
                .threads(1)
                .forks(1)
                .shouldFailOnError(true)
                .shouldDoGC(true)
                .build();

        new Runner(opt).run();
    }

    @Benchmark
    public void measure_wellformed(Blackhole bh) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (var byteArrayInputStream = new ByteArrayInputStream(CONTENT)) {
            byteArrayInputStream.transferTo(byteArrayOutputStream);
        }
        byteArrayOutputStream.close();
        byte[] byteArray = byteArrayOutputStream.toByteArray();
        bh.consume(byteArray);
    }

    @Benchmark
    public void measure_force_crlf_inputstream_wellformed(Blackhole bh) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (var byteArrayInputStream = new CRLFInputStream(new ByteArrayInputStream(CONTENT))) {
            byteArrayInputStream.transferTo(byteArrayOutputStream);
        }
        byteArrayOutputStream.close();
        byte[] byteArray = byteArrayOutputStream.toByteArray();
        bh.consume(byteArray);
    }

    @Benchmark
    public void measure_force_crlf_outputstream_wellformed(Blackhole bh) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (var byteArrayInputStream = new ByteArrayInputStream(CONTENT)) {
            byteArrayInputStream.transferTo(new CRLFOutputStream(byteArrayOutputStream));
        }
        byteArrayOutputStream.close();
        byte[] byteArray = byteArrayOutputStream.toByteArray();
        bh.consume(byteArray);
    }

    private static byte[] loadMessage(String resourceName) {
        try {
            ClassLoader cl = CRLFInputStreamBenchmark.class.getClassLoader();

            ByteArrayOutputStream outstream = new ByteArrayOutputStream();
            try (InputStream instream = cl.getResourceAsStream(resourceName)) {
                instream.transferTo(outstream);
            }
            outstream.close();
            return outstream.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
