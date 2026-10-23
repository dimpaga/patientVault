package com.example.patientvault.appointment.internal;

import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** Pure logic that turns lead times into reminder instants. No Spring, no database. */
final class ReminderPlanner {

    private ReminderPlanner() {
    }

    /**
     * @return strictly future reminder instants (earliest first, no duplicates) for an appointment at
     *         {@code scheduledAt}; lead times that already passed are skipped, so rescheduling to a nearer time
     *         never triggers an immediate "24 hours before" mail
     */
    static List<Instant> plan(Instant scheduledAt, ZoneId zone, List<String> leadTimes, Instant now) {
        ZonedDateTime appointment = scheduledAt.atZone(zone);
        TreeSet<Instant> instants = new TreeSet<>();
        for (String lead : leadTimes) {
            Instant at = subtract(appointment, lead.strip()).toInstant();
            if (at.isAfter(now) && at.isBefore(scheduledAt)) {
                instants.add(at);
            }
        }
        return new ArrayList<>(instants);
    }

    /** Date-only amounts ("P1D", "P2W") are calendar based, anything with a time part is an exact duration. */
    static ZonedDateTime subtract(ZonedDateTime appointment, String isoAmount) {
        if (isoAmount.indexOf('T') < 0 && isoAmount.indexOf('t') < 0) {
            return appointment.minus(Period.parse(isoAmount));
        }
        return appointment.minus(Duration.parse(isoAmount));
    }

    /** Fails fast at start-up on a malformed lead time. */
    static void validate(List<String> leadTimes) {
        ZonedDateTime sample = ZonedDateTime.now();
        for (String lead : leadTimes) {
            subtract(sample, lead.strip());
        }
    }
}
