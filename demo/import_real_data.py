#!/usr/bin/env python3
"""Turns two real, published incident logs into SQL for the fairness tracker's schema.

    python3 -I demo/import_real_data.py              # downloads the logs (once), writes demo/real-data.sql
    python3 -I demo/import_real_data.py --keep-dates # the same, with the dates as recorded

Datasets (both real incident management logs; people and teams as recorded, anonymised by their publishers):
  * Volvo IT, BPI Challenge 2013 incidents: Ward Steeman (2013), 4TU.ResearchData,
    doi:10.4121/uuid:500573e6-accc-4b0c-9576-aa5468b10cee (4TU General Terms of Use)
  * A ServiceNow instance, "Incident management process enriched event log": Amaral, C., Fantinato, M.,
    Peres, S. (2018), UCI Machine Learning Repository, doi:10.24432/C57S4H (CC BY 4.0)

Only teams with at least 10 people are kept. Each person belongs to the team they did most of their work in
(the schema allows one team per engineer). An alert is an incident one of those people handled; it belongs to
the first of them who took it (the paged engineer), and each later hand-over to another of them is an
ESCALATED (to another team) or REASSIGNED (within the team) step of its paging history.

Dates: unless --keep-dates, each dataset moves by a whole number of weeks, in its teams' local time, so its
latest event lands in the past week. Weekday and local hour of every event stay as recorded, so fairness,
burnout and time-of-day figures are unchanged, and the app's "last 30 days" views have data.

The SQL only adds rows (ON CONFLICT DO NOTHING on every natural key), in one transaction, so it can run on a
database that already has data, and again without duplicating anything. It creates no logins.
"""

import argparse
import collections
import csv
import datetime as dt
import gzip
import hashlib
import io
import re
import sys
import urllib.request
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path
from zoneinfo import ZoneInfo

HERE = Path(__file__).resolve().parent
DATASETS = HERE / "datasets"
UTC = dt.timezone.utc
MIN_TEAM_SIZE = 10

VOLVO_URL = ("https://data.4tu.nl/file/0fc5c579-e544-4fab-9143-fab1f5192432/"
             "aa51ffbb-25fd-4b5a-b0b8-9aba659b7e8c")
VOLVO_MD5 = "d4809bd55e3e1c15b017ab4e58228297"
SN_URL = ("https://archive.ics.uci.edu/static/public/498/"
          "incident+management+process+enriched+event+log.zip")

# Team timezone from the country most of its people work in (Volvo records each person's country)
COUNTRY_ZONES = {
    "sweden": "Europe/Stockholm", "poland": "Europe/Warsaw", "india": "Asia/Kolkata",
    "brazil": "America/Sao_Paulo", "usa": "America/New_York", "belgium": "Europe/Brussels",
    "france": "Europe/Paris", "china": "Asia/Shanghai", "korea": "Asia/Seoul", "canada": "America/Toronto",
    "netherlands": "Europe/Amsterdam", "germany": "Europe/Berlin", "uk": "Europe/London",
    "united kingdom": "Europe/London", "japan": "Asia/Tokyo", "thailand": "Asia/Bangkok",
    "australia": "Australia/Sydney", "mexico": "America/Mexico_City", "peru": "America/Lima",
    "malaysia": "Asia/Kuala_Lumpur", "turkey": "Europe/Istanbul", "russian federation": "Europe/Moscow",
}
VOLVO_SEVERITY = {"Major": "P1", "High": "P2", "Medium": "P3", "Low": "P4"}
SN_SEVERITY = {"1": "P1", "2": "P2", "3": "P3", "4": "P4"}


# --- Model ---------------------------------------------------------------------------------------------------

class Step:
    """One hand-over in an alert's paging history."""

    def __init__(self, person, group, at):
        self.person, self.group, self.at = person, group, at


class Alert:
    def __init__(self, **fields):
        self.__dict__.update(fields)


class Dataset:
    """People, teams and alerts of one log, ready to write."""

    def __init__(self, key, label, domain):
        self.key, self.label, self.domain = key, label, domain
        self.teams = {}       # team name -> timezone
        self.people = {}      # person -> (name, email, team)
        self.services = {}    # service key -> (name, team)
        self.alerts = []


