# -*- coding: utf-8 -*-
"""WASAPI loopback 系统声音捕获（纯 ctypes 实现）。

不依赖立体声混音、不依赖 sounddevice/pyaudio 的回环能力，直接调用 Windows Core Audio
API 抓取『系统正在播放的声音』（扬声器/耳机输出）。输出固定为单声道 44100Hz 16bit PCM，
直接喂给手机端 AudioTrack。

仅 Windows 可用；其它平台调用 capture_system_audio 会抛 RuntimeError，由上层回退。
"""
import ctypes
import ctypes.wintypes as wt
import array
import time

# ===================== GUID =====================
class GUID(ctypes.Structure):
    _fields_ = [
        ("Data1", ctypes.c_ulong),
        ("Data2", ctypes.c_ushort),
        ("Data3", ctypes.c_ushort),
        ("Data4", ctypes.c_ubyte * 8),
    ]


CLSID_MMDeviceEnumerator = GUID(0xBCDE0395, 0xE52F, 0x467C,
                                 (0x8E, 0x3D, 0xC4, 0x57, 0x92, 0x91, 0x69, 0x2E))
IID_IMMDeviceEnumerator = GUID(0xA95664D2, 0x9614, 0x4F35,
                                (0xA7, 0x46, 0xDE, 0x8D, 0xB6, 0x36, 0x17, 0xE6))
IID_IAudioClient = GUID(0x1CB9AD4C, 0xDBFA, 0x4C32,
                        (0xB1, 0x78, 0xC2, 0xF5, 0x68, 0xA7, 0x03, 0xB2))
IID_IAudioCaptureClient = GUID(0xC8ADBD64, 0xE71E, 0x48A0,
                               (0xA4, 0xDE, 0x18, 0x5C, 0x39, 0x7C, 0xDA, 0xFB))

# ===================== 常量 =====================
WAVE_FORMAT_PCM = 0x0001
WAVE_FORMAT_EXTENSIBLE = 0xFFFE
SUBTYPE_IEEE_FLOAT = GUID(0x00000003, 0x0000, 0x0010,
                          (0x80, 0x00, 0x00, 0xAA, 0x00, 0x38, 0x9B, 0x71))
SUBTYPE_PCM = GUID(0x00000001, 0x0000, 0x0010,
                   (0x80, 0x00, 0x00, 0xAA, 0x00, 0x38, 0x9B, 0x71))

AUDCLNT_SHAREMODE_SHARED = 1
AUDCLNT_STREAMFLAGS_LOOPBACK = 0x00020000
AUDCLNT_BUFFERFLAGS_SILENT = 0x00000002

CLSCTX_ALL = 0x17
eRender = 0
eConsole = 0

# ===================== 结构体 =====================
class WAVEFORMATEX(ctypes.Structure):
    _fields_ = [
        ("wFormatTag", ctypes.c_ushort),
        ("nChannels", ctypes.c_ushort),
        ("nSamplesPerSec", ctypes.c_ulong),
        ("nAvgBytesPerSec", ctypes.c_ulong),
        ("nBlockAlign", ctypes.c_ushort),
        ("wBitsPerSample", ctypes.c_ushort),
        ("cbSize", ctypes.c_ushort),
    ]


class WAVEFORMATEXTENSIBLE(ctypes.Structure):
    _fields_ = [
        ("Format", WAVEFORMATEX),
        ("Samples", ctypes.c_ushort),
        ("dwChannelMask", ctypes.c_ulong),
        ("SubFormat", GUID),
    ]


# ===================== COM 调用辅助 =====================
_c_void = ctypes.c_void_p
_c_uint = ctypes.c_uint
_c_ulong = ctypes.c_ulong
_c_ulonglong = ctypes.c_ulonglong
_c_int = ctypes.c_int
HRESULT = ctypes.HRESULT


def _vfunc(iface, index, restype, argtypes):
    """从 COM 接口指针的虚表里取出第 index 个方法，包装成可调用函数。"""
    vtbl = ctypes.cast(iface, ctypes.POINTER(ctypes.POINTER(_c_void)))[0]
    fn = ctypes.cast(vtbl[index], ctypes.CFUNCTYPE(restype, *argtypes))
    return fn


def _guid_equal(a, b):
    return (a.Data1 == b.Data1 and a.Data2 == b.Data2 and a.Data3 == b.Data3
            and bytes(a.Data4) == bytes(b.Data4))


def _resample_mono_int16(samples, in_rate, out_rate=44100):
    """单声道 int16 列表，按线性插值重采样到 out_rate。"""
    if in_rate == out_rate or not samples:
        return samples
    ratio = out_rate / float(in_rate)
    n_out = int(len(samples) * ratio)
    out = []
    denom = len(samples) - 1
    for i in range(n_out):
        pos = i / ratio
        i0 = int(pos)
        frac = pos - i0
        i1 = i0 + 1
        if i1 > denom:
            i1 = denom
        v = samples[i0] * (1.0 - frac) + samples[i1] * frac
        iv = int(v)
        if iv > 32767:
            iv = 32767
        elif iv < -32768:
            iv = -32768
        out.append(iv)
    return out


