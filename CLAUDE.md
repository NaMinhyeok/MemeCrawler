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
1. 크롤링 : `https://namu.wiki/w/%EB%B0%88(%EC%9D%B8%ED%84%B0%EB%84%B7%20%EC%9A%A9%EC%96%B4)/%EB%8C%80%ED%95%9C%EB%AF%BC%EA%B5%AD` 페이지에 하이퍼링크가 존재하는 데이터를 1차 크롤링한다.
2. 데이터 저장 : 크롤링한 데이터를 JSON 형식으로 저장한다.
3. 구조화 : 의미 없는 html 태그를 제거하고, 밈의 메타데이터를 추출하여 구조화한다.
4. 분석 : Google Vertex AI를 활용하여 밈의 유행 정도, 기원 등을 분석하여 하위의 구조화 가능한 데이터 형태로 구조화한다.
5. 저장 : 구조화된 데이터를 JSON 형식으로 저장한다.

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

## 크롤링 모듈
- 대상 URL:`https://namu.wiki/w/%EB%B0%88(%EC%9D%B8%ED%84%B0%EB%84%B7%20%EC%9A%A9%EC%96%B4)/%EB%8C%80%ED%95%9C%EB%AF%BC%EA%B5%AD` 
- 내부의 하이퍼링크를 통해 크롤링한다.
- Rate Limiting: (2초당 1회 요청으로 설정)
- 크롤링한 데이터는 JSON 형식으로 저장한다.