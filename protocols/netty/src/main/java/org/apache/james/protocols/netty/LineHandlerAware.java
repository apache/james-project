package org.apache.james.protocols.netty;

import io.netty.channel.ChannelInboundHandlerAdapter;

public interface LineHandlerAware {
    void pushLineHandler(ChannelInboundHandlerAdapter lineHandlerUpstreamHandler);

    void popLineHandler();
}
