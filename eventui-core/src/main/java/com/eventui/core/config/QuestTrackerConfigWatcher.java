package com.eventui.core.config;

import com.eventui.core.EventUIPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class QuestTrackerConfigWatcher {

    private static final Logger LOGGER = Logger.getLogger("EventUI");
    private static final long DEBOUNCE_MS = 300;

    private final EventUIPlugin plugin;
    private final File configFile;
    private WatchService watchService;
    private WatchKey watchKey;
    private Thread watchThread;
    private volatile boolean running = false;
    private final ScheduledExecutorService debouncer;
    private final Map<String, ScheduledFuture<?>> pending = new HashMap<>();

    public QuestTrackerConfigWatcher(EventUIPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "quest_tracker_config.yml");
        this.debouncer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "EventUI-QuestTrackerConfig-Debounce");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        File configDir = configFile.getParentFile();
        if (!configDir.exists()) {
            configDir.mkdirs();
        }

        LOGGER.info("[QuestTrackerConfig] Iniciando watcher para: " + configFile.getAbsolutePath());

        try {
            watchService = FileSystems.getDefault().newWatchService();
            Path watchPath = configDir.toPath();
            watchKey = watchPath.register(watchService,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE);

            running = true;
            watchThread = new Thread(this::watchLoop, "EventUI-QuestTrackerConfigWatcher");
            watchThread.setDaemon(true);
            watchThread.start();
            LOGGER.info("[QuestTrackerConfig] WatchService iniciado correctamente");
        } catch (IOException e) {
            LOGGER.severe("[QuestTrackerConfig] Error iniciando WatchService: " + e.getMessage());
            LOGGER.warning("[QuestTrackerConfig] Usando polling como fallback");
            startPollingFallback();
        }
    }

    private void startPollingFallback() {
        running = true;
        watchThread = new Thread(this::pollingFallbackLoop, "EventUI-QuestTrackerConfigWatcher-Fallback");
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
                LOGGER.warning("[QuestTrackerConfig] Error cerrando WatchService: " + e.getMessage());
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

                    if (filename.toString().equals(configFile.getName())) {
                        String path = configFile.getAbsolutePath();
                        ScheduledFuture<?> prev = pending.get(path);
                        if (prev != null && !prev.isDone()) prev.cancel(false);

                        pending.put(path, debouncer.schedule(
                                this::notifyClients,
                                DEBOUNCE_MS, TimeUnit.MILLISECONDS
                        ));
                    }
                }

                boolean valid = key.reset();
                if (!valid) {
                    LOGGER.warning("[QuestTrackerConfig] WatchKey invalidado, reiniciando...");
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
        long lastModified = 0;

        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                break;
            }

            if (configFile.exists()) {
                long currentTs = configFile.lastModified();
                String path = configFile.getAbsolutePath();

                if (lastModified != 0 && currentTs > lastModified) {
                    lastModified = currentTs;

                    ScheduledFuture<?> prev = pending.get(path);
                    if (prev != null && !prev.isDone()) prev.cancel(false);

                    pending.put(path, debouncer.schedule(
                            this::notifyClients,
                            DEBOUNCE_MS, TimeUnit.MILLISECONDS
                    ));
                } else if (lastModified == 0) {
                    lastModified = currentTs;
                }
            }
        }
    }

    private void notifyClients() {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                LOGGER.info("[QuestTrackerConfig] Detectado cambio en archivo, notificando clientes...");

                
                if (configFile.exists()) {
                    String configContent = java.nio.file.Files.readString(configFile.toPath());
                    plugin.notifyQuestTrackerConfigReload(configContent);
                } else {
                    LOGGER.warning("[QuestTrackerConfig] Archivo de configuración no existe");
                }
            } catch (Exception e) {
                LOGGER.severe("[QuestTrackerConfig] Error al notificar clientes: " + e.getMessage());
            }
        });
    }
}
