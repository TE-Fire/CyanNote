package com.tefire.mono.store;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 上传落本地临时目录，重启不清空但数据索引会重置，演示够用。
 * 不用 MinIO：单体版的存在意义就是零外部依赖。
 */
@Component
public class FileStore {

    public static final String PUBLIC_BASE = "http://127.0.0.1:8000/img/upload/";

    private final Path dir;

    public FileStore() {
        this.dir = Paths.get(System.getProperty("java.io.tmpdir"), "bluenote-mono-uploads");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建上传目录: " + dir, e);
        }
    }

    public String save(MultipartFile file) throws IOException {
        String name = UUID.randomUUID().toString().replace("-", "") + extOf(file.getOriginalFilename());
        file.transferTo(dir.resolve(name));
        return PUBLIC_BASE + name;
    }

    public Path resolve(String name) {
        // 只接受纯文件名，避免 ../ 穿越
        if (name.contains("/") || name.contains("\\") || name.contains("..")) {
            return null;
        }
        Path p = dir.resolve(name);
        return Files.exists(p) ? p : null;
    }

    private String extOf(String original) {
        if (original == null) {
            return "";
        }
        int dot = original.lastIndexOf('.');
        return dot < 0 ? "" : original.substring(dot).toLowerCase();
    }
}
