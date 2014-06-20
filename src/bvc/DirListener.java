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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author Pelepeichenko A.V.
 */
public class DirListener {
    
    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    
    Map<String, WatchKey> keys = new HashMap();
    Map<WatchKey, Path> patchs = new HashMap();
    
    WatchService watcher;
    
    Path[] backDir = new Path[]{Paths.get("/tmp/2"), Paths.get("/tmp/a")};
    Path[] listDir = new Path[]{Paths.get("/tmp/4"), Paths.get("/tmp/b")};
    
    long[] delay = new long[]{3, 2};
    
    boolean[] copyOnStart = new boolean[]{false, false};
    
    Pattern[] ignores;
    Pattern[] noCopy;
    
    void init() throws IOException{
        watcher = Paths.get("/").getFileSystem().newWatchService();
        
        for(Path p: listDir){
            init(p);
        }
    }
    
    void init(Path from) throws IOException{
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
        
        Files.walkFileTree(from, maker);
    }
    

    void start() throws IOException, InterruptedException {
        
        init();
        
        int i=0;
        for(boolean b:copyOnStart){
            if (b) scheduleSnapshot(listDir[i]);
            i++;
        }
        
        for (;;) {

            WatchKey watchKey = watcher.take();
            List<WatchEvent<?>> events = watchKey.pollEvents();
            
            Path start = patchs.get(watchKey);
            
            if (start==null){
                //System.err.println("ERROR! try get Path by object "+watchKey+" Sequence of filesystem event is reordered!");
                watchKey.reset();
                continue;
            }
            
            newEvent:
            for (WatchEvent event : events) {
                Path full = start.resolve( (Path) event.context() );
                String fullPath = full.toString();
                for(Pattern ignorPattern: ignores){
                    //System.out.println("try pattern "+ignorPattern.toString());
                    Matcher m = ignorPattern.matcher(fullPath);
                    if (m.matches()){
                      //  System.out.println("skip "+fullPath+" by "+ignorPattern.toString());
                        continue newEvent;
                    }
                }
                
                if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                    boolean dir = full.toFile().isDirectory();
                    if (dir){
                        onCreateDir(full, watcher);                        
                    }else{
                        onCreateFile(full);
                    }
                } else
                
                if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                    WatchKey testKey = keys.get(full.toString());
                    boolean dir = testKey!=null;
                    if (dir) {
                        onDelDir(testKey);
                    }else{
                        onDelFile(full);
                    }
                } else
                
                if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                    WatchKey testKey = keys.get(full.toString());
                    boolean dir = testKey!=null;
                    if (dir){
                        onModifyDir(full);
                    }else{
                        onModifyFile(full);
                    }
                }
                
                if (event.kind() == StandardWatchEventKinds.OVERFLOW){
                    System.out.println("OVERFLOW!!!");
                }                
            }
            
            if (!watchKey.reset()) {
                watchKey.cancel();
            }

        }


    }

    
    private void onCreateDir(Path full, WatchService watcher) throws IOException {
        WatchKey dirKey = full.register(watcher,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY);
        
        keys.put(full.toString(), dirKey);
        patchs.put(dirKey, full);
        //System.out.println("create dir "+full.toString()+" "+dirKey);
        scheduleSnapshot(full);
    }

    
    private void onCreateFile(Path full) throws IOException {
        scheduleSnapshot(full);
    }

    private void onDelDir(WatchKey key) throws IOException {
        Path p = patchs.get(key);
        patchs.remove(key);
        keys.remove(key.toString());
        key.cancel();
       // System.out.println("del dir "+p.toString()+"  key "+key);
        
        scheduleSnapshot(p);
    }

    private void onDelFile(Path full) throws IOException {
        scheduleSnapshot(full);
    }

    private void onModifyDir(Path full) throws IOException {
        scheduleSnapshot(full);
    }

    private void onModifyFile(Path full) throws IOException {
        scheduleSnapshot(full);
    }
    
    
    
    private ScheduledFuture sf;
    
    private void scheduleSnapshot(Path p) throws IOException{
        String insidePath = p.toString();
        Path finded=null;
        int i=0;
        for(Path v: listDir){
            String str = v.toString();
            if (insidePath.startsWith(str)){
                finded=v;
                break;
            }
            i++;
        }
        
        final Path pathFrom = finded;
        final Path pathTo = backDir[i];
        final Date date = new Date();
        final Runnable task =new Runnable(){
            @Override
            public void run() {
                synchronized(DirListener.this){sf = null;}
                try {
                    makeSnapshot(pathFrom, pathTo, date);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        };
        
        synchronized(this){
            if (sf!=null) sf.cancel(false);
            sf = executor.schedule(task, delay[i], TimeUnit.SECONDS);
        }

    }
    
    
    DateFormat df = new SimpleDateFormat("yyyy.MM.dd_HH:mm:ss");
    
    private void makeSnapshot(final Path from, final Path to, Date date) throws IOException{
        final Path dir = to.resolve(df.format(date));
        
        FileVisitor<Path> maker = new FileVisitor<Path>() {

            @Override
            public FileVisitResult preVisitDirectory(Path fullSrc, BasicFileAttributes attrs) throws IOException {
                //System.out.println("visit dir "+dir.toString());
                Path relSrc = from.relativize(fullSrc);
                Path fullDest = dir.resolve(relSrc);
                fullDest.toFile().mkdirs();        
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path fileSrc, BasicFileAttributes attrs) throws IOException {
                //System.out.println("visit file "+fileSrc.toString());
                String src = fileSrc.toString();
                Path relSrc = from.relativize(fileSrc);
                Path fullDest = dir.resolve(relSrc);
                
                for(Pattern ignorPattern: noCopy){
                    //System.out.println("try pattern "+ignorPattern.toString());
                    Matcher m = ignorPattern.matcher(src);
                    if (m.matches()){
                        //System.out.println("Skip copy "+src+" -> "+fullDest.toString() );
                        return FileVisitResult.CONTINUE;
                    }
                }

                
                Files.copy(fileSrc, fullDest, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException { return FileVisitResult.CONTINUE; }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException { return FileVisitResult.CONTINUE; }
        };
        
        Files.walkFileTree(from, maker);
    }
    
    

    
    
}


