import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import org.yaml.snakeyaml.Yaml;

import javax.json.*;
import javax.json.stream.JsonGenerator;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Main {
    static Yaml yaml = new Yaml();
    static Path out;

    static JsonGenerator generator;
    private static MessageDigest hasher;


    public static void main(String[] args) throws IOException {
        StringWriter output = new StringWriter();
        generator = Json.createGenerator(output);
        generator.writeStartArray();

        JsonReader reader = Json.createReader(new FileInputStream("config.json"));
        JsonObject config = reader.readObject();

        String gitPath = config.getString("repo");

        out = Paths.get("temp");

        if(!Files.exists(out))
            Files.createDirectory(out);

        System.out.println("* Cloning repository...");
        ProcessBuilder bp = new ProcessBuilder("/usr/bin/git", "clone", gitPath, out.toString());
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

        System.out.println("* Scanning for exercises...");
        Map<String, Object> courseInfo = yaml.load(Files.newInputStream(out.resolve("course-info.yaml")));
        ArrayList<String> content = (ArrayList<String>) courseInfo.get("content");
        for(String lesson : content) {
            parseLesson(lesson);
        }


        generator.writeEnd();
        generator.close();

        System.out.print("* Writing Json...");
        Map<String, Object> jconfig = Map.of(JsonGenerator.PRETTY_PRINTING, Boolean.TRUE);
        JsonWriter writer = Json.createWriterFactory(jconfig).createWriter(new FileWriter("out.json"));
        writer.write(Json.createReader(new StringReader(output.toString())).read());
        writer.close();
    }

    private static void parseLesson(String lesson) throws IOException {
        Map<String, Object> lessonInfo = yaml.load(Files.newInputStream(out.resolve(lesson).resolve("lesson-info.yaml")));
        ArrayList<String> content = (ArrayList<String>) lessonInfo.get("content");
        for(String task : content) {
            parseTask(lesson, task);
        }
    }

    private static void parseTask(String lesson, String task) throws IOException {
        Map<String, Object> taskInfo = yaml.load(Files.newInputStream(out.resolve(lesson).resolve(task).resolve("task-info.yaml")));
        ArrayList<HashMap<String, Object> > files = (ArrayList<HashMap<String, Object>>) taskInfo.get("files");
        System.out.println(" - Scanning " + lesson + "/" + task);

        generator.writeStartObject();
        generator.write("subject", lesson);
        generator.write("name", task);

        long fileSize = 0;

        try {
            hasher = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        for(HashMap<String, Object> file : files) {
            String filename = (String) file.get("name");
            System.out.println("   - file " + filename);

            String fileData = Files.readString(out.resolve(lesson).resolve(task).resolve(filename));
            hasher.update(fileData.getBytes());
            fileSize += fileData.getBytes().length;

            CompilationUnit compilationUnit = StaticJavaParser.parse(fileData);
            parseFile(compilationUnit.findRootNode());

        }
        generator.write("hash", bytesToHex(hasher.digest()));
        generator.write("size", fileSize);
        generator.writeEnd();
    }

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    private static void parseFile(Node rootNode) {
        for(Node n : rootNode.getChildNodes()) {
            if(n instanceof ClassOrInterfaceDeclaration || n instanceof MethodDeclaration) {
                parseFile(n);
            }
            else if(n instanceof SingleMemberAnnotationExpr) {
                if(n.getChildNodes().get(0).toString().equals("Points")) {
                    generator.writeStartArray("points");
                    generator.write(((StringLiteralExpr)n.getChildNodes().get(1)).getValue());
                    generator.writeEnd();
                }
            }
        }

    }
}
