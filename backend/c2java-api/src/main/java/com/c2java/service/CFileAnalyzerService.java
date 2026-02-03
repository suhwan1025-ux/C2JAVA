package com.c2java.service;

import com.c2java.dto.CFileStructure;
import com.c2java.dto.CFileStructure.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * C 파일 분석 서비스
 * C 소스 파일의 구조를 파악하여 변환에 필요한 정보를 추출
 */
@Service
@Slf4j
public class CFileAnalyzerService {

    // 정규식 패턴
    private static final Pattern FUNCTION_PATTERN = Pattern.compile(
        "(\\w+)\\s+(\\w+)\\s*\\(([^)]*)\\)\\s*\\{"
    );
    
    private static final Pattern STRUCT_PATTERN = Pattern.compile(
        "struct\\s+(\\w+)\\s*\\{([^}]*?)\\}\\s*;?",
        Pattern.DOTALL
    );
    
    private static final Pattern ENUM_PATTERN = Pattern.compile(
        "enum\\s+(\\w+)?\\s*\\{([^}]+)\\}",
        Pattern.DOTALL
    );
    
    private static final Pattern DEFINE_PATTERN = Pattern.compile(
        "#define\\s+(\\w+)(\\([^)]*\\))?\\s+(.+)"
    );
    
    private static final Pattern INCLUDE_PATTERN = Pattern.compile(
        "#include\\s+[<\"]([^>\"]+)[>\"]"
    );
    
