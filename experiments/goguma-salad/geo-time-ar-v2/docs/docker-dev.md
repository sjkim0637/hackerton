# Docker Development

## 서비스

| 서비스 | 포트 | 역할 |
|---|---:|---|
| db | 5432 | PostgreSQL/PostGIS |
| minio | 9000 | Object Storage API |
| minio | 9001 | Console |
| minio-init | 없음 | Bucket과 Demo Asset 초기화 후 종료 |
| backend | 8000 | Migration, Seed, FastAPI |

## 시작 및 검증

```powershell
docker compose up -d --build
docker compose ps
powershell -ExecutionPolicy Bypass -File .\infra\scripts\smoke-test.ps1
```
Backend 시작 명령은 Alembic upgrade와 Seed를 먼저 실행한다. Seed는 고정 UUID를 사용하며 Demo Zone이 존재하면 중복 생성하지 않는다.

## 로그

```powershell
docker compose logs -f backend
docker compose logs -f db
docker compose logs minio-init
```

## 완전 재생성

다음 명령은 이 Compose 프로젝트의 PostgreSQL과 MinIO 볼륨을 삭제한다.

```powershell
docker compose down -v
docker compose up -d --build
```
