package com.eventui.core.config;

import com.eventui.api.skill.SkillTreeDefinition;
import com.eventui.core.EventUIPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class SkillTreeFileWatcher {

    private static final Logger LOGGER = Logger.getLogger("EventUI");
    private static final long POLL_INTERVAL_MS = 1000;   // check every second
    private static final long DEBOUNCE_MS = 300;

    private final EventUIPlugin plugin;
    private final File skillsFolder;
    private Thread pollThread;
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

        running = true;
        pollThread = new Thread(this::pollLoop, "EventUI-SkillTreeFileWatcher");
        pollThread.setDaemon(true);
        pollThread.start();
    }

    public void stop() {
        running = false;
        if (pollThread != null) pollThread.interrupt();
        debouncer.shutdownNow();
        LOGGER.info("[EventUI] Skill Tree Hot Reload detenido.");
    }

    private void pollLoop() {
        // Snapshot inicial de lastModified para no recargar todo al arrancar
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
                        LOGGER.info("[SkillTreeWatcher] Change detected: " + fileName);

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
                LOGGER.info("[EventUI] Skill Tree Hot Reload: '"
                        + newTree.getId() + "' recargado desde " + fileName);
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
