# LiveMap · 手机实时位置 → 电脑地图

手机（Kotlin 前台服务）把 GPS 位置实时推给电脑，电脑浏览器打开一张
Leaflet + OpenStreetMap 地图，实时显示当前位置与运动轨迹。

> 这是 `路线图.md` 第一部分的**新测试项目**，PC 端与手机端都是**独立可运行**的，
> **没有并入 PhoneHub 主工程**，互不干扰。

## 目录

```
cs_apps/LiveMap/
├── pc/map_server.py                 # 电脑端：零第三方依赖的地图服务（Python 3.8+）
└── android/                         # 手机端：独立 Gradle 工程，包名 com.phonehub.livemap
    ├── settings.gradle.kts / build.gradle.kts / gradle.properties / local.properties
    └── app/src/main/
        ├── AndroidManifest.xml
        ├── java/com/phonehub/livemap/MainActivity.kt         # 控制台（填 IP/端口、起停、看状态）
        └── java/com/phonehub/livemap/LiveLocationPusher.kt    # 前台定位服务（推点 + 断网补发）
```

## 一、电脑端

```bash
cd cs_apps/LiveMap/pc
python map_server.py            # 默认 5678 端口，防火墙放行
# 浏览器打开 http://127.0.0.1:5678/
```

不接手机也能自测（模拟推点）：

```bash
curl -X POST http://127.0.0.1:5678/push \
     -d "{\"lat\":30.15,\"lon\":120.10,\"spd\":3.5,\"bat\":88,\"acc\":12}"
```

## 二、手机端（独立 APK）

刻意**零第三方依赖**（只用平台 LocationManager / Notification.Builder / HttpURLConnection），
所以本机离线也能编（Gradle 缓存里缺 androidx 的传递依赖，如 lifecycle-runtime:2.3.1）。

```bash
cd cs_apps/LiveMap/android
<gradle-8.9>/bin/gradle assembleDebug --no-daemon --console=plain --offline
# 产物：app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> `gradle.properties` 里 `org.gradle.java.home` 用 **F:** 实路径（不要用 `C:\PhoneHub` 的
> junction），否则 Kotlin 增量缓存会出现 C:/F: 根路径不匹配。

装上后桌面上会出现独立图标 **LiveMap**：

1. 填电脑的局域网 IP 与端口（默认 5678）→ 点「开始共享位置」
2. 首次会要定位权限，允许即可
3. 界面每秒刷新：成功/失败次数、待补发点数、最近定位与速度、最后一次错误
4. 电脑上打开 `http://127.0.0.1:5678/` 看地图

## 三、接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/push` | 推点：`{"lat","lon","spd"(m/s),"bat"(0-100,-1未知),"acc"(米),"ts"(秒)}` |
| GET | `/track?since=<ts>` | 轨迹点全量/增量 JSON：`{"count":n,"points":[...]}` |
| GET | `/` | 地图页（Leaflet，每秒轮询刷新） |

## 四、行为细节

- **电脑端**：内存环形队列最多 5000 点，超出丢最旧；轨迹 polyline 实时重绘；
  当前点 marker + 气泡（时间/速度/电量/精度），地图自动跟随平移。
- **手机端**：GPS + 网络双定位源，谁新用谁；每 2 秒取一次，
  位移 < 10 米不发（但静止时每 10 秒发一次心跳），省电省流量。
  断网写入 `Android/data/com.phonehub.livemap/files/livemap_pending.txt`，
  恢复后**按原顺序**逐条补发，不丢轨迹。
- 停服务即停推；App 不常驻后台耗电（前台服务只在共享期间存在）。

## 五、验证记录

- 2026-09-13：连推 3 点 → `/track` 返回 3 点、字段完整；页面 HTML 正常返回。
- 2026-09-13：手机端独立工程 `assembleDebug` 通过并装机 —— 见下节路线图。

## 六、下一步（路线图 M2）

- 手机端服务并入 PhoneHub 主 app（路线图页加开关），与现有 `location` 上报复用一套定位源。
- 历史轨迹回放（时间轴拖动）、地理围栏、多点手机同屏。
- Leaflet 与 OSM 瓦片走 CDN，内网环境需要离线瓦片兜底。
