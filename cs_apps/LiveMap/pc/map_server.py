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


# 地图页：**完全离线自绘**（不依赖任何外部 CSS/JS/瓦片）。
# 原因：本机无法访问 unpkg（只返回 497 字节假响应）与 tile.openstreetmap.org（http=000 连不上），
# 用 Leaflet + OSM 瓦片的话页面就是一片全黑。这里改用 Canvas + Web 墨卡托投影自己画：
# 经纬网格 + 轨迹 polyline + 当前点 + 比例尺，支持拖拽平移、滚轮缩放、双击回到跟随。
PAGE = """<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>PhoneHub · LiveMap（离线自绘）</title>
<style>
 html,body{height:100%;margin:0;background:#0d0d0d;overflow:hidden}
 #bar{position:fixed;z-index:10;top:0;left:0;right:0;background:#161616f2;color:#d0d0d0;
      font:13px/1.7 "Segoe UI",system-ui,sans-serif;padding:7px 14px;
      border-bottom:1px solid #2a2a2a;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
 #bar b{color:#00e676;font-weight:600}
 #bar .dim{color:#777}
 #cv{display:block;width:100vw;height:100vh;cursor:grab}
 #cv.drag{cursor:grabbing}
 #hud{position:fixed;z-index:10;left:14px;bottom:12px;color:#8a8a8a;
      font:12px/1.6 Consolas,monospace;background:#161616cc;padding:6px 10px;border-radius:5px}
 #hud b{color:#d0d0d0;font-weight:500}
 #empty{position:fixed;z-index:9;inset:0;display:flex;align-items:center;justify-content:center;
        color:#555;font:14px/2 "Segoe UI",sans-serif;text-align:center;pointer-events:none}
</style></head>
<body>
<div id="bar">LiveMap · 等待手机位置… <span class="dim">（离线自绘模式，每秒刷新）</span></div>
<div id="empty">还没有收到手机位置<br>请在手机 LiveMap 里填本机 IP 并点「开始共享位置」</div>
<canvas id="cv"></canvas>
<div id="hud"></div>
<script>
const cv = document.getElementById('cv'), ctx = cv.getContext('2d');
const bar = document.getElementById('bar'), hud = document.getElementById('hud');
const emptyBox = document.getElementById('empty');
const R = 6378137, CIRC = 2 * Math.PI * R;      // Web 墨卡托（WGS84）
const MIN_SPAN = 2e-5;                           // 最小视野（归一化世界坐标，约 800m）

let pts = [];                    // 轨迹点
let cx = 0.5, cy = 0.5, S = 1e6; // 视图中心（归一化世界坐标）与缩放（像素/世界单位）
let follow = true;               // 是否跟随最新点
let dpr = window.devicePixelRatio || 1;

function proj(lat, lon){
  const x = (lon + 180) / 360;
  const s = Math.sin(lat * Math.PI / 180);
  const y = 0.5 - Math.log((1 + s) / (1 - s)) / (4 * Math.PI);
  return [x, y];
}
function unproj(x, y){
  const lon = x * 360 - 180;
  const n = Math.PI - 2 * Math.PI * y;
  const lat = 180 / Math.PI * Math.atan(0.5 * (Math.exp(n) - Math.exp(-n)));
  return [lat, lon];
}
function resize(){
  dpr = window.devicePixelRatio || 1;
  cv.width = Math.floor(innerWidth * dpr);
  cv.height = Math.floor(innerHeight * dpr);
}
addEventListener('resize', resize); resize();

function W(){ return cv.width / dpr; }
function H(){ return cv.height / dpr; }
function toPx(x, y){ return [ (x - cx) * S + W()/2, (y - cy) * S + H()/2 ]; }

// 选一个"好看"的经纬网格间隔
const STEPS = [10,5,2,1,.5,.2,.1,.05,.02,.01,.005,.002,.001,.0005,.0002,.0001];
function pickStep(spanDeg){
  for (const s of STEPS) if (spanDeg / s >= 4) return s;
  return STEPS[STEPS.length - 1];
}

function fit(){
  if (!pts.length) return;
  let x0=1e9,y0=1e9,x1=-1e9,y1=-1e9;
  for (const p of pts){ const q = proj(p.lat, p.lon);
    if(q[0]<x0)x0=q[0]; if(q[1]<y0)y0=q[1]; if(q[0]>x1)x1=q[0]; if(q[1]>y1)y1=q[1]; }
  let spanX = Math.max(x1 - x0, MIN_SPAN), spanY = Math.max(y1 - y0, MIN_SPAN);
  cx = (x0 + x1) / 2; cy = (y0 + y1) / 2;
  S = Math.min(W() * 0.78 / spanX, H() * 0.78 / spanY);
}

function draw(){
  const w = W(), h = H();
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.fillStyle = '#0d0d0d'; ctx.fillRect(0, 0, w, h);
  if (!pts.length) return;

  // 可视范围对应的经纬度
  const [la0, lo0] = unproj(cx - (w/2)/S, cy + (h/2)/S);
  const [la1, lo1] = unproj(cx + (w/2)/S, cy - (h/2)/S);
  const step = pickStep(Math.min(lo1 - lo0, la1 - la0));

  // 经纬网格
  ctx.lineWidth = 1; ctx.font = '11px Consolas,monospace';
  for (let i = Math.floor(lo0/step)*step; i <= lo1; i += step){
    const [px] = toPx(proj(0, i)[0], 0);
    ctx.strokeStyle = '#1b1b1b'; ctx.beginPath(); ctx.moveTo(px, 0); ctx.lineTo(px, h); ctx.stroke();
    ctx.fillStyle = '#4a4a4a'; ctx.fillText(i.toFixed(step < 0.01 ? 4 : (step < 1 ? 2 : 0)) + '°', px + 4, h - 6);
  }
  for (let i = Math.floor(la0/step)*step; i <= la1; i += step){
    const py = toPx(0, proj(i, 0)[1])[1];
    ctx.strokeStyle = '#1b1b1b'; ctx.beginPath(); ctx.moveTo(0, py); ctx.lineTo(w, py); ctx.stroke();
    ctx.fillStyle = '#4a4a4a'; ctx.fillText(i.toFixed(step < 0.01 ? 4 : (step < 1 ? 2 : 0)) + '°', 6, py - 4);
  }

  // 轨迹
  ctx.strokeStyle = '#00e676'; ctx.lineWidth = 2.5; ctx.lineJoin = 'round'; ctx.lineCap = 'round';
  ctx.beginPath();
  pts.forEach((p, i) => { const q = proj(p.lat, p.lon), s = toPx(q[0], q[1]);
    i ? ctx.lineTo(s[0], s[1]) : ctx.moveTo(s[0], s[1]); });
  ctx.stroke();

  // 起点
  const a = toPx(...proj(pts[0].lat, pts[0].lon));
  ctx.fillStyle = '#4fc3f7'; ctx.beginPath(); ctx.arc(a[0], a[1], 4, 0, 7); ctx.fill();
  ctx.fillStyle = '#4fc3f7'; ctx.fillText('起点', a[0] + 7, a[1] + 4);

  // 当前点（脉冲圈）
  const last = pts[pts.length - 1];
  const b = toPx(...proj(last.lat, last.lon));
  const pulse = 6 + 4 * Math.abs(Math.sin(Date.now() / 500));
  ctx.strokeStyle = '#ff5252'; ctx.lineWidth = 2;
  ctx.beginPath(); ctx.arc(b[0], b[1], pulse, 0, 7); ctx.stroke();
  ctx.fillStyle = '#ff5252'; ctx.beginPath(); ctx.arc(b[0], b[1], 5, 0, 7); ctx.fill();
  const txt = (last.spd*3.6).toFixed(1) + ' km/h' + (last.bat >= 0 ? '  ' + last.bat.toFixed(0) + '%' : '');
  ctx.fillStyle = '#e0e0e0'; ctx.font = '12px "Segoe UI",sans-serif'; ctx.fillText(txt, b[0] + 10, b[1] - 8);

  // 比例尺
  const mPerPx = CIRC * Math.cos(last.lat * Math.PI / 180) / S;
  let target = 100, barPx = 0;
  for (const m of [10,20,50,100,200,500,1000,2000,5000,10000,20000]){ 
    barPx = m / mPerPx; if (barPx > 60 && barPx < 200){ target = m; break; } }
  const bx = 14, by = h - 58;
  ctx.strokeStyle = '#9e9e9e'; ctx.lineWidth = 2;
  ctx.beginPath(); ctx.moveTo(bx, by); ctx.lineTo(bx + barPx, by);
  ctx.moveTo(bx, by - 4); ctx.lineTo(bx, by + 4);
  ctx.moveTo(bx + barPx, by - 4); ctx.lineTo(bx + barPx, by + 4); ctx.stroke();
  ctx.fillStyle = '#9e9e9e'; ctx.font = '11px Consolas,monospace';
  ctx.fillText(target >= 1000 ? (target/1000) + ' km' : target + ' m', bx + barPx + 8, by + 4);
}

function fmt(t){ return new Date(t * 1000).toLocaleTimeString(); }

let lastCount = -1;
async function tick(){
  try{
    const r = await fetch('/track');
    const d = await r.json();
    if (d.points.length !== lastCount){
      lastCount = d.points.length;
      pts = d.points;
      if (follow) fit();
    }
    if (pts.length){
      emptyBox.style.display = 'none';
      const p = pts[pts.length - 1];
      bar.innerHTML = 'LiveMap · 最新 <b>' + fmt(p.ts) + '</b> ｜ ' +
        p.lat.toFixed(6) + ', ' + p.lon.toFixed(6) + ' ｜ <b>' + (p.spd*3.6).toFixed(1) +
        ' km/h</b>' + (p.bat >= 0 ? ' ｜ 电量 ' + p.bat.toFixed(0) + '%' : '') +
        ' ｜ 精度 ±' + p.acc.toFixed(0) + ' m ｜ 轨迹 <b>' + d.count + '</b> 点' +
        ' <span class="dim">（拖拽平移 / 滚轮缩放 / 双击跟随）</span>';
      hud.innerHTML = follow ? '<b>跟随中</b>' : '<b>自由浏览</b>（双击回到跟随）';
    }
  }catch(e){ /* 下一秒再试 */ }
  draw();
}
setInterval(tick, 1000); tick();
requestAnimationFrame(function loop(){ if (pts.length) draw(); requestAnimationFrame(loop); });

// 交互
let dragging = false, lx = 0, ly = 0;
cv.addEventListener('mousedown', e => { dragging = true; lx = e.clientX; ly = e.clientY; cv.classList.add('drag'); });
addEventListener('mouseup', () => { dragging = false; cv.classList.remove('drag'); });
addEventListener('mousemove', e => {
  if (!dragging) return;
  cx -= (e.clientX - lx) / S; cy -= (e.clientY - ly) / S;
  lx = e.clientX; ly = e.clientY; follow = false; draw();
});
cv.addEventListener('wheel', e => {
  e.preventDefault();
  const k = e.deltaY > 0 ? 0.85 : 1.18;
  S = Math.max(200, Math.min(5e8, S * k));
  follow = false; draw();
}, {passive: false});
cv.addEventListener('dblclick', () => { follow = true; fit(); draw(); });
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
