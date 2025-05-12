# :mega: ChatUnitest Core

[English](./README.md) | [中文](./Readme_zh.md)

## 动机
相信很多人试过用ChatGPT帮助自己完成各种各样的编程任务，并且已经取得了不错的效果。但是，直接使用ChatGPT存在一些问题： 一是生成的代码很多时候不能正常执行，**“编程五分钟，调试两小时”**； 二是不方便跟现有工程进行集成，需要手动与ChatGPT进行交互，并且在不同页面间切换。为了解决这些问题，我们提出了 **“生成-验证-修复”** 框架，并实现了原型系统，同时为了方便大家使用，我们开发了一些插件，能够方便的集成到已有开发流程中。已完成Maven插件开发，最新版已发布到Maven中心仓库，欢迎试用和反馈。此外我们已在 IntelliJ IDEA 插件市场中上架了 Chatunitest 插件，您可以在市场中搜索并安装 ChatUniTest，或者访问插件页面[Chatunitest:IntelliJ IDEA Plugin](https://plugins.jetbrains.com/plugin/22522-chatunitest) 了解有关我们插件的更多信息。在这个最新分支，整合了多项我们复现的相关工作，大家可以自行选择，按需使用。

## 概览

![概览](docs/img/overview.jpg)

## 运行步骤

### 0. core的构建
```shell
mvn clean install
```

### 1. 将以下依赖项添加到maven-plugin的`pom.xml`文件中
注意core中pom的版本要与引入依赖的版本一致
```xml
<dependency>
    <groupId>io.github.zju-aces-ise</groupId>
    <artifactId>chatunitest-core</artifactId>
    <version>2.1.1</version>
</dependency>
```
### 2. 后续步骤
详见maven-plugin部分
[chatunitest-maven-plugin/corporation](https://github.com/ZJU-ACES-ISE/chatunitest-maven-plugin)

## 自定义内容
### 使用 FTL 模板

#### 1. 配置映射关系
在 `config.properties` 中定义映射关系。

#### 2. 定义 PromptFile 枚举类
在 `PromptFile` 枚举类中定义枚举常量及其对应的模板文件名。

#### 3. 引用模板
在 `PromptGenerator` 类中的 `getInitPromptFile` 和 `getRepairPromptFile` 方法中引用 `PromptFile` 的模板。

#### 4. 生成 Prompt
后续调用 `PromptGenerator` 的 `generateMessages` 方法即可获取 prompt。具体实现方式可参见 HITS 的实现。

### 扩展 FTL 模板
`PromptInfo` 是一个数据实体类，这部分可以按需扩展。`PromptTemplate` 中的 `dataModel` 存放着供 FTL 模板使用的变量数据。如果有自定义新的 FTL 模板，请检查是否有新的变量引入，并及时更新 `dataModel`。

### 修改生成单测的粒度
可以构造一个 `MethodRunner` 的继承类，参见 `HITSRunner`。并在 `selectRunner` 方法中添加新的实现。

### 自定义单元测试生成方案
如果你想要自行定义单元测试生成方案，下面给出一个示例：

- 首先，你需要定义一个 `PhaseImpl` 的继承类，用于实现核心的生成方案。我们一般将其放置在 `phase` 的 `solution` 文件夹中。
  
- 接着，你需要在 `PhaseImpl` 类中的 `createPhase` 方法中添加新的实现。如果有新增模板，请参考上述使用 FTL 模板的部分；如果有新的数据变量引入，请参见修改 FTL 模板的部分。

- 如需修改生成单测的粒度，例如 HITS 是针对方法切片生成单元测试，请参考修改生成单测的粒度部分。

## :email: 联系我们

如果您有任何问题，请随时通过电子邮件与我们联系，联系方式如下：

1. Corresponding author: `zjuzhichen AT zju.edu.cn`
2. Author: `yh_ch AT zju.edu.cn`, `xiezhuokui AT zju.edu.cn`