    private static final Pattern SQL_PATTERN = Pattern.compile(
        "EXEC\\s+SQL\\s+(SELECT|INSERT|UPDATE|DELETE|MERGE|CREATE|ALTER|DROP)\\s+([^;]+);",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    private static final Pattern BIND_VAR_PATTERN = Pattern.compile(
        ":(\\w+)"
    );

    /**
     * C 파일 분석
     */
    public CFileStructure analyzeFile(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        String fileName = filePath.getFileName().toString();
        
        log.info("Analyzing file: {}", fileName);
        
        return CFileStructure.builder()
                .fileName(fileName)
                .fileType(determineFileType(fileName))
                .lineCount(countLines(content))
                .functions(extractFunctions(content))
                .structs(extractStructs(content))
                .enums(extractEnums(content))
                .sqlQueries(extractSqlQueries(content))
                .includes(extractIncludes(content))
                .defines(extractDefines(content))
                .globalVariables(extractGlobalVariables(content))
                .build();
    }

    /**
     * 파일 타입 결정
     */
    private String determineFileType(String fileName) {
        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".pc")) return "pro_c";
        if (lowerName.endsWith(".h") || lowerName.endsWith(".hpp")) return "c_header";
        if (lowerName.endsWith(".c")) return "c_source";
        if (lowerName.endsWith(".cpp") || lowerName.endsWith(".cc") || lowerName.endsWith(".cxx")) return "cpp_source";
        return "unknown";
    }

    /**
     * 라인 수 계산
     */
    private int countLines(String content) {
        return (int) content.lines().count();
    }

    /**
     * 함수 추출
     */
    private List<FunctionInfo> extractFunctions(String content) {
        List<FunctionInfo> functions = new ArrayList<>();
        String[] lines = content.split("\n");
        Matcher matcher = FUNCTION_PATTERN.matcher(content);
        
        while (matcher.find()) {
            String returnType = matcher.group(1);
            String funcName = matcher.group(2);
            String params = matcher.group(3);
            
            // main, static 함수는 제외 가능
            if (funcName.equals("main")) continue;
            
            // 라인 번호 찾기
            int lineNumber = findLineNumber(content, matcher.start());
            
            // 주석 찾기
            String comment = extractPrecedingComment(lines, lineNumber);
            
            functions.add(FunctionInfo.builder()
                    .name(funcName)
                    .returnType(returnType)
                    .lineNumber(lineNumber)
                    .parameters(parseParameters(params))
                    .signature(matcher.group(0))
                    .comment(comment)
                    .isStatic(content.substring(Math.max(0, matcher.start() - 20), matcher.start()).contains("static"))
                    .build());
        }
        
        log.info("Extracted {} functions", functions.size());
        return functions;
    }

    /**
     * 구조체 추출
     */
    private List<StructInfo> extractStructs(String content) {
        List<StructInfo> structs = new ArrayList<>();
        String[] lines = content.split("\n");
        Matcher matcher = STRUCT_PATTERN.matcher(content);
        
        while (matcher.find()) {
            String structName = matcher.group(1);
            String body = matcher.group(2);
            int lineNumber = findLineNumber(content, matcher.start());
            String comment = extractPrecedingComment(lines, lineNumber);
            
            List<StructInfo.Field> fields = parseStructFields(body);
            
            structs.add(StructInfo.builder()
                    .name(structName)
                    .lineNumber(lineNumber)
                    .fields(fields)
                    .comment(comment)
                    .build());
        }
        
        log.info("Extracted {} structs", structs.size());
        return structs;
    }

    /**
     * 열거형 추출
     */
    private List<EnumInfo> extractEnums(String content) {
        List<EnumInfo> enums = new ArrayList<>();
        Matcher matcher = ENUM_PATTERN.matcher(content);
        
        while (matcher.find()) {
            String enumName = matcher.group(1);
            String body = matcher.group(2);
            int lineNumber = findLineNumber(content, matcher.start());
            
            List<String> values = Arrays.stream(body.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            
            enums.add(EnumInfo.builder()
                    .name(enumName != null ? enumName : "AnonymousEnum")
                    .lineNumber(lineNumber)
                    .values(values)
                    .build());
        }
        
        log.info("Extracted {} enums", enums.size());
        return enums;
    }

    /**
     * SQL 쿼리 추출 (Pro*C)
     */
    private List<SqlQuery> extractSqlQueries(String content) {
        List<SqlQuery> queries = new ArrayList<>();
        Matcher matcher = SQL_PATTERN.matcher(content);
        
        while (matcher.find()) {
            String sqlType = matcher.group(1).toUpperCase();
            String sqlBody = matcher.group(2).trim();
            int lineNumber = findLineNumber(content, matcher.start());
            
            // 바인드 변수 추출
            List<String> bindVars = new ArrayList<>();
            Matcher bindMatcher = BIND_VAR_PATTERN.matcher(sqlBody);
            while (bindMatcher.find()) {
                bindVars.add(bindMatcher.group(1));
            }
            
            // 동적 SQL 여부 (sprintf 등)
            boolean isDynamic = content.substring(
                Math.max(0, matcher.start() - 100), matcher.start()
            ).contains("sprintf") || sqlBody.contains("%s") || sqlBody.contains("%d");
            
            queries.add(SqlQuery.builder()
                    .lineNumber(lineNumber)
                    .type(sqlType)
                    .query(sqlBody)
                    .bindVariables(bindVars)
                    .isDynamic(isDynamic)
                    .build());
        }
        
        log.info("Extracted {} SQL queries", queries.size());
        return queries;
    }

    /**
     * #include 추출
     */
    private List<String> extractIncludes(String content) {
        List<String> includes = new ArrayList<>();
        Matcher matcher = INCLUDE_PATTERN.matcher(content);
        
        while (matcher.find()) {
            includes.add(matcher.group(1));
        }
        
        return includes;
    }

    /**
     * #define 추출
     */
    private List<DefineInfo> extractDefines(String content) {
        List<DefineInfo> defines = new ArrayList<>();
        Matcher matcher = DEFINE_PATTERN.matcher(content);
        
        while (matcher.find()) {
            String name = matcher.group(1);
            String funcParams = matcher.group(2);
            String value = matcher.group(3).trim();
            int lineNumber = findLineNumber(content, matcher.start());
            
            defines.add(DefineInfo.builder()
                    .name(name)
                    .value(value)
                    .lineNumber(lineNumber)
                    .isFunctionLike(funcParams != null)
                    .build());
        }
        
        log.info("Extracted {} defines", defines.size());
        return defines;
    }

    /**
     * 전역 변수 추출 (간단 버전)
     */
    private List<VariableInfo> extractGlobalVariables(String content) {
        // 함수 밖의 변수 선언 찾기 (간단 구현)
        return new ArrayList<>();
    }

    /**
     * 파라미터 파싱
     */
    private List<FunctionInfo.Parameter> parseParameters(String params) {
        if (params == null || params.trim().isEmpty() || params.trim().equals("void")) {
            return Collections.emptyList();
        }
        
        return Arrays.stream(params.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(param -> {
                    String[] parts = param.trim().split("\\s+");
                    if (parts.length >= 2) {
                        String type = String.join(" ", Arrays.copyOf(parts, parts.length - 1));
                        String name = parts[parts.length - 1].replaceAll("[*\\[\\]]", "");
                        boolean isPointer = param.contains("*");
                        
                        return FunctionInfo.Parameter.builder()
                                .type(type)
                                .name(name)
                                .isPointer(isPointer)
                                .build();
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 구조체 필드 파싱
     */
    private List<StructInfo.Field> parseStructFields(String body) {
        return Arrays.stream(body.split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(field -> {
                    String[] parts = field.trim().split("\\s+");
                    if (parts.length >= 2) {
                        String type = String.join(" ", Arrays.copyOf(parts, parts.length - 1));
                        String name = parts[parts.length - 1];
                        
                        // 배열 크기 추출
                        String arraySize = null;
                        if (name.contains("[")) {
                            arraySize = name.substring(name.indexOf("[") + 1, name.indexOf("]"));
                            name = name.substring(0, name.indexOf("["));
                        }
                        
                        boolean isPointer = type.contains("*") || name.contains("*");
                        name = name.replaceAll("[*]", "");
                        
                        return StructInfo.Field.builder()
                                .type(type.replaceAll("[*]", "").trim())
                                .name(name)
                                .isPointer(isPointer)
                                .arraySize(arraySize)
                                .build();
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 라인 번호 찾기
     */
    private int findLineNumber(String content, int position) {
        return (int) content.substring(0, position).chars().filter(ch -> ch == '\n').count() + 1;
    }

    /**
     * 앞의 주석 추출
     */
    private String extractPrecedingComment(String[] lines, int lineNumber) {
        if (lineNumber <= 1) return null;
        
        StringBuilder comment = new StringBuilder();
        for (int i = lineNumber - 2; i >= 0; i--) {
            String line = lines[i].trim();
            if (line.startsWith("//") || line.startsWith("/*") || line.startsWith("*")) {
                comment.insert(0, line + "\n");
            } else if (!line.isEmpty()) {
                break;
            }
        }
        
        return comment.length() > 0 ? comment.toString().trim() : null;
    }
}
