# Aspose Docx Wrapper Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a 0-1 Aspose Words wrapper that creates docx files through a fluent Builder API backed by an internal docx component tree.

**Architecture:** The wrapper exposes fluent builders for document, section, paragraph, table, row, and cell creation. Internally it stores a Composite tree of block and inline nodes, resolves default styles through a registry, validates legal parent-child relationships, and renders through an Aspose adapter boundary.

**Tech Stack:** Java 11 source level, Maven dependency management through `/Users/luojiang/maven/apache-maven-3.6.3/bin/mvn`, settings at `/Users/luojiang/maven/apache-maven-3.6.3/conf/settings.xml`, local repository at `/Users/luojiang/maven/repository`, Aspose Words 24.12 jdk17 classifier, JUnit Jupiter 5.

---

## File Structure

- Create: `src/main/java/cn/bugstack/office/docx/api/DocxDocument.java`
  - Public facade and fluent entry point.
- Create: `src/main/java/cn/bugstack/office/docx/builder/*.java`
  - Fluent builders for document sections, paragraphs, tables, rows, and cells.
- Create: `src/main/java/cn/bugstack/office/docx/model/*.java`
  - Composite node model: document, section, paragraph, table, row, cell, text, image, visio.
- Create: `src/main/java/cn/bugstack/office/docx/style/*.java`
  - Default style registry and copyable style option objects.
- Create: `src/main/java/cn/bugstack/office/docx/source/*.java`
  - Image source strategy abstraction.
- Create: `src/main/java/cn/bugstack/office/docx/validate/*.java`
  - Tree validation before rendering.
- Create: `src/main/java/cn/bugstack/office/docx/render/*.java`
  - Renderer interface and Aspose implementation boundary.
- Create: `src/main/java/cn/bugstack/office/docx/exception/*.java`
  - Build, validation, and render exceptions.
- Create: `src/test/java/cn/bugstack/office/docx/*Test.java`
  - JUnit tests for builder structure, styles, validation, and rendering.
- Create: `src/main/java/cn/bugstack/office/docx/example/DocxWrapperExample.java`
  - Runnable example that creates a document with headings, paragraphs, images/Visio placeholders, and a table.

## Task 1: Composite Model and Builder API

**Files:**
- Create: `src/test/java/cn/bugstack/office/docx/DocxBuilderTest.java`
- Create: `src/main/java/cn/bugstack/office/docx/api/DocxDocument.java`
- Create: `src/main/java/cn/bugstack/office/docx/builder/SectionBuilder.java`
- Create: `src/main/java/cn/bugstack/office/docx/builder/ParagraphBuilder.java`
- Create: `src/main/java/cn/bugstack/office/docx/builder/TableBuilder.java`
- Create: `src/main/java/cn/bugstack/office/docx/builder/RowBuilder.java`
- Create: `src/main/java/cn/bugstack/office/docx/builder/CellBuilder.java`
- Create: `src/main/java/cn/bugstack/office/docx/model/*.java`

- [ ] Write failing JUnit test for document/section/paragraph/table tree shape.
- [ ] Run `/Users/luojiang/maven/apache-maven-3.6.3/bin/mvn -s /Users/luojiang/maven/apache-maven-3.6.3/conf/settings.xml -Dmaven.repo.local=/Users/luojiang/maven/repository -q -Dtest=DocxBuilderTest test`.
- [ ] Implement marker interfaces, node classes, and fluent builders.
- [ ] Run `/Users/luojiang/maven/apache-maven-3.6.3/bin/mvn -s /Users/luojiang/maven/apache-maven-3.6.3/conf/settings.xml -Dmaven.repo.local=/Users/luojiang/maven/repository -q -Dtest=DocxBuilderTest test`.

## Task 2: Default Style Registry

**Files:**
- Create: `src/test/java/cn/bugstack/office/docx/StyleRegistryTest.java`
- Create: `src/main/java/cn/bugstack/office/docx/style/StyleRegistry.java`
- Create: `src/main/java/cn/bugstack/office/docx/style/DefaultStyles.java`
- Create: `src/main/java/cn/bugstack/office/docx/style/ParagraphStyle.java`
- Create: `src/main/java/cn/bugstack/office/docx/style/RunStyle.java`
- Create: `src/main/java/cn/bugstack/office/docx/style/TableStyle.java`
- Create: `src/main/java/cn/bugstack/office/docx/style/ImageStyle.java`

