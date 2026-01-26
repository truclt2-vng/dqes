/**
 * Created: Jan 26, 2026 3:16:41 PM
 * Copyright © 2026 by A4B. All rights reserved
 */
package com.a4b.dqes.util;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public final class DateTimeUtil {

    private DateTimeUtil() {}

    public static OffsetDateTime startOfDay(LocalDate date, ZoneId zone) {
        return date.atStartOfDay(zone)
                   .toOffsetDateTime();
    }

    public static OffsetDateTime endOfDay(LocalDate date, ZoneId zone) {
        return date.atTime(LocalTime.MAX)
                   .atZone(zone)
                   .toOffsetDateTime();
    }


    public static OffsetDateTime startOfDay(OffsetDateTime input) {
        return input.toLocalDate().atStartOfDay(input.getOffset()).toOffsetDateTime();
    }

    public static OffsetDateTime endOfDay(OffsetDateTime input) {
        return input.toLocalDate().atTime(LocalTime.MAX).atOffset(input.getOffset());
    }

    
}