def download(url, target, md5=None):
    if target.exists():
        return
    target.parent.mkdir(parents=True, exist_ok=True)
    print(f"downloading {url}", file=sys.stderr)
    with urllib.request.urlopen(url, timeout=120) as response:
        data = response.read()
    if md5 and hashlib.md5(data).hexdigest() != md5:
        sys.exit(f"{target.name}: checksum mismatch, not using it")
    target.write_bytes(data)


def slug(text):
    return re.sub(r"[^a-z0-9]+", ".", text.lower()).strip(".") or "person"


def primary_teams(work):
    """Each person's team: where they did most of their work. Returns person -> group."""
    best = {}
    for (person, group), count in work.items():
        if person not in best or count > best[person][1]:
            best[person] = (group, count)
    return {person: group for person, (group, _) in best.items()}


def kept_people(primary):
    """People whose team has at least MIN_TEAM_SIZE of them."""
    size = collections.Counter(primary.values())
    return {person: group for person, group in primary.items() if size[group] >= MIN_TEAM_SIZE}


def history(steps):
    """Paging history from the hand-overs between kept people: the first is PAGED."""
    result, previous = [], None
    for step in steps:
        if previous is None:
            result.append(("PAGED", step))
        elif step.person != previous.person:
            result.append(("ESCALATED" if step.group != previous.group else "REASSIGNED", step))
        previous = step
    return result


def add_people(ds, members, team_of_group):
    used = set()
    for person, group in sorted(members.items()):
        email = slug(person)
        n = 1
        while email in used:
            n += 1
            email = f"{slug(person)}.{n}"
        used.add(email)
        ds.people[person] = (person, f"{email}@{ds.domain}", team_of_group[group])


def add_services(ds, alerts_by_service):
    """A service belongs to the team that handled most of its alerts."""
    for key, (name, teams) in alerts_by_service.items():
        ds.services[key] = (name, teams.most_common(1)[0][0])


# --- Volvo IT (BPI Challenge 2013) ------------------------------------------------------------------------

def read_volvo(path):
    """Incidents as lists of events: (time, activity, transition, group, person, country, impact, product)."""
    incidents = {}
    with gzip.open(path) as stream:
        for _, el in ET.iterparse(stream, events=("end",)):
            if el.tag.split("}")[-1] != "trace":
                continue
            case, events = None, []
            for child in el:
                tag = child.tag.split("}")[-1]
                if tag == "string" and child.get("key") == "concept:name":
                    case = child.get("value")
                elif tag == "event":
                    a = {x.get("key"): x.get("value") for x in child}
                    events.append((dt.datetime.fromisoformat(a["time:timestamp"]).astimezone(UTC),
                                   a.get("concept:name"), a.get("lifecycle:transition"), a.get("org:group"),
                                   a.get("org:resource"), a.get("resource country"), a.get("impact"),
                                   a.get("product")))
            events.sort(key=lambda e: e[0])
            incidents[case] = events
            el.clear()
    return incidents


