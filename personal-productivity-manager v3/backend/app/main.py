import asyncio
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles

from .config import settings
from .db import Base, engine
from .routes_core import router as core
from .whatsapp.routes import router as wa_router
from .whatsapp.scheduler import scheduler_loop


@asynccontextmanager
async def lifespan(app: FastAPI):
    Base.metadata.create_all(engine)  # fine for a first deployment; adopt Alembic before changing columns
    task = asyncio.create_task(scheduler_loop()) if settings.enable_scheduler else None
    yield
    if task:
        task.cancel()


app = FastAPI(title="Momentum API", version="0.3.0", lifespan=lifespan)
app.include_router(core)
app.include_router(wa_router)

_web = Path(__file__).resolve().parent.parent / "web"
if _web.exists():
    app.mount("/app", StaticFiles(directory=_web, html=True), name="web")
