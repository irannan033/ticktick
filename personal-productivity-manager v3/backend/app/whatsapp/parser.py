"""Rule-based command parser for WhatsApp replies. Deterministic and offline: it never guesses.
If something is ambiguous the service asks the user instead of acting."""
import re
from dataclasses import dataclass, field
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

WEEKDAYS = ["monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"]


@dataclass
class Intent:
    kind: str  # HELP LIST DONE CANCEL POSTPONE ADD YES NO UNKNOWN
    numbers: list[int] = field(default_factory=list)
    text: str = ""  # task title fragment (DONE/CANCEL/POSTPONE) or new title (ADD)
    when_ms: int | None = None
    when_text: str = ""


def _ms(dt: datetime) -> int:
    return int(dt.timestamp() * 1000)


_TIME = re.compile(r"\b(?:at\s+)?(\d{1,2})(?::(\d{2}))?\s*(am|pm)\b|\bat\s+(\d{1,2})(?::(\d{2}))?\b|\b([01]?\d|2[0-3]):([0-5]\d)\b", re.I)
_IN = re.compile(r"\bin\s+(\d+)\s*(minute|minutes|min|mins|hour|hours|hr|hrs|day|days)\b", re.I)
_DAY = re.compile(r"\b(today|tonight|tomorrow|" + "|".join(WEEKDAYS) + r")\b", re.I)


def parse_when(text: str, now: datetime) -> tuple[int | None, str]:
    """Finds ONE time expression, returns (epoch_ms | None, text with the expression removed).
    'tomorrow' / weekday without a time defaults to 09:00. Time already passed today rolls to tomorrow only for bare times."""
    s = text
    m_in = _IN.search(s)
    if m_in:
        n, unit = int(m_in.group(1)), m_in.group(2).lower()
        delta = timedelta(minutes=n) if unit.startswith("m") else timedelta(hours=n) if unit.startswith("h") else timedelta(days=n)
        return _ms(now + delta), (s[:m_in.start()] + s[m_in.end():]).strip(" ,.-")

    day = None
    m_day = _DAY.search(s)
    if m_day:
        word = m_day.group(1).lower()
        base = now.replace(second=0, microsecond=0)
        if word == "today":
            day = base
        elif word == "tonight":
            day = base.replace(hour=20, minute=0)
        elif word == "tomorrow":
            day = base + timedelta(days=1)
        else:
            ahead = (WEEKDAYS.index(word) - now.weekday()) % 7 or 7
            day = base + timedelta(days=ahead)
        s = s[:m_day.start()] + s[m_day.end():]

    hh = mm = None
    m_t = _TIME.search(s)
    if m_t:
        if m_t.group(1):
            hh, mm, ap = int(m_t.group(1)), int(m_t.group(2) or 0), m_t.group(3).lower()
            if hh == 12:
                hh = 0
            hh += 12 if ap == "pm" else 0
        elif m_t.group(4):
            hh, mm = int(m_t.group(4)), int(m_t.group(5) or 0)
        else:
            hh, mm = int(m_t.group(6)), int(m_t.group(7))
        if hh > 23 or mm > 59:
            return None, text
        s = s[:m_t.start()] + s[m_t.end():]

    if day is None and hh is None:
        return None, text
    if day is None:
        day = now
        target = day.replace(hour=hh, minute=mm, second=0, microsecond=0)
        if target <= now:
            target += timedelta(days=1)
        return _ms(target), re.sub(r"\s+", " ", s).strip(" ,.-")
    if hh is None:
        if day.hour == 20 and m_day and m_day.group(1).lower() == "tonight":
            hh, mm = 20, 0
        else:
            hh, mm = 9, 0
    target = day.replace(hour=hh, minute=mm, second=0, microsecond=0)
    return _ms(target), re.sub(r"\s+", " ", s).strip(" ,.-")




def _numbers(rest: str) -> list[int] | None:
    cleaned = re.sub(r"\band\b", ",", rest, flags=re.I)
    if cleaned.strip() and re.fullmatch(r"[\d\s,&]+", cleaned):
        return [int(x) for x in re.findall(r"\d+", cleaned)]
    return None


_VERBS = {
    "DONE": ("done", "finished", "finish", "completed", "complete"),
    "CANCEL": ("cancel", "skip", "drop", "delete"),
    "POSTPONE": ("postpone", "reschedule", "snooze", "move", "delay"),
    "ADD": ("add", "new", "todo", "remind me to", "remind me", "create"),
}


def parse(text: str, now: datetime) -> Intent:
    t = (text or "").strip()
    low = t.lower().strip(" .!")
    if low in ("yes", "y", "yep", "confirm", "ok", "okay"):
        return Intent("YES")
    if low in ("no", "n", "nope", "stop", "abort"):
        return Intent("NO")
    if low in ("help", "?", "menu", "hi", "hello"):
        return Intent("HELP")
    if low in ("list", "today", "tasks", "pending", "show", "my tasks", "what's pending", "whats pending"):
        return Intent("LIST")

    for kind, verbs in _VERBS.items():
        for v in sorted(verbs, key=len, reverse=True):
            if low == v or low.startswith(v + " "):
                rest = t[len(v):].strip()
                if kind == "ADD":
                    when, title = parse_when(rest, now)
                    return Intent("ADD", text=title if when else rest, when_ms=when, when_text=rest) if (title if when else rest) else Intent("UNKNOWN")
                if kind == "POSTPONE":
                    m = re.search(r"\b(?:to|until|till|for|by)\b", rest, re.I)
                    target_part, when_part = (rest[:m.start()], rest[m.end():]) if m else (rest, "")
                    when, _ = parse_when(when_part or rest, now)
                    nums = _numbers(target_part.strip())
                    return Intent("POSTPONE", numbers=nums or [], text="" if nums else target_part.strip(), when_ms=when, when_text=when_part)
                nums = _numbers(rest)
                if nums:
                    return Intent(kind, numbers=nums)
                if rest:
                    return Intent(kind, text=rest)
                return Intent("UNKNOWN")
    return Intent("UNKNOWN", text=t)
