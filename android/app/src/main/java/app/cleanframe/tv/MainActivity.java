package app.cleanframe.tv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CleanFrame for Android TV. A full-screen WebView shows the bundled CleanFrame UI and a small
 * bridge lets it list and open installed apps and switch HDMI inputs. Everything is local:
 * pages are served from the APK under a private https origin, and nothing goes to the network.
 */
public class MainActivity extends Activity {
    private static final String ORIGIN = "https://cleanframe.local";
    private static final String START = ORIGIN + "/home/index.html";
    private static final Pattern DIGITS = Pattern.compile("(\\d+)");

    private WebView web;
    private final Map<String, byte[]> icons = Collections.synchronizedMap(new HashMap<String, byte[]>());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        web.setBackgroundColor(0xFF000000);
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true); // Saved Home preferences live in localStorage.
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setTextZoom(100);

        web.addJavascriptInterface(new Bridge(), "CleanFrameAndroid");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new LocalClient());
        setContentView(web);
        hideSystemUi();
        web.loadUrl(START);
        web.requestFocus();
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        if (web != null) { web.onResume(); web.requestFocus(); }
    }

    @Override
    protected void onPause() {
        if (web != null) web.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }

    /** The remote's Back key never reaches page JavaScript on its own; pass it to CleanFrame. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (event.getAction() == KeyEvent.ACTION_UP && web != null) {
                web.evaluateJavascript("window.CleanFrameKey&&window.CleanFrameKey(27)", null);
            }
            return true; // As a Home app, Back must not close CleanFrame.
        }
        return super.dispatchKeyEvent(event);
    }

    /** Serves the bundled UI and app icons under a private https origin. */
    private class LocalClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return !request.getUrl().toString().startsWith(ORIGIN + "/");
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            if (!url.startsWith(ORIGIN + "/")) return blocked();
            String path = request.getUrl().getPath();
            if (path == null || path.contains("..")) return blocked();
            if (path.startsWith("/icons/")) {
                byte[] png = icons.get(path.substring("/icons/".length()));
                return png == null ? blocked() : response("image/png", new ByteArrayInputStream(png));
            }
            try {
                InputStream in = getAssets().open(path.substring(1));
                return response(mime(path), in);
            } catch (Exception e) {
                return blocked();
            }
        }
    }

    private static WebResourceResponse response(String mime, InputStream in) {
        WebResourceResponse r = new WebResourceResponse(mime, mime.startsWith("text/") || mime.endsWith("javascript") || mime.endsWith("json") || mime.endsWith("svg+xml") ? "utf-8" : null, in);
        Map<String, String> headers = new HashMap<>();
        headers.put("Access-Control-Allow-Origin", ORIGIN);
        headers.put("Cache-Control", "no-cache");
        r.setResponseHeaders(headers);
        return r;
    }

    private static WebResourceResponse blocked() {
        WebResourceResponse r = new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
        r.setStatusCodeAndReasonPhrase(404, "Not Found");
        return r;
    }

    private static String mime(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        if (p.endsWith(".html")) return "text/html";
        if (p.endsWith(".js") || p.endsWith(".mjs")) return "application/javascript";
        if (p.endsWith(".css")) return "text/css";
        if (p.endsWith(".svg")) return "image/svg+xml";
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".webp")) return "image/webp";
        if (p.endsWith(".woff2")) return "font/woff2";
        if (p.endsWith(".woff")) return "font/woff";
        if (p.endsWith(".ttf")) return "font/ttf";
        if (p.endsWith(".otf")) return "font/otf";
        if (p.endsWith(".json")) return "application/json";
        return "application/octet-stream";
    }

    private byte[] iconBytes(Drawable d) {
        int size = 128;
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, size, size);
        d.draw(c);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.PNG, 100, out);
        b.recycle();
        return out.toByteArray();
    }

    /** Methods called from android/web/device.js. Each returns "" on success or an error message. */
    private class Bridge {
        @JavascriptInterface
        public String listApps() {
            JSONArray list = new JSONArray();
            PackageManager pm = getPackageManager();
            Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            Set<String> seen = new HashSet<>();
            try {
                for (ResolveInfo r : pm.queryIntentActivities(main, 0)) {
                    ActivityInfo a = r.activityInfo;
                    if (a == null || getPackageName().equals(a.packageName) || !seen.add(a.packageName)) continue;
                    JSONObject o = new JSONObject();
                    o.put("id", a.packageName);
                    o.put("name", String.valueOf(r.loadLabel(pm)));
                    try {
                        String key = a.packageName + ".png";
                        if (!icons.containsKey(key)) icons.put(key, iconBytes(r.loadIcon(pm)));
                        o.put("icon", "/icons/" + key);
                    } catch (Exception ignored) { /* The UI falls back to initials. */ }
                    list.put(o);
                }
            } catch (Exception e) {
                return "null";
            }
            return list.toString();
        }

        @JavascriptInterface
        public boolean isInstalled(String pkg) {
            return launchIntent(pkg) != null;
        }

        @JavascriptInterface
        public String launch(String pkg) {
            Intent i = launchIntent(pkg);
            if (i == null) return "This app is not installed.";
            return start(i);
        }

        @JavascriptInterface
        public String openSettings() {
            return start(new Intent(Settings.ACTION_SETTINGS));
        }

        /** Opens the TV's original Home screen (the first Home app that is not CleanFrame). */
        @JavascriptInterface
        public String openSystemHome() {
            PackageManager pm = getPackageManager();
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            for (ResolveInfo r : pm.queryIntentActivities(home, 0)) {
                ActivityInfo a = r.activityInfo;
                if (a == null || getPackageName().equals(a.packageName)) continue;
                Intent i = new Intent(home).setClassName(a.packageName, a.name);
                return start(i);
            }
            return "No other Home screen was found.";
        }

        @JavascriptInterface
        public String listInputs() {
            JSONArray list = new JSONArray();
            try {
                for (Input in : hdmiInputs()) {
                    JSONObject o = new JSONObject();
                    o.put("number", in.number);
                    o.put("name", in.label);
                    o.put("connected", in.connected);
                    list.put(o);
                }
            } catch (Exception e) {
                return "null";
            }
            return list.toString();
        }

        @JavascriptInterface
        public String openInput(int number) {
            Input target = null;
            for (Input in : hdmiInputs()) if (in.number == number) { target = in; break; }
            if (target == null) return "HDMI " + number + " was not found.";
            Intent view = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(target.id));
            return start(view);
        }
    }

    private Intent launchIntent(String pkg) {
        if (pkg == null || pkg.isEmpty()) return null;
        PackageManager pm = getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        return i;
    }

    private String start(final Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
            return "";
        } catch (ActivityNotFoundException e) {
            return "The TV has no app for this action.";
        } catch (SecurityException e) {
            return "The TV did not allow this action.";
        } catch (Exception e) {
            return String.valueOf(e.getMessage());
        }
    }

    private static class Input {
        String id;
        String label;
        int number;
        boolean connected;
    }

    /** Top-level HDMI ports (not the HDMI-CEC devices behind them), numbered as the TV labels them. */
    private List<Input> hdmiInputs() {
        List<Input> result = new ArrayList<>();
        TvInputManager tv = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        if (tv == null) return result;
        List<TvInputInfo> infos = tv.getTvInputList();
        Set<Integer> used = new HashSet<>();
        List<Input> unnumbered = new ArrayList<>();
        for (TvInputInfo info : infos) {
            if (info.getType() != TvInputInfo.TYPE_HDMI || info.getParentId() != null) continue;
            Input in = new Input();
            in.id = info.getId();
            CharSequence label = info.loadCustomLabel(this);
            if (label == null || label.length() == 0) label = info.loadLabel(this);
            in.label = label == null ? "" : label.toString();
            try { in.connected = tv.getInputState(in.id) != TvInputManager.INPUT_STATE_DISCONNECTED; }
            catch (Exception e) { in.connected = true; }
            Matcher m = DIGITS.matcher(info.loadLabel(this) == null ? "" : info.loadLabel(this).toString());
            if (m.find()) {
                in.number = Integer.parseInt(m.group(1));
                used.add(in.number);
                result.add(in);
            } else unnumbered.add(in);
        }
        int next = 1;
        for (Input in : unnumbered) {
            while (used.contains(next)) next++;
            in.number = next;
            used.add(next);
            result.add(in);
        }
        Collections.sort(result, (a, b) -> Integer.compare(a.number, b.number));
        for (Input in : result) if (in.label.isEmpty()) in.label = "HDMI " + in.number;
        return result;
    }
}
