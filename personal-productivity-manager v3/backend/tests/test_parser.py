from datetime import datetime
from zoneinfo import ZoneInfo

from app.tasks_service import next_due
from app.whatsapp.parser import parse, parse_when

TZ = ZoneInfo("Asia/Dhaka")
NOW = datetime(2026, 11, 4, 10, 0, tzinfo=TZ)  # Wednesday


def at(ms):
    return datetime.fromtimestamp(ms / 1000, TZ)


def test_tomorrow_with_time():
    ms, rest = parse_when("call Sam tomorrow at 5pm", NOW)
    d = at(ms)
    assert (d.day, d.hour, d.minute) == (5, 17, 0) and rest == "call Sam"


def test_tomorrow_defaults_to_nine():
    d = at(parse_when("tomorrow", NOW)[0])
    assert (d.day, d.hour) == (5, 9)


def test_weekday_and_24h_time():
    d = at(parse_when("monday 17:30", NOW)[0])
    assert (d.weekday(), d.hour, d.minute) == (0, 17, 30) and d.day == 9


def test_in_hours():
    assert at(parse_when("in 2 hours", NOW)[0]).hour == 12


def test_bare_past_time_rolls_to_tomorrow():
    d = at(parse_when("at 9am", NOW)[0])
    assert d.day == 5 and d.hour == 9


def test_12am_12pm():
    assert at(parse_when("today 12am", NOW)[0]).hour == 0
    assert at(parse_when("today 12pm", NOW)[0]).hour == 12


def test_no_time_expression():
    assert parse_when("buy milk", NOW) == (None, "buy milk")


def test_intents():
    assert parse("done 1,3", NOW).numbers == [1, 3]
    assert parse("Done 1 and 2", NOW).numbers == [1, 2]
    i = parse("finished write report", NOW)
    assert (i.kind, i.text) == ("DONE", "write report")
    assert parse("cancel 2", NOW).kind == "CANCEL"
    p = parse("postpone 2 to tomorrow 5pm", NOW)
    assert p.kind == "POSTPONE" and p.numbers == [2] and p.when_ms
    a = parse("add Buy milk tomorrow 6pm", NOW)
    assert a.kind == "ADD" and a.text == "Buy milk" and a.when_ms
    assert parse("yes", NOW).kind == "YES" and parse("No", NOW).kind == "NO"
    assert parse("list", NOW).kind == "LIST" and parse("help", NOW).kind == "HELP"
    assert parse("blah blah", NOW).kind == "UNKNOWN"
    assert parse("done", NOW).kind == "UNKNOWN"


def test_next_due_rules():
    jan31 = int(datetime(2026, 1, 31, 9, 0, tzinfo=TZ).timestamp() * 1000)
    assert at(next_due(jan31, "MONTHLY", TZ)).strftime("%m-%d %H") == "02-28 09"
    assert at(next_due(jan31, "DAILY", TZ)).day == 1
    assert at(next_due(jan31, "WEEKLY", TZ)).strftime("%m-%d") == "02-07"
    dec = int(datetime(2026, 12, 15, 9, 0, tzinfo=TZ).timestamp() * 1000)
    assert at(next_due(dec, "MONTHLY", TZ)).strftime("%Y-%m") == "2027-01"
