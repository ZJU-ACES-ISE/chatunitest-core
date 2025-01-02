package zju.cst.aces.util.hits;

import java.io.*;
import java.nio.file.*;
import java.util.regex.*;

public class processNameForHITS {

    public static void main(String[] args) {
        // 文件夹路径
        String folderPath = "D:\\APP\\IdeaProjects\\commons-csv-rel-commons-csv-1.10.0\\src\\test\\java\\org\\apache\\commons\\csv"; // 请替换为你的文件夹路径

        // 正则表达式匹配文件名
        Pattern filePattern = Pattern.compile("^(.*)_Test_slice(\\d+)\\.java$");

        // 遍历文件夹
        File folder = new File(folderPath);
        File[] files = folder.listFiles();

        if (files != null) {
            for (File file : files) {
                // 只处理 .java 文件
                if (file.isFile() && file.getName().endsWith(".java")) {
                    Matcher matcher = filePattern.matcher(file.getName());
                    if (matcher.matches()) {
                        String xxx = matcher.group(1);
                        String n = matcher.group(2);
                        String newClassName = xxx + "_slice" + n + "_Test";
                        String newFileName = xxx + "_slice" + n + "_Test.java";

                        // 读取文件内容并替换类名
                        try {
                            String content = new String(Files.readAllBytes(file.toPath()));
                            content = content.replaceAll("class " + Pattern.quote(xxx + "_Test"), "class " + newClassName);

                            // 写入新的文件内容
                            Files.write(file.toPath(), content.getBytes());

                            // 重命名文件
                            File newFile = new File(folderPath, newFileName);
                            if (file.renameTo(newFile)) {
                                System.out.println("Renamed: " + file.getName() + " to " + newFileName);
                            } else {
                                System.out.println("Failed to rename: " + file.getName());
                            }
                        } catch (IOException e) {
                            System.err.println("Error processing file: " + file.getName());
                            e.printStackTrace();
                        }
                    }
                }
            }
        } else {
            System.out.println("The specified folder does not exist or is empty.");
        }
    }
}

