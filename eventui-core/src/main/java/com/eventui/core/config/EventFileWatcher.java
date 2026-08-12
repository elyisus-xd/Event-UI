package com.eventui.core.config;

import com.eventui.core.EventUIPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class EventFileWatcher {

    private static final Logger LOGGER = Logger.getLogger("EventUI");
    private static final long DEBOUNCE_MS = 300;

    private final EventUIPlugin plugin;
    private final File eventsFolder;
    private WatchService watchService;
    private WatchKey watchKey;
    private Thread watchThread;
    private volatile boolean running = false;
    private final ScheduledExecutorService debouncer;
    private final Map<String, ScheduledFuture<?>> pending = new HashMap<>();

    public EventFileWatcher(EventUIPlugin plugin) {
        this.plugin = plugin;
        this.eventsFolder = new File(plugin.getDataFolder(), "events");
        this.debouncer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "EventUI-EventReload-Debounce");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        if (!eventsFolder.exists()) {
            eventsFolder.mkdirs();
        }

        try {
            watchService = FileSystems.getDefault().newWatchService();
            Path watchPath = eventsFolder.toPath();
            watchKey = watchPath.register(watchService,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE);

            running = true;
            watchThread = new Thread(this::watchLoop, "EventUI-EventFileWatcher");
            watchThread.setDaemon(true);
            watchThread.start();
        } catch (IOException e) {
            LOGGER.severe("[EventUI] Error iniciando WatchService: " + e.getMessage());
            LOGGER.warning("[EventUI] Usando polling como fallback");
            startPollingFallback();
        }
    }

    private void startPollingFallback() {
        running = true;
        watchThread = new Thread(this::pollingFallbackLoop, "EventUI-EventFileWatcher-Fallback");
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

                        String path = eventsFolder.getAbsolutePath() + File.separator + fileName;
                        ScheduledFuture<?> prev = pending.get(path);
                        if (prev != null && !prev.isDone()) prev.cancel(false);

                        pending.put(path, debouncer.schedule(
                                () -> reloadOnMainThread(fileName),
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

                        ScheduledFuture<?> prev = pending.get(path);
                        if (prev != null && !prev.isDone()) prev.cancel(false);

                        pending.put(path, debouncer.schedule(
                                () -> reloadOnMainThread(fileName),
                                DEBOUNCE_MS, TimeUnit.MILLISECONDS
                        ));
                    }
                }
            }
        }
    }

    private File[] listYamlFiles() {
        return eventsFolder.listFiles((dir, name) ->
                name.endsWith(".yml") || name.endsWith(".yaml"));
    }

    private void reloadOnMainThread(String fileName) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                File eventFile = new File(eventsFolder, fileName);
                if (!eventFile.exists()) {
                    LOGGER.warning("[EventUI] Hot Reload: Archivo no existe " + fileName);
                    return;
                }

                var eventDef = plugin.getConfigLoader().loadEventFromFile(eventFile);
                if (eventDef != null) {
                    plugin.getStorage().registerEvent(eventDef);
                    plugin.getObjectiveTracker().buildObjectiveTypeIndex();
                    plugin.getObjectiveTracker().initializeActiveEventsIndex();
                } else {
                    LOGGER.warning("[EventUI] Hot Reload: No se pudo cargar el evento desde " + fileName);
                }
            } catch (Exception e) {
                LOGGER.severe("[EventUI] Hot Reload: error al recargar evento - " + e.getMessage());
            }
        });
    }
}
