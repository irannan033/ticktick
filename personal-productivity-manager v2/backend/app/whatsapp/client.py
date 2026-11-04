import httpx

from ..config import settings


class WhatsAppError(Exception):
    pass


class WhatsAppClient:
    """Thin wrapper over the official WhatsApp Cloud API. Free-form text only works inside the 24h window
    after the user's last message; business-initiated messages must be approved templates."""

    def _post(self, payload: dict) -> str:
        if not (settings.whatsapp_phone_number_id and settings.whatsapp_access_token):
            raise WhatsAppError("WhatsApp is not configured")
        url = f"https://graph.facebook.com/{settings.whatsapp_graph_version}/{settings.whatsapp_phone_number_id}/messages"
        try:
            r = httpx.post(url, json={"messaging_product": "whatsapp", **payload},
                           headers={"Authorization": f"Bearer {settings.whatsapp_access_token}"}, timeout=15)
        except httpx.HTTPError as e:
            raise WhatsAppError(str(e))
        if r.status_code >= 300:
            raise WhatsAppError(f"{r.status_code}: {r.text[:300]}")
        return r.json()["messages"][0]["id"]

    def send_text(self, to: str, body: str) -> str:
        return self._post({"to": to, "type": "text", "text": {"body": body[:4000]}})

    def send_template(self, to: str, name: str, params: list[str]) -> str:
        return self._post({
            "to": to, "type": "template",
            "template": {
                "name": name, "language": {"code": settings.whatsapp_template_lang},
                "components": [{"type": "body", "parameters": [{"type": "text", "text": p} for p in params]}],
            },
        })


def get_client() -> WhatsAppClient:
    return WhatsAppClient()
