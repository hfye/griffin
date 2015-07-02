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
package com.pawandubey.griffin;

import static com.pawandubey.griffin.DirectoryCrawler.config;
import com.pawandubey.griffin.cli.GriffinCommand;
import com.pawandubey.griffin.cli.NewCommand;
import com.pawandubey.griffin.cli.PreviewCommand;
import com.pawandubey.griffin.cli.PublishCommand;
import com.pawandubey.griffin.model.Parsable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.ParserProperties;
import org.kohsuke.args4j.spi.BooleanOptionHandler;
import org.kohsuke.args4j.spi.SubCommand;
import org.kohsuke.args4j.spi.SubCommandHandler;
import org.kohsuke.args4j.spi.SubCommands;

/**
 *
 * @author Pawan Dubey pawandubey@outlook.com
 */
public class Griffin {

    public final static LinkedBlockingQueue<Parsable> fileQueue = new LinkedBlockingQueue<>();

    private final DirectoryCrawler crawler;
    private Parser parser;

    @Option(name = "--version", aliases = {"-v"}, handler = BooleanOptionHandler.class, usage = "print the current version")
    private boolean version = false;

    @Option(name = "--help", aliases = {"-h"}, handler = BooleanOptionHandler.class, usage = "print help message for the command")
    private boolean help = false;

    @Argument(usage = "Execute subcommands", metaVar = "<commands>", handler = SubCommandHandler.class)
    @SubCommands({
        @SubCommand(name = "new", impl = NewCommand.class),
        @SubCommand(name = "publish", impl = PublishCommand.class),
        @SubCommand(name = "preview", impl = PreviewCommand.class)
    })
    public GriffinCommand commands;

    /**
     * Creates a new instance of Griffin
     */
    public Griffin() {
        crawler = new DirectoryCrawler();
    }

    /**
     * Creates a new instance of Griffin with the root directory of the site set
     * to the given path.
     *
     * @param path The path to the root directory of the griffin site.
     */
    public Griffin(Path path) {
        crawler = new DirectoryCrawler(path.toString());
    }

    /**
     * Creates(scaffolds out) a new Griffin directory at the given path.
     *
     * @param path The path at which to scaffold.
     * @param name The name to give to the directory
     * @throws IOException the exception
     */
    public void initialize(Path path, String name) throws IOException {
        checkPathValidity(path, name);

        initializeConfigurationSettings(path, name);

        Initializer init = new Initializer();
        init.scaffold(path, name);
        config.writeConfig(path.resolve(name));
    }

    /**
     * Parses the content of the site in the 'content' directory and produces
     * the output. It parses incrementally i.e it only parses the content which
     * has changed since the last parsing event if the fastParse variable is
     * true.
     *
     * @param fastParse Do a fast incremental parse
     * @throws IOException the exception
     * @throws InterruptedException the exception
     */
    public void publish(boolean fastParse) throws IOException, InterruptedException {
        InfoHandler info = new InfoHandler();
        long start = System.currentTimeMillis();
        if (fastParse == true) {
            crawler.fastReadIntoQueue(Paths.get(DirectoryCrawler.SOURCE_DIRECTORY).normalize());
        }
        else {
            crawler.readIntoQueue(Paths.get(DirectoryCrawler.SOURCE_DIRECTORY).normalize());
        }
        long read = System.currentTimeMillis();
        info.findLatestPosts(fileQueue);
        info.findNavigationPages(fileQueue);
        long ps = System.currentTimeMillis();
        parser = new Parser();
        parser.parse(fileQueue);
        info.writeInfoFile();
        long pe = System.currentTimeMillis();
        System.out.println("Crawl: " + (read - start));
        System.out.println("Parse: " + (pe - ps));
    }

    /**
     * Creates the server and starts a preview at the given port
     *
     * @param port the port number for the server to run on.
     */
    public void preview(Integer port) {
        Server server = new Server(port);
        server.startPreview();
        server.openBrowser();
    }

    private void printHelpMessage() {
        String title = this.getClass().getPackage().getImplementationTitle();
        String ver = this.getClass().getPackage().getImplementationVersion();
        String author = this.getClass().getPackage().getImplementationVendor();
        String header = title + " version " + ver + " copyright " + author;
        String desc = "a simple and fast static site generator";
        String usage = "usage: " + title + " [subcommand] [options..] [arguments...]";
        String moreHelp = "run " + title + " <subcommand> " + "--help to see more help about individual subcommands";

        StringBuilder sb = new StringBuilder();
        sb.append(header).append("\n");
        if (this.version) {
            System.out.println(sb.toString());
        }
        else {
            sb.append(desc)
                    .append("\n")
                    .append(usage)
                    .append("\n")
                    .append(moreHelp)
                    .append("\n\n");
            System.out.println(sb.toString());
        }
    }

