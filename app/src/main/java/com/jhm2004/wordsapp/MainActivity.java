package com.jhm2004.wordsapp;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {

    private static final int C_GREEN = 0xFF22C55E;
    private static final int C_RED   = 0xFFEF4444;
    private static final int C_BLUE  = 0xFF2563EB;
    private static final int C_BLUE_DARK = 0xFF1D4ED8;
    private static final int C_BG    = 0xFFF5F0E8;
    private static final int C_CARD  = 0xFFFFFFFF;
    private static final int C_LINE  = 0xFFE5E1D8;
    private static final int C_TEXT  = 0xFF1F2937;
    private static final int C_SUB   = 0xFF6B7280;

    private static final String PREFS = "wordquiz";
    private static final String KEY_WRONG = "wrong_set";
    private static final String KEY_SESSION = "session";

    private static final int MODE_SEQ = 0;
    private static final int MODE_RANDOM = 1;
    private static final int MODE_WRONG = 2;

    private static class Word {
        final String w, c; final String[] d;
        Word(String w, String c, String[] d) { this.w = w; this.c = c; this.d = d; }
    }

    private final List<Word> words = new ArrayList<>();
    private final Set<Integer> wrongSet = new HashSet<>();
    private final Handler handler = new Handler();
    private SharedPreferences prefs;

    private int mode;
    private List<Integer> order;
    private int ptr;
    private int quizTotal;
    private int correctCount;
    private int wrongCount;
    private boolean answered;
    private int correctIdx;
    private String[] shown;

    // 布局成员
    private LinearLayout container;
    private LinearLayout progressBarWrap;
    private TextView progressLabel;
    private ProgressBar progressBar;
    private TextView statsView;
    private TextView wordView;
    private LinearLayout optionsBox;
    private Button nextBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        loadWrongFromPrefs();
        loadWords();
        buildShell();
        showHome();
    }

    // ---------- 构建静态外壳 ----------
    private void buildShell() {
        // 标题栏
        final TextView title = new TextView(this);
        title.setText("考研单词刷题");
        title.setTextSize(18);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);

        final Button back = btn("返回首页", 13, C_SUB, mCard(12, 0xFFEBE7DE));
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showHome(); }
        });

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(12), dp(16), dp(12), dp(6));
        LinearLayout topRow = new LinearLayout(this);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        topRow.addView(back, new LinearLayout.LayoutParams(-2, dp(36)));
        topRow.addView(title, new LinearLayout.LayoutParams(0, dp(36), 1f));
        header.addView(topRow);
        GradientDrawable hg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{C_BLUE, C_BLUE_DARK});
        header.setBackground(hg);

        // 内容容器
        container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(container, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    // ---------- 首页 ----------
    private void showHome() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(26), dp(50), dp(26), dp(30));

        TextView title = tv("考研单词", 28, C_TEXT, true);
        title.setGravity(Gravity.CENTER);
        TextView sub = tv("共 " + words.size() + " 个单词 · 每题四选一", 14, C_SUB, false);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(6), 0, dp(30));
        box.addView(title);
        box.addView(sub);

        box.addView(homeBtn("顺序刷题", new Runnable() { @Override public void run() { startMode(MODE_SEQ); } }));
        box.addView(homeBtn("随机刷题", new Runnable() { @Override public void run() { startMode(MODE_RANDOM); } }));
        box.addView(homeBtn("错题本（" + wrongSet.size() + " 个）", new Runnable() {
            @Override public void run() {
                if (wrongSet.isEmpty()) { toast("错题本为空，先去刷题吧"); return; }
                startMode(MODE_WRONG);
            }
        }));
        if (!wrongSet.isEmpty()) {
            box.addView(homeBtn("清空错题本", new Runnable() {
                @Override public void run() { wrongSet.clear(); saveWrong(); showHome(); }
            }));
        }

        fill(box);
    }

    private Button homeBtn(String label, final Runnable action) {
        Button b = btn(label, 17, -1, mCard(16, C_BLUE));
        b.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(58));
        lp.setMargins(0, dp(10), 0, 0);
        b.setLayoutParams(lp);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { action.run(); }
        });
        return b;
    }

    // ---------- 开始模式 ----------
    private void startMode(int m) {
        // 该模式有未完成的进度则直接恢复，否则开启新的一轮
        if (loadSession(m)) {
            buildQuizBox();
            showQuiz();
            return;
        }
        mode = m;
        correctCount = 0;
        wrongCount = 0;
        if (m == MODE_WRONG) {
            List<Integer> list = new ArrayList<>(wrongSet);
            Collections.shuffle(list);
            order = list;
        } else {
            order = new ArrayList<>();
            for (int i = 0; i < words.size(); i++) order.add(i);
            if (m == MODE_RANDOM) Collections.shuffle(order);
        }
        if (order.isEmpty()) { toast("没有可刷的单词"); showHome(); return; }
        buildQuizBox();
        quizTotal = order.size();
        ptr = 0;
        saveSession();
        showQuiz();
    }

    // ---------- 构建答题区 ----------
    private void buildQuizBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(16), dp(20), dp(20));

        // 顶部信息行
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        progressLabel = tv("", 14, C_SUB, false);
        statsView = tv("", 14, C_SUB, false);
        row.addView(progressLabel, new LinearLayout.LayoutParams(0, dp(34), 1f));
        row.addView(statsView, new LinearLayout.LayoutParams(-2, dp(34)));
        box.addView(row);

        // 进度条
        progressBarWrap = new LinearLayout(this);
        progressBarWrap.setPadding(0, dp(4), 0, dp(12));
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(C_BLUE));
        progressBarWrap.addView(progressBar, new LinearLayout.LayoutParams(-1, dp(12)));
        box.addView(progressBarWrap);

        // 单词
        wordView = tv("", 34, C_TEXT, true);
        wordView.setGravity(Gravity.CENTER);
        box.addView(wordView, new LinearLayout.LayoutParams(-1, dp(78)));

        // 选项
        optionsBox = new LinearLayout(this);
        optionsBox.setOrientation(LinearLayout.VERTICAL);
        box.addView(optionsBox);

        // 下一题（选错后出现）
        nextBtn = btn("下一题 ▶", 17, -1, mCard(14, C_BLUE));
        nextBtn.setTextColor(Color.WHITE);
        nextBtn.setVisibility(View.GONE);
        box.addView(nextBtn, new LinearLayout.LayoutParams(-1, dp(56)));
        nextBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (answered) advance(); }
        });

        fill(box);
    }

    // ---------- 显示一题 ----------
    private void showQuiz() {
        answered = false;
        nextBtn.setVisibility(View.GONE);

        int idx = order.get(ptr);
        Word wd = words.get(idx);

        progressLabel.setText("第 " + (ptr + 1) + " / " + quizTotal + " 题");
        progressBar.setMax(quizTotal);
        progressBar.setProgress(ptr);
        statsView.setText("正确 " + correctCount + "   错误 " + wrongCount);
        wordView.setText(wd.w);

        // 打乱选项
        List<String> opts = new ArrayList<>();
        opts.add(wd.c);
        opts.add(wd.d[0]); opts.add(wd.d[1]); opts.add(wd.d[2]);
        Collections.shuffle(opts);
        shown = opts.toArray(new String[0]);
        correctIdx = 0;
        for (int i = 0; i < shown.length; i++) if (shown[i].equals(wd.c)) correctIdx = i;

        optionsBox.removeAllViews();
        for (int i = 0; i < shown.length; i++) {
            final int fi = i;
            Button b = optionBtn("ABCD".charAt(fi) + ".  " + shown[i]);
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { if (!answered) onOptionClick((Button) v, fi); }
            });
            optionsBox.addView(b);
        }
    }

    private Button optionBtn(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(17);
        b.setTextColor(C_TEXT);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        GradientDrawable g = mCard(14, C_CARD);
        g.setStroke(dp(1), C_LINE);
        b.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(58));
        lp.setMargins(0, dp(8), 0, 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void onOptionClick(Button clicked, int i) {
        answered = true;
        if (i == correctIdx) {
            clicked.setTextColor(Color.WHITE);
            bg(clicked, C_GREEN, 14);
            correctCount++;
            statsView.setText("正确 " + correctCount + "   错误 " + wrongCount);
            // 错题本中答对，移出错词集
            if (mode == MODE_WRONG) {
                wrongSet.remove(order.get(ptr));
                saveWrong();
            }
            // 答对后立即跳到下一题
            advance();
        } else {
            clicked.setTextColor(Color.WHITE);
            bg(clicked, C_RED, 14);
            Button cb = (Button) optionsBox.getChildAt(correctIdx);
            cb.setTextColor(Color.WHITE);
            bg(cb, C_GREEN, 14);
            wrongCount++;
            statsView.setText("正确 " + correctCount + "   错误 " + wrongCount);
            // 记入错题本
            if (mode != MODE_WRONG) {
                wrongSet.add(order.get(ptr));
                saveWrong();
            }
            saveSession();
            nextBtn.setVisibility(View.VISIBLE);
        }
    }

    private void advance() {
        ptr++;
        if (ptr >= quizTotal) { showSummary(); return; }
        saveSession();
        showQuiz();
    }

    // ---------- 结果页 ----------
    private void showSummary() {
        clearSession(mode);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(26), dp(70), dp(26), dp(30));

        TextView t = tv(mode == MODE_WRONG ? "错题复习完成" : "本组完成", 24, C_TEXT, true);
        t.setGravity(Gravity.CENTER);
        box.addView(t);
        TextView ok = tv("正确  " + correctCount, 20, C_GREEN, true);
        ok.setGravity(Gravity.CENTER);
        ok.setPadding(0, dp(20), 0, 0);
        TextView no = tv("错误  " + wrongCount, 20, C_RED, true);
        no.setGravity(Gravity.CENTER);
        no.setPadding(0, dp(6), 0, dp(30));
        box.addView(ok);
        box.addView(no);

        box.addView(homeBtn("返回首页", new Runnable() { @Override public void run() { showHome(); } }));
        fill(box);
    }

    // ---------- 工具 ----------
    private void fill(View v) {
        container.removeAllViews();
        container.addView(v, new LinearLayout.LayoutParams(-1, -1));
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics()));
    }

    private void bg(View v, int color, float radius) {
        GradientDrawable g = mCard(radius, color);
        v.setBackground(g);
    }

    private GradientDrawable mCard(float radius, int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        return g;
    }

    private Button btn(String s, int sp, int color, GradientDrawable bg) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(sp);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        if (color > 0) b.setTextColor(color);
        b.setBackground(bg);
        return b;
    }

    private TextView tv(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return t;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    // ---------- 数据 ----------
    private void loadWords() {
        try {
            InputStream is = getAssets().open("words.json");
            byte[] buf = new byte[is.available()];
            int n = is.read(buf);
            is.close();
            JSONArray arr = new JSONArray(new String(buf, 0, n, StandardCharsets.UTF_8));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                JSONArray dd = o.getJSONArray("d");
                words.add(new Word(o.getString("w"), o.getString("c"),
                        new String[]{dd.getString(0), dd.getString(1), dd.getString(2)}));
            }
        } catch (Exception e) {
            words.clear();
        }
    }

    private void loadWrongFromPrefs() {
        wrongSet.clear();
        try {
            for (String s : prefs.getStringSet(KEY_WRONG, new HashSet<String>()))
                wrongSet.add(Integer.parseInt(s));
        } catch (Exception ignore) {}
    }

    private void saveWrong() {
        HashSet<String> set = new HashSet<>();
        for (int i : wrongSet) set.add(String.valueOf(i));
        prefs.edit().putStringSet(KEY_WRONG, set).apply();
    }

    // ---------- 刷题进度持久化（按模式分别存储） ----------
    private static String sessionKey(int m) {
        return KEY_SESSION + "." + m;
    }

    private void saveSession() {
        try {
            JSONObject o = new JSONObject();
            o.put("mode", mode);
            o.put("ptr", ptr);
            o.put("total", quizTotal);
            o.put("ok", correctCount);
            o.put("no", wrongCount);
            JSONArray arr = new JSONArray();
            for (int i : order) arr.put(i);
            o.put("order", arr);
            prefs.edit().putString(sessionKey(mode), o.toString()).apply();
        } catch (Exception ignore) {}
    }

    private boolean loadSession(int m) {
        String s = prefs.getString(sessionKey(m), null);
        if (s == null) return false;
        try {
            JSONObject o = new JSONObject(s);
            mode = m;
            ptr = o.getInt("ptr");
            quizTotal = o.getInt("total");
            correctCount = o.getInt("ok");
            wrongCount = o.getInt("no");
            JSONArray arr = o.getJSONArray("order");
            order = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) order.add(arr.getInt(i));
            if (order.isEmpty() || ptr >= quizTotal) { clearSession(m); return false; }
            return true;
        } catch (Exception e) {
            clearSession(m);
            return false;
        }
    }

    private void clearSession(int m) {
        prefs.edit().remove(sessionKey(m)).apply();
    }
}