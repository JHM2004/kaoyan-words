package com.jhm2004.wordsapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
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
import android.widget.Switch;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
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
    private static final String KEY_FAV = "fav_set";
    private static final String KEY_SETTING_RMC = "rm_correct";
    private static final String KEY_STATS = "stats";
    private static final String KEY_SESSION = "session";

    private static final int MODE_SEQ = 0;
    private static final int MODE_RANDOM = 1;
    private static final int MODE_WRONG = 2;

    private static class Word {
        final String w, c; final String[] d;
        Word(String w, String c, String[] d) { this.w = w; this.c = c; this.d = d; }
    }

    private final List<Word> words = new ArrayList<>();
    private final Map<String, Integer> wordIndex = new HashMap<>();
    private final Set<String> wrongSet = new HashSet<>();
    private final Set<String> favSet = new HashSet<>();
    private boolean rmAfterCorrect;
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
    private Button favBtn;
    private Button prevBtn;
    private int[] selIdx;
    private boolean[] selOk;
    private long quizSeed;
    private int statsMonthOffset;
    private String statsSel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        loadWords();
        loadWrongFromPrefs();
        loadFavFromPrefs();
        loadSettings();
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
        box.addView(homeBtn("错题重刷（" + wrongSet.size() + " 个）", new Runnable() {
            @Override public void run() {
                if (wrongSet.isEmpty()) { toast("错题本为空，先去刷题吧"); return; }
                startMode(MODE_WRONG);
            }
        }));
        box.addView(homeBtn("错题记录（" + wrongSet.size() + " 个）", new Runnable() {
            @Override public void run() { showWrongList(); }
        }));
        box.addView(homeBtn("收藏夹（" + favSet.size() + " 个）", new Runnable() {
            @Override public void run() { showFavList(); }
        }));
        box.addView(homeBtn("设置", new Runnable() {
            @Override public void run() { showSettings(); }
        }));

        // 我的努力：学习统计卡（首页末尾）
        box.addView(buildStatsCard());

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
        if (m == MODE_WRONG) {
            // 错题重刷：始终按当前错题重建，实时反映最新错题数
            mode = m;
            correctCount = 0;
            wrongCount = 0;
            List<Integer> list = new ArrayList<>();
            for (String w : wrongSet) {
                Integer idx = wordIndex.get(w);
                if (idx != null) list.add(idx);
            }
            Collections.shuffle(list);
            order = list;
            if (order.isEmpty()) { toast("没有可刷的单词"); showHome(); return; }
            quizSeed = new Random().nextLong();
            quizTotal = order.size();
            initSel(quizTotal);
            ptr = 0;
            buildQuizBox();
            saveSession();
            showQuiz();
            return;
        }
        // 顺序/随机：该模式有未完成的进度则恢复，否则开启新的一轮
        if (loadSession(m)) {
            buildQuizBox();
            showQuiz();
            return;
        }
        mode = m;
        correctCount = 0;
        wrongCount = 0;
        order = new ArrayList<>();
        for (int i = 0; i < words.size(); i++) order.add(i);
        if (m == MODE_RANDOM) Collections.shuffle(order);
        if (order.isEmpty()) { toast("没有可刷的单词"); showHome(); return; }
        quizSeed = new Random().nextLong();
        quizTotal = order.size();
        initSel(quizTotal);
        ptr = 0;
        buildQuizBox();
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

        // 收藏按钮（在所有选项下面）
        favBtn = new Button(this);
        favBtn.setTextSize(16);
        favBtn.setAllCaps(false);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(-1, dp(40));
        flp.setMargins(dp(60), dp(10), dp(60), dp(6));
        favBtn.setLayoutParams(flp);
        favBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleFav(); }
        });
        box.addView(favBtn);

        // 导航行：上一题 / 下一题
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(0, dp(4), 0, 0);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-1, -2);
        nav.setLayoutParams(nlp);

        prevBtn = btn("← 上一题", 16, -1, mCard(12, 0xFFEFF1F5));
        prevBtn.setTextColor(C_TEXT);
        prevBtn.setLayoutParams(new LinearLayout.LayoutParams(0, dp(54), 1f));
        prevBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { prev(); }
        });
        nav.addView(prevBtn);

        nextBtn = btn("下一题 ▶", 16, -1, mCard(12, C_BLUE));
        nextBtn.setTextColor(Color.WHITE);
        nextBtn.setLayoutParams(new LinearLayout.LayoutParams(0, dp(54), 1f));
        nextBtn.setVisibility(View.GONE);
        nextBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (answered) advance(); }
        });
        nav.addView(nextBtn);
        box.addView(nav);

        // 全览按钮（题目最下方）
        Button overviewBtn = new Button(this);
        overviewBtn.setText("全览 · 全部题目 ▦");
        overviewBtn.setTextSize(13);
        overviewBtn.setAllCaps(false);
        overviewBtn.setTextColor(C_SUB);
        GradientDrawable og = mCard(30, 0xFFF1EFE8);
        og.setStroke(dp(1), C_LINE);
        overviewBtn.setBackground(og);
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(-2, dp(38));
        olp.setMargins(0, dp(12), 0, 0);
        overviewBtn.setLayoutParams(olp);
        overviewBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showOverview(); }
        });
        LinearLayout wrapOv = new LinearLayout(this);
        wrapOv.setGravity(Gravity.CENTER_HORIZONTAL);
        wrapOv.addView(overviewBtn);
        box.addView(wrapOv);

        fill(box);
    }

    // ---------- 显示一题 ----------
    private void showQuiz() {
        answered = (selIdx != null && selIdx[ptr] != -1);
        nextBtn.setVisibility(View.GONE);
        prevBtn.setVisibility(ptr > 0 ? View.VISIBLE : View.GONE);

        int idx = order.get(ptr);
        Word wd = words.get(idx);

        progressLabel.setText("第 " + (ptr + 1) + " / " + quizTotal + " 题");
        progressBar.setMax(quizTotal);
        progressBar.setProgress(ptr);
        statsView.setText("正确 " + correctCount + "   错误 " + wrongCount);
        wordView.setText(wd.w);
        refreshFavBtn();

        // 打乱选项（每题按固定种子生成一致排列，保证返回上一题时标注不错位）
        Random rng = new Random(quizSeed + (long) ptr * 7919L);
        shown = new String[4];
        int r = rng.nextInt(4);
        shown[r] = wd.c;
        List<String> dist = new ArrayList<>(Arrays.asList(wd.d[0], wd.d[1], wd.d[2]));
        Collections.shuffle(dist, rng);
        int k = 0;
        for (int i = 0; i < 4; i++) if (i != r) shown[i] = dist.get(k++);
        correctIdx = r;

        optionsBox.removeAllViews();
        for (int i = 0; i < 4; i++) {
            final int fi = i;
            Button b = optionBtn("ABCD".charAt(fi) + ".  " + shown[i]);
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { if (!answered) onOptionClick((Button) v, fi); }
            });
            optionsBox.addView(b);
        }
        optionsBox.requestLayout();

        // 返回上一题：锁定显示当时的选择结果
        if (answered) {
            Button sb = (Button) optionsBox.getChildAt(selIdx[ptr]);
            if (selOk[ptr]) {
                sb.setTextColor(Color.WHITE);
                bg(sb, C_GREEN, 14);
            } else {
                sb.setTextColor(Color.WHITE);
                bg(sb, C_RED, 14);
                Button cb = (Button) optionsBox.getChildAt(correctIdx);
                cb.setTextColor(Color.WHITE);
                bg(cb, C_GREEN, 14);
            }
            nextBtn.setVisibility(View.VISIBLE);
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
        selIdx[ptr] = i;
        selOk[ptr] = (i == correctIdx);
        recount();
        recordAnswer(selOk[ptr]);
        statsView.setText("正确 " + correctCount + "   错误 " + wrongCount);
        if (selOk[ptr]) {
            clicked.setTextColor(Color.WHITE);
            bg(clicked, C_GREEN, 14);
            // 错题重刷中答对：按全局设置决定是否从错题中移除
            if (mode == MODE_WRONG && rmAfterCorrect) {
                wrongSet.remove(words.get(order.get(ptr)).w);
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
            // 记入错题本
            if (mode != MODE_WRONG) {
                wrongSet.add(words.get(order.get(ptr)).w);
                saveWrong();
            }
            saveSession();
            nextBtn.setVisibility(View.VISIBLE);
        }
    }

    private void prev() {
        if (ptr <= 0) return;
        ptr--;
        saveSession();
        showQuiz();
    }

    // 依据每题的答题记录重算正确/错误数，保证返回上一题后统计仍一致
    private void recount() {
        correctCount = 0;
        int ans = 0;
        for (int i = 0; i < selIdx.length; i++) {
            if (selIdx[i] != -1) { ans++; if (selOk[i]) correctCount++; }
        }
        wrongCount = ans - correctCount;
    }

    // 初始化记分矩阵（-1 表示该题尚未作答）
    private void initSel(int n) {
        selIdx = new int[n];
        selOk = new boolean[n];
        Arrays.fill(selIdx, -1);
    }

    // 重建答题界面（从全览返回或跳到某题时调用）
    private void showQuizScreen() {
        buildQuizBox();
        showQuiz();
    }

    // ---------- 全览页：滑动词都总览，点题号跳转 ----------
    private void showOverview() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(12), dp(16), dp(16));

        // 顶部：标题 + 返回
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = tv("全览 · 第 " + (ptr + 1) + " / " + quizTotal + " 题", 17, C_TEXT, true);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        Button back = btn("返回刷题", 14, C_TEXT, mCard(10, C_CARD));
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showQuizScreen(); }
        });
        head.addView(back, new LinearLayout.LayoutParams(-2, dp(40)));
        box.addView(head);

        // 图例
        TextView legend = tv("绿=答对  红=答错  黄框=当前  灰框=未做", 13, C_SUB, false);
        legend.setPadding(0, dp(12), 0, dp(8));
        box.addView(legend);

        // 网格（由外层滚动承载，可上下滑动）
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        int perRow = 5;
        for (int i = 0; i < quizTotal; i += perRow) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int j = 0; j < perRow && i + j < quizTotal; j++) {
                row.addView(overviewCell(i + j));
            }
            grid.addView(row);
        }
        box.addView(grid);

        fill(box);
    }

    private Button overviewCell(final int i) {
        Button cell = new Button(this);
        cell.setText(String.valueOf(i + 1));
        cell.setTextSize(14);
        cell.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        cell.setLayoutParams(lp);

        boolean done = selIdx != null && selIdx[i] != -1;
        if (done) {
            bg(cell, selOk[i] ? C_GREEN : C_RED, 10);
            cell.setTextColor(Color.WHITE);
        } else {
            bg(cell, C_CARD, 10);
            cell.setTextColor(C_SUB);
        }
        if (i == ptr) {
            GradientDrawable g = mCard(10, done ? (selOk[i] ? C_GREEN : C_RED) : C_CARD);
            g.setStroke(dp(3), 0xFFFACC15);
            cell.setBackground(g);
        }
        cell.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { gotoQuestion(i); }
        });
        return cell;
    }

    private void gotoQuestion(int i) {
        if (i < 0 || i >= quizTotal) return;
        ptr = i;
        saveSession();
        showQuizScreen();
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
                wordIndex.put(o.getString("w"), i);
            }
        } catch (Exception e) {
            words.clear();
        }
    }

    private void loadWrongFromPrefs() {
        wrongSet.clear();
        try {
            for (String s : prefs.getStringSet(KEY_WRONG, new HashSet<String>())) {
                // 兼容旧版本：数字索引 -> 转为单词
                Integer idx = null;
                try { idx = Integer.parseInt(s); } catch (NumberFormatException ignore) {}
                if (idx != null) {
                    if (idx >= 0 && idx < words.size()) s = words.get(idx).w; else continue;
                }
                if (wordIndex.containsKey(s)) wrongSet.add(s);
            }
        } catch (Exception ignore) {}
    }

    private void saveWrong() {
        HashSet<String> set = new HashSet<>();
        for (String w : wrongSet) set.add(w);
        prefs.edit().putStringSet(KEY_WRONG, set).apply();
    }

    // ---------- 错题记录页 ----------
    private void showWrongList() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(16), dp(20), dp(20));

        if (wrongSet.isEmpty()) {
            TextView t = tv("还没有错题～\n去刷题，答错的单词会自动记录在这里", 15, C_SUB, false);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(70), 0, 0);
            box.addView(t);
            fill(box);
            return;
        }

        box.addView(tv("错题记录（" + wrongSet.size() + " 个）", 18, C_TEXT, true));

        // 操作按钮行
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(10), 0, dp(14));
        actions.addView(actionBtn("一键复制", new Runnable() { @Override public void run() { copyWrong(); } }));
        actions.addView(actionBtn("分享导出", new Runnable() { @Override public void run() { shareWrong(); } }));
        actions.addView(actionBtn("清空错题", new Runnable() { @Override public void run() { confirmClearWrong(); } }));
        box.addView(actions);

        List<String> sorted = new ArrayList<>(wrongSet);
        Collections.sort(sorted);
        for (String w : sorted) box.addView(wrongRow(w));

        fill(box);
    }

    private Button actionBtn(String label, final Runnable action) {
        Button b = btn(label, 13, Color.WHITE, mCard(10, C_BLUE));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1f);
        lp.setMargins(dp(3), 0, dp(3), 0);
        b.setLayoutParams(lp);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { action.run(); }
        });
        return b;
    }

    private View wrongRow(final String w) {
        Integer idx = wordIndex.get(w);
        String meaning = (idx != null) ? words.get(idx).c : "";
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = mCard(10, C_CARD);
        g.setStroke(dp(1), C_LINE);
        row.setBackground(g);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
        rlp.setMargins(0, 0, 0, dp(8));
        row.setLayoutParams(rlp);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(12), dp(6), dp(8), 0);
        top.addView(tv(w, 16, C_TEXT, true), new LinearLayout.LayoutParams(0, -2, 1f));
        Button del = btn("✕", 15, C_RED, mCard(8, 0xFFFEE2E2));
        del.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { wrongSet.remove(w); saveWrong(); showWrongList(); }
        });
        top.addView(del, new LinearLayout.LayoutParams(dp(30), dp(30)));
        row.addView(top);

        TextView tm = tv(meaning, 15, C_SUB, false);
        tm.setPadding(dp(12), 0, dp(12), dp(10));
        row.addView(tm);
        return row;
    }

    private String buildWrongText() {
        List<String> sorted = new ArrayList<>(wrongSet);
        Collections.sort(sorted);
        StringBuilder sb = new StringBuilder();
        for (String w : sorted) {
            Integer idx = wordIndex.get(w);
            if (idx != null) sb.append(w).append(' ').append(words.get(idx).c).append('\n');
        }
        return sb.toString().trim();
    }

    private void copyWrong() {
        String text = buildWrongText();
        if (text.isEmpty()) { toast("错题本为空"); return; }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("错题", text));
        toast("已复制 " + wrongSet.size() + " 个错题（含正确意思）");
    }

    private void shareWrong() {
        String text = buildWrongText();
        if (text.isEmpty()) { toast("错题本为空"); return; }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, text);
        try {
            startActivity(Intent.createChooser(send, "导出错题"));
        } catch (Exception e) {
            toast("没有可用的分享应用");
        }
    }

    private void confirmClearWrong() {
        new AlertDialog.Builder(this)
                .setTitle("清空错题本")
                .setMessage("确定要清空全部 " + wrongSet.size() + " 个错题吗？此操作不可恢复。")
                .setPositiveButton("清空", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        wrongSet.clear();
                        saveWrong();
                        showWrongList();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ---------- 收藏功能 ----------
    private void loadFavFromPrefs() {
        favSet.clear();
        try {
            for (String s : prefs.getStringSet(KEY_FAV, new HashSet<String>())) {
                if (wordIndex.containsKey(s)) favSet.add(s);
            }
        } catch (Exception ignore) {}
    }

    private void saveFav() {
        HashSet<String> set = new HashSet<>();
        for (String w : favSet) set.add(w);
        prefs.edit().putStringSet(KEY_FAV, set).apply();
    }

    private void refreshFavBtn() {
        String w = words.get(order.get(ptr)).w;
        boolean f = favSet.contains(w);
        if (favBtn == null) return;
        favBtn.setText(f ? "已收藏 ★" : "收藏 ☆");
        favBtn.setTextColor(f ? C_BLUE : C_SUB);
        favBtn.setBackground(f ? mCard(10, 0xFFFEF3C7) : bgFlat(0xFFEBE7DE, 10));
    }

    private void toggleFav() {
        String w = words.get(order.get(ptr)).w;
        if (favSet.contains(w)) {
            favSet.remove(w);
            toast("已取消收藏：" + w);
        } else {
            favSet.add(w);
            toast("已收藏：" + w);
        }
        saveFav();
        refreshFavBtn();
    }

    private GradientDrawable bgFlat(int color, int r) {
        return mCard(r, color);
    }

    private void showFavList() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(16), dp(20), dp(20));

        if (favSet.isEmpty()) {
            TextView t = tv("收藏夹是空的～\n刷题时点击单词下方的“收藏”即可加入", 15, C_SUB, false);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(70), 0, 0);
            box.addView(t);
            fill(box);
            return;
        }

        box.addView(tv("收藏夹（" + favSet.size() + " 个）", 18, C_TEXT, true));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(10), 0, dp(14));
        actions.addView(actionBtn("一键复制", new Runnable() { @Override public void run() { copyFav(); } }));
        actions.addView(actionBtn("分享导出", new Runnable() { @Override public void run() { shareFav(); } }));
        actions.addView(actionBtn("清空收藏", new Runnable() { @Override public void run() { confirmClearFav(); } }));
        box.addView(actions);

        List<String> sorted = new ArrayList<>(favSet);
        Collections.sort(sorted);
        for (String w : sorted) box.addView(favRow(w));

        fill(box);
    }

    private View favRow(final String w) {
        Integer idx = wordIndex.get(w);
        String meaning = (idx != null) ? words.get(idx).c : "";
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = mCard(10, C_CARD);
        g.setStroke(dp(1), C_LINE);
        row.setBackground(g);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
        rlp.setMargins(0, 0, 0, dp(8));
        row.setLayoutParams(rlp);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(12), dp(6), dp(8), 0);
        top.addView(tv(w, 16, C_TEXT, true), new LinearLayout.LayoutParams(0, -2, 1f));
        Button del = btn("✕", 15, C_RED, mCard(8, 0xFFFEE2E2));
        del.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { favSet.remove(w); saveFav(); showFavList(); }
        });
        top.addView(del, new LinearLayout.LayoutParams(dp(30), dp(30)));
        row.addView(top);

        TextView tm = tv(meaning, 15, C_SUB, false);
        tm.setPadding(dp(12), 0, dp(12), dp(10));
        row.addView(tm);
        return row;
    }

    private String buildFavText() {
        List<String> sorted = new ArrayList<>(favSet);
        Collections.sort(sorted);
        StringBuilder sb = new StringBuilder();
        for (String w : sorted) {
            Integer idx = wordIndex.get(w);
            if (idx != null) sb.append(w).append(' ').append(words.get(idx).c).append('\n');
        }
        return sb.toString().trim();
    }

    private void copyFav() {
        String text = buildFavText();
        if (text.isEmpty()) { toast("收藏夹为空"); return; }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("收藏", text));
        toast("已复制 " + favSet.size() + " 个收藏单词（含意思）");
    }

    private void shareFav() {
        String text = buildFavText();
        if (text.isEmpty()) { toast("收藏夹为空"); return; }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, text);
        try {
            startActivity(Intent.createChooser(send, "导出收藏"));
        } catch (Exception e) {
            toast("没有可用的分享应用");
        }
    }

    private void confirmClearFav() {
        new AlertDialog.Builder(this)
                .setTitle("清空收藏夹")
                .setMessage("确定要清空全部 " + favSet.size() + " 个收藏吗？此操作不可恢复。")
                .setPositiveButton("清空", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        favSet.clear();
                        saveFav();
                        showFavList();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ---------- 设置 ----------
    private void loadSettings() {
        rmAfterCorrect = prefs.getBoolean(KEY_SETTING_RMC, false);
    }

    private void saveSettings() {
        prefs.edit().putBoolean(KEY_SETTING_RMC, rmAfterCorrect).apply();
    }

    private void showSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(16), dp(20), dp(20));

        box.addView(tv("设置", 18, C_TEXT, true));

        // 错题重刷：答对后是否移除
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(8), dp(10));
        row.setBackground(mCard(12, C_CARD));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.setMargins(0, dp(12), 0, 0);
        row.setLayoutParams(rp);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        row.addView(textCol, new LinearLayout.LayoutParams(0, -2, 1f));
        textCol.addView(tv("错题重刷答对后移除", 16, C_TEXT, true));
        TextView hint = tv("开启后，错题重刷中答对的单词会从错题中移除；关闭则错题记录保留所有错题。", 13, C_SUB, false);
        hint.setPadding(0, dp(4), 0, 0);
        textCol.addView(hint);

        Switch sw = new Switch(this);
        sw.setChecked(rmAfterCorrect);
        row.addView(sw, new LinearLayout.LayoutParams(-2, -2));
        sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(android.widget.CompoundButton b, boolean checked) {
                rmAfterCorrect = checked;
                saveSettings();
            }
        });

        box.addView(row);
        fill(box);
    }

    // ---------- 学习统计 ----------
    private static class DayStat { String date; int answered; int correct; }

    private static String todayStr() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private HashMap<String, int[]> loadStatsMap() {
        HashMap<String, int[]> map = new HashMap<>();
        try {
            JSONObject o = new JSONObject(prefs.getString(KEY_STATS, "{}"));
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                JSONArray a = o.getJSONArray(k);
                if (a.length() >= 2) map.put(k, new int[]{a.getInt(0), a.getInt(1)});
            }
        } catch (Exception ignore) {}
        return map;
    }

    private void saveStatsMap(HashMap<String, int[]> map) {
        try {
            JSONObject o = new JSONObject();
            for (Map.Entry<String, int[]> e : map.entrySet()) {
                JSONArray a = new JSONArray();
                a.put(e.getValue()[0]); a.put(e.getValue()[1]);
                o.put(e.getKey(), a);
            }
            prefs.edit().putString(KEY_STATS, o.toString()).apply();
        } catch (Exception ignore) {}
    }

    private void recordAnswer(boolean correct) {
        HashMap<String, int[]> map = loadStatsMap();
        String d = todayStr();
        int[] c = map.get(d);
        if (c == null) c = new int[]{0, 0};
        c[0]++;
        if (correct) c[1]++;
        map.put(d, c);
        saveStatsMap(map);
    }

    // 连续签到天数（今天没答也从昨天开始算，断一天则清零）
    private int calcStreak(HashMap<String, int[]> map) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        Calendar cal = Calendar.getInstance();
        int[] today = map.get(sdf.format(cal.getTime()));
        if (today == null || today[0] <= 0) cal.add(Calendar.DAY_OF_YEAR, -1);
        int streak = 0;
        while (true) {
            String ds = sdf.format(cal.getTime());
            int[] v = map.get(ds);
            if (v == null || v[0] <= 0) break;
            streak++;
            cal.add(Calendar.DAY_OF_YEAR, -1);
        }
        return streak;
    }

    private View buildStatsCard() {
        HashMap<String, int[]> map = loadStatsMap();
        int streak = calcStreak(map);
        int totA = 0, totC = 0;
        for (int[] v : map.values()) { totA += v[0]; totC += v[1]; }
        int todayA = 0, todayC = 0;
        int[] tv = map.get(todayStr());
        if (tv != null) { todayA = tv[0]; todayC = tv[1]; }

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = mCard(16, C_CARD);
        g.setStroke(dp(1), C_LINE);
        card.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(24), 0, 0);
        card.setLayoutParams(lp);
        card.setPadding(dp(16), dp(14), dp(16), dp(6));

        // 标题 + 连续天数（火苗）
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = tv("我的努力", 16, C_TEXT, true);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView fire = tv("🔥 连续 " + streak + (streak <= 1 ? " 天" : " 天"), 15, 0xFFF59E0B, true);
        head.addView(fire);
        card.addView(head);

        // 三个数字块
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(14), 0, dp(8));
        row.addView(numBlock("今日刷题", todayA, 0xFF22C55E));
        row.addView(numBlock("累计刷题", totA, C_BLUE));
        row.addView(numBlock("总正确率", (totA == 0) ? 0 : Math.round(100f * totC / totA), 0xFFF59E0B));
        card.addView(row);

        // 查看趋势入口
        Button more = btn("查看学习日历 → 回顾每一天的刷题", 14, C_BLUE, mCard(12, 0xFFE8F0FE));
        more.setAllCaps(false);
        more.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showStats(); }
        });
        card.addView(more, new LinearLayout.LayoutParams(-1, dp(46)));

        return card;
    }

    private View numBlock(String label, int value, int color) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        b.setLayoutParams(lp);
        TextView n = tv(String.valueOf(value), 26, color, true);
        n.setGravity(Gravity.CENTER_HORIZONTAL);
        b.addView(n);
        TextView l = tv(label, 13, C_SUB, false);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        l.setPadding(0, dp(4), 0, 0);
        b.addView(l);
        return b;
    }

    // 统计详情页（月份日历可视化）
    private void showStats() {
        statsMonthOffset = 0;
        statsSel = todayStr();
        renderStatsPage();
    }

    private void renderStatsPage() {
        HashMap<String, int[]> map = loadStatsMap();
        int streak = calcStreak(map);
        int totA = 0, totC = 0;
        for (int[] v : map.values()) { totA += v[0]; totC += v[1]; }
        int todayA = 0, todayC = 0;
        int[] tv = map.get(todayStr());
        if (tv != null) { todayA = tv[0]; todayC = tv[1]; }
        int todayPct = (todayA == 0) ? 0 : Math.round(100f * todayC / todayA);
        int totPct = (totA == 0) ? 0 : Math.round(100f * totC / totA);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(12), dp(18), dp(20));

        // 头部：返回
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = tv("我的努力 · 学习统计", 18, C_TEXT, true);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        Button back = btn("返回", 14, C_TEXT, mCard(10, C_CARD));
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showHome(); }
        });
        head.addView(back, new LinearLayout.LayoutParams(-2, dp(40)));
        box.addView(head);

        // 连续签到卡
        LinearLayout streakCard = new LinearLayout(this);
        streakCard.setOrientation(LinearLayout.HORIZONTAL);
        streakCard.setGravity(Gravity.CENTER_VERTICAL);
        streakCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        streakCard.setBackground(mCard(14, 0xFF1F1E2D));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.setMargins(0, dp(12), 0, 0);
        streakCard.setLayoutParams(slp);
        TextView fire = tv("🔥", 30, 0xFFFCD34D, true);
        streakCard.addView(fire);
        LinearLayout sc = new LinearLayout(this);
        sc.setOrientation(LinearLayout.VERTICAL);
        streakCard.addView(sc, new LinearLayout.LayoutParams(0, -2, 1f));
        sc.addView(tv("已连续坚持 " + streak + " 天", 18, Color.WHITE, true));
        TextView sub = tv(streak > 0 ? "继续保持，火苗别灭！" : "今天还没开始，刷几题让火苗燃起来", 13, 0xFFB4B3C0, false);
        sub.setPadding(0, dp(4), 0, 0);
        sc.addView(sub);
        box.addView(streakCard);

        // 今日与累计
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.HORIZONTAL);
        grid.setPadding(0, dp(14), 0, 0);
        grid.addView(bigBlock("今日刷题", todayA, "题", 0xFF22C55E));
        grid.addView(bigBlock("今日正确率", todayPct, "%", 0xFF22C55E));
        grid.addView(bigBlock("累计刷题", totA, "题", C_BLUE));
        grid.addView(bigBlock("累计正确率", totPct, "%", 0xFFF59E0B));
        box.addView(grid);

        // 月份导航：◀ 标题 ▶
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(0, dp(18), 0, dp(8));
        Button pre = btn("◀", 16, C_TEXT, mCard(10, C_LINE));
        pre.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { statsMonthOffset--; renderStatsPage(); }
        });
        nav.addView(pre, new LinearLayout.LayoutParams(-2, dp(40)));
        TextView navTitle = tv(monthTitle(), 17, C_TEXT, true);
        navTitle.setGravity(Gravity.CENTER);
        nav.addView(navTitle, new LinearLayout.LayoutParams(0, -2, 1f));
        Button nxt = btn("▶", 16, C_TEXT, mCard(10, C_LINE));
        nxt.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { statsMonthOffset++; renderStatsPage(); }
        });
        nav.addView(nxt, new LinearLayout.LayoutParams(-2, dp(40)));
        box.addView(nav);

        // 日历主体（月份视图）
        box.addView(monthCalendar(map));

        // 选中日期详情
        box.addView(dayDetail(map));

        TextView empty = tv((totA == 0) ? "还没有答题记录，去刷几题吧。" : "", 13, C_SUB, false);
        if (totA == 0) { empty.setPadding(0, dp(6), 0, 0); box.addView(empty); }

        fill(box);
    }

    private String monthTitle() {
        Calendar mc = Calendar.getInstance();
        mc.add(Calendar.MONTH, statsMonthOffset);
        return (mc.get(Calendar.YEAR)) + "年" + (mc.get(Calendar.MONTH) + 1) + "月";
    }

    private int statsColor(int n) {
        if (n <= 0) return 0xFFECE8DF;
        if (n < 5) return 0xFFBFE3D0;
        if (n < 15) return 0xFF7FC98A;
        if (n < 30) return 0xFF43B75C;
        return 0xFF22C55E;
    }

    // 月视图：日历网格
    private View monthCalendar(HashMap<String, int[]> map) {
        SimpleDateFormat key = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        Calendar mc = Calendar.getInstance();
        mc.set(Calendar.DAY_OF_MONTH, 1);
        mc.add(Calendar.MONTH, statsMonthOffset);
        int firstDow = mc.get(Calendar.DAY_OF_WEEK); // 1=日
        int daysInMonth = mc.getActualMaximum(Calendar.DAY_OF_MONTH);
        int year = mc.get(Calendar.YEAR), month = mc.get(Calendar.MONTH);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(mCard(12, C_CARD));
        box.setPadding(dp(10), dp(10), dp(10), dp(10));

        String[] wt = {"日", "一", "二", "三", "四", "五", "六"};
        int[] wc = {0xFFEF4444, C_SUB, C_SUB, C_SUB, C_SUB, C_SUB, 0xFFEF4444};
        LinearLayout hdr = new LinearLayout(this);
        hdr.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 7; i++) {
            TextView t = tv(wt[i], 13, wc[i], true);
            t.setGravity(Gravity.CENTER);
            hdr.addView(t, new LinearLayout.LayoutParams(0, dp(26), 1f));
        }
        box.addView(hdr);

        int[][] rows = new int[6][7];
        int day = 1;
        for (int r = 0; r < 6; r++) {
            for (int c = 0; c < 7; c++) {
                if (r == 0 && c < firstDow - 1) rows[r][c] = 0;
                else if (day <= daysInMonth) rows[r][c] = day++;
                else rows[r][c] = 0;
            }
        }

        for (int r = 0; r < 6; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            boolean allEmpty = true;
            for (int c = 0; c < 7; c++) {
                if (rows[r][c] != 0) allEmpty = false;
            }
            if (allEmpty) continue;
            for (int c = 0; c < 7; c++) {
                int dd = rows[r][c];
                if (dd == 0) {
                    View blank = new View(this);
                    row.addView(blank, new LinearLayout.LayoutParams(0, dp(64), 1f));
                    continue;
                }
                Calendar d = Calendar.getInstance();
                d.set(year, month, dd);
                String ds = key.format(d.getTime());
                int[] v = map.get(ds);
                int n = (v == null) ? 0 : v[0];
                boolean today = ds.equals(todayStr());
                final String fds = ds;

                LinearLayout cell = new LinearLayout(this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER);
                cell.setPadding(0, dp(4), 0, dp(4));
                GradientDrawable cg = mCard(8, (n > 0) ? statsColor(n) : C_CARD);
                if (today) cg.setStroke(dp(2), C_BLUE);
                cell.setBackground(cg);
                cell.setClickable(true);
                cell.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { statsSel = fds; renderStatsPage(); }
                });
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, dp(64), 1f);
                clp.setMargins(dp(2), dp(2), dp(2), dp(2));
                cell.setLayoutParams(clp);

                TextView dnum = tv(today ? dd + " 今" : String.valueOf(dd), 14, today ? C_BLUE : 0xFF4B5563, today);
                dnum.setGravity(Gravity.CENTER);
                cell.addView(dnum);
                if (n > 0) {
                    TextView cnt = tv(String.valueOf(n), 15, 0xFF1F6B45, true);
                    cnt.setGravity(Gravity.CENTER);
                    cell.addView(cnt);
                } else {
                    TextView ed = tv("·", 14, 0xFFCFC9BD, true);
                    ed.setGravity(Gravity.CENTER);
                    cell.addView(ed);
                }
                row.addView(cell);
            }
            box.addView(row);
        }
        return box;
    }

    // 选中日期的明细
    private View dayDetail(HashMap<String, int[]> map) {
        SimpleDateFormat label = new SimpleDateFormat("yyyy年M月d日 EEEE", Locale.CHINA);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(mCard(12, C_CARD));
        GradientDrawable dg = mCard(12, C_CARD);
        dg.setStroke(dp(1), C_LINE);
        card.setBackground(dg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(12), 0, 0);
        card.setLayoutParams(lp);

        int[] v = map.get(statsSel);
        int n = (v == null) ? 0 : v[0];
        int c = (v == null) ? 0 : v[1];
        int pct = (n == 0) ? 0 : Math.round(100f * c / n);
        boolean isToday = statsSel.equals(todayStr());

        TextView head = tv(label.format(dateOf(statsSel)) + (isToday ? "（今天）" : ""), 15, C_TEXT, true);
        head.setGravity(Gravity.CENTER);
        card.addView(head);

        if (n == 0) {
            TextView t = tv("这一天没有刷题记录，点上方 ◀ ▶ 切换月份浏览其他日期。", 13, C_SUB, false);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(8), 0, 0);
            card.addView(t);
        } else {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(12), 0, 0);
            row.addView(miniBlock("刷题", n + " 题", 0xFF22C55E));
            row.addView(miniBlock("答对", c + " 题", C_BLUE));
            row.addView(miniBlock("正确率", pct + "%", 0xFFF59E0B));
            card.addView(row);
        }
        return card;
    }

    private View miniBlock(String label, String value, int color) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER_HORIZONTAL);
        b.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        TextView val = tv(value, 20, color, true);
        val.setGravity(Gravity.CENTER_HORIZONTAL);
        b.addView(val);
        TextView l = tv(label, 12, C_SUB, false);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        l.setPadding(0, dp(3), 0, 0);
        b.addView(l);
        return b;
    }

    private Date dateOf(String ymd) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(ymd);
        } catch (Exception e) { return new Date(); }
    }

    private View bigBlock(String label, int value, String unit, int color) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER);
        b.setBackground(mCard(10, C_CARD));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(86), 1f);
        lp.setMargins(dp(3), 0, dp(3), 0);
        b.setLayoutParams(lp);
        b.addView(tv(value + unit, 20, color, true));
        TextView l = tv(label, 12, C_SUB, false);
        l.setPadding(0, dp(4), 0, 0);
        b.addView(l);
        return b;
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
            o.put("seed", quizSeed);
            JSONArray si = new JSONArray();
            for (int v : selIdx) si.put(v);
            o.put("sel", si);
            JSONArray so = new JSONArray();
            for (boolean b : selOk) so.put(b ? 1 : 0);
            o.put("selok", so);
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
            quizSeed = o.optLong("seed", new Random().nextLong());
            // 恢复记分矩阵（旧版本无则初始化）
            initSel(quizTotal);
            JSONArray si = o.optJSONArray("sel");
            JSONArray so = o.optJSONArray("selok");
            if (si != null && so != null && si.length() == quizTotal) {
                for (int i = 0; i < si.length(); i++) {
                    selIdx[i] = si.getInt(i);
                    selOk[i] = so.getInt(i) == 1;
                }
                recount();
            }
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