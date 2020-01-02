import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class SrgMappingParser implements MappingParser {
    BufferedReader reader;
    String currentLine;
    int currentLineNo;
    public SrgMappingParser(String filePath) throws IOException {
        reader = new BufferedReader(new FileReader(filePath));
        readLine();
    }
    private void readLine() throws IOException {
        currentLine = reader.readLine();
        currentLineNo++;
    }
    @Override
    public List<Mapping.ClassMapping> parse() throws IOException, InvalidInputException {
        Map<String, Mapping.ClassMapping> classMappings = new HashMap<>();
        while (currentLine != null) {
            if (currentLine.startsWith("PK: ")) {
                // do nothing
            } else if (currentLine.startsWith("CL: ")) {
                String[] f = currentLine.split(" ");
                if (f.length != 3) throw new InvalidInputException(currentLineNo, "wrong number of fields");
                String obfName = f[1];
                String deobfName = f[2];
                classMappings.put(obfName, new Mapping.ClassMapping(deobfName, obfName, new ArrayList<>(), new ArrayList<>()));
            } else if (currentLine.startsWith("FD: ")) {
                String[] f = currentLine.split(" ");
                if (f.length != 3) throw new InvalidInputException(currentLineNo, "wrong number of fields");
                String f1 = f[1];
                String f2 = f[2];
                int i = f1.lastIndexOf('/');
                if (i<0) throw new InvalidInputException(currentLineNo, "invalid syntax");
                String obfClassName = f1.substring(0, i);
                String obfFieldName = f1.substring(i + 1);
                i = f2.lastIndexOf('/');
                if (i<0) throw new InvalidInputException(currentLineNo, "invalid syntax");
                String deobfClassName = f2.substring(0, i);
                String deobfFieldName = f2.substring(i+1);
                Mapping.ClassMapping cm = classMappings.get(obfClassName);
                if (cm == null) {
                    throw new InvalidInputException(currentLineNo, "class "+obfClassName+" is not declared");
                }
                if (!cm.originalName.equals(deobfClassName)) {
                    throw new InvalidInputException(currentLineNo, String.format("deobfuscated class names do not match (%s,%s)", cm.originalName, deobfClassName));
                }
                cm.fieldMappings.add(new Mapping.FieldMapping(deobfFieldName, obfFieldName, ""));
            } else if (currentLine.startsWith("MD: ")) {
                String[] f = currentLine.split(" ");
                if (f.length != 5) {
                    throw new InvalidInputException(currentLineNo, "wrong number of fields");
                }
                String f1 = f[1];
                String f3 = f[3];
                String deobfDesc = f[4];
                int i = f1.lastIndexOf('/');
                String obfClassName = f1.substring(0, i);
                String obfMethodName = f1.substring(i+1);
                i = f3.lastIndexOf('/');
                String deobfClassName = f3.substring(0, i);
                String deobfMethodName = f3.substring(i+1);
                Mapping.ClassMapping cm = classMappings.get(obfClassName);
                if (cm == null) {
                    throw new InvalidInputException(currentLineNo, "class "+obfClassName+" is not declared");
                }
                if (!cm.originalName.equals(deobfClassName)) {
                    throw new InvalidInputException(currentLineNo, String.format("deobfuscated class names do not match (%s,%s)", cm.originalName, deobfClassName));
                }
                cm.methodMappings.add(new Mapping.MethodMapping(deobfMethodName, obfMethodName, deobfDesc));
            } else {
                System.err.println("skipping invalid line: "+currentLine);
            }
            readLine();
        }
        return new ArrayList<>(classMappings.values());
    }
}
