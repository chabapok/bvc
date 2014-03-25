/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package bvc;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.StandardWatchEventKinds;
import java.util.List;

/**
 *
 * @author Pelepeichenko A.V.
 */
public class Bvc {

    
    public static void main(String[] args) throws IOException, InterruptedException {
        DirListener dirListener = new DirListener();
        dirListener.start();
    }

}
