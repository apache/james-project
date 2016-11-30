package org.apache.james.protocols.lmtp.netty;

import java.net.InetSocketAddress;

import org.apache.james.protocols.api.Encryption;
import org.apache.james.protocols.api.Protocol;
import org.apache.james.protocols.api.ProtocolServer;
import org.apache.james.protocols.api.utils.BogusSslContextFactory;
import org.apache.james.protocols.lmtp.AbstractLMTPSServerTest;
import org.apache.james.protocols.netty.AbstractChannelPipelineFactory;
import org.apache.james.protocols.netty.NettyServer;
import org.jboss.netty.handler.codec.frame.DelimiterBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.Delimiters;

public class NettyLMTPSServerTest extends AbstractLMTPSServerTest{

    @Override
    protected ProtocolServer createServer(Protocol protocol, InetSocketAddress address) {
        NettyServer server =  new NettyServer(protocol, Encryption.createTls(BogusSslContextFactory.getServerContext()),
                new DelimiterBasedFrameDecoder(AbstractChannelPipelineFactory.MAX_LINE_LENGTH, false, Delimiters.lineDelimiter()));
        server.setListenAddresses(address);
        return server;
    }
    
}
