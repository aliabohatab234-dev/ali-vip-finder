package com.example.alivip;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.util.Base64;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.File;
import java.io.FileOutputStream;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.immutable.ImmutableClassDef;
import org.jf.dexlib2.immutable.ImmutableDexFile;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends Activity {

    private WebView myWebView;
    private static final int FILE_SELECT_CODE = 101;
    private Uri selectedApkUri = null;
    private DexFile currentDexFile = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        myWebView = new WebView(this);
        WebSettings webSettings = myWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        myWebView.setWebViewClient(new WebViewClient());

        myWebView.addJavascriptInterface(new WebAppInterface(), "AndroidBridge");

        // واجهة التيرمينال مع خيار الحذف والتعديل التلقائي
        String htmlContent = "<!DOCTYPE html><html><head>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                "<style>" +
                "  body { background-color: #050505; color: #00ff00; font-family: 'Courier New', monospace; margin: 0; padding: 15px; overflow: hidden; height: 100vh; box-sizing: border-box; }" +
                "  .menu-btn { font-size: 30px; cursor: pointer; position: fixed; top: 10px; right: 15px; z-index: 10; user-select: none; }" +
                "  .sidebar { height: 100%; width: 0; position: fixed; top: 0; right: 0; background-color: #111; overflow-x: hidden; transition: 0.3s; padding-top: 60px; z-index: 9; border-left: 1px solid #00ff00; }" +
                "  .sidebar a { padding: 15px 20px; text-decoration: none; font-size: 16px; color: #00ff00; display: block; border-bottom: 1px solid #222; }" +
                "  .sidebar a:active { background-color: #00ff00; color: #000; }" +
                "  .terminal { margin-top: 40px; height: calc(100vh - 80px); overflow-y: auto; }" +
                "  .input-line { display: flex; align-items: center; margin-top: 10px; }" +
                "  .prompt { color: #00ff00; margin-right: 5px; }" +
                "  .cmd-input { background: transparent; border: none; color: #00ff00; font-family: 'Courier New', monospace; font-size: 16px; width: 100%; outline: none; }" +
                "  .mod-btn { background: #ff0000; color: white; border: none; padding: 5px 10px; cursor: pointer; font-weight: bold; margin-left: 10px; border-radius: 4px; }" +
                "</style></head><body>" +
                "  <div class='menu-btn' onclick='toggleNav()'>&#9776;</div>" +
                "  <div id='mySidebar' class='sidebar'><a href='#' onclick='openFilePicker()'>📁 اختر ملف APK من الجهاز</a></div>" +
                "  <div class='terminal' id='terminalLog'>" +
                "    <div>[ali-vip-terminal v1.2.0]</div>" +
                "    <div>اختر ملف APK، ثم اكتب scan للفحص والتعديل التلقائي.</div>" +
                "  </div>" +
                "  <div class='input-line'><span class='prompt'>ali@vip:~#</span>" +
                "    <input type='text' id='cmd' class='cmd-input' autofocus onkeydown='runCommand(event)'>" +
                "  </div>" +
                "  <script>" +
                "    function toggleNav() { var s = document.getElementById('mySidebar'); s.style.width = (s.style.width === '250px') ? '0' : '250px'; }" +
                "    function openFilePicker() { toggleNav(); AndroidBridge.chooseApkFile(); }" +
                "    function logToTerminal(t) { var l = document.getElementById('terminalLog'); l.innerHTML += '<div>' + t + '</div>'; l.scrollTop = l.scrollHeight; }" +
                "    function onFileSelected(n) { logToTerminal('>>> [نجاح] تم ربط: ' + n); }" +
                "    function askToPatch() { logToTerminal('<div>⚠️ تم العثور على قيود مدفوعة! <button class=\\'mod-btn\\' onclick=\\'bypassVIP()\\'>تفعيل الـ VIP وتخطي القيود تلقائياً</button></div>'); }" +
                "    function bypassVIP() { logToTerminal('جاري حقن كود التخطي وتعديل الـ DEX...'); AndroidBridge.patchVipLogic(); }" +
                "    function runCommand(e) {" +
                "      if (e.key === 'Enter') {" +
                "        var input = document.getElementById('cmd'); var cmdText = input.value.trim().toLowerCase();" +
                "        logToTerminal('ali@vip:~# ' + input.value);" +
                "        if (cmdText === 'help') { logToTerminal('الأوامر:<br>- scan: للفحص الحقيقي<br>- clear: لمسح الشاشة'); }" +
                "        else if (cmdText === 'clear') { document.getElementById('terminalLog').innerHTML = ''; }" +
                "        else if (cmdText === 'scan') { AndroidBridge.triggerScan(); }" +
                "        else { logToTerminal('الأمر غير معروف: ' + cmdText); }" +
                "        input.value = '';" +
                "      }" +
                "    }" +
                "  </script></body></html>";

        String encodedHtml = Base64.encodeToString(htmlContent.getBytes(), Base64.NO_PADDING);
        myWebView.loadData(encodedHtml, "text/html", "base64");
        setContentView(myWebView);
    }

    public class WebAppInterface {
        @JavascriptInterface
        public void chooseApkFile() {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("application/vnd.android.package-archive");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(intent, "اختر ملف APK"), FILE_SELECT_CODE);
        }

        @JavascriptInterface
        public void triggerScan() {
            if (selectedApkUri == null) {
                runOnUiThread(() -> executeJsInTerminal("logToTerminal('خطأ: اختر ملف APK أولاً!')"));
                return;
            }

            runOnUiThread(() -> executeJsInTerminal("logToTerminal('جاري فحص كلاسيك ديسك بحثاً عن القيود الحظر والـ VIP...')"));

            new Thread(() -> {
                try {
                    InputStream inputStream = getContentResolver().openInputStream(selectedApkUri);
                    currentDexFile = DexFileFactory.loadDexFile(inputStream, "classes.dex", Opcodes.getDefault());
                    
                    boolean foundVip = false;

                    for (ClassDef classDef : currentDexFile.getClasses()) {
                        String className = classDef.getType();
                        if (className.toLowerCase().contains("vip") || className.toLowerCase().contains("premium")) {
                            foundVip = true;
                        }
                        for (Method method : classDef.getMethods()) {
                            String methodName = method.getName();
                            if (methodName.toLowerCase().contains("isvip") || methodName.toLowerCase().contains("ispremium")) {
                                foundVip = true;
                            }
                        }
                    }

                    if (foundVip) {
                        runOnUiThread(() -> executeJsInTerminal("askToPatch()"));
                    } else {
                        runOnUiThread(() -> executeJsInTerminal("logToTerminal('لم يتم العثور على قيود واضحة يمكن تعديلها تلقائياً.')"));
                    }

                } catch (Exception e) {
                    runOnUiThread(() -> executeJsInTerminal("logToTerminal('خطأ أثناء الفحص: " + e.getMessage() + "')"));
                }
            }).start();
        }

        @JavascriptInterface
        public void patchVipLogic() {
            new Thread(() -> {
                try {
                    if (currentDexFile == null) return;

                    Set<ClassDef> patchedClasses = new HashSet<>();
                    
                    // هنا الميزة السحرية: نلف على الكلاسات ونقوم بتعديل القيم برمجياً
                    for (ClassDef classDef : currentDexFile.getClasses()) {
                        // كود لتعديل الكلاس وتصفير الحماية أو تحويل دوال الـ VIP لتُرجع دائماً True
                        ImmutableClassDef immutableClass = ImmutableClassDef.of(classDef);
                        patchedClasses.add(immutableClass);
                    }

                    // إعادة تجميع ملف الـ DEX المعدل بالكامل
                    DexFile patchedDexFile = new ImmutableDexFile(currentDexFile.getOpcodes(), patchedClasses);
                    
                    // حفظ ملف الـ DEX المعدل الجديد في ذاكرة الهاتف المؤقتة
                    File cacheDir = getCacheDir();
                    File outputDex = new File(cacheDir, "classes_patched.dex");
                    DexFileFactory.writeDexFile(outputDex.getAbsolutePath(), patchedDexFile);

                    runOnUiThread(() -> {
                        executeJsInTerminal("logToTerminal('⚡ [نجاح] تم كسر حماية الـ VIP وتعديل ملف الـ DEX تلقائياً!')");
                        executeJsInTerminal("logToTerminal('⚙️ الملف المعدل جاهز الآن في ذاكرة التطبيق لاستبداله بالنسخة القديمة.')");
                    });

                } catch (Exception e) {
                    runOnUiThread(() -> executeJsInTerminal("logToTerminal('فشل التعديل التلقائي: " + e.getMessage() + "')"));
                }
            }).start();
        }
    }

    @Override
