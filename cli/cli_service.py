"""
C2JAVA CLI Service
AIDER, Fabric 통합 CLI HTTP API 서비스
여러 사용자의 동시 요청 처리 지원
"""

import os
import subprocess
import json
import asyncio
from pathlib import Path
from typing import Optional, Dict, Any, List
from concurrent.futures import ThreadPoolExecutor
from dotenv import load_dotenv
import structlog
from fastapi import FastAPI, HTTPException, BackgroundTasks
from pydantic import BaseModel
import uvicorn
import httpx

# 환경변수 로드
load_dotenv()

# 로거 설정
logger = structlog.get_logger()

# FastAPI 앱
app = FastAPI(
    title="C2JAVA CLI Service",
    description="AIDER, Fabric 통합 CLI 서비스",
    version="1.0.0"
)

# 작업 처리를 위한 ThreadPool
worker_count = int(os.getenv('CLI_WORKER_COUNT', '3'))
executor = ThreadPoolExecutor(max_workers=worker_count)


class LlmConfig:
    """LLM 설정 관리"""
    
    def __init__(self):
        self.active_provider = os.getenv('ACTIVE_LLM_PROVIDER', 'qwen3')
        
        # QWEN3 설정
        self.qwen3_url = os.getenv('QWEN3_API_URL', '')
        self.qwen3_key = os.getenv('QWEN3_API_KEY', '')
        self.qwen3_model = os.getenv('QWEN3_MODEL_NAME', 'qwen3-vl-235b')
        
        # GPT OSS 설정
        self.gpt_oss_url = os.getenv('GPT_OSS_API_URL', '')
        self.gpt_oss_key = os.getenv('GPT_OSS_API_KEY', '')
        self.gpt_oss_model = os.getenv('GPT_OSS_MODEL_NAME', 'gpt-oss')
    
    def get_active_config(self) -> Dict[str, str]:
        """활성 LLM 설정 반환"""
        if self.active_provider == 'qwen3':
            return {
                'api_url': self.qwen3_url,
                'api_key': self.qwen3_key,
                'model': self.qwen3_model,
            }
        else:
            return {
                'api_url': self.gpt_oss_url,
                'api_key': self.gpt_oss_key,
                'model': self.gpt_oss_model,
            }


class AiderService:
    """AIDER CLI 래퍼"""
    
    def __init__(self, llm_config: LlmConfig):
        self.llm_config = llm_config
        self.enabled = os.getenv('AIDER_ENABLED', 'true').lower() == 'true'
        self.auto_commits = os.getenv('AIDER_AUTO_COMMITS', 'false').lower() == 'true'
    
    def convert(self, 
                source_file: str, 
                output_dir: str,
                instructions: str) -> Dict[str, Any]:
        """AIDER를 사용한 코드 변환"""
        
        if not self.enabled:
            logger.warning("AIDER is disabled")
            return {'success': False, 'error': 'AIDER is disabled'}
        
        config = self.llm_config.get_active_config()
        
        # AIDER 환경변수 설정
        env = os.environ.copy()
        env['OPENAI_API_BASE'] = config['api_url']
        env['OPENAI_API_KEY'] = config['api_key']
        
        # AIDER 명령어 구성
        cmd = [
            'aider',
            '--model', config['model'],
            '--file', source_file,
            '--message', instructions,
        ]
        
        if not self.auto_commits:
            cmd.append('--no-auto-commits')
        
        try:
            result = subprocess.run(
                cmd,
                env=env,
                capture_output=True,
                text=True,
                timeout=600,  # 10분 타임아웃
                cwd=output_dir
            )
            
            return {
                'success': result.returncode == 0,
                'stdout': result.stdout,
                'stderr': result.stderr,
                'returncode': result.returncode
            }
            
        except subprocess.TimeoutExpired:
            logger.error("AIDER timeout")
            return {'success': False, 'error': 'Timeout'}
        except Exception as e:
            logger.error("AIDER error", error=str(e))
            return {'success': False, 'error': str(e)}


