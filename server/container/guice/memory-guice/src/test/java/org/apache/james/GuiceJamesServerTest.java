package org.apache.james;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.lifecycle.api.Startable;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.utils.InitializationOperation;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.multibindings.Multibinder;

class GuiceJamesServerTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(GuiceJamesServerTest.class);

    private static JamesServerBuilder extensionBuilder() {
        return new JamesServerBuilder<>(JamesServerBuilder.defaultConfigurationProvider())
            .server(configuration -> MemoryJamesServerMain.createServer(configuration)
                .overrideWith(new TestJMAPServerModule()))
            .disableAutoStart();
    }

    @Nested
    class NormalBehaviour {
        @RegisterExtension
        JamesServerExtension jamesServerExtension = extensionBuilder().build();

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
        private final InitializationOperation throwingInitializationOperation = new InitializationOperation() {
            @Override
            public void initModule() {
                throw new RuntimeException();
            }

            @Override
            public Class<? extends Startable> forClass() {
                return Startable.class;
            }
        };

        @RegisterExtension
        JamesServerExtension jamesServerExtension = extensionBuilder()
            .overrideServerModule(binder -> Multibinder.newSetBinder(binder, InitializationOperation.class)
                .addBinding()
                .toInstance(throwingInitializationOperation))
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
