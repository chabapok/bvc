/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package bvc;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.regex.Pattern;


/**
 *
 * @author Pelepeichenko A.V.
 */
public class Bvc {

    
    public static void main(String[] args) throws IOException, InterruptedException {
        DirListener dirListener = new DirListener();
        
        ArrayList<Path> from = new ArrayList();
        ArrayList<Path> to = new ArrayList();
        ArrayList<Long> pauses = new ArrayList();
        ArrayList<Boolean> copyOnStart = new ArrayList();
        
        ArrayList<Pattern> ignores = new ArrayList();
        try( BufferedReader br = new BufferedReader(new FileReader(args[0]))){
            String line;
            while ((line = br.readLine()) != null) {
               if ( line.startsWith("#") ) continue;
               
               
               if (line.toLowerCase().startsWith("ignore ")){
                   line = line.substring(7).trim();
                   ignores.add( Pattern.compile(line) );
                   continue;
               }
               
               if ( line.indexOf("->")!=-1 ){
                    String toks[] = line.split("->");
                    String toks1[] = toks[1].split("\\|");

                    String fromDir = toks[0].trim();
                    String toDir = toks1[0].trim();

                    long pause = 3;
                    if (toks1.length>=2){
                        pause = Long.parseLong( toks1[1].trim() );
                    }

                    boolean cOS = false;
                    if (toks1.length==3){
                        cOS = toks1[2].trim().equals("1");
                    }

                    from.add( Paths.get(fromDir) );
                    to.add( Paths.get(toDir) );
                    pauses.add(pause);
                    copyOnStart.add(cOS);
               }
            }
        }
        
        int sz= from.size();
        
        dirListener.listDir = from.toArray(new Path[sz] );
        
        dirListener.backDir = to.toArray( new Path[sz]);
        
        dirListener.delay = new long[sz];
        for(int i=0; i<sz; i++){dirListener.delay[i] = pauses.get(i);}
        
        dirListener.copyOnStart = new boolean[sz];
        for(int i=0; i<sz; i++){dirListener.copyOnStart[i] = copyOnStart.get(i);}
        
        dirListener.ignores = ignores.toArray(new Pattern[0]);
        
        dirListener.start();
    }

}