def volvo(path):
    ds = Dataset("volvo", "Volvo IT (BPI Challenge 2013)", "volvo-it.example")
    incidents = read_volvo(path)
    unknown = (None, "", "UNKNOWN")

    work, country = collections.Counter(), {}
    for events in incidents.values():
        for _, _, _, group, person, place, _, _ in events:
            if group not in unknown and person not in unknown:
                work[(person, group)] += 1
                country[person] = (place or "").strip().lower()
    members = kept_people(primary_teams(work))

    team_of_group = {}
    for group in sorted(set(members.values())):
        places = collections.Counter(country[p] for p, g in members.items() if g == group)
        zone = next((COUNTRY_ZONES[c] for c, _ in places.most_common() if c in COUNTRY_ZONES), "UTC")
        team_of_group[group] = f"Volvo {group}"
        ds.teams[team_of_group[group]] = zone
    add_people(ds, members, team_of_group)

    by_service = {}
    for case, events in incidents.items():
        steps = [Step(e[4], e[3], e[0]) for e in events if e[4] in members]
        if not steps:
            continue
        person = steps[0].person
        triggered = events[0][0]
        accepted = next((e[0] for e in events if e[1] == "Accepted"), None)
        completed = next((e[0] for e in events if e[1] == "Completed"), None)
        impact = next((e[6] for e in events if e[6] in VOLVO_SEVERITY), "Medium")
        product = next((e[7] for e in events if e[7] not in unknown), None)
        if product:
            by_service.setdefault(product, (product, collections.Counter()))[1][ds.people[person][2]] += 1
        ds.alerts.append(Alert(
            key=f"VOLVO-{case}", number=int(re.sub(r"\D", "", case)[-9:] or 0) or None,
            title=f"Volvo IT incident {case}" + (f" ({product})" if product else ""),
            severity=VOLVO_SEVERITY[impact], urgency="high" if impact in ("Major", "High") else "low",
            status="resolved" if completed else "acknowledged" if accepted else "triggered",
            triggered=triggered, acknowledged=accepted, resolved=completed,
            person=person, service=product, history=history(steps)))
    add_services(ds, by_service)
    return ds


# --- ServiceNow (UCI incident management event log) -------------------------------------------------------

def sn_time(text):
    if not text or text == "?":
        return None
    # Recorded without a zone: kept as recorded, in teams whose timezone is UTC
    return dt.datetime.strptime(text, "%d/%m/%Y %H:%M").replace(tzinfo=UTC)


def servicenow(path):
    ds = Dataset("servicenow", "ServiceNow (UCI incident event log)", "servicenow-log.example")
    with zipfile.ZipFile(path) as archive, archive.open("incident_event_log.csv") as raw:
        rows = list(csv.DictReader(io.TextIOWrapper(raw, encoding="utf-8")))
    incidents = collections.defaultdict(list)
    for row in rows:
        incidents[row["number"]].append(row)

    work = collections.Counter()
    for row in rows:
        if "?" not in (row["assignment_group"], row["assigned_to"]):
            work[(row["assigned_to"], row["assignment_group"])] += 1
    members = kept_people(primary_teams(work))
    team_of_group = {g: f"ServiceNow {g}" for g in sorted(set(members.values()))}
    for team in team_of_group.values():
        ds.teams[team] = "UTC"
    add_people(ds, members, team_of_group)

    by_service = {}
    for number, events in incidents.items():
        events.sort(key=lambda r: int(r["sys_mod_count"]))
        steps = [Step(r["assigned_to"], r["assignment_group"], sn_time(r["sys_updated_at"]))
                 for r in events if r["assigned_to"] in members and sn_time(r["sys_updated_at"])]
        if not steps:
            continue
        first, last = events[0], events[-1]
        triggered = sn_time(first["opened_at"])
        if triggered is None:
            continue
        active = next((sn_time(r["sys_updated_at"]) for r in events if r["incident_state"] == "Active"), None)
        done = last["incident_state"] in ("Resolved", "Closed")
        resolved = (sn_time(last["resolved_at"]) or sn_time(last["closed_at"])
                    or sn_time(last["sys_updated_at"])) if done else None
        priority = last["priority"][:1]
        category = last["category"] if last["category"] != "?" else None
        person = steps[0].person
        if category:
            by_service.setdefault(category, (category, collections.Counter()))[1][ds.people[person][2]] += 1
        subcategory = last["subcategory"] if last["subcategory"] != "?" else None
        ds.alerts.append(Alert(
            key=f"SN-{number}", number=int(re.sub(r"\D", "", number) or 0) or None,
            title=f"{number}: " + ", ".join(x for x in (category, subcategory) if x) if category else number,
            severity=SN_SEVERITY.get(priority, "P3"), urgency="high" if last["urgency"][:1] == "1" else "low",
            status="resolved" if done else "acknowledged" if active else "triggered",
            triggered=triggered, acknowledged=active, resolved=resolved,
            person=person, service=category, history=history(steps)))
    add_services(ds, by_service)
    return ds


# --- Dates --------------------------------------------------------------------------------------------------

