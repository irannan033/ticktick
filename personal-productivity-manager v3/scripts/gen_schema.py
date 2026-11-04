"""Regenerates database/schema.sql from the SQLAlchemy models. Run from the repo root: python scripts/gen_schema.py"""
import sys
sys.path.insert(0, "backend")
from sqlalchemy.dialects import postgresql
from sqlalchemy.schema import CreateIndex, CreateTable

import app.models  # noqa: F401
from app.db import Base

out = ["-- GENERATED from backend/app/models.py (PostgreSQL dialect) by scripts/gen_schema.py. Do not edit by hand.",
       "-- Times are epoch milliseconds (UTC). Primary keys are client-generated UUIDs.\n"]
for t in Base.metadata.sorted_tables:
    out.append(str(CreateTable(t).compile(dialect=postgresql.dialect())).strip() + ";\n")
    out += [str(CreateIndex(ix).compile(dialect=postgresql.dialect())).strip() + ";" for ix in sorted(t.indexes, key=lambda i: i.name)]
    out.append("")
open("database/schema.sql", "w").write("\n".join(out))
