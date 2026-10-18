package tv.drive.dashboard;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.JavascriptInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;
import android.media.MediaPlayer;
import android.widget.Toast;
import java.io.File;
import java.io.IOException;

public class MainActivity extends Activity {
    private WebView webView;
    private UploadServer uploadServer;
    private MediaPlayer player;
    private File currentAudio;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                             WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(8, 11, 16));
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new Bridge(), "DriveTV");
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");

        try {
            uploadServer = new UploadServer(this);
            uploadServer.start();
        } catch (Exception ignored) {}
    }

    private String lanIp() {
        try {
            WifiManager wm = (WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
            int ip = wm.getConnectionInfo().getIpAddress();
            return Formatter.formatIpAddress(ip);
        } catch (Exception e) { return "TV_IP_UNAVAILABLE"; }
    }

    public class Bridge {
        @JavascriptInterface public String getUploadUrl() {
            return "http://" + lanIp() + ":8787/";
        }
        @JavascriptInterface public String getLocalAudioName() {
            return currentAudio == null ? "" : currentAudio.getName();
        }
        @JavascriptInterface public void playLocalAudio() {
            runOnUiThread(() -> {
                if (currentAudio == null || !currentAudio.exists()) {
                    Toast.makeText(MainActivity.this, "Send a music file from your iPad first.", Toast.LENGTH_LONG).show();
                    return;
                }
                try {
                    if (player != null) { player.release(); player = null; }
                    player = new MediaPlayer();
                    player.setDataSource(currentAudio.getAbsolutePath());
                    player.setOnPreparedListener(MediaPlayer::start);
                    player.setOnCompletionListener(mp -> {
                        mp.release();
                        player = null;
                    });
                    player.prepareAsync();
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Could not play this audio format.", Toast.LENGTH_LONG).show();
                }
            });
        }
        @JavascriptInterface public void pauseAudio() {
            runOnUiThread(() -> { if (player != null && player.isPlaying()) player.pause(); });
        }
        @JavascriptInterface public void resumeAudio() {
            runOnUiThread(() -> { if (player != null) player.start(); });
        }
        @JavascriptInterface public void stopAudio() {
            runOnUiThread(() -> {
                if (player != null) { player.stop(); player.release(); player = null; }
            });
        }
        @JavascriptInterface public boolean launchPackage(String packageName) {
            try {
                Intent i = getPackageManager().getLaunchIntentForPackage(packageName);
                if (i == null) return false;
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                return true;
            } catch (Exception e) { return false; }
        }
        @JavascriptInterface public boolean packageInstalled(String packageName) {
            try {
                getPackageManager().getPackageInfo(packageName, 0);
                return true;
            } catch (Exception e) { return false; }
        }
        @JavascriptInterface public void openUrl(String url) {
            runOnUiThread(() -> {
                try {
                    Uri uri = Uri.parse(url);
                    if (!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme())) return;
                    Intent i = new Intent(Intent.ACTION_VIEW, uri);
                    startActivity(i);
                } catch (Exception ignored) {}
            });
        }
    }

    public void setCurrentAudio(File f) { currentAudio = f; }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (uploadServer != null) uploadServer.stopServer();
        if (player != null) { player.release(); player = null; }
        if (webView != null) { webView.removeJavascriptInterface("DriveTV"); webView.destroy(); }
        super.onDestroy();
    }
}
