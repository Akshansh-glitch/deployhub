from app import app


def test_hello():
    client = app.test_client()
    body = client.get("/").get_json()
    assert body["version"] == "v1"
    assert "Hello" in body["message"]


def test_health():
    assert app.test_client().get("/healthz").status_code == 200
