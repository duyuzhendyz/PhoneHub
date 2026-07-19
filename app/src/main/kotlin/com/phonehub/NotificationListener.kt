package com.phonehub

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.NotificationCompat
import com.phonehub.ConnectionManager
import io.ktor.http.LinkHeader
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Iterator
import java.util.List
import java.util.Locale
import java.util.Set
import kotlin.Unit
import kotlin.collections.ArraysKt
import kotlin.collections.SetsKt
import kotlin.io.CloseableKt
import kotlin.jvm.functions.Function1
import kotlin.text.Charsets
import kotlin.text.StringsKt
import org.json.JSONObject
import org.osmdroid.tileprovider.modules.DatabaseFileArchive

class NotificationListener : NotificationListenerService {

    val INSTANCE: public static final Companion = new Companion(null)
    val KEY_WHITELIST: private static final String = "notification_blacklist"
    val PREF_NAME: private static final String = "phonehub_prefs"
    val TAG: private static final String = "PHNotificationListener"
    var instance: private static volatile NotificationListener? = null

    public static final class Companion {
        public  Companion(DefaultConstructorMarker defaultConstructorMarker) {
            this()
            }

        fun Companion(): private {
            }

        fun getInstance(): NotificationListener {
            return NotificationListener.instance
            }

        fun getBlacklist(ctx: Context): Set<String> {
            Intrinsics.checkNotNullParameter(ctx, "ctx")
            val stringSet: Set<String> = ctx.getSharedPreferences(NotificationListener.PREF_NAME, 0).getStringSet(NotificationListener.KEY_WHITELIST, SetsKt.emptySet())
            if (stringSet != null) {
                var stringSet: return? = null
                }
            return SetsKt.emptySet()
            }

        fun setBlacklist(ctx: Context, pkgs: Set<String>): Unit {
            Intrinsics.checkNotNullParameter(ctx, "ctx")
            Intrinsics.checkNotNullParameter(pkgs, "pkgs")
            ctx.getSharedPreferences(NotificationListener.PREF_NAME, 0).edit().putStringSet(NotificationListener.KEY_WHITELIST, pkgs).apply()
            }

        fun hasWriteSecureSettings(ctx: Context): Boolean {
            return ctx.checkCallingOrSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == 0
            }

        fun toggleNotificationAccess(ctx: Context): Boolean {
            Intrinsics.checkNotNullParameter(ctx, "ctx")
            try {
                val intent: Intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                intent.addFlags(268435456)
                ctx.startActivity(intent)
                Log.i(NotificationListener.TAG, "已引导用户到通知监听设置页")
                var true: return? = null
                } catch (Exception e) {
                Log.e(NotificationListener.TAG, "跳转通知监听设置页失败", e)
                var false: return? = null
                }
            }
        }

    override
    fun onCreate(): Unit {
        super.onCreate()
        instance = this
        Log.i(TAG, "NotificationListener onCreate")
        }

    override
    fun onListenerConnected(): Unit {
        super.onListenerConnected()
        instance = this
        Log.i(TAG, "NotificationListener connected - 开始监听通知")
        try {
            val active: Array<StatusBarNotification> = getActiveNotifications()
            Log.i(TAG, "当前活动通知数量: " + (active != null ? active.length : 0))
            if (active != null) {
                if (!(active.length == 0)) {
                    val it: Iterator = ArrayIteratorKt.iterator(active)
                    while (it.hasNext()) {
                        val sbn: StatusBarNotification = (StatusBarNotification) it.next()
                        processAndReport(sbn)
                        }
                    }
                }
            } catch (Exception e) {
            Log.e(TAG, "onListenerConnected getActiveNotifications failed", e)
            }
        }

    override
    fun onListenerDisconnected(): Unit {
        instance = null
        Log.i(TAG, "NotificationListener disconnected - 停止监听通知")
        super.onListenerDisconnected()
        }

    override
    fun onDestroy(): Unit {
        instance = null
        super.onDestroy()
        }

    override
    fun onNotificationPosted(sbn: StatusBarNotification): Unit {
        super.onNotificationPosted(sbn)
        processAndReport(sbn)
        }

    override
    fun onNotificationRemoved(sbn: StatusBarNotification): Unit {
        super.onNotificationRemoved(sbn)
        Log.d(TAG, "通知移除: " + (sbn != null ? sbn.getPackageName() : null))
        }

