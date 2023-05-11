package org.apache.james.lmtpserver.webadmin;

import javax.inject.Inject;

import org.apache.james.lmtpserver.netty.LMTPServerFactory;
import org.apache.james.protocols.lib.webadmin.AbstractServerRoutes;

public class LMTPRoutes extends AbstractServerRoutes {

    public static final String BASE_PATH = "/lmtp";

    @Inject
    public LMTPRoutes(LMTPServerFactory lmtpServerFactory) {
        this.serverFactory = lmtpServerFactory;
    }

    @Override
    public String getBasePath() {
        return BASE_PATH;
    }
}