def shift_to_recent(ds, now):
    """Moves every time by whole weeks in its team's local time so the latest lands in the past week."""
    times = [t for a in ds.alerts for t in (a.triggered, a.acknowledged, a.resolved) if t]
    weeks = (now - max(times)).days // 7
    delta = dt.timedelta(weeks=weeks)

    def move(t, zone):
        if t is None:
            return None
        local = t.astimezone(zone).replace(tzinfo=None) + delta
        return local.replace(tzinfo=zone).astimezone(UTC)

    for a in ds.alerts:
        zone = ZoneInfo(ds.teams[ds.people[a.person][2]])
        a.triggered, a.acknowledged, a.resolved = (move(a.triggered, zone), move(a.acknowledged, zone),
                                                   move(a.resolved, zone))
        a.history = [(kind, Step(s.person, s.group, move(s.at, zone))) for kind, s in a.history]
    return weeks


def tidy(a):
    """Recorded data is not always ordered (and clock changes can swap an hour): keep the schema's rules."""
    if a.acknowledged and a.acknowledged < a.triggered:
        a.acknowledged = a.triggered
    if a.resolved and a.resolved < a.triggered:
        a.resolved = a.triggered
    a.history = [(k, Step(s.person, s.group, max(s.at, a.triggered))) for k, s in a.history]


# --- SQL ----------------------------------------------------------------------------------------------------

def q(value):
    if value is None:
        return "NULL"
    if isinstance(value, dt.datetime):
        return "'" + value.astimezone(UTC).strftime("%Y-%m-%d %H:%M:%S") + "'"
    if isinstance(value, int):
        return str(value)
    return "'" + str(value).replace("'", "''") + "'"


def chunks(items, size=1000):
    for i in range(0, len(items), size):
        yield items[i:i + size]


