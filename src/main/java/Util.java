import java.util.HashMap;
import java.util.Map;

public class Util {
    public static String encodeType(String s) {
        // handle array types
        if (s.endsWith("[]")) {
            return "[" + encodeType(s.substring(0, s.length()-2));
        }
        // handle base types
        switch (s) {
            case "byte": return "B";
            case "char": return "C";
            case "double": return "D";
            case "float": return "F";
            case "int": return "I";
            case "short": return "S";
            case "long": return "J";
            case "boolean": return "Z";
            case "void": return "V";
        }
        return "L" + s.replace('.', '/') + ";";
    }

    public static String encodeMethodType(String retType, String[] argTypes) {
        StringBuilder sb = new StringBuilder("(");
        for (String argType : argTypes) {
            sb.append(encodeType(argType));
        }
        sb.append(')');
        sb.append(encodeType(retType));
        return sb.toString();
    }

    public static boolean isBuiltinType(String s) {
        switch (s) {
            case "byte":
            case "char":
            case "double":
            case "float":
            case "int":
            case "short":
            case "long":
            case "boolean":
            case "void":
                return true;
            default:
                return false;
        }
    }

//    public static String mapSignature(String sig, Map<String, String> map) {
//        int i = sig.indexOf('L');
//        if (i < 0) return sig;
//        StringBuilder sb = new StringBuilder(sig.substring(0, i+1));
//        while (true) {
//            // i points to 'L'
//            int j = sig.indexOf(';', i + 1 /* right after 'L' */);
//            String className = sig.substring(i + 1, j);
//            String mappedClassName;
//            if (map.containsKey(className)) {
//                mappedClassName = map.get(className).replace('.', '/');
//            } else {
//                mappedClassName = className;
//            }
//            sb.append(mappedClassName);
//            i = sig.indexOf('L', j + 1/* right after ';' */);
//            if (i < 0) {
//                sb.append(sig.substring(j));
//                return sb.toString();
//            }
//            sb.append(sig.substring(j, i+1));
//        }
//    }

    public static String mapSignature(String sig, Map<String, String> map) {
        int i = sig.indexOf('L');
        if (i < 0) return sig;
        StringBuilder sb = new StringBuilder(sig.substring(0, i+1));
        while (true) {
            // i points to 'L'
            int j = i+1;
            while (true) {
                char c = sig.charAt(j);
                if (c == ';' || c == '<' || c == '.') break;
                j++;
            }
            String className = sig.substring(i + 1, j);
            String mappedClassName;
            if (map.containsKey(className)) {
                mappedClassName = map.get(className);
            } else {
                mappedClassName = className;
            }
            sb.append(mappedClassName);
            i = sig.indexOf('L', j + 1/* right after delimiter (one of ';' '<' '.') */);
            if (i < 0) {
                sb.append(sig.substring(j));
                return sb.toString();
            }
            sb.append(sig.substring(j, i+1));
        }
    }

    /*public static void main(String[] args) {
        Map<String, String> map = new HashMap<>();
        map.put("a", "com.example.A");
        map.put("b", "com.example.B");
        System.out.println(mapSignature("(ILa;[Lc<La;>;)Lb<LL;>;", map));
    }*/
}
