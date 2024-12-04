# :mega: ChatUnitest Core

[English](./README.md) | [中文](./Readme_zh.md)

## Motivation
Many people have tried using ChatGPT to assist with various programming tasks and have achieved good results. However, there are some issues with directly using ChatGPT: First, the generated code often does not execute properly, leading to the adage **“five minutes of coding, two hours of debugging.”** Second, it is inconvenient to integrate with existing projects, as it requires manual interaction with ChatGPT and switching between different pages. 

To address these issues, we have proposed a **“Generate-Validate-Fix”** framework and implemented a prototype system. Additionally, to facilitate usage, we have developed several plugins that can be easily integrated into existing development workflows. The Maven plugin has been completed and the latest version has been published to the Maven Central Repository. We welcome you to try it out and provide feedback. Furthermore, we have launched the Chatunitest plugin in the IntelliJ IDEA Plugin Marketplace. You can search for and install ChatUniTest in the marketplace or visit the plugin page [Chatunitest: IntelliJ IDEA Plugin](https://plugins.jetbrains.com/plugin/22522-chatunitest) for more information. In this latest branch, we have integrated multiple related works that we have reproduced, allowing users to choose and use them as needed.

## Overview

![Overview](docs/img/overview.jpg)

### Reproduced Works
#### ChatTester
This is an implementation for the paper "No More Manual Tests? Evaluating and Improving ChatGPT for Unit Test Generation" [arxiv](https://arxiv.org/abs/2305.04207).
#### CoverUp
This is an implementation for the paper "CoverUp: Coverage-Guided LLM-Based Test Generation" [arxiv](https://arxiv.org/abs/2403.16218).
#### TELPA
This is an implementation for the paper "Enhancing LLM-based Test Generation for Hard-to-Cover Branches via Program Analysis" [arxiv](https://arxiv.org/abs/2404.04966).
#### SysPrompt
This is an implementation for the paper "Code-Aware Prompting: A study of Coverage Guided Test Generation in Regression Setting using LLM" [arxiv](https://arxiv.org/abs/2402.00097).
#### TestPilot
This is an implementation for the paper "An Empirical Evaluation of Using Large Language Models for Automated Unit Test Generation" [arxiv](https://arxiv.org/abs/2302.06527).
#### HITS
This is an implementation for the paper "HITS: High-coverage LLM-based Unit Test Generation via Method Slicing" [arxiv](https://arxiv.org/abs/2408.11324).
#### MUTAP
This is an implementation for the paper "Effective Test Generation Using Pre-trained Large Language Models and Mutation Testing" [arxiv](https://arxiv.org/abs/2308.16557).
#### TestSpark
This is an implementation for the paper "TestSpark: IntelliJ IDEA's Ultimate Test Generation Companion" [arxiv](https://arxiv.org/abs/2401.06580).

## Running Steps

### 0. Build the Core
```shell
mvn clean install
```

### 1. Add the Following Dependency to the `pom.xml` File of the Project for Unit Test Generation
Make sure the version in the core's `pom.xml` matches the version of the dependency you are adding.
```xml
<dependency>
    <groupId>io.github.ZJU-ACES-ISE</groupId>
    <artifactId>chatunitest-starter</artifactId>
    <version>2.0.0</version>
    <type>pom</type>
</dependency>
```

### 2. Subsequent Steps
For detailed instructions, see the Maven plugin section [chatunitest-maven-plugin/corporation](https://github.com/ZJU-ACES-ISE/chatunitest-maven-plugin/tree/corporation).

## :email: Contact Us

If you have any questions, please feel free to contact us via email at the following addresses:

1. Corresponding author: `zjuzhichen AT zju.edu.cn`
2. Author: `yh_ch AT zju.edu.cn`, `xiezhuokui AT zju.edu.cn`
