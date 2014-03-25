package bvc;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Pelepeichenko A.V.
 */
public class DirListener {
    
    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    
    Map<String, WatchKey> keys = new HashMap();
    Map<WatchKey, Path> patchs = new HashMap();
    
    WatchService watcher;
    
    Path backDir = Paths.get("/tmp/2");
    Path listDir = Paths.get("/tmp/4");
    long delay = 3;
    
    DirListener(){
         
    }
    
    
    void init() throws IOException{
        watcher = listDir.getFileSystem().newWatchService();
        
        FileVisitor<Path> maker = new FileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                WatchKey key = dir.register(watcher, 
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE, 
                    StandardWatchEventKinds.ENTRY_MODIFY);
                
                keys.put(dir.toString(), key);
                patchs.put(key, dir);
                
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path fileSrc, BasicFileAttributes attrs) throws IOException { return FileVisitResult.CONTINUE; }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException { return FileVisitResult.CONTINUE; }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException { return FileVisitResult.CONTINUE; }
        };
        
        Files.walkFileTree(listDir, maker);
    }
    

    void start() throws IOException, InterruptedException {
        /*
        WatchKey key = listDir.register(watcher, 
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE, 
                StandardWatchEventKinds.ENTRY_MODIFY);

        patchs.put(key, listDir);
        */
        
        for (;;) {

            WatchKey watchKey = watcher.take();
            Path start = patchs.get(watchKey);
            
            List<WatchEvent<?>> events = watchKey.pollEvents();
            for (WatchEvent event : events) {
                
                Path full = start.resolve( (Path) event.context() );
                                
                if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                    boolean dir = full.toFile().isDirectory();
                    if (dir){
                        onCreateDir(full, watcher);                        
                    }else{
                        onCreateFile(full);
                    }
                }
                
                if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                    WatchKey testKey = keys.get(full.toString());
                    boolean dir = testKey!=null;
                    if (dir) {
                        onDelDir(testKey);
                    }else{
                        onDelFile(full);
                    }
                }
                
                if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                    if (true){
                        onModifyDir(full);
                    }else{
                        onModifyFile(full);
                    }
                }
                
                if (!watchKey.reset()) break;
            }
        }


    }

    
    private void onCreateDir(Path full, WatchService watcher) throws IOException {
        System.out.println("create dir "+full.toString());
        WatchKey dirKey = full.register(watcher,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY);
        
        keys.put(full.toString(), dirKey);
        patchs.put(dirKey, full);
        
        scheduleSnapshot();
    }

    
    private void onCreateFile(Path full) throws IOException {
        scheduleSnapshot();
    }

    private void onDelDir(WatchKey key) throws IOException {
        Path p = patchs.get(key);
        patchs.remove(key);
        keys.remove(key.toString());
        key.cancel();
        System.out.println("del dir "+p.toString());
        
        scheduleSnapshot();
    }

    private void onDelFile(Path full) throws IOException {
        scheduleSnapshot();
    }

    private void onModifyDir(Path full) throws IOException {
        scheduleSnapshot();
    }

    private void onModifyFile(Path full) throws IOException {
        scheduleSnapshot();
    }
    
    
    
    private ScheduledFuture sf;
    
    private void scheduleSnapshot() throws IOException{
        
        final Runnable task =new Runnable(){
            @Override
            public void run() {
                synchronized(DirListener.this){sf = null;}
                try {
                    makeSnapshot();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        };
        
        synchronized(this){
            if (sf!=null) sf.cancel(false);
            sf = executor.schedule(task, delay, TimeUnit.SECONDS);
        }

    }
    
    
    DateFormat df = new SimpleDateFormat("yyyy.MM.dd_HH:mm:ss");
    private void makeSnapshot() throws IOException{
        Path dir = backDir.resolve(df.format(new Date()));
        
        FileVisitor<Path> maker = new FileVisitor<Path>() {

            @Override
            public FileVisitResult preVisitDirectory(Path fullSrc, BasicFileAttributes attrs) throws IOException {
                System.out.println("visit dir "+dir.toString());
                Path relSrc = listDir.relativize(fullSrc);
                Path fullDest = dir.resolve(relSrc);
                fullDest.toFile().mkdirs();        
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path fileSrc, BasicFileAttributes attrs) throws IOException {
                System.out.println("visit file "+fileSrc.toString());
                Path relSrc = listDir.relativize(fileSrc);
                Path fullDest = dir.resolve(relSrc);
                Files.copy(fileSrc, fullDest, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException { return FileVisitResult.CONTINUE; }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException { return FileVisitResult.CONTINUE; }
        };
        
        Files.walkFileTree(listDir, maker);
    }
    
    

    
    
}