- [ ] Write failing test for required default style names and copy isolation.
- [ ] Run `/Users/luojiang/maven/apache-maven-3.6.3/bin/mvn -s /Users/luojiang/maven/apache-maven-3.6.3/conf/settings.xml -Dmaven.repo.local=/Users/luojiang/maven/repository -q -Dtest=StyleRegistryTest test`.
- [ ] Implement default styles and copy methods.
- [ ] Run `/Users/luojiang/maven/apache-maven-3.6.3/bin/mvn -s /Users/luojiang/maven/apache-maven-3.6.3/conf/settings.xml -Dmaven.repo.local=/Users/luojiang/maven/repository -q -Dtest=StyleRegistryTest test`.

## Task 3: Validation

**Files:**
- Create: `src/test/java/cn/bugstack/office/docx/DocxValidatorTest.java`
- Create: `src/main/java/cn/bugstack/office/docx/validate/DocxValidator.java`
- Create: `src/main/java/cn/bugstack/office/docx/validate/ValidationResult.java`
- Create: `src/main/java/cn/bugstack/office/docx/exception/DocxValidationException.java`

- [ ] Write failing validation tests for empty document, valid section, and inconsistent table row cell counts.
- [ ] Run `/Users/luojiang/maven/apache-maven-3.6.3/bin/mvn -s /Users/luojiang/maven/apache-maven-3.6.3/conf/settings.xml -Dmaven.repo.local=/Users/luojiang/maven/repository -q -Dtest=DocxValidatorTest test`.
- [ ] Implement recursive validator.
- [ ] Run `/Users/luojiang/maven/apache-maven-3.6.3/bin/mvn -s /Users/luojiang/maven/apache-maven-3.6.3/conf/settings.xml -Dmaven.repo.local=/Users/luojiang/maven/repository -q -Dtest=DocxValidatorTest test`.

## Task 4: Aspose Renderer Boundary

**Files:**
- Create: `src/test/java/cn/bugstack/office/docx/RendererBoundaryTest.java`
- Create: `src/main/java/cn/bugstack/office/docx/render/DocxRenderer.java`
- Create: `src/main/java/cn/bugstack/office/docx/render/AsposeDocxRenderer.java`
- Create: `src/main/java/cn/bugstack/office/docx/render/RenderContext.java`
- Create: `src/main/java/cn/bugstack/office/docx/source/ImageSource.java`
- Create: `src/main/java/cn/bugstack/office/docx/source/PathImageSource.java`
- Create: `src/main/java/cn/bugstack/office/docx/exception/DocxRenderException.java`

- [ ] Write failing test that `DocxDocument.save(path)` delegates to a renderer and validates first.
- [ ] Run `/Users/luojiang/maven/apache-maven-3.6.3/bin/mvn -s /Users/luojiang/maven/apache-maven-3.6.3/conf/settings.xml -Dmaven.repo.local=/Users/luojiang/maven/repository -q -Dtest=RendererBoundaryTest test`.
- [ ] Implement renderer interface and Aspose renderer adapter.
- [ ] Run `/Users/luojiang/maven/apache-maven-3.6.3/bin/mvn -s /Users/luojiang/maven/apache-maven-3.6.3/conf/settings.xml -Dmaven.repo.local=/Users/luojiang/maven/repository -q -Dtest=RendererBoundaryTest test`.

## Task 5: Example and Full Verification

**Files:**
- Create: `src/main/java/cn/bugstack/office/docx/example/DocxWrapperExample.java`

- [ ] Add runnable example that builds a rich document tree and calls `save`.
- [ ] Run `/Users/luojiang/maven/apache-maven-3.6.3/bin/mvn -s /Users/luojiang/maven/apache-maven-3.6.3/conf/settings.xml -Dmaven.repo.local=/Users/luojiang/maven/repository test`.
- [ ] Run `java -cp target/classes:/Users/luojiang/maven/repository/com/aspose/aspose-words/24.12/aspose-words-24.12-jdk17.jar cn.bugstack.office.docx.example.DocxWrapperExample`.

## Self-Review

Spec coverage:

- Document, Section, Paragraph, Table, Image, Visio, and Style are covered by Tasks 1-4.
- Builder, Composite, Adapter/Renderer, Strategy, Factory-like construction, Prototype-like style copying, and validation are covered.
- Visio is intentionally modeled as a paragraph inline and rendered like image insertion in 0-1.

Known scope boundaries:

- Maven is used for dependency management and JUnit verification through the user-provided Maven path/settings/local repository.
- OLE Visio embedding is not implemented in 0-1.
- `.vsdx` conversion is not implemented in 0-1.
- Template replacement, TOC, headers, footers, and watermarks remain future extensions.
