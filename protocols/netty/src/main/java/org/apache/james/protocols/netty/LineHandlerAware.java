package org.apache.james.protocols.netty;

public interface LineHandlerAware {
    void pushLineHandler(LineHandlerUpstreamHandler lineHandlerUpstreamHandler);

    void popLineHandler();
}
