# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MemeCrawler is a Java project managed with Gradle. This is a fresh project with basic structure but no source code yet.

## Build & Development Commands

### Building the project
```bash
./gradlew build
```

### Running tests
```bash
./gradlew test
```

### Cleaning build artifacts
```bash
./gradlew clean
```

### Running a single test class
```bash
./gradlew test --tests "ClassName"
```

## Project Structure

- Standard Gradle Java project layout
- Source code: `src/main/java/`
- Test code: `src/test/java/`
- Resources: `src/main/resources/` and `src/test/resources/`
- Uses JUnit 5 for testing

## Key Configuration

- Java project using Gradle build system
- Group: `org.nexters`
- Uses JUnit Jupiter (JUnit 5) for testing
- Maven Central repository for dependencies

# 프로젝트의 목적
MemeCrawler는 인터넷에서 밈을 수집하고 분석하는 도구입니다. 이 프로젝트는 Java로 작성되며 Gradle을 사용하여 빌드 및 의존성 관리를 수행합니다.

크롤링 하는 사이트는 `namu.wiki`이며 해당 사이트의 robots.txt는 아래와 같다.

```txt
User-agent: *
Disallow: /
Allow: /$
Allow: /ads.txt
Allow: /w/
Allow: /history/
Allow: /backlink/
Allow: /OrphanedPages
Allow: /UncategorizedPages
Allow: /ShortestPages
Allow: /LongestPages
Allow: /RecentChanges
Allow: /RecentDiscuss
Allow: /Search
Allow: /discuss/
Allow: /js/
Allow: /img/
Allow: /css/
Allow: /skins/
Allow: /favicon.ico
Allow: /_nuxt/
Allow: /sidebar.json
Allow: /cdn-cgi/
```

## 전체 어플리케이션 파이프라인
1. **1차 크롤링** : `https://namu.wiki/w/%EB%B0%88(%EC%9D%B8%ED%84%B0%EB%84%B7%20%EC%9A%A9%EC%96%B4)/%EB%8C%80%ED%95%9C%EB%AF%BC%EA%B5%AD` 페이지에서 하이퍼링크 목록을 추출
2. **메타데이터 저장** : 추출한 링크들을 `raw_meme_data.json`에 저장
3. **2차 상세 크롤링** : `raw_meme_data.json`의 각 URL에 접속하여 전체 페이지 데이터를 크롤링
4. **개별 파일 저장** : 각 밈의 상세 데이터를 `detailed_meme_data/` 디렉토리에 title 기반 파일명으로 저장
5. **구조화** : 의미 없는 html 태그를 제거하고, 밈의 메타데이터를 추출하여 구조화
6. **분석** : Google Vertex AI를 활용하여 밈의 유행 정도, 기원 등을 분석하여 구조화된 데이터 형태로 변환
7. **최종 저장** : 구조화된 데이터를 JSON 형식으로 저장

## 개발 가이드라인
크롤링 한 데이터를 저장하는 형식은 JSON으로 하며, 각 밈의 메타데이터를 포함해야 한다.

### 크롤링한 데이터를 통해 구조화한다.
Google Vertex AI를 활용하여 이를 분석하고, 아래의 구조화된 형태로 저장한다.
- 밈의 제목
- 밈의 설명
- 밈의 기원
- 밈의 유행 정도
- 패러디 된 밈
- 밈의 유행 시기
- 밈의 관련 이미지 URL
- 밈의 나무위키 출처
- 밈의 관련 키워드

## 크롤링 실행 방법

### 1차 크롤링 (링크 목록 추출)
```bash
./gradlew run
# 또는 IDE에서 Main.java 실행
```
- 결과: `raw_meme_data.json` 파일 생성
- 포함 데이터: URL, 제목, 기본 컨텐츠, 이미지 목록

### 2차 상세 크롤링 (개별 페이지 전체 데이터)
```bash
# DetailedMemeCrawler.java 실행
```
- 전제조건: `raw_meme_data.json` 파일 존재
- 결과: `detailed_meme_data/` 디렉토리에 개별 JSON 파일들 생성
- 파일명: 밈 제목을 기반으로 sanitize된 파일명 사용
- 포함 데이터: 
  - 전체 HTML 콘텐츠
  - 메타 태그들
  - 모든 이미지 정보 (src, alt, title)
  - 모든 링크 정보 (href, text, title)
  - 헤딩 구조 (h1-h6)
  - 크롤링 시간

### 크롤링 설정
- Rate Limiting: 0.5초당 1회 요청
- Timeout: 15초
- User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36