package com.xeroxurf.printplugin;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.os.LocaleListCompat;
import androidx.preference.PreferenceManager;

import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    private static final int NOTIF_PERMISSION_REQUEST = 100;
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int IPP_PORT = 631;

    private LinearLayout itemPrinterModel;
    private TextView textPrinterModel;
    private TextView textPrinterModelValue;
    private LinearLayout itemAppLanguage;
    private TextView textAppLanguageValue;
    private MaterialButton btnDiscover;
    private TextInputEditText inputPrinterName;
    private TextInputEditText inputPrinterIp;
    private LinearLayout itemScanDoc;
    private LinearLayout itemTestNetwork;
    private LinearLayout itemPrintTestPage;
    private LinearLayout itemPrintViaAndroid;
    private LinearLayout itemJobHistory;
    private LinearLayout itemViewLogs;
    private TextView textSetupInstructions;
    private LinearLayout itemCentreware;
    private MaterialCardView itemGithub;
    private View cardScanDoc;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        itemPrinterModel = findViewById(R.id.item_printer_model);
        textPrinterModel = findViewById(R.id.text_printer_model);
        textPrinterModelValue = findViewById(R.id.text_printer_model_value);
        itemAppLanguage = findViewById(R.id.item_app_language);
        textAppLanguageValue = findViewById(R.id.text_app_language_value);
        btnDiscover = findViewById(R.id.btn_discover);
        inputPrinterName = findViewById(R.id.input_printer_name);
        inputPrinterIp = findViewById(R.id.input_printer_ip);
        itemScanDoc = findViewById(R.id.item_scan_doc);
        itemTestNetwork = findViewById(R.id.item_test_network);
        itemPrintTestPage = findViewById(R.id.item_print_test_page);
        itemPrintViaAndroid = findViewById(R.id.item_print_via_android);
        itemJobHistory = findViewById(R.id.item_job_history);
        itemViewLogs = findViewById(R.id.item_view_logs);
        textSetupInstructions = findViewById(R.id.text_setup_instructions);
        itemCentreware = findViewById(R.id.item_centreware);
        itemGithub = findViewById(R.id.item_github);
        cardScanDoc = findViewById(R.id.card_scan_doc);

        setupUI();

        // Request notification permission on API 33+
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIF_PERMISSION_REQUEST);
            }
        }
    }

    private void setupUI() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Load initial values
        String model = prefs.getString("printer_model", "workcentre_3025");
        updateModelUI(model);

        String currentName = prefs.getString("printer_name", "phaser_3020".equals(model) ? "Xerox Phaser 3020" : "Xerox WorkCentre 3025");
        inputPrinterName.setText(currentName);

        String currentIp = prefs.getString("printer_ip", "192.168.1.11");
        inputPrinterIp.setText(currentIp);

        updateLanguageUI();

        // Check first launch
        if (!prefs.contains("printer_model")) {
            showModelSelectionDialog();
        }

        // Listeners
        itemPrinterModel.setOnClickListener(v -> showModelSelectionDialog());
        itemAppLanguage.setOnClickListener(v -> showLanguageSelectionDialog());
        btnDiscover.setOnClickListener(v -> runPrinterDiscovery());

        inputPrinterName.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                prefs.edit().putString("printer_name", s.toString().trim()).apply();
            }
        });

        inputPrinterIp.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                prefs.edit().putString("printer_ip", s.toString().trim()).apply();
            }
        });

        itemScanDoc.setOnClickListener(v -> startActivity(new Intent(this, ScanActivity.class)));
        itemTestNetwork.setOnClickListener(v -> runNetworkTest());
        itemPrintTestPage.setOnClickListener(v -> runPrintTestPage());
        itemPrintViaAndroid.setOnClickListener(v -> runPrintViaAndroid());
        itemJobHistory.setOnClickListener(v -> startActivity(new Intent(this, JobHistoryActivity.class)));
        itemViewLogs.setOnClickListener(v -> showLogViewer());
        itemCentreware.setOnClickListener(v -> startActivity(new Intent(this, CentreWareActivity.class)));
        itemGithub.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/xphilxs/Xerox-3020-3025-Print-Plugin-for-Android"));
            startActivity(intent);
        });
    }

    private void updateModelUI(String modelKey) {
        boolean isPhaser = "phaser_3020".equals(modelKey);
        String modelName = isPhaser ? "Xerox Phaser 3020" : "Xerox WorkCentre 3025";
        textPrinterModel.setText(getString(isPhaser ? R.string.model_mode_print_only : R.string.model_mode_print_scan));
        textPrinterModelValue.setText(modelName);
        cardScanDoc.setVisibility(isPhaser ? View.GONE : View.VISIBLE);
        updateInfoSummary(modelName);
    }

    private void updateInfoSummary(String modelName) {
        if (textSetupInstructions != null) {
            String baseSummary = getString(R.string.pref_info_summary);
            String appName = getString(R.string.app_name);
            String updatedSummary = baseSummary
                    .replace("Xerox WorkCentre 3025", modelName)
                    .replace("Xerox Phaser 3020", modelName)
                    .replace("Xerox 3025 Print Plugin", appName)
                    .replace("Xerox 3025", "Xerox 3020/3025");
            textSetupInstructions.setText(updatedSummary);
        }
    }

    private void updateLanguageUI() {
        LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
        if (locales.isEmpty()) {
            textAppLanguageValue.setText(R.string.lang_system);
        } else {
            Locale locale = locales.get(0);
            textAppLanguageValue.setText(locale != null ? locale.getDisplayName() : "English");
        }
    }

    private void showModelSelectionDialog() {
        String[] options = new String[]{
                getString(R.string.model_wc3025),
                getString(R.string.model_phaser3020)
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_select_model_title)
                .setCancelable(false)
                .setItems(options, (dialog, which) -> {
                    String selectedModel = (which == 1) ? "phaser_3020" : "workcentre_3025";
                    applyModelSelection(selectedModel);
                })
                .show();
    }

    private void applyModelSelection(String modelKey) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String oldModel = prefs.getString("printer_model", "workcentre_3025");
        prefs.edit().putString("printer_model", modelKey).apply();

        String oldDefaultName = "phaser_3020".equals(oldModel) ? "Xerox Phaser 3020" : "Xerox WorkCentre 3025";
        String newDefaultName = "phaser_3020".equals(modelKey) ? "Xerox Phaser 3020" : "Xerox WorkCentre 3025";
        String currentName = prefs.getString("printer_name", oldDefaultName);

        if (currentName.equals(oldDefaultName) || !prefs.contains("printer_name")) {
            prefs.edit().putString("printer_name", newDefaultName).apply();
            inputPrinterName.setText(newDefaultName);
        }

        updateModelUI(modelKey);
    }

    private void showLanguageSelectionDialog() {
        String[] entries = getResources().getStringArray(R.array.language_entries);
        String[] values = getResources().getStringArray(R.array.language_values);

        new AlertDialog.Builder(this)
                .setTitle(R.string.pref_app_language)
                .setItems(entries, (dialog, which) -> {
                    String langCode = values[which];
                    if ("system".equals(langCode)) {
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList());
                    } else {
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(langCode));
                    }
                    updateLanguageUI();
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private String getPrinterIp() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        return prefs.getString("printer_ip", "192.168.1.11");
    }

    private void runPrinterDiscovery() {
        PrinterDiscoveryManager discoveryManager = new PrinterDiscoveryManager(this);
        ArrayList<PrinterDiscoveryManager.DiscoveredPrinter> allPrinters = new ArrayList<>();

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.discover_title)
                .setMessage(getString(R.string.discover_scanning))
                .setCancelable(false)
                .create();
        dialog.show();

        discoveryManager.startDiscovery(new PrinterDiscoveryManager.DiscoveryListener() {
            @Override
            public void onPrinterFound(PrinterDiscoveryManager.DiscoveredPrinter printer) {
                runOnUiThread(() -> allPrinters.add(printer));
            }

            @Override
            public void onDiscoveryComplete(List<PrinterDiscoveryManager.DiscoveredPrinter> printers) {
                runOnUiThread(() -> {
                    dialog.dismiss();
                    discoveryManager.destroy();
                    showDiscoveryResults(allPrinters);
                });
            }

            @Override
            public void onDiscoveryFailed(String error) {
                runOnUiThread(() -> {
                    dialog.dismiss();
                    discoveryManager.destroy();
                    Toast.makeText(SettingsActivity.this,
                            getString(R.string.discover_failed, error),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showDiscoveryResults(List<PrinterDiscoveryManager.DiscoveredPrinter> printers) {
        if (printers.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.discover_title)
                    .setMessage(R.string.discover_no_printers)
                    .setPositiveButton(R.string.dialog_ok, null)
                    .show();
            return;
        }

        String[] items = new String[printers.size()];
        for (int i = 0; i < printers.size(); i++) {
            PrinterDiscoveryManager.DiscoveredPrinter p = printers.get(i);
            items[i] = p.name + "\n" + p.host + ":" + p.port;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.discover_select)
                .setItems(items, (dialog, which) -> {
                    PrinterDiscoveryManager.DiscoveredPrinter selected = printers.get(which);
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                    prefs.edit()
                            .putString("printer_ip", selected.host)
                            .putString("printer_name", selected.name)
                            .apply();

                    inputPrinterName.setText(selected.name);
                    inputPrinterIp.setText(selected.host);

                    Toast.makeText(this,
                            getString(R.string.discover_configured, selected.name),
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void runNetworkTest() {
        String ip = getPrinterIp();

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.net_test_title)
                .setMessage(getString(R.string.net_test_testing, ip))
                .setCancelable(false)
                .create();
        dialog.show();

        new Thread(() -> {
            StringBuilder result = new StringBuilder();
            boolean success = true;

            result.append(getString(R.string.net_test_target, ip));
            try {
                long start = System.currentTimeMillis();
                InetAddress addr = InetAddress.getByName(ip);
                long elapsed = System.currentTimeMillis() - start;
                result.append(getString(R.string.net_test_dns_ok, addr.getHostAddress(), elapsed));
            } catch (Exception e) {
                result.append(getString(R.string.net_test_dns_fail, e.getMessage()));
                success = false;
            }

            if (success) {
                try {
                    long start = System.currentTimeMillis();
                    boolean reachable = InetAddress.getByName(ip).isReachable(CONNECT_TIMEOUT_MS);
                    long elapsed = System.currentTimeMillis() - start;
                    if (reachable) {
                        result.append(getString(R.string.net_test_ping_ok, elapsed));
                    } else {
                        result.append(getString(R.string.net_test_ping_warn, elapsed));
                    }
                } catch (Exception e) {
                    result.append(getString(R.string.net_test_ping_fail, e.getMessage()));
                }
            }

            if (success) {
                Socket socket = new Socket();
                try {
                    long start = System.currentTimeMillis();
                    socket.connect(new InetSocketAddress(ip, 9100), CONNECT_TIMEOUT_MS);
                    long elapsed = System.currentTimeMillis() - start;
                    result.append(getString(R.string.net_test_port9100_ok, elapsed));
                } catch (IOException e) {
                    result.append(getString(R.string.net_test_port9100_warn));
                } finally {
                    try { socket.close(); } catch (IOException ignored) {}
                }
            }

            if (success) {
                Socket socket = new Socket();
                try {
                    long start = System.currentTimeMillis();
                    socket.connect(new InetSocketAddress(ip, IPP_PORT), CONNECT_TIMEOUT_MS);
                    long elapsed = System.currentTimeMillis() - start;
                    result.append(getString(R.string.net_test_ipp_ok, IPP_PORT, elapsed));
                } catch (IOException e) {
                    result.append(getString(R.string.net_test_ipp_fail, IPP_PORT, e.getMessage()));
                    success = false;
                } finally {
                    try { socket.close(); } catch (IOException ignored) {}
                }
            }

            result.append("\n");
            if (success) {
                result.append(getString(R.string.net_test_ready));
            } else {
                result.append(getString(R.string.net_test_unreachable));
            }

            String finalMessage = result.toString();
            runOnUiThread(() -> {
                dialog.setMessage(finalMessage);
                dialog.setCancelable(true);
                dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.dialog_ok),
                        (d, w) -> d.dismiss());
            });
        }).start();
    }

    private void runPrintTestPage() {
        String ip = getPrinterIp();

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.print_test_title)
                .setMessage(getString(R.string.print_test_sending, ip))
                .setCancelable(false)
                .create();
        dialog.show();

        new Thread(() -> {
            String resultMessage;
            try {
                byte[] urfData = loadAsset("test_page.urf");
                IppClient.IppResult result = IppClient.sendPrintJob(ip, urfData, "Test Page");

                if (result.success) {
                    resultMessage = getString(R.string.print_test_success, urfData.length, ip);
                } else {
                    resultMessage = getString(R.string.print_test_ipp_failed, result.message);
                }
            } catch (IOException e) {
                resultMessage = getString(R.string.print_test_failed, e.getMessage());
            }

            String finalMessage = resultMessage;
            runOnUiThread(() -> {
                dialog.setMessage(finalMessage);
                dialog.setCancelable(true);
                dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.dialog_ok),
                        (d, w) -> d.dismiss());
            });
        }).start();
    }

    private void runPrintViaAndroid() {
        PrintManager printManager = (PrintManager) getSystemService(Context.PRINT_SERVICE);

        PrintDocumentAdapter adapter = new PrintDocumentAdapter() {
            @Override
            public void onLayout(android.print.PrintAttributes oldAttributes,
                                 android.print.PrintAttributes newAttributes,
                                 android.os.CancellationSignal cancellationSignal,
                                 LayoutResultCallback callback,
                                 android.os.Bundle extras) {
                PrintDocumentInfo info = new PrintDocumentInfo.Builder("test-document.pdf")
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(1)
                        .build();
                callback.onLayoutFinished(info, true);
            }

            @Override
            public void onWrite(android.print.PageRange[] pages,
                                android.os.ParcelFileDescriptor destination,
                                android.os.CancellationSignal cancellationSignal,
                                WriteResultCallback callback) {
                try {
                    byte[] pdf = generateMinimalPdf();
                    FileOutputStream out = new FileOutputStream(destination.getFileDescriptor());
                    out.write(pdf);
                    out.close();
                    callback.onWriteFinished(new android.print.PageRange[]{android.print.PageRange.ALL_PAGES});
                } catch (IOException e) {
                    callback.onWriteFailed(e.getMessage());
                }
            }
        };

        printManager.print("Test Document", adapter,
                new PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .setResolution(new PrintAttributes.Resolution("600dpi", "600 dpi", 600, 600))
                        .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
                        .build());
    }

    private byte[] generateMinimalPdf() {
        String p1 = "BT\n" +
                "/F1 36 Tf 50 780 Td (Print Quality Test) Tj\n" +
                "/F1 12 Tf 0 -50 Td (Normal text at 12pt) Tj\n" +
                "0 -20 Td (The quick brown fox jumps over the lazy dog.) Tj\n" +
                "0 -20 Td (ABCDEFGHIJKLMNOPQRSTUVWXYZ 0123456789) Tj\n" +
                "/F1 8 Tf 0 -30 Td (Small 8pt: Should be readable at 600 DPI) Tj\n" +
                "/F1 24 Tf 0 -40 Td (Large 24pt heading) Tj\n" +
                "/F1 12 Tf 0 -30 Td (Repeated patterns:) Tj\n" +
                "0 -20 Td (||||||||||||||||||||||||||||||||||||||||) Tj\n" +
                "0 -20 Td (========================================) Tj\n" +
                "0 -20 Td (########################################) Tj\n" +
                "/F1 14 Tf 0 -40 Td (Page 1 of 2) Tj\n" +
                "ET\n" +
                "0.5 w 50 430 m 545 430 l S\n";
        String p2 = "BT\n" +
                "/F1 28 Tf 50 780 Td (Page 2: Layout Test) Tj\n" +
                "/F1 12 Tf 50 720 Td (Left aligned) Tj 350 720 Td (Right area) Tj\n" +
                "/F1 16 Tf 50 680 Td (Medium 16pt heading) Tj\n" +
                "/F1 10 Tf 50 650 Td (Body text below the heading for layout testing.) Tj\n" +
                "50 635 Td (Row 1: 100 200 300 400 500) Tj\n" +
                "50 620 Td (Row 2: 150 250 350 450 550) Tj\n" +
                "/F1 14 Tf 50 480 Td (End of test - Page 2 of 2) Tj\n" +
                "ET\n";
        String pdf = "%PDF-1.4\n" +
                "1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n" +
                "2 0 obj<</Type/Pages/Kids[3 0 R 6 0 R]/Count 2>>endobj\n" +
                "3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 595 842]" +
                "/Contents 4 0 R/Resources<</Font<</F1 5 0 R>>>>>>endobj\n" +
                "4 0 obj<</Length " + p1.length() + ">>stream\n" + p1 + "endstream\nendobj\n" +
                "5 0 obj<</Type/Font/Subtype/Type1/BaseFont/Helvetica>>endobj\n" +
                "6 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 595 842]" +
                "/Contents 7 0 R/Resources<</Font<</F1 5 0 R>>>>>>endobj\n" +
                "7 0 obj<</Length " + p2.length() + ">>stream\n" + p2 + "endstream\nendobj\n" +
                "xref\n0 8\n" +
                "0000000000 65535 f \n0000000009 00000 n \n0000000058 00000 n \n" +
                "0000000115 00000 n \n0000000266 00000 n \n0000000900 00000 n \n" +
                "0000000977 00000 n \n0000001100 00000 n \n" +
                "trailer<</Size 8/Root 1 0 R>>\nstartxref\n1400\n%%EOF";
        return pdf.getBytes();
    }

    private void showLogViewer() {
        String logs = PrintLog.exportAsText();
        if (logs.isEmpty()) logs = getString(R.string.logs_empty);

        TextView textView = new TextView(this);
        textView.setText(logs);
        textView.setTextSize(11);
        textView.setPadding(32, 16, 32, 16);
        textView.setTypeface(android.graphics.Typeface.MONOSPACE);
        textView.setMovementMethod(new ScrollingMovementMethod());
        textView.setVerticalScrollBarEnabled(true);

        new AlertDialog.Builder(this)
                .setTitle(R.string.logs_title)
                .setView(textView)
                .setPositiveButton(R.string.dialog_close, null)
                .setNeutralButton(R.string.dialog_copy, (d, w) -> {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("Print Logs",
                            PrintLog.exportAsText()));
                    Toast.makeText(this, R.string.logs_copied, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.dialog_clear, (d, w) -> {
                    PrintLog.clear();
                    Toast.makeText(this, R.string.logs_cleared, Toast.LENGTH_SHORT).show();
                    showLogViewer();
                })
                .show();
    }

    private byte[] loadAsset(String filename) throws IOException {
        InputStream is = getAssets().open(filename);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int len;
        while ((len = is.read(chunk)) != -1) {
            buffer.write(chunk, 0, len);
        }
        is.close();
        return buffer.toByteArray();
    }
}
