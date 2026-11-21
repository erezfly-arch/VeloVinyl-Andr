package com.erezfly.velovinyl
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import org.json.JSONArray
class MainActivity:Activity(){
 private lateinit var web:WebView
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
  window.statusBarColor=Color.rgb(16,37,29);window.navigationBarColor=Color.rgb(16,37,29)
  web=WebView(this);web.settings.javaScriptEnabled=true;web.settings.domStorageEnabled=true;web.settings.mediaPlaybackRequiresUserGesture=false;web.settings.allowFileAccess=false
  web.addJavascriptInterface(Bridge(),"VeloVinylNative");web.webChromeClient=WebChromeClient()
  web.webViewClient=object:WebViewClient(){override fun onPageFinished(v:WebView,url:String){injectPanel()}}
  setContentView(FrameLayout(this).apply{addView(web,FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT))})
  web.loadUrl("https://velovinyl.vercel.app/")
 }
 private fun injectPanel(){val js="""(function(){
 if(document.getElementById('vv-native-wa'))return;
 const s=document.createElement('style');s.textContent='#vv-native-wa{direction:rtl;background:#fff;border:1px solid #dfe8e1;border-radius:18px;padding:14px;margin:12px 16px;color:#183126;font:600 15px system-ui;box-shadow:0 4px 16px #173b2415;position:relative;z-index:999}#vv-native-wa button{border:0;border-radius:12px;padding:12px;background:#16834c;color:white;font-weight:700;margin:8px 4px 0 0}#vv-native-wa .vv-muted{font-size:13px;color:#728279;font-weight:400;margin-top:6px}#vv-native-wa .vv-item{padding:8px;border-top:1px solid #e7f4eb;font-weight:400;font-size:13px}';document.head.appendChild(s);
 const p=document.createElement('section');p.id='vv-native-wa';p.innerHTML='<div>💬 הודעות WhatsApp <span id="vv-wa-state" style="float:left">בודק הרשאה…</span></div><div class="vv-muted">אפשר להציג כאן הודעות WhatsApp לאחר אישור גישה להתראות Android.</div><button id="vv-wa-enable">הפעלת הרשאה</button><button id="vv-wa-refresh" style="background:#e7f4eb;color:#16834c">רענון הודעות</button><div id="vv-wa-list"></div>';
 const found=[...document.querySelectorAll('body *')].find(e=>e.children.length===0&&(e.textContent||'').includes('הודעות WhatsApp נכנסות'));
 if(found){let n=found;for(let i=0;i<5&&n.parentElement;i++)n=n.parentElement;n.style.display='none';n.insertAdjacentElement('afterend',p)}else document.body.insertBefore(p,document.body.firstChild);
 const esc=x=>String(x||'').replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
 function refresh(){try{let on=VeloVinylNative.isEnabled();let st=document.getElementById('vv-wa-state');st.textContent=on?'ההרשאה פעילה':'נדרשת הרשאה';st.style.color=on?'#16834c':'#c18b2b';let a=JSON.parse(VeloVinylNative.getNotifications());document.getElementById('vv-wa-list').innerHTML=a.slice().reverse().slice(0,8).map(x=>'<div class="vv-item"><b>'+esc(x.title)+'</b><br>'+esc(x.text)+'</div>').join('')||'<div class="vv-muted">עדיין אין התראות WhatsApp חדשות.</div>'}catch(e){}}
 document.getElementById('vv-wa-enable').onclick=()=>VeloVinylNative.openSettings();document.getElementById('vv-wa-refresh').onclick=refresh;refresh();window.addEventListener('focus',refresh);setInterval(refresh,5000);
 })();""";web.evaluateJavascript(js,null)}
 inner class Bridge{
  @JavascriptInterface fun isEnabled()=NotificationAccess.isEnabled(this@MainActivity)
  @JavascriptInterface fun getNotifications():String=getSharedPreferences("wa_notifications",MODE_PRIVATE).getString("items","[]")?:"[]"
  @JavascriptInterface fun openSettings(){runOnUiThread{startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))}}
 }
 @Suppress("DEPRECATION") override fun onBackPressed(){if(web.canGoBack())web.goBack()else super.onBackPressed()}
}
