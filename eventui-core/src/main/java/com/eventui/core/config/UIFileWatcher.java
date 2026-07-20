package com.eventui.core.config;

import com.eventui.api.ui.UIConfig;
import com.eventui.core.EventUIPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class UIFileWatcher {

    private static final Logger LOGGER = Logger.getLogger("EventUI");
    private static final long DEBOUNCE_MS = 300;

    private final EventUIPlugin plugin;
    private final File uisFolder;
    private WatchService watchService;
    private WatchKey watchKey;
    private Thread watchThread;
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

        try {
            watchService = FileSystems.getDefault().newWatchService();
            Path watchPath = uisFolder.toPath();
            watchKey = watchPath.register(watchService,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE);

            running = true;
            watchThread = new Thread(this::watchLoop, "EventUI-FileWatcher");
            watchThread.setDaemon(true);
            watchThread.start();

            LOGGER.info("[EventUI] UI WatchService iniciado en " + uisFolder.getAbsolutePath());
        } catch (IOException e) {
            LOGGER.severe("[EventUI] Error iniciando WatchService: " + e.getMessage());
            LOGGER.warning("[EventUI] Usando polling como fallback");
            startPollingFallback();
        }
    }

    private void startPollingFallback() {
        running = true;
        watchThread = new Thread(this::pollingFallbackLoop, "EventUI-FileWatcher-Fallback");
        watchThread.setDaemon(true);
        watchThread.start();
    }

    public void stop() {
        running = false;
        if (watchThread != null) watchThread.interrupt();
        debouncer.shutdownNow();

        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                LOGGER.warning("[EventUI] Error cerrando WatchService: " + e.getMessage());
            }
        }
        LOGGER.info("[EventUI] Hot Reload detenido.");
    }

    private void watchLoop() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                WatchKey key = watchService.poll(1, TimeUnit.SECONDS);
                if (key == null) continue;

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path filename = ev.context();

                    if (filename.toString().endsWith(".yml") || filename.toString().endsWith(".yaml")) {
                        String fileName = filename.toString();
                        LOGGER.info("[UIWatcher] Change detected: " + fileName);

                        String path = uisFolder.getAbsolutePath() + File.separator + fileName;
                        ScheduledFuture<?> prev = pending.get(path);
                        if (prev != null && !prev.isDone()) prev.cancel(false);

                        File file = new File(uisFolder, fileName);
                        pending.put(path, debouncer.schedule(
                                () -> reloadOnMainThread(file, fileName),
                                DEBOUNCE_MS, TimeUnit.MILLISECONDS
                        ));
                    }
                }

                boolean valid = key.reset();
                if (!valid) {
                    LOGGER.warning("[EventUI] WatchKey invalidado, reiniciando...");
                    break;
                }
            } catch (InterruptedException e) {
                break;
            } catch (ClosedWatchServiceException e) {
                break;
            }
        }
    }

    private void pollingFallbackLoop() {
        Map<String, Long> lastModified = new HashMap<>();
        File[] initialFiles = listYamlFiles();
        if (initialFiles != null) {
            for (File f : initialFiles) {
                lastModified.put(f.getAbsolutePath(), f.lastModified());
            }
        }

        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(1000);
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

                    if (previousTs != null) {
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
