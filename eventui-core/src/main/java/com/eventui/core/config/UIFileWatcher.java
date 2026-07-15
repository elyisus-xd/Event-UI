package com.eventui.core.config;

import com.eventui.api.ui.UIConfig;
import com.eventui.core.EventUIPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class UIFileWatcher {

    private static final Logger LOGGER = Logger.getLogger("EventUI");
    private static final long POLL_INTERVAL_MS = 1000;
    private static final long DEBOUNCE_MS = 300;

    private final EventUIPlugin plugin;
    private final File uisFolder;
    private Thread pollThread;
    private volatile boolean running = false;
    private final ScheduledExecutorService debouncer;
    private final Map<String, ScheduledFuture<?>> pending = new HashMap<>();

    public UIFileWatcher(EventUIPlugin plugin) {
        this.plugin = plugin;
        this.uisFolder = plugin.getUIConfigLoader().getUisDirectory();
        this.debouncer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "EventUI-HotReload-Debounce");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        if (!uisFolder.exists()) {
            uisFolder.mkdirs();
        }

        running = true;
        pollThread = new Thread(this::pollLoop, "EventUI-FileWatcher");
        pollThread.setDaemon(true);
        pollThread.start();
    }

    public void stop() {
        running = false;
        if (pollThread != null) pollThread.interrupt();
        debouncer.shutdownNow();
        LOGGER.info("[EventUI] Hot Reload detenido.");
    }

    private void pollLoop() {
        // Snapshot inicial para no recargar todo al arrancar
        Map<String, Long> lastModified = new HashMap<>();
        File[] initialFiles = listYamlFiles();
        if (initialFiles != null) {
            for (File f : initialFiles) {
                lastModified.put(f.getAbsolutePath(), f.lastModified());
            }
        }

        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                break;
            }

            File[] currentFiles = listYamlFiles();
            if (currentFiles == null) continue;

            for (File file : currentFiles) {
                String path = file.getAbsolutePath();
                long currentTs = file.lastModified();
                Long previousTs = lastModified.get(path);

                if (previousTs == null || currentTs > previousTs) {
                    lastModified.put(path, currentTs);

                    if (previousTs != null) { // skip on first detection (startup)
                        String fileName = file.getName();
                        LOGGER.info("[UIWatcher] Change detected: " + fileName);

                        ScheduledFuture<?> prev = pending.get(path);
                        if (prev != null && !prev.isDone()) prev.cancel(false);

                        pending.put(path, debouncer.schedule(
                                () -> reloadOnMainThread(file, fileName),
                                DEBOUNCE_MS, TimeUnit.MILLISECONDS
                        ));
                    }
                }
            }
        }
    }

    private File[] listYamlFiles() {
        return uisFolder.listFiles((dir, name) ->
                name.endsWith(".yml") || name.endsWith(".yaml"));
    }

    private void reloadOnMainThread(File file, String fileName) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                UIConfig newConfig = plugin.getUIConfigLoader().loadUIConfigFromFile(file);
                plugin.getUIConfigs().put(newConfig.getId(), newConfig);
                LOGGER.info("[EventUI] Hot Reload: '"
                        + newConfig.getId() + "' recargado desde " + fileName);
                plugin.notifyHotReload(newConfig.getId());
            } catch (Exception e) {
                LOGGER.severe("[EventUI] Hot Reload: error en "
                        + fileName + " - " + e.getMessage());
                LOGGER.severe("[EventUI] La UI anterior sigue activa"
                        + " hasta que el archivo sea válido.");
            }
        });
    }
}
