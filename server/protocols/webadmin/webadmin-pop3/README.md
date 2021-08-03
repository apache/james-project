# Pop3 fix inconsistencies

We introduced an additional webadmin endpoint allowing fixing possible inconsistencies.

In order to run this task:

```bash
curl -XPOST 'http://ip:port/mailboxes?task=fixPop3Inconsistencies'
```

Will schedule a task for deleting stale and inserting missing POP3 meta data entries.

[More details about endpoints returning a task](https://james.staged.apache.org/james-project/3.6.0/servers/distributed/operate/webadmin.html#_endpoints_returning_a_task).

The scheduled task will have the following type `Pop3MetaDataFixInconsistenciesTask` and the following additionalInformation:

```json
{
    "type": "Pop3MetaDataFixInconsistenciesTask",
    "runningOptions": {
        "messagesPerSecond": 100
    },
    "processedImapUidEntries": 0,
    "processedPop3MetaDataStoreEntries": 1,
    "stalePOP3Entries": 1,
    "missingPOP3Entries": 0,
    "fixedInconsistencies": [
        {
            "mailboxId": "6fdae960-c72f-11eb-9b1d-973b9140e460",
            "messageId": "6ffca230-c72f-11eb-9b1d-973b9140e460"
        }
    ],
    "errors": [
        {
            "mailboxId": "7fdae960-c72f-11eb-9b1d-973b9140e460",
            "messageId": "7fdae960-c72f-11eb-2222-973b9140e460"
        }
    ]
}
```