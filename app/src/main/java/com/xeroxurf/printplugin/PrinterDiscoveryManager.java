package com.xeroxurf.printplugin;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CopyOnWriteArrayList;

public class PrinterDiscoveryManager {

    public static class DiscoveredPrinter {
        public final String name;
        public final String host;
        public final int port;
        public final String serviceType;
        public final String attributes;

        public DiscoveredPrinter(String name, String host, int port,
                                 String serviceType, String attributes) {
            this.name = name;
            this.host = host;
            this.port = port;
            this.serviceType = serviceType;
            this.attributes = attributes;
        }

        @Override
        public String toString() {
            return name + " (" + host + ":" + port + ")";
        }
    }

    public interface DiscoveryListener {
        void onPrinterFound(DiscoveredPrinter printer);
        void onDiscoveryComplete(List<DiscoveredPrinter> printers);
        void onDiscoveryFailed(String error);
    }

    private static final int DISCOVERY_TIMEOUT_MS = 8000;
    private static final String SERVICE_TYPE_IPP = "_ipp._tcp.";
    private static final String SERVICE_TYPE_PDL = "_pdl-datastream._tcp.";

    private final Context context;
    private NsdManager nsdManager;
    private NsdManager.DiscoveryListener activeDiscoveryListener;
    private String activeServiceType;
    private DiscoveryListener callback;
    private final CopyOnWriteArrayList<DiscoveredPrinter> foundPrinters = new CopyOnWriteArrayList<>();
    private final Queue<NsdServiceInfo> resolveQueue = new LinkedList<>();
    private boolean isResolving = false;
    private boolean discoveryActive = false;

    public PrinterDiscoveryManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public void startDiscovery(DiscoveryListener listener) {
        if (discoveryActive) {
            stopDiscovery();
        }

        this.callback = listener;
        this.foundPrinters.clear();
        synchronized (resolveQueue) {
            this.resolveQueue.clear();
            this.isResolving = false;
        }
        this.nsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);

        if (nsdManager == null) {
            if (callback != null) callback.onDiscoveryFailed("NSD service not available");
            return;
        }

