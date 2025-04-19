# :mega: ChatUnitest Core

[English](./README.md) | [中文](./Readme_zh.md)

## Motivation
Many have attempted to use ChatGPT to assist with various programming tasks, achieving good results. However, there are some challenges with directly using ChatGPT: First, the generated code often fails to execute correctly, leading to the saying **“five minutes of coding, two hours of debugging”**; second, integrating with existing projects is cumbersome, requiring manual interaction with ChatGPT and switching between different pages. To address these issues, we propose a **“Generate-Validate-Fix”** framework and have developed a prototype system. To facilitate usage, we created several plugins that can easily integrate into existing development workflows. We have completed the development of the Maven plugin, and the latest version has been released to the Maven Central Repository for your trial and feedback. Additionally, we have launched the Chatunitest plugin in the IntelliJ IDEA Plugin Marketplace. You can search for and install ChatUniTest in the marketplace or visit the plugin page [Chatunitest: IntelliJ IDEA Plugin](https://plugins.jetbrains.com/plugin/22522-chatunitest) for more information. This latest branch integrates several related works we have reproduced, allowing you to choose according to your needs.

## Overview

![Overview](docs/img/overview.jpg)

## Running Steps

### 0. Build the Core
```shell
mvn clean install
```

### 1. Add the following dependency to the `pom.xml` file of the Maven plugin
Make sure the version in the core's pom matches the version of the imported dependency.
```xml
<dependency>
    <groupId>io.github.zju-aces-ise</groupId>
    <artifactId>chatunitest-core</artifactId>
    <version>2.1.0</version>
</dependency>
```

### 2. Subsequent Steps
For detailed instructions, please refer to the Maven plugin section:
[chatunitest-maven-plugin/corporation](https://github.com/ZJU-ACES-ISE/chatunitest-maven-plugin/)

## Custom Content
### Using FTL Templates

#### 1. Configure Mapping
Define the mapping in the `config.properties` file.

#### 2. Define the PromptFile Enum
Define enum constants and their corresponding template filenames in the `PromptFile` enum class.

#### 3. Reference Templates
Reference the `PromptFile` templates in the `getInitPromptFile` and `getRepairPromptFile` methods of the `PromptGenerator` class.

#### 4. Generate Prompts
Subsequently, call the `generateMessages` method of the `PromptGenerator` to obtain the prompt. For specific implementation details, please refer to the HITS implementation.

### Extending FTL Templates
`PromptInfo` is a data entity class that can be extended as needed. The `dataModel` in `PromptTemplate` holds the variable data used by the FTL templates. If you introduce new variables in a custom FTL template, ensure that you update the `dataModel` accordingly.

### Modifying the Granularity of Generated Unit Tests
You can create a subclass of `MethodRunner`, similar to `HITSRunner`, and add new implementations in the `selectRunner` method.

### Custom Unit Test Generation Scheme
If you wish to define your own unit test generation scheme, here is an example:

- First, define a subclass of `PhaseImpl` to implement the core generation scheme. We typically place this in the `phase`'s `solution` folder.
  
- Next, add new implementations in the `createPhase` method of the `PhaseImpl` class. If you have new templates, please refer to the section on using FTL templates; if there are new data variables, see the section on modifying FTL templates.

- If you need to modify the granularity of the generated unit tests (for example, HITS generates unit tests based on method slicing), please refer to the section on modifying the granularity of generated unit tests.

## :email: Contact Us

If you have any questions, please feel free to contact us via email:

1. Corresponding author: `zjuzhichen AT zju.edu.cn`
2. Author: `yh_ch AT zju.edu.cn`, `xiezhuokui AT zju.edu.cn`
