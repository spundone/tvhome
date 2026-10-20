package com.spandan.tvhome;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Minimal iCalendar reader for a Google Calendar "secret address in iCal format".
 * Handles timed and all-day events, UTC / TZID times, EXDATE, RECURRENCE-ID overrides and the
 * common RRULE shapes (DAILY, WEEKLY with BYDAY, MONTHLY by date, YEARLY, UNTIL, COUNT, INTERVAL).
 */
final class CalendarFeed {
    private CalendarFeed() {}

    static final class Event {
        long start, end;
        boolean allDay;
        String title;
    }

    private static final class Raw {
        String uid = "", title = "", rrule = "", status = "";
        ZonedDateTime start, end;
        boolean allDay;
        List<ZonedDateTime> ex = new ArrayList<>();
        ZonedDateTime recId;
    }

    /** Upcoming and in-progress events between now and now + horizonDays, sorted by start. */
    static List<Event> parse(String ics, long nowMs, int horizonDays, ZoneId zone) {
        List<Raw> raws = new ArrayList<>();
        Raw cur = null;
        for (String line : unfold(ics)) {
            if (line.equals("BEGIN:VEVENT")) { cur = new Raw(); continue; }
            if (line.equals("END:VEVENT")) { if (cur != null && cur.start != null) raws.add(cur); cur = null; continue; }
            if (cur == null) continue;
            int colon = line.indexOf(':');
            if (colon < 0) continue;
            String head = line.substring(0, colon), val = line.substring(colon + 1);
            String name = head.contains(";") ? head.substring(0, head.indexOf(';')) : head;
            String params = head.contains(";") ? head.substring(head.indexOf(';') + 1) : "";
            switch (name) {
                case "UID": cur.uid = val; break;
                case "SUMMARY": cur.title = unescape(val); break;
                case "STATUS": cur.status = val; break;
                case "RRULE": cur.rrule = val; break;
                case "DTSTART": {
                    cur.allDay = params.contains("VALUE=DATE") && !params.contains("DATE-TIME");
                    cur.start = dt(val, params, zone);
                    break;
                }
                case "DTEND": cur.end = dt(val, params, zone); break;
                case "EXDATE":
                    for (String v : val.split(",")) { ZonedDateTime z = dt(v, params, zone); if (z != null) cur.ex.add(z); }
                    break;
                case "RECURRENCE-ID": cur.recId = dt(val, params, zone); break;
                default: break;
            }
        }
        long horizon = nowMs + horizonDays * 86400000L;
        Set<String> overridden = new HashSet<>();
        for (Raw r : raws) if (r.recId != null) overridden.add(r.uid + "@" + r.recId.toInstant().toEpochMilli());
        List<Event> out = new ArrayList<>();
        for (Raw r : raws) {
            if ("CANCELLED".equalsIgnoreCase(r.status)) continue;
            long dur = r.end != null ? r.end.toInstant().toEpochMilli() - r.start.toInstant().toEpochMilli()
                    : (r.allDay ? 86400000L : 3600000L);
            if (dur <= 0) dur = r.allDay ? 86400000L : 3600000L;
            if (r.rrule.isEmpty() || r.recId != null) {
                add(out, r, r.start.toInstant().toEpochMilli(), dur, nowMs, horizon);
            } else {
                Set<Long> ex = new HashSet<>();
                for (ZonedDateTime z : r.ex) ex.add(z.toInstant().toEpochMilli());
                for (ZonedDateTime occ : expand(r, horizon)) {
                    long t = occ.toInstant().toEpochMilli();
                    if (ex.contains(t) || overridden.contains(r.uid + "@" + t)) continue;
                    add(out, r, t, dur, nowMs, horizon);
                }
            }
        }
        Collections.sort(out, (a, b) -> Long.compare(a.start, b.start));
        return out;
    }

    private static void add(List<Event> out, Raw r, long start, long dur, long now, long horizon) {
        long end = start + dur;
        if (end <= now || start > horizon) return;
        Event e = new Event();
        e.start = start; e.end = end; e.allDay = r.allDay;
        e.title = r.title.isEmpty() ? "Busy" : r.title;
        out.add(e);
    }

