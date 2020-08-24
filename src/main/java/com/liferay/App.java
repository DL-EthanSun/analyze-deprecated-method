package com.liferay;

import com.google.gson.GsonBuilder;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.eclipse.jdt.core.dom.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.eclipse.jdt.core.dom.ASTNode.METHOD_DECLARATION;

/**
 * @author Ethan Sun
 * @param 'args[0]' The liferay-portal project path(Absolute).
 * @param 'args[1]' The location where the json file will locate at(Absolute).
 */
public class App {
    public static void main( String[] args ) {

        List<Map<String, String>> simpleNameList = new LinkedList<>();

        ASTParser parser = ASTParser.newParser(AST.JLS14);

        parser.setKind(ASTParser.K_COMPILATION_UNIT);

        parser.setResolveBindings(true);

        Collection<File> fileCollection = FileUtils.listFiles(new File(args[0]), FileFilterUtils.suffixFileFilter(".java"), FileFilterUtils.makeDirectoryOnly(new IOFileFilter() {
            @Override
            public boolean accept(File file) {
                return true;
            }

            @Override
            public boolean accept(File file, String s) {
                if (s.equals("classes") && file.getAbsolutePath().contains("/test/")) {
                    return false;
                }

                return true;
            }
        }));

        fileCollection.forEach(file -> {
            try {
                String readContents = FileUtils.readFileToString(file, StandardCharsets.UTF_8);

                parser.setSource(readContents.toCharArray());

                CompilationUnit cu = (CompilationUnit) parser.createAST(null);

                cu.accept(new ASTVisitor() {

                    @Override
                    public boolean visit(MarkerAnnotation node) {
                        Name name = node.getTypeName();

                        String qualifiedName = name.getFullyQualifiedName();

                        if (qualifiedName.equals("Deprecated")) {

                            ASTNode astNode = node.getParent();

                            int nodeType = astNode.getNodeType();

                            if (nodeType == METHOD_DECLARATION) {

                                String className = FilenameUtils.getBaseName(file.getName());

                                Javadoc javadoc = (Javadoc) astNode.getStructuralProperty(MethodDeclaration.JAVADOC_PROPERTY);

                                SimpleName methodName = (SimpleName) astNode.getStructuralProperty(MethodDeclaration.NAME_PROPERTY);

                                String packageName = cu.getPackage().getName().getFullyQualifiedName();

                                Collection <SingleVariableDeclaration> parametersCollection = (Collection<SingleVariableDeclaration>) astNode.getStructuralProperty(MethodDeclaration.PARAMETERS_PROPERTY);

                                SingleVariableDeclaration[] parameterArrays = parametersCollection.toArray(new SingleVariableDeclaration[0]);

                                List<String> parameters = new ArrayList<>();

                                Arrays.stream(parameterArrays).forEachOrdered(e -> parameters.add(e.getType().toString()));

                                String deprecatedVersion = "";

                                String javadocCompact = "";

                                if (Objects.nonNull(javadoc)) {
                                    javadocCompact = javadoc.toString().replaceAll("(/\\*+\\s\n)|((?<=\n)\\s\\*\\s)|(\n\\s\\*/\n)","");

                                    Matcher matcher = _pattern.matcher(javadocCompact);

                                    if (matcher.find()) {
                                        deprecatedVersion = matcher.group(1);
                                    }
                                }

                                Map<String,String> linkedHashMap = new LinkedHashMap<>();

                                linkedHashMap.put("className", className);

                                linkedHashMap.put("deprecatedVersion", deprecatedVersion);

                                linkedHashMap.put("javadoc", javadocCompact);

                                linkedHashMap.put("methodName", methodName.getIdentifier());

                                linkedHashMap.put("packageName", packageName);

                                linkedHashMap.put("parameters", parameters.toString());

                                simpleNameList.add(linkedHashMap);
                            }
                        }

                        return super.visit(node);
                    }
                });
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        });

        List<Map<String, String>> validValueLists = simpleNameList.stream().filter(e -> e.get("deprecatedVersion").equals("7.2")).collect(Collectors.toList());

        String contents = new GsonBuilder().setPrettyPrinting().create().toJson(validValueLists);

        Path folder = Paths.get(args[1]);

        if (Files.isDirectory(folder)) {
            try {
                Files.createFile(folder.resolve("deprecatedMethod72.json"));

                Files.write(folder.resolve("deprecatedMethod72.json"), contents.getBytes(Charset.defaultCharset()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    public static final Pattern _pattern = Pattern.compile("(?<=\\()([67]\\.\\d)\\.x(?=\\))");
}
