package org.apache.james.jmap.delegation

import org.apache.james.jmap.core.{AccountId, SetError, UuidState}
import org.apache.james.jmap.method.WithAccountId

case class DelegatedAccountSetRequest(accountId: AccountId,
                                      destroy: Option[Seq[UnparsedDelegateId]]) extends WithAccountId

case class DelegatedAccountSetResponse(accountId: AccountId,
                                       oldState: Option[UuidState],
                                       newState: UuidState,
                                       destroyed: Option[Seq[DelegationId]],
                                       notDestroyed: Option[Map[UnparsedDelegateId, SetError]])