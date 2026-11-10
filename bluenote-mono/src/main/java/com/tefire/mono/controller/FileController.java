package com.tefire.mono.controller;

import com.tefire.mono.common.R;
import com.tefire.mono.store.FileStore;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 前端 OSS 前缀是 /oss/file，而微服务网关只配了 /file/**，这里按前端原样提供，
 * 免得为了跑通演示去改前端代理或网关路由。
 */
@RestController
public class FileController {

    private final FileStore files;

    public FileController(FileStore files) {
        this.files = files;
    }

    @PostMapping(value = "/oss/file/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@RequestPart("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return R.fail("文件为空");
        }
        return R.ok(files.save(file));
    }

    @GetMapping("/img/upload/{name}")
    public ResponseEntity<Resource> read(@PathVariable String name) throws IOException {
        Path path = files.resolve(name);
        if (path == null) {
            return ResponseEntity.notFound().build();
        }
        String type = Files.probeContentType(path);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(type == null ? "application/octet-stream" : type))
                .body(new FileSystemResource(path));
    }
}