class FabricService:
    """Fabric CLI 래퍼"""
    
    def __init__(self, llm_config: LlmConfig):
        self.llm_config = llm_config
        self.enabled = os.getenv('FABRIC_ENABLED', 'true').lower() == 'true'
        self.default_pattern = os.getenv('FABRIC_DEFAULT_PATTERN', 'analyze_code')
    
    def analyze(self, 
                source_file: str,
                pattern: Optional[str] = None) -> Dict[str, Any]:
        """Fabric을 사용한 코드 분석"""
        
        if not self.enabled:
            logger.warning("Fabric is disabled")
            return {'success': False, 'error': 'Fabric is disabled'}
        
        config = self.llm_config.get_active_config()
        pattern = pattern or self.default_pattern
        
        # Fabric 환경변수 설정
        env = os.environ.copy()
        env['OPENAI_API_BASE'] = config['api_url']
        env['OPENAI_API_KEY'] = config['api_key']
        
        # 파일 내용 읽기
        with open(source_file, 'r') as f:
            content = f.read()
        
        # Fabric 명령어 구성
        cmd = [
            'fabric',
            '--pattern', pattern,
            '--model', config['model'],
        ]
        
        try:
            result = subprocess.run(
                cmd,
                env=env,
                input=content,
                capture_output=True,
                text=True,
                timeout=300  # 5분 타임아웃
            )
            
            return {
                'success': result.returncode == 0,
                'analysis': result.stdout,
                'stderr': result.stderr,
                'returncode': result.returncode
            }
            
        except subprocess.TimeoutExpired:
            logger.error("Fabric timeout")
            return {'success': False, 'error': 'Timeout'}
        except Exception as e:
            logger.error("Fabric error", error=str(e))
            return {'success': False, 'error': str(e)}


class CliService:
    """통합 CLI 서비스"""
    
    def __init__(self):
        self.llm_config = LlmConfig()
        self.aider = AiderService(self.llm_config)
        self.fabric = FabricService(self.llm_config)
    
    def analyze_file(self, source_file: str) -> Dict[str, Any]:
        """파일 분석"""
        logger.info("Analyzing file", file=source_file)
        return self.fabric.analyze(source_file)
    
    def convert_to_java(self, 
                        source_file: str,
                        output_dir: str) -> Dict[str, Any]:
        """C to Java 변환"""
        
        instructions = """
        Convert this C code to Java Spring Boot 3.2.5 code.
        
        Requirements:
        1. Use Java 21 LTS features
        2. Follow Spring Boot best practices
        3. Use Lombok annotations (@Data, @Builder)
        4. Include proper exception handling
        5. Add Javadoc comments
        6. Convert C structs to Java classes
        7. Convert C functions to Java methods
        8. Handle memory management appropriately
        """
        
        logger.info("Converting to Java", source=source_file, output=output_dir)
        return self.aider.convert(source_file, output_dir, instructions)
    
    def fix_compile_errors(self,
                           java_file: str,
                           error_log: str) -> Dict[str, Any]:
        """컴파일 오류 수정"""
        
        instructions = f"""
        Fix the following compile errors in the Java code:
        
        {error_log}
        
        Make minimal changes to fix the errors while maintaining functionality.
        """
        
        output_dir = str(Path(java_file).parent)
        
        logger.info("Fixing compile errors", file=java_file)
        return self.aider.convert(java_file, output_dir, instructions)


# ============================================
# HTTP API 엔드포인트
# ============================================

class AnalyzeRequest(BaseModel):
    source_file: str
    pattern: Optional[str] = None


class ConvertRequest(BaseModel):
    source_file: str
    output_dir: str
    instructions: Optional[str] = None


class FixRequest(BaseModel):
    java_file: str
    error_log: str


class ConfigUpdateRequest(BaseModel):
    configs: Dict[str, str]


# 싱글톤 서비스 인스턴스
cli_service = CliService()


@app.get("/health")
async def health_check():
    """헬스체크"""
    return {
        "status": "healthy",
        "llm_provider": cli_service.llm_config.active_provider,
        "aider_enabled": cli_service.aider.enabled,
        "fabric_enabled": cli_service.fabric.enabled,
        "worker_count": worker_count
    }


