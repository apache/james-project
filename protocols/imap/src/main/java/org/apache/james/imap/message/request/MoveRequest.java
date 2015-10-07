package org.apache.james.imap.message.request;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.request.ImapRequest;

/**
 * {@link ImapRequest} which request the move of messages
 */
public class MoveRequest extends CopyRequest {

	public MoveRequest(ImapCommand command, IdRange[] idSet,
			String mailboxName, boolean useUids, String tag) {
		super(command, idSet, mailboxName, useUids, tag);
	}

}
