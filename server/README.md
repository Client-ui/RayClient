# Ray presence server

The Nametag module shows the Ray logo in front of players who use the client. Minecraft servers do not tell other
players what client you use, so clients check in with a small shared service. This folder has one you can run.

1. Run it: `python3 ray_presence.py --port 8787` (Python 3.8+, no packages needed).
2. Put it behind HTTPS (for example a reverse proxy such as Caddy or nginx, with `--behind-proxy`).
3. In each client's `config/ray.json` set `"nametagServer": "https://your.server.example"`.
   (For a quick test you can instead start Minecraft with `-Dray.presence=http://localhost:8787`.)

While `nametagServer` is empty the client never contacts anything, and only your own nametag shows the logo.
Only a public player id is sent, to the server you set. Read the notes at the top of `ray_presence.py` about limits
and about what is and is not verified.
