# 빌드 / 패치 매뉴얼

## 코드 편집 위치
- 항상 `paper-server/` (또는 `paper-api/`) 작업 트리에서 수정 — `.patch` 파일 직접 편집 금지

## 상황별 명령

- 최초 1회만 (Windows long-path) → `git config --global core.longpaths true`
- 작업 트리 처음 생성 / 업스트림·버전 갱신 후 → `.\gradlew.bat applyAllPatches`
- 소스만 고쳐서 JAR만 바로 뽑기 (실행·테스트용) → `.\gradlew.bat createMojmapPaperclipJar`
- 수정을 .patch로 영구 저장 (커밋 전 필수) → `paper-server/`에서 `git add -A && git commit` → `.\gradlew.bat :aspaper-server:rebuildAllServerPatches`
- 작업 트리가 깨졌다 / 패치부터 다시 적용 → `.\gradlew.bat applyAllPatches` → 빌드
- Paper 업스트림 버전업 (같은 MC) → `gradle.properties`의 `paperRef`를 새 커밋으로 변경 → `.\gradlew.bat applyAllPatches` (충돌 시 수동 해결) → 빌드
- Minecraft 버전 자체 변경 → `paperRef` + `mcVersion` + (`aspaper-server/build.gradle.kts`의 `mache` 버전) 변경 → `applyAllPatches` → 충돌 해결 → 빌드

## 주의 (버전 숫자 의미)
- `version=...build.21` → JAR 라벨일 뿐, 바꿔도 컴파일 소스 안 바뀜
- 실제 업스트림 소스는 `paperRef`(커밋 해시)가 결정 → 이걸 바꿔야 최신 기준
- `paperRef` 바꾼 뒤 `applyAllPatches` 안 돌리면 옛 소스로 빌드됨 (번들링만으론 갱신 안 됨)

## paperRef 확인/변경 위치
- 정의 위치 → `gradle.properties`의 `paperRef=...` (line 7)
- 새 값 출처 → https://github.com/PaperMC/Paper 의 MC 버전 브랜치(현재 26.2는 기본 브랜치) 최신 커밋 → 그 커밋의 전체 SHA 복사
- 현재 값 커밋 내용 확인 → `git -C .gradle/caches/paperweight/upstreams/paper log -1 <SHA>`

## 버전업 전체 절차 (내 패치 보존)
- 내 작업은 전부 `.patch` 파일(`aspaper-server/**`, `aspaper-api/**`)로 메인 저장소에 있고, `applyAllPatches`가 새 업스트림 위에 다시 얹어줌 → 그래서 보존됨. 단 `.patch`로 안 뽑은 작업 트리 수정은 날아감.
- 1) 작업 트리(`paper-server/`)에 미저장 수정 있으면 먼저 저장: 그 폴더에서 `git add -A && git commit` → `.\gradlew.bat :aspaper-server:rebuildAllServerPatches` → 메인 저장소에서 바뀐 `.patch` 커밋
- 2) `gradle.properties`의 `paperRef`를 새 커밋 SHA로 변경
- 3) `.\gradlew.bat applyAllPatches` (내 패치 재적용)
- 3-충돌) 충돌 시: 밀린 reject를 `paper-server/`에서 수동 수정 → 그 폴더에서 `git add -A && git commit` → `.\gradlew.bat :aspaper-server:rebuildAllServerPatches`
- 4) `.\gradlew.bat createMojmapPaperclipJar` (번들링)
- 5) 메인 저장소에서 `gradle.properties` + 갱신된 `.patch` 파일 커밋

## 산출물
- `aspaper-server/build/libs/aspaper-paperclip-<version>.jar`
- 실행: `java -jar aspaper-paperclip-*.jar`
