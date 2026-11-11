package com.mimo.pet;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.TextView;

import org.json.JSONObject;

public class PetService extends Service {

    public static final String PREFS = "mimo_pet";
    public static final String K_PET = "petType";
    public static final String K_SIZE = "size";
    public static final String K_QUIET = "quiet";
    public static final String K_PASS = "passThrough";
    public static final String K_WALK = "walk";

    private static final String CHANNEL_ID = "pet_channel";
    private static final int NOTIF_ID = 1;
    public static final String ACTION_UPDATE_WALK = "com.mimo.pet.UPDATE_WALK";

    private WindowManager wm;
    private WebView web;
    private WindowManager.LayoutParams params;
    private SharedPreferences sp;
    private int screenW, screenH;
    private float density;
    private int lastW, lastH; // 记录创建窗时的屏幕尺寸，用于检测横竖屏翻转
    private int lastRotation = -1; // 当前屏幕旋转方向，横屏按握持方向分流贴边补偿

    // 游走状态（速度 px/秒，浮点位置按帧平滑推进）
    private Handler walkHandler;
    private Runnable walkTick;
    private float vx = 0, vy = 0;
    private float posX = 0, posY = 0;
    private int frameCount = 0;
    private static final int FRAME_MS = 32;

    // 触摸拖动状态
    private boolean dragging = false;
    private boolean snapped = false;
    private int snapEdge = -1; // 0左 1右 3下（顶边不吸附）
    private float downRawX, downRawY;
    private int downX, downY;
    private int touchSlop;

    // 吸附露出比例：左边多露一点、右边露一半；贴底露出约四分之三身高（头与上身在屏内，仅裁脚底）
    private static final float LEFT_SHOW = 0.60f;
    private static final float RIGHT_SHOW = 0.50f;
    // 横屏软件层 WebView 角色在窗内偏右，右贴边多收一些，露出量对齐竖屏基准；竖屏沿用 0.50
    private static final float RIGHT_SHOW_LAND = 0.70f;
    // rot1（正握横屏）窗内角色偏右更多，右贴边需再加大补偿；rot3（反握）沿用 0.70
    private static final float RIGHT_SHOW_LAND_R1 = 0.85f;
    private static final float BOTTOM_SHOW = 0.76f;

    // 吸附后定时游回
    private Handler snapHandler;
    private Runnable snapBack;

    // 独立原生气泡窗：与角色 WebView 完全解耦，位置由原生代码按角色窗算死，
    // 角色图在网页内怎么跳/重绘都带不动气泡
    private TextView bubble;
    private WindowManager.LayoutParams bubbleParams;
    private Handler bubbleHandler;
    private Runnable bubbleHide;
    private boolean bubbleShown = false;
    // 角色窗四周余量：顶部给跳跃位移，左右给跳跃放大/旋转留白，防止被窗沿裁掉
    private int jumpReservePx;
    private int sideReservePx;
    private int jumpUpPx;
    private float topReserveDp, sideReserveDp;

    @Override
    public void onCreate() {
        super.onCreate();
        sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        updateDisplaySize();
        touchSlop = Math.round(10 * density);
    }

    // 动态读取当前显示的真实宽高（随横竖屏切换更新）
    private void updateDisplaySize() {
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;
        density = dm.density;
        try { lastRotation = wm.getDefaultDisplay().getRotation(); } catch (Exception ignored) {}
    }

    // 宽高比是否发生横竖屏翻转
    private boolean orientationFlipped() {
        if (lastW == 0) return false;
        return (lastW > lastH) != (screenW > screenH);
    }

    // 右贴边露出比例：竖屏用验收基准；横屏按握持方向分流，正握(rot1)补偿更大，反握(rot3)沿用横屏基准
    private float rightShow() {
        if (screenW <= screenH) return RIGHT_SHOW;
        return lastRotation == 1 ? RIGHT_SHOW_LAND_R1 : RIGHT_SHOW_LAND;
    }


