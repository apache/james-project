## Additional webadmin endpoints

### Report spam messages to RSpamD
One can use this route to schedule a task that reports spam messages to RSpamD for its spam classify learning.

```bash
curl -XPOST 'http://ip:port/rspamd?action=reportSpam
```

This endpoint has the following param:
- `action` (required): need to be `reportSpam`
- `messagesPerSecond` (optional): Concurrent learns performed for RSpamD, default to 10
- `period` (optional): duration (support many time units, default in seconds), only messages between `now` and `now - duration` are reported. By default, 
all messages are reported. 
   These inputs represent the same duration: `1d`, `1day`, `86400 seconds`, `86400`...
- `samplingProbability` (optional): float between 0 and 1, represent the chance to report each given message to RSpamD. 
By default, all messages are reported.

Will return the task id. E.g:
```
{
    "taskId": "70c12761-ab86-4321-bb6f-fde99e2f74b0"
}
```

Response codes:
- 201: Task generation succeeded. Corresponding task id is returned.
- 400: Invalid arguments supplied in the user request.

[More details about endpoints returning a task](https://james.apache.org/server/manage-webadmin.html#Endpoints_returning_a_task).

The scheduled task will have the following type `FeedSpamToRSpamDTask` and the following additionalInformation:

```json
{
  "errorCount": 1,
  "reportedSpamMessageCount": 2,
  "runningOptions": {
    "messagesPerSecond": 10,
    "periodInSecond": 3600,
    "samplingProbability": 1.0
  },
  "spamMessageCount": 4,
  "timestamp": "2007-12-03T10:15:30Z",
  "type": "FeedSpamToRSpamDTask"
}
```