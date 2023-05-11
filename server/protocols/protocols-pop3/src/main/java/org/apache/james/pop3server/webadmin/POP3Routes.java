package org.apache.james.pop3server.webadmin;

import javax.inject.Inject;

import org.apache.james.pop3server.netty.POP3ServerFactory;
import org.apache.james.protocols.lib.webadmin.AbstractServerRoutes;

public class POP3Routes extends AbstractServerRoutes {

    public static final String BASE_PATH = "/pop3";

    @Inject
    public POP3Routes(POP3ServerFactory pop3ServerFactory) {
        this.serverFactory = pop3ServerFactory;
    }

    @Override
    public String getBasePath() {
        return BASE_PATH;
    }
}