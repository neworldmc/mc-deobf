import org.apache.bcel.Const;
import org.apache.bcel.classfile.*;
import org.apache.commons.cli.*;
import org.apache.commons.lang3.tuple.ImmutablePair;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Remapper {
    private final Map<String, ClassMapping> obfuscatedMappings;
    private final Map<String, String> classNameMap; // obf. -> original
    private final Map<String, String> inverseClassNameMap;
    private final ClassMapping classMapping;
    private final Map<String, String[]> hierarchy;
    private final JavaClass jclass;
    private final String obfuscatedClassName; // *** path components separated with '/' ***

    Remapper(Map<String, ClassMapping> obfuscatedMappings, Map<String, String> classNameMap, Map<String,String> inverseClassNameMap, Map<String, String[]> hierarchy, JavaClass jclass) {
        this.obfuscatedMappings = obfuscatedMappings;
        this.classNameMap = classNameMap;
        this.inverseClassNameMap = inverseClassNameMap;
        this.jclass = jclass;
        Constant[] constants = jclass.getConstantPool().getConstantPool();
        obfuscatedClassName = ((ConstantUtf8)constants[((ConstantClass)constants[jclass.getClassNameIndex()]).getNameIndex()]).getBytes();
        classMapping = obfuscatedMappings.getOrDefault(obfuscatedClassName, null);
        this.hierarchy = hierarchy;
        if (classMapping != null) {
            Set<String> originalFields = new HashSet<>();
            classMapping.fieldMap.forEach((badName, goodName) -> {
               originalFields.add(goodName);
            });
            Set<ImmutablePair<String, String>> originalMethods = new HashSet<>();
            classMapping.methodMap.forEach((badNameAndType, goodName) -> {
                originalMethods.add(new ImmutablePair<>(goodName, badNameAndType.right));
            });
            int i = 0;
            for (Field field : jclass.getFields()) {
                String fieldName = field.getName();
                if (!classMapping.fieldMap.containsKey(fieldName) && originalFields.contains(fieldName)) {
                    String newFieldName = fieldName + "_" + i;
                    System.out.printf("in class %s: field %s -> %s\n", classMapping.originalName, fieldName, newFieldName);
                    classMapping.fieldMap.put(fieldName, newFieldName);
                }
            }
            i = 0;
            for (Method method : jclass.getMethods()) {
                String methodName = method.getName();
                String methodDesc = Util.mapSignature(method.getSignature(), classNameMap);
                ImmutablePair<String, String> key = new ImmutablePair<>(methodName, methodDesc);
                if (!classMapping.methodMap.containsKey(key) /* method name not in obfuscation mapping */) {
                    if (originalMethods.contains(key)) {
                        String newMethodName = methodName + "_" + i;
                        System.out.printf("in class %s: method %s -> %s\n", classMapping.originalName, methodName, newMethodName);
                        classMapping.methodMap.put(key, newMethodName);
                    }
                }
                i++;
            }
        }
    }

    private String getString(Constant[] cp, int index) {
        return ((ConstantUtf8) cp[index]).getBytes();
    }

    private static int getStringIndex(Map<String, Integer> map, String s, List<Constant> constants) {
        if (map.containsKey(s)) return map.get(s);
        int index = constants.size();
        constants.add(new ConstantUtf8(s));
        map.put(s, index);
        return index;
    }

    private String mapFieldName(String obfuscatedClassName, ClassMapping cm, String name) {
        if (cm != null && cm.fieldMap.containsKey(name)) return cm.fieldMap.get(name);
        if (!hierarchy.containsKey(obfuscatedClassName)) return null;
        for (String parentName : hierarchy.get(obfuscatedClassName)) {
            String tmp = mapFieldName(parentName, obfuscatedMappings.getOrDefault(parentName, null), name);
            if (tmp != null) return tmp;
        }
        return null;
    }

    private String mapMethodName(String obfuscatedClassName, ClassMapping cm, String name, String desc) {
        ImmutablePair<String, String> nameAndType = new ImmutablePair<>(name, desc);
        if (cm != null && cm.methodMap.containsKey(nameAndType)) return cm.methodMap.get(nameAndType);
        if (!hierarchy.containsKey(obfuscatedClassName)) return null;
        for (String parentName : hierarchy.get(obfuscatedClassName)) {
            String tmp = mapMethodName(parentName, obfuscatedMappings.getOrDefault(parentName, null), name, desc);
            if (tmp != null) return tmp;
        }
        return null;
    }

    private String mapFieldName(String name) {
        String tmp = mapFieldName(this.obfuscatedClassName, this.classMapping, name);
        return tmp == null ? name : tmp;
    }

    private String mapMethodName(String name, String desc) {
        String tmp = mapMethodName(this.obfuscatedClassName, this.classMapping, name, desc);
        return tmp == null ? name : tmp;
    }

    void remap() {
        ConstantPool constantPool = jclass.getConstantPool();
        Constant[] originalConstants = constantPool.getConstantPool();
        ArrayList<Constant> constants = new ArrayList<>(originalConstants.length);
        Collections.addAll(constants, originalConstants);
        Map<String, Integer> stringIndexMap = new HashMap<>();
        Map<ImmutablePair<String, String>, Integer> nameAndTypeIndexMap = new HashMap<>();
        // initialize stringIndexMap and nameAndTypeIndexMap
        for (int i = 1; i < originalConstants.length; i++) {
            Constant c = originalConstants[i];
            if (c instanceof ConstantUtf8) {
                String s = ((ConstantUtf8)c).getBytes();
                stringIndexMap.put(s, i);
            } else if (c instanceof ConstantNameAndType) {
                ConstantNameAndType cnt = (ConstantNameAndType) c;
                String name = cnt.getName(constantPool);
                String desc = cnt.getSignature(constantPool);
                nameAndTypeIndexMap.put(new ImmutablePair<>(name, desc), i);
            }
        }
        // fix ConstantClass's and ConstantNameAndType's
        for (Constant c : originalConstants) {
            if (c instanceof ConstantClass) {
                ConstantClass cc = (ConstantClass) c;
                String className = getString(originalConstants, cc.getNameIndex());
                String originalClassName;
                if (className.startsWith("[")) {
                    originalClassName = Util.mapSignature(className, classNameMap);
                } else {
                    originalClassName = mapClassName(className);
                }
                if (!originalClassName.equals(className)) {
                    cc.setNameIndex(getStringIndex(stringIndexMap, originalClassName, constants));
                }
            } else if (c instanceof ConstantNameAndType) {
                ConstantNameAndType cnt = (ConstantNameAndType) c;
                String desc = cnt.getSignature(constantPool);
                String mappedDesc = Util.mapSignature(desc, classNameMap);
                cnt.setSignatureIndex(getStringIndex(stringIndexMap, mappedDesc, constants));
            }
        }
        for (Constant c : originalConstants) {
            if (c instanceof ConstantFieldref || c instanceof ConstantMethodref || c instanceof ConstantInterfaceMethodref) {
                ConstantCP ccp = (ConstantCP) c;
                ConstantClass v1 = (ConstantClass)constants.get(ccp.getClassIndex());
                ConstantUtf8 v2 = (ConstantUtf8)constants.get(v1.getNameIndex());
                String className = v2.getBytes();
                ConstantNameAndType cnt = (ConstantNameAndType) originalConstants[ccp.getNameAndTypeIndex()];
                String memberName = cnt.getName(constantPool);
                String mappedMemberName;
                String desc = ((ConstantUtf8)constants.get(cnt.getSignatureIndex())).getBytes();
                String mappedDesc = Util.mapSignature(desc, classNameMap);
                String obfClassName = inverseClassNameMap.getOrDefault(className, className);
                if (c instanceof ConstantFieldref) {
                    mappedMemberName = mapFieldName(obfClassName, obfuscatedMappings.getOrDefault(obfClassName, null), memberName);
                } else {
                    mappedMemberName = mapMethodName(obfClassName, obfuscatedMappings.getOrDefault(obfClassName, null), memberName, mappedDesc);
                }
                if (mappedMemberName == null) mappedMemberName = memberName;
                if (!mappedMemberName.equals(memberName) || !mappedDesc.equals(desc)) {
                    ImmutablePair<String, String> mappedNameAndType = new ImmutablePair<>(mappedMemberName, mappedDesc);
                    int cnt1_index;
                    if (nameAndTypeIndexMap.containsKey(mappedNameAndType)) {
                        cnt1_index = nameAndTypeIndexMap.get(mappedNameAndType);
                    } else {
                        int nameIndex = getStringIndex(stringIndexMap, mappedMemberName, constants);
                        int typeIndex = getStringIndex(stringIndexMap, mappedDesc, constants);
                        cnt1_index = constants.size();
                        constants.add(new ConstantNameAndType(nameIndex, typeIndex));
                        nameAndTypeIndexMap.put(mappedNameAndType, cnt1_index);
                    }
                    ccp.setNameAndTypeIndex(cnt1_index);
                    //System.out.printf("%s %s -> %s %s\n", desc, memberName, mappedDesc, mappedMemberName);
                }
            } else if (c instanceof ConstantMethodType) {
                ConstantMethodType cmt = (ConstantMethodType) c;
                String desc = getString(originalConstants, cmt.getDescriptorIndex());
                String mappedDesc = Util.mapSignature(desc, classNameMap);
                cmt.setDescriptorIndex(getStringIndex(stringIndexMap, mappedDesc, constants));
            }
        }

        // fix class signature
        for (Attribute attr : jclass.getAttributes()) {
            if (attr instanceof Signature) {
                Signature sigAttr = (Signature) attr;
                String sig = sigAttr.getSignature();
                String mappedSig = Util.mapSignature(sig, classNameMap);
                sigAttr.setSignatureIndex(getStringIndex(stringIndexMap, mappedSig, constants));
            } else if (attr instanceof InnerClasses) {
                InnerClasses innerClasses = (InnerClasses) attr;
                for (InnerClass innerClass : innerClasses.getInnerClasses()) {
                    if (innerClass.getOuterClassIndex() > 0 && innerClass.getInnerClassIndex() > 0) {
                        String outerClassName = ((ConstantUtf8) constants.get(((ConstantClass) constants.get(innerClass.getOuterClassIndex())).getNameIndex())).getBytes();
                        String innerClassName = ((ConstantUtf8) constants.get(((ConstantClass) constants.get(innerClass.getInnerClassIndex())).getNameIndex())).getBytes();
                        if (innerClassName.startsWith(outerClassName + "$")) {
                            String simpleInnerClassName = innerClassName.substring(outerClassName.length()+1);
                            innerClass.setInnerNameIndex(getStringIndex(stringIndexMap, simpleInnerClassName, constants));
                        } else {
                            System.err.printf("outer class name: %s, inner class name: %s\n", outerClassName, innerClassName);
                        }
                    }
                }
            }
        }

        Field[] fields = jclass.getFields();
        for (Field field : fields) {
            String fieldName = field.getName();
            String desc = field.getType().getSignature();
            String mappedFieldName = mapFieldName(fieldName);
            String mappedDesc = Util.mapSignature(desc, classNameMap);
            if (!mappedFieldName.equals(fieldName)) {
                field.setNameIndex(getStringIndex(stringIndexMap, mappedFieldName, constants));
            }
            if (!mappedDesc.equals(desc)) {
                field.setSignatureIndex(getStringIndex(stringIndexMap, mappedDesc, constants));
            }
            Attribute[] attributes = field.getAttributes();
            for (Attribute attr : attributes) {
                if (attr instanceof Signature) {
                    Signature sigAttr = (Signature) attr;
                    String mappedSig = Util.mapSignature(sigAttr.getSignature(), classNameMap);
                    sigAttr.setSignatureIndex(getStringIndex(stringIndexMap, mappedSig, constants));
                }
            }
        }

        Method[] methods = jclass.getMethods();
        int method_id = 0;
        for (Method method : methods) {
            String methodName = method.getName();
            String desc = method.getSignature();
            String mappedDesc = Util.mapSignature(desc, classNameMap);
            String mappedMethodName = mapMethodName(methodName, mappedDesc);
            if (!mappedMethodName.equals(methodName)) {
                method.setNameIndex(getStringIndex(stringIndexMap, mappedMethodName, constants));
            }
            if (!mappedDesc.equals(desc)) {
                method.setSignatureIndex(getStringIndex(stringIndexMap, mappedDesc, constants));
            }
            Attribute[] attributes = method.getAttributes();
            for (Attribute attr : attributes) {
                if (attr instanceof Code) {
                    Code code = (Code)attr;
                    ArrayList<Attribute> modifiedCodeAttributes = new ArrayList<>();
                    for (Attribute codeAttr : code.getAttributes()) {
                        if (!(codeAttr instanceof LocalVariableTypeTable)) {
                            modifiedCodeAttributes.add(codeAttr);
                        }
                        if (codeAttr instanceof LocalVariableTable) {
                            LocalVariableTable lvt = (LocalVariableTable) codeAttr;
                            int lvid = 0;
                            for (LocalVariable lv : lvt.getLocalVariableTable()) {
                                String lvName = lv.getName();
                                if (lvName.contains("\u2603")) /* the infamous snowman character */ {
                                    String newName = String.format("local%d_%d", method_id, lvid);
                                    lv.setNameIndex(getStringIndex(stringIndexMap, newName, constants));
                                }
                                String mappedDescLV = Util.mapSignature(lv.getSignature(), classNameMap);
                                lv.setSignatureIndex(getStringIndex(stringIndexMap, mappedDescLV, constants));
                                lvid++;
                            }
                        }
                    }
                    code.setAttributes(modifiedCodeAttributes.toArray(new Attribute[0]));
                } else if (attr instanceof Signature) {
                    Signature sigAttr = (Signature) attr;
                    String mappedSig = Util.mapSignature(sigAttr.getSignature(), classNameMap);
                    sigAttr.setSignatureIndex(getStringIndex(stringIndexMap, mappedSig, constants));
                }
            }
            method_id++;
        }

        constantPool.setConstantPool(constants.toArray(new Constant[0]));
    }

    void dump(String filePath) throws IOException {
        jclass.dump(filePath);
    }

    private String mapClassName(String className) {
        if (!obfuscatedMappings.containsKey(className)) return className;
        return obfuscatedMappings.get(className).originalName;
    }

    static class ClassMapping {
        final String obfuscatedName;
        final String originalName;
        final Map<String, String> fieldMap = new HashMap<>();
        final Map<ImmutablePair<String, String>, String> methodMap = new HashMap<>();

        ClassMapping(Mapping.ClassMapping cm) {
            obfuscatedName = cm.obfuscatedName.replace('.', '/');
            originalName = cm.originalName.replace('.', '/');
            for (Mapping.FieldMapping fm : cm.fieldMappings) {
                fieldMap.put(fm.obfuscatedName, fm.originalName);
            }
            for (Mapping.MethodMapping mm : cm.methodMappings) {
                methodMap.put(new ImmutablePair<>(mm.obfuscatedName, mm.type), mm.originalName);
            }
        }
    }
}