    private static List<ZonedDateTime> expand(Raw r, long horizonMs) {
        Map<String, String> p = new HashMap<>();
        for (String kv : r.rrule.split(";")) {
            int i = kv.indexOf('=');
            if (i > 0) p.put(kv.substring(0, i), kv.substring(i + 1));
        }
        String freq = p.getOrDefault("FREQ", "");
        int interval = 1;
        try { interval = Math.max(1, Integer.parseInt(p.getOrDefault("INTERVAL", "1"))); } catch (Exception ignored) { }
        int count = Integer.MAX_VALUE;
        try { if (p.containsKey("COUNT")) count = Integer.parseInt(p.get("COUNT")); } catch (Exception ignored) { }
        long until = Long.MAX_VALUE;
        if (p.containsKey("UNTIL")) {
            ZonedDateTime u = dt(p.get("UNTIL"), "", r.start.getZone());
            if (u != null) until = u.toInstant().toEpochMilli() + (p.get("UNTIL").length() == 8 ? 86399000L : 0);
        }
        List<DayOfWeek> days = new ArrayList<>();
        if (p.containsKey("BYDAY")) {
            for (String d : p.get("BYDAY").split(",")) {
                String code = d.replaceAll("[^A-Z]", "");
                DayOfWeek dw = dow(code);
                if (dw != null) days.add(dw);
            }
        }
        List<ZonedDateTime> out = new ArrayList<>();
        int made = 0;
        for (int n = 0; n < 4000 && made < count; n++) {
            List<ZonedDateTime> batch = new ArrayList<>();
            switch (freq) {
                case "DAILY": batch.add(r.start.plusDays((long) n * interval)); break;
                case "WEEKLY": {
                    ZonedDateTime weekBase = r.start.plusWeeks((long) n * interval);
                    if (days.isEmpty()) batch.add(weekBase);
                    else {
                        ZonedDateTime monday = weekBase.minusDays(weekBase.getDayOfWeek().getValue() - 1);
                        for (DayOfWeek dw : days) {
                            ZonedDateTime c = monday.plusDays(dw.getValue() - 1);
                            if (!c.isBefore(r.start)) batch.add(c);
                        }
                        Collections.sort(batch);
                    }
                    break;
                }
                case "MONTHLY": batch.add(r.start.plusMonths((long) n * interval)); break;
                case "YEARLY": batch.add(r.start.plusYears((long) n * interval)); break;
                default: return out;
            }
            boolean past = false;
            for (ZonedDateTime c : batch) {
                long t = c.toInstant().toEpochMilli();
                if (t > until || t > horizonMs) { past = true; break; }
                if (made++ >= count) { past = true; break; }
                out.add(c);
            }
            if (past) break;
        }
        return out;
    }

    private static DayOfWeek dow(String c) {
        switch (c) {
            case "MO": return DayOfWeek.MONDAY; case "TU": return DayOfWeek.TUESDAY;
            case "WE": return DayOfWeek.WEDNESDAY; case "TH": return DayOfWeek.THURSDAY;
            case "FR": return DayOfWeek.FRIDAY; case "SA": return DayOfWeek.SATURDAY;
            case "SU": return DayOfWeek.SUNDAY; default: return null;
        }
    }

    private static ZonedDateTime dt(String v, String params, ZoneId dflt) {
        try {
            if (v.length() == 8) return LocalDate.parse(v, DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay(dflt);
            if (v.endsWith("Z")) {
                return LocalDateTime.parse(v.substring(0, v.length() - 1), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
                        .atZone(ZoneId.of("UTC")).withZoneSameInstant(dflt);
            }
            ZoneId z = dflt;
            int i = params.indexOf("TZID=");
            if (i >= 0) {
                String id = params.substring(i + 5);
                if (id.contains(";")) id = id.substring(0, id.indexOf(';'));
                try { z = ZoneId.of(id.replace("\"", "")); } catch (Exception ignored) { }
            }
            return LocalDateTime.parse(v, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")).atZone(z).withZoneSameInstant(dflt);
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> unfold(String ics) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = null;
        for (String raw : ics.split("\r?\n")) {
            if ((raw.startsWith(" ") || raw.startsWith("\t")) && cur != null) cur.append(raw.substring(1));
            else { if (cur != null) out.add(cur.toString()); cur = new StringBuilder(raw); }
        }
        if (cur != null) out.add(cur.toString());
        return out;
    }

    private static String unescape(String s) {
        return s.replace("\\n", " ").replace("\\N", " ").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\");
    }
}
