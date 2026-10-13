package com.c22dev.muguet.android;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** A small Java launcher for selecting legally owned game data before starting NativeActivity. */
public final class MainActivity extends Activity {
    private static final int REQUEST_GAME_ZIP = 4027;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextView status;
    private Button startButton;
    private Button importButton;
    private ProgressBar progress;

    private File supportDir() {
        return new File(getFilesDir(), "Muguet");
    }
    private File gameZip() {
        return new File(supportDir(), "game.zip");
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        refreshState();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(24), dp(24), dp(28));
        root.setBackgroundColor(0xFFF6F3EE);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("MUGUET  /  ANDROID");
        title.setTextColor(0xFF20252A);
        title.setTextSize(27);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView sub = new TextView(this);
        sub.setText("A native Android experiment for the Muguet GTA V WebAssembly port");
        sub.setTextColor(0xFF58616A);
        sub.setTextSize(15);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(12), 0, dp(20));
        root.addView(sub, matchWrap());

        TextView note = new TextView(this);
        note.setText("Use your own legally obtained game ZIP. The archive must contain data/ and b/<build>/ with game.wasm and shaders/. The game itself is not included in this app.\n\nBasic touch map: drag in the left half to move, drag elsewhere to look around, press the lower-right zones for fire/jump/action, and tap the upper-left corner for pause.");
        note.setTextColor(0xFF3B4248);
        note.setTextSize(15);
        note.setLineSpacing(dp(4), 1.0f);
        note.setPadding(dp(16), dp(16), dp(16), dp(16));
        note.setBackgroundColor(0xFFEAE5DC);
        root.addView(note, matchWrap());

        status = new TextView(this);
        status.setTextColor(0xFF31373D);
        status.setTextSize(14);
        status.setPadding(0, dp(18), 0, dp(12));
        root.addView(status, matchWrap());

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(dp(48), dp(48)));

        importButton = new Button(this);
        importButton.setText("Choose game ZIP");
        importButton.setAllCaps(false);
        importButton.setOnClickListener(v -> chooseZip());
        LinearLayout.LayoutParams buttonParams = matchWrap();
        buttonParams.topMargin = dp(12);
        root.addView(importButton, buttonParams);

        startButton = new Button(this);
        startButton.setText("Start Muguet");
        startButton.setAllCaps(false);
        startButton.setOnClickListener(v -> startGame());
        LinearLayout.LayoutParams startParams = matchWrap();
        startParams.topMargin = dp(8);
        root.addView(startButton, startParams);

        setContentView(scroll);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private void refreshState() {
        boolean exists = gameZip().isFile() && gameZip().length() > 0;
        startButton.setEnabled(exists);
        status.setText(exists
            ? "Game ZIP found (" + android.text.format.Formatter.formatFileSize(this, gameZip().length()) + "). Tap Start Muguet to import and launch."
            : "No game ZIP selected yet. Choose the archive to continue.");
    }

    private void chooseZip() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        try {
            startActivityForResult(intent, REQUEST_GAME_ZIP);
        } catch (Exception e) {
            intent.setType("application/octet-stream");
            startActivityForResult(intent, REQUEST_GAME_ZIP);
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_GAME_ZIP || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        importButton.setEnabled(false);
        startButton.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        status.setText("Copying and checking the archive. Large archives may take a while...");
        worker.execute(() -> importZip(uri));
    }

    private void importZip(Uri uri) {
        File dir = supportDir();
        File temp = new File(dir, "game.zip.partial");
        File dest = gameZip();
        try {
            if (!dir.exists() && !dir.mkdirs()) throw new Exception("Cannot create app storage directory");
            try (InputStream in = getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(temp)) {
                if (in == null) throw new Exception("The selected document could not be opened");
                byte[] buffer = new byte[1024 * 1024];
                int n;
                while ((n = in.read(buffer)) >= 0) if (n > 0) out.write(buffer, 0, n);
                out.getFD().sync();
            }
            validateZip(temp);
            if (dest.exists() && !dest.delete()) throw new Exception("Cannot replace previous archive");
            if (!temp.renameTo(dest)) throw new Exception("Cannot finalize copied archive");
            runOnUiThread(() -> {
                progress.setVisibility(View.GONE);
                importButton.setEnabled(true);
                refreshState();
                Toast.makeText(this, "Game ZIP ready", Toast.LENGTH_LONG).show();
            });
        } catch (Exception e) {
            temp.delete();
            runOnUiThread(() -> {
                progress.setVisibility(View.GONE);
                importButton.setEnabled(true);
                startButton.setEnabled(false);
                status.setText("Import failed: " + e.getMessage() + "\nChoose the ZIP used by the Muguet web port, not an installer or game executable.");
                Toast.makeText(this, "That ZIP does not match the expected game format", Toast.LENGTH_LONG).show();
            });
        }
    }

    private void validateZip(File file) throws Exception {
        boolean manifest = false, shaders = false, wasm = false;
        try (ZipFile zip = new ZipFile(file)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.matches("(^|.*/)data/manifest\\.json")) manifest = true;
                if (name.matches("(^|.*/)b/[^/]+/shaders/index\\.json")) shaders = true;
                if (name.matches("(^|.*/)b/[^/]+/game\\.wasm")) wasm = true;
            }
        }
        if (!manifest || !shaders || !wasm) {
            throw new Exception("Required data/manifest.json, b/<build>/shaders/index.json and b/<build>/game.wasm entries are missing");
        }
    }

    private void startGame() {
        if (!gameZip().isFile()) { refreshState(); return; }
        startActivity(new Intent(this, GameActivity.class));
    }

    @Override protected void onDestroy() {
        worker.shutdown();
        super.onDestroy();
    }
}
