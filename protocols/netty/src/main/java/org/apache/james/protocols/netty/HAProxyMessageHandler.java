package org.apache.james.protocols.netty;

import static org.apache.james.protocols.api.ProtocolSession.State.Connection;
import static org.apache.james.protocols.netty.BasicChannelInboundHandler.MDC_ATTRIBUTE_KEY;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import org.apache.james.protocols.api.CommandDetectionSession;
import org.apache.james.protocols.api.Protocol;
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.api.ProxyInformation;
import org.apache.james.util.MDCBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.handler.codec.haproxy.HAProxyProxiedProtocol;
import io.netty.util.AttributeKey;

public class HAProxyMessageHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(HAProxyMessageHandler.class);
    private static final AttributeKey<CommandDetectionSession> SESSION_ATTRIBUTE_KEY = AttributeKey.valueOf("session");

    private static String retrieveIp(ChannelHandlerContext ctx) {
        SocketAddress remoteAddress = ctx.channel().remoteAddress();
        if (remoteAddress instanceof InetSocketAddress) {
            InetSocketAddress address = (InetSocketAddress) remoteAddress;
            return address.getAddress().getHostAddress();
        }
        return remoteAddress.toString();
    }

    protected final Protocol protocol;
    private final ProtocolMDCContextFactory mdcContextFactory;

    public HAProxyMessageHandler(Protocol protocol, ProtocolMDCContextFactory mdcContextFactory) {
        this.protocol = protocol;
        this.mdcContextFactory = mdcContextFactory;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HAProxyMessage) {
            HAProxyMessage haproxyMsg = (HAProxyMessage) msg;

            ProtocolSession pSession = (ProtocolSession) ctx.channel().attr(SESSION_ATTRIBUTE_KEY).get();
            if (haproxyMsg.proxiedProtocol().equals(HAProxyProxiedProtocol.TCP4) || haproxyMsg.proxiedProtocol().equals(HAProxyProxiedProtocol.TCP6)) {

                ProxyInformation proxyInformation = new ProxyInformation(
                    new InetSocketAddress(haproxyMsg.sourceAddress(), haproxyMsg.sourcePort()),
                    new InetSocketAddress(haproxyMsg.destinationAddress(), haproxyMsg.destinationPort()));
                pSession.setProxyInformation(proxyInformation);

                LOGGER.info("Connection from {} runs through {} proxy", haproxyMsg.sourceAddress(), haproxyMsg.destinationAddress());
                // Refresh MDC info to account for proxying
                MDCBuilder boundMDC = mdcContextFactory.onBound(protocol, ctx);
                boundMDC.addToContext("proxy.source", proxyInformation.getSource().toString());
                boundMDC.addToContext("proxy.destination", proxyInformation.getDestination().toString());
                boundMDC.addToContext("proxy.ip", retrieveIp(ctx));
                pSession.setAttachment(MDC_ATTRIBUTE_KEY, boundMDC, Connection);
            } else {
                throw new IllegalArgumentException("Only TCP4/TCP6 are supported when using PROXY protocol.");
            }

            haproxyMsg.release();
            super.channelReadComplete(ctx);
        } else {
            super.channelRead(ctx, msg);
        }
    }
}
