import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MemoryMonitor {
    
    // Anlık belleği tutan değişken (Thread-safe olması için volatile)
    private volatile long currentMemoryMB = 0; 
    private final long pid;
    private final ScheduledExecutorService scheduler;

    public MemoryMonitor() {
        this.pid = ProcessHandle.current().pid();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        startMonitoring();
    }

    private void startMonitoring() {
        // Her 2 saniyede bir Windows'a sor ve değeri güncelle
        scheduler.scheduleAtFixedRate(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder("tasklist", "/FI", "PID eq " + pid, "/FO", "CSV", "/NH");
                Process p = pb.start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line = reader.readLine();
                    if (line != null) {
                        String[] parts = line.split("\",\"");
                        if (parts.length >= 5) {
                            String memStr = parts[4].replaceAll("[^0-9]", "");
                            currentMemoryMB = Long.parseLong(memStr) / 1024;
                        }
                    }
                }
            } catch (Exception e) {
                // Hataları yut, ana programı kitleme
            }
        }, 0, 2, TimeUnit.SECONDS);
    }

    // Ana programın sadece değeri okumak için çağıracağı hızlı metod
    public long getMemoryMB() {
        return currentMemoryMB;
    }

    // Program kapanırken arka plan işçisini temizlemek için
    public void stop() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
    }
}