import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Mapping {
    public static class FieldMapping {
        public String originalName;
        public String obfuscatedName;
        public String type;
        public FieldMapping(String originalName, String obfuscatedName, String type) {
            this.originalName = originalName;
            this.obfuscatedName = obfuscatedName;
            this.type = type;
        }
    }
    public static class MethodMapping {
        public String originalName;
        public String obfuscatedName;
        public String returnType;
        public String[] argTypes;
        public MethodMapping(String originalName, String obfuscatedName, String returnType, String[] argTypes) {
            this.originalName = originalName;
            this.obfuscatedName = obfuscatedName;
            this.returnType = returnType;
            this.argTypes = argTypes;
        }
    }
    public static class ClassMapping {
        public String originalName;
        public String obfuscatedName;
        public List<FieldMapping> fieldMappings;
        public List<MethodMapping> methodMappings;
        public ClassMapping(String originalName, String obfuscatedName, List<FieldMapping> fieldMappings, List<MethodMapping> methodMappings) {
            this.originalName = originalName;
            this.obfuscatedName = obfuscatedName;
            this.fieldMappings = fieldMappings;
            this.methodMappings = methodMappings;
        }
    }

    public static class Parser {
        BufferedReader reader;
        String currentLine;
        int currentLineNo;

        public static class SyntaxErrorException extends Exception {
            public int line;
            public String text;

            @Override
            public String getMessage() {
                return String.format("Syntax error at line %d: %s", line, text);
            }

            public SyntaxErrorException(int line, String text) {
                this.line = line;
                this.text = text;
            }
        }

        public Parser(String filePath) throws IOException {
            reader = new BufferedReader(new FileReader(filePath));
            readLine();
        }

        private static final String IDENT = "[0-9A-Za-z_$.<>-]+";
        private static final String TYPE = "[0-9A-Za-z_$.]+(?:\\[\\])*";
        private static final Pattern CLASS_MAPPING_PATTERN = Pattern.compile("(" + IDENT + ") -> (" + IDENT + "):");
        private static final Pattern FIELD_MAPPING_PATTERN = Pattern.compile("    (" + TYPE + ") (" + IDENT + ") -> (" + IDENT + ")");
        private static final Pattern METHOD_MAPPING_PATTERN = Pattern.compile("    (?:\\d+:\\d+:)?("+/*return*/TYPE+") ("+IDENT+")\\((|"+TYPE+"(?:,"+TYPE+")*)\\) -> ("+IDENT+")");

        // ignores empty lines and lines starting with '#'
        private void readLine() throws IOException {
            do {
                currentLine = reader.readLine();
                currentLineNo++;
            } while (currentLine != null && (currentLine.length() == 0 || currentLine.charAt(0) == '#'));
        }

        private FieldMapping tryParseFieldMapping() throws IOException {
            if (currentLine == null) return null;
            Matcher m = FIELD_MAPPING_PATTERN.matcher(currentLine);
            if (!m.matches()) return null;
            readLine();
            String type = m.group(1);
            String originalName = m.group(2);
            String obfuscatedName = m.group(3);
            return new FieldMapping(originalName, obfuscatedName, type);
        }

        private MethodMapping tryParseMethodMapping() throws IOException {
            if (currentLine == null) return null;
            Matcher m = METHOD_MAPPING_PATTERN.matcher(currentLine);
            if (!m.matches()) return null;
            readLine();
            String returnType = m.group(1);
            String originalName = m.group(2);
            String[] argTypes;
            String group3 = m.group(3);
            if (group3.isEmpty()) {
                argTypes = new String[0];
            } else {
                argTypes = group3.split(",");
            }
            String obfuscatedName = m.group(4);
            return new MethodMapping(originalName, obfuscatedName, returnType, argTypes);
        }

        private ClassMapping parseClassMapping() throws IOException, SyntaxErrorException {
            Matcher m = CLASS_MAPPING_PATTERN.matcher(currentLine);
            if (!m.matches()) {
                throw new SyntaxErrorException(currentLineNo, currentLine);
            }
            readLine();
            String originalName = m.group(1);
            String obfuscatedName = m.group(2);
            List<FieldMapping> fieldMappings = new ArrayList<>();
            List<MethodMapping> methodMappings = new ArrayList<>();
            FieldMapping fieldMapping;
            while ((fieldMapping = tryParseFieldMapping()) != null) {
                fieldMappings.add(fieldMapping);
            }
            MethodMapping methodMapping;
            while ((methodMapping = tryParseMethodMapping()) != null) {
                methodMappings.add(methodMapping);
            }
            return new ClassMapping(originalName, obfuscatedName, fieldMappings, methodMappings);
        }

        public List<ClassMapping> parse() throws IOException, SyntaxErrorException {
            List<ClassMapping> result = new ArrayList<>();
            while (currentLine != null) {
                ClassMapping mapping = parseClassMapping();
                result.add(mapping);
            }
            return result;
        }

        public static void main(String[] args) {
            if (args.length < 1) {
                System.err.println("No file specified");
                System.exit(1);
            }
            try {
                Parser p = new Parser(args[0]);
                List<ClassMapping> classMappings = p.parse();
                System.out.println(classMappings.size());
            } catch (SyntaxErrorException | IOException e) {
                System.err.println(e.getMessage());
                System.exit(1);
            }
        }
    }
}
