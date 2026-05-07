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
    private final Path watchPath;
    private WatchService watchService;
    private Thread watchThread;
    private final ScheduledExecutorService debouncer;
    private final Map<String, ScheduledFuture<?>> pending = new HashMap<>();

    public UIFileWatcher(EventUIPlugin plugin) {
        this.plugin = plugin;
        this.watchPath = plugin.getUIConfigLoader().getUisDirectory().toPath();
        this.debouncer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "EventUI-HotReload-Debounce");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            watchPath.register(watchService,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE);

            watchThread = new Thread(this::watchLoop, "EventUI-FileWatcher");
            watchThread.setDaemon(true);
            watchThread.start();

            LOGGER.info("[EventUI] Hot Reload activo, vigilando: " + watchPath);
        } catch (IOException e) {
            LOGGER.severe("[EventUI] No se pudo iniciar el file watcher: " + e.getMessage());
        }
    }

    public void stop() {
        if (watchThread != null) watchThread.interrupt();
        try {
            if (watchService != null) watchService.close();
        } catch (IOException ignored) {}
        debouncer.shutdownNow();
        LOGGER.info("[EventUI] Hot Reload detenido.");
    }

    private void watchLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException | ClosedWatchServiceException e) {
                break;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue;

                String fileName = ((Path) event.context()).getFileName().toString();
                if (!fileName.endsWith(".yml") && !fileName.endsWith(".yaml")) continue;


                ScheduledFuture<?> prev = pending.get(fileName);
                if (prev != null && !prev.isDone()) prev.cancel(false);

                File changedFile = watchPath.resolve((Path) event.context()).toFile();
                pending.put(fileName, debouncer.schedule(
                        () -> reloadOnMainThread(changedFile, fileName),
                        DEBOUNCE_MS, TimeUnit.MILLISECONDS
                ));
            }
            key.reset();
        }
    }

    private void reloadOnMainThread(File file, String fileName) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                UIConfig newConfig = plugin.getUIConfigLoader().loadUIConfigFromFile(file);
                plugin.getUIConfigs().put(newConfig.getId(), newConfig);
                LOGGER.info("[EventUI] Hot Reload: '" + newConfig.getId() + "' recargado desde " + fileName);
                plugin.notifyHotReload(newConfig.getId());
            } catch (Exception e) {
                LOGGER.severe("[EventUI] Hot Reload: error en " + fileName + " - " + e.getMessage());
                LOGGER.severe("[EventUI] La UI anterior sigue activa hasta que el archivo sea válido.");
            }
        });
    }
}
