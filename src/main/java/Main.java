import org.apache.bcel.classfile.*;
import org.apache.commons.cli.*;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.CountDownLatch;

public class Main {
    private static Map<String, Remapper.ClassMapping> convertMappings(List<Mapping.ClassMapping> mappings) {
        Map<String, Remapper.ClassMapping> result = new HashMap<>();
        for (Mapping.ClassMapping mapping : mappings) {
            result.put(mapping.obfuscatedName.replace('.', '/'), new Remapper.ClassMapping(mapping));
        }
        return result;
    }

    public static void main(String[] args) {
        Options options = new Options();
        options.addRequiredOption("m", null, true, "obfuscation mapping file");
        options.addRequiredOption("i", null, true, "input directory");
        options.addRequiredOption("o", null, true, "output directory");

        CommandLineParser clp = new DefaultParser();
        try {
            CommandLine cl = clp.parse(options, args);
            String mappingFilePath = cl.getOptionValue('m');
            String inputDir = cl.getOptionValue('i');
            String tmp = cl.getOptionValue('o');
            String outDirRoot;
            if (!tmp.isEmpty() && tmp.charAt(tmp.length()-1) != '/') {
                outDirRoot = tmp + "/";
            } else {
                outDirRoot = tmp;
            }

            System.err.println("parsing obfuscation mapping...");
            Mapping.Parser mp = new Mapping.Parser(mappingFilePath);
            Map<String, Remapper.ClassMapping> obfuscatedMappings = convertMappings(mp.parse());
            Map<String, String> classNameMap = new HashMap<>();
            Map<String, String> inverseClassNameMap = new HashMap<>();
            obfuscatedMappings.forEach((String obfuscatedName, Remapper.ClassMapping cm) -> {
                classNameMap.put(obfuscatedName, cm.originalName);
                inverseClassNameMap.put(cm.originalName, obfuscatedName);
            });

            System.err.println("building class hierarchy..."); // also builds a list of classes to process
            Map<String, String[]> hier = new HashMap<>();
            List<JavaClass> classes = new ArrayList<>();
            FileVisitor<Path> fv = new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path path, BasicFileAttributes attrs)
                        throws IOException {
                    String path_s = path.toString();
                    if (path_s.endsWith(".class")) {
                        ClassParser classParser = new ClassParser(path_s);
                        JavaClass cls = classParser.parse();
                        classes.add(cls);
                        String className = cls.getClassName(); // separated with '.'
                        if (className.endsWith("package-info")) {
                            return FileVisitResult.CONTINUE;
                        }
                        ArrayList<String> parents = new ArrayList<>(); // FQCN, '.'-separated
                        if (cls.getSuperclassNameIndex() > 0) {
                            parents.add(cls.getSuperclassName());
                        }
                        Collections.addAll(parents, cls.getInterfaceNames());
                        if (parents.size() > 0) {
                            String[] parents_a = new String[parents.size()];
                            for (int i = 0; i < parents_a.length; i++) {
                                parents_a[i] = parents.get(i).replace('.', '/');
                            }
                            hier.put(className.replace('.', '/'), parents_a);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            };
            Files.walkFileTree(Paths.get(inputDir), fv);

            System.err.println("deobfuscating...");
            long startTime = System.currentTimeMillis();
            int nWorker = Runtime.getRuntime().availableProcessors();
            Thread[] workers = new Thread[nWorker];
            CountDownLatch latch = new CountDownLatch(nWorker);
            System.err.printf("number of worker threads: %d\n", nWorker);
            class Worker extends Thread {
                private int start, end;
                private Worker(int start, int end) {
                    this.start = start;
                    this.end = end;
                }
                public void run() {
                    for (int i=start; i<end; i++) {
                        JavaClass cls = classes.get(i);
                        Remapper remapper = new Remapper(obfuscatedMappings, classNameMap, inverseClassNameMap, hier, cls);
                        remapper.remap();
                        Constant[] constants = cls.getConstantPool().getConstantPool();
                        // get '/'-separated FQCN
                        String fqClassName = ((ConstantUtf8)constants[((ConstantClass)constants[cls.getClassNameIndex()]).getNameIndex()]).getBytes();
                        String outPath = outDirRoot + fqClassName + ".class";
                        try {
                            remapper.dump(outPath);
                        } catch (IOException e) {
                            System.err.println(e.getMessage());
                        }
                    }
                    latch.countDown();
                }
            }
            int[] p = new int[nWorker+1];
            int nClass = classes.size();
            for (int i=0; i<nWorker; i++) {
                p[i] = nClass*i/nWorker;
            }
            p[nWorker] = nClass;
            for (int i=0; i<nWorker; i++) {
                workers[i] = new Worker(p[i], p[i+1]);
                workers[i].start();
            }
            latch.await();
            long elapsed = System.currentTimeMillis() - startTime;
            System.err.printf("done in %dms\n", elapsed);
        } catch (ParseException | IOException | Mapping.Parser.SyntaxErrorException | InterruptedException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }
}
