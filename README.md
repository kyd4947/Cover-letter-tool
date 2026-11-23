# Cover-letter-tool

자기소개서 AI — 채용 공고를 읽고, 내 경력 자료(profile.md)에서 **근거 있는 재료만** 골라 항목별 초안을 만든다.

- 공고 URL 크롤링 또는 `posting.txt` 백업 모드
- 공고 양식 자동 감지 (항목 + 글자 수), 실패 시 기본 4항목
- 항목별 초안 + 글자 수 clamp (초과 시 압축 재호출 1회)
- 자료 적재: 파일/폴더 + GitHub 공개 레포 → LLM 카드 → **사람 승인 y/n**
- CLI 3종 + Swing GUI (외부 GUI 의존 없음)

## 요구사항

- Java 21, Gradle (wrapper 포함)
- 외부 라이브러리: Gson, Jsoup, PDFBox
- Gemini API 키 — 우선순위: ① 환경변수 `GEMINI_API_KEY` → ② `config.json`의 `geminiApiKey`

## 빠른 시작

### 1. 설정 — `coverletter/config.json`

```json
{
  "geminiApiKey": "발급받은 키",
  "models": ["gemini-2.5-flash"],
  "temperature": 0.7,
  "maxOutputTokens": 4096,
  "profilePath": "profile.md",
  "outputDir": "output"
}
```

- 키는 환경변수로 대체 가능 (환경변수가 있으면 config.json 값보다 우선)
- `config.json`은 **gitignored** — 개인 키를 커밋하지 말 것
- `models` 첫 번째가 기본 모델, JSON이 깨져도 기본값으로 통과 (fail-open)

### 2. 경력 자료 — `coverletter/profile.md`

```md
# 이름 프로필

(전공·지원 직무 방향 1~2문장)

## 프로젝트명

목적: 만들거나 한 이유
기술: 실제 사용한 것
과제: 직면한 문제
결과: 근거 있는 결과
```

- `profile.md`도 **gitignored** — 개인 데이터
- 적재(Ingest)가 승인된 카드를 이 포맷으로 append

### 3. 실행 (Eclipse Run Config / cwd = `coverletter`)

| 진입점 | 인자 | 용도 |
|--------|------|------|
| `coverletter.gui.GuiMain` | 없음 | GUI (권장) |
| `coverletter.Main` | `<공고URL\|posting.txt> <회사명>` | 초안 생성 CLI |
| `coverletter.IngestMain` | `<자료파일...>` | 파일/폴더 적재 CLI |
| `coverletter.RepoIngestMain` | `<레포URL> [branch]` | GitHub 레포 적재 CLI |

GUI 첫 실행 시 키가 없으면 **온보딩 화면**이 뜨고, 키를 저장하면 메인으로 넘어간다.

## GUI 화면

| 화면 | 내용 |
|------|------|
| 온보딩 | 키 없을 때 최초 1회 안내 + 키 입력 → config.json 병합 저장 |
| 메인 | 공고 URL/파일 + 회사명 → 생성 (LLM 호출은 백그라운드, 로그 패널) |
| 결과 | 항목별 초안 + 글자 수 + 저장 파일 열기 |
| 적재 | 파일/폴더 선택 또는 레포 URL → 카드별 승인 다이얼로그 |
| 설정 | config 6키 편집 + profile 백업 (`backup/profile_날짜시간.md`) |

승인 다이얼로그는 **X(닫기) = 거부** (fail-closed). 승인 없이는 profile.md에 아무것도 쓰지 않는다.

## 생성 흐름 (`Main.generate`)

1. **공고 확보** — `http`로 시작하면 Jsoup 크롤, 아니면 텍스트 파일 경로
   - JS 렌더링으로 본문이 비면 중단하고 `posting.txt` 백업 모드 안내
2. **profile 로드** — 프로젝트 수 로그
3. **요구역량 추출** — LLM 1차
4. **양식 감지** — 항목·글자 수 유추, 실패 시 기본 양식
   (성장과정 / 지원동기 / 성격 장단점 / 입사 후 포부)
5. **항목별 초안** — 글자 수 초과 시 압축 재호출 1회
   - 압축 성공 → 채택 / 여전히 초과 → 더 가까운 것 채택 + 경고 / 무효 → 원본 유지
6. **저장** — `output/<회사명>_<날짜>.md`, 같은 날 재실행 시 `_2` 접미사

## 적재 (STORE → profile.md)

### 파일/폴더

지원 형식: `txt` `md` `pdf` `docx` `pptx` `hwpx` `ipynb` 등. 폴더는 재귀 수집.

```bash
IngestMain 이력서.pdf 프로젝트노트.md
```

### GitHub 레포

공개 레포만. `codeload` zip 직결(비로그인 API rate 미소모) → `cache/owner-repo/` 풀기
→ 텍스트 선별(확장자·1MB·무시 폴더) → LLM 요약 카드 → 승인 관문.

```bash
RepoIngestMain https://github.com/owner/repo
RepoIngestMain https://github.com/owner/repo/tree/브랜치
```

- `cache/`는 소모품 — 새로 받으려면 폴더 삭제 후 재실행
- private 레포는 미지원 (백로그)

### 승인 규칙

- 카드 본문을 그대로 보여주고 `y/n` 질문 — y만 append
- **완전 중복**(제목 일치)은 질문 없이 조용히 스킵
- **근사 중복**은 `[중복 의심]` 경고 후 사람이 판단
  (정규화 후 최장 공통 ≥ 짧은 쪽의 0.6, 5자 미만 미발동)

## 프로젝트 구조

```
coverletter/
├─ src/main/java/coverletter/
│  ├─ Main.java              생성 흐름 (CLI·GUI 공용)
│  ├─ IngestMain.java        파일/폴더 적재
│  ├─ RepoIngestMain.java    레포 적재
│  ├─ PromptBuilder.java     프롬프트 + 응답 파싱
│  ├─ config/AppConfig.java  설정 로딩
│  ├─ crawl/PostingScraper   공고 크롤링
│  ├─ ingest/                추출기·카드·승인 관문
│  ├─ llm/GeminiClient       Gemini 호출 (모델 후보 폴백)
│  ├─ output/                저장·회사 정보
│  ├─ profile/ProfileStore   profile.md 파싱
│  └─ gui/                   Swing GUI (5화면 + 상태바)
├─ config.json               (gitignored)
├─ profile.md                (gitignored)
├─ posting.txt               (gitignored) 공고 본문 백업용
├─ cache/                    (gitignored) 레포 스냅샷
└─ output/                   (gitignored) 생성 결과
```

## Windows 빌드 (M4)

```bat
build.bat
```

- 산출물: `dist\CoverLetterTool\CoverLetterTool.exe` (app-image, JDK 런타임 내장) + `dist\CoverLetterTool-portable.zip`
- 압축을 푼 폴더가 실행 위치 — `config.json`·`profile.md`·`output`도 그 폴더 기준으로 생성
- 설치형 `exe`(MSI 포함)는 **WiX Toolset 3.x** 필요 — 미설치 환경에서는 app-image + zip으로 배포

## 참고

- `config.json` 위치는 고정 (`coverletter/config.json`) — 위치 자체는 설정 불가
- GUI 설정 화면 저장은 폼이 아는 6키만 기록
- 공고 본문을 `posting.txt`에 붙여넣으면 URL 크롤 실패 시에도 생성 가능
