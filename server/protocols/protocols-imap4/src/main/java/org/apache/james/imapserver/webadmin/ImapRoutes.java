package org.apache.james.imapserver.webadmin;

import javax.inject.Inject;

import org.apache.james.imapserver.netty.IMAPServerFactory;
import org.apache.james.protocols.lib.webadmin.AbstractServerRoutes;

public class ImapRoutes
        extends AbstractServerRoutes {

    public static final String BASE_PATH = "/imap";

    @Inject
    public ImapRoutes(IMAPServerFactory imapServerFactory) {
        this.serverFactory = imapServerFactory;
    }

    @Override
    public String getBasePath() {
        return BASE_PATH;
    }
}