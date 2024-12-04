# :mega: ChatUnitest Core

[English](./README.md) | [中文](./Readme_zh.md)

## 动机
相信很多人试过用ChatGPT帮助自己完成各种各样的编程任务，并且已经取得了不错的效果。但是，直接使用ChatGPT存在一些问题： 一是生成的代码很多时候不能正常执行，**“编程五分钟，调试两小时”**； 二是不方便跟现有工程进行集成，需要手动与ChatGPT进行交互，并且在不同页面间切换。为了解决这些问题，我们提出了 **“生成-验证-修复”** 框架，并实现了原型系统，同时为了方便大家使用，我们开发了一些插件，能够方便的集成到已有开发流程中。已完成Maven插件开发，最新版已发布到Maven中心仓库，欢迎试用和反馈。此外我们已在 IntelliJ IDEA 插件市场中上架了 Chatunitest 插件，您可以在市场中搜索并安装 ChatUniTest，或者访问插件页面[Chatunitest:IntelliJ IDEA Plugin](https://plugins.jetbrains.com/plugin/22522-chatunitest) 了解有关我们插件的更多信息。在这个最新分支，整合了多项我们复现的相关工作，大家可以自行选择，按需使用。

## 概览

![概览](docs/img/overview.jpg)

### 复现工作
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
## 运行步骤

### 0. core的构建
```shell
mvn clean install
```

### 1. 将以下依赖项添加到待生成单元测试项目的`pom.xml`文件中
注意core中pom的版本要与引入依赖的版本一致
```xml
<dependency>
    <groupId>io.github.ZJU-ACES-ISE</groupId>
    <artifactId>chatunitest-starter</artifactId>
    <version>2.0.0</version>
    <type>pom</type>
</dependency>
```
### 2. 后续步骤
详见maven-plugin部分
[chatunitest-maven-plugin/corporation](https://github.com/ZJU-ACES-ISE/chatunitest-maven-plugin/tree/corporation)


## :email: 联系我们

如果您有任何问题，请随时通过电子邮件与我们联系，联系方式如下：

1. Corresponding author: `zjuzhichen AT zju.edu.cn`
2. Author: `yh_ch AT zju.edu.cn`, `xiezhuokui AT zju.edu.cn`









