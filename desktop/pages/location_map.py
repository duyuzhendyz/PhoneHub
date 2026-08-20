# -*- coding: utf-8 -*-
"""移动路线图页面（暂未开放）"""
from PyQt5.QtWidgets import QWidget, QVBoxLayout
from PyQt5.QtCore import Qt
from PyQt5.QtGui import QFont
from qfluentwidgets import (TitleLabel, BodyLabel, setFont, FluentIcon as FIF)
from styles import get_theme, _c

# ====================================================================
# 以下为原始功能代码，因闪退/白屏问题暂时注释禁用
# 移动路线图功能暂未开放，仅显示提示页面
# ====================================================================
'''
import os
import json
import time
from PyQt5.QtWidgets import (QWidget, QVBoxLayout, QHBoxLayout, QLabel,
                               QPushButton, QFrame, QListWidget, QListWidgetItem,
                               QDateEdit, QMessageBox, QMenu, QButtonGroup,
                               QRadioButton, QProgressBar)
from PyQt5.QtCore import Qt, QDate, QUrl
from styles import get_theme, _c, apply_dark_title_bar

DATA_DIR = os.path.join(os.path.expanduser("~"), "PhoneHub", "data")
CACHE_FILE = os.path.join(DATA_DIR, "location_cache.json")

LEAFLET_HTML = """<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8" />
<title>Location Map</title>
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
<style>
  html, body, #map { margin:0; padding:0; height:100%; background:#1e1e1e; }
</style>
</head>
<body>
<div id="map"></div>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<script>
  const map = L.map('map').setView([35.0, 105.0], 4);
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: 'OpenStreetMap',
    maxZoom: 19
  }).addTo(map);

  let currentLayer = null;

  function renderLocations(locs) {
    if (currentLayer) { map.removeLayer(currentLayer); }
    if (!locs || locs.length === 0) { return; }

    const group = L.layerGroup().addTo(map);
    const sorted = locs.slice().sort((a,b) => (a.ts||0) - (b.ts||0));
    const latlngs = [];
    let prevLatLng = null;

    sorted.forEach((loc, idx) => {
      const lat = parseFloat(loc.lat);
      const lng = parseFloat(loc.lng);
      if (isNaN(lat) || isNaN(lng)) return;
      const latlng = [lat, lng];
      const isGap = (loc.signal === false) || (loc.signal === 0);

      if (prevLatLng) {
        if (isGap) {
          // 无信号路段灰色虚线
          L.polyline([prevLatLng, latlng], {
            color: '#888', weight: 3, opacity: 0.7, dashArray: '6,6'
          }).addTo(group);
        } else {
          L.polyline([prevLatLng, latlng], {
            color: '#0078d4', weight: 4, opacity: 0.85
          }).addTo(group);
        }
      }
      latlngs.push(latlng);
      const marker = L.circleMarker(latlng, {
        radius: 5, color: isGap ? '#888' : '#0078d4',
        fillColor: isGap ? '#aaa' : '#fff', fillOpacity: 0.9
      }).addTo(group);
      const tsStr = new Date((loc.ts || 0) * 1000).toLocaleString();
      marker.bindPopup(`<b>点 ${idx+1}</b><br>时间: ${tsStr}<br>经纬: ${lat.toFixed(5)}, ${lng.toFixed(5)}${isGap?'<br><i style=color:#888>(无信号)</i>':''}`);
      prevLatLng = latlng;
    });

    if (latlngs.length > 0) {
      try { map.fitBounds(L.latLngBounds(latlngs).pad(0.2)); } catch(e) {}
    }
    currentLayer = group;
  }

  window.renderLocations = renderLocations;
  // 暴露给Python的接口
  window._pushLocations = function(arr) { renderLocations(arr); };
</script>
</body>
</html>"""


class _LocationMapPageDisabled(QWidget):
    """移动路线图（已禁用）"""

    def __init__(self, manager):
        super().__init__()
        self.manager = manager
        self.locations_cache = []
        self._load_cache()
        self._setup_ui()
        self._connect_signals()
        self._start_async_load()

    def _setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(24, 20, 24, 20)
        layout.setSpacing(16)

        title = QLabel("移动路线图")
        title.setObjectName("titleLabel")
        layout.addWidget(title)

        # 控制栏
        ctrl_frame = QFrame()
        ctrl_layout = QHBoxLayout(ctrl_frame)
        ctrl_layout.setSpacing(8)

        self.rb_today = QRadioButton("今天")
        self.rb_7days = QRadioButton("近7天")
        self.rb_custom = QRadioButton("自定义")
        self.rb_today.setChecked(True)
        grp = QButtonGroup(self)
        grp.addButton(self.rb_today)
        grp.addButton(self.rb_7days)
        grp.addButton(self.rb_custom)
        for rb in (self.rb_today, self.rb_7days, self.rb_custom):
            ctrl_layout.addWidget(rb)

        ctrl_layout.addWidget(QLabel("从"))
        self.date_from = QDateEdit()
        self.date_from.setCalendarPopup(True)
        self.date_from.setDate(QDate.currentDate().addDays(-1))
        self.date_from.setDisplayFormat("yyyy-MM-dd")
        ctrl_layout.addWidget(self.date_from)

        ctrl_layout.addWidget(QLabel("到"))
        self.date_to = QDateEdit()
        self.date_to.setCalendarPopup(True)
        self.date_to.setDate(QDate.currentDate())
        self.date_to.setDisplayFormat("yyyy-MM-dd")
        ctrl_layout.addWidget(self.date_to)

        self.query_btn = QPushButton("查询")
        ctrl_layout.addWidget(self.query_btn)

        self.clear_btn = QPushButton("一键清空")
        ctrl_layout.addWidget(self.clear_btn)

        self.del_sel_btn = QPushButton("删除选中")
        ctrl_layout.addWidget(self.del_sel_btn)

        ctrl_layout.addStretch()
        layout.addWidget(ctrl_frame)

        # 加载占位符 (进度条)
        self.loading_frame = QFrame()
        loading_layout = QVBoxLayout(self.loading_frame)
        loading_layout.setContentsMargins(40, 40, 40, 40)
        loading_layout.setSpacing(20)

        self.loading_label = QLabel("正在加载地图组件...")
        self.loading_label.setAlignment(Qt.AlignCenter)
        loading_layout.addWidget(self.loading_label)

        self.progress_bar = QProgressBar()
        self.progress_bar.setRange(0, 100)
        self.progress_bar.setValue(0)
        self.progress_bar.setTextVisible(True)
        loading_layout.addWidget(self.progress_bar)

        layout.addWidget(self.loading_frame, 1)

        # 实际内容容器 (初始隐藏，通过content_frame控制可见性)
        self.content_frame = QFrame()
        self.content_layout = QHBoxLayout(self.content_frame)
        self.content_layout.setSpacing(12)
        self.content_layout.setContentsMargins(0, 0, 0, 0)
        layout.addWidget(self.content_frame, 1)
        self.content_frame.hide()

        self.web_view = None
        self.list_widget = None

    def _start_async_load(self):
        import threading
        thread = threading.Thread(target=self._async_load_webengine, daemon=True)
        thread.start()

    def _async_load_webengine(self):
        time.sleep(0.1)
        try:
            from PyQt5.QtWebEngineWidgets import QWebEngineView
            from PyQt5.QtCore import QMetaObject, Q_ARG
            web_view = QWebEngineView()

            def finish_load():
                web_view.setHtml(LEAFLET_HTML, QUrl("https://unpkg.com/leaflet@1.9.4/"))
                self._init_content(web_view)

            QMetaObject.invokeMethod(web_view, "show", Qt.QueuedConnection)
            QMetaObject.invokeMethod(self, "_update_progress", Qt.QueuedConnection,
                                     Q_ARG(int, 50))
            time.sleep(0.5)
            QMetaObject.invokeMethod(self, "_update_progress", Qt.QueuedConnection,
                                     Q_ARG(int, 100))
            QMetaObject.invokeMethod(self, finish_load, Qt.QueuedConnection)
        except Exception as e:
            from PyQt5.QtCore import QMetaObject, Q_ARG
            QMetaObject.invokeMethod(self, "_load_failed", Qt.QueuedConnection,
                                     Q_ARG(str, str(e)))

    def _update_progress(self, value):
        self.progress_bar.setValue(value)
        if value == 100:
            self.loading_label.setText("加载完成，正在显示地图...")

    def _load_failed(self, error_msg):
        self.loading_frame.hide()
        error_label = QLabel(f"无法加载地图组件。\n错误: {error_msg}\n\n请安装 PyQtWebEngine:\n  pip install PyQtWebEngine")
        error_label.setAlignment(Qt.AlignCenter)
        self.content_layout.addWidget(error_label)
        self.content_layout.parentWidget().show()

    def _init_content(self, web_view):
        self.web_view = web_view

        map_frame = QFrame()
        map_layout = QVBoxLayout(map_frame)
        map_layout.setContentsMargins(2, 2, 2, 2)
        map_layout.addWidget(web_view)
        self.content_layout.addWidget(map_frame, 3)

        list_frame = QFrame()
        list_layout = QVBoxLayout(list_frame)
        label = QLabel("轨迹点 (可多选删除)")
        list_layout.addWidget(label)
        self.list_widget = QListWidget()
        self.list_widget.setSelectionMode(QListWidget.ExtendedSelection)
        self.list_widget.setContextMenuPolicy(Qt.CustomContextMenu)
        list_layout.addWidget(self.list_widget)
        self.list_widget.customContextMenuRequested.connect(self._on_list_menu)
        self.content_layout.addWidget(list_frame, 1)

        self.loading_frame.hide()
        self.content_frame.show()

        self._refresh_list()
        from PyQt5.QtCore import QTimer
        QTimer.singleShot(500, self._render_on_map)

    def _connect_signals(self):
        self.query_btn.clicked.connect(self._query)
        self.clear_btn.clicked.connect(self._clear_all)
        self.del_sel_btn.clicked.connect(self._delete_selected)
        try:
            self.manager.location_received.connect(self._on_location_received)
        except Exception:
            pass

    def _load_map(self):
        if self.web_view:
            QTimer = None
            from PyQt5.QtCore import QTimer as _QTimer
            _QTimer.singleShot(800, self._render_on_map)

    def _render_on_map(self):
        if not self.web_view:
            return
        locs_json = json.dumps(self._filtered_locations())
        # 调用JS渲染
        js = f"window.renderLocations && window.renderLocations({locs_json});"
        self.web_view.page().runJavaScript(js)

    def _filtered_locations(self):
        if self.rb_today.isChecked():
            start = time.mktime(time.strptime(time.strftime("%Y-%m-%d"), "%Y-%m-%d"))
            return [l for l in self.locations_cache if l.get('ts', 0) >= start]
        elif self.rb_7days.isChecked():
            cutoff = time.time() - 7 * 86400
            return [l for l in self.locations_cache if l.get('ts', 0) >= cutoff]
        else:
            start = self.date_from.date().startOfDay().toSecsSinceEpoch()
            end = self.date_to.date().endOfDay().toSecsSinceEpoch()
            return [l for l in self.locations_cache if start <= l.get('ts', 0) <= end]

    def _query(self):
        # PAW中转已禁用，仅查询本地缓存的轨迹数据
        try:
            locs = self._filtered_locations()
            self._refresh_list()
            self._render_on_map()
            QMessageBox.information(self, "查询完成", f"本地缓存共 {len(locs)} 条轨迹数据。")
        except Exception as e:
            QMessageBox.warning(self, "查询出错", str(e))

    def _merge_into_cache(self, locs):
        existing_keys = set()
        for l in self.locations_cache:
            key = (round(l.get('lat', 0), 6), round(l.get('lng', 0), 6), int(l.get('ts', 0)))
            existing_keys.add(key)
        for l in locs:
            key = (round(l.get('lat', 0), 6), round(l.get('lng', 0), 6), int(l.get('ts', 0)))
            if key not in existing_keys:
                self.locations_cache.append(l)
                existing_keys.add(key)
        self.locations_cache.sort(key=lambda x: x.get('ts', 0))

    def _clear_all(self):
        if QMessageBox.question(self, "确认", "清空本地所有缓存轨迹数据?") != QMessageBox.Yes:
            return
        self.locations_cache.clear()
        self._save_cache()
        self._refresh_list()
        self._render_on_map()

    def _delete_selected(self):
        items = self.list_widget.selectedItems()
        if not items:
            QMessageBox.information(self, "提示", "请先在列表中选择要删除的轨迹点。")
            return
        to_delete = []
        for item in items:
            data = item.data(Qt.UserRole)
            if data:
                to_delete.append(data)
        if QMessageBox.question(self, "确认", f"删除选中的 {len(to_delete)} 条轨迹?") != QMessageBox.Yes:
            return
        # 仅删除本地缓存
        for d in to_delete:
            try:
                self.locations_cache.remove(d)
            except ValueError:
                pass
        self._save_cache()
        self._refresh_list()
        self._render_on_map()

    def _on_list_menu(self, pos):
        item = self.list_widget.itemAt(pos)
        if not item:
            return
        menu = QMenu(self)
        act_del = menu.addAction("删除此条")
        action = menu.exec_(self.list_widget.mapToGlobal(pos))
        if action == act_del:
            self.list_widget.clearSelection()
            item.setSelected(True)
            self._delete_selected()

    def _refresh_list(self):
        self.list_widget.clear()
        for loc in self._filtered_locations():
            ts = time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(loc.get('ts', 0)))
            lat = loc.get('lat', 0)
            lng = loc.get('lng', 0)
            sig = "无信号" if (loc.get('signal') in (False, 0)) else "正常"
            text = f"[{ts}] {lat:.5f},{lng:.5f} ({sig})"
            item = QListWidgetItem(text)
            item.setData(Qt.UserRole, loc)
            self.list_widget.addItem(item)

    def _on_location_received(self, locations):
        if isinstance(locations, list) and locations:
            self._merge_into_cache(locations)
            self._save_cache()
            self._refresh_list()
            self._render_on_map()

    def _load_cache(self):
        try:
            if os.path.exists(CACHE_FILE):
                with open(CACHE_FILE, 'r', encoding='utf-8') as f:
                    self.locations_cache = json.load(f)
        except Exception:
            self.locations_cache = []

    def _save_cache(self):
        try:
            os.makedirs(os.path.dirname(CACHE_FILE), exist_ok=True)
            with open(CACHE_FILE, 'w', encoding='utf-8') as f:
                json.dump(self.locations_cache, f, ensure_ascii=False, indent=2)
        except Exception:
            pass
'''
# ====================================================================
# 原始功能代码注释结束
# ====================================================================


class LocationMapPage(QWidget):
    """移动路线图（暂未开放，仅显示提示页面，避免闪退/白屏）"""

    def __init__(self, manager):
        super().__init__()
        self.manager = manager
        self._setup_ui()

    def _setup_ui(self):
        """构建简单的提示页面"""
        layout = QVBoxLayout(self)
        layout.setContentsMargins(24, 20, 24, 20)
        layout.setSpacing(12)

        # 标题
        title = TitleLabel("移动路线图")
        setFont(title, 28, QFont.Bold)
        layout.addWidget(title)

        # 居中提示
        hint = BodyLabel("该功能暂未开放，敬请期待")
        hint.setAlignment(Qt.AlignCenter)
        c = _c()
        hint.setStyleSheet(
            f"font-size: 18px; color: {c['text_secondary']}; "
            f"padding: 60px; background-color: transparent;"
        )
        layout.addWidget(hint, 1)
