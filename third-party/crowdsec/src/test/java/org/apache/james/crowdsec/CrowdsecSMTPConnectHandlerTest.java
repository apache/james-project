package org.apache.james.crowdsec;

import java.io.IOException;
import java.net.URL;

import org.apache.james.crowdsec.client.CrowdsecClientConfiguration;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.utils.BaseFakeSMTPSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.apache.james.crowdsec.CrowdsecExtension.CROWDSEC_PORT;
import static org.assertj.core.api.Assertions.assertThat;

class CrowdsecSMTPConnectHandlerTest {
    @RegisterExtension
    static CrowdsecExtension crowdsecExtension = new CrowdsecExtension();

    private CrowdsecSMTPConnectHandler connectHandler;

    @BeforeEach
    void setUpEach() throws IOException {
        int port = crowdsecExtension.getCrowdsecContainer().getMappedPort(CROWDSEC_PORT);
        var crowdsecClientConfiguration = new CrowdsecClientConfiguration(new URL("http://localhost:" + port + "/v1"), CrowdsecClientConfiguration.DEFAULT_API_KEY);
        connectHandler = new CrowdsecSMTPConnectHandler(new CrowdsecService(crowdsecClientConfiguration));
    }

    @Test
    void givenIPBannedByCrowdsecDecision() throws IOException, InterruptedException {
        crowdsecExtension.banIP("--ip", "127.0.0.1");
        SMTPSession session = new BaseFakeSMTPSession() {};

        assertThat(connectHandler.onConnect(session)).isEqualTo(Response.DISCONNECT);
    }

    @Test
    void givenIPNotBannedByCrowdsecDecision() throws IOException, InterruptedException {
        crowdsecExtension.banIP("--range", "192.182.39.2/24");

        SMTPSession session = new BaseFakeSMTPSession() {};

        assertThat(connectHandler.onConnect(session)).isEqualTo(CrowdsecSMTPConnectHandler.NOOP);
    }
}