def _convert_to_mono_int16(buf, channels, bits, is_float, rate, target_rate=44100):
    """把原始交错音频字节转成单声道 int16 列表（已重采样到 target_rate）。"""
    if is_float:
        fa = array.array('f')
        fa.frombytes(buf)
        samples = [float(x) for x in fa]
    elif bits == 16:
        sa = array.array('h')
        sa.frombytes(buf)
        samples = [int(x) for x in sa]
    elif bits == 32:
        ia = array.array('i')
        ia.frombytes(buf)
        samples = [x / 2147483648.0 * 32767.0 for x in ia]
    elif bits == 24:
        # 24bit 小端 -> int
        n = len(buf)
        samples = []
        for i in range(0, n, 3):
            v = buf[i] | (buf[i + 1] << 8) | (buf[i + 2] << 16)
            if v & 0x800000:
                v -= 0x1000000
            samples.append(v / 8388608.0 * 32767.0)
    elif bits == 8:
        ba = array.array('b')
        ba.frombytes(buf)
        samples = [int(x) * 256 for x in ba]
    else:
        return []

    # 多声道下混为单声道
    if channels > 1:
        mono = []
        for i in range(0, len(samples), channels):
            s = 0
            for c in range(channels):
                s += samples[i + c]
            mono.append(s / channels)
        samples = mono

    # 转 int16 并钳制
    ints = []
    for v in samples:
        iv = int(round(v)) if isinstance(v, float) else int(v)
        if iv > 32767:
            iv = 32767
        elif iv < -32768:
            iv = -32768
        ints.append(iv)

    if rate != target_rate:
        ints = _resample_mono_int16(ints, rate, target_rate)
    return ints


