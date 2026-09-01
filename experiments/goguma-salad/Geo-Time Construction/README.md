# Geo-Time Construction

실제 건설 CAD를 `Geo × Time × Layer` 구조의 공간 이력 데이터로 변환할 수 있는지 검증하는 독립 Workstream이다.

현재 Phase 1은 `ET-1101`의 `e-wire`, `e-wire3s`를 통신 배선 Construction Object로 바꾸고 2D/3D로 표시하는 PoC를 구현한다. 원본 작업지시서는 `docs/raw/Geo-Time Construction.md`, CAD 사전 검토 결과는 `docs/phase-1-cad-review.md`, 구현 구조는 `docs/architecture.md`를 참고한다.

전체 CAD Dataset은 로컬 참고자료로 유지한다. Git에는 Phase 1 대상인 `ET-1101 단위세대 홈네트워크설비 평면도.dwg`와 재현에 필요한 두 Xref만 포함할 수 있도록 제한한다.

## 현재 범위

- DXF Layer, Entity, 단위와 평형 목록 분석
- `XR_SHEET` 좌표를 이용한 84㎡A 등 평형별 Crop
- `e-wire`, `e-wire3s` 선형 Entity의 `communication/cable_path` 변환
- 원본 mm 좌표를 평형 기준 m 좌표로 정규화
- `XR_단위`의 선형 Entity를 100mm 이상 경량 건축 배경으로 결합
- React 2D SVG와 Three.js 3D Cylinder Viewer
- 2D·3D 객체 선택과 CAD 원본 속성 확인

## 준비

Python 3.11 이상, Node.js 20 이상이 필요하다. DWG를 DXF로 바꾸려면 ODA File Converter 또는 AutoCAD가 필요하다.

Script는 PATH, 일반 설치 경로와 로컬 임시 추출 경로를 순서대로 탐색한다. 자동으로 찾지 못하면 실제 `ODAFileConverter.exe` 위치를 PowerShell 환경 변수로 지정한다. 아래 경로는 예시이므로 설치 위치에 맞게 바꾼다.

```powershell
$env:ODA_FILE_CONVERTER = 'C:\Path\To\ODAFileConverter.exe'
```

현재 저장소를 검증할 때처럼 Windows 임시 폴더에 압축을 풀었다면 다음 형식을 사용할 수 있다.

```powershell
$env:ODA_FILE_CONVERTER = "$env:TEMP\ODAFileConverterPortable\ODAFileConverter.exe"
```

Phase 1 세 파일만 DXF로 변환한다.

```powershell
.\scripts\convert-phase1.ps1
```

변환 결과는 Git에서 제외되는 `data/derived/`에 생성된다.

## Backend 실행

```powershell
cd backend
py -3.11 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -e '.[dev]'
.\.venv\Scripts\python.exe -m uvicorn app.main:app --reload
```

API:

- `POST /api/cad/analyze`: DXF 통계와 평형 목록
- `POST /api/cad/construction-objects`: 선택 평형의 통신 배선 Construction Object
- `POST /api/cad/architecture-background`: 선택 평형의 경량 건축 배경 선분
- `GET /health`: 상태 확인

## Web 실행

```powershell
cd web
npm install
npm run dev
```

`http://localhost:5173`에서 통신 도면으로 `data/derived/ET-1101 단위세대 홈네트워크설비 평면도.dxf`, 선택 건축 배경으로 `data/derived/XR_단위.dxf`를 지정한다. 기본값은 84㎡A, 높이 2.3m, 지름 0.03m이며 높이와 지름은 Viewer용 가정값이다. 건축 배경은 Toggle로 숨길 수 있고 통신 경로를 클릭하면 원본 Layer, Entity, Handle과 표시 속성을 확인할 수 있다.

## 검증

```powershell
cd backend
.\.venv\Scripts\python.exe -m ruff check app tests
.\.venv\Scripts\python.exe -m pytest -q

cd ..\web
npm run lint
npm run build
```
