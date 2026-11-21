package com.erezfly.velovinyl
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject
class WhatsAppNotificationListener : NotificationListenerService() {
 override fun onListenerConnected() { super.onListenerConnected(); sendBroadcast(android.content.Intent("com.erezfly.velovinyl.WA_UPDATED").setPackage(packageName)) }
 override fun onNotificationPosted(sbn: StatusBarNotification) {
  if (sbn.packageName != "com.whatsapp" && sbn.packageName != "com.whatsapp.w4b") return
  val e=sbn.notification.extras
  val title=e.getCharSequence("android.title")?.toString().orEmpty()
  val body=(e.getCharSequence("android.text") ?: e.getCharSequence("android.bigText"))?.toString().orEmpty()
  if(title.isBlank() && body.isBlank()) return
  val p=getSharedPreferences("wa_notifications", MODE_PRIVATE)
  val a=JSONArray(p.getString("items","[]"))
  a.put(JSONObject().put("title",title).put("text",body).put("time",sbn.postTime))
  val out=JSONArray(); for(i in maxOf(0,a.length()-30) until a.length()) out.put(a.getJSONObject(i))
  p.edit().putString("items",out.toString()).apply(); sendBroadcast(android.content.Intent("com.erezfly.velovinyl.WA_UPDATED").setPackage(packageName))
 }
}
