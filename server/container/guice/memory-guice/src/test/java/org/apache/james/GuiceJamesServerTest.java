package org.apache.james;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.store.search.PDFTextExtractor;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.utils.ConfigurationPerformer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.inject.multibindings.Multibinder;

class GuiceJamesServerTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(GuiceJamesServerTest.class);

    private static final int LIMIT_TO_10_MESSAGES = 10;

    @Nested
    class NormalBehaviour {
        @RegisterExtension
        JamesServerExtension jamesServerExtension = new JamesServerExtensionBuilder()
            .server(configuration -> GuiceJamesServer.forConfiguration(configuration)
                .combineWith(MemoryJamesServerMain.IN_MEMORY_SERVER_AGGREGATE_MODULE)
                .overrideWith(new TestJMAPServerModule(LIMIT_TO_10_MESSAGES))
                .overrideWith(binder -> binder.bind(TextExtractor.class).to(PDFTextExtractor.class)))
            .disableAutoStart()
            .build();

        @Test
        void serverShouldBeStartedAfterCallingStart(GuiceJamesServer server) throws Exception {
            server.start();

            assertThat(server.isStarted()).isTrue();
        }

        @Test
        void serverShouldNotBeStartedAfterCallingStop(GuiceJamesServer server) throws Exception {
            server.start();

            server.stop();

            assertThat(server.isStarted()).isFalse();
        }

        @Test
        void serverShouldNotBeStartedBeforeCallingStart(GuiceJamesServer server) {
            assertThat(server.isStarted()).isFalse();
        }
    }

    @Nested
    class InitFailed {
        private final ConfigurationPerformer THROWING_CONFIGURATION_PERFORMER = new ConfigurationPerformer() {
            @Override
            public void initModule() {
                throw new RuntimeException();
            }

            @Override
            public List<Class<? extends Configurable>> forClasses() {
                return ImmutableList.of();
            }
        };

        @RegisterExtension
        JamesServerExtension jamesServerExtension = new JamesServerExtensionBuilder()
            .server(configuration -> GuiceJamesServer.forConfiguration(configuration)
                .combineWith(MemoryJamesServerMain.IN_MEMORY_SERVER_AGGREGATE_MODULE)
                .overrideWith(new TestJMAPServerModule(LIMIT_TO_10_MESSAGES))
                .overrideWith(binder -> binder.bind(TextExtractor.class).to(PDFTextExtractor.class))
                .overrideWith(binder -> Multibinder.newSetBinder(binder, ConfigurationPerformer.class)
                    .addBinding()
                    .toInstance(THROWING_CONFIGURATION_PERFORMER)))
            .disableAutoStart()
            .build();

        @Test
        void serverShouldPropagateUncaughtConfigurationException(GuiceJamesServer server) {
            assertThatThrownBy(server::start)
                .isInstanceOf(RuntimeException.class);
        }

        @Test
        void serverShouldNotBeStartedOnUncaughtException(GuiceJamesServer server) throws Exception {
            try {
                server.start();
            } catch (RuntimeException e) {
                LOGGER.info("Ignored expected exception", e);
            }

            assertThat(server.isStarted()).isFalse();
        }
    }
}
