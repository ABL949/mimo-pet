package com.mimo.pet;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.io.InputStream;

public class MainActivity extends Activity {

    private static final int REQ_OVERLAY = 1001;
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
