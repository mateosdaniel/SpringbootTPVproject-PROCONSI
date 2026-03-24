package com.proconsi.electrobazar.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

/**
 * Service to manage automatic and manual database backups.
 * Executes mysqldump and compresses results with Gzip.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupService {

    private final ActivityLogService activityLogService;

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Value("${spring.datasource.username}")
    private String dbUsername;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    @Value("${backup.local.path:C:/TPV/backups/}")
    private String backupPath;

    @Value("${mysqldump.path:mysqldump}")
    private String mysqldumpPath;

    private static final DateTimeFormatter FILE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /**
     * Daily backup at 23:00h
     */
    @Scheduled(cron = "0 0 23 * * ?")
    public void scheduledDailyBackup() {
        log.info("Iniciando backup diario automático...");
        performBackup("DAILY", null);
        cleanupOldBackups();
    }

    /**
     * Weekly full backup on Sundays at 23:30h
     */
    @Scheduled(cron = "0 30 23 * * SUN")
    public void scheduledWeeklyBackup() {
        log.info("Iniciando backup semanal automático...");
        performBackup("WEEKLY", null);
        cleanupOldBackups();
    }

    /**
     * Performs a database backup and compression.
     * @param type "DAILY", "WEEKLY" or "MANUAL"
     * @param requestor Username of the person requesting manual backup, or null for system
     * @return Result object with status and metadata
     */
    public BackupResult performBackup(String type, String requestor) {
        String timestamp = LocalDateTime.now().format(FILE_FORMATTER);
        // Naming according to request
        String isWeeklySuffix = "WEEKLY".equals(type) ? "_weekly" : "";
        
        String backupFileName = "backup_tpv_" + timestamp + isWeeklySuffix + ".sql";
        String logsFileName = "logs_tpv_" + timestamp + isWeeklySuffix + ".sql";

        try {
            Path targetDir = Paths.get(backupPath);
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }

            if (!isMysqldumpAvailable()) {
                String errorMsg = "mysqldump no está disponible en el PATH. Instale MariaDB/MySQL Client y añádalo al PATH o configure 'mysqldump.path'.";
                activityLogService.logError("BACKUP", "[BACKUP] Fallo en backup automático: " + errorMsg, "Sistema", "BACKUP", null);
                return new BackupResult(false, null, 0, LocalDateTime.now(), errorMsg);
            }

            File backupFile = executeDump(null, backupFileName);
            File logsFile = executeDump("activity_logs", logsFileName);

            String finalBackupName = compressFile(backupFile);
            String finalLogsName = compressFile(logsFile);

            long size = Files.size(targetDir.resolve(finalBackupName));
            
            String logMsg = (requestor != null) 
                ? "[BACKUP] Backup manual ejecutado por " + requestor 
                : "[BACKUP] Backup automático (" + type + ") completado: " + finalBackupName + " y " + finalLogsName;
            
            activityLogService.logActivity("BACKUP", logMsg, requestor != null ? requestor : "Sistema", "BACKUP", null);
            
            return new BackupResult(true, finalBackupName, size, LocalDateTime.now(), null);

        } catch (Exception e) {
            String errorMsg = "[BACKUP] Fallo en backup " + (requestor == null ? "automático" : "manual") + ": " + e.getMessage();
            log.error(errorMsg, e);
            activityLogService.logError("BACKUP", errorMsg, requestor != null ? requestor : "Sistema", "BACKUP", null);
            return new BackupResult(false, null, 0, LocalDateTime.now(), e.getMessage());
        }
    }

    private boolean isMysqldumpAvailable() {
        try {
            Process p = new ProcessBuilder(mysqldumpPath, "--version").start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private File executeDump(String table, String fileName) throws IOException, InterruptedException {
        DbConfig config = parseUrl(dbUrl);
        
        // Commands list
        ProcessBuilder pb = new ProcessBuilder(
            mysqldumpPath,
            "-h", config.host,
            "-P", config.port,
            "-u", dbUsername,
            "--routines",
            "--events",
            "--single-transaction",
            config.database
        );

        if (table != null) {
            pb.command().add(table);
        }

        // Set password via environment variable (safer than CLI argument)
        pb.environment().put("MYSQL_PWD", dbPassword);

        File outputFile = new File(backupPath, fileName);
        pb.redirectOutput(outputFile);
        
        Process process = pb.start();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            StringBuilder errors = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                errors.append(line).append("\n");
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("mysqldump falló (code " + exitCode + "): " + errors.toString());
            }
        }
        
        return outputFile;
    }

    private String compressFile(File file) throws IOException {
        String gzName = file.getName() + ".gz";
        File gzFile = new File(file.getParent(), gzName);
        
        try (FileInputStream fis = new FileInputStream(file);
             FileOutputStream fos = new FileOutputStream(gzFile);
             GZIPOutputStream gzos = new GZIPOutputStream(fos)) {
            
            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) > 0) {
                gzos.write(buffer, 0, len);
            }
        }
        file.delete();
        return gzName;
    }

    /**
     * Retention policy: 
     * - Delete backups older than 6 years.
     * - Never delete weekly backups of the last 3 months.
     */
    private void cleanupOldBackups() {
        try {
            Path dir = Paths.get(backupPath);
            if (!Files.exists(dir)) return;

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime sixYearsAgo = now.minusYears(6);
            LocalDateTime threeMonthsAgo = now.minusMonths(3);

            Files.list(dir)
                .filter(p -> p.toString().endsWith(".gz"))
                .forEach(path -> {
                    try {
                        String name = path.getFileName().toString();
                        FileTime time = Files.getLastModifiedTime(path);
                        LocalDateTime fileDate = LocalDateTime.ofInstant(time.toInstant(), ZoneId.systemDefault());

                        boolean isWeekly = name.contains("weekly");

                        if (fileDate.isBefore(sixYearsAgo)) {
                            Files.delete(path);
                            activityLogService.logActivity("BACKUP_CLEANUP", "Eliminado backup antiguo por política fiscal (>6 años): " + name, "Sistema", "BACKUP", null);
                        } else if (isWeekly && fileDate.isAfter(threeMonthsAgo)) {
                            // Protected by policy
                        }
                        // Note: the prompt doesn't specify to delete daily backups older than 3 months, 
                        // only to PROTECT weekly ones. So we keep them for 6 years as well.
                    } catch (IOException e) {
                        log.error("Error limpiando backup " + path, e);
                    }
                });
        } catch (IOException e) {
            log.error("Error accediendo a carpeta de backups", e);
        }
    }

    private DbConfig parseUrl(String url) {
        // Expected format: jdbc:mysql://host:port/database?optional_params
        Pattern pattern = Pattern.compile("jdbc:(mysql|mariadb)://([^:/]+)(?::(\\d+))?/([^?]+)");
        Matcher m = pattern.matcher(url);
        if (m.find()) {
            return new DbConfig(
                m.group(2), 
                m.group(3) != null ? m.group(3) : "3306", 
                m.group(4)
            );
        }
        // Fallback or specific Railway format parsing could be added here if needed
        return new DbConfig("localhost", "3306", "electrobazar");
    }

    private static class DbConfig {
        String host, port, database;
        DbConfig(String h, String p, String d) { this.host = h; this.port = p; this.database = d; }
    }

    public record BackupResult(boolean status, String filename, long size, LocalDateTime timestamp, String error) {}
}
