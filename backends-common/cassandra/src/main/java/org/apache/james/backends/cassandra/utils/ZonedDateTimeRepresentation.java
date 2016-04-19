

package org.apache.james.backends.cassandra.utils;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

public class ZonedDateTimeRepresentation {

    public static ZonedDateTimeRepresentation fromZonedDateTime(ZonedDateTime zonedDateTime) {
        return new ZonedDateTimeRepresentation(zonedDateTime);
    }

    public static ZonedDateTimeRepresentation fromDate(Date date, String serializedZoneId) {
        return new ZonedDateTimeRepresentation(ZonedDateTime.ofInstant(date.toInstant(), ZoneId.of(serializedZoneId)));
    }

    private final ZonedDateTime zonedDateTime;


    public ZonedDateTimeRepresentation(ZonedDateTime zonedDateTime) {
        this.zonedDateTime = zonedDateTime;
    }

    public Date getDate() {
        return new Date(zonedDateTime.toInstant().toEpochMilli());
    }

    public String getSerializedZoneId() {
        return zonedDateTime.getZone().getId();
    }

    public ZonedDateTime getZonedDateTime() {
        return zonedDateTime;
    }
}
