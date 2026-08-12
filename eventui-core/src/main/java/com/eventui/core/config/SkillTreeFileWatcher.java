package com.eventui.core.config;

import com.eventui.api.skill.SkillTreeDefinition;
import com.eventui.core.EventUIPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class SkillTreeFileWatcher {

    private static final Logger LOGGER = Logger.getLogger("EventUI");
    private static final long DEBOUNCE_MS = 300;

    private final EventUIPlugin plugin;
    private final File skillsFolder;
    private WatchService watchService;
    private WatchKey watchKey;
    private Thread watchThread;
    private volatile boolean running = false;
    private final ScheduledExecutorService debouncer;
    private final Map<String, ScheduledFuture<?>> pending = new HashMap<>();

    public SkillTreeFileWatcher(EventUIPlugin plugin) {
        this.plugin = plugin;
        this.skillsFolder = new File(plugin.getDataFolder(), "skills");
        this.debouncer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "EventUI-SkillTreeHotReload-Debounce");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        if (!skillsFolder.exists()) {
            skillsFolder.mkdirs();
        }

        try {
            watchService = FileSystems.getDefault().newWatchService();
            Path watchPath = skillsFolder.toPath();
            watchKey = watchPath.register(watchService,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE);

            running = true;
            watchThread = new Thread(this::watchLoop, "EventUI-SkillTreeFileWatcher");
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
        watchThread = new Thread(this::pollingFallbackLoop, "EventUI-SkillTreeFileWatcher-Fallback");
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

                        String path = skillsFolder.getAbsolutePath() + File.separator + fileName;
                        ScheduledFuture<?> prev = pending.get(path);
                        if (prev != null && !prev.isDone()) prev.cancel(false);

                        File file = new File(skillsFolder, fileName);
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
        return skillsFolder.listFiles((dir, name) ->
                name.endsWith(".yml") || name.endsWith(".yaml"));
    }

    private void reloadOnMainThread(File file, String fileName) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                SkillTreeDefinition newTree =
                        plugin.getSkillTreeConfigLoader().loadSkillTreeFromFile(file);
                plugin.getSkillTreeStorage().registerSkillTree(newTree);
                plugin.notifySkillTreeHotReload(newTree.getId());
            } catch (Exception e) {
                LOGGER.severe("[EventUI] Skill Tree Hot Reload: error en "
                        + fileName + " - " + e.getMessage());
                LOGGER.severe("[EventUI] El skill tree anterior sigue activo"
                        + " hasta que el archivo sea válido.");
            }
        });
    }
}
