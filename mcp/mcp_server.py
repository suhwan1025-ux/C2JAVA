"""
C2JAVA MCP Server
Model Context Protocol 서버 구현
"""

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import Optional, List, Dict, Any
import os
import structlog

# 로거 설정
logger = structlog.get_logger()

app = FastAPI(
    title="C2JAVA MCP Server",
    description="Model Context Protocol Server for C2JAVA",
    version="1.0.0"
)


class ConversionContext(BaseModel):
    """변환 컨텍스트 모델"""
    source_code: str
    source_language: str = "c"
    target_language: str = "java"
    target_framework: str = "spring-boot-3.2.5"
    conversion_rules: Optional[Dict[str, Any]] = None
    additional_context: Optional[str] = None


class ConversionResult(BaseModel):
    """변환 결과 모델"""
    success: bool
    converted_code: Optional[str] = None
    errors: List[str] = []
    warnings: List[str] = []
    metadata: Dict[str, Any] = {}


class HealthResponse(BaseModel):
    """헬스체크 응답"""
    status: str
    version: str


@app.get("/health", response_model=HealthResponse)
async def health_check():
    """헬스체크 엔드포인트"""
    return HealthResponse(
        status="healthy",
        version="1.0.0"
    )


@app.post("/context/prepare", response_model=Dict[str, Any])
async def prepare_context(context: ConversionContext):
    """
    변환을 위한 컨텍스트 준비
    LLM에 전달할 프롬프트와 컨텍스트를 구성합니다.
    """
    logger.info("Preparing conversion context")
    
    # 변환 규칙 로드
    rules = context.conversion_rules or load_default_rules()
    
    # 시스템 프롬프트 생성
    system_prompt = build_system_prompt(context, rules)
    
    # 사용자 프롬프트 생성
    user_prompt = build_user_prompt(context)
    
    return {
        "system_prompt": system_prompt,
        "user_prompt": user_prompt,
        "metadata": {
            "source_language": context.source_language,
            "target_language": context.target_language,
            "target_framework": context.target_framework,
            "rules_applied": list(rules.keys()) if rules else []
        }
    }


@app.post("/context/analyze", response_model=Dict[str, Any])
async def analyze_code(context: ConversionContext):
    """
    소스 코드 분석
    변환 전 코드 구조를 분석합니다.
    """
    logger.info("Analyzing source code")
    
    source_code = context.source_code
    
    # 기본 분석
    analysis = {
        "line_count": len(source_code.split('\n')),
        "char_count": len(source_code),
        "includes": extract_includes(source_code),
        "functions": extract_functions(source_code),
        "structs": extract_structs(source_code),
        "global_variables": extract_globals(source_code),
        "complexity_estimate": estimate_complexity(source_code)
    }
    
    return analysis


@app.post("/context/validate", response_model=Dict[str, Any])
async def validate_conversion(result: ConversionResult):
    """
    변환 결과 검증
    변환된 코드의 유효성을 검사합니다.
    """
    logger.info("Validating conversion result")
    
    if not result.converted_code:
        return {
            "valid": False,
            "issues": ["No converted code provided"]
        }
    
    issues = []
    warnings = []
    
    code = result.converted_code
    
    # 기본 검증
    if "package " not in code:
        issues.append("Missing package declaration")
    
    if "import " not in code:
        warnings.append("No import statements found")
    
    if "public class " not in code:
        issues.append("Missing public class declaration")
    
    # Spring Boot 관련 검증
    if "@SpringBootApplication" not in code and "Application" in code:
        warnings.append("Consider adding @SpringBootApplication annotation")
    
    return {
        "valid": len(issues) == 0,
        "issues": issues,
        "warnings": warnings
    }


def load_default_rules() -> Dict[str, Any]:
    """기본 변환 규칙 로드"""
    return {
        "type_mapping": {
            "int": "int",
            "long": "long",
            "char*": "String",
            "void*": "Object",
        },
        "function_mapping": {
            "printf": "System.out.printf",
            "malloc": "new",
            "free": "// GC handles memory",
        }
    }


def build_system_prompt(context: ConversionContext, rules: Dict[str, Any]) -> str:
    """시스템 프롬프트 생성"""
    return f"""You are an expert software engineer specializing in code conversion.
Your task is to convert {context.source_language.upper()} code to {context.target_language.upper()} 
using {context.target_framework} framework.

Follow these rules:
1. Maintain the original logic and functionality
2. Use idiomatic {context.target_language} patterns
3. Apply proper error handling
4. Add appropriate documentation

Type Mappings:
{rules.get('type_mapping', {})}

Function Mappings:
{rules.get('function_mapping', {})}
"""


def build_user_prompt(context: ConversionContext) -> str:
    """사용자 프롬프트 생성"""
    prompt = f"""Convert the following {context.source_language.upper()} code to {context.target_language.upper()}:

```{context.source_language}
{context.source_code}
```
"""
    if context.additional_context:
        prompt += f"\n\nAdditional context:\n{context.additional_context}"
    
    return prompt


def extract_includes(code: str) -> List[str]:
    """include 문 추출"""
    includes = []
    for line in code.split('\n'):
        if line.strip().startswith('#include'):
            includes.append(line.strip())
    return includes


def extract_functions(code: str) -> List[Dict[str, str]]:
    """함수 정의 추출 (간단한 구현)"""
    # 실제 구현에서는 더 정교한 파싱 필요
    functions = []
    import re
    pattern = r'(\w+)\s+(\w+)\s*\(([^)]*)\)\s*\{'
    for match in re.finditer(pattern, code):
        functions.append({
            "return_type": match.group(1),
            "name": match.group(2),
            "params": match.group(3)
        })
    return functions


def extract_structs(code: str) -> List[str]:
    """구조체 정의 추출"""
    structs = []
    import re
    pattern = r'struct\s+(\w+)'
    for match in re.finditer(pattern, code):
        structs.append(match.group(1))
    return structs


def extract_globals(code: str) -> List[str]:
    """전역 변수 추출"""
    # 간단한 구현 - 실제로는 더 정교한 분석 필요
    return []


def estimate_complexity(code: str) -> str:
    """복잡도 추정"""
    lines = len(code.split('\n'))
    if lines < 50:
        return "low"
    elif lines < 200:
        return "medium"
    else:
        return "high"


if __name__ == "__main__":
    import uvicorn
    
    port = int(os.getenv("MCP_PORT", "8082"))
    uvicorn.run(app, host="0.0.0.0", port=port)
