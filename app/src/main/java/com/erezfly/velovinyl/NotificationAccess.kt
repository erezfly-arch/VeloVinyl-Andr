package com.erezfly.velovinyl
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
object NotificationAccess { fun isEnabled(c:Context):Boolean { val raw=Settings.Secure.getString(c.contentResolver,"enabled_notification_listeners")?:return false; val name=ComponentName(c,WhatsAppNotificationListener::class.java).flattenToString(); return raw.split(":").any{it.equals(name,true)} } }