def write_sql(out, datasets, shifts):
    w = out.write
    w("-- Real incident data for the fairness tracker, generated by demo/import_real_data.py. Do not edit.\n")
    w("-- Sources (cite them when you show this data):\n")
    w("--   Volvo IT, BPI Challenge 2013 incidents: Ward Steeman (2013), 4TU.ResearchData,\n")
    w("--     doi:10.4121/uuid:500573e6-accc-4b0c-9576-aa5468b10cee (4TU General Terms of Use)\n")
    w("--   ServiceNow incident management event log: Amaral, Fantinato, Peres (2018), UCI Machine Learning\n")
    w("--     Repository, doi:10.24432/C57S4H (CC BY 4.0)\n")
    for ds in datasets:
        moved = f"moved {shifts[ds.key]} weeks later" if shifts.get(ds.key) else "dates as recorded"
        w(f"-- {ds.label}: {len(ds.teams)} teams, {len(ds.people)} engineers, {len(ds.alerts)} alerts; {moved}\n")
    w("-- Only adds rows (ON CONFLICT DO NOTHING), in one transaction: safe to run on a database with data,\n")
    w("-- and to run again. Times are UTC, like every event time in the schema.\n\n")
    w("\\set ON_ERROR_STOP on\nBEGIN;\n\n")

    teams = [(name, zone) for ds in datasets for name, zone in sorted(ds.teams.items())]
    w("INSERT INTO team (name, timezone) VALUES\n")
    w(",\n".join(f"  ({q(n)}, {q(z)})" for n, z in teams) + "\nON CONFLICT (name) DO NOTHING;\n\n")

    services = [(f"{ds.key.upper()}-{k}", f"{ds.label.split(' (')[0]} {name}", team)
                for ds in datasets for k, (name, team) in sorted(ds.services.items())]
    for part in chunks(services):
        w("INSERT INTO service (pagerduty_service_id, name, team_id)\nSELECT v.id, v.name, t.id FROM (VALUES\n")
        w(",\n".join(f"  ({q(i)}, {q(n)}, {q(t)})" for i, n, t in part))
        w("\n) v(id, name, team) JOIN team t ON t.name = v.team\nON CONFLICT (pagerduty_service_id) DO NOTHING;\n\n")

    people = [p for ds in datasets for p in sorted(ds.people.values(), key=lambda x: x[1])]
    for part in chunks(people):
        w("INSERT INTO engineer_data (email, name, team_id, active)\nSELECT v.email, v.name, t.id, true FROM (VALUES\n")
        w(",\n".join(f"  ({q(e)}, {q(n)}, {q(t)})" for n, e, t in part))
        w("\n) v(email, name, team) JOIN team t ON t.name = v.team\nON CONFLICT ((lower(email))) DO NOTHING;\n\n")

    alerts = [(ds, a) for ds in datasets for a in ds.alerts]
    for part in chunks(alerts):
        w("INSERT INTO alert_event (pager_duty_incident_id, incident_number, title, severity, status, source, urgency,\n"
          "                         triggered_at, acknowledged_at, resolved_at, engineer_id, assigned_engineer_name,\n"
          "                         service_id)\n"
          "SELECT v.id, v.number, v.title, v.severity, v.status, 'API', v.urgency, v.t::timestamp, v.ack::timestamp,\n"
          "       v.res::timestamp, e.id, e.name, s.id FROM (VALUES\n")
        rows = []
        for ds, a in part:
            service = f"{ds.key.upper()}-{a.service}" if a.service else None
            rows.append(f"  ({q(a.key)}, {q(a.number)}, {q(a.title[:255])}, {q(a.severity)}, {q(a.status)}, "
                        f"{q(a.urgency)}, {q(a.triggered)}, {q(a.acknowledged)}, {q(a.resolved)}, "
                        f"{q(ds.people[a.person][1])}, {q(service)})")
        w(",\n".join(rows))
        w("\n) v(id, number, title, severity, status, urgency, t, ack, res, email, service)\n"
          "JOIN engineer_data e ON lower(e.email) = v.email\n"
          "LEFT JOIN service s ON s.pagerduty_service_id = v.service\n"
          "ON CONFLICT (pager_duty_incident_id) DO NOTHING;\n\n")

    steps = [(a.key, ds.people[s.person][1], kind, s.at) for ds, a in alerts for kind, s in a.history]
    for part in chunks(steps, 2000):
        w("INSERT INTO alert_assignment (alert_id, engineer_id, kind, assigned_at, source)\n"
          "SELECT a.id, e.id, v.kind, v.at::timestamp, 'BACKFILL' FROM (VALUES\n")
        w(",\n".join(f"  ({q(k)}, {q(e)}, {q(kind)}, {q(at)})" for k, e, kind, at in part))
        w("\n) v(alert, email, kind, at)\nJOIN alert_event a ON a.pager_duty_incident_id = v.alert\n"
          "JOIN engineer_data e ON lower(e.email) = v.email\nON CONFLICT DO NOTHING;\n\n")
    w("COMMIT;\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--keep-dates", action="store_true", help="keep the dates as recorded")
    parser.add_argument("--out", type=Path, default=HERE / "real-data.sql")
    args = parser.parse_args()

    volvo_file = DATASETS / "bpi2013" / "BPI_Challenge_2013_incidents.xes.gz"
    sn_file = DATASETS / "uci" / "incident_event_log.zip"
    download(VOLVO_URL, volvo_file, VOLVO_MD5)
    download(SN_URL, sn_file)

    datasets = [volvo(volvo_file), servicenow(sn_file)]
    now = dt.datetime.now(UTC)
    shifts = {} if args.keep_dates else {ds.key: shift_to_recent(ds, now) for ds in datasets}
    for ds in datasets:
        for a in ds.alerts:
            tidy(a)
        sizes = collections.Counter(team for _, _, team in ds.people.values())
        span = (min(a.triggered for a in ds.alerts).date(), max(a.triggered for a in ds.alerts).date())
        print(f"{ds.label}: {len(ds.teams)} teams (people per team {min(sizes.values())}-{max(sizes.values())}), "
              f"{len(ds.people)} engineers, {len(ds.alerts)} alerts, "
              f"{sum(len(a.history) for a in ds.alerts)} paging steps, triggered {span[0]} to {span[1]}",
              file=sys.stderr)
    with open(args.out, "w", encoding="utf-8") as out:
        write_sql(out, datasets, shifts)
    print(f"wrote {args.out}", file=sys.stderr)


if __name__ == "__main__":
    main()
