# :mega: ChatUnitest Core

![logo](docs/img/logo.png)


[English](./README.md) | [中文](./Readme_zh.md)

## Background
Many people have tried using ChatGPT to help them with various programming tasks and have achieved good results. However, there are some issues with using ChatGPT directly. Firstly, the generated code often fails to execute correctly, leading to the famous saying **"five minutes to code, two hours to debug"**. Secondly, it is inconvenient to integrate with existing projects as it requires manual interaction with ChatGPT and switching between different platforms. To address these problems, we have proposed the **"Generate-Validate-Repair"** framework and implemented a prototype. Additionally, to make it easier for everyone to use, we have developed some plugins that can be seamlessly integrated into existing development workflows.In this latest branch, multiple related works that we have replicated have been integrated, and everyone can choose and use them as needed.

## Overview

![Overview](docs/img/overview.jpg)

### Related Works
#### ChatTester
This is an implementation for the paper "No More Manual Tests? Evaluating and Improving ChatGPT for Unit Test Generation" [arxiv](https://arxiv.org/abs/2305.04207)
#### CoverUp
This is an implementation for the paper "CoverUp: Coverage-Guided LLM-Based Test Generation" [arxiv](https://arxiv.org/abs/2403.16218).
#### TELPA
This is an implementation for the paper "Enhancing LLM-based Test Generation for Hard-to-Cover Branches via Program Analysis" [arxiv](https://arxiv.org/abs/2404.04966)
#### SysPrompt
This is an implementation for the paper "Code-Aware Prompting: A study of Coverage Guided Test Generation in Regression Setting using LLM" [arxiv](https://arxiv.org/abs/2402.00097)
#### TestPilot
This is an implementation for the paper "An Empirical Evaluation of Using Large Language Models for Automated Unit Test Generation" [arxiv](https://arxiv.org/abs/2302.06527)
#### HITS
This is an implementation for the paper "HITS: High-coverage LLM-based Unit Test Generation via Method Slicing" [arxiv](https://arxiv.org/abs/2408.11324)
#### MUTAP
This is an implementation for the paper "Effective Test Generation Using Pre-trained Large Language Models and Mutation Testing" [arxiv](https://arxiv.org/abs/2308.16557)
#### TestSpark
This is an implementation for the paper "TestSpark: IntelliJ IDEA's Ultimate Test Generation Companion" [arxiv](https://arxiv.org/abs/2401.06580)

## Steps to run

### 0. Construction of Core
```shell
mvn clean install
```

### 1. Modify the ` pom. xml ` file for generating unit test projects
Note that the version of pom in the core should be consistent with the version that introduces dependencies
```xml
<dependency>
    <groupId>io.github.ZJU-ACES-ISE</groupId>
    <artifactId>chatunitest-starter</artifactId>
    <version>2.0.0</version>
    <type>pom</type>
</dependency>
```
### 2. Subsequent steps
Please refer to the Maven plugin section for details
[chatunitest-maven-plugin/corporation](https://github.com/ZJU-ACES-ISE/chatunitest-maven-plugin/tree/corporation)

If you have any questions, please feel free to contact us via email. The email addresses of the authors are as follows:

1. Corresponding author: `zjuzhichen AT zju.edu.cn`
2. Author: `yh_ch AT zju.edu.cn`, `xiezhuokui AT zju.edu.cn`
