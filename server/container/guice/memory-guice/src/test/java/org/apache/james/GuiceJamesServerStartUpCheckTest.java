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

package org.apache.james;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Stream;

import org.apache.james.lifecycle.api.StartUpCheck;
import org.apache.james.modules.BlobExportImplChoice;
import org.apache.james.modules.TestJMAPServerModule;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.Inject;
import com.google.inject.multibindings.Multibinder;

class GuiceJamesServerStartUpCheckTest {

    private static class NoopStartUpCheck implements StartUpCheck {

        private static final String CHECK_NAME = "NoopStartUpCheck";

        @Override
        public CheckResult check() {
            return CheckResult.builder()
                .checkName(checkName())
                .resultType(ResultType.GOOD)
                .build();
        }

        @Override
        public String checkName() {
            return CHECK_NAME;
        }
    }

    private static class FailingStartUpCheck implements StartUpCheck {

        private static final String CHECK_NAME = "FaillingStartUpCheck";

        @Override
        public CheckResult check() {
            return CheckResult.builder()
                .checkName(checkName())
                .resultType(ResultType.BAD)
                .description("Failing by intention")
                .build();
        }

        @Override
        public String checkName() {
            return CHECK_NAME;
        }
    }

    private static class TestBlobExportMechanismStartUpCheck implements StartUpCheck {

        private static final String CHECK_NAME = "TestBlobExportMechanismStartUpCheck";

        @SuppressWarnings("unused")
        private final BlobExportImplChoice blobExportImplChoice;

        @Inject
        private TestBlobExportMechanismStartUpCheck(BlobExportImplChoice blobExportImplChoice) {
            // do no thing, just verify that start up checks are able to be injected by guice
            this.blobExportImplChoice = blobExportImplChoice;
        }

        @Override
        public CheckResult check() {
            return CheckResult.builder()
                .checkName(checkName())
                .resultType(ResultType.GOOD)
                .build();
        }

        @Override
        public String checkName() {
            return CHECK_NAME;
        }
    }

    interface StartUpCheckSuccessContract {

        @Test
        default void serverShouldStartSuccessfully(GuiceJamesServer server) throws Exception {
            server.start();

            assertThat(server.isStarted()).isTrue();
        }
    }

    private static JamesServerBuilder extensionBuilder() {
        return new JamesServerBuilder<>(JamesServerBuilder.defaultConfigurationProvider())
            .server(configuration -> GuiceJamesServer.forConfiguration(configuration)
                .combineWith(MemoryJamesServerMain.IN_MEMORY_SERVER_AGGREGATE_MODULE)
                .overrideWith(new TestJMAPServerModule()))
            .disableAutoStart();
    }

    @Nested
    class WithStartUpCheckDoesntRequireGuiceComponents implements StartUpCheckSuccessContract {

        @RegisterExtension
        JamesServerExtension jamesServerExtension = extensionBuilder()
            .overrideServerModule(binder -> Multibinder.newSetBinder(binder, StartUpCheck.class)
                .addBinding().to(NoopStartUpCheck.class))
            .build();
    }

    @Nested
    class WithStartUpCheckRequireGuiceComponents implements StartUpCheckSuccessContract {

        @RegisterExtension
        JamesServerExtension jamesServerExtension = extensionBuilder()
            .overrideServerModule(binder -> Multibinder.newSetBinder(binder, StartUpCheck.class)
                .addBinding().to(TestBlobExportMechanismStartUpCheck.class))
            .build();
    }

    @Nested
    class WithNoStartUpCheck implements StartUpCheckSuccessContract {

        @RegisterExtension
        JamesServerExtension jamesServerExtension = extensionBuilder().build();
    }

    @Nested
    class StartUpCheckFails {

        @RegisterExtension
        JamesServerExtension jamesServerExtension = extensionBuilder()
            .overrideServerModule(binder -> {
                    Multibinder<StartUpCheck> setBinder = Multibinder
                        .newSetBinder(binder, StartUpCheck.class);

                    setBinder.addBinding().to(NoopStartUpCheck.class);
                    setBinder.addBinding().to(FailingStartUpCheck.class);
                })
            .build();

        @Test
        void startUpCheckFailsShouldThrowAnExceptionCarryingOnlyBadChecks(GuiceJamesServer server) throws Exception {
            assertThatThrownBy(server::start)
                .isInstanceOfSatisfying(
                    StartUpChecksPerformer.StartUpChecksException.class,
                    exception -> assertThat(nameOfStartUpChecks(exception.getBadChecks()))
                        .containsOnly(FailingStartUpCheck.CHECK_NAME));
        }

        @Test
        void serverShouldNotStartWhenAStartUpCheckFails(GuiceJamesServer server) throws Exception {
            assertThatThrownBy(server::start)
                .isInstanceOf(StartUpChecksPerformer.StartUpChecksException.class);

            assertThat(server.isStarted())
                .isFalse();
        }

        private Stream<String> nameOfStartUpChecks(List<StartUpCheck.CheckResult> checkResults) {
            return checkResults.stream()
                .map(StartUpCheck.CheckResult::getName);
        }
    }
}
