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
import java.util.stream.Stream;

import static org.eclipse.jdt.core.dom.ASTNode.METHOD_DECLARATION;


public class App {

    /**
     * @author Ethan Sun
     * @param args args[0]:The liferay-portal project path(Absolute), arg[1]:The location where the json file will locate at(Absolute).
     */
    public static void main( String[] args ) {

        List<Map<String, Object>> simpleNameList = new LinkedList<>();

        ASTParser parser = ASTParser.newParser(AST.JLS14);

        parser.setKind(ASTParser.K_COMPILATION_UNIT);

        parser.setResolveBindings(true);

        String[] filterDirs = new String[] {"modules", "portal-impl", "portal-kernel", "portal-web", "support-tomcat", "support-websphere", "tools", "util-bridges", "util-java", "util-slf4j", "util-taglib"};
        
        List<File> fileCollection = Stream.of(
                filterDirs
        ).map(
                name -> new File(args[0], name)
        ).flatMap(
                dir -> {
                    Collection<File> files = FileUtils.listFiles(dir, new String[] {"java"}, true);

                    return files.stream();
                }
        ).collect(
                Collectors.toList()
        );

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

                                Javadoc javadocModel = (Javadoc) astNode.getStructuralProperty(MethodDeclaration.JAVADOC_PROPERTY);

                                SimpleName methodName = (SimpleName) astNode.getStructuralProperty(MethodDeclaration.NAME_PROPERTY);

                                String packageName = cu.getPackage().getName().getFullyQualifiedName();

                                Collection <SingleVariableDeclaration> parametersCollection = (Collection<SingleVariableDeclaration>) astNode.getStructuralProperty(MethodDeclaration.PARAMETERS_PROPERTY);

                                SingleVariableDeclaration[] parameterArrays = parametersCollection.toArray(new SingleVariableDeclaration[0]);

                                String[] parameterTypeArrays = Arrays.stream(parameterArrays).map(SingleVariableDeclaration::getType).map(ASTNode::toString).toArray(String[]::new);

                                String deprecatedVersion = "";

                                String javadoc = "";

                                if (Objects.nonNull(javadocModel)) {

                                    List list = javadocModel.tags();

                                    for (int i = 0; i < list.size(); i++) {
                                        if(list.get(i).toString().startsWith("\n * @deprecated")) {
                                            javadoc = list.get(i).toString().replace("\n * ", "");
                                        };
                                    }

                                    Matcher matcher = _pattern.matcher(javadoc);

                                    if (matcher.find()) {
                                        deprecatedVersion = matcher.group(1);
                                    }
                                }

                                Map<String,Object> linkedHashMap = new LinkedHashMap<>();

                                linkedHashMap.put("className", className);

                                linkedHashMap.put("deprecatedVersion", deprecatedVersion);

                                linkedHashMap.put("javadoc", javadoc);

                                linkedHashMap.put("methodName", methodName.getIdentifier());

                                linkedHashMap.put("packageName", packageName);

                                linkedHashMap.put("parameters", parameterTypeArrays);

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

        Path folder = Paths.get(args[1]);

        String[][] options = new String[][] {{"7.2", "deprecatedMethod72.json"}, {"", "deprecatedMethodNoneVersion.json"}};

        Arrays.stream(options).forEach(e -> {
            List<Map<String, Object>> deprecatedMethodsVersionList = simpleNameList.stream().filter(v -> v.get("deprecatedVersion").equals(e[0])).collect(Collectors.toList());

            String jsonData = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create().toJson(deprecatedMethodsVersionList);

            if (Files.isDirectory(folder)) {
                try {
                    Files.createFile(folder.resolve(e[1]));

                    Files.write(folder.resolve(e[1]), jsonData.getBytes(StandardCharsets.UTF_8));
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            }
        });
    }

    public static final Pattern _pattern = Pattern.compile("(?<=\\()([67]\\.\\d)\\.x(?=\\))");
}
