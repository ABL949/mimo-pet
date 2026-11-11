package com.mimo.pet;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;

public class MainActivity extends Activity {

    private static final int REQ_OVERLAY = 1001;
    private static final String K_IGNORE_VER = "ignore_version";

    private static final String GITEE_LATEST = "https://gitee.com/api/v5/repos/ABL949/mimo-pet/releases/latest";
    private static final String GITHUB_LATEST = "https://api.github.com/repos/ABL949/mimo-pet/releases/latest";
    private static final String GITEE_RELEASES = "https://gitee.com/ABL949/mimo-pet/releases";
    private static final String GITHUB_RELEASES = "https://github.com/ABL949/mimo-pet/releases";

    private SharedPreferences sp;

    private View cardBunny, cardChibi;
    private Button btnSmall, btnStandard, btnLarge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sp = getSharedPreferences(PetService.PREFS, MODE_PRIVATE);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);
        setContentView(scroll);

        // 标题
        TextView title = new TextView(this);
        title.setText("MIMO 桌宠");
        title.setTextColor(Color.rgb(28, 30, 42));
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("个人二创同人桌宠 · 非小米官方应用");
        sub.setTextColor(Color.rgb(120, 124, 140));
        sub.setTextSize(12);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(6), 0, dp(20));
        root.addView(sub);

        // 1. 形象选择
        root.addView(sectionLabel("形象"));
        LinearLayout petRow = new LinearLayout(this);
        petRow.setOrientation(LinearLayout.HORIZONTAL);
        petRow.setGravity(Gravity.CENTER);
        petRow.setPadding(0, dp(6), 0, dp(6));
        cardBunny = makePetCard("bunny", "兔娘");
        cardChibi = makePetCard("chibi", "萌版");
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                dp(140), LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.rightMargin = dp(16);
        cardBunny.setLayoutParams(cp);
        cardChibi.setLayoutParams(new LinearLayout.LayoutParams(
                dp(140), LinearLayout.LayoutParams.WRAP_CONTENT));
        petRow.addView(cardBunny);
        petRow.addView(cardChibi);
        root.addView(petRow);
        refreshPetCards();

        // 2. 大小
        root.addView(sectionLabel("大小"));
        LinearLayout sizeRow = new LinearLayout(this);
        sizeRow.setOrientation(LinearLayout.HORIZONTAL);
        sizeRow.setGravity(Gravity.CENTER);
        sizeRow.setPadding(0, dp(6), 0, dp(6));
        btnSmall = makeSizeBtn("small", "小");
        btnStandard = makeSizeBtn("standard", "标准");
        btnLarge = makeSizeBtn("large", "大");
        LinearLayout.LayoutParams slp2 = new LinearLayout.LayoutParams(0, dp(46), 1f);
        slp2.rightMargin = dp(10);
        btnSmall.setLayoutParams(slp2);
        btnStandard.setLayoutParams(slp2);
        btnLarge.setLayoutParams(new LinearLayout.LayoutParams(0, dp(46), 1f));
        sizeRow.addView(btnSmall);
        sizeRow.addView(btnStandard);
        sizeRow.addView(btnLarge);
        root.addView(sizeRow);
        refreshSizeBtns();

        // 3. 开关
        root.addView(sectionLabel("行为与触摸"));
        root.addView(makeSwitch("安静模式", "只做动作，不冒气泡", PetService.K_QUIET, true));
        root.addView(makeSwitch("穿透模式", "桌宠不拦截触摸，遮挡处可正常点击应用", PetService.K_PASS, true));
        root.addView(makeSwitch("自主走动", "待机时在屏幕内自由游走，可上下左右移动", PetService.K_WALK, false));

        // 启动 / 关闭
        Button start = new Button(this);
        start.setText("启动桌宠");
        start.setTextSize(16);
        start.setTextColor(Color.WHITE);
        start.setBackground(roundRect(Color.rgb(242, 122, 56), dp(12)));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        slp.topMargin = dp(24);
        start.setLayoutParams(slp);
        root.addView(start);

        Button stop = new Button(this);
        stop.setText("关闭桌宠");
        stop.setTextSize(16);
        stop.setTextColor(Color.rgb(60, 64, 84));
        stop.setBackground(roundRect(Color.rgb(232, 234, 240), dp(12)));
        LinearLayout.LayoutParams stlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        stlp.topMargin = dp(12);
        stop.setLayoutParams(stlp);
        root.addView(stop);

        // 免责声明
        TextView notice = new TextView(this);
        notice.setText("开源与免责声明\n" +
                "本项目为个人学习用途的二创同人桌宠，角色形象与小米官方无关，未经授权不得用于商业用途。" +
                "素材来源于网络二创，如侵犯您的权益，请联系作者删除。\n" +
                "本软件按 AGPL-3.0-or-later 协议开源，按现状提供，不附带任何担保。");
        notice.setTextColor(Color.rgb(130, 134, 150));
        notice.setTextSize(11);
        notice.setLineSpacing(dp(4), 1f);
        notice.setPadding(0, dp(20), 0, dp(12));
        root.addView(notice);

        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!Settings.canDrawOverlays(MainActivity.this)) {
                    requestOverlayPermission();
                } else {
                    launchPet();
                }
            }
        });
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopService(new Intent(MainActivity.this, PetService.class));
            }
        });

        checkUpdate();
    }

    // ---------- update check ----------
    private String currentVersion() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "0.0.0";
        }
    }

    private void checkUpdate() {
        final String current = currentVersion();
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override public void run() {
                String[] latest = fetchLatest(GITEE_LATEST);
                if (latest == null) latest = fetchLatest(GITHUB_LATEST);
                if (latest == null) return;
                final String tag = latest[0];
                final String body = latest.length > 1 ? latest[1] : "";
                if (tag == null || compareVersion(tag, current) <= 0) return;
                if (tag.equals(sp.getString(K_IGNORE_VER, ""))) return;
                main.post(new Runnable() {
                    @Override public void run() { showUpdateDialog(tag, body); }
                });
            }
        }).start();
    }

    // 返回 [tag_name, body]；失败返回 null
    private String[] fetchLatest(String urlStr) {
        HttpURLConnection c = null;
        try {
            URL url = new URL(urlStr);
            c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(3000);
            c.setReadTimeout(3000);
            c.setRequestMethod("GET");
            c.setRequestProperty("Accept", "application/json");
            if (c.getResponseCode() != 200) return null;
            BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            JSONObject o = new JSONObject(sb.toString());
            String tag = o.optString("tag_name", "");
            String body = o.optString("body", "");
            if (tag.length() == 0) return null;
            return new String[]{tag, body};
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    // 清理 Release 正文：弹窗只展示更新说明，去掉 Markdown 引用块与免责/版权条款段
    private String cleanReleaseBody(String raw) {
        if (raw == null) return "";
        String[] lines = raw.split("\\r?\\n");
        StringBuilder sb = new StringBuilder();
        for (String ln : lines) {
            String t = ln.trim();
            if (t.length() == 0) continue;
            if (t.startsWith(">")) continue;            // Markdown 引用块（免责声明通常写成引用）
            if (isDisclaimer(t)) continue;            // 命中免责/版权关键词的整行
            sb.append(t).append("\n");
        }
        return sb.toString().trim();
    }

    private boolean isDisclaimer(String t) {
        String[] keys = {"非官方", "二创同人", "商业使用", "不得用于商业", "版权归原作者",
                "侵权", "联系下架", "与小米", "品牌形象", "个人学习", "素材来源于网络"};
        for (String k : keys) if (t.contains(k)) return true;
        return false;
    }

    // 语义化版本比较：a>b 返回正，a<b 返回负，相等 0；忽略前导 v
    private int compareVersion(String a, String b) {
        int[] pa = parseVer(a);
        int[] pb = parseVer(b);
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            int x = i < pa.length ? pa[i] : 0;
            int y = i < pb.length ? pb[i] : 0;
            if (x != y) return x > y ? 1 : -1;
        }
        return 0;
    }

    private int[] parseVer(String v) {
        String s = v.trim().replaceFirst("^[vV]", "");
        String[] parts = s.split("[^0-9]+");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try { out[i] = parts[i].length() > 0 ? Integer.parseInt(parts[i]) : 0; }
            catch (NumberFormatException e) { out[i] = 0; }
        }
        return out;
    }

    private void showUpdateDialog(final String tag, String body) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        box.setPadding(pad, dp(16), pad, 0);

        TextView msg = new TextView(this);
        String text = "检测到新版本 " + tag + "，可前往下载页查看更新内容。";
        String cleaned = cleanReleaseBody(body);
        if (cleaned.length() > 0) {
            if (cleaned.length() > 200) cleaned = cleaned.substring(0, 200) + "…";
            text = text + "\n\n" + cleaned;
        }
        msg.setText(text);
        msg.setTextColor(Color.rgb(60, 64, 84));
        msg.setTextSize(14);
        msg.setLineSpacing(dp(4), 1f);
        box.addView(msg);

        final CheckBox cb = new CheckBox(this);
        cb.setText("本版本更新不再提醒");
        cb.setTextColor(Color.rgb(90, 94, 110));
        cb.setTextSize(13);
        cb.setPadding(0, dp(12), 0, 0);
        box.addView(cb);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("发现新版本 " + tag)
                .setView(box)
                .setPositiveButton("Gitee 下载", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        rememberIgnore(tag, cb.isChecked());
                        openLink(GITEE_RELEASES);
                    }
                })
                .setNegativeButton("GitHub 下载", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        rememberIgnore(tag, cb.isChecked());
                        openLink(GITHUB_RELEASES);
                    }
                })
                .setNeutralButton("稍后", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        rememberIgnore(tag, cb.isChecked());
                    }
                })
                .create();
        dialog.show();
    }

    private void rememberIgnore(String tag, boolean ignore) {
        if (ignore) sp.edit().putString(K_IGNORE_VER, tag).apply();
        else if (tag.equals(sp.getString(K_IGNORE_VER, ""))) sp.edit().remove(K_IGNORE_VER).apply();
    }

    private void openLink(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {}
    }

    // ---------- UI helpers ----------
    private TextView sectionLabel(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.rgb(60, 64, 84));
        t.setTextSize(15);
        t.setPadding(dp(4), dp(16), 0, dp(4));
        return t;
    }

    private GradientDrawable roundRect(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private View makePetCard(String key, String label) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(10), dp(12), dp(10), dp(10));
        card.setBackground(roundRect(Color.rgb(241, 243, 248), dp(14)));

        ImageView iv = new ImageView(this);
        Bitmap bmp = loadAssetBitmap("pets/" + key + ".png");
        if (bmp != null) iv.setImageBitmap(bmp);
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(92), dp(122)));
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        card.addView(iv);

        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(13);
        t.setTextColor(Color.rgb(60, 64, 84));
        t.setPadding(0, dp(8), 0, 0);
        card.addView(t);

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sp.edit().putString(PetService.K_PET, key).apply();
                refreshPetCards();
                restartPet();
            }
        });
        return card;
    }

    private Button makeSizeBtn(String key, String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(14);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sp.edit().putString(PetService.K_SIZE, key).apply();
                refreshSizeBtns();
                restartPet();
            }
        });
        return b;
    }

    private LinearLayout makeSwitch(String title, String desc, String key, boolean needsRestart) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(14), dp(14), dp(14));
        row.setBackground(roundRect(Color.rgb(241, 243, 248), dp(14)));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(8);
        row.setLayoutParams(rlp);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(15);
        t.setTextColor(Color.rgb(35, 38, 52));
        TextView d = new TextView(this);
        d.setText(desc);
        d.setTextSize(11);
        d.setTextColor(Color.rgb(130, 134, 150));
        col.addView(t);
        col.addView(d);
        row.addView(col);

        Switch sw = new Switch(this);
        sw.setChecked(sp.getBoolean(key, false));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = dp(12);
        sw.setLayoutParams(slp);
        row.addView(sw);

        sw.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sp.edit().putBoolean(key, sw.isChecked()).apply();
                if (needsRestart) {
                    restartPet();
                } else {
                    // 走动开关：只在服务内启停游走，不重建窗口、不改位置
                    Intent i = new Intent(MainActivity.this, PetService.class);
                    i.setAction(PetService.ACTION_UPDATE_WALK);
                    startService(i);
                }
            }
        });
        return row;
    }

    private void refreshPetCards() {
        String cur = sp.getString(PetService.K_PET, "bunny");
        cardBunny.setBackground(roundRect(
                "bunny".equals(cur) ? Color.rgb(253, 237, 230) : Color.rgb(241, 243, 248), dp(14)));
        cardChibi.setBackground(roundRect(
                "chibi".equals(cur) ? Color.rgb(253, 237, 230) : Color.rgb(241, 243, 248), dp(14)));
    }

    private void refreshSizeBtns() {
        String cur = sp.getString(PetService.K_SIZE, "standard");
        applySize(btnSmall, "small", cur);
        applySize(btnStandard, "standard", cur);
        applySize(btnLarge, "large", cur);
    }

    private void applySize(Button b, String key, String cur) {
        boolean on = key.equals(cur);
        b.setBackground(roundRect(on ? Color.rgb(242, 122, 56) : Color.rgb(232, 234, 240), dp(10)));
        b.setTextColor(on ? Color.WHITE : Color.rgb(60, 64, 84));
    }

    private Bitmap loadAssetBitmap(String path) {
        try (InputStream is = getAssets().open(path)) {
            return BitmapFactory.decodeStream(is);
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- service ----------
    private void launchPet() {
        Intent i = new Intent(this, PetService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(i);
        } else {
            startService(i);
        }
    }

    private void restartPet() {
        if (Settings.canDrawOverlays(this)) {
            stopService(new Intent(this, PetService.class));
            launchPet();
        }
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQ_OVERLAY);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_OVERLAY && Settings.canDrawOverlays(this)) {
            launchPet();
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