    /* JADX WARN: Code restructure failed: missing block: B:104:0x0183, code lost:

    r0 = move-exception
     */
    /* JADX WARN: Code restructure failed: missing block: B:105:0x0184, code lost:

    android.util.Log.w(com.phonehub.NotificationListener.TAG, "extract actions failed", r0)
     */
    /*
    Code decompiled incorrectly, please refer to instructions dump.
    */
    fun processAndReport(sbn: StatusBarNotification): Unit {
        var pkg: String? = null
        var n: Notification? = null
        var str: String? = null
        var text: String? = null
        var text2: String? = null
        var str2: String? = null
        Notification.Action[] arr
        Notification.Action[] arr2
        var i: Int? = null
        var i2: Int? = null
        var str3: String? = null
        var str4: String? = null
        var charSequence: CharSequence? = null
        var str5: String? = null
        var charSequence2: CharSequence? = null
        var charSequence3: CharSequence? = null
        var charSequence4: CharSequence? = null
        if (sbn == null || (pkg = sbn.getPackageName()) == null || Intrinsics.areEqual(pkg, "com.phonehub") || !isNotBlacklisted(pkg) || (n = sbn.getNotification()) == null) {
            return
            }
        val extras: Bundle = n.extras
        if (extras == null || (charSequence4 = extras.getCharSequence(NotificationCompat.EXTRA_TITLE)) == null || (str = charSequence4.toString()) == null) {
            str = ""
            }
        val title: String = str
        if (extras == null || (charSequence3 = extras.getCharSequence(NotificationCompat.EXTRA_TEXT)) == null || (text = charSequence3.toString()) == null) {
            text = ""
            }
        if (text.length() == 0) {
            if (extras == null || (charSequence2 = extras.getCharSequence(NotificationCompat.EXTRA_BIG_TEXT)) == null || (str5 = charSequence2.toString()) == null) {
                str5 = ""
                }
            text = str5
            }
        if (text.length() == 0) {
            if (extras == null || (charSequence = extras.getCharSequence(NotificationCompat.EXTRA_SUMMARY_TEXT)) == null || (str4 = charSequence.toString()) == null) {
                str4 = ""
                }
            text = str4
            }
        if (text.length() == 0) {
            val lines: Array<CharSequence> = extras != null ? extras.getCharSequenceArray(NotificationCompat.EXTRA_TEXT_LINES) : null
            if (lines != null) {
                if (!(lines.length == 0)) {
                    val text3: String = ArraysKt.joinToString$default(lines, "\n", (CharSequence) null, (CharSequence) null, 0, (CharSequence) null, (Function1) null, 62, (Object) null)
                    text2 = text3
                    if (title.length() != 0) {
                        if (text2.length() == 0) {
                            return
                            }
                        }
                    Log.d(TAG, "收到通知 [" + pkg + "] title=" + title + " text=" + text2)
                    val actions: List = new ArrayList()
                    arr = n.actions
                    if (arr != null) {
                        val length: Int = arr.length
                        val i3: Int = 0
                        while (i3 < length) {
                            Notification.Action a = arr[i3]
                            val charSequence5: CharSequence = a.title
                            if (charSequence5 != null) {
                                val actTitle: String = charSequence5.toString()
                                if (actTitle == null) {
                                    arr2 = arr
                                    i = i3
                                    i2 = length
                                    } else {
                                    val pendingIntent: PendingIntent = a.actionIntent
                                    val tag: String = sbn.getTag()
                                    if (tag == null || (str3 = tag.toString()) == null) {
                                        str3 = ""
                                        }
                                    arr2 = arr
                                    i = i3
                                    val str6: String = str3
                                    i2 = length
                                    actions.add(new ConnectionManager.NotificationAction(actTitle, pendingIntent, pkg, str6, sbn.getId()))
                                    }
                                } else {
                                arr2 = arr
                                i = i3
                                i2 = length
                                }
                            i3 = i + 1
                            length = i2
                            arr = arr2
                            }
                        }
                    val currentTimeMillis: Long = System.currentTimeMillis()
                    val id: Int = sbn.getId()
                    val tag2: String = sbn.getTag()
                    val str7: String = (tag2 != null || (str2 = tag2.toString()) == null) ? "" : str2
                    val key: String = sbn.getKey()
                    Intrinsics.checkNotNullExpressionValue(key, "getKey(...)")
                    ConnectionManager.NotificationItem item = new ConnectionManager.NotificationItem(pkg, title, text2, currentTimeMillis, actions, id, str7, key)
                    ConnectionManager.INSTANCE.reportNotification(item)
                    persistNotification(item)
                    }
                }
            }
        text2 = text
        if (title.length() != 0) {
            }
        Log.d(TAG, "收到通知 [" + pkg + "] title=" + title + " text=" + text2)
        val actions2: List = new ArrayList()
        arr = n.actions
        if (arr != null) {
            }
        val currentTimeMillis2: Long = System.currentTimeMillis()
        val id2: Int = sbn.getId()
        val tag22: String = sbn.getTag()
        if (tag22 != null) {
            }
        val key2: String = sbn.getKey()
        Intrinsics.checkNotNullExpressionValue(key2, "getKey(...)")
        ConnectionManager.NotificationItem item2 = new ConnectionManager.NotificationItem(pkg, title, text2, currentTimeMillis2, actions2, id2, str7, key2)
        ConnectionManager.INSTANCE.reportNotification(item2)
        persistNotification(item2)
        }

