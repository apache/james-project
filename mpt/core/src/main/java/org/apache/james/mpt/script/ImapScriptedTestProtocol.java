package org.apache.james.mpt.script;

import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mpt.api.ImapHostSystem;

public class ImapScriptedTestProtocol extends GenericSimpleScriptedTestProtocol<ImapHostSystem, ImapScriptedTestProtocol> {


    private static class CreateMailbox implements PrepareCommand<ImapHostSystem> {

        final MailboxPath mailboxPath;

        CreateMailbox(MailboxPath mailboxPath) {
            this.mailboxPath = mailboxPath;
        }
        
        public void prepare(ImapHostSystem system) throws Exception {
            system.createMailbox(mailboxPath);
        }
    }
    
    public ImapScriptedTestProtocol(String scriptDirectory, ImapHostSystem hostSystem) throws Exception {
        super(scriptDirectory, hostSystem);
    }
    
    public ImapScriptedTestProtocol withMailbox(MailboxPath mailboxPath) {
        return withPreparedCommand(new CreateMailbox(mailboxPath));
    }

}