def capture_system_audio(on_chunk, stop_check, target_rate=44100, logger=None):
    """持续捕获系统播放声音。

    on_chunk(bytes): 每个转换后的单声道 44100Hz int16 块回调一次。
    stop_check() -> bool: 返回 True 时停止捕获。
    非 Windows 或 COM 失败抛 RuntimeError。
    """
    if not hasattr(ctypes, "windll"):
        raise RuntimeError("WASAPI loopback 仅支持 Windows")
    ole32 = ctypes.windll.ole32
    ole32.CoInitializeEx.argtypes = [_c_void, _c_uint]
    ole32.CoInitializeEx.restype = HRESULT
    ole32.CoUninitialize.argtypes = []
    ole32.CoUninitialize.restype = None
    ole32.CoCreateInstance.argtypes = [ctypes.POINTER(GUID), _c_void,
                                       _c_ulong, ctypes.POINTER(GUID),
                                       ctypes.POINTER(_c_void)]
    ole32.CoCreateInstance.restype = HRESULT

    def log(m):
        if logger:
            logger(m)

    ole32.CoInitializeEx(None, 0)  # COINIT_MULTITHREADED = 0
    pEnum = _c_void()
    pDev = _c_void()
    pClient = _c_void()
    pCap = _c_void()
    try:
        hr = ole32.CoCreateInstance(ctypes.byref(CLSID_MMDeviceEnumerator), None,
                                    CLSCTX_ALL, ctypes.byref(IID_IMMDeviceEnumerator),
                                    ctypes.byref(pEnum))
        if hr < 0:
            raise ctypes.WinError(hr)
        # IMMDeviceEnumerator::GetDefaultAudioEndpoint (vtable index 4)
        fn = _vfunc(pEnum, 4, HRESULT,
                    [_c_void, _c_int, _c_int, ctypes.POINTER(_c_void)])
        hr = fn(pEnum, eRender, eConsole, ctypes.byref(pDev))
        if hr < 0:
            raise ctypes.WinError(hr)
        # IMMDevice::Activate (vtable index 3)
        fn = _vfunc(pDev, 3, HRESULT,
                    [_c_void, ctypes.POINTER(GUID), _c_ulong, _c_void,
                     ctypes.POINTER(_c_void)])
        hr = fn(pDev, ctypes.byref(IID_IAudioClient), CLSCTX_ALL, None,
                ctypes.byref(pClient))
        if hr < 0:
            raise ctypes.WinError(hr)
        def _build_pcm(rate, ch, bits=16):
            fe = WAVEFORMATEX()
            fe.wFormatTag = WAVE_FORMAT_PCM
            fe.nChannels = ch
            fe.nSamplesPerSec = rate
            fe.wBitsPerSample = bits
            fe.nBlockAlign = bits // 8 * ch
            fe.nAvgBytesPerSec = rate * fe.nBlockAlign
            fe.cbSize = 0
            return ctypes.pointer(fe)

        # IAudioClient::Initialize (vtable index 3)  —— loopback
        # 注意：不能直接用 GetMixFormat 返回的 32bit int 混音格式（部分驱动拒绝），
        # 改用 16bit PCM 让音频引擎做格式转换；依次尝试几个候选格式。
        fn_init = _vfunc(pClient, 3, HRESULT,
                         [_c_void, _c_int, _c_ulong, ctypes.c_longlong,
                          ctypes.c_longlong, ctypes.POINTER(WAVEFORMATEX), _c_void])
        candidates = [_build_pcm(44100, 2), _build_pcm(48000, 2), _build_pcm(44100, 1)]
        initialized = False
        last_hr = -1
        for fmt_ptr in candidates:
            f = fmt_ptr.contents
            log(f"[wasapi] 尝试 loopback 格式 {f.nSamplesPerSec}Hz {f.wBitsPerSample}bit 声道={f.nChannels}")
            last_hr = fn_init(pClient, AUDCLNT_SHAREMODE_SHARED,
                              AUDCLNT_STREAMFLAGS_LOOPBACK, 0, 0, fmt_ptr, None)
            if last_hr >= 0:
                channels, rate, bits, is_float = (f.nChannels, f.nSamplesPerSec,
                                                  f.wBitsPerSample, False)
                initialized = True
                break
            else:
                log(f"[wasapi] 该格式 loopback 失败: {ctypes.WinError(last_hr)}")
        if not initialized:
            raise ctypes.WinError(last_hr)
        # IAudioClient::GetService (vtable index 14)
        fn = _vfunc(pClient, 14, HRESULT,
                    [_c_void, ctypes.POINTER(GUID), ctypes.POINTER(_c_void)])
        hr = fn(pClient, ctypes.byref(IID_IAudioCaptureClient), ctypes.byref(pCap))
        if hr < 0:
            raise ctypes.WinError(hr)
        # IAudioClient::Start (vtable index 10)
        fn = _vfunc(pClient, 10, HRESULT, [_c_void])
        hr = fn(pClient)
        if hr < 0:
            raise ctypes.WinError(hr)
        # IAudioCaptureClient 方法
        fn_get = _vfunc(pCap, 3, HRESULT,
                        [_c_void, ctypes.POINTER(_c_void), ctypes.POINTER(_c_uint),
                         ctypes.POINTER(_c_uint), ctypes.POINTER(_c_ulonglong),
                         ctypes.POINTER(_c_ulonglong)])
        fn_next = _vfunc(pCap, 5, HRESULT, [_c_void, ctypes.POINTER(_c_uint)])
        fn_rel = _vfunc(pCap, 4, HRESULT, [_c_void, _c_uint])

        bytes_per_sample = max(1, bits // 8)
        frame_bytes = channels * bytes_per_sample
        silent_streak = 0
        log("[wasapi] loopback 已开启，开始捕获系统声音")
        while not stop_check():
            nps = _c_uint(0)
            hr = fn_next(pCap, ctypes.byref(nps))
            if hr < 0 or nps.value == 0:
                time.sleep(0.005)
                continue
            pdata = _c_void()
            flags = _c_uint(0)
            pos = _c_ulonglong(0)
            qpc = _c_ulonglong(0)
            frames = _c_uint(0)
            hr = fn_get(pCap, ctypes.byref(pdata), ctypes.byref(frames),
                        ctypes.byref(flags), ctypes.byref(pos), ctypes.byref(qpc))
            if hr < 0:
                time.sleep(0.005)
                continue
            n = frames.value
            if n > 0 and not (flags.value & AUDCLNT_BUFFERFLAGS_SILENT) and pdata:
                raw = ctypes.string_at(pdata, n * frame_bytes)
                ints = _convert_to_mono_int16(raw, channels, bits, is_float, rate,
                                              target_rate)
                if ints:
                    chunk = array.array('h', ints).tobytes()
                    on_chunk(chunk)
                    peak = max((abs(x) for x in ints), default=0)
                    if peak < 50:
                        silent_streak += 1
                    else:
                        silent_streak = 0
                    if silent_streak >= 200:
                        log("[wasapi] 已连续约 9 秒接近静音，请确认电脑正在播放声音。")
                        silent_streak = 0
            fn_rel(pCap, n)
        # IAudioClient::Stop (vtable index 11)
        fn = _vfunc(pClient, 11, HRESULT, [_c_void])
        fn(pClient)
    finally:
        for p in (pCap, pClient, pDev, pEnum):
            if p:
                try:
                    _vfunc(p, 2, HRESULT, [_c_void])(p)  # Release
                except Exception:
                    pass
        ole32.CoUninitialize()
