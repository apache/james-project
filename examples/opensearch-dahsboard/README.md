# OpenSearch dashboard for Apache James

[OpenSearch dashboard](https://opensearch.org/docs/latest/dashboards/) is a solution allowing data visualization on top of an existing OpenSearch cluster.

It can be used to create interesting visualizations when used on top of Apache James OpenSearch indexes.

## Set up

1. Adapt the `docker-compose.yml` file to your needs (IP address & credentials)
2. Start it with `docker compose up`
3. Login in your browser: `http://127.0.0.1:5601` with the credential documented in the `docker-compose.yml` file
4. In `Dashboard Management > Index pattern` import the `mailbox_v2` (using date as a date field) and `quota_ratio_v1` 
5. In `Dashboard Management > Saved objects` import `james-dashboard.ndjson`

## Supported visualizations

 - Quota usage

![img.png](img.png)

 - New mails timeline

![img_1.png](img_1.png)

 - Top senders

![img_2.png](img_2.png)

 - Top recipients

![img_3.png](img_3.png)

 - Email size distribution

![img_4.png](img_4.png)

 - Attachment usage report

![img_5.png](img_5.png)

 - Total email created during the period

![img_6.png](img_6.png)

 - Unique email (by message-id)

![img_7.png](img_7.png)

 - Read / unread ratio

![img_8.png](img_8.png)

 - has attachment ratio

![img_9.png](img_9.png)

 - user flag heatmap

![img_10.png](img_10.png)

 - People that reads the more mails

![img_12.png](img_12.png)

 - People that answers the more mails

![img_11.png](img_11.png)

 - People that generates the most data (by size)

![img_13.png](img_13.png)

 - Recipient count analysis

![img_14.png](img_14.png)

 - Estimated delivery times in second (differences between sentDate and savedDate)

![img_15.png](img_15.png)

## Scripted fields

Some of the above visualizations relies on scripted fields.

In order to create them navigate the following menus:

> Dashboard management
> Index pattern
> mailbox_v2
> scripted field

Then create the following scripted fields:

```
language: painless
type: number
format: numer
name: recipientCount
Script:

int count = 0;

    for (nested in params._source.to) {
        count += 1;
    }   

return count;
```

and 

```
> Dashboard management
> Index pattern
> mailbox_v2
> scripted field

language: painless
type: number
format: numer
name: deliveryTime
Script:

if (doc.containsKey('saveDate') && doc.containsKey('sentDate')) {
    return (doc['saveDate'].value.getMillis() - doc['sentDate'].value.getMillis()) / 1000;  
} else {
    return 0;
}
```