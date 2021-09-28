package org.apache.james.webadmin.integration.memory;

import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.MemoryJamesServerMain;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.webadmin.integration.QuotaSearchIntegrationTest;
import org.junit.jupiter.api.extension.RegisterExtension;

public class MemoryQuotaSearchIntegrationTest extends QuotaSearchIntegrationTest {
    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<>(JamesServerBuilder.defaultConfigurationProvider())
        .server(configuration -> MemoryJamesServerMain.createServer(configuration)
            .overrideWith(new TestJMAPServerModule()))
        .build();
}
