import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProguardMappingParser implements MappingParser {
    BufferedReader reader;
    String currentLine;
    int currentLineNo;

    public ProguardMappingParser(String filePath) throws IOException {
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

    private Mapping.FieldMapping tryParseFieldMapping() throws IOException {
        if (currentLine == null) return null;
        Matcher m = FIELD_MAPPING_PATTERN.matcher(currentLine);
        if (!m.matches()) return null;
        readLine();
        String type = m.group(1);
        String originalName = m.group(2);
        String obfuscatedName = m.group(3);
        return new Mapping.FieldMapping(originalName, obfuscatedName, type);
    }

    private Mapping.MethodMapping tryParseMethodMapping() throws IOException {
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
        return new Mapping.MethodMapping(originalName, obfuscatedName, returnType, argTypes);
    }

    private Mapping.ClassMapping parseClassMapping() throws IOException, InvalidInputException {
        Matcher m = CLASS_MAPPING_PATTERN.matcher(currentLine);
        if (!m.matches()) {
            throw new InvalidInputException(currentLineNo, "invalid syntax");
        }
        readLine();
        String originalName = m.group(1);
        String obfuscatedName = m.group(2);
        List<Mapping.FieldMapping> fieldMappings = new ArrayList<>();
        List<Mapping.MethodMapping> methodMappings = new ArrayList<>();
        Mapping.FieldMapping fieldMapping;
        while ((fieldMapping = tryParseFieldMapping()) != null) {
            fieldMappings.add(fieldMapping);
        }
        Mapping.MethodMapping methodMapping;
        while ((methodMapping = tryParseMethodMapping()) != null) {
            methodMappings.add(methodMapping);
        }
        return new Mapping.ClassMapping(originalName, obfuscatedName, fieldMappings, methodMappings);
    }

    public List<Mapping.ClassMapping> parse() throws IOException, InvalidInputException {
        List<Mapping.ClassMapping> result = new ArrayList<>();
        while (currentLine != null) {
            Mapping.ClassMapping mapping = parseClassMapping();
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
            ProguardMappingParser p = new ProguardMappingParser(args[0]);
            List<Mapping.ClassMapping> classMappings = p.parse();
            System.out.println(classMappings.size());
        } catch (InvalidInputException | IOException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }
}
