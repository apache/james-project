package org.apache.james.smtpserver.webadmin;

import javax.inject.Inject;

import org.apache.james.protocols.lib.webadmin.AbstractServerRoutes;
import org.apache.james.smtpserver.netty.SMTPServerFactory;

public class SmtpRoutes extends AbstractServerRoutes {

    public static final String BASE_PATH = "/smtp";

    @Inject
    public SmtpRoutes(SMTPServerFactory imapServerFactory) {
        this.serverFactory = imapServerFactory;
    }

    @Override
    public String getBasePath() {
        return BASE_PATH;
    }
}