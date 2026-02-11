package org.apache.james.smtpserver;

import org.apache.james.core.MaybeSender;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.hook.MailHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthRequiredHook implements MailHook {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthRequiredHook.class);
    protected static final HookResult AUTH_REQUIRED = HookResult.builder()
        .hookReturnCode(HookReturnCode.deny())
        .smtpReturnCode(SMTPRetCode.AUTH_REQUIRED)
        .smtpDescription(DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.SECURITY_AUTH)
            + " Authentication Required")
        .build();

    @Override
    public HookResult doMail(SMTPSession session, MaybeSender sender) {
        ExtendedSMTPSession nSession = (ExtendedSMTPSession) session;
        if (!session.isRelayingAllowed() && !nSession.senderVerificationConfiguration().allowUnauthenticatedSender()) {
            LOGGER.info("Authentication is required for sending email (sender: {})", sender.asString());
            return AUTH_REQUIRED;
        }
        return HookResult.DECLINED;
    }
}
