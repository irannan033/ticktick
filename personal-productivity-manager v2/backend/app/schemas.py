from typing import Literal

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel

Priority = Literal["HIGH", "MEDIUM", "LOW", "NONE"]
Status = Literal["UPCOMING", "IN_PROGRESS", "COMPLETED", "CANCELLED"]
Recurrence = Literal["DAILY", "WEEKLY", "MONTHLY"]
SessionStatus = Literal["COMPLETED", "ABANDONED"]


class CamelModel(BaseModel):
    """JSON uses camelCase (same names as the Android Room entities)."""
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, from_attributes=True)


class TaskDTO(CamelModel):
    id: str = Field(min_length=1, max_length=36)
    title: str = Field(min_length=1, max_length=500)
    description: str = Field(default="", max_length=20000)
    priority: Priority = "NONE"
    important: bool = False
    urgent: bool = False
    category_id: str | None = None
    start_at: int | None = None
    due_at: int | None = None
    reminder_at: int | None = None
    estimated_minutes: int | None = None
    status: Status = "UPCOMING"
    completed_at: int | None = None
    postpone_count: int = 0
    recurrence: Recurrence | None = None
    created_at: int = 0
    updated_at: int = 0
    deleted_at: int | None = None


class CategoryDTO(CamelModel):
    id: str = Field(min_length=1, max_length=36)
    name: str = Field(min_length=1, max_length=200)
    color_argb: int = 0
    updated_at: int = 0
    deleted_at: int | None = None


class SessionDTO(CamelModel):
    id: str = Field(min_length=1, max_length=36)
    task_id: str | None = None
    planned_minutes: int
    completed_seconds: int
    started_at: int
    ended_at: int | None = None
    status: SessionStatus


class SyncRequest(CamelModel):
    since: int = 0
    tasks: list[TaskDTO] = Field(default_factory=list, max_length=2000)
    categories: list[CategoryDTO] = Field(default_factory=list, max_length=500)
    sessions: list[SessionDTO] = Field(default_factory=list, max_length=5000)


class SyncResponse(CamelModel):
    server_time: int
    tasks: list[TaskDTO]
    categories: list[CategoryDTO]
    sessions: list[SessionDTO]


class GoogleLogin(CamelModel):
    id_token: str


class UserDTO(CamelModel):
    id: str
    name: str
    email: str | None = None
    timezone: str = "UTC"
    whatsapp_number: str | None = None
    whatsapp_enabled: bool = False
    morning_time: str = "08:00"
    evening_time: str = "23:00"
    followup_hours: int = 2


class AuthResponse(CamelModel):
    token: str
    user: UserDTO


class SettingsUpdate(CamelModel):
    name: str | None = Field(default=None, max_length=200)
    timezone: str | None = None
    whatsapp_number: str | None = Field(default=None, max_length=20)  # '' clears it
    whatsapp_enabled: bool | None = None
    morning_time: str | None = Field(default=None, pattern=r"^([01]\d|2[0-3]):[0-5]\d$")
    evening_time: str | None = Field(default=None, pattern=r"^([01]\d|2[0-3]):[0-5]\d$")
    followup_hours: int | None = Field(default=None, ge=1, le=12)
