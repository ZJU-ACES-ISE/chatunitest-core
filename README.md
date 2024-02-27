# :mega: ChatUnitest Core

![logo](docs/img/logo.png)


[English](./README.md) | [中文](./Readme_zh.md)

## Background
Many people have tried using ChatGPT to help them with various programming tasks and have achieved good results. However, there are some issues with using ChatGPT directly. Firstly, the generated code often fails to execute correctly, leading to the famous saying **"five minutes to code, two hours to debug"**. Secondly, it is inconvenient to integrate with existing projects as it requires manual interaction with ChatGPT and switching between different platforms. To address these problems, we have proposed the **"Generate-Validate-Repair"** framework and implemented a prototype. Additionally, to make it easier for everyone to use, we have developed some plugins that can be seamlessly integrated into existing development workflows.

## Overview

![Overview](docs/img/overview.jpg)

## Steps to run

### 0. Add our dependency to `pom.xml` and config
```xml
<dependency>
    <groupId>io.github.ZJU-ACES-ISE</groupId>
    <artifactId>chatunitest-core</artifactId>
    <version>1.1.0</version>
</dependency>
```

### 1. Add the following dependency to pom.xml

```xml
<dependency>
    <groupId>io.github.ZJU-ACES-ISE</groupId>
    <artifactId>chatunitest-starter</artifactId>
    <version>1.4.0</version>
    <type>pom</type>
</dependency>
```

## Design Your Custom Prompt

### 1.Create a Directory Containing Your Prompt Files Using Freemarker

Refer to the examples in `src/main/resources/prompt`:

`initial.ftl` serves as the initial prompt in the basic generation process.
`initial_system.ftl` serves as the corresponding system prompt in the basic generation process.

`extra.ftl` and `extra_system.ftl` are designed for further extensions in the pipeline (currently not in use).

`repair.ftl` serves as the repair prompt in the repair process. 

### 2. Update the Template Filenames in the `config.properties` File

```properties
PROMPT_TEMPLATE_INIT=initial.ftl
PROMPT_TEMPLATE_EXTRA=extra.ftl
PROMPT_TEMPLATE_REPAIR=repair.ftl
```

### 3. To Use an Extra Template, Extend the `MethodRunner` and Override the `startRounds` Method

Refer to the example in `ChatTester Github Repository`.

## :email: Contact us

If you have any questions, please feel free to contact us via email. The email addresses of the authors are as follows:

1. Corresponding author: `zjuzhichen AT zju.edu.cn`
2. Author: `yh_ch AT zju.edu.cn`, `xiezhuokui AT zju.edu.cn`
