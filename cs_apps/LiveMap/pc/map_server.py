#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
LiveMap · 手机实时位置 → 电脑地图（独立测试项目，零第三方依赖）
================================================================
手机端（Kotlin 前台服务）周期性 POST 一个位置点，本服务把它存进内存环形队列，
浏览器打开地图页即可实时看到手机位置与轨迹。

  - 手机推点 : POST http://<PC IP>:5678/push   body: {"lat":..,"lon":..,"spd":..,"bat":..,"ts":..}
  - 轨迹数据 : GET  http://<PC IP>:5678/track  → {"points":[...]}（浏览器每秒轮询）
  - 地图页面 : GET  http://<PC IP>:5678/       → Leaflet + OpenStreetMap，双击标记看详情

运行：  python map_server.py [端口，默认 5678]
测试：  python map_server.py 之后浏览器打开 http://127.0.0.1:5678/
        另开终端模拟推点（见 README.md 里的 curl 示例）。
"""
import json
import math
import threading
import time
from collections import deque
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse

PORT = 5678
MAX_POINTS = 5000          # 内存里最多保留多少个轨迹点
DEFAULT_TTL = 0            # 0=永久保留；>0 秒则只保留最近 N 秒（暂不启用）

_lock = threading.Lock()
_points = deque(maxlen=MAX_POINTS)   # 每项: dict(lat,lon,spd,bat,ts,acc)


def _push_point(d):
    p = {
        "lat": float(d.get("lat", 0.0)),
        "lon": float(d.get("lon", 0.0)),
        "spd": float(d.get("spd", 0.0)),      # m/s
        "bat": float(d.get("bat", -1.0)),     # 0~100，-1=未知
        "acc": float(d.get("acc", 0.0)),      # 精度（米）
        "ts": float(d.get("ts") or time.time()),
    }
    with _lock:
        _points.append(p)
    print(f"[LiveMap] +点 ({p['lat']:.6f},{p['lon']:.6f}) "
          f"{p['spd']*3.6:.1f}km/h 电量{p['bat']:.0f}% 共{len(_points)}点")
    return p


def _snapshot(since_ts=0.0):
    with _lock:
        pts = [p for p in _points if p["ts"] >= since_ts]
    return {"count": len(_points), "points": pts}


PAGE = """<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>PhoneHub · LiveMap</title>
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css">
<style>
 html,body,#map{height:100%;margin:0;background:#0d0d0d}
 .bar{position:fixed;z-index:1000;top:0;left:0;right:0;background:#161616ee;color:#d0d0d0;
      font:13px/1.6 "Segoe UI",sans-serif;padding:6px 12px;border-bottom:1px solid #2a2a2a}
 .bar b{color:#00e676}
</style></head>
<body>
<div class="bar" id="bar">LiveMap · 等待手机位置…（每秒自动刷新）</div>
<div id="map"></div>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<script>
const map = L.map('map').setView([30.0, 120.0], 13);
L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',
            {maxZoom: 19, attribution: '&copy; OpenStreetMap'}).addTo(map);
const line = L.polyline([], {color:'#00e676', weight:4, opacity:.85}).addTo(map);
let marker = null, lastCount = -1;

function fmt(t){ const d=new Date(t*1000); return d.toLocaleTimeString(); }

async function tick(){
  try{
    const r = await fetch('/track');           // 全量（点数有上限，量级很小）
    const data = await r.json();
    if (data.points.length === lastCount) return;
    lastCount = data.points.length;
    const latlngs = data.points.map(p=>[p.lat,p.lon]);
    line.setLatLngs(latlngs);
    const last = data.points[data.points.length-1];
    if (last){
      const kmh = (last.spd*3.6).toFixed(1);
      const bat = last.bat>=0 ? (' 电量 '+last.bat.toFixed(0)+'%') : '';
      const html = `<b>${fmt(last.ts)}</b><br>速度 ${kmh} km/h${bat}<br>精度 ±${last.acc.toFixed(0)} m`;
      if (!marker){
        marker = L.marker([last.lat,last.lon]).addTo(map).bindPopup(html).openPopup();
        map.setView([last.lat,last.lon], 16);
      } else {
        marker.setLatLng([last.lat,last.lon]).setPopupContent(html);
        map.panTo([last.lat,last.lon]);
      }
      document.getElementById('bar').innerHTML =
        `LiveMap · 最新 <b>${fmt(last.ts)}</b> ｜ ${last.lat.toFixed(6)}, ${last.lon.toFixed(6)}` +
        ` ｜ ${kmh} km/h${bat} ｜ 轨迹 ${data.count} 点`;
    }
  }catch(e){ /* 服务没开等，下一秒再试 */ }
}
setInterval(tick, 1000); tick();
</script></body></html>"""


class Handler(BaseHTTPRequestHandler):
    def _send(self, code, body, ctype):
        data = body if isinstance(body, bytes) else body.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        path = urlparse(self.path).path
        if path in ("/", "/index.html"):
            self._send(200, PAGE, "text/html; charset=utf-8")
        elif path == "/track":
            since = 0.0
            try:
                from urllib.parse import parse_qs, urlparse as up
                q = parse_qs(up(self.path).query)
                since = float(q.get("since", ["0"])[0])
            except Exception:
                pass
            self._send(200, json.dumps(_snapshot(since), ensure_ascii=False),
                       "application/json; charset=utf-8")
        else:
            self._send(404, "not found", "text/plain")

    def do_POST(self):
        if urlparse(self.path).path != "/push":
            self._send(404, "not found", "text/plain")
            return
        try:
            n = int(self.headers.get("Content-Length", 0))
            d = json.loads(self.rfile.read(n) or b"{}")
            p = _push_point(d)
            self._send(200, json.dumps({"ok": True, "count": len(_points), "echo": p}),
                       "application/json; charset=utf-8")
        except Exception as e:
            self._send(400, json.dumps({"ok": False, "error": str(e)}),
                       "application/json; charset=utf-8")

    def log_message(self, *a):   # 静默访问日志
        pass


if __name__ == "__main__":
    import sys
    port = int(sys.argv[1]) if len(sys.argv) > 1 else PORT
    print("=" * 52)
    print(f"LiveMap 已启动  http://127.0.0.1:{port}/")
    print(f"手机推点接口    POST http://<本机IP>:{port}/push")
    print("=" * 52)
    ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()