    private void initializeConfigurationSettings(Path path, String name) throws NumberFormatException, IOException {
        String nam, tag, auth, src, out, date;// = config.getSiteName();//,
        String port;// = config.getPort();

        showWelcomeMessage(path, name);

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        System.out.println("1. What would you like to call your site?(" + config.getSiteName() + "):");
        nam = br.readLine();
        System.out.println("2. Who's authoring this site?");
        auth = br.readLine();
        System.out.println("3. What will be the tagline for the site?(" + config.getSiteTagline() + "):");
        tag = br.readLine();
        System.out.println("4. What will you like to name the folder where your posts will be stored?(" + config.getSourceDir() + "):");
        src = br.readLine();
        System.out.println("5. What will you like to name the folder where the generated site will be stored?(" + config.getOutputDir() + "):");
        out = br.readLine();
        System.out.println("6. What will you like to format the dates on your posts and pages as?(" + config.getDateFormat() + "):");
        date = br.readLine();
        System.out.println("7. On what port will you like to see the live preview of your site?(" + config.getPort() + "):");
        port = br.readLine();
        finalizeConfigurationSettings(nam, auth, tag, src, out, date, port);
    }

    private void finalizeConfigurationSettings(String nam, String auth, String tag, String src, String out, String date, String port) {
        if (nam != null && !nam.equals(config.getSiteName()) && !nam.equals("")) {
            config.withSiteName(nam);
        }
        if (auth != null && !auth.equals(config.getSiteAuthor()) && !auth.equals("")) {
            config.withSiteAuthour(auth);
        }
        if (tag != null && !tag.equals(config.getSiteTagline()) && !tag.equals("")) {
            config.withSiteTagline(tag);
        }
        if (src != null && !src.equals(config.getSourceDir()) && !src.equals("")) {
            config.withSourceDir(src);
        }
        if (out != null && !out.equals(config.getOutputDir()) && !out.equals("")) {
            config.withOutputDir(out);
        }
        if (date != null && !date.equals(config.getDateFormat()) && !date.equals("")) {
            config.withDateFormat(date);
        }
        if (port != null && !port.equals(config.getPort().toString()) && !port.equals("")) {
            config.withPort(Integer.parseInt(port));
        }
    }

    private void showWelcomeMessage(Path path, String name) {
        StringBuilder welcomeMessage = new StringBuilder();
        printAsciiGriffin();
        welcomeMessage.append("Heya! This is griffin. Your personal, fast and easy static site generator. (And an overall good guy)").append("\n");
        welcomeMessage.append("You have chosen to create your new griffin site at: ").append(path.resolve(name).toString()).append("\n");
        welcomeMessage.append("I'd love to help you set up some initial settings for your site, so let's go.").append("\n");
        welcomeMessage.append("I'll ask you a set of simple questions and you can type in your answer. Some questions have a default answer, which will be marked in brackets.\nYou can just press enter to accept the default value in those cases.").append("\n\n");
        System.out.println(welcomeMessage);
    }

    public void printAsciiGriffin() {
        System.out.println("                       ___     ___                  ");
        System.out.println("                __   /'___\\  /'___\\  __             ");
        System.out.println("   __    _ __  /\\_\\ /\\ \\__/ /\\ \\__/ /\\_\\     ___    ");
        System.out.println(" /'_ `\\ /\\`'__\\\\/\\ \\\\ \\ ,__\\\\ \\ ,__\\\\/\\ \\  /' _ `\\  ");
        System.out.println("/\\ \\L\\ \\\\ \\ \\/  \\ \\ \\\\ \\ \\_/ \\ \\ \\_/ \\ \\ \\ /\\ \\/\\ \\ ");
        System.out.println("\\ \\____ \\\\ \\_\\   \\ \\_\\\\ \\_\\   \\ \\_\\   \\ \\_\\\\ \\_\\ \\_\\");
        System.out.println(" \\/___L\\ \\\\/_/    \\/_/ \\/_/    \\/_/    \\/_/ \\/_/\\/_/");
        System.out.println("   /\\____/                                          ");
        System.out.println("   \\_/__/                                           ");
    }

    private void checkPathValidity(Path path, String name) throws FileSystemException, NotDirectoryException, FileAlreadyExistsException {
        if (!Files.isWritable(path)) {
            System.out.println("That path doesn't seem to be writable :(\nCheck if you have write permission to that path and try again.");
            throw new java.nio.file.FileSystemException(path.toString());
        }
        if (Files.exists(path.resolve(name))) {
            System.out.println("Aw shucks! It seems like there is already a file of that name at that path :(\nTry again with another name.");
            throw new FileAlreadyExistsException(path.resolve(name).toString());
        }
        if (!Files.isDirectory(path)) {
            System.out.println("Aw, man. That path does not seem to be a valid directory :(\nTry with another path again.");
            throw new java.nio.file.NotDirectoryException(path.toString());
        }
    }

    /**
     * @param args the command line arguments
     * @throws java.io.IOException the exception
     * @throws java.lang.InterruptedException the exception
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        try {
            Griffin griffin = new Griffin();

            CmdLineParser parser = new CmdLineParser(griffin, ParserProperties.defaults().withUsageWidth(120));
            parser.parseArgument(args);

            if (griffin.help || griffin.version || args.length == 0) {
                griffin.printHelpMessage();
                parser.printUsage(System.out);
            }
            else {
                griffin.commands.execute();
            }
        }
        catch (CmdLineException ex) {
            Logger.getLogger(Griffin.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

}
