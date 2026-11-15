#!/usr/bin/env python3
"""May chu thu: gia lap GitHub Releases (co chuyen huong 302) va che do 'bi sua file'."""
import http.server, os, sys

ROOT = sys.argv[1]
PORT = int(sys.argv[2])
state = {"tamper": False}

class H(http.server.BaseHTTPRequestHandler):
    def log_message(self, *a): pass
    def do_GET(self):
        path = self.path.split("?")[0]
        if path == "/_tamper/on": state["tamper"] = True; return self.ok(b"on")
        if path == "/_tamper/off": state["tamper"] = False; return self.ok(b"off")
        if path.startswith("/dl/"):       # giong github.com/.../releases/download/...
            self.send_response(302)
            self.send_header("Location", "/store/" + path[4:] + "?sig=abc")
            self.send_header("Content-Length", "0"); self.end_headers(); return
        if path.startswith("/store/"):
            name = os.path.basename(path)
            full = os.path.join(ROOT, name)
            if not os.path.isfile(full):
                self.send_response(404); self.send_header("Content-Length", "0"); self.end_headers(); return
            data = open(full, "rb").read()
            if state["tamper"] and name != "manifest.txt":
                b = bytearray(data); b[len(b) // 2] ^= 0x01; data = bytes(b)   # sua 1 bit
            return self.ok(data)
        self.send_response(404); self.send_header("Content-Length", "0"); self.end_headers()
    def ok(self, data):
        self.send_response(200); self.send_header("Content-Length", str(len(data))); self.end_headers(); self.wfile.write(data)

http.server.ThreadingHTTPServer(("127.0.0.1", PORT), H).serve_forever()
