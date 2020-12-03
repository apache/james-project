package org.apache.james.protocols.lmtp.netty;

import java.net.InetSocketAddress;

import org.apache.james.protocols.api.Encryption;
import org.apache.james.protocols.api.Protocol;
import org.apache.james.protocols.api.ProtocolServer;
import org.apache.james.protocols.api.utils.BogusSslContextFactory;
import org.apache.james.protocols.lmtp.AbstractLMTPSServerTest;
import org.apache.james.protocols.netty.NettyServer;
import org.jboss.netty.util.HashedWheelTimer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public class NettyLMTPSServerTest extends AbstractLMTPSServerTest {

    private static final String LOCALHOST_IP = "127.0.0.1";
    private static final int RANDOM_PORT = 0;

    private HashedWheelTimer hashedWheelTimer;

    @BeforeEach
    public void setup() {
        hashedWheelTimer = new HashedWheelTimer();
    }

    @AfterEach
    public void teardown() {
        hashedWheelTimer.stop();
    }

    @Override
    protected ProtocolServer createServer(Protocol protocol) {
        NettyServer server = new NettyServer.Factory(hashedWheelTimer)
                .protocol(protocol)
                .secure(Encryption.createTls(BogusSslContextFactory.getServerContext()))
                .build();
        server.setListenAddresses(new InetSocketAddress(LOCALHOST_IP, RANDOM_PORT));
        return server;
    }
    
}
