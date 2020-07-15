import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.yaml.snakeyaml.Yaml;

import javax.json.*;
import javax.json.stream.JsonGenerator;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class Main {
    static Yaml yaml = new Yaml();
    static Path gitRepo;

    static JsonGenerator generator;
    private static MessageDigest hasher;
    private static Path zipPath;


    public static void main(String[] args) throws IOException {
        StringWriter output = new StringWriter();
        generator = Json.createGenerator(output);
        generator.writeStartArray();

        JsonReader reader = Json.createReader(new FileInputStream("config.json"));
        JsonObject config = reader.readObject();

        String gitPath = config.getString("repo");
        
        Main.gitRepo = Paths.get("temp");
        Main.zipPath = Paths.get("zip");

        if(!Files.exists(Main.gitRepo))
            Files.createDirectories(Main.gitRepo);
        if(!Files.exists(zipPath))
            Files.createDirectories(zipPath);
        else
        {
            Util.EmptyDirectory(zipPath);
            Files.createDirectories(zipPath);
        }

        System.out.println("* Cloning repository...");
        ProcessBuilder bp = new ProcessBuilder("git", "clone", gitPath, Main.gitRepo.toString());
        Process git = bp.start();
        BufferedReader br = new BufferedReader(new InputStreamReader(git.getInputStream()));
        String line;
        while ((line = br.readLine()) != null) {
            System.out.println(line);
        }
        br = new BufferedReader(new InputStreamReader(git.getErrorStream()));
        while ((line = br.readLine()) != null) {
            System.out.println(line);
        }

        System.out.println("* Building zipfile skeleton");
        Files.copy(gitRepo.resolve("build.gradle"), zipPath.resolve("build.gradle"));
        Files.copy(gitRepo.resolve("gradlew"), zipPath.resolve("gradlew"));
        Files.copy(gitRepo.resolve("gradlew.bat"), zipPath.resolve("gradlew.bat"));
        Files.copy(gitRepo.resolve("settings.gradle"), zipPath.resolve("settings.gradle"));
        Files.copy(gitRepo.resolve("course-info.yaml"), zipPath.resolve("course-info.yaml"));
        Files.writeString(zipPath.resolve("course-info.yaml"), "mode: Study\n", StandardOpenOption.APPEND);

        Files.createDirectory(zipPath.resolve("lib"));
        Util.CopyDirectory(gitRepo.resolve("lib"), zipPath.resolve("lib"));

        Files.createDirectory(zipPath.resolve("gradle"));
        Util.CopyDirectory(gitRepo.resolve("gradle"), zipPath.resolve("gradle"));

        Files.createDirectory(zipPath.resolve(".idea"));
        Files.copy(gitRepo.resolve(".idea/misc.xml"), zipPath.resolve(".idea/misc.xml"));
        //Files.createDirectory(zipPath.resolve(".idea/modules"));
        String projectName = "";

        try {
            //read file
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(gitRepo.resolve(".idea/modules.xml").toFile());


            NodeList modules = doc.getElementsByTagName("module");

            for(int i = 0; i < modules.getLength(); i++) {
                org.w3c.dom.Node module = modules.item(i);
                String fileurl = module.getAttributes().getNamedItem("fileurl").getNodeValue();
                if(fileurl.contains(".iml")) { //TODO, find a better way to find the project name
                    projectName = fileurl;
                    projectName = projectName.substring(projectName.lastIndexOf("/")+1, projectName.length()-4);
                    break;
                }
            }
            System.out.println(" - Found project name " + projectName);
            /*for(int i = 0; i < modules.getLength(); i++) {
                org.w3c.dom.Node module = modules.item(i);

                String fileurl = module.getAttributes().getNamedItem("fileurl").getNodeValue();
                String filepath = module.getAttributes().getNamedItem("filepath").getNodeValue();

                String origPath = filepath;

                if(!fileurl.contains(projectName + ".iml") && fileurl.contains("/modules/")) {
                    fileurl = fileurl.replace(projectName + ".", "");
                    filepath = filepath.replace(projectName + ".", "");
                    module.getAttributes().getNamedItem("fileurl").setNodeValue(fileurl);
                    module.getAttributes().getNamedItem("filepath").setNodeValue(filepath);
                }

                origPath = origPath.replace("$PROJECT_DIR$/", "");
                filepath = filepath.replace("$PROJECT_DIR$/", "");

                Path dir = zipPath.resolve(filepath).getParent();
                if(!Files.exists(dir))
                    Files.createDirectories(dir);
                Files.copy(gitRepo.resolve(origPath), zipPath.resolve(filepath));
            }
            //write to file
            TransformerFactory tFactory = TransformerFactory.newInstance();
            Transformer transformer = tFactory.newTransformer();

            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(zipPath.resolve(".idea/modules.xml").toFile());
            transformer.transform(source, result);*/

        } catch (ParserConfigurationException | SAXException/* | TransformerException*/ e) {
            e.printStackTrace();
        }

        System.out.println("* Scanning for exercises...");
        Map<String, Object> courseInfo = yaml.load(Files.newInputStream(Main.gitRepo.resolve("course-info.yaml")));
        ArrayList<String> content = (ArrayList<String>) courseInfo.get("content");
        for(String lesson : content) {
            parseLesson(lesson);
        }


        generator.writeEnd();
        generator.close();

        System.out.println("* Writing Json...");
        Map<String, Object> jconfig = Map.of(JsonGenerator.PRETTY_PRINTING, Boolean.TRUE);
        JsonWriter writer = Json.createWriterFactory(jconfig).createWriter(new FileWriter("out/exercises.json"));
        writer.write(Json.createReader(new StringReader(output.toString())).read());
        writer.close();

        System.out.println("* Building zip...");

        Path zipOutPath = Paths.get("out/" + projectName + ".zip");
        Files.deleteIfExists(zipOutPath);

        Map<String, String> env = new HashMap<>();
        env.put("create", "true");
        URI uri = URI.create("jar:" + zipOutPath.toUri().toString());
        try (FileSystem zipfs = FileSystems.newFileSystem(uri, env)) {
            //Util.CopyDirectory(zipPath, zipfs.getPath("/"));
            Files.walk(zipPath).forEach(f -> {
                Path targetFile = zipfs.getPath("/" + zipPath.relativize(f).toString());
                try {
                    if(Files.isDirectory(f))
                    {
                        if(!Files.exists(targetFile))
                            Files.createDirectory(targetFile);
                    }
                    else
                        Files.copy(f, targetFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    private static void parseLesson(String lesson) throws IOException {
        Map<String, Object> lessonInfo = yaml.load(Files.newInputStream(gitRepo.resolve(lesson).resolve("lesson-info.yaml")));
        ArrayList<String> content = (ArrayList<String>) lessonInfo.get("content");

        Files.createDirectory(zipPath.resolve(lesson));
        Files.copy(gitRepo.resolve(lesson).resolve("lesson-info.yaml"), zipPath.resolve(lesson).resolve("lesson-info.yaml"));

        for(String task : content) {
            parseTask(lesson, task);
        }
    }

    private static void parseTask(String lesson, String task) throws IOException {
        Map<String, Object> taskInfo = yaml.load(Files.newInputStream(gitRepo.resolve(lesson).resolve(task).resolve("task-info.yaml")));
        Map taskInfoOut = new HashMap();

        ArrayList<HashMap<String, Object> > files = (ArrayList<HashMap<String, Object>>) taskInfo.get("files");
        System.out.println(" - Scanning " + lesson + "/" + task);

        Path taskSrc = gitRepo.resolve(lesson).resolve(task);
        Path taskDst = zipPath.resolve(lesson).resolve(task);
        Files.createDirectory(taskDst);
        Files.createDirectory(taskDst.resolve("src"));
        Files.createDirectory(taskDst.resolve("test"));
        Files.copy(taskSrc.resolve("task.md"), taskDst.resolve("task.md"));

        taskInfoOut.put("type", "edu"); // TODO: get from taskInfo
        taskInfoOut.put("status", "Unchecked");
        taskInfoOut.put("record", -1);
        List filesOut = new ArrayList();
        taskInfoOut.put("files", filesOut);

        generator.writeStartObject();
        generator.write("subject", lesson);
        generator.write("name", task);


        long fileSize = 0;
        try {
            hasher = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        Set<String> points = new HashSet<>();
        Set<Test> tests = new HashSet<>();
        for(HashMap<String, Object> file : files) {
            String filename = (String) file.get("name");
            System.out.println("   - file " + filename);

            String fileData = Files.readString(taskSrc.resolve(filename));
            hasher.update(fileData.getBytes());
            fileSize += fileData.getBytes().length;

            CompilationUnit compilationUnit = StaticJavaParser.parse(fileData);
            parseFileForPoints(compilationUnit.findRootNode(), points);

            parseFileForTests(compilationUnit.findRootNode(), tests);


            fileData = fileData.replace("\r\n", "\n");
            ArrayList<Map> placeholders = (ArrayList<Map>) file.get("placeholders");
            if(placeholders != null) {
                for(Map placeholder : placeholders) {
                    int offset = (int) placeholder.get("offset");
                    int length = (int) placeholder.get("length");
                    String replacement = (String) placeholder.get("placeholder_text");
                    fileData = fileData.substring(0, offset) + replacement + fileData.substring(offset+length);
                }
                //TODO: test when multiple placeholders are in
            }
            Files.writeString(taskDst.resolve(filename), fileData);

            Map fileOut = new HashMap();

            fileOut.put("name", filename);
            fileOut.put("visible", file.get("visible"));
            fileOut.put("text", fileData);
            fileOut.put("learner_created", false);
            filesOut.add(fileOut);

        }

        generator.writeStartArray("points");
        for(String p : points)
            generator.write(p);
        generator.writeEnd();

        generator.writeStartArray("tests");
        for(Test t : tests)
        {
            generator.writeStartObject();
            generator.write("name", t.name);
            generator.write("className", t.className);
            generator.write("point", t.point);
            generator.writeEnd();
        }
        generator.writeEnd();


        generator.write("hash", Util.bytesToHex(hasher.digest()));
        generator.write("size", fileSize);
        generator.writeEnd();


        Files.writeString(taskDst.resolve("task-info.yaml"), yaml.dump(taskInfoOut));
    }

    private static String findName(Node node)
    {
        for(Node n : node.getChildNodes())
        {
            if(n instanceof SimpleName)
                return ((SimpleName)n).asString();
        }
        return "";
    }

    private static String findPoint(Node node)
    {
        for(Node n : node.getChildNodes()) {
            if(n instanceof SingleMemberAnnotationExpr) {
                if(n.getChildNodes().get(0).toString().equals("Points")) {
                    return ((StringLiteralExpr)n.getChildNodes().get(1)).getValue();
                }
            }
        }
        if(node.getParentNode().isPresent())
            return findPoint(node.getParentNode().get());
        return "";
    }


    private static void parseFileForPoints(Node rootNode, Set<String> points) {
        for(Node n : rootNode.getChildNodes()) {
            if(n instanceof ClassOrInterfaceDeclaration || n instanceof MethodDeclaration) {
                parseFileForPoints(n, points);
            }
            else if(n instanceof SingleMemberAnnotationExpr) {
                if(n.getChildNodes().get(0).toString().equals("Points")) {
                    points.add(((StringLiteralExpr)n.getChildNodes().get(1)).getValue());
                }
            }
        }
    }


    private static void parseFileForTests(Node rootNode, Set<Test> tests) {
        for(Node n : rootNode.getChildNodes()) {
            if(n instanceof ClassOrInterfaceDeclaration || n instanceof MethodDeclaration) {
                parseFileForTests(n, tests);
            }
            else if(n instanceof MarkerAnnotationExpr) {
                if(n.getChildNodes().get(0).toString().equals("Test")) {
                    tests.add(new Test(
                            findName(rootNode),
                            findName(rootNode.getParentNode().get()),
                            findPoint(rootNode)));
                }
            }
        }

    }
}
