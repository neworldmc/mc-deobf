import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Mapping {
    public static class FieldMapping {
        public String originalName;
        public String obfuscatedName;
        public String type; // not used now
        public FieldMapping(String originalName, String obfuscatedName, String type) {
            this.originalName = originalName;
            this.obfuscatedName = obfuscatedName;
            this.type = type;
        }
    }
    public static class MethodMapping {
        public String originalName;
        public String obfuscatedName;
        public String type; // descriptor
        public MethodMapping(String originalName, String obfuscatedName, String returnType, String[] argTypes) {
            this.originalName = originalName;
            this.obfuscatedName = obfuscatedName;
            this.type = Util.encodeMethodType(returnType, argTypes);
        }
        public MethodMapping(String originalName, String obfuscatedName, String type) {
            this.originalName = originalName;
            this.obfuscatedName = obfuscatedName;
            this.type = type;
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
}
