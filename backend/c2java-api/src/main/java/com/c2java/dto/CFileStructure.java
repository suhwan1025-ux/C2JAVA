package com.c2java.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * C 파일 구조 분석 결과 DTO
 */
@Data
@Builder
public class CFileStructure {
    private String fileName;
    private String fileType; // c_source, c_header, pro_c
    private int lineCount;
    private List<FunctionInfo> functions;
    private List<StructInfo> structs;
    private List<EnumInfo> enums;
    private List<SqlQuery> sqlQueries;
    private List<String> includes;
    private List<DefineInfo> defines;
    private List<VariableInfo> globalVariables;
    
    @Data
    @Builder
    public static class FunctionInfo {
        private String name;
        private int lineNumber;
        private String returnType;
        private List<Parameter> parameters;
        private String signature;
        private String comment;
        private boolean isStatic;
        
        @Data
        @Builder
        public static class Parameter {
            private String type;
            private String name;
            private boolean isPointer;
        }
    }
    
    @Data
    @Builder
    public static class StructInfo {
        private String name;
        private int lineNumber;
        private List<Field> fields;
        private String comment;
        
        @Data
        @Builder
        public static class Field {
            private String type;
            private String name;
            private boolean isPointer;
            private String arraySize;
        }
    }
    
    @Data
    @Builder
    public static class EnumInfo {
        private String name;
        private int lineNumber;
        private List<String> values;
    }
    
    @Data
    @Builder
    public static class SqlQuery {
        private int lineNumber;
        private String type; // SELECT, INSERT, UPDATE, DELETE
        private String query;
        private List<String> bindVariables;
        private boolean isDynamic;
    }
    
    @Data
    @Builder
    public static class DefineInfo {
        private String name;
        private String value;
        private int lineNumber;
        private boolean isFunctionLike; // 매크로 함수
    }
    
    @Data
    @Builder
    public static class VariableInfo {
        private String type;
        private String name;
        private boolean isStatic;
        private boolean isConst;
    }
}