    fun isNotBlacklisted(pkg: String): Boolean {
        val companion: Companion = INSTANCE
        val applicationContext: Context = getApplicationContext()
        Intrinsics.checkNotNullExpressionValue(applicationContext, "getApplicationContext(...)")
        val set: Set = companion.getBlacklist(applicationContext)
        return set.isEmpty() || !set.contains(pkg)
        }

    fun reportAllActiveNotifications(): Unit {
        try {
            val active: Array<StatusBarNotification> = getActiveNotifications()
            if (active == null) {
                return
                }
            Log.i(TAG, "上报当前所有活动通知: " + active.length + " 条")
            for (StatusBarNotification sbn : active) {
                processAndReport(sbn)
                }
            } catch (Exception e) {
            Log.e(TAG, "reportAllActiveNotifications failed", e)
            }
        }

    fun cancelNotificationByKey(key: String): Unit {
        Intrinsics.checkNotNullParameter(key, "key")
        try {
            cancelNotification(key)
            } catch (Exception e) {
            try {
                val parts: List = StringsKt.split$default((CharSequence) key, new String[]{"|"}, false, 0, 6, (Object) null)
                if (parts.size() >= 3) {
                    val pkg: String = (String) parts.get(0)
                    val tag: String = (String) parts.get(1)
                    val intOrNull: Integer = StringsKt.toIntOrNull((String) parts.get(2))
                    if (intOrNull == null) {
                        return
                        }
                    val id: Int = intOrNull.intValue()
                    cancelNotification(pkg, tag, id)
                    }
                val unit: Unit = Unit.INSTANCE
                } catch (Exception e2) {
                Integer.valueOf(Log.e(TAG, "cancelNotification failed", e2))
                }
            }
        }

    fun persistNotification(/* ConnectionManager.NotificationItem item */): Unit {
        try {
            val dir: File = new File(getExternalFilesDir(null), "NotificationCache")
            if (!dir.exists()) {
                dir.mkdirs()
                }
            val cutoff: Long = System.currentTimeMillis() - 604800000
            val listFiles: Array<File> = dir.listFiles()
            if (listFiles != null) {
                for (File file : listFiles) {
                    if (file.lastModified() < cutoff) {
                        file.delete()
                        }
                    }
                }
            val ts: String = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(new Date(item.getTimestamp()))
            val file2: File = new File(dir, ts + "_" + StringsKt.replace$default(item.getPackageName(), '.', '_', false, 4, (Object) null) + ".json")
            val json: JSONObject = new JSONObject()
            json.put("package", item.getPackageName())
            json.put(LinkHeader.Parameters.Title, item.getTitle())
            json.put(TextNotificationReceiver.EXTRA_TEXT, item.getText())
            json.put("timestamp", item.getTimestamp())
            val fileOutputStream: FileOutputStream = new FileOutputStream(file2)
            try {
                val it: FileOutputStream = fileOutputStream
                val jSONObject: String = json.toString()
                Intrinsics.checkNotNullExpressionValue(jSONObject, "toString(...)")
                val bytes: Array<Byte> = jSONObject.getBytes(Charsets.UTF_8)
                Intrinsics.checkNotNullExpressionValue(bytes, "getBytes(...)")
                it.write(bytes)
                val unit: Unit = Unit.INSTANCE
                CloseableKt.closeFinally(fileOutputStream, null)
                } finally {
                }
            } catch (Exception e) {
            Log.e(TAG, "persistNotification failed", e)
            }
        }
    }