        discoveryActive = true;
        PrintLog.i("PrinterDiscovery", "Starting mDNS discovery for IPP printers");
        discover(SERVICE_TYPE_IPP);
    }

    private void discover(String serviceType) {
        activeServiceType = serviceType;
        activeDiscoveryListener = createDiscoveryListener(serviceType);
        try {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, activeDiscoveryListener);
        } catch (Exception e) {
            PrintLog.e("PrinterDiscovery", "Failed to start discoverServices: " + e.getMessage());
            discoveryActive = false;
            if (callback != null) callback.onDiscoveryFailed(e.getMessage());
            return;
        }

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (discoveryActive && SERVICE_TYPE_IPP.equals(activeServiceType)) {
                PrintLog.i("PrinterDiscovery", "IPP discovery timeout, trying PDL");
                safelyStopDiscoveryListener();
                discover(SERVICE_TYPE_PDL);
            } else if (discoveryActive) {
                stopDiscovery();
                notifyComplete();
            }
        }, DISCOVERY_TIMEOUT_MS);
    }

    private void safelyStopDiscoveryListener() {
        if (nsdManager != null && activeDiscoveryListener != null) {
            try {
                nsdManager.stopServiceDiscovery(activeDiscoveryListener);
            } catch (Exception e) {
                PrintLog.w("PrinterDiscovery", "Error stopping service discovery: " + e.getMessage());
            }
            activeDiscoveryListener = null;
        }
    }

    private NsdManager.DiscoveryListener createDiscoveryListener(String serviceType) {
        return new NsdManager.DiscoveryListener() {
            @Override
            public void onDiscoveryStarted(String serviceType) {
                PrintLog.i("PrinterDiscovery", "Discovery started: " + serviceType);
            }

            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) {
                PrintLog.i("PrinterDiscovery", "Service found: " + serviceInfo.getServiceName()
                        + " type=" + serviceInfo.getServiceType());
                enqueueResolve(serviceInfo);
            }

            @Override
            public void onServiceLost(NsdServiceInfo serviceInfo) {
                PrintLog.i("PrinterDiscovery", "Service lost: " + serviceInfo.getServiceName());
                DiscoveredPrinter removed = null;
                for (DiscoveredPrinter p : foundPrinters) {
                    if (p.name.equals(serviceInfo.getServiceName())) {
                        removed = p;
                        break;
                    }
                }
                if (removed != null) {
                    foundPrinters.remove(removed);
                }
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                PrintLog.i("PrinterDiscovery", "Discovery stopped: " + serviceType);
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                PrintLog.e("PrinterDiscovery", "Discovery start failed: " + errorCode);
                discoveryActive = false;
                if (callback != null) callback.onDiscoveryFailed(
                        "Discovery failed (error " + errorCode + ")");
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                PrintLog.w("PrinterDiscovery", "Stop discovery failed: " + errorCode);
            }
        };
    }

    private void enqueueResolve(NsdServiceInfo serviceInfo) {
        synchronized (resolveQueue) {
            resolveQueue.add(serviceInfo);
            processResolveQueue();
        }
    }

    private void processResolveQueue() {
        synchronized (resolveQueue) {
            if (isResolving || resolveQueue.isEmpty() || nsdManager == null) {
                return;
            }
            isResolving = true;
            NsdServiceInfo serviceInfo = resolveQueue.poll();
            if (serviceInfo == null) {
                isResolving = false;
                return;
            }

            try {
                nsdManager.resolveService(serviceInfo, new NsdManager.ResolveListener() {
                    @Override
                    public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                        PrintLog.w("PrinterDiscovery", "Resolve failed for "
                                + serviceInfo.getServiceName() + ": " + errorCode);
                        onResolveFinished();
                    }

                    @Override
                    public void onServiceResolved(NsdServiceInfo serviceInfo) {
                        try {
                            handleServiceResolved(serviceInfo);
                        } catch (Exception e) {
                            PrintLog.e("PrinterDiscovery", "Error handling resolved service: " + e.getMessage());
                        } finally {
                            onResolveFinished();
                        }
                    }

                    private void onResolveFinished() {
                        synchronized (resolveQueue) {
                            isResolving = false;
                            processResolveQueue();
                        }
                    }
                });
            } catch (Exception e) {
                PrintLog.e("PrinterDiscovery", "Exception calling resolveService: " + e.getMessage());
                isResolving = false;
                processResolveQueue();
            }
        }
    }

    private void handleServiceResolved(NsdServiceInfo serviceInfo) {
        String name = serviceInfo.getServiceName() != null ? serviceInfo.getServiceName() : "Unknown Printer";
        String host = serviceInfo.getHost() != null
                ? serviceInfo.getHost().getHostAddress() : "unknown";
        int port = serviceInfo.getPort();
        String type = serviceInfo.getServiceType() != null ? serviceInfo.getServiceType() : "";
        String attrs = serviceInfo.getAttributes() != null
                ? parseAttributes(serviceInfo.getAttributes()) : "";

        PrintLog.i("PrinterDiscovery", "Resolved: " + name + " at " + host + ":" + port);

        android.content.SharedPreferences prefs =
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);
        String model = prefs.getString("printer_model", "workcentre_3025");
        boolean isPhaserSelected = "phaser_3020".equals(model);

        String nameLower = name.toLowerCase();
        boolean isWorkCentreMdns = nameLower.contains("workcentre") || nameLower.contains("3025");
        boolean isPhaserMdns = nameLower.contains("phaser") || nameLower.contains("3020");

        if (isPhaserSelected && isWorkCentreMdns) {
            PrintLog.i("PrinterDiscovery", "Skipping WorkCentre " + name + " in Phaser mode");
            return;
        }
        if (!isPhaserSelected && isPhaserMdns) {
            PrintLog.i("PrinterDiscovery", "Skipping Phaser " + name + " in WorkCentre mode");
            return;
        }

        DiscoveredPrinter printer = new DiscoveredPrinter(name, host, port, type, attrs);

        for (DiscoveredPrinter existing : foundPrinters) {
            if (existing.host.equals(host) && existing.port == port) {
                return;
            }
        }
        foundPrinters.add(printer);

        if (callback != null) {
            callback.onPrinterFound(printer);
        }
    }

    private String parseAttributes(Map<String, byte[]> attributes) {
        if (attributes == null) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, byte[]> entry : attributes.entrySet()) {
            if (entry == null || entry.getKey() == null) continue;
            if (sb.length() > 0) sb.append(", ");
            byte[] val = entry.getValue();
            String value = (val != null) ? new String(val, StandardCharsets.UTF_8) : "";
            sb.append(entry.getKey()).append("=").append(value);
        }
        return sb.toString();
    }

    public void stopDiscovery() {
        discoveryActive = false;
        safelyStopDiscoveryListener();
    }

    private void notifyComplete() {
        if (callback != null) {
            List<DiscoveredPrinter> result = new ArrayList<>(foundPrinters);
            callback.onDiscoveryComplete(result);
        }
    }

    public List<DiscoveredPrinter> getFoundPrinters() {
        return Collections.unmodifiableList(new ArrayList<>(foundPrinters));
    }

    public void destroy() {
        stopDiscovery();
        synchronized (resolveQueue) {
            resolveQueue.clear();
            isResolving = false;
        }
        foundPrinters.clear();
    }
}
