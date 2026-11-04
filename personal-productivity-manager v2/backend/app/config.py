from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """All secrets come from the environment / .env on the SERVER. Nothing here ships in the APK."""

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    database_url: str = "sqlite:///./dev.db"
    jwt_secret: str = ""
    jwt_days: int = 30
    google_web_client_id: str = ""

    whatsapp_phone_number_id: str = ""
    whatsapp_access_token: str = ""
    whatsapp_app_secret: str = ""
    whatsapp_verify_token: str = ""
    whatsapp_template_morning: str = "morning_tasks"
    whatsapp_template_followup: str = "task_followup"
    whatsapp_template_evening: str = "daily_report"
    whatsapp_template_lang: str = "en"
    whatsapp_graph_version: str = "v20.0"

    enable_scheduler: bool = False


settings = Settings()
