/*
 * Copyright 2015 Pawan Dubey pawandubey@outlook.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.pawandubey;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class to track some meta information about the parsing process, including,
 * but not limited to the last parse time and date, and the latest posts etc.
 *
 * @author Pawan Dubey pawandubey@outlook.com
 */
public class InfoHandler {

    public static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy MM dd HH:mm:ss");
    static String LAST_PARSE_DATE;
    final List<Path> latestPosts = new ArrayList<>();

    public InfoHandler() {
        try (BufferedReader br = Files.newBufferedReader(Paths.get(DirectoryCrawler.INFO_FILE),
                                                         StandardCharsets.UTF_8)) {
            LAST_PARSE_DATE = br.readLine();
  
//            String line;
//            while ((line = br.readLine()) != null) {
//                latestPosts.add(Paths.get(line));
//            }
        }
        catch (IOException ex) {
            Logger.getLogger(InfoHandler.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public void writeInfoFile() {
        Path infoFilePath = Paths.get(DirectoryCrawler.INFO_FILE);
        try (BufferedWriter bw
                            = Files.newBufferedWriter(infoFilePath,
                                                      StandardCharsets.UTF_8,
                                                      StandardOpenOption.WRITE,
                                                      StandardOpenOption.TRUNCATE_EXISTING)) {
            bw.write(calculateTimeStamp());
        }
        catch (IOException ex) {
            Logger.getLogger(InfoHandler.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    //TODO implement
//    private String findLatestPosts(BlockingQueue<Post> collection) {
//        Arrays.sort(collection.toArray());
//
//        Collections.sort(list, new Comparator<Post>() {
//
//            @Override
//            public int compare(Post o1, Post o2) {
//                return o1.getDate().compareTo(o2.getDate());
//            }
//
//        });
//
//    }

    private String calculateTimeStamp() {
        LocalDateTime parseTime = LocalDateTime.now();
        return parseTime.format(formatter);
    }
}