    // 是否需要按新屏重建：仅宽高比发生横竖屏翻转（沿用 3.1.3 验收口径，尺寸微变不重建）
    private boolean shouldRebuild() {
        if (lastW == 0) return false;
        return orientationFlipped();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_UPDATE_WALK.equals(intent.getAction())) {
            // 仅同步走动开关：不重建窗口、不改变当前位置
            if (web != null) {
                if (walk()) startWalkLoop(); else stopWalkLoop();
            }
            return START_NOT_STICKY;
        }
        startForegroundSafe();
        if (web == null) {
            createOverlay();
        }
        // 不使用 START_STICKY：未点「启动桌宠」时系统不自动拉起
        return START_NOT_STICKY;
    }

    // 独立监听横竖屏/尺寸变化：不依赖走动循环，翻转即按新屏重建，避免窗沿用旧宽高出屏或消失。
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateDisplaySize();
        if (web == null) return;
        if (lastW != 0 && ((lastW > lastH) != (screenW > screenH))) {
            rebuild();
        }
    }

    // ---------- config ----------
    private String petType() { return sp.getString(K_PET, "bunny"); }
    private String sizeKey() { return sp.getString(K_SIZE, "standard"); }
    private boolean quiet() { return sp.getBoolean(K_QUIET, false); }
    private boolean passThrough() { return sp.getBoolean(K_PASS, false); }
    private boolean walk() { return sp.getBoolean(K_WALK, true); }

    // 按屏幕短边（dp）给尺寸系数：以 360dp 宽主流屏为基准，小屏收、大屏放，夹 0.85~1.20；
    // 横屏短边=屏高，角色自动偏小，不占满矮屏。密度换算由 density 另行处理。
    private float screenScale() {
        float wdp = screenW / density;
        float hdp = screenH / density;
        float shortDp = Math.min(wdp, hdp);
        return Math.max(0.85f, Math.min(1.20f, shortDp / 360f));
    }

    private int[] sizeDp() {
        float s = screenScale();
        // 兔娘三档：标准取小/大中间值；大档横屏不占满整屏；小档再缩小
        int[] bunny;
        switch (sizeKey()) {
            case "small":   bunny = new int[]{100, 136}; break;
            case "large":   bunny = new int[]{170, 228}; break;
            default:        bunny = new int[]{135, 182};
        }
        int bw = Math.round(bunny[0] * s);
        int bh = Math.round(bunny[1] * s);
        if (petType().equals("chibi")) {
            // 萌版按兔娘 ×0.8，同档明显比兔娘小一圈
            return new int[]{
                Math.round(bw * 0.80f),
                Math.round(bh * 0.80f)
            };
        }
        return new int[]{bw, bh};
    }

    // ---------- overlay ----------
    private void createOverlay() {
        updateDisplaySize();
        int[] dp = sizeDp();
        int roleW = Math.round(dp[0] * density);
        int roleH = Math.round(dp[1] * density);
        boolean isChibi = petType().equals("chibi");
        // 各形象跳起的上移幅度（与网页 jump 关键帧一致）；气泡基线按此上抬，跳起后停在帽檐上方、不压眼
        float jumpUpDp = isChibi ? 38f : 26f;
        // 跳跃关键帧最大缩放（与网页一致）：余量要覆盖跳幅 + 缩放把头顶/左右顶出的部分 + 安全边，否则跳起被窗沿裁
        float scaleMax = isChibi ? 1.06f : 1.02f;
        float roleWdp = dp[0], roleHdp = dp[1];
        // 安全边放大：顶部覆盖跳幅+头顶放大外扩+缓冲；两侧覆盖左右放大外扩+缓冲
        topReserveDp = jumpUpDp + roleHdp * (scaleMax - 1f) + 3f;
        sideReserveDp = roleWdp * (scaleMax - 1f) + 14f;
        jumpReservePx = Math.round(topReserveDp * density);
        sideReservePx = Math.round(sideReserveDp * density);
        jumpUpPx = Math.round(jumpUpDp * density);
        int h = roleH + jumpReservePx;
        // 横屏可视高度较矮时，窗口高度不超过可视区，避免角色被上下裁掉
        h = Math.min(h, Math.round(screenH * 0.92f));
        int w = roleW + sideReservePx * 2;
        // 宽度不超过屏宽减小边距，窄屏/小窗机型上不顶满整宽
        int maxW = Math.max(roleW, screenW - Math.round(8 * density));
        w = Math.min(w, maxW);

        web = new WebView(this);
        web.setBackgroundColor(0x00000000);
        web.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        // 锁定视口宽度=悬浮窗宽度，关闭缩放与文字缩放，避免窄窗把页面压缩瘪形
        s.setUseWideViewPort(false);
        s.setLoadWithOverviewMode(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setTextZoom(100);
        s.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);
        web.setWebChromeClient(new android.webkit.WebChromeClient() {});
        web.addJavascriptInterface(new Bridge(), "Android");
        web.setOnTouchListener(new PetTouch());
        web.loadUrl("file:///android_asset/pet.html?pet=" + petType());

        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        if (passThrough()) {
            flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }

        params = new WindowManager.LayoutParams(
                w, h,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                flags,
                PixelFormat.RGBA_8888);
        params.gravity = Gravity.TOP | Gravity.START;
        int maxX = Math.max(0, screenW - w);
        int maxY = Math.max(0, screenH - h);
        params.x = Math.max(0, Math.min((screenW - w) / 2, maxX));
        params.y = Math.max(0, Math.min(screenH - h - Math.round(60 * density), maxY));
        wm.addView(web, params);
        lastW = screenW;
        lastH = screenH;
        createBubble();

        initWalkDir();
        startWalkLoop();
    }

    // ---------- native bubble (independent overlay) ----------
    private void createBubble() {
        bubble = new TextView(this);
        int padH = Math.round(14 * density);
        int padV = Math.round(8 * density);
        bubble.setPadding(padH, padV, padH, padV);
        bubble.setTextSize(13);
        bubble.setTextColor(Color.rgb(43, 43, 58));
        bubble.setGravity(Gravity.CENTER);
        bubble.setSingleLine(true);
        bubble.setLineSpacing(Math.round(4 * density), 1f);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(247, 255, 255, 255));
        bg.setStroke(Math.round(2 * density), Color.rgb(242, 122, 56));
        float r = Math.round(16 * density);
        bg.setCornerRadii(new float[]{r, r, r, r, r, r, Math.round(4 * density), Math.round(4 * density)});
        bubble.setBackground(bg);

        int bFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        if (passThrough()) bFlags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        bubbleParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                bFlags,
                PixelFormat.RGBA_8888);
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubble.setVisibility(View.GONE);
        wm.addView(bubble, bubbleParams);

        bubbleHandler = new Handler(Looper.getMainLooper());
    }

    private void showBubbleNative(String text) {
        if (web == null || bubble == null) return;
        if (text == null || text.length() == 0) return;
        bubble.setText(text);
        if (bubble.getVisibility() != View.VISIBLE) bubble.setVisibility(View.VISIBLE);
        bubbleShown = true;
        layoutBubble();
        if (bubbleHide != null) bubbleHandler.removeCallbacks(bubbleHide);
        bubbleHide = new Runnable() {
            @Override public void run() { hideBubbleNative(); }
        };
        bubbleHandler.postDelayed(bubbleHide, 1600);
    }

    private void hideBubbleNative() {
        if (bubble == null) return;
        bubbleShown = false;
        bubble.setVisibility(View.GONE);
    }

    // 按角色窗位置算气泡窗坐标：水平居中于角色窗；垂直贴在角色帽尖上方
    // （帽尖位于窗内顶部跳跃预留区下沿），不靠窗顶、不悬空；靠顶时退到预留区内
    private void layoutBubble() {
        if (bubble == null || !bubbleShown) return;
        int unspec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        bubble.measure(unspec, unspec);
        int bw = bubble.getMeasuredWidth();
        int bh = bubble.getMeasuredHeight();
        int gap = Math.round(8 * density);

        int bx = params.x + (params.width - bw) / 2;
        bx = Math.max(0, Math.min(bx, Math.max(0, screenW - bw)));
        // 气泡贴近站立帽尖上方：仅留间隙，不再随跳幅上抬；跳起帽尖上移后仍与气泡保有距离，不压眼
        int hatTop = params.y + jumpReservePx;
        int by = hatTop - bh - gap;
        if (by < 0) by = params.y + Math.round(4 * density);

        bubbleParams.x = bx;
        bubbleParams.y = by;
        try { wm.updateViewLayout(bubble, bubbleParams); } catch (Exception ignored) {}
    }

    // 角色窗位置变化（游走/拖动/吸附）时，同步已显示的气泡窗位置
    private void syncBubble() {
        if (bubbleShown) layoutBubble();
    }

    // ---------- random walking ----------
    private void initWalkDir() {
        float speedX = density * 10f / 0.42f;
        float speedY = density * 6.5f / 0.42f;
        vx = (Math.random() < 0.5 ? -1 : 1) * speedX;
        vy = (Math.random() < 0.5 ? -1 : 1) * speedY;
        posX = params.x;
        posY = params.y;
        frameCount = 100 + (int) (Math.random() * 50);
    }

    private void startWalkLoop() {
        stopWalkLoop();
        if (!walk() || web == null) return;
        walkHandler = new Handler(Looper.getMainLooper());
        walkTick = new Runnable() {
            @Override
            public void run() {
                moveWalking();
                walkHandler.postDelayed(this, FRAME_MS);
            }
        };
        walkHandler.postDelayed(walkTick, 2500);
    }

    private void stopWalkLoop() {
        if (walkHandler != null && walkTick != null) {
            walkHandler.removeCallbacks(walkTick);
        }
        walkHandler = null;
        walkTick = null;
    }

    private void moveWalking() {
        if (web == null || !web.isShown() || dragging) return;
        updateDisplaySize();
        // 横竖屏翻转后按新屏尺寸重建窗口，避免沿用旧屏宽高导致边界/裁切错位
        if (shouldRebuild()) {
            rebuild();
            return;
        }
        maybeAutoSnap();
        if (snapped) return;

        int maxX = Math.max(0, screenW - params.width);
        // 顶部不留安全边，让角色能靠到屏幕最上部；头顶与屏顶之间仍隔着跳跃预留区，跳起不裁头
        int minY = 0;
        int maxY = Math.max(minY, screenH - params.height - Math.round(16 * density));

        if (--frameCount <= 0) {
            // 拉长换向间隔、方向摆动取两版中间值
            float speedX = density * 10f / 0.42f;
            float speedY = density * 6.5f / 0.42f;
            vx += (float) (Math.random() * 2 - 1) * speedX * 0.22f;
            vy += (float) (Math.random() * 2 - 1) * speedY * 0.22f;
            vx = Math.max(-speedX, Math.min(speedX, vx));
            vy = Math.max(-speedY, Math.min(speedY, vy));
            frameCount = 100 + (int) (Math.random() * 50);
        }

        float dt = FRAME_MS / 1000f;
        posX += vx * dt;
        posY += vy * dt;

        if (posX <= 0) { posX = 0; vx = Math.abs(vx); }
        else if (posX >= maxX) { posX = maxX; vx = -Math.abs(vx); }
        if (posY <= minY) { posY = minY; vy = Math.abs(vy); }
        else if (posY >= maxY) { posY = maxY; vy = -Math.abs(vy); }

        int newX = Math.round(posX);
        int newY = Math.round(posY);
        if (newX != params.x || newY != params.y) {
            params.x = newX;
            params.y = newY;
            try { wm.updateViewLayout(web, params); } catch (Exception ignored) {}
            syncBubble();
        }
    }

    // ---------- snap to edge ----------
    private int clampX(int x) {
        int min = Math.round(-params.width * (1 - LEFT_SHOW));
        int max = screenW - Math.round(params.width * rightShow());
        return Math.max(min, Math.min(x, max));
    }

    private int clampY(int y) {
        // 顶边不出屏；底边最多藏到露出约三分之二身高（头与上身在屏内）
        int min = 0;
        int max = screenH - Math.round(params.height * BOTTOM_SHOW);
        return Math.max(min, Math.min(y, max));
    }

    // 拖动松手：就近贴边吸附（左/右/底，顶边不吸）。按到各边的距离判定，避免补偿后某边隐藏量落在阈值临界而吸不住
    private void maybeSnapDrag() {
        int w = params.width, h = params.height;
        int distLeft = Math.max(0, params.x);
        int distRight = Math.max(0, screenW - (params.x + w));
        int distBottom = Math.max(0, screenH - (params.y + h));
        int threshold = Math.round(24 * density);

        if (distLeft <= threshold && distLeft <= distRight && distLeft <= distBottom) {
            params.x = Math.round(-w * (1 - LEFT_SHOW));
            snapped = true; snapEdge = 0;
        } else if (distRight <= threshold && distRight <= distLeft && distRight <= distBottom) {
            params.x = screenW - Math.round(w * rightShow());
            snapped = true; snapEdge = 1;
        } else if (distBottom <= threshold && distBottom <= distLeft && distBottom <= distRight) {
            params.y = screenH - Math.round(h * BOTTOM_SHOW);
            snapped = true; snapEdge = 3;
        } else {
            snapped = false;
        }

        if (snapped) {
            try { wm.updateViewLayout(web, params); } catch (Exception ignored) {}
            syncBubble();
            scheduleSnapBack();
        }
    }

    // 待机时小概率就近贴到左/右/底边（顶边不吸附），之后会随机游回
    private void maybeAutoSnap() {
        if (snapped || dragging || !walk()) return;
        if (Math.random() > 0.004) return;

        int w = params.width, h = params.height;
        int distLeft = Math.max(0, params.x);
        int distRight = Math.max(0, screenW - (params.x + w));
        int distBottom = Math.max(0, screenH - (params.y + h));

        // 只有已经贴近某条边（阈值内）才就近吸附，不在屏幕中央跳向远处的边
        int threshold = Math.round(24 * density);
        int minDist = Math.min(Math.min(distLeft, distRight), distBottom);
        if (minDist > threshold) return;

        if (distLeft <= distRight && distLeft <= distBottom) {
            params.x = Math.round(-w * (1 - LEFT_SHOW)); snapEdge = 0;
        } else if (distRight <= distLeft && distRight <= distBottom) {
            params.x = screenW - Math.round(w * rightShow()); snapEdge = 1;
        } else {
            params.y = screenH - Math.round(h * BOTTOM_SHOW); snapEdge = 3;
        }
        snapped = true;
        posX = params.x;
        posY = params.y;
        try { wm.updateViewLayout(web, params); } catch (Exception ignored) {}
        syncBubble();
        scheduleSnapBack();
    }

    private void scheduleSnapBack() {
        if (snapHandler == null) snapHandler = new Handler(Looper.getMainLooper());
        if (snapBack != null) snapHandler.removeCallbacks(snapBack);
        snapBack = new Runnable() {
            @Override
            public void run() { unsnap(); }
        };
        // 15~35 秒后随机游回屏幕内
        snapHandler.postDelayed(snapBack, 15000 + (int) (Math.random() * 20000));
    }

    private void unsnap() {
        // 从吸附的那条边缘继续走入屏幕，不随机跳回中间
        float speedX = density * 10f / 0.42f;
        float speedY = density * 6.5f / 0.42f;
        switch (snapEdge) {
            case 0: vx = Math.abs(speedX); vy = (Math.random() < 0.5 ? -1 : 1) * speedY; break; // 左 → 向右
            case 1: vx = -Math.abs(speedX); vy = (Math.random() < 0.5 ? -1 : 1) * speedY; break; // 右 → 向左
            case 3: vy = -Math.abs(speedY); vx = (Math.random() < 0.5 ? -1 : 1) * speedX; break; // 下 → 向上
            default:
                vx = (Math.random() < 0.5 ? -1 : 1) * speedX;
                vy = (Math.random() < 0.5 ? -1 : 1) * speedY;
        }
        posX = params.x;
        posY = params.y;
        snapped = false;
        if (snapBack != null && snapHandler != null) snapHandler.removeCallbacks(snapBack);
        try { wm.updateViewLayout(web, params); } catch (Exception ignored) {}
        syncBubble();
    }

    private void petTap() {
        if (web != null) {
            web.evaluateJavascript("window.petTap && window.petTap()", null);
        }
    }

    private void resumeWalk() {
        dragging = false;
        posX = params.x;
        posY = params.y;
        startWalkLoop();
    }

    // ---------- touch (native, for responsiveness) ----------
    private class PetTouch implements View.OnTouchListener {
        @Override
        public boolean onTouch(View v, MotionEvent e) {
            if (passThrough()) return false;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    stopWalkLoop();
                    updateDisplaySize();
                    if (snapHandler != null && snapBack != null) snapHandler.removeCallbacks(snapBack);
                    dragging = false;
                    downRawX = e.getRawX();
                    downRawY = e.getRawY();
                    downX = params.x;
                    downY = params.y;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    int dx = Math.round(e.getRawX() - downRawX);
                    int dy = Math.round(e.getRawY() - downRawY);
                    if (Math.abs(dx) + Math.abs(dy) > touchSlop) dragging = true;
                    if (dragging) {
                        params.x = clampX(downX + dx);
                        params.y = clampY(downY + dy);
                        try { wm.updateViewLayout(web, params); } catch (Exception ignored) {}
                        syncBubble();
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    if (dragging) {
                        maybeSnapDrag();
                    } else {
                        if (snapped) unsnap();
                        else petTap();
                    }
                    resumeWalk();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    resumeWalk();
                    return true;
            }
            return true;
        }
    }

    // ---------- foreground ----------
    private void startForegroundSafe() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "桌宠运行中", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b = new Notification.Builder(this, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this);
        }
        b.setSmallIcon(android.R.drawable.ic_menu_myplaces)
                .setContentTitle("MIMO 桌宠")
                .setContentText("她正在桌面上陪你~")
                .setOngoing(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        startForeground(NOTIF_ID, b.build());
    }

    // 移除角色窗与气泡窗、停掉游走/吸附/气泡计时，供横竖屏重建与销毁复用
    private void releaseOverlay() {
        stopWalkLoop();
        // 重建后旧吸附状态不再适用，清掉避免新窗沿用旧贴边方向/速度
        snapped = false;
        snapEdge = -1;
        vx = 0;
        vy = 0;
        if (snapHandler != null && snapBack != null) snapHandler.removeCallbacks(snapBack);
        if (bubbleHandler != null && bubbleHide != null) bubbleHandler.removeCallbacks(bubbleHide);
        if (bubble != null) {
            try { wm.removeView(bubble); } catch (Exception ignored) {}
            bubble = null;
        }
        if (web != null) {
            try { wm.removeView(web); } catch (Exception ignored) {}
            web.destroy();
            web = null;
        }
    }

    // 横竖屏翻转后按当前屏幕尺寸重建窗口
    private void rebuild() {
        try {
            releaseOverlay();
            createOverlay();
        } catch (Exception e) {
            // 重建异常时兜底：尽量保留可运行状态，避免桌宠直接消失
            if (web == null) {
                try { createOverlay(); } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public void onDestroy() {
        releaseOverlay();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private class Bridge {
        @JavascriptInterface
        public String getConfig() {
            try {
                JSONObject o = new JSONObject();
                o.put("pet", petType());
                o.put("quiet", quiet());
                o.put("walk", walk());
                o.put("top", Math.round(topReserveDp));
                o.put("side", Math.round(sideReserveDp));
                return o.toString();
            } catch (Exception e) {
                return "{}";
            }
        }

        // 网页把台词交给原生气泡窗显示；安静模式不下发
        @JavascriptInterface
        public void showBubble(final String text) {
            if (quiet() || bubbleHandler == null) return;
            bubbleHandler.post(new Runnable() {
                @Override public void run() { showBubbleNative(text); }
            });
        }

        @JavascriptInterface
        public void hideBubble() {
            if (bubbleHandler == null) return;
            bubbleHandler.post(new Runnable() {
                @Override public void run() { hideBubbleNative(); }
            });
        }
    }
}
