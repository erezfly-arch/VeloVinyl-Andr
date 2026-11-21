package com.erezfly.velovinyl

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.*
import android.location.*
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.*

class MainActivity : Activity(), TextToSpeech.OnInitListener {
 private val bg=Color.rgb(13,20,17); private val panel=Color.rgb(26,39,32); private val gold=Color.rgb(218,185,112); private val green=Color.rgb(91,207,139); private val muted=Color.rgb(167,181,171)
 private data class Track(val name:String,val uri:String)
 private val tracks=mutableListOf<Track>(); private var current=-1; private var player:MediaPlayer?=null
 private lateinit var bike:BikeView; private lateinit var titleView:TextView; private lateinit var playButton:Button; private lateinit var trackList:LinearLayout
 private lateinit var pages:LinearLayout; private lateinit var statsText:TextView; private lateinit var mapPage:LinearLayout; private lateinit var homePage:LinearLayout; private lateinit var waPage:LinearLayout
 private lateinit var map:MapView; private var locationOverlay:MyLocationNewOverlay?=null; private var tts:TextToSpeech?=null
 private var lastLocation:Location?=null; private var routePoints= mutableListOf<GeoPoint>(); private var routeLine:Polyline?=null; private var rideMeters=0.0; private var announcedKm=0; private var riding=false; private var destinationBox:EditText?=null
 private var locationManager:LocationManager?=null; private var pendingDestination:String?=null; private var routeRequested=false; private val locationListener=LocationListener { loc -> onGps(loc) }
 private val pickCode=701; private val contactCode=702; private val audioPerm=703
 override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState); window.statusBarColor=bg; window.navigationBarColor=bg; Configuration.getInstance().userAgentValue=packageName; loadTracks(); tts=TextToSpeech(this,this); buildUi() }
 override fun onInit(status:Int) { if(status==TextToSpeech.SUCCESS) tts?.language=Locale("he","IL") }
 private fun dp(v:Int)= (v*resources.displayMetrics.density).toInt()
 private fun label(s:String,size:Float,color:Int,bold:Boolean=false,center:Boolean=false)=TextView(this).apply { text=s; textSize=size; setTextColor(color); gravity=if(center) Gravity.CENTER else Gravity.START; if(bold) typeface=Typeface.DEFAULT_BOLD; setPadding(dp(4),dp(5),dp(4),dp(5)) }
 private fun card()=LinearLayout(this).apply { orientation=1; setPadding(dp(15),dp(12),dp(15),dp(12)); background=android.graphics.drawable.GradientDrawable().apply { setColor(panel); cornerRadius=dp(18).toFloat() } }
 private fun button(s:String,action:()->Unit)=Button(this).apply { text=s; isAllCaps=false; textSize=14f; setTextColor(Color.rgb(18,25,19)); backgroundTintList=android.content.res.ColorStateList.valueOf(gold); setOnClickListener { action() }; layoutParams=LinearLayout.LayoutParams(-2,dp(46)).apply { setMargins(dp(3),dp(5),dp(3),dp(5)) } }
 private fun buildUi() {
  val root=LinearLayout(this).apply { orientation=1; setBackgroundColor(bg) }
  val header=LinearLayout(this).apply { orientation=1; gravity=Gravity.CENTER; setPadding(dp(16),dp(12),dp(16),dp(6)) }
  header.addView(label("VELOVINYL",13f,gold,true,true)); header.addView(label("רוכבים על הקצב",24f,Color.WHITE,true,true)); header.addView(label("המוזיקה שלך. הדרך שלך.",13f,muted,false,true)); root.addView(header)
  val tabs=LinearLayout(this).apply { gravity=Gravity.CENTER; setPadding(dp(4),0,dp(4),dp(5)) }
  tabs.addView(button("האופניים והנגן"){ showPage(homePage) }); tabs.addView(button("מפה ורכיבה"){ showPage(mapPage) }); tabs.addView(button("הודעות"){ showPage(waPage) }); root.addView(tabs)
  pages=LinearLayout(this).apply { orientation=1 }; homePage=LinearLayout(this).apply { orientation=1 }; mapPage=LinearLayout(this).apply { orientation=1 }; waPage=LinearLayout(this).apply { orientation=1 }
  pages.addView(homePage); pages.addView(mapPage); pages.addView(waPage); root.addView(pages,LinearLayout.LayoutParams(-1,0,1f))
  buildHome(); buildMapPage(); buildWhatsAppPage(); showPage(homePage); setContentView(root)
 }
 private fun showPage(page:LinearLayout) { homePage.visibility=if(page===homePage) View.VISIBLE else View.GONE; mapPage.visibility=if(page===mapPage) View.VISIBLE else View.GONE; waPage.visibility=if(page===waPage) View.VISIBLE else View.GONE; if(page===mapPage) map.onResume(); if(page===waPage) refreshWhatsApp() }
 private fun scrollWrap(child:View):ScrollView=ScrollView(this).apply { addView(child); isFillViewport=true }
 private fun buildHome() {
  val c=LinearLayout(this).apply { orientation=1; setPadding(dp(16),dp(8),dp(16),dp(20)) }
  val hero=card(); bike=BikeView(this); hero.addView(bike,LinearLayout.LayoutParams(-1,dp(230))); hero.addView(label("האופניים שלך הופכים לתקליט כשהמוזיקה מתנגנת",12f,muted,false,true)); c.addView(hero)
  val playerCard=card(); playerCard.addView(label("נגן המוזיקה",19f,Color.WHITE,true)); titleView=label("לא נבחר שיר",16f,Color.WHITE,true,true); playerCard.addView(titleView)
  val controls=LinearLayout(this).apply { gravity=Gravity.CENTER }; controls.addView(button("⏮"){ previous() }); playButton=button("▶ נגן"){ togglePlay() }; controls.addView(playButton); controls.addView(button("⏭"){ next() }); playerCard.addView(controls)
  playerCard.addView(button("＋ הוספת שירים מהמכשיר"){ pickTracks() }); trackList=LinearLayout(this).apply { orientation=1 }; playerCard.addView(trackList); renderTracks(); c.addView(playerCard)
  val quick=card(); quick.addView(label("קיצורי דרך",18f,Color.WHITE,true)); val row=LinearLayout(this).apply { gravity=Gravity.CENTER }
  row.addView(button("WhatsApp"){ openWhatsApp() }); row.addView(button("התקשר ליפה שלי"){ callMyLove() }); quick.addView(row); quick.addView(button("פתח מסך רכיבה וניווט"){ showPage(mapPage) }); c.addView(quick)
  homePage.addView(scrollWrap(c),LinearLayout.LayoutParams(-1,-1))
 }
 private fun buildMapPage() {
  val c=LinearLayout(this).apply { orientation=1; setPadding(dp(12),dp(8),dp(12),dp(12)) }
  c.addView(label("מפת הרכיבה",20f,Color.WHITE,true))
  map=MapView(this); map.setTileSource(TileSourceFactory.MAPNIK); map.setMultiTouchControls(true); map.controller.setZoom(14.0); map.controller.setCenter(GeoPoint(32.015,34.78))
  c.addView(map,LinearLayout.LayoutParams(-1,dp(300)))
  val nav=card(); nav.addView(label("לאן רוכבים?",17f,Color.WHITE,true))
  destinationBox=EditText(this).apply { hint="הקלד כתובת או יעד"; setTextColor(Color.WHITE); setHintTextColor(muted); textSize=16f; setSingleLine(true); setPadding(dp(12),dp(8),dp(12),dp(8)); setBackgroundColor(Color.rgb(43,55,47)) }
  nav.addView(destinationBox,LinearLayout.LayoutParams(-1,dp(50)))
  val actions=LinearLayout(this).apply { gravity=Gravity.CENTER }
  actions.addView(button("התחל ניווט ורכיבה"){ beginNavigation() }); actions.addView(button("עצור"){ stopRide() }); nav.addView(actions)
  c.addView(nav)
  val stats=card(); stats.addView(label("נתוני רכיבה",16f,Color.WHITE,true)); statsText=label("מרחק: 0.00 ק״מ   •   ממתין ל-GPS",13f,muted); stats.addView(statsText); c.addView(stats)
  mapPage.addView(scrollWrap(c),LinearLayout.LayoutParams(-1,-1))
 }
 private fun buildWhatsAppPage() {
  val c=LinearLayout(this).apply { orientation=1; setPadding(dp(16),dp(12),dp(16),dp(20)) }
  val panel=card(); panel.addView(label("הודעות WhatsApp",20f,Color.WHITE,true)); panel.addView(label("הודעות שמגיעות כהתראות בלבד — עד 30 אחרונות.",12f,muted))
  panel.addView(button("הפעל הרשאת גישה להתראות"){ startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) })
  panel.addView(button("פתח WhatsApp"){ openWhatsApp() }); panel.addView(button("רענן הודעות"){ refreshWhatsApp() })
  val status=label("",12f,gold); status.tag="permission_status"; panel.addView(status)
  val list=LinearLayout(this).apply { orientation=1 }; list.tag="message_list"; panel.addView(list); c.addView(panel); waPage.addView(scrollWrap(c),LinearLayout.LayoutParams(-1,-1))
 }
 private fun refreshWhatsApp() {
  if(!::waPage.isInitialized) return
  val panel=waPage.getChildAt(0) as? ScrollView ?: return; val card=panel.getChildAt(0) as? LinearLayout ?: return
  val status=card.findViewWithTag<TextView>("permission_status"); val list=card.findViewWithTag<LinearLayout>("message_list")
  val enabled=NotificationAccess.isEnabled(this); status.text=if(enabled) "● הרשאת התראות פעילה" else "○ ההרשאה עדיין לא הופעלה"
  list.removeAllViews()
  val arr=org.json.JSONArray(getSharedPreferences("wa_notifications",MODE_PRIVATE).getString("items","[]"))
  if(arr.length()==0) list.addView(label("כאן יופיעו הודעות WhatsApp חדשות שיתקבלו כהתראה.",13f,muted))
  for(i in arr.length()-1 downTo 0) { val o=arr.optJSONObject(i)?:continue; val item=card(); item.addView(label(o.optString("title","WhatsApp"),14f,gold,true)); item.addView(label(o.optString("text",""),14f,Color.WHITE)); item.addView(label(java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(o.optLong("time"))),11f,muted)); list.addView(item); if(list.childCount>=30) break }
 }
 private fun openWhatsApp() { try { startActivity(packageManager.getLaunchIntentForPackage("com.whatsapp") ?: Intent(Intent.ACTION_VIEW,Uri.parse("https://wa.me/"))) } catch(e:Exception) { toast("WhatsApp לא מותקן") } }
 private fun callMyLove() {
  val prefs=getSharedPreferences("love_contact",MODE_PRIVATE); val saved=prefs.getString("number",null)
  if(!saved.isNullOrBlank()) { startActivity(Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+Uri.encode(saved)))); return }
  val pick=Intent(Intent.ACTION_PICK,ContactsContract.CommonDataKinds.Phone.CONTENT_URI); startActivityForResult(pick,contactCode)
 }
 private fun pickTracks() { val i=Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type="audio/*"; putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true) }; startActivityForResult(i,pickCode) }
 @Deprecated("Activity result")
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
  super.onActivityResult(requestCode,resultCode,data); if(resultCode!=RESULT_OK || data==null) return
  if(requestCode==contactCode) { val u=data.data?:return; val cur=contentResolver.query(u,arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),null,null,null); cur?.use { if(it.moveToFirst()) getSharedPreferences("love_contact",MODE_PRIVATE).edit().putString("number",it.getString(0)).apply() }; toast("המספר נשמר. לחיצה נוספת תחייג."); return }
  if(requestCode!=pickCode) return
  val uris=mutableListOf<Uri>(); data.clipData?.let { for(i in 0 until it.itemCount) uris.add(it.getItemAt(i).uri) }; if(uris.isEmpty()) data.data?.let { uris.add(it) }
  uris.forEach { u -> try { contentResolver.takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION); val n=contentResolver.query(u,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use { if(it.moveToFirst()) it.getString(0) else "שיר" } ?: "שיר"; if(tracks.none { t->t.uri==u.toString() }) tracks.add(Track(n,u.toString())) } catch(_:Exception){} }
  saveTracks(); renderTracks(); if(current<0 && tracks.isNotEmpty()) selectTrack(0)
 }
 private fun renderTracks() {
  if(!::trackList.isInitialized) return; trackList.removeAllViews()
  if(tracks.isEmpty()) { trackList.addView(label("עדיין אין שירים בספרייה.",12f,muted)); return }
  tracks.forEachIndexed { i,t -> val r=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }; r.addView(label(t.name,13f,Color.WHITE).apply { maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END },LinearLayout.LayoutParams(0,dp(42),1f)); r.addView(button("▶"){ current=i; startTrack() }); trackList.addView(r) }
 }
 private fun selectTrack(i:Int) { if(i !in tracks.indices) return; current=i; titleView.text=tracks[i].name }
 private fun startTrack() { if(current !in tracks.indices) { toast("בחר שיר"); return }; try { player?.release(); player=MediaPlayer().apply { setDataSource(this@MainActivity,Uri.parse(tracks[current].uri)); setOnCompletionListener { next() }; prepare(); start() }; playButton.text="Ⅱ השהה"; bike.setPlaying(true) } catch(_:Exception) { toast("לא ניתן לנגן את הקובץ"); bike.setPlaying(false) } }
 private fun togglePlay() { val p=player; if(p?.isPlaying==true) { p.pause(); playButton.text="▶ נגן"; bike.setPlaying(false) } else if(p!=null) { p.start(); playButton.text="Ⅱ השהה"; bike.setPlaying(true) } else startTrack() }
 private fun next() { if(tracks.isNotEmpty()) { current=(current+1)%tracks.size; selectTrack(current); startTrack() } }
 private fun previous() { if(tracks.isNotEmpty()) { current=if(current<=0) tracks.lastIndex else current-1; selectTrack(current); startTrack() } }
 private fun beginNavigation() {
  val dest=destinationBox?.text?.toString()?.trim().orEmpty(); if(dest.isBlank()) { toast("הקלד יעד"); return }
  if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) { ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION),audioPerm); return }
  val lm=getSystemService(LOCATION_SERVICE) as LocationManager; locationManager=lm
  try { val last=lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER); if(last!=null) onGps(last) } catch(_:Exception){}
  try { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,1500L,2f,locationListener) } catch(_:SecurityException){}
  riding=true; announcedKm=0; rideMeters=0.0; lastLocation=null
  tts?.speak("הניווט והרכיבה התחילו. סע בזהירות.",TextToSpeech.QUEUE_FLUSH,null,"start")
  thread { try { val address=URLEncoder.encode(dest,"UTF-8"); val geocoder=Geocoder(this,Locale("he","IL")); val results=geocoder.getFromLocationName(dest,1); runOnUiThread { if(results.isNullOrEmpty()) toast("לא מצאתי את היעד במפה") else { val p=GeoPoint(results[0].latitude,results[0].longitude); map.controller.animateTo(p); map.controller.setZoom(15.0) } } } catch(_:Exception){} }
  updateStats()
 }
 private fun onGps(loc:Location) {
  val p=GeoPoint(loc.latitude,loc.longitude); if(routePoints.isNotEmpty()) { val prev=lastLocation; if(prev!=null) { val d=prev.distanceTo(loc); if(d<150) rideMeters+=d }; routePoints.add(p); if(routePoints.size>1000) routePoints.removeAt(0); routeLine?.let { map.overlays.remove(it) }; routeLine=Polyline().apply { setPoints(routePoints); outlinePaint.color=gold; outlinePaint.strokeWidth=dp(5).toFloat() }; map.overlays.add(routeLine) }
  lastLocation=loc; if(riding && !routeRequested && pendingDestination!=null) { routeRequested=true; val dest=pendingDestination!!; thread { try { val geocoder=Geocoder(this,Locale("he","IL")); val found=geocoder.getFromLocationName(dest,1); if(!found.isNullOrEmpty()) fetchBikeRoute(loc,GeoPoint(found[0].latitude,found[0].longitude)) } catch(_:Exception){} } }; if(::map.isInitialized) { map.controller.animateTo(p); map.invalidate() }
  if(riding) { val km=(rideMeters/1000).toInt(); if(km>announcedKm) { announcedKm=km; tts?.speak("עברתם $km קילומטרים",TextToSpeech.QUEUE_ADD,null,"km$km") }; updateStats() }
 }
 private fun updateStats() {
  if(!::mapPage.isInitialized) return
  val sv=mapPage.findViewWithTag<TextView>("stats") ?: return
  sv.text="מרחק: %.2f ק״מ   •   GPS: %s".format(rideMeters/1000,if(lastLocation!=null) "מחובר" else "ממתין למיקום")
 }
 private fun stopRide() { riding=false; try { locationManager?.removeUpdates(locationListener) } catch(_:Exception){}; tts?.speak("הרכיבה הסתיימה",TextToSpeech.QUEUE_FLUSH,null,"stop") }
 override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray) { super.onRequestPermissionsResult(requestCode,permissions,grantResults); if(requestCode==audioPerm && grantResults.any { it==PackageManager.PERMISSION_GRANTED }) beginNavigation() else if(requestCode==audioPerm) toast("צריך לאשר מיקום כדי להפעיל GPS") }
 private fun saveTracks() { val a=JSONArray(); tracks.forEach { a.put(JSONObject().put("name",it.name).put("uri",it.uri)) }; getSharedPreferences("library",MODE_PRIVATE).edit().putString("tracks",a.toString()).apply() }
 private fun loadTracks() { try { val a=JSONArray(getSharedPreferences("library",MODE_PRIVATE).getString("tracks","[]")); for(i in 0 until a.length()) { val o=a.getJSONObject(i); tracks.add(Track(o.getString("name"),o.getString("uri"))) } } catch(_:Exception){} }
 private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()
 override fun onResume() { super.onResume(); if(::map.isInitialized) map.onResume(); if(::waPage.isInitialized && waPage.visibility==View.VISIBLE) refreshWhatsApp() }
 override fun onPause() { if(::map.isInitialized) map.onPause(); super.onPause() }
 override fun onDestroy() { stopRide(); player?.release(); player=null; tts?.shutdown(); if(::map.isInitialized) map.onDetach(); super.onDestroy() }

 inner class BikeView(ctx:Context):View(ctx) {
  private val paint=Paint(3); private var angle=0f; private var playing=false; private var anim:ValueAnimator?=null
  fun setPlaying(v:Boolean) { playing=v; anim?.cancel(); if(v) { anim=ValueAnimator.ofFloat(angle,angle+360f).apply { duration=1800; repeatCount=ValueAnimator.INFINITE; interpolator=LinearInterpolator(); addUpdateListener { angle=it.animatedValue as Float; invalidate() }; start() } } else { angle=0f; invalidate() } }
  override fun onDraw(c:Canvas) { super.onDraw(c); val w=width.toFloat(); val h=height.toFloat(); val y=h*.67f; val r=min(w*.19f,h*.28f); val x1=w*.25f; val x2=w*.75f
   fun line(x:Float,y:Float,a:Float,b:Float,col:Int,sw:Float=dp(4).toFloat()) { paint.color=col; paint.strokeWidth=sw; paint.style=Paint.Style.STROKE; paint.strokeCap=Paint.Cap.ROUND; c.drawLine(x,y,a,b,paint) }
   fun circle(x:Float,y:Float,rad:Float,col:Int,sw:Float,fill:Boolean=false) { paint.color=col; paint.strokeWidth=sw; paint.style=if(fill) Paint.Style.FILL else Paint.Style.STROKE; c.drawCircle(x,y,rad,paint) }
   val frame=gold
   for(x in listOf(x1,x2)) { if(playing) { c.save(); c.rotate(angle,x,y); circle(x,y,r,Color.BLACK,dp(5).toFloat()); circle(x,y,r*.88f,Color.DKGRAY,dp(2).toFloat()); for(k in 0..7) { val a=k*PI/4; line(x+cos(a).toFloat()*r*.18f,y+sin(a).toFloat()*r*.18f,x+cos(a).toFloat()*r*.82f,y+sin(a).toFloat()*r*.82f,Color.GRAY,dp(1).toFloat()) }; circle(x,y,r*.16f,gold,dp(3).toFloat(),true); circle(x,y,r*.06f,Color.BLACK,dp(1).toFloat(),true); c.restore() } else { circle(x,y,r,frame,dp(4).toFloat()); circle(x,y,r*.07f,frame,dp(3).toFloat(),true); for(k in 0..11) { val a=k*PI/6; line(x,y,x+cos(a).toFloat()*r*.94f,y+sin(a).toFloat()*r*.94f,frame,dp(1).toFloat()) } } }
   val seatX=w*.47f; val seatY=h*.29f; val crankX=w*.51f; val crankY=y-r*.02f; val headX=w*.68f; val headY=h*.35f
   line(x1,y,seatX,seatY,frame); line(seatX,seatY,crankX,crankY,frame); line(crankX,crankY,x1,y,frame); line(seatX,seatY,headX,headY,frame); line(headX,headY,x2,y,frame); line(crankX,crankY,x2,y,frame); line(seatX,seatY,seatX-dp(14),seatY,frame,dp(6).toFloat()); line(headX,headY,headX+dp(12),headY-dp(20),frame); line(headX+dp(12),headY-dp(20),headX+dp(35),headY-dp(20),frame,dp(5).toFloat()); line(seatX,seatY,seatX-dp(5),seatY-dp(23),frame); line(seatX-dp(5),seatY-dp(23),seatX+dp(17),seatY-dp(23),frame,dp(6).toFloat())
   paint.style=Paint.Style.FILL; paint.color=gold; c.drawRoundRect(w*.48f,h*.43f,w*.53f,h*.57f,dp(4).toFloat(),dp(4).toFloat(),paint)
   paint.color=muted; paint.textSize=dp(11).toFloat(); paint.textAlign=Paint.Align.CENTER; c.drawText(if(playing) "VINYL MODE • מתנגן" else "VELOVINYL • מוכן לרכיבה",w/2,h-dp(8).toFloat(),paint)
  }
 }
}
