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

package org.apache.james.backends.cassandra;

public class DockerCassandraSingleton {
    @FunctionalInterface
    interface BeforeHook {
        void run() throws Exception;
    }

    @FunctionalInterface
    interface AfterHook {
        void run() throws Exception;
    }

    private static final int MAX_TEST_PLAYED = 500;

    private static int testsPlayedCount = 0;

    public static final DockerCassandra singleton = new DockerCassandra();

    public static void incrementTestsPlayed() {
        testsPlayedCount += 1;
    }

    // Call this method to ensure that cassandra is restarted every MAX_TEST_PLAYED tests
    public static void restartAfterMaxTestsPlayed(BeforeHook before, AfterHook after) throws Exception {
        if (testsPlayedCount > MAX_TEST_PLAYED) {
            testsPlayedCount = 0;
            before.run();
            restart();
            after.run();
        }
    }

    // Call this method to ensure that cassandra is restarted every MAX_TEST_PLAYED tests
    public static void restartAfterMaxTestsPlayed() throws Exception {
        restartAfterMaxTestsPlayed(() -> { }, () -> { });
    }

    private static void restart() {
        singleton.stop();
        singleton.start();
    }

    // Cleanup will be performed by test container resource reaper
}
