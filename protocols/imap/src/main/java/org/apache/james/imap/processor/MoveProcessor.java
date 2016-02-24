package org.apache.james.imap.processor;

import java.util.Collections;
import java.util.List;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.SelectedMailbox;
import org.apache.james.imap.message.request.MoveRequest;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;

public class MoveProcessor extends AbstractMessageRangeProcessor<MoveRequest> implements CapabilityImplementingProcessor {

	private static final List<String> CAPS = Collections.unmodifiableList(Collections.singletonList(ImapConstants.MOVE_COMMAND_NAME));

	public MoveProcessor(ImapProcessor next, MailboxManager mailboxManager, StatusResponseFactory factory) {
		super(MoveRequest.class, next, mailboxManager, factory);
	}

    @Override
    protected List<MessageRange> process(MailboxPath targetMailbox,
                                         SelectedMailbox currentMailbox,
                                         MailboxSession mailboxSession,
                                         MailboxManager mailboxManager, MessageRange messageSet) throws MailboxException {
		return mailboxManager.moveMessages(messageSet, currentMailbox.getPath(), targetMailbox, mailboxSession);
	}

    @Override
    protected String getOperationName() {
        return "Move";
    }

    /**
    * @see org.apache.james.imap.processor.CapabilityImplementingProcessor
    * #getImplementedCapabilities(org.apache.james.imap.api.process.ImapSession)
    */
	public List<String> getImplementedCapabilities(ImapSession session) {
		return CAPS;
	}

}
