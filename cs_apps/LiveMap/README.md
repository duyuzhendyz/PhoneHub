# LiveMap · 手机实时位置 → 电脑地图

手机（Kotlin 前台服务）把 GPS 位置实时推给电脑，电脑浏览器打开一张
Leaflet + OpenStreetMap 地图，实时显示当前位置与运动轨迹。

## 运行

**电脑端（零第三方依赖，Python 3.8+）**
```bash
cd cs_apps/LiveMap/pc
python map_server.py            # 默认 5678 端口，防火墙放行
# 浏览器打开 http://127.0.0.1:5678/
```

**模拟推点（无手机也可自测）**
```bash
curl -X POST http://127.0.0.1:5678/push -d "{\"lat\":30.15,\"lon\":120.10,\"spd\":3.5,\"bat\":88,\"acc\":12}"
```

**手机端（Kotlin）**
- `android/LiveLocationPusher.kt`：前台服务，每 2 秒（或位移>10 米）推一个点，
  断网先缓存、恢复后按序补发。
- 并入主 app：manifest 注册 service（`foregroundServiceType="location"`）+
  `FOREGROUND_SERVICE_LOCATION` 权限；路线图页加开关即可。
  单独跑：`adb shell am start-foreground-service ...` 或在任意 Activity 里
  `startForegroundService(Intent(this, LiveLocationPusher::class.java)
      .putExtra("pc_ip", "192.168.3.9").putExtra("pc_port", 5678))`

## 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/push` | 推点：`{"lat","lon","spd"(m/s),"bat"(0-100,-1未知),"acc"(米),"ts"(秒)}` |
| GET | `/track?since=<ts>` | 轨迹点全量/增量 JSON：`{"count":n,"points":[...]}` |
| GET | `/` | 地图页（Leaflet，每秒轮询刷新） |

## 行为细节
- 内存环形队列最多 5000 点，超出丢最旧；轨迹为 polyline 实时重绘。
- 页面当前点 marker + 弹出气泡（时间/速度/电量/精度），地图自动跟随平移。
- 手机端 GPS + 网络双定位源，谁新用谁；停服务即停推，无后台残留。

## 验证记录（2026-09-13）
- 连推 3 点 → `/track` 返回 3 点、字段完整；页面 HTML 正常返回。
