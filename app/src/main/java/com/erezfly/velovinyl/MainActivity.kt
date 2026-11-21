package com.erezfly.velovinyl

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    private val bg=Color.rgb(12,19,16); private val panel=Color.rgb(25,36,30)
    private val green=Color.rgb(74,205,130); private val muted=Color.rgb(161,177,166)
    private data class Track(val name:String,val uri:String)
    private val tracks=mutableListOf<Track>(); private var current=-1
    private var player:MediaPlayer?=null; private var spin:ObjectAnimator?=null
    private lateinit var vinyl:TextView; private lateinit var titleView:TextView
    private lateinit var statusView:TextView; private lateinit var playButton:Button
    private lateinit var trackList:LinearLayout
    private val pickCode=701
    override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState); window.statusBarColor=bg; window.navigationBarColor=bg; loadTracks(); buildUi() }
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun label(s:String,size:Float,color:Int,bold:Boolean=false,gravity:Int=Gravity.START)=TextView(this).apply { text=s; textSize=size; setTextColor(color); this.gravity=gravity; if(bold) typeface=Typeface.DEFAULT_BOLD; setPadding(dp(4),dp(5),dp(4),dp(5)) }
    private fun card()=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(14),dp(16),dp(14)); setBackgroundColor(panel) }
    private fun button(s:String,action:()->Unit)=Button(this).apply { text=s; textSize=14f; isAllCaps=false; setTextColor(Color.rgb(8,27,17)); backgroundTintList=android.content.res.ColorStateList.valueOf(green); setOnClickListener { action() }; layoutParams=LinearLayout.LayoutParams(-2,dp(48)).apply { setMargins(dp(4),dp(8),dp(4),dp(4)) } }
    private fun buildUi() {
        val scroll=ScrollView(this)
        val content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(18),dp(20),dp(28)); setBackgroundColor(bg) }
        content.addView(label("VELOVINYL  ♫",13f,green,true,Gravity.CENTER))
        content.addView(label("המוזיקה שלך. הדרך שלך.",25f,Color.WHITE,true,Gravity.CENTER))
        content.addView(label("נגן מוזיקה עצמאי",14f,muted,false,Gravity.CENTER))
        val pc=card(); vinyl=label("◉",138f,Color.rgb(220,224,217),false,Gravity.CENTER).apply { includeFontPadding=false }
        pc.addView(vinyl,LinearLayout.LayoutParams(-1,dp(190)))
        titleView=label("לא נבחר שיר",18f,Color.WHITE,true,Gravity.CENTER); pc.addView(titleView)
        statusView=label("מוכן לנגן",13f,muted,false,Gravity.CENTER); pc.addView(statusView)
        val controls=LinearLayout(this).apply { gravity=Gravity.CENTER }
        controls.addView(button("⏮"){ previous() }); playButton=button("▶ נגן"){ togglePlay() }; controls.addView(playButton); controls.addView(button("⏭"){ next() }); pc.addView(controls); content.addView(pc)
        content.addView(label("ספריית המוזיקה",19f,Color.WHITE,true).apply { setPadding(0,dp(22),0,dp(8)) })
        content.addView(button("＋ הוספת שירים מהמכשיר"){ pickTracks() })
        trackList=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }; content.addView(trackList); renderTracks()
        content.addView(label("ניווט",19f,Color.WHITE,true).apply { setPadding(0,dp(22),0,dp(8)) })
        val nav=card(); nav.addView(label("הקלד יעד ופתח ניווט קולי",15f,Color.WHITE,true))
        val destination=EditText(this).apply { hint="לאן נוסעים?"; textSize=16f; setTextColor(Color.WHITE); setHintTextColor(muted); setSingleLine(true); setPadding(dp(12),dp(12),dp(12),dp(12)); setBackgroundColor(Color.rgb(38,51,43)) }
        nav.addView(destination,LinearLayout.LayoutParams(-1,dp(52)))
        nav.addView(button("נווט עם Google Maps  ↗") {
            val q=destination.text.toString().trim()
            if(q.isEmpty()) { toast("יש להזין יעד"); return@button }
            val uri=Uri.parse("google.navigation:q="+Uri.encode(q)+"&mode=d")
            try { startActivity(Intent(Intent.ACTION_VIEW,uri).setPackage("com.google.android.apps.maps")) }
            catch(e:Exception) { startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/maps/dir/?api=1&destination="+Uri.encode(q)))) }
        })
        nav.addView(label("ההנחיות הקוליות יושמעו ב-Google Maps.",12f,muted)); content.addView(nav)
        content.addView(label("VeloVinyl • אפליקציית Android",11f,muted,false,Gravity.CENTER).apply { setPadding(0,dp(22),0,0) })
        scroll.addView(content); setContentView(scroll)
    }
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()
    private fun pickTracks() { val i=Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type="audio/*"; putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true) }; startActivityForResult(i,pickCode) }
    @Deprecated("Document picker callback")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data); if(requestCode!=pickCode || resultCode!=RESULT_OK || data==null) return
        val uris=mutableListOf<Uri>(); data.clipData?.let { c -> for(i in 0 until c.itemCount) uris.add(c.getItemAt(i).uri) }; if(uris.isEmpty()) data.data?.let { uris.add(it) }
        uris.forEach { u -> try {
            contentResolver.takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val n=contentResolver.query(u,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use { if(it.moveToFirst()) it.getString(0) else "שיר" } ?: "שיר"
            if(tracks.none { t -> t.uri==u.toString() }) tracks.add(Track(n,u.toString()))
        } catch(e:Exception) {} }
        saveTracks(); renderTracks(); if(current<0 && tracks.isNotEmpty()) selectTrack(0)
    }
    private fun renderTracks() {
        if(!::trackList.isInitialized) return; trackList.removeAllViews()
        if(tracks.isEmpty()) { trackList.addView(label("עדיין אין שירים. הוסף שירים מהמכשיר.",13f,muted)); return }
        tracks.forEachIndexed { i,t ->
            val row=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL; setPadding(dp(8),dp(4),dp(4),dp(4)); setBackgroundColor(panel) }
            row.addView(TextView(this).apply { text=(i+1).toString()+".  "+t.name; textSize=14f; setTextColor(Color.WHITE); maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END },LinearLayout.LayoutParams(0,dp(48),1f))
            row.addView(button("▶"){ selectTrack(i); startTrack() }); trackList.addView(row)
        }
    }
    private fun selectTrack(i:Int) { if(i !in tracks.indices) return; current=i; titleView.text=tracks[i].name; statusView.text="נבחר" }
    private fun startTrack() {
        if(current !in tracks.indices) { toast("בחר שיר מהרשימה"); return }
        try { player?.release(); player=MediaPlayer().apply { setDataSource(this@MainActivity,Uri.parse(tracks[current].uri)); setOnCompletionListener { next() }; prepare(); start() }; playButton.text="Ⅱ השהה"; statusView.text="מתנגן עכשיו"; startSpin() }
        catch(e:Exception) { toast("לא ניתן לנגן את הקובץ הזה"); stopSpin() }
    }
    private fun togglePlay() {
        val p=player
        if(p?.isPlaying==true) { p.pause(); playButton.text="▶ נגן"; statusView.text="מושהה"; stopSpin() }
        else if(p!=null) { p.start(); playButton.text="Ⅱ השהה"; statusView.text="מתנגן עכשיו"; startSpin() } else startTrack()
    }
    private fun next() { if(tracks.isNotEmpty()) { current=(current+1)%tracks.size; selectTrack(current); startTrack() } }
    private fun previous() { if(tracks.isNotEmpty()) { current=if(current<=0) tracks.lastIndex else current-1; selectTrack(current); startTrack() } }
    private fun startSpin() { stopSpin(); spin=ObjectAnimator.ofFloat(vinyl,View.ROTATION,0f,360f).apply { duration=2400; repeatCount=ValueAnimator.INFINITE; interpolator=LinearInterpolator(); start() } }
    private fun stopSpin() { spin?.cancel(); spin=null; if(::vinyl.isInitialized) vinyl.rotation=0f }
    private fun saveTracks() { val a=JSONArray(); tracks.forEach { a.put(JSONObject().put("name",it.name).put("uri",it.uri)) }; getSharedPreferences("library",MODE_PRIVATE).edit().putString("tracks",a.toString()).apply() }
    private fun loadTracks() { try { val a=JSONArray(getSharedPreferences("library",MODE_PRIVATE).getString("tracks","[]")); for(i in 0 until a.length()) { val o=a.getJSONObject(i); tracks.add(Track(o.getString("name"),o.getString("uri"))) } } catch(e:Exception) {} }
    override fun onDestroy() { stopSpin(); player?.release(); player=null; super.onDestroy() }
}