@app.get("/config")
async def get_config():
    """현재 설정 조회"""
    return {
        "llm": {
            "active_provider": cli_service.llm_config.active_provider,
            "qwen3_url": cli_service.llm_config.qwen3_url,
            "qwen3_model": cli_service.llm_config.qwen3_model,
            "gpt_oss_url": cli_service.llm_config.gpt_oss_url,
            "gpt_oss_model": cli_service.llm_config.gpt_oss_model,
        },
        "aider": {
            "enabled": cli_service.aider.enabled,
            "auto_commits": cli_service.aider.auto_commits,
        },
        "fabric": {
            "enabled": cli_service.fabric.enabled,
            "default_pattern": cli_service.fabric.default_pattern,
        }
    }


@app.post("/config")
async def update_config(request: ConfigUpdateRequest):
    """설정 업데이트 (런타임 반영)"""
    configs = request.configs
    
    for key, value in configs.items():
        os.environ[key.upper()] = value
    
    # 서비스 재초기화
    global cli_service
    cli_service = CliService()
    
    logger.info("Configuration updated", configs=list(configs.keys()))
    return {"status": "updated", "configs": list(configs.keys())}


@app.post("/analyze")
async def analyze_file(request: AnalyzeRequest):
    """파일 분석 API"""
    try:
        # ThreadPool에서 실행 (블로킹 작업)
        loop = asyncio.get_event_loop()
        result = await loop.run_in_executor(
            executor,
            cli_service.fabric.analyze,
            request.source_file,
            request.pattern
        )
        return result
    except Exception as e:
        logger.error("Analysis failed", error=str(e))
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/convert")
async def convert_to_java(request: ConvertRequest, background_tasks: BackgroundTasks):
    """C to Java 변환 API"""
    try:
        instructions = request.instructions or """
        Convert this C code to Java Spring Boot 3.2.5 code.
        Use Java 21 LTS features, Lombok annotations, and proper exception handling.
        """
        
        # ThreadPool에서 실행
        loop = asyncio.get_event_loop()
        result = await loop.run_in_executor(
            executor,
            cli_service.aider.convert,
            request.source_file,
            request.output_dir,
            instructions
        )
        return result
    except Exception as e:
        logger.error("Conversion failed", error=str(e))
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/fix")
async def fix_compile_errors(request: FixRequest):
    """컴파일 오류 수정 API"""
    try:
        loop = asyncio.get_event_loop()
        result = await loop.run_in_executor(
            executor,
            cli_service.fix_compile_errors,
            request.java_file,
            request.error_log
        )
        return result
    except Exception as e:
        logger.error("Fix failed", error=str(e))
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/status")
async def get_status():
    """서비스 상태 조회"""
    return {
        "active_workers": executor._work_queue.qsize(),
        "max_workers": worker_count,
        "llm_provider": cli_service.llm_config.active_provider
    }


def main():
    """메인 진입점 - HTTP 서버 모드"""
    import argparse
    
    parser = argparse.ArgumentParser(description='C2JAVA CLI Service')
    parser.add_argument('--port', type=int, default=8083, help='HTTP server port')
    parser.add_argument('--host', type=str, default='0.0.0.0', help='HTTP server host')
    parser.add_argument('--analyze', type=str, help='Analyze a C file (CLI mode)')
    parser.add_argument('--convert', type=str, help='Convert a C file to Java (CLI mode)')
    parser.add_argument('--output', type=str, default='.', help='Output directory')
    
    args = parser.parse_args()
    
    # CLI 모드
    if args.analyze:
        result = cli_service.analyze_file(args.analyze)
        print(json.dumps(result, indent=2))
    elif args.convert:
        result = cli_service.convert_to_java(args.convert, args.output)
        print(json.dumps(result, indent=2))
    else:
        # HTTP 서버 모드
        port = int(os.getenv('CLI_SERVICE_PORT', args.port))
        logger.info(f"Starting CLI HTTP service on {args.host}:{port}")
        uvicorn.run(app, host=args.host, port=port)


if __name__ == '__main__':
    main()
