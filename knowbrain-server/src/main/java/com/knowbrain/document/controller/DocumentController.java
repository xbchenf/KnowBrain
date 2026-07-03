package com.knowbrain.document.controller;

import com.knowbrain.common.Result;
import com.knowbrain.document.entity.EkDocument;
import com.knowbrain.document.service.DocumentService;
import com.knowbrain.permission.PermissionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final PermissionService permissionService;

    /**
     * 上传文档（需 EDITOR 以上权限）
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<EkDocument> upload(@RequestParam("file") MultipartFile file,
                                      @RequestParam("spaceId") Long spaceId,
                                      @RequestParam(value = "category", required = false) String category,
                                      HttpServletRequest request) {
        if (file.isEmpty()) {
            return Result.badRequest("文件不能为空");
        }
        Long uploaderId = getUserId(request);
        permissionService.checkWriteAccess(spaceId, uploaderId);
        EkDocument doc = documentService.upload(file, spaceId, uploaderId, category);
        return Result.ok("上传成功", doc);
    }

    /**
     * 查询文档详情（需空间读权限）
     */
    @GetMapping("/{id}")
    public Result<EkDocument> getById(@PathVariable Long id, HttpServletRequest request) {
        EkDocument doc = documentService.getById(id);
        if (doc == null) {
            return Result.notFound("文档不存在");
        }
        Long userId = getUserId(request);
        permissionService.checkReadAccess(doc.getSpaceId(), userId);
        return Result.ok(doc);
    }

    /**
     * 删除文档（需 EDITOR 以上权限）
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        EkDocument doc = documentService.getById(id);
        if (doc == null) {
            return Result.notFound("文档不存在");
        }
        Long userId = getUserId(request);
        permissionService.checkWriteAccess(doc.getSpaceId(), userId);
        documentService.delete(id);
        return Result.ok("删除成功", null);
    }

    private Long getUserId(HttpServletRequest request) {
        Object uid = request.getAttribute("userId");
        return uid instanceof Number ? ((Number) uid).longValue() : null;
